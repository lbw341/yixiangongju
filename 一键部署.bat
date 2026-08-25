@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul
title 一线工具平台 - 一键环境部署

set "ROOT=%~dp0"
set "RUNTIME=%ROOT%runtime"
set "JAVA=%RUNTIME%\jdk-21\bin\java.exe"
set "MVN=%RUNTIME%\maven\bin\mvn.cmd"
set "PY=%RUNTIME%\python\python.exe"
set "CACHE=%RUNTIME%\cache"

if not exist "%RUNTIME%" mkdir "%RUNTIME%"
if not exist "%CACHE%"   mkdir "%CACHE%"

echo ============================================
echo   一线工具平台 - 一键环境部署
echo   （首次运行需联网，后续离线可启动）
echo ============================================
echo.

:: ===================== JDK 21 =====================
echo [1/4] 检查 JDK 21...
if exist "%JAVA%" (
    "%JAVA%" -version 2>nul | findstr /C:"21" >nul && (echo   已就绪: JDK 21 & goto :MAVEN)
)
echo   正在下载 JDK 21（约 180MB，请耐心等待）...
set "JDK_URL=https://mirrors.tuna.tsinghua.edu.cn/Adoptium/21/jdk/x64/windows/OpenJDK21U-jdk_x64_windows_hotspot_21.0.12.1_1.zip"
set "JDK_ZIP=%CACHE%\jdk21.zip"
powershell -NoProfile -Command "Invoke-WebRequest -Uri '%JDK_URL%' -OutFile '%JDK_ZIP%' -UseBasicParsing"
if not exist "%JDK_ZIP%" (
    echo   [失败] JDK 下载失败，请检查网络后重试。
    echo   手动安装 JDK 21 后可跳过此步骤。
    goto :MAVEN
)
echo   正在解压...
powershell -NoProfile -Command "Expand-Archive -Path '%JDK_ZIP%' -DestinationPath '%RUNTIME%\jdk21_tmp' -Force"
for /d %%D in ("%RUNTIME%\jdk21_tmp\jdk-*") do rename "%%D" jdk-21 2>nul
if not exist "%RUNTIME%\jdk-21\bin\java.exe" (
    rem 解压后目录名不含版本号的情况，直接移动
    for /d %%D in ("%RUNTIME%\jdk21_tmp\*") do (
        if exist "%%D\bin\java.exe" move "%%D" "%RUNTIME%\jdk-21" >nul
    )
)
rd /s /q "%RUNTIME%\jdk21_tmp" 2>nul
del "%JDK_ZIP%" 2>nul
if exist "%JAVA%" (echo   JDK 21 安装完成) else echo   [警告] JDK 解压异常，后续请确认 JAVA_HOME 或 PATH

:MAVEN
echo [2/4] 检查 Maven...
if exist "%MVN%" (echo   已就绪: Maven & goto :PYTHON)
echo   正在下载 Maven 3.9.16（约 9MB）...
set "MVN_URL=https://mirrors.tuna.tsinghua.edu.cn/apache/maven/maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.zip"
set "MVN_ZIP=%CACHE%\maven.zip"
powershell -NoProfile -Command "Invoke-WebRequest -Uri '%MVN_URL%' -OutFile '%MVN_ZIP%' -UseBasicParsing"
if not exist "%MVN_ZIP%" (echo   [失败] Maven 下载失败 & goto :PYTHON)
echo   正在解压...
powershell -NoProfile -Command "Expand-Archive -Path '%MVN_ZIP%' -DestinationPath '%RUNTIME%\maven_tmp' -Force"
for /d %%D in ("%RUNTIME%\maven_tmp\apache-maven-*") do move "%%D" "%RUNTIME%\maven" >nul
rd /s /q "%RUNTIME%\maven_tmp" 2>nul
del "%MVN_ZIP%" 2>nul
if exist "%MVN%" (echo   Maven 安装完成) else echo   [警告] Maven 解压异常

:PYTHON
echo [3/4] 检查 Python 3.x...
set "HAS_PY=0"
if exist "%PY%" set "HAS_PY=1"
if "%HAS_PY%"=="0" python --version >nul 2>nul && set "HAS_PY=1" && set "PY=python"
if "%HAS_PY%"=="0" py --version >nul 2>nul && set "HAS_PY=1" && set "PY=py"
if "%HAS_PY%"=="1" (
    %PY% --version 2>nul | findstr /C:"Python 3" >nul && (echo   已就绪: !PY! & goto :MYSQL)
)
echo   正在下载 Python 3.13（约 26MB，静默安装中）...
set "PY_URL=https://www.python.org/ftp/python/3.13.15/python-3.13.15-amd64.exe"
set "PY_EXE=%CACHE%\python-setup.exe"
powershell -NoProfile -Command "Invoke-WebRequest -Uri '%PY_URL%' -OutFile '%PY_EXE%' -UseBasicParsing"
if not exist "%PY_EXE%" (echo   [失败] Python 下载失败 & goto :MYSQL)
"%PY_EXE%" /quiet InstallAllUsers=0 PrependPath=1 Include_pip=1 Include_test=0
timeout /t 10 >nul
set "PY=python"
if exist "%RUNTIME%\python\python.exe" set "PY=%RUNTIME%\python\python.exe"
echo   Python 安装完成

:MYSQL
echo [4/4] 检查 MySQL...
sc query MySQL >nul 2>nul && goto :MYSQL_RUNNING
sc query MySQL57 >nul 2>nul && goto :MYSQL_RUNNING
sc query MySQL80 >nul 2>nul && goto :MYSQL_RUNNING
echo   [提示] 未检测到 MySQL 服务。
echo   请先安装 MySQL（推荐 5.7 或 8.0）并确保服务已启动。
echo   安装地址: https://dev.mysql.com/downloads/mysql/
echo.
set /p WAITMYSQL=安装好 MySQL 后按回车继续...
goto :MYSQL

:MYSQL_RUNNING
echo   MySQL 服务已启动

echo.
echo ============================================
echo   环境检查完成
echo ============================================
echo.

:: ---- 数据库初始化 ----
echo 正在检查数据库账号...
set "DBOK=0"
setlocal
set "ROOTPWD="
set /p ROOTPWD=请输入 MySQL root 密码（若已初始化过可直接回车）: 
if "%ROOTPWD%"=="" (set "DBOK=1" & goto :LAUNCH)
echo 正在初始化账号...
where mysql >nul 2>nul
if errorlevel 1 (
    set /p MYSQLBIN=请输入 mysql.exe 所在目录: 
    set "PATH=%MYSQLBIN%;%PATH%"
)
mysql -u root -p%ROOTPWD% --default-character-set=utf8mb4 < "%ROOT%database\init_user.sql"
if errorlevel 1 (
    echo [警告] 数据库账号初始化失败（可能已存在），继续尝试启动...
) else (
    echo 数据库账号初始化完成
)
endlocal

:LAUNCH
:: 设置运行时路径
set "PATH=%RUNTIME%\jdk-21\bin;%RUNTIME%\maven\bin;%PATH%"
cd /d "%ROOT%backend-java"

echo ============================================
echo   正在启动平台...
echo   首次启动将自动建库建表（约30秒）
echo   看到 Started 后访问: http://localhost:5000
echo   管理员账号: admin / 123456
echo ============================================
echo.
call mvn spring-boot:run
pause
