@echo off
setlocal EnableExtensions EnableDelayedExpansion

cd /d "%~dp0"

:: -----------------------------------------------------------------------
:: Paths
:: -----------------------------------------------------------------------
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "ANDROID_SDK_ROOT=C:\Users\jemi\AppData\Local\Android\Sdk"
set "BUILD_TOOLS=%ANDROID_SDK_ROOT%\build-tools\34.0.0"

set "PATH=%JAVA_HOME%\bin;%BUILD_TOOLS%;%ANDROID_SDK_ROOT%\platform-tools;%PATH%"

set "KEYSTORE_FILE=%~dp0app\release.jks"
set "KEYSTORE_ALIAS=pocastcloni"
set "APK_UNSIGNED=%~dp0app\build\outputs\apk\release\app-release-unsigned.apk"
set "APK_SIGNED=%~dp0app-release-signed.apk"
set "APKSIGNER=%BUILD_TOOLS%\apksigner.bat"
set "STORE_PASS="
set "KEY_PASS="
set "KEYSTORE_MODE=USE_EXISTING"

echo.
echo ====================================================
echo   PocastCloni - Build Signed Release APK
echo ====================================================
echo.

:: -----------------------------------------------------------------------
:: Sanity checks
:: -----------------------------------------------------------------------
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo ERROR: Java not found at:
    echo   %JAVA_HOME%
    echo.
    pause
    exit /b 1
)

if not exist "%JAVA_HOME%\bin\keytool.exe" (
    echo ERROR: keytool not found at:
    echo   %JAVA_HOME%\bin\keytool.exe
    echo.
    pause
    exit /b 1
)

if not exist "%APKSIGNER%" (
    echo ERROR: apksigner not found at:
    echo   %APKSIGNER%
    echo Make sure build-tools 34.0.0 is installed in Android Studio.
    echo.
    pause
    exit /b 1
)

:: -----------------------------------------------------------------------
:: Keystore handling
:: -----------------------------------------------------------------------
if exist "!KEYSTORE_FILE!" (
    echo Keystore found:
    echo   !KEYSTORE_FILE!
    echo.
    echo Choose an option:
    echo   [1] Use existing keystore
    echo   [2] Reset keystore password ^(creates a NEW keystore^)
    echo   [3] Cancel
    echo.
    choice /c 123 /n /m "Selection: "

    if errorlevel 3 (
        echo.
        echo Cancelled.
        echo.
        pause
        exit /b 1
    )

    if errorlevel 2 (
        set "KEYSTORE_MODE=RESET"
    ) else (
        set "KEYSTORE_MODE=USE_EXISTING"
    )
    echo.
) else (
    set "KEYSTORE_MODE=CREATE_NEW"
)

if /i "!KEYSTORE_MODE!"=="USE_EXISTING" goto USE_EXISTING_KEYSTORE
if /i "!KEYSTORE_MODE!"=="RESET" goto RESET_KEYSTORE
if /i "!KEYSTORE_MODE!"=="CREATE_NEW" goto CREATE_NEW_KEYSTORE

echo ERROR: Unknown keystore mode.
echo.
pause
exit /b 1

:USE_EXISTING_KEYSTORE
echo Using existing keystore.
echo.
call :readSecret "Keystore password" STORE_PASS
if "!STORE_PASS!"=="" (
    echo ERROR: Password cannot be empty.
    echo.
    pause
    exit /b 1
)

call :readSecret "Key password (Enter = same as keystore password)" KEY_PASS
if "!KEY_PASS!"=="" set "KEY_PASS=!STORE_PASS!"
echo.
goto BUILD_APK

:RESET_KEYSTORE
echo WARNING: A forgotten password cannot be recovered from the existing keystore.
echo This option does NOT recover the old password.
echo It creates a NEW keystore and backs up the old one.
echo.
echo IMPORTANT:
echo - Existing installed app updates signed with the OLD keystore will no longer install as updates.
echo - Users will need to uninstall the old app first, then install the new APK.
echo - Play Store / store updates with the old signing key will also no longer match.
echo.
choice /c YN /n /m "Continue and create a new keystore? [Y/N]: "
if errorlevel 2 (
    echo.
    echo Cancelled.
    echo.
    pause
    exit /b 1
)

