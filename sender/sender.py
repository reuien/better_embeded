from __future__ import annotations

import argparse
import json
import logging
import socket
import time
from datetime import datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from logging.handlers import RotatingFileHandler
from pathlib import Path
from threading import Event, Lock, Thread
from typing import Any
from urllib.parse import urlparse, urlunparse
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


SENDER_PAGE = b"""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>PLC Sender Console</title>
  <style>
    :root { color-scheme: light; font-family: 'Segoe UI', Verdana, sans-serif; background: #eef2f1; color: #17211d; }
    body { margin: 0; }
    header { background: #0b1f18; color: #fff; padding: 18px 24px; display: flex; justify-content: space-between; gap: 18px; align-items: center; }
    h1 { margin: 0; font-size: 24px; letter-spacing: 0; }
    main { display: grid; grid-template-columns: minmax(360px, 1fr) 390px; gap: 16px; padding: 16px; }
    .panel { background: #fff; border: 1px solid #d9e0e7; border-radius: 6px; overflow: hidden; }
    .video-shell { position: relative; min-height: 420px; background: #111; }
    .video { width: 100%; min-height: 420px; object-fit: contain; display: block; }
    .video-badge { position: absolute; left: 14px; top: 14px; background: rgba(5, 18, 14, .78); color: #f8fafc; border-radius: 4px; padding: 6px 9px; font-size: 12px; font-weight: 700; }
    .stats, .controls { padding: 16px; display: grid; gap: 12px; }
    .stack { display: grid; gap: 16px; }
    .row { display: flex; justify-content: space-between; gap: 16px; border-bottom: 1px solid #eef2f5; padding-bottom: 8px; }
    .row span:first-child { color: #64748b; }
    .value { font-weight: 700; text-align: right; word-break: break-word; }
    label { display: grid; gap: 5px; color: #52615b; font-size: 13px; }
    input { height: 34px; border: 1px solid #ccd6d1; border-radius: 4px; padding: 0 10px; font-size: 14px; }
    .actions { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 8px; }
    button { height: 36px; border: 0; border-radius: 4px; font-weight: 700; cursor: pointer; }
    button:disabled { opacity: .45; cursor: not-allowed; }
    .start { background: #116149; color: white; }
    .stop { background: #b91c1c; color: white; }
    .test { background: #dbe7e2; color: #14231e; }
    .defect { color: #c62828; }
    .normal { color: #15803d; }
    .failed { color: #c2410c; }
    .success { color: #15803d; }
    @media (max-width: 900px) { main { grid-template-columns: 1fr; } }
  </style>
</head>
<body>
  <header>
    <div>
      <h1>PLC Sender Console</h1>
      <div>Configure receiver, start camera inference, and monitor upload status</div>
    </div>
    <div id="runningBadge">waiting</div>
  </header>
  <main>
    <section class="panel">
      <div class="video-shell">
        <img class="video" src="/stream.mjpg" alt="live camera detection stream">
        <div class="video-badge" id="videoBadge">camera waiting</div>
      </div>
    </section>
    <aside class="stack">
      <section class="panel controls">
        <label>macOS receiver IP<input id="receiverIp" value="10.84.87.28"></label>
        <label>receiver port<input id="receiverPort" value="8080"></label>
        <label>device ID<input id="deviceInput" value="PLC-WIN-01"></label>
        <div class="actions">
          <button class="test" id="testBtn">Test</button>
          <button class="start" id="startBtn">Start</button>
          <button class="stop" id="stopBtn">Stop</button>
        </div>
      </section>
      <section class="panel stats">
        <div class="row"><span>Device</span><span class="value" id="deviceId">-</span></div>
        <div class="row"><span>Receiver</span><span class="value" id="serverUrl">-</span></div>
        <div class="row"><span>Running</span><span class="value" id="running">-</span></div>
        <div class="row"><span>Result</span><span class="value" id="result">-</span></div>
        <div class="row"><span>Defect type</span><span class="value" id="defectType">-</span></div>
        <div class="row"><span>Confidence</span><span class="value" id="confidence">-</span></div>
        <div class="row"><span>Detections</span><span class="value" id="detections">-</span></div>
        <div class="row"><span>Upload</span><span class="value" id="uploadStatus">-</span></div>
        <div class="row"><span>Last image</span><span class="value" id="imagePath">-</span></div>
        <div class="row"><span>Updated</span><span class="value" id="updatedAt">-</span></div>
        <div class="row"><span>Error</span><span class="value failed" id="uploadError">-</span></div>
      </section>
    </aside>
  </main>
  <script>
    const ids = ["deviceId", "serverUrl", "running", "result", "defectType", "confidence", "detections", "uploadStatus", "imagePath", "updatedAt", "uploadError"];
    const receiverIp = document.getElementById("receiverIp");
    const receiverPort = document.getElementById("receiverPort");
    const deviceInput = document.getElementById("deviceInput");
    const configInputs = [receiverIp, receiverPort, deviceInput];
    function payload() {
      const ip = receiverIp.value.trim();
      const port = receiverPort.value.trim() || "8080";
      return {
        receiverIp: ip,
        receiverPort: port,
        serverUrl: `http://${ip}:${port}/api/detection-records`,
        deviceId: deviceInput.value.trim() || "PLC-WIN-01"
      };
    }
    async function postJson(path) {
      const response = await fetch(path, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload())
      });
      const data = await response.json();
      if (!response.ok || data.ok === false) throw new Error(data.error || response.statusText);
      return data;
    }
    async function refreshStatus() {
      try {
        const response = await fetch("/api/status", { cache: "no-store" });
        const data = await response.json();
        for (const id of ids) document.getElementById(id).textContent = data[id] ?? "-";
        if (!configInputs.includes(document.activeElement)) {
          if (data.receiverIp) receiverIp.value = data.receiverIp;
          if (data.receiverPort) receiverPort.value = data.receiverPort;
          if (data.deviceId) deviceInput.value = data.deviceId;
        }
        document.getElementById("runningBadge").textContent = data.running ? "running" : "stopped";
        document.getElementById("videoBadge").textContent = data.running ? "camera live" : "camera waiting";
        document.getElementById("startBtn").disabled = !!data.running;
        document.getElementById("stopBtn").disabled = !data.running;
        document.getElementById("testBtn").disabled = !!data.running;
        const result = document.getElementById("result");
        result.className = "value " + (data.result === "defect" ? "defect" : "normal");
        const upload = document.getElementById("uploadStatus");
        upload.className = "value " + (data.uploadStatus || "");
      } catch (error) {
        document.getElementById("uploadError").textContent = String(error);
      }
    }
    document.getElementById("testBtn").onclick = async () => { try { await postJson("/api/check"); } catch (error) { alert(error.message); } finally { refreshStatus(); } };
    document.getElementById("startBtn").onclick = async () => { try { await postJson("/api/start"); } catch (error) { alert(error.message); } finally { refreshStatus(); } };
    document.getElementById("stopBtn").onclick = async () => { try { await postJson("/api/stop"); } catch (error) { alert(error.message); } finally { refreshStatus(); } };
    refreshStatus();
    setInterval(refreshStatus, 1000);
  </script>
</body>
</html>"""


