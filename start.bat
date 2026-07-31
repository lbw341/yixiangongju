@echo off
chcp 65001 >nul
title Tool Platform

echo ========================================
echo    Tool Platform - One-Click Start
echo ========================================
echo.

cd /d "%~dp0"

echo [1/4] Checking environment...

where node >nul 2>nul
if %errorlevel% neq 0 (
    echo [ERROR] Node.js not found, please install Node.js 18+
    echo Download: https://nodejs.org/
    goto :error
)

where mvn >nul 2>nul
if %errorlevel% neq 0 (
    echo [ERROR] Maven not found, please install Maven 3.8+
    echo Download: https://maven.apache.org/
    goto :error
)

where java >nul 2>nul
if %errorlevel% neq 0 (
    echo [ERROR] JDK not found, please install JDK 21
    echo Download: https://adoptium.net/
    goto :error
)

echo Environment OK
echo.

echo [2/4] Building frontend...
cd frontend

if not exist "node_modules" (
    echo Installing dependencies...
    call npm install
    if %errorlevel% neq 0 (
        echo [ERROR] npm install failed
        goto :error
    )
)

call npm run build
if %errorlevel% neq 0 (
    echo [ERROR] Frontend build failed
    goto :error
)
echo Frontend built ^(output to backend static^)
cd ..
echo.

echo [3/4] Building backend...

for /f "tokens=5" %%a in ('netstat -ano ^| findstr :5000 ^| findstr LISTENING') do (
    echo   Killing old process PID: %%a
    taskkill /F /PID %%a >nul 2>nul
)

cd backend-java

if not exist "target\classes" (
    echo Compiling backend...
    call mvn clean compile -q
    if %errorlevel% neq 0 (
        echo [ERROR] Backend compile failed
        goto :error
    )
)
echo Backend ready
cd ..
echo.

echo [4/4] Starting backend service...
echo.
echo ========================================
echo   Starting... Please wait...
echo   Browser will open automatically
echo   Press Ctrl+C to stop
echo ========================================
echo.

cd backend-java
start "ToolPlatform" /B mvn spring-boot:run
cd ..

echo Waiting for backend to start...
setlocal enabledelayedexpansion
set /a retry=0
:wait_loop
set /a retry+=1
if !retry! gtr 30 (
    echo [WARN] Timeout waiting for service, opening browser anyway...
    goto :open_browser
)
ping -n 2 127.0.0.1 >nul
netstat -ano | findstr ":5000" | findstr "LISTENING" >nul 2>nul
if errorlevel 1 (
    echo   Waiting... ^(!retry!/30^)
    goto :wait_loop
)
echo   Service is ready!
:open_browser
endlocal

start "" "http://localhost:5000"

echo.
echo ========================================
echo   System started!
echo   URL: http://localhost:5000
echo   Admin: admin / 123456
echo ========================================
echo.
echo Window will stay open. Press Ctrl+C to stop.
echo.
pause
goto :eof

:error
echo.
echo ========================================
echo   FAILED - See error above
echo ========================================
echo.
pause
exit /b 1
