@echo off
cd /d "%~dp0backend-java"
echo ============================================
echo   Tool Platform - Java Backend
echo ============================================
echo.
echo Checking Java...
java -version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Java not found. Please install Java 17+
    pause
    exit /b 1
)
echo Java OK
echo.
echo Building...
call mvn package -DskipTests -q
if errorlevel 1 (
    echo Build failed!
    pause
    exit /b 1
)
echo.
echo ============================================
echo   http://localhost:5000
echo   admin / 123456
echo   Ctrl+C to stop
echo ============================================
echo.
java -jar target\tool-platform-1.0.0.jar
echo.
echo Stopped.
pause