class SenderWebState:
    def __init__(self, device_id: str, server_url: str) -> None:
        self.lock = Lock()
        self.latest_jpeg: bytes | None = None
        parsed = urlparse(server_url)
        receiver_ip = parsed.hostname or "10.84.87.28"
        receiver_port = str(parsed.port or 8080)
        self.status: dict[str, Any] = {
            "deviceId": device_id,
            "receiverIp": receiver_ip,
            "receiverPort": receiver_port,
            "serverUrl": server_url,
            "running": False,
            "result": "waiting",
            "defectType": "-",
            "confidence": "-",
            "detections": 0,
            "uploadStatus": "waiting",
            "uploadError": "-",
            "imagePath": "-",
            "updatedAt": "-",
            "serverStopping": False,
        }

    def update(self, **values: Any) -> None:
        with self.lock:
            self.status.update(values)

    def update_frame(self, frame_jpeg: bytes, **values: Any) -> None:
        with self.lock:
            self.latest_jpeg = frame_jpeg
            self.status.update(values)

    def snapshot(self) -> tuple[bytes | None, dict[str, Any]]:
        with self.lock:
            return self.latest_jpeg, dict(self.status)


def build_detection_url(receiver_ip: str, receiver_port: str | int) -> str:
    receiver_ip = receiver_ip.strip()
    receiver_port = str(receiver_port).strip() or "8080"
    if receiver_ip.startswith("http://") or receiver_ip.startswith("https://"):
        parsed = urlparse(receiver_ip)
        path = parsed.path if parsed.path else "/api/detection-records"
        if path == "/":
            path = "/api/detection-records"
        return urlunparse((parsed.scheme, parsed.netloc, path, "", "", ""))
    return f"http://{receiver_ip}:{receiver_port}/api/detection-records"


