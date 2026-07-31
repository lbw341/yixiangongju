@echo off
chcp 65001 >nul
title 一线工具平台 - 前端启动

cd /d "%~dp0"

echo [前端开发模式]
echo.
echo 步骤1: 检查依赖...
if not exist "node_modules" (
    echo 首次运行，安装依赖中...
    call npm install
    if %errorlevel% neq 0 (
        echo [错误] 依赖安装失败
        pause
        exit /b 1
    )
)

echo.
echo 步骤2: 构建前端...
call npm run build
if %errorlevel% neq 0 (
    echo [错误] 构建失败
    pause
    exit /b 1
)

echo.
echo 前端构建完成！
echo.
echo 如果需要启动后端，请使用根目录的 start.bat
echo.
pause
