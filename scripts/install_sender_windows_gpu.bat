@echo off
setlocal
cd /d "%~dp0\.."
set PY_CMD=py -3.11
%PY_CMD% --version >nul 2>nul
if errorlevel 1 set PY_CMD=python
%PY_CMD% --version >nul 2>nul
if errorlevel 1 (
  echo Python 3.10+ is required. Please install Python from https://www.python.org/downloads/windows/
  exit /b 1
)
%PY_CMD% -m venv sender\.venv
call sender\.venv\Scripts\activate
python -m pip install --upgrade pip
echo Install CUDA-enabled PyTorch from https://pytorch.org/get-started/locally/
echo Then run:
echo pip install -r sender\requirements.txt
echo.
echo After that set YOLO_DEVICE=0 before running scripts\run_sender_windows.bat
