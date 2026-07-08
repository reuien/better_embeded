@echo off
setlocal
cd /d "%~dp0\.."
set MAC_RECEIVER_IP=10.84.87.28
set WINDOWS_SENDER_IP=10.84.67.62
if "%RECEIVER_URL%"=="" set RECEIVER_URL=http://%MAC_RECEIVER_IP%:8080/api/detection-records
if "%DEVICE_ID%"=="" set DEVICE_ID=PLC-WIN-01
if "%YOLO_DEVICE%"=="" set YOLO_DEVICE=auto
if "%CAMERA_INDEX%"=="" set CAMERA_INDEX=0
if "%SENDER_WEB_PORT%"=="" set SENDER_WEB_PORT=8090
set HTTP_PROXY=
set HTTPS_PROXY=
set ALL_PROXY=
set NO_PROXY=localhost,127.0.0.1,%MAC_RECEIVER_IP%,%WINDOWS_SENDER_IP%
if "%RECEIVER_URL%"=="http://127.0.0.1:8080/api/detection-records" (
  echo RECEIVER_URL is still 127.0.0.1. Set it to the Mac receiver URL first.
  echo Example: set RECEIVER_URL=http://%MAC_RECEIVER_IP%:8080/api/detection-records
  exit /b 1
)
if "%VIRTUAL_ENV%"=="" if "%CONDA_PREFIX%"=="" if exist sender\.venv\Scripts\activate call sender\.venv\Scripts\activate
echo Using Python:
python -c "import sys; print(sys.executable)"
echo Receiver URL: %RECEIVER_URL%
echo Sender live page: http://127.0.0.1:%SENDER_WEB_PORT%
echo Open the sender page, set receiver IP, then click Test and Start.
python sender\sender.py --model models\best.pt --server-url "%RECEIVER_URL%" --device-id "%DEVICE_ID%" --camera %CAMERA_INDEX% --camera-api dshow --device "%YOLO_DEVICE%" --web-port %SENDER_WEB_PORT%
