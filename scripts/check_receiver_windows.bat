@echo off
setlocal
cd /d "%~dp0\.."
set MAC_RECEIVER_IP=192.168.32.197
set WINDOWS_SENDER_IP=192.168.32.249
set CHECK_URL=http://%MAC_RECEIVER_IP%:8080/api/system-status
set HTTP_PROXY=
set HTTPS_PROXY=
set ALL_PROXY=
set NO_PROXY=localhost,127.0.0.1,%MAC_RECEIVER_IP%,%WINDOWS_SENDER_IP%

echo Windows sender IP expected: %WINDOWS_SENDER_IP%
echo macOS receiver IP expected: %MAC_RECEIVER_IP%
echo Check URL: %CHECK_URL%
echo.
echo Active IPv4 addresses:
ipconfig | findstr /R /C:"IPv4"
echo.
echo Route to macOS receiver:
route print %MAC_RECEIVER_IP%
echo.
echo TCP port test:
powershell -NoProfile -ExecutionPolicy Bypass -Command "Test-NetConnection %MAC_RECEIVER_IP% -Port 8080 -InformationLevel Detailed"
echo.
echo HTTP test without proxy:
python -c "import urllib.request; opener=urllib.request.build_opener(urllib.request.ProxyHandler({})); r=opener.open('%CHECK_URL%', timeout=5); print('HTTP', r.status); print(r.read(500).decode('utf-8', 'ignore'))"
if errorlevel 1 (
  echo.
  echo FAILED: Windows cannot reach the macOS receiver HTTP endpoint.
  echo Check macOS receiver, router/client isolation, VPN, and firewall settings.
  exit /b 1
)
echo.
echo OK: Windows can reach the macOS receiver.
