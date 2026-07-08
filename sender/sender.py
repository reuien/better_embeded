from __future__ import annotations

import argparse
import logging
import socket
import time
from datetime import datetime
from logging.handlers import RotatingFileHandler
from pathlib import Path
from typing import Any
from uuid import uuid4

import cv2
import requests
from ultralytics import YOLO


CAMERA_APIS = {
    "auto": 0,
    "default": 0,
    "dshow": getattr(cv2, "CAP_DSHOW", 0),
    "msmf": getattr(cv2, "CAP_MSMF", 0),
    "avfoundation": getattr(cv2, "CAP_AVFOUNDATION", 0),
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="PLC defect detection sender")
    parser.add_argument("--model", default="models/best.pt", help="YOLO model path")
    parser.add_argument("--server-url", default="http://127.0.0.1:8080/api/detection-records")
    parser.add_argument("--device-id", default=socket.gethostname())
    parser.add_argument("--camera", type=int, default=0)
    parser.add_argument("--camera-api", choices=CAMERA_APIS.keys(), default="auto")
    parser.add_argument("--width", type=int)
    parser.add_argument("--height", type=int)
    parser.add_argument("--imgsz", type=int, default=640)
    parser.add_argument("--conf", type=float, default=0.35)
    parser.add_argument("--device", default="auto", help="YOLO device: auto, cpu, 0, 1, ...")
    parser.add_argument("--interval", type=float, default=1.0)
    parser.add_argument("--output", default="sender_output")
    parser.add_argument("--log-file", default="logs/sender.log")
    parser.add_argument("--preview", action="store_true")
    parser.add_argument("--once", action="store_true")
    parser.add_argument("--retry", type=int, default=3)
    parser.add_argument("--heartbeat-url", help="Optional device heartbeat URL")
    parser.add_argument("--heartbeat-interval", type=float, default=30.0)
    return parser.parse_args()


def configure_logging(log_file: Path) -> None:
    log_file.parent.mkdir(parents=True, exist_ok=True)
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(message)s",
        handlers=[
            RotatingFileHandler(log_file, maxBytes=5_000_000, backupCount=5, encoding="utf-8"),
            logging.StreamHandler(),
        ],
    )


def open_camera(args: argparse.Namespace) -> cv2.VideoCapture:
    capture = cv2.VideoCapture(args.camera, CAMERA_APIS[args.camera_api])
    if args.width:
        capture.set(cv2.CAP_PROP_FRAME_WIDTH, args.width)
    if args.height:
        capture.set(cv2.CAP_PROP_FRAME_HEIGHT, args.height)
    if not capture.isOpened():
        raise RuntimeError(f"Camera open failed: index={args.camera}, api={args.camera_api}")
    return capture


def predict(model: YOLO, frame, args: argparse.Namespace) -> tuple[str, str, float, list[dict[str, Any]], Any]:
    kwargs: dict[str, Any] = {"source": frame, "imgsz": args.imgsz, "conf": args.conf, "verbose": False}
    if args.device != "auto":
        kwargs["device"] = args.device
    result = model.predict(**kwargs)[0]
    annotated = result.plot()
    detections: list[dict[str, Any]] = []
    max_confidence = 0.0
    labels: list[str] = []
    names = result.names or {}

    if result.boxes is not None:
        for box in result.boxes:
            class_id = int(box.cls[0].detach().cpu())
            label = str(names.get(class_id, class_id))
            confidence = float(box.conf[0].detach().cpu())
            xyxy = [float(value) for value in box.xyxy[0].detach().cpu().tolist()]
            labels.append(label)
            max_confidence = max(max_confidence, confidence)
            detections.append({"classId": class_id, "label": label, "confidence": confidence, "xyxy": xyxy})

    result_label = "defect" if detections else "normal"
    defect_type = ",".join(sorted(set(labels))) if labels else "none"
    return result_label, defect_type, max_confidence, detections, annotated


def save_images(output_root: Path, frame, annotated, result: str) -> Path:
    date_folder = datetime.now().strftime("%Y-%m-%d")
    stamp = datetime.now().strftime("%Y%m%dT%H%M%S%f")
    image_id = uuid4().hex[:8]
    raw_dir = output_root / "raw" / date_folder
    result_dir = output_root / result / date_folder
    raw_dir.mkdir(parents=True, exist_ok=True)
    result_dir.mkdir(parents=True, exist_ok=True)
    raw_path = raw_dir / f"{stamp}_{image_id}.jpg"
    annotated_path = result_dir / f"{stamp}_{image_id}.jpg"
    cv2.imwrite(str(raw_path), frame)
    cv2.imwrite(str(annotated_path), annotated)
    return annotated_path


def upload(server_url: str, image_path: Path, args: argparse.Namespace, result: str, defect_type: str, confidence: float) -> None:
    data = {
        "deviceId": args.device_id,
        "result": result,
        "defectType": defect_type,
        "confidence": f"{confidence:.6f}",
        "detectTime": datetime.now().isoformat(timespec="seconds"),
    }
    for attempt in range(1, args.retry + 1):
        try:
            with image_path.open("rb") as file:
                response = requests.post(
                    server_url,
                    data=data,
                    files={"image": (image_path.name, file, "image/jpeg")},
                    timeout=10,
                )
            response.raise_for_status()
            logging.info("upload success result=%s defectType=%s image=%s", result, defect_type, image_path)
            return
        except requests.RequestException as exc:
            logging.warning("upload failed attempt=%s/%s error=%s", attempt, args.retry, exc)
            time.sleep(min(2 * attempt, 8))
    raise RuntimeError(f"Upload failed after {args.retry} attempts: {image_path}")


def heartbeat(args: argparse.Namespace, model_name: str) -> None:
    if not args.heartbeat_url:
        return
    payload = {
        "hostName": socket.gethostname(),
        "camera": str(args.camera),
        "modelName": model_name,
    }
    try:
        requests.put(args.heartbeat_url, json=payload, timeout=5).raise_for_status()
        logging.info("heartbeat success device=%s", args.device_id)
    except requests.RequestException as exc:
        logging.warning("heartbeat failed error=%s", exc)


def main() -> None:
    args = parse_args()
    configure_logging(Path(args.log_file))
    model_path = Path(args.model)
    if not model_path.exists():
        raise FileNotFoundError(f"Model not found: {model_path}")

    logging.info("sender starting device=%s model=%s", args.device_id, model_path)
    model = YOLO(str(model_path))
    capture = open_camera(args)
    output_root = Path(args.output)
    last_heartbeat = 0.0

    try:
        while True:
            now = time.monotonic()
            if now - last_heartbeat >= args.heartbeat_interval:
                heartbeat(args, model_path.name)
                last_heartbeat = now

            ok, frame = capture.read()
            if not ok:
                raise RuntimeError("Camera frame read failed")

            result, defect_type, confidence, detections, annotated = predict(model, frame, args)
            image_path = save_images(output_root, frame, annotated, result)
            upload(args.server_url, image_path, args, result, defect_type, confidence)
            logging.info(
                "detect done result=%s detections=%s confidence=%.4f image=%s",
                result,
                len(detections),
                confidence,
                image_path,
            )

            if args.preview:
                cv2.imshow("PLC defect sender", annotated)
                if cv2.waitKey(1) & 0xFF == ord("q"):
                    break
            if args.once:
                break
            time.sleep(max(0.0, args.interval))
    finally:
        capture.release()
        if args.preview:
            cv2.destroyAllWindows()
        logging.info("sender stopped")


if __name__ == "__main__":
    main()
