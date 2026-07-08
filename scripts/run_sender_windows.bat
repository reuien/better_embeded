@echo off
setlocal
cd /d "%~dp0\.."
if "%RECEIVER_URL%"=="" set RECEIVER_URL=http://127.0.0.1:8080/api/detection-records
if "%DEVICE_ID%"=="" set DEVICE_ID=%COMPUTERNAME%
if "%YOLO_DEVICE%"=="" set YOLO_DEVICE=auto
if "%CAMERA_INDEX%"=="" set CAMERA_INDEX=0
if "%SENDER_WEB_PORT%"=="" set SENDER_WEB_PORT=8090
if exist sender\.venv\Scripts\activate call sender\.venv\Scripts\activate
python sender\sender.py --model models\best.pt --server-url "%RECEIVER_URL%" --device-id "%DEVICE_ID%" --camera %CAMERA_INDEX% --camera-api dshow --device "%YOLO_DEVICE%" --web-port %SENDER_WEB_PORT% --preview
