@echo off
chcp 65001 >nul
title 一线工具平台 - 启动器

echo ============================================
echo        一线工具平台 启动器
echo ============================================
echo.

REM 检查 Java 环境
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 未检测到 Java 环境，请先安装 JDK 21
    echo 下载地址: https://adoptium.net/
    pause
    exit /b 1
)

REM 获取当前目录
set BASE_DIR=%~dp0
set APP_DIR=%BASE_DIR%backend-java
set APP_NAME=tool-platform-1.0.0.jar

REM 检查是否已构建
if not exist "%APP_DIR%\target\%APP_NAME%" (
    echo [提示] 未找到 jar 包，正在构建项目...
    cd /d "%APP_DIR%"
    call mvn clean package -DskipTests -q
    if %errorlevel% neq 0 (
        echo [错误] 项目构建失败
        pause
        exit /b 1
    )
    echo [完成] 项目构建成功
    echo.
)

REM 检查端口占用
netstat -ano | findstr :5000 >nul 2>&1
if %errorlevel% equ 0 (
    echo [警告] 端口 5000 已被占用
    choice /c YN /m "是否强制停止占用进程"
    if errorlevel 2 (
        echo [取消] 启动已取消
        pause
        exit /b 0
    )
    echo 正在停止占用进程...
    for /f "tokens=5" %%a in ('netstat -ano ^| findstr :5000 ^| findstr LISTENING') do (
        taskkill /PID %%a /F >nul 2>&1
    )
    timeout /t 2 /nobreak >nul
)

REM 创建必要目录
if not exist "%APP_DIR%\uploads\templates" mkdir "%APP_DIR%\uploads\templates"
if not exist "%APP_DIR%\uploads\results" mkdir "%APP_DIR%\uploads\results"
if not exist "%APP_DIR%\data" mkdir "%APP_DIR%\data"

REM 启动应用
echo [启动] 正在启动服务...
cd /d "%APP_DIR%"
start "一线工具平台服务" /B java -Xms512m -Xmx2048m -jar target\%APP_NAME% > app.log 2>&1

REM 等待启动
echo [等待] 正在等待服务启动...
timeout /t 5 /nobreak >nul

REM 检查是否启动成功
netstat -ano | findstr :5000 >nul 2>&1
if %errorlevel% equ 0 (
    echo.
    echo ============================================
    echo   服务启动成功！
    echo   访问地址: http://localhost:5000
    echo   默认账号: admin
    echo   默认密码: 123456
    echo ============================================
    echo.
    echo 按任意键打开浏览器...
    pause >nul
    start http://localhost:5000
) else (
    echo.
    echo [错误] 服务启动失败，请查看日志:
    echo   %APP_DIR%\app.log
    echo.
    type "%APP_DIR%\app.log" | findstr /i "error exception"
    pause
)