echo.
for /f "usebackq delims=" %%T in (`powershell -NoProfile -Command "Get-Date -Format 'yyyyMMdd-HHmmss'"`) do set "TS=%%T"
set "KEYSTORE_BACKUP=%~dp0app\release-backup-!TS!.jks"

if exist "!KEYSTORE_FILE!" (
    move /Y "!KEYSTORE_FILE!" "!KEYSTORE_BACKUP!" >nul
    if errorlevel 1 (
        echo ERROR: Failed to back up old keystore.
        echo.
        pause
        exit /b 1
    )
    echo Old keystore backed up to:
    echo   !KEYSTORE_BACKUP!
    echo.
)

goto CREATE_NEW_KEYSTORE

:CREATE_NEW_KEYSTORE
echo No usable keystore password available.
echo A new keystore will be created at:
 echo   !KEYSTORE_FILE!
echo.
echo IMPORTANT: Back up app\release.jks after creation.
echo            You need the same keystore for every future app update.
echo            It is excluded from git via .gitignore.
echo.
echo NOTE: Avoid using ! in the password, because CMD delayed expansion can break it.
echo.

call :readSecret "Keystore password (min 6 chars)" STORE_PASS
if "!STORE_PASS!"=="" (
    echo ERROR: Password cannot be empty.
    echo.
    pause
    exit /b 1
)

call :readSecret "Key password (Enter = same as keystore password)" KEY_PASS
if "!KEY_PASS!"=="" set "KEY_PASS=!STORE_PASS!"

echo.
echo Creating keystore...
"%JAVA_HOME%\bin\keytool.exe" -genkeypair -v -keystore "!KEYSTORE_FILE!" -alias "!KEYSTORE_ALIAS!" -keyalg RSA -keysize 2048 -validity 10000 -storepass "!STORE_PASS!" -keypass "!KEY_PASS!" -dname "CN=PocastCloni, OU=Dev, O=Personal, L=Unknown, ST=Unknown, C=DE"

if errorlevel 1 (
    echo.
    echo ERROR: Failed to create keystore. Check the password and try again.
    echo.
    pause
    exit /b 1
)

echo.
echo Keystore created successfully.
echo.

goto BUILD_APK

:BUILD_APK
:: -----------------------------------------------------------------------
:: Build
:: -----------------------------------------------------------------------
echo Building release APK...
echo.
call .\gradlew.bat assembleRelease
if errorlevel 1 (
    echo.
    echo ERROR: Gradle build failed. See output above.
    echo.
    pause
    exit /b 1
)

:: -----------------------------------------------------------------------
:: Locate unsigned APK
:: -----------------------------------------------------------------------
if not exist "!APK_UNSIGNED!" (
    echo.
    echo ERROR: Unsigned APK not found at:
    echo   !APK_UNSIGNED!
    echo.
    pause
    exit /b 1
)

:: -----------------------------------------------------------------------
:: Sign
:: -----------------------------------------------------------------------
echo.
echo Signing APK...
call "!APKSIGNER!" sign --ks "!KEYSTORE_FILE!" --ks-key-alias "!KEYSTORE_ALIAS!" --ks-pass "pass:!STORE_PASS!" --key-pass "pass:!KEY_PASS!" --out "!APK_SIGNED!" "!APK_UNSIGNED!"

if errorlevel 1 (
    echo.
    echo ERROR: Signing failed. Wrong password?
    echo.
    pause
    exit /b 1
)

:: -----------------------------------------------------------------------
:: Verify
:: -----------------------------------------------------------------------
echo.
echo Verifying signature...
call "!APKSIGNER!" verify --verbose --print-certs "!APK_SIGNED!"
if errorlevel 1 (
    echo.
    echo WARNING: Signature verification failed.
    echo.
    pause
    exit /b 1
)

:: -----------------------------------------------------------------------
:: Done
:: -----------------------------------------------------------------------
echo.
echo ====================================================
echo   SUCCESS
echo   Signed APK: !APK_SIGNED!
echo ====================================================
echo.
echo Transfer this file to your Huawei P30 Pro and install it.
echo (Settings ^> Apps ^> Special app access ^> Install unknown apps)
echo.
pause
exit /b 0

:readSecret
set "%~2="
for /f "usebackq delims=" %%P in (`powershell -NoProfile -Command "$p = Read-Host -AsSecureString '%~1'; [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($p))"`) do set "%~2=%%P"
exit /b 0
