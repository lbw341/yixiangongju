@echo off
cd /d "%~dp0"
echo ============================================
echo   Tool Platform - Python Backend
echo ============================================
echo.
echo Checking Python...
python --version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Python not found.
    pause
    exit /b 1
)
echo Python OK
echo.
cd backend
echo Installing deps...
pip install -r requirements.txt -q
echo.
echo ============================================
echo   http://localhost:5000
echo   admin / 123456
echo   Ctrl+C to stop
echo ============================================
echo.
python app.py
echo.
echo Stopped.
pause