def payload_config(payload: dict[str, Any], fallback: dict[str, Any]) -> dict[str, str]:
    receiver_ip = str(payload.get("receiverIp") or fallback.get("receiverIp") or "10.84.87.28").strip()
    receiver_port = str(payload.get("receiverPort") or fallback.get("receiverPort") or "8080").strip()
    server_url = str(payload.get("serverUrl") or build_detection_url(receiver_ip, receiver_port)).strip()
    device_id = str(payload.get("deviceId") or fallback.get("deviceId") or "PLC-WIN-01").strip()
    parsed = urlparse(server_url)
    return {
        "receiverIp": parsed.hostname or receiver_ip,
        "receiverPort": str(parsed.port or receiver_port or "8080"),
        "serverUrl": server_url,
        "deviceId": device_id,
    }


class SenderRuntime:
    def __init__(self, args: argparse.Namespace, state: SenderWebState) -> None:
        self.args = args
        self.state = state
        self.lock = Lock()
        self.stop_event = Event()
        self.thread: Thread | None = None

    def is_running(self) -> bool:
        return self.thread is not None and self.thread.is_alive()

    def check(self, config: dict[str, str]) -> None:
        if self.is_running():
            raise RuntimeError("Stop recognition before changing or testing receiver settings")
        self.args.server_url = config["serverUrl"]
        self.args.device_id = config["deviceId"]
        self.state.update(
            **config,
            uploadStatus="checking",
            uploadError="-",
            updatedAt=datetime.now().isoformat(timespec="seconds"),
        )
        check_receiver(self.args)
        self.state.update(
            uploadStatus="reachable",
            uploadError="-",
            updatedAt=datetime.now().isoformat(timespec="seconds"),
        )

    def start(self, config: dict[str, str]) -> None:
        with self.lock:
            if self.is_running():
                return
            self.args.server_url = config["serverUrl"]
            self.args.device_id = config["deviceId"]
            self.stop_event = Event()
            self.state.update(
                **config,
                running=True,
                uploadStatus="starting",
                uploadError="-",
                updatedAt=datetime.now().isoformat(timespec="seconds"),
            )
            self.thread = Thread(target=self.run_loop, daemon=True)
            self.thread.start()

    def stop(self) -> None:
        self.stop_event.set()
        self.state.update(running=False, uploadStatus="stopping", updatedAt=datetime.now().isoformat(timespec="seconds"))

    def publish_frame(self, frame: Any, **values: Any) -> None:
        jpeg_ok, jpeg_buffer = cv2.imencode(
            ".jpg",
            frame,
            [int(cv2.IMWRITE_JPEG_QUALITY), max(1, min(100, self.args.jpeg_quality))],
        )
        if jpeg_ok:
            self.state.update_frame(
                jpeg_buffer.tobytes(),
                updatedAt=datetime.now().isoformat(timespec="seconds"),
                **values,
            )

    def run_loop(self) -> None:
        model_path = Path(self.args.model)
        capture: cv2.VideoCapture | None = None
        failed = False
        try:
            capture = open_camera(self.args)
            ok, frame = capture.read()
            if not ok:
                raise RuntimeError("Camera frame read failed")
            self.publish_frame(
                frame,
                result="preview",
                defectType="-",
                confidence="-",
                detections=0,
                uploadStatus="camera_ready",
                uploadError="-",
                imagePath="-",
            )
            check_receiver(self.args)
            self.state.update(uploadStatus="loading_model", uploadError="-", updatedAt=datetime.now().isoformat(timespec="seconds"))
            model = YOLO(str(model_path))
            output_root = Path(self.args.output)
            last_heartbeat = 0.0
            self.state.update(uploadStatus="running", uploadError="-", updatedAt=datetime.now().isoformat(timespec="seconds"))

            while not self.stop_event.is_set():
                now = time.monotonic()
                if now - last_heartbeat >= self.args.heartbeat_interval:
                    heartbeat(self.args, model_path.name)
                    last_heartbeat = now

                ok, frame = capture.read()
                if not ok:
                    raise RuntimeError("Camera frame read failed")
                self.publish_frame(
                    frame,
                    result="preview",
                    defectType="-",
                    confidence="-",
                    detections=0,
                    uploadStatus="recognizing",
                    uploadError="-",
                )

                result, defect_type, confidence, detections, annotated = predict(model, frame, self.args)
                image_path = save_images(output_root, frame, annotated, result)
                self.publish_frame(
                    annotated,
                    result=result,
                    defectType=defect_type,
                    confidence=f"{confidence:.1%}",
                    detections=len(detections),
                    uploadStatus="uploading",
                    uploadError="-",
                    imagePath=str(image_path),
                )

                uploaded = upload(self.args.server_url, image_path, self.args, result, defect_type, confidence)
                self.state.update(
                    uploadStatus="success" if uploaded else "failed",
                    uploadError="-" if uploaded else f"Upload failed after {self.args.retry} attempts",
                    updatedAt=datetime.now().isoformat(timespec="seconds"),
                )
                logging.info(
                    "detect done result=%s detections=%s confidence=%.4f uploaded=%s image=%s",
                    result,
                    len(detections),
                    confidence,
                    uploaded,
                    image_path,
                )

                if self.args.preview:
                    cv2.imshow("PLC defect sender", annotated)
                    if cv2.waitKey(1) & 0xFF == ord("q"):
                        break
                if self.args.once:
                    break
                self.stop_event.wait(max(0.0, self.args.interval))
        except Exception as exc:
            failed = True
            logging.exception("sender worker failed")
            self.state.update(uploadStatus="failed", uploadError=str(exc), updatedAt=datetime.now().isoformat(timespec="seconds"))
        finally:
            if capture:
                capture.release()
            if self.args.preview:
                cv2.destroyAllWindows()
            self.state.update(
                running=False,
                uploadStatus="failed" if failed else "stopped",
                updatedAt=datetime.now().isoformat(timespec="seconds"),
            )


