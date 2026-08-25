@echo off
chcp 65001 >nul
title Tool Platform - One-Click Deploy

set "ROOT=%~dp0"
set "RUNTIME=%ROOT%runtime"
set "JAVA=%RUNTIME%\jdk-21\bin\java.exe"
set "MVN=%RUNTIME%\maven\bin\mvn.cmd"
set "PY=%RUNTIME%\python\python.exe"
set "CACHE=%RUNTIME%\cache"

if not exist "%RUNTIME%" mkdir "%RUNTIME%"
if not exist "%CACHE%"   mkdir "%CACHE%"

echo ============================================
echo   Tool Platform - One-Click Deploy
echo   (First run will download dependencies)
echo ============================================
echo.

:: ===================== JDK 21 =====================
echo [1/4] Checking JDK 21...
if exist "%JAVA%" (
    "%JAVA%" -version 2>nul | findstr /C:"21" >nul && (echo   Already installed: JDK 21 & goto :MAVEN)
)
echo   Downloading JDK 21 (~196MB, please wait)...
set "JDK_URL=https://mirrors.tuna.tsinghua.edu.cn/Adoptium/21/jdk/x64/windows/OpenJDK21U-jdk_x64_windows_hotspot_21.0.12.1_1.zip"
set "JDK_ZIP=%CACHE%\jdk21.zip"
powershell -NoProfile -Command "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; (New-Object Net.WebClient).DownloadFile('%JDK_URL%','%JDK_ZIP%')"
if not exist "%JDK_ZIP%" (
    echo   [FAIL] JDK download failed, please check network.
    echo   Please install JDK 21 manually and run start.bat
    goto :MAVEN
)
echo   Extracting...
powershell -NoProfile -Command "Expand-Archive -Path '%JDK_ZIP%' -DestinationPath '%RUNTIME%\jdk21_tmp' -Force"
for /d %%D in ("%RUNTIME%\jdk21_tmp\jdk-*") do rename "%%D" jdk-21 2>nul
if not exist "%RUNTIME%\jdk-21\bin\java.exe" (
    for /d %%D in ("%RUNTIME%\jdk21_tmp\*") do (
        if exist "%%D\bin\java.exe" move "%%D" "%RUNTIME%\jdk-21" >nul
    )
)
rd /s /q "%RUNTIME%\jdk21_tmp" 2>nul
del "%JDK_ZIP%" 2>nul
if exist "%JAVA%" (echo   JDK 21 installed) else echo   [WARN] JDK extraction error

:MAVEN
echo [2/4] Checking Maven...
if exist "%MVN%" (echo   Already installed: Maven & goto :PYTHON)
echo   Downloading Maven 3.9.16 (~9MB)...
set "MVN_URL=https://mirrors.tuna.tsinghua.edu.cn/apache/maven/maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.zip"
set "MVN_ZIP=%CACHE%\maven.zip"
powershell -NoProfile -Command "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; (New-Object Net.WebClient).DownloadFile('%MVN_URL%','%MVN_ZIP%')"
if not exist "%MVN_ZIP%" (echo   [FAIL] Maven download failed & goto :PYTHON)
echo   Extracting...
powershell -NoProfile -Command "Expand-Archive -Path '%MVN_ZIP%' -DestinationPath '%RUNTIME%\maven_tmp' -Force"
for /d %%D in ("%RUNTIME%\maven_tmp\apache-maven-*") do move "%%D" "%RUNTIME%\maven" >nul
rd /s /q "%RUNTIME%\maven_tmp" 2>nul
del "%MVN_ZIP%" 2>nul
if exist "%MVN%" (echo   Maven installed) else echo   [WARN] Maven extraction error

:PYTHON
echo [3/4] Checking Python 3.x...
set "HAS_PY=0"
if exist "%PY%" set "HAS_PY=1"
if "%HAS_PY%"=="0" python --version >nul 2>nul && set "HAS_PY=1" && set "PY=python"
if "%HAS_PY%"=="0" py --version >nul 2>nul && set "HAS_PY=1" && set "PY=py"
if "%HAS_PY%"=="1" (
    %PY% --version 2>nul | findstr /C:"Python 3" >nul && (echo   Already installed: Python & goto :MYSQL)
)
echo   Downloading Python 3.13 (~28MB, default install)...
set "PY_URL=https://www.python.org/ftp/python/3.13.15/python-3.13.15-amd64.exe"
set "PY_EXE=%CACHE%\python-setup.exe"
powershell -NoProfile -Command "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; (New-Object Net.WebClient).DownloadFile('%PY_URL%','%PY_EXE%')"
if not exist "%PY_EXE%" (echo   [FAIL] Python download failed & goto :MYSQL)
"%PY_EXE%" /quiet InstallAllUsers=0 PrependPath=1 Include_pip=1 Include_test=0
timeout /t 15 >nul
echo   Python installed

:MYSQL
echo [4/4] Checking MySQL...
sc query MySQL >nul 2>nul && goto :MYSQL_RUNNING
sc query MySQL57 >nul 2>nul && goto :MYSQL_RUNNING
sc query MySQL80 >nul 2>nul && goto :MYSQL_RUNNING
echo   [INFO] MySQL service not detected.
echo   Please install MySQL (5.7 or 8.0) and ensure it is running.
echo   Download: https://dev.mysql.com/downloads/mysql/
echo.
set /p WAITMYSQL=Press Enter after installing MySQL...
goto :MYSQL

:MYSQL_RUNNING
echo   MySQL service is running.
echo.

:: ---- Database Init ----
echo Enter MySQL root password (press Enter to skip if already initialized):
set /p ROOTPWD=^>
if "%ROOTPWD%"=="" goto :LAUNCH
echo Initializing database account...
where mysql >nul 2>nul
if errorlevel 1 (
    set /p MYSQLBIN=Enter mysql.exe directory: 
    set "PATH=%MYSQLBIN%;%PATH%"
)
mysql -u root -p%ROOTPWD% --default-character-set=utf8mb4 < "%ROOT%database\init_user.sql"
if errorlevel 1 (
    echo [WARN] Database init failed (account may already exist), continuing...
) else (
    echo Database account initialized successfully.
)

:LAUNCH
set "PATH=%RUNTIME%\jdk-21\bin;%RUNTIME%\maven\bin;%PATH%"
cd /d "%ROOT%backend-java"
echo ============================================
echo   Starting Tool Platform...
echo   First run will auto-download deps (~30s)
echo   After "Started" visit: http://localhost:5000
echo   Admin account: admin / 123456
echo ============================================
echo.
call mvn spring-boot:run
pause
