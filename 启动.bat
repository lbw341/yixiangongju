@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul
set "ROOT=%~dp0"

:: 优先使用 runtime 内的 JDK/Maven
if exist "%ROOT%runtime\jdk-21\bin\java.exe" set "PATH=%ROOT%runtime\jdk-21\bin;%PATH%"
if exist "%ROOT%runtime\maven\bin\mvn.cmd"    set "PATH=%ROOT%runtime\maven\bin;%PATH%"
if exist "%ROOT%runtime\python\python.exe"    set "PATH=%ROOT%runtime\python;%PATH%"

echo ============================================
echo   一线工具平台 - 启动
echo ============================================
where java >nul 2>nul || (echo [错误] 未找到 java，请先运行「一键部署.bat」& pause & exit /b 1)
where mvn  >nul 2>nul || (echo [错误] 未找到 mvn，请先运行「一键部署.bat」& pause & exit /b 1)

cd /d "%ROOT%backend-java"
echo 启动成功后请访问: http://localhost:5000
echo.
call mvn spring-boot:run
pause