def make_web_handler(state: SenderWebState, runtime: SenderRuntime) -> type[BaseHTTPRequestHandler]:
    class SenderWebHandler(BaseHTTPRequestHandler):
        def do_GET(self) -> None:
            if self.path in ("/", "/index.html"):
                self.send_response(200)
                self.send_header("Content-Type", "text/html; charset=utf-8")
                self.send_header("Content-Length", str(len(SENDER_PAGE)))
                self.end_headers()
                self.wfile.write(SENDER_PAGE)
                return
            if self.path == "/api/status":
                self.write_json(state.snapshot()[1])
                return
            if self.path == "/stream.mjpg":
                self.stream_frames()
                return
            self.send_error(404)

        def do_POST(self) -> None:
            try:
                payload = self.read_payload()
                config = payload_config(payload, state.snapshot()[1])
                if self.path == "/api/check":
                    runtime.check(config)
                    self.write_json({"ok": True, **state.snapshot()[1]})
                    return
                if self.path == "/api/start":
                    runtime.start(config)
                    self.write_json({"ok": True, **state.snapshot()[1]})
                    return
                if self.path == "/api/stop":
                    runtime.stop()
                    self.write_json({"ok": True, **state.snapshot()[1]})
                    return
                self.send_error(404)
            except Exception as exc:
                logging.warning("sender web request failed path=%s error=%s", self.path, exc)
                self.write_json({"ok": False, "error": str(exc)}, status=400)

        def read_payload(self) -> dict[str, Any]:
            length = int(self.headers.get("Content-Length", "0"))
            if length <= 0:
                return {}
            body = self.rfile.read(length)
            return json.loads(body.decode("utf-8"))

        def write_json(self, payload: dict[str, Any], status: int = 200) -> None:
            body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Cache-Control", "no-store")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def stream_frames(self) -> None:
            self.send_response(200)
            self.send_header("Content-Type", "multipart/x-mixed-replace; boundary=frame")
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            try:
                while True:
                    jpeg, status = state.snapshot()
                    if status.get("serverStopping"):
                        return
                    if jpeg is None:
                        time.sleep(0.2)
                        continue
                    self.wfile.write(b"--frame\r\n")
                    self.wfile.write(b"Content-Type: image/jpeg\r\n")
                    self.wfile.write(f"Content-Length: {len(jpeg)}\r\n\r\n".encode("ascii"))
                    self.wfile.write(jpeg)
                    self.wfile.write(b"\r\n")
                    time.sleep(0.1)
            except (BrokenPipeError, ConnectionResetError):
                return

        def log_message(self, format: str, *args: Any) -> None:
            logging.debug("sender web: " + format, *args)

    return SenderWebHandler


