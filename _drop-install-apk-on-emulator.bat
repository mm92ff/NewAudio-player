@echo off
setlocal EnableExtensions EnableDelayedExpansion

cd /d "%~dp0"

:: -----------------------------------------------------------------------
:: Paths
:: -----------------------------------------------------------------------
set "APP_ID=com.example.pocastcloni"
set "MAIN_ACTIVITY=%APP_ID%.ui.main.MainActivity"
set "ANDROID_SDK_ROOT=C:\Users\jemi\AppData\Local\Android\Sdk"
set "ADB=%ANDROID_SDK_ROOT%\platform-tools\adb.exe"
set "EMULATOR=%ANDROID_SDK_ROOT%\emulator\emulator.exe"
set "SLEEP_CMD=powershell -NoProfile -ExecutionPolicy Bypass -Command Start-Sleep -Seconds"
set "STOP_EMULATORS_CMD=Get-Process | Where-Object { $_.ProcessName -like 'emulator' -or $_.ProcessName -like 'qemu-system-*' } | Stop-Process -Force"

:: -----------------------------------------------------------------------
:: APK argument (supports drag-and-drop onto the .bat file)
:: -----------------------------------------------------------------------
set "APK_PATH=%~1"

if "!APK_PATH!"=="" (
    echo.
    echo Usage: Drag and drop an APK onto this script, or run:
    echo   %~nx0 path\to\your.apk
    echo.
    pause
    exit /b 1
)

if not exist "!APK_PATH!" (
    echo.
    echo ERROR: APK not found:
    echo   !APK_PATH!
    echo.
    pause
    exit /b 1
)

echo.
echo ================================================
echo   PocastCloni - Install APK on Emulator
echo ================================================
echo.
echo APK: !APK_PATH!
echo.

:: -----------------------------------------------------------------------
:: Sanity checks
:: -----------------------------------------------------------------------
if not exist "!ADB!" (
    echo ERROR: adb not found at:
    echo   !ADB!
    echo.
    pause
    exit /b 1
)

if not exist "!EMULATOR!" (
    echo ERROR: emulator.exe not found at:
    echo   !EMULATOR!
    echo.
    pause
    exit /b 1
)

:: -----------------------------------------------------------------------
:: Find a running emulator or start one
:: -----------------------------------------------------------------------
set "TARGET_SERIAL="
set "STALE_EMULATOR_FOUND=0"

for /f "skip=1 tokens=1,2" %%D in ('"!ADB!" devices') do (
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

:: No running emulator found - start the first available AVD
set "TARGET_AVD="
for /f "delims=" %%A in ('"!EMULATOR!" -list-avds') do (
    if not "%%A"=="" (
        set "TARGET_AVD=%%A"
        goto :start_emulator
    )
)

echo ERROR: No Android Virtual Device found.
echo Create one in Android Studio ^> Device Manager.
echo.
pause
exit /b 1

:start_emulator
if "!STALE_EMULATOR_FOUND!"=="1" (
    echo Stale offline emulator detected - closing it...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "!STOP_EMULATORS_CMD!"
    %SLEEP_CMD% 3
)

echo Starting emulator: !TARGET_AVD!
start "Android Emulator - !TARGET_AVD!" "!EMULATOR!" -avd "!TARGET_AVD!" -no-snapshot

echo Waiting for emulator to connect...
set /a WAIT_SECONDS=0
:wait_for_serial
set "TARGET_SERIAL="
for /f "skip=1 tokens=1,2" %%D in ('"!ADB!" devices') do (
    if not "%%D"=="" (
        echo %%D | findstr /R "^emulator-" >nul
        if not errorlevel 1 (
            set "TARGET_SERIAL=%%D"
            goto :wait_for_boot
        )
    )
)
set /a WAIT_SECONDS+=2
if !WAIT_SECONDS! GEQ 180 (
    echo ERROR: Timed out waiting for emulator to appear in adb.
    echo.
    pause
    exit /b 1
)
%SLEEP_CMD% 2
goto :wait_for_serial

:wait_for_boot
echo Connected: !TARGET_SERIAL!
echo Waiting for Android to finish booting...

set /a BOOT_WAIT=0
:boot_loop
set "DEVICE_STATE="
for /f "usebackq delims=" %%S in (`"!ADB!" -s !TARGET_SERIAL! get-state 2^>nul`) do (
    set "DEVICE_STATE=%%S"
)
if /I not "!DEVICE_STATE!"=="device" goto :boot_wait

set "BOOT_STATE="
for /f "usebackq delims=" %%B in (`"!ADB!" -s !TARGET_SERIAL! shell getprop sys.boot_completed 2^>nul`) do (
    set "BOOT_STATE=%%B"
)
if "!BOOT_STATE!"=="1" goto :device_ready

:boot_wait
set /a BOOT_WAIT+=3
if !BOOT_WAIT! GEQ 240 (
    echo ERROR: Timed out waiting for Android to finish booting.
    echo.
    pause
    exit /b 1
)
%SLEEP_CMD% 3
goto :boot_loop

:: -----------------------------------------------------------------------
:: Install and launch
:: -----------------------------------------------------------------------
:device_ready
echo.
echo Device ready: !TARGET_SERIAL!
echo.

echo Installing APK...
"!ADB!" -s !TARGET_SERIAL! install -r "!APK_PATH!"
if errorlevel 1 (
    echo.
    echo ERROR: Installation failed.
    echo   Make sure the APK is compatible with the emulator architecture (x86_64).
    echo   Release APKs signed with a different key need to be uninstalled first.
    echo.
    pause
    exit /b 1
)

:: Brief pause so the system registers the newly installed package
%SLEEP_CMD% 2

echo.
echo Launching app...
"!ADB!" -s !TARGET_SERIAL! shell am start -n "!MAIN_ACTIVITY!"
if errorlevel 1 (
    echo.
    echo ERROR: App launch failed.
    echo.
    pause
    exit /b 1
)

echo.
echo ================================================
echo   SUCCESS
echo   App installed and started on !TARGET_SERIAL!
echo ================================================
echo.
timeout /t 3 >nul
exit /b 0
