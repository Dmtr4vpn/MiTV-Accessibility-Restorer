@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul
cd /d "%~dp0"

set "PACKAGE=com.mitv.accessibilityrestorer"
set "APK=MiTVAccessibilityRestorer-4.0.0.apk"
set "LOG_FILE=%TEMP%\mitv-restorer-install-%RANDOM%.log"

where adb.exe >nul 2>&1
if errorlevel 1 (
    echo ERROR: adb.exe was not found in PATH.
    echo Install Android Platform Tools and add its directory to PATH.
    goto :fail
)

if not exist "%APK%" (
    echo ERROR: %APK% was not found next to INSTALL.cmd.
    goto :fail
)

echo Starting ADB server...
adb start-server
if errorlevel 1 (
    echo ERROR: adb start-server failed.
    goto :fail
)

set /a DEVICE_COUNT=0
set "DEVICE_SERIAL="
for /f "skip=1 tokens=1,2" %%A in ('adb devices 2^>nul') do (
    if "%%B"=="device" (
        set /a DEVICE_COUNT+=1
        set "DEVICE_SERIAL=%%A"
    )
)

if !DEVICE_COUNT! EQU 0 (
    echo ERROR: no authorized ADB device is connected.
    echo Check the TV connection and accept the debugging authorization prompt.
    adb devices
    goto :fail
)

if !DEVICE_COUNT! GTR 1 (
    echo ERROR: more than one ADB device is connected.
    echo Disconnect extra devices; this installer will not choose one at random.
    adb devices
    goto :fail
)

echo Installing %APK% on !DEVICE_SERIAL!...
adb -s "!DEVICE_SERIAL!" install -r "%APK%" >"%LOG_FILE%" 2>&1
set "INSTALL_RESULT=!ERRORLEVEL!"
type "%LOG_FILE%"
if not "!INSTALL_RESULT!"=="0" (
    findstr /C:"INSTALL_FAILED_UPDATE_INCOMPATIBLE" "%LOG_FILE%" >nul
    if not errorlevel 1 (
        echo.
        echo The installed version is signed with a different key.
        echo For a clean installation, first remove the old version manually:
        echo adb uninstall %PACKAGE%
        echo The installer did not uninstall anything.
    )
    del /q "%LOG_FILE%" >nul 2>&1
    goto :fail
)
del /q "%LOG_FILE%" >nul 2>&1

echo Granting WRITE_SECURE_SETTINGS...
adb -s "!DEVICE_SERIAL!" shell pm grant %PACKAGE% android.permission.WRITE_SECURE_SETTINGS
if errorlevel 1 (
    echo ERROR: permission grant failed.
    goto :fail
)

echo Verifying WRITE_SECURE_SETTINGS...
adb -s "!DEVICE_SERIAL!" shell dumpsys package %PACKAGE% | findstr /C:"android.permission.WRITE_SECURE_SETTINGS: granted=true" >nul
if errorlevel 1 (
    echo ERROR: WRITE_SECURE_SETTINGS readback is not granted=true.
    goto :fail
)

echo Opening first-run setup on the TV...
adb -s "!DEVICE_SERIAL!" shell am start -n %PACKAGE%/.ControlActivity
if errorlevel 1 (
    echo ERROR: ControlActivity could not be opened.
    goto :fail
)

echo.
echo Installation completed successfully.
echo Complete the setup on the TV screen.
pause
exit /b 0

:fail
echo.
echo Installation was not completed.
pause
exit /b 1