def start_web_server(args: argparse.Namespace, state: SenderWebState, runtime: SenderRuntime) -> ThreadingHTTPServer | None:
    if args.no_web:
        return None
    server = ThreadingHTTPServer((args.web_host, args.web_port), make_web_handler(state, runtime))
    thread = Thread(target=server.serve_forever, daemon=True)
    thread.start()
    logging.info("sender web page running at http://%s:%s", args.web_host, args.web_port)
    return server


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="PLC defect detection sender")
    parser.add_argument("--model", default="models/best.pt", help="YOLO model path")
    parser.add_argument("--server-url", default="http://10.84.87.28:8080/api/detection-records")
    parser.add_argument("--device-id", default="PLC-WIN-01")
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
    parser.add_argument("--auto-start", action="store_true", help="Start inference immediately without pressing Start on the web page")
    parser.add_argument("--no-web", action="store_true", help="Disable the sender local web preview page")
    parser.add_argument("--web-host", default="0.0.0.0")
    parser.add_argument("--web-port", type=int, default=8090)
    parser.add_argument("--jpeg-quality", type=int, default=80)
    parser.add_argument("--once", action="store_true")
    parser.add_argument("--retry", type=int, default=3)
    parser.add_argument("--receiver-check-retry", type=int, default=5)
    parser.add_argument("--receiver-check-timeout", type=float, default=3.0)
    parser.add_argument("--skip-receiver-check", action="store_true")
    parser.add_argument(
        "--use-system-proxy",
        action="store_true",
        help="Use HTTP_PROXY/HTTPS_PROXY from the system environment. Disabled by default for LAN uploads.",
    )
    parser.add_argument("--heartbeat-url", help="Optional device heartbeat URL")
    parser.add_argument("--heartbeat-interval", type=float, default=30.0)
    return parser.parse_args()


def receiver_check_url(server_url: str) -> str:
    parsed = urlparse(server_url)
    if not parsed.scheme or not parsed.netloc:
        raise ValueError(f"Invalid server URL: {server_url}")
    return urlunparse((parsed.scheme, parsed.netloc, "/api/system-status", "", "", ""))


