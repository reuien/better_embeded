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
./scripts/run_receiver_mac.sh
```

Open:

```text
http://127.0.0.1:8080
http://10.84.87.28:8080
```

Find the Mac LAN IP for the Windows sender:

```bash
ipconfig getifaddr en0
```

Current deployment addresses:

```text
Windows sender: 10.84.67.62
macOS receiver: 10.84.87.28
```

Upload endpoint for Windows sender:

```text
http://10.84.87.28:8080/api/detection-records
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
$env:DEVICE_ID="PLC-WIN-01"
$env:YOLO_DEVICE="cpu"
.\scripts\run_sender_windows.bat
```

Open the sender live preview page on the Windows host:

```text
http://127.0.0.1:8090
```

In the sender page:

- Set `macOS receiver IP` to `10.84.87.28`.
- Set `receiver port` to `8080`.
- Click `Test`. It calls `http://10.84.87.28:8080/api/system-status` without system proxy.
- Click `Start` only after the test is reachable.

If `Test` fails, start the macOS receiver first, allow port `8080` in macOS firewall, and check that the WiFi/router allows traffic between `10.84.67.62` and `10.84.87.28`.

The receiver dashboard shows uploaded history and saved result images. The sender live page shows the current camera inference frame, model result, confidence, local image path, and upload status.

CPU mode is enough when the Windows host does not have an NVIDIA GPU or CUDA PyTorch installed.

If upload logs mention `127.0.0.1:7890`, Windows system proxy is intercepting the request. The sender disables system proxy by default. For old copies of the sender, clear proxy variables before running:

```powershell
$env:HTTP_PROXY=""
$env:HTTPS_PROXY=""
$env:ALL_PROXY=""
$env:NO_PROXY="*"
```

For NVIDIA GPU, install CUDA PyTorch from:

```text
https://pytorch.org/get-started/locally/
```

For CUDA 12.1, the typical commands are:

```powershell
conda activate sender
pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu121
pip install -r sender\requirements.txt
python -c "import torch; print(torch.__version__); print(torch.cuda.is_available()); print(torch.cuda.get_device_name(0))"
```

Then set:

```powershell
$env:YOLO_DEVICE="0"
```

If `import cv2` fails, install the sender dependencies in the same environment that runs the sender:

```powershell
pip install -r sender\requirements.txt
python -c "import sys, cv2, torch; print(sys.executable); print(cv2.__version__); print(torch.cuda.is_available())"
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
