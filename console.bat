@echo off
chcp 65001 >nul
title 一线工具平台 - 控制台

echo ============================================
echo     一线工具平台 控制台
echo ============================================
echo.

:MENU
cls
echo ============================================
echo     一线工具平台 控制台
echo ============================================
echo.
echo   [1] 启动服务
echo   [2] 停止服务
echo   [3] 重启服务
echo   [4] 查看服务状态
echo   [5] 查看日志
echo   [6] 打开浏览器
echo   [7] 构建项目
echo   [0] 退出
echo.

choice /c 12345670 /n /m "请选择操作: "

if errorlevel 8 goto END
if errorlevel 7 goto BUILD
if errorlevel 6 goto OPEN_BROWSER
if errorlevel 5 goto VIEW_LOG
if errorlevel 4 goto CHECK_STATUS
if errorlevel 3 goto RESTART
if errorlevel 2 goto STOP
if errorlevel 1 goto START

:START
cls
echo --- 启动服务 ---
call "%~dp0start.bat"
pause
goto MENU

:STOP
cls
echo --- 停止服务 ---
call "%~dp0stop.bat"
pause
goto MENU

:RESTART
cls
echo --- 重启服务 ---
echo 正在停止服务...
call "%~dp0stop.bat"
timeout /t 2 /nobreak >nul
echo.
echo 正在启动服务...
call "%~dp0start.bat"
pause
goto MENU

:CHECK_STATUS
cls
echo --- 服务状态 ---
echo.
netstat -ano | findstr :5000
if %errorlevel% equ 0 (
    echo.
    echo 服务正在运行
) else (
    echo.
    echo 服务未运行
)
echo.
pause
goto MENU

:VIEW_LOG
cls
echo --- 查看日志 (最近 50 行) ---
echo.
if exist "%~dp0backend-java\app.log" (
    powershell -Command "Get-Content '%~dp0backend-java\app.log' -Tail 50"
) else (
    echo 日志文件不存在
)
echo.
pause
goto MENU

:OPEN_BROWSER
cls
echo --- 打开浏览器 ---
start http://localhost:5000
echo 已尝试打开 http://localhost:5000
echo 如果页面无法访问，请先启动服务
echo.
timeout /t 2 /nobreak >nul
goto MENU

:BUILD
cls
echo --- 构建项目 ---
echo.
cd /d "%~dp0backend-java"
call mvn clean package -DskipTests
echo.
pause
goto MENU

:END
cls
echo 再见！
timeout /t 2 /nobreak >nul
exit /b 0