def check_receiver(args: argparse.Namespace) -> None:
    if args.skip_receiver_check:
        logging.warning("receiver preflight check skipped")
        return

    check_url = receiver_check_url(args.server_url)
    session = requests.Session()
    session.trust_env = args.use_system_proxy
    last_error = ""
    for attempt in range(1, args.receiver_check_retry + 1):
        try:
            response = session.get(check_url, timeout=args.receiver_check_timeout)
            response.raise_for_status()
            logging.info("receiver reachable checkUrl=%s status=%s", check_url, response.status_code)
            return
        except requests.RequestException as exc:
            last_error = str(exc)
            logging.warning(
                "receiver preflight failed attempt=%s/%s url=%s error=%s",
                attempt,
                args.receiver_check_retry,
                check_url,
                exc,
            )
            time.sleep(min(attempt, 3))

    raise RuntimeError(
        "Receiver is not reachable before startup. "
        f"checkUrl={check_url}; lastError={last_error}. "
        "Confirm the macOS receiver is running at the configured IP and port, "
        "the macOS firewall allows port 8080, and the two hosts can route to each other."
    )


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


def upload(server_url: str, image_path: Path, args: argparse.Namespace, result: str, defect_type: str, confidence: float) -> bool:
    data = {
        "deviceId": args.device_id,
        "result": result,
        "defectType": defect_type,
        "confidence": f"{confidence:.6f}",
        "detectTime": datetime.now().isoformat(timespec="seconds"),
    }
    session = requests.Session()
    session.trust_env = args.use_system_proxy
    for attempt in range(1, args.retry + 1):
        try:
            with image_path.open("rb") as file:
                response = session.post(
                    server_url,
                    data=data,
                    files={"image": (image_path.name, file, "image/jpeg")},
                    timeout=10,
                )
            response.raise_for_status()
            logging.info("upload success result=%s defectType=%s image=%s", result, defect_type, image_path)
            return True
        except requests.RequestException as exc:
            logging.warning("upload failed attempt=%s/%s error=%s", attempt, args.retry, exc)
            time.sleep(min(2 * attempt, 8))
    logging.error("upload failed after %s attempts image=%s", args.retry, image_path)
    return False


def heartbeat(args: argparse.Namespace, model_name: str) -> None:
    if not args.heartbeat_url:
        return
    payload = {
        "hostName": socket.gethostname(),
        "camera": str(args.camera),
        "modelName": model_name,
    }
    try:
        session = requests.Session()
        session.trust_env = args.use_system_proxy
        session.put(args.heartbeat_url, json=payload, timeout=5).raise_for_status()
        logging.info("heartbeat success device=%s", args.device_id)
    except requests.RequestException as exc:
        logging.warning("heartbeat failed error=%s", exc)


def main() -> None:
    args = parse_args()
    configure_logging(Path(args.log_file))
    model_path = Path(args.model)
    if not model_path.exists():
        raise FileNotFoundError(f"Model not found: {model_path}")

    logging.info(
        "sender starting device=%s model=%s server=%s systemProxy=%s",
        args.device_id,
        model_path,
        args.server_url,
        args.use_system_proxy,
    )
    web_state = SenderWebState(args.device_id, args.server_url)
    runtime = SenderRuntime(args, web_state)
    web_server = start_web_server(args, web_state, runtime)
    if args.auto_start or args.no_web:
        runtime.start(payload_config({}, web_state.snapshot()[1]))

    try:
        while True:
            if args.once and not runtime.is_running():
                break
            time.sleep(0.5)
    except KeyboardInterrupt:
        logging.info("sender interrupted")
    finally:
        runtime.stop()
        web_state.update(serverStopping=True, running=False, uploadStatus="stopped")
        if web_server:
            web_server.shutdown()
            web_server.server_close()
        logging.info("sender stopped")


if __name__ == "__main__":
    main()
