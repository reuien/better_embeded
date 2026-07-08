# PLC Defect Detection System

This project follows `code.md`.

- `sender/`: Windows Python sender. Captures camera frames, runs `models/best.pt`, saves local images, and uploads `multipart/form-data`.
- `receiver/`: macOS Spring Boot receiver. Receives images, stores SQLite records, writes logs, serves APIs, and serves the built frontend.
- `front/`: Vue3 + TypeScript + Vite + Element Plus + Axios + ECharts dashboard.

## Build the Web Dashboard

```bash
cd front
npm install
npm run build
cd ..
```

The Spring Boot receiver serves `front/dist`.

## Run macOS Receiver

```bash
cd receiver
mvn spring-boot:run
```

Open:

```text
http://127.0.0.1:8080
```

Find the Mac LAN IP for the Windows sender:

```bash
ipconfig getifaddr en0
```

Upload endpoint for Windows sender:

```text
http://<mac-ip>:8080/api/detection-records
```

## Run Windows Sender

Install CPU dependencies once:

```powershell
cd C:\mos
.\scripts\install_sender_windows_cpu.bat
```

Run sender:

```powershell
cd C:\mos
$env:RECEIVER_URL="http://<mac-ip>:8080/api/detection-records"
$env:DEVICE_ID="PLC-WIN-01"
$env:YOLO_DEVICE="cpu"
.\scripts\run_sender_windows.bat
```

CPU mode is enough when the Windows host does not have an NVIDIA GPU or CUDA PyTorch installed.

For NVIDIA GPU, install CUDA PyTorch from:

```text
https://pytorch.org/get-started/locally/
```

Then set:

```powershell
$env:YOLO_DEVICE="0"
```

## REST APIs

- `POST /api/detection-records`
- `GET /api/detection-records`
- `GET /api/detection-records/{id}`
- `DELETE /api/detection-records/{id}`
- `GET /api/images/{filename}`
- `GET /api/logs`
- `GET /api/statistics`
- `GET /api/devices`
- `PUT /api/devices/{deviceId}/heartbeat`
- `GET /api/system-status`
