@echo off
setlocal EnableExtensions EnableDelayedExpansion

cd /d "%~dp0"

set "APP_ID=com.example.pocastcloni"
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "ANDROID_SDK_ROOT=C:\Users\jemi\AppData\Local\Android\Sdk"
set "ANDROID_HOME=%ANDROID_SDK_ROOT%"
set "PATH=%JAVA_HOME%\bin;%ANDROID_SDK_ROOT%\platform-tools;%ANDROID_SDK_ROOT%\emulator;%PATH%"

set "ADB=%ANDROID_SDK_ROOT%\platform-tools\adb.exe"
set "EMULATOR=%ANDROID_SDK_ROOT%\emulator\emulator.exe"
set "SLEEP_CMD=powershell -NoProfile -ExecutionPolicy Bypass -Command Start-Sleep -Seconds"
set "STOP_EMULATORS_CMD=Get-Process | Where-Object { $_.ProcessName -like 'emulator' -or $_.ProcessName -like 'qemu-system-*' } | Stop-Process -Force"
set "UNINSTALL_LOG=%TEMP%\pocastcloni-uninstall.txt"
set "STARTED_EMULATOR=0"

if /I "%~1"=="help" goto :help
if /I "%~1"=="--help" goto :help
if /I "%~1"=="-h" goto :help
if /I "%~1"=="list" goto :list_avds

if not exist "%ADB%" (
    echo FEHLER: adb nicht gefunden
    echo   %ADB%
    goto :error_exit
)

if not exist "%EMULATOR%" (
    echo FEHLER: emulator.exe nicht gefunden
    echo   %EMULATOR%
    goto :error_exit
)

set "TARGET_SERIAL="
set "TARGET_AVD=%~1"
set "STALE_EMULATOR_FOUND=0"

if not "%TARGET_AVD%"=="" goto :ensure_device

for /f "skip=1 tokens=1,2" %%D in ('"%ADB%" devices') do (
    if not "%%D"=="" (
        echo %%D | findstr /R "^emulator-" >nul
        if not errorlevel 1 (
            if /I "%%E"=="device" (
                set "TARGET_SERIAL=%%D"
                goto :device_ready
            )
            if /I "%%E"=="offline" (
                set "STALE_EMULATOR_FOUND=1"
            )
        )
    )
)

for /f "delims=" %%A in ('"%EMULATOR%" -list-avds') do (
    if not "%%A"=="" (
        set "TARGET_AVD=%%A"
        goto :ensure_device
    )
)

echo FEHLER: Kein Android-Emulator verfuegbar
goto :error_exit

:ensure_device
if not "%TARGET_SERIAL%"=="" goto :device_ready

if "%STALE_EMULATOR_FOUND%"=="1" (
    echo Offline-Emulator erkannt. Alte Emulator-Prozesse werden beendet...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "%STOP_EMULATORS_CMD%"
    %SLEEP_CMD% 3
)

echo Starte Emulator: %TARGET_AVD%
set "STARTED_EMULATOR=1"
start "Android Emulator - %TARGET_AVD%" "%EMULATOR%" -avd "%TARGET_AVD%" -no-snapshot

echo Warte auf Emulator...
set /a WAIT_SECONDS=0
:wait_for_serial
set "TARGET_SERIAL="
for /f "skip=1 tokens=1,2" %%D in ('"%ADB%" devices') do (
    if not "%%D"=="" (
        echo %%D | findstr /R "^emulator-" >nul
        if not errorlevel 1 (
            set "TARGET_SERIAL=%%D"
            goto :wait_for_boot
        )
    )
)

set /a WAIT_SECONDS+=2
if %WAIT_SECONDS% GEQ 180 (
    echo FEHLER: Emulator taucht nicht in adb auf
    call :shutdown_emulator_if_started
    goto :error_exit
)
%SLEEP_CMD% 2
goto :wait_for_serial

:wait_for_boot
set /a BOOT_WAIT=0
:boot_loop
set "DEVICE_STATE="
for /f "usebackq delims=" %%S in (`"%ADB%" -s %TARGET_SERIAL% get-state 2^>nul`) do (
    set "DEVICE_STATE=%%S"
)

if /I not "!DEVICE_STATE!"=="device" goto :boot_wait

set "BOOT_STATE="
for /f "usebackq delims=" %%B in (`"%ADB%" -s %TARGET_SERIAL% shell getprop sys.boot_completed 2^>nul`) do (
    set "BOOT_STATE=%%B"
)

if "!BOOT_STATE!"=="1" goto :device_ready

:boot_wait
set /a BOOT_WAIT+=3
if !BOOT_WAIT! GEQ 240 (
    echo FEHLER: Android bootet nicht fertig
    call :shutdown_emulator_if_started
    goto :error_exit
)
%SLEEP_CMD% 3
goto :boot_loop

:device_ready
echo Verwende Geraet: %TARGET_SERIAL%

if exist "%UNINSTALL_LOG%" del /f /q "%UNINSTALL_LOG%" >nul 2>nul
"%ADB%" -s %TARGET_SERIAL% uninstall %APP_ID% > "%UNINSTALL_LOG%" 2>&1
set "UNINSTALL_EXIT=%ERRORLEVEL%"

set "UNINSTALL_RESULT="
for /f "usebackq delims=" %%L in ("%UNINSTALL_LOG%") do (
    set "UNINSTALL_RESULT=%%L"
)

if "%UNINSTALL_EXIT%"=="0" (
    echo OK: App wurde vom Emulator deinstalliert
    echo   %APP_ID%
    call :shutdown_emulator_if_started
    exit /b 0
)

findstr /I /C:"Unknown package" "%UNINSTALL_LOG%" >nul
if not errorlevel 1 (
    echo OK: App war auf dem Emulator nicht installiert
    echo   %APP_ID%
    call :shutdown_emulator_if_started
    exit /b 0
)

findstr /I /C:"not installed for" "%UNINSTALL_LOG%" >nul
if not errorlevel 1 (
    echo OK: App war auf dem Emulator nicht installiert
    echo   %APP_ID%
    call :shutdown_emulator_if_started
    exit /b 0
)

echo FEHLER: Deinstallation fehlgeschlagen
if not "%UNINSTALL_RESULT%"=="" echo   %UNINSTALL_RESULT%
call :shutdown_emulator_if_started
goto :error_exit

:shutdown_emulator_if_started
if /I not "%STARTED_EMULATOR%"=="1" exit /b 0
if "%TARGET_SERIAL%"=="" exit /b 0

echo Schließe gestarteten Emulator wieder...
"%ADB%" -s %TARGET_SERIAL% emu kill >nul 2>&1
%SLEEP_CMD% 3
exit /b 0

:error_exit
echo.
pause
exit /b 1

:list_avds
echo Available AVDs:
"%EMULATOR%" -list-avds
echo.
pause
exit /b 0

:help
echo Usage:
echo   %~nx0
echo   %~nx0 list
echo   %~nx0 AVD_NAME
echo.
echo Was das Skript macht:
echo   1. Emulator bereitstellen
echo   2. App deinstallieren
echo   3. Sauberen Ausgangszustand fuer einen Neu-Install schaffen
echo   4. Falls vom Skript gestartet, Emulator danach wieder schließen
echo.
pause
exit /b 0
