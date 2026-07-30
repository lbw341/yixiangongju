@echo off
chcp 65001 >nul
title 一线工具平台 - 停止器

echo ============================================
echo        一线工具平台 停止器
echo ============================================
echo.

REM 查找占用 5000 端口的进程
echo [查找] 正在查找占用端口 5000 的进程...
set PID=
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :5000 ^| findstr LISTENING') do (
    set PID=%%a
)

if "%PID%"=="" (
    echo [提示] 服务未运行
    pause
    exit /b 0
)

echo [发现] 发现占用进程 PID: %PID%
choice /c YN /m "是否停止该进程"
if errorlevel 2 (
    echo [取消] 操作已取消
    pause
    exit /b 0
)

REM 停止进程
echo [停止] 正在停止服务...
taskkill /PID %PID% /F

if %errorlevel% equ 0 (
    echo [完成] 服务已停止
) else (
    echo [错误] 停止服务失败
)

timeout /t 2 /nobreak >nul

REM 验证
netstat -ano | findstr :5000 >nul 2>&1
if %errorlevel% neq 0 (
    echo [验证] 端口 5000 已释放
) else (
    echo [警告] 端口仍被占用，可能需要手动重启
)

echo.
pause
