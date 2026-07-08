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
pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cpu
pip install -r sender\requirements.txt
python -c "import torch; print('torch', torch.__version__); print('cuda available:', torch.cuda.is_available())"
echo.
echo Sender CPU environment is ready.
