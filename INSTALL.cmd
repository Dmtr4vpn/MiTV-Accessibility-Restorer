@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul
cd /d "%~dp0"

set "ROOT=%~dp0"
set "ADB=%ROOT%adb\adb.exe"
set "INSTALL_DIR=%ROOT%install"
set "LOG_FILE=%ROOT%INSTALL-LOG.txt"
set "TEMP_OUTPUT=%TEMP%\mitv-installer-%RANDOM%-%RANDOM%.tmp"
set "DEVICES_OUTPUT=%TEMP%\mitv-devices-%RANDOM%-%RANDOM%.tmp"
set "APK_METADATA_OUTPUT=%TEMP%\mitv-apk-metadata-%RANDOM%-%RANDOM%.tmp"
set "RESTORER_PACKAGE=com.mitv.accessibilityrestorer"

set /a APK_TOTAL=0
set /a APK_INDEX=0
set /a APK_NEW_INSTALLED=0
set /a APK_UPDATED=0
set /a APK_SAME_SKIPPED=0
set /a APK_NEWER_SKIPPED=0
set /a APK_FAILED=0
set "DEVICE_SERIAL="
set "RESTORER_STATUS=not checked"
set "PERMISSION_STATUS=not checked"
set "SETUP_STATUS=not opened"
set "FATAL_ERROR="

>"%LOG_FILE%" echo MiTV application bundle installer
>>"%LOG_FILE%" echo Started: %DATE% %TIME%
>>"%LOG_FILE%" echo Root: %ROOT%
>>"%LOG_FILE%" echo.

if not exist "%ADB%" (
    set "ADB="
    for /f "delims=" %%A in ('where adb.exe 2^>nul') do (
        if not defined ADB set "ADB=%%A"
    )
)

if not defined ADB (
    set "FATAL_ERROR=adb.exe not found in adb\ or PATH"
    goto :summary
)

echo Запуск ADB...
>>"%LOG_FILE%" echo COMMAND: "%ADB%" version
"%ADB%" version >"%TEMP_OUTPUT%" 2>&1
type "%TEMP_OUTPUT%" >>"%LOG_FILE%"
>>"%LOG_FILE%" echo.

>>"%LOG_FILE%" echo COMMAND: "%ADB%" start-server
"%ADB%" start-server >"%TEMP_OUTPUT%" 2>&1
set "ADB_START_RESULT=!ERRORLEVEL!"
type "%TEMP_OUTPUT%" >>"%LOG_FILE%"
>>"%LOG_FILE%" echo.
if not "!ADB_START_RESULT!"=="0" (
    set "FATAL_ERROR=adb start-server failed"
    goto :summary
)

call :ensure_device
if errorlevel 1 (
    if not defined FATAL_ERROR set "FATAL_ERROR=no authorized device selected"
    goto :summary
)

echo Подключено устройство: !DEVICE_SERIAL!
>>"%LOG_FILE%" echo Selected device: !DEVICE_SERIAL!
call :log_device_info

if not exist "%INSTALL_DIR%\" (
    >>"%LOG_FILE%" echo WARNING: install directory not found: %INSTALL_DIR%
) else (
    for /f "delims=" %%F in ('dir /b /a-d /on "%INSTALL_DIR%\*.apk" 2^>nul') do (
        set /a APK_TOTAL+=1
    )
)

if !APK_TOTAL! EQU 0 (
    echo APK в папке install не найдены. Проверяется уже установленный Restorer.
    >>"%LOG_FILE%" echo WARNING: no APK files found in install\
) else (
    echo Найдено APK: !APK_TOTAL!. Установка без дополнительных подтверждений.
    >>"%LOG_FILE%" echo APK files found: !APK_TOTAL!
    for /f "delims=" %%F in ('dir /b /a-d /on "%INSTALL_DIR%\*.apk" 2^>nul') do (
        call :install_apk "%%F"
    )
)

call :configure_restorer
goto :summary

:ensure_device
call :scan_devices
if !DEVICE_COUNT! EQU 1 exit /b 0
if !DEVICE_COUNT! GTR 1 (
    call :select_device
    exit /b !ERRORLEVEL!
)

if !UNAUTHORIZED_COUNT! GTR 0 (
    echo Подтвердите RSA-доступ на экране телевизора. Ожидание до 60 секунд...
    >>"%LOG_FILE%" echo Waiting for RSA authorization.
    call :wait_for_device
    if not errorlevel 1 exit /b 0
)

:connection_menu
echo.
echo ADB-устройство не подключено.
echo [1] Подключить Xiaomi / Network ADB по IP и порту
echo [2] Выполнить Wireless Debugging pairing
echo [3] Проверить подключение снова
echo [4] Завершить установку
set "CONNECTION_CHOICE="
set /p "CONNECTION_CHOICE=Выберите действие: "

if "%CONNECTION_CHOICE%"=="1" (
    call :network_connect
    if not errorlevel 1 exit /b 0
    goto :connection_menu
)
if "%CONNECTION_CHOICE%"=="2" (
    call :wireless_pair
    if not errorlevel 1 exit /b 0
    goto :connection_menu
)
if "%CONNECTION_CHOICE%"=="3" (
    call :scan_devices
    if !DEVICE_COUNT! EQU 1 exit /b 0
    if !DEVICE_COUNT! GTR 1 (
        call :select_device
        exit /b !ERRORLEVEL!
    )
    goto :connection_menu
)
if "%CONNECTION_CHOICE%"=="4" (
    set "FATAL_ERROR=cancelled by user before device connection"
    exit /b 1
)

echo Неизвестный пункт меню.
goto :connection_menu

:network_connect
set "TV_IP="
set "TV_PORT="
set /p "TV_IP=IP-адрес телевизора: "
if not defined TV_IP exit /b 1
set /p "TV_PORT=Порт [5555]: "
if not defined TV_PORT set "TV_PORT=5555"

echo Подключение к !TV_IP!:!TV_PORT!...
>>"%LOG_FILE%" echo COMMAND: adb connect !TV_IP!:!TV_PORT!
"%ADB%" connect "!TV_IP!:!TV_PORT!" >"%TEMP_OUTPUT%" 2>&1
type "%TEMP_OUTPUT%" >>"%LOG_FILE%"
>>"%LOG_FILE%" echo.
echo Если на ТВ появился RSA-запрос, подтвердите его. Ожидание до 60 секунд...
call :wait_for_device
exit /b !ERRORLEVEL!

:wireless_pair
"%ADB%" help >"%TEMP_OUTPUT%" 2>&1
findstr /R /I /C:"^[ ]*pair[ ]" "%TEMP_OUTPUT%" >nul
if errorlevel 1 (
    echo Встроенная версия adb не поддерживает команду pair.
    >>"%LOG_FILE%" echo ERROR: adb pair command is unavailable.
    exit /b 1
)

set "PAIR_IP="
set "PAIR_PORT="
set "PAIR_CODE="
set "CONNECT_IP="
set "CONNECT_PORT="
set /p "PAIR_IP=IP-адрес телевизора для pairing: "
if not defined PAIR_IP exit /b 1
set /p "PAIR_PORT=Pairing port: "
if not defined PAIR_PORT exit /b 1
set /p "PAIR_CODE=Шестизначный pairing code: "
if not defined PAIR_CODE exit /b 1

echo Выполняется pairing с !PAIR_IP!:!PAIR_PORT!...
>>"%LOG_FILE%" echo COMMAND: adb pair !PAIR_IP!:!PAIR_PORT! [code hidden]
"%ADB%" pair "!PAIR_IP!:!PAIR_PORT!" "!PAIR_CODE!" >"%TEMP_OUTPUT%" 2>&1
set "PAIR_RESULT=!ERRORLEVEL!"
set "PAIR_CODE="
type "%TEMP_OUTPUT%" >>"%LOG_FILE%"
>>"%LOG_FILE%" echo.
if not "!PAIR_RESULT!"=="0" (
    echo Pairing не выполнен. Подробности: INSTALL-LOG.txt
    exit /b 1
)

set "CONNECT_IP=!PAIR_IP!"
set /p "CONNECT_IP=IP для подключения [!PAIR_IP!]: "
if not defined CONNECT_IP set "CONNECT_IP=!PAIR_IP!"
set /p "CONNECT_PORT=Connection port, показанный на ТВ: "
if not defined CONNECT_PORT exit /b 1

echo Подключение к !CONNECT_IP!:!CONNECT_PORT!...
>>"%LOG_FILE%" echo COMMAND: adb connect !CONNECT_IP!:!CONNECT_PORT!
"%ADB%" connect "!CONNECT_IP!:!CONNECT_PORT!" >"%TEMP_OUTPUT%" 2>&1
type "%TEMP_OUTPUT%" >>"%LOG_FILE%"
>>"%LOG_FILE%" echo.
call :wait_for_device
exit /b !ERRORLEVEL!

:wait_for_device
for /l %%I in (1,1,20) do (
    call :scan_devices
    if !DEVICE_COUNT! EQU 1 exit /b 0
    if !DEVICE_COUNT! GTR 1 (
        call :select_device
        exit /b !ERRORLEVEL!
    )
    if %%I LSS 20 timeout /t 3 /nobreak >nul
)
echo Устройство не стало доступно за 60 секунд.
>>"%LOG_FILE%" echo Device wait timed out after 60 seconds.
exit /b 1

:scan_devices
set /a DEVICE_COUNT=0
set /a UNAUTHORIZED_COUNT=0
set /a OFFLINE_COUNT=0
set "DEVICE_SERIAL="
"%ADB%" devices >"%DEVICES_OUTPUT%" 2>&1
>>"%LOG_FILE%" echo COMMAND: adb devices
type "%DEVICES_OUTPUT%" >>"%LOG_FILE%"
>>"%LOG_FILE%" echo.
for /f "usebackq skip=1 tokens=1,2" %%A in ("%DEVICES_OUTPUT%") do (
    if "%%B"=="device" (
        set /a DEVICE_COUNT+=1
        set "DEVICE_SERIAL=%%A"
    )
    if "%%B"=="unauthorized" set /a UNAUTHORIZED_COUNT+=1
    if "%%B"=="offline" set /a OFFLINE_COUNT+=1
)
exit /b 0

:select_device
echo Обнаружено несколько ADB-устройств:
type "%DEVICES_OUTPUT%"
:select_device_again
set "DEVICE_SERIAL="
set /p "DEVICE_SERIAL=Введите serial или IP:port нужного телевизора: "
if not defined DEVICE_SERIAL goto :select_device_again
set "DEVICE_VALID="
for /f "usebackq skip=1 tokens=1,2" %%A in ("%DEVICES_OUTPUT%") do (
    if "%%A"=="!DEVICE_SERIAL!" if "%%B"=="device" set "DEVICE_VALID=1"
)
if not defined DEVICE_VALID (
    echo Устройство не найдено или не авторизовано.
    goto :select_device_again
)
>>"%LOG_FILE%" echo Device selected by user: !DEVICE_SERIAL!
exit /b 0

:log_device_info
>>"%LOG_FILE%" echo DEVICE INFORMATION
for %%P in (ro.product.manufacturer ro.product.model ro.build.version.release ro.build.version.sdk ro.build.fingerprint) do (
    >>"%LOG_FILE%" echo %%P:
    "%ADB%" -s "!DEVICE_SERIAL!" shell getprop %%P >>"%LOG_FILE%" 2>&1
)
>>"%LOG_FILE%" echo.
exit /b 0

:read_apk_metadata
set "APK_PACKAGE="
set "APK_VERSION_CODE="
set "MITV_INSTALLER_PATH=%~f0"
set "MITV_APK_PATH=%~1"
set "MITV_APK_METADATA_OUTPUT=%APK_METADATA_OUTPUT%"
del /q "%APK_METADATA_OUTPUT%" >nul 2>&1
powershell.exe -NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command "$content=[IO.File]::ReadAllText($env:MITV_INSTALLER_PATH,[Text.Encoding]::UTF8); $marker=':__APK_METADATA_POWERSHELL__'; $position=$content.LastIndexOf($marker,[StringComparison]::Ordinal); if($position -lt 0){exit 10}; Invoke-Expression $content.Substring($position+$marker.Length)" >"%TEMP_OUTPUT%" 2>&1
set "METADATA_RESULT=!ERRORLEVEL!"
set "MITV_INSTALLER_PATH="
set "MITV_APK_PATH="
set "MITV_APK_METADATA_OUTPUT="
if not "!METADATA_RESULT!"=="0" (
    >>"%LOG_FILE%" echo ERROR: APK metadata parser failed with exit code !METADATA_RESULT!.
    type "%TEMP_OUTPUT%" >>"%LOG_FILE%"
    exit /b 1
)
if not exist "%APK_METADATA_OUTPUT%" (
    >>"%LOG_FILE%" echo ERROR: APK metadata output was not created.
    exit /b 1
)
for /f "usebackq tokens=1,* delims==" %%A in ("%APK_METADATA_OUTPUT%") do (
    if /I "%%A"=="PACKAGE" set "APK_PACKAGE=%%B"
    if /I "%%A"=="VERSION_CODE" set "APK_VERSION_CODE=%%B"
)
if not defined APK_PACKAGE (
    >>"%LOG_FILE%" echo ERROR: package name is missing in APK metadata.
    exit /b 1
)
if not defined APK_VERSION_CODE (
    >>"%LOG_FILE%" echo ERROR: versionCode is missing in APK metadata.
    exit /b 1
)
exit /b 0

:get_installed_version
set "PACKAGE_PRESENT=0"
set "INSTALLED_VERSION_CODE="
"%ADB%" -s "!DEVICE_SERIAL!" shell pm path "!APK_PACKAGE!" >"%TEMP_OUTPUT%" 2>&1
findstr /B /C:"package:" "%TEMP_OUTPUT%" >nul
if errorlevel 1 exit /b 0
set "PACKAGE_PRESENT=1"
"%ADB%" -s "!DEVICE_SERIAL!" shell dumpsys package "!APK_PACKAGE!" >"%TEMP_OUTPUT%" 2>&1
for /f "tokens=2 delims==" %%V in ('findstr /R /C:"versionCode=[0-9][0-9]*" "%TEMP_OUTPUT%"') do (
    if not defined INSTALLED_VERSION_CODE for /f "tokens=1" %%W in ("%%V") do set "INSTALLED_VERSION_CODE=%%W"
)
if not defined INSTALLED_VERSION_CODE exit /b 1
exit /b 0

:install_apk
set /a APK_INDEX+=1
set "APK_NAME=%~1"
set "APK_PATH=%INSTALL_DIR%\!APK_NAME!"
echo [!APK_INDEX!/!APK_TOTAL!] Проверка !APK_NAME!...
>>"%LOG_FILE%" echo ============================================================
>>"%LOG_FILE%" echo APK [!APK_INDEX!/!APK_TOTAL!]: !APK_NAME!

call :read_apk_metadata "!APK_PATH!"
if errorlevel 1 (
    set /a APK_FAILED+=1
    echo     Ошибка: не удалось прочитать package/versionCode из APK.
    >>"%LOG_FILE%" echo RESULT: FAILED_TO_READ_APK_METADATA
    exit /b 0
)
>>"%LOG_FILE%" echo Package: !APK_PACKAGE!
>>"%LOG_FILE%" echo APK versionCode: !APK_VERSION_CODE!

call :get_installed_version
if errorlevel 1 (
    set /a APK_FAILED+=1
    echo     Ошибка: не удалось определить установленный versionCode.
    >>"%LOG_FILE%" echo Installed package: yes
    >>"%LOG_FILE%" echo RESULT: FAILED_TO_READ_INSTALLED_VERSION_CODE
    exit /b 0
)

set "INSTALL_SWITCH="
set "INSTALL_ACTION=INSTALL"
if "!PACKAGE_PRESENT!"=="0" (
    >>"%LOG_FILE%" echo Installed package: no
    >>"%LOG_FILE%" echo Decision: INSTALL
) else (
    >>"%LOG_FILE%" echo Installed package: yes
    >>"%LOG_FILE%" echo Installed versionCode: !INSTALLED_VERSION_CODE!
    if !INSTALLED_VERSION_CODE! EQU !APK_VERSION_CODE! (
        set /a APK_SAME_SKIPPED+=1
        echo     Пропущено: такая же версия уже установлена ^(versionCode !APK_VERSION_CODE!^).
        >>"%LOG_FILE%" echo Decision: SKIP_SAME_VERSION
        >>"%LOG_FILE%" echo RESULT: INSTALLATION_SKIPPED_SAME_VERSION_ALREADY_INSTALLED
        exit /b 0
    )
    if !INSTALLED_VERSION_CODE! GTR !APK_VERSION_CODE! (
        set /a APK_NEWER_SKIPPED+=1
        echo     Пропущено: на ТВ установлена более новая версия ^(!INSTALLED_VERSION_CODE! ^> !APK_VERSION_CODE!^).
        >>"%LOG_FILE%" echo Decision: SKIP_NEWER_VERSION_NO_DOWNGRADE
        >>"%LOG_FILE%" echo RESULT: NEWER_INSTALLED_VERSION_KEPT
        exit /b 0
    )
    set "INSTALL_SWITCH=-r"
    set "INSTALL_ACTION=UPDATE"
    >>"%LOG_FILE%" echo Decision: UPDATE
)

if "!INSTALL_ACTION!"=="UPDATE" (
    echo     Обновление !APK_PACKAGE!: !INSTALLED_VERSION_CODE! -^> !APK_VERSION_CODE!...
    >>"%LOG_FILE%" echo COMMAND: adb -s !DEVICE_SERIAL! install -r "install\!APK_NAME!"
) else (
    echo     Установка нового пакета !APK_PACKAGE! ^(versionCode !APK_VERSION_CODE!^)...
    >>"%LOG_FILE%" echo COMMAND: adb -s !DEVICE_SERIAL! install "install\!APK_NAME!"
)
"%ADB%" -s "!DEVICE_SERIAL!" install !INSTALL_SWITCH! "!APK_PATH!" >"%TEMP_OUTPUT%" 2>&1
set "APK_RESULT=!ERRORLEVEL!"
type "%TEMP_OUTPUT%" >>"%LOG_FILE%"
>>"%LOG_FILE%" echo.

if "!APK_RESULT!"=="0" (
    if "!INSTALL_ACTION!"=="UPDATE" (
        set /a APK_UPDATED+=1
    ) else (
        set /a APK_NEW_INSTALLED+=1
    )
    echo     OK
    >>"%LOG_FILE%" echo RESULT: !INSTALL_ACTION!_SUCCESS
    exit /b 0
)

findstr /I /C:"INSTALL_FAILED_VERSION_DOWNGRADE" "%TEMP_OUTPUT%" >nul
if not errorlevel 1 (
    set /a APK_NEWER_SKIPPED+=1
    echo     Пропущено: на ТВ установлена более новая версия.
    >>"%LOG_FILE%" echo RESULT: NEWER_INSTALLED_VERSION_KEPT_AFTER_ADB_RECHECK
    exit /b 0
)

findstr /I /C:"INSTALL_FAILED_UPDATE_INCOMPATIBLE" "%TEMP_OUTPUT%" >nul
if not errorlevel 1 (
    set /a APK_FAILED+=1
    echo     Предупреждение: несовместимая подпись, существующее приложение сохранено.
    >>"%LOG_FILE%" echo RESULT: FAILED_INCOMPATIBLE_SIGNATURE_NO_UNINSTALL
    exit /b 0
)

set /a APK_FAILED+=1
echo     Ошибка. Установка остальных APK продолжается.
>>"%LOG_FILE%" echo RESULT: FAILED
exit /b 0

:configure_restorer
echo Настройка MiTV Accessibility Restorer...
>>"%LOG_FILE%" echo ============================================================
>>"%LOG_FILE%" echo RESTORER CONFIGURATION
"%ADB%" -s "!DEVICE_SERIAL!" shell pm path %RESTORER_PACKAGE% >"%TEMP_OUTPUT%" 2>&1
type "%TEMP_OUTPUT%" >>"%LOG_FILE%"
findstr /B /C:"package:" "%TEMP_OUTPUT%" >nul
if errorlevel 1 (
    set "RESTORER_STATUS=not installed"
    set "PERMISSION_STATUS=not available"
    set "SETUP_STATUS=not opened"
    >>"%LOG_FILE%" echo Restorer package was not found.
    exit /b 0
)

set "RESTORER_STATUS=installed"
>>"%LOG_FILE%" echo COMMAND: pm grant WRITE_SECURE_SETTINGS
"%ADB%" -s "!DEVICE_SERIAL!" shell pm grant %RESTORER_PACKAGE% android.permission.WRITE_SECURE_SETTINGS >"%TEMP_OUTPUT%" 2>&1
set "GRANT_RESULT=!ERRORLEVEL!"
type "%TEMP_OUTPUT%" >>"%LOG_FILE%"
if not "!GRANT_RESULT!"=="0" (
    set "PERMISSION_STATUS=grant command failed"
) else (
    "%ADB%" -s "!DEVICE_SERIAL!" shell dumpsys package %RESTORER_PACKAGE% >"%TEMP_OUTPUT%" 2>&1
    type "%TEMP_OUTPUT%" >>"%LOG_FILE%"
    findstr /C:"android.permission.WRITE_SECURE_SETTINGS: granted=true" "%TEMP_OUTPUT%" >nul
    if errorlevel 1 (
        set "PERMISSION_STATUS=readback is not granted"
    ) else (
        set "PERMISSION_STATUS=granted"
    )
)

>>"%LOG_FILE%" echo COMMAND: am start ControlActivity
"%ADB%" -s "!DEVICE_SERIAL!" shell am start -n %RESTORER_PACKAGE%/.ControlActivity >"%TEMP_OUTPUT%" 2>&1
set "SETUP_RESULT=!ERRORLEVEL!"
type "%TEMP_OUTPUT%" >>"%LOG_FILE%"
if "!SETUP_RESULT!"=="0" (
    set "SETUP_STATUS=opened"
) else (
    set "SETUP_STATUS=failed to open"
)
exit /b 0

:summary
del /q "%TEMP_OUTPUT%" >nul 2>&1
del /q "%DEVICES_OUTPUT%" >nul 2>&1
del /q "%APK_METADATA_OUTPUT%" >nul 2>&1

echo.
echo ============================================================
if defined FATAL_ERROR (
    echo Установка не завершена: !FATAL_ERROR!
) else if !APK_FAILED! GTR 0 (
    echo Установка завершена с предупреждениями.
) else (
    echo Установка завершена.
)
echo.
echo APK найдено:             !APK_TOTAL!
echo Установлено новых:       !APK_NEW_INSTALLED!
echo Обновлено:               !APK_UPDATED!
echo Та же версия, пропуск:   !APK_SAME_SKIPPED!
echo Оставлена новая версия:  !APK_NEWER_SKIPPED!
echo Ошибок APK:              !APK_FAILED!
echo Restorer:                !RESTORER_STATUS!
echo WRITE_SECURE_SETTINGS:   !PERMISSION_STATUS!
echo Setup UI:                !SETUP_STATUS!
echo.
echo Подробности: INSTALL-LOG.txt
echo ============================================================

>>"%LOG_FILE%" echo.
>>"%LOG_FILE%" echo FINAL SUMMARY
>>"%LOG_FILE%" echo APK found: !APK_TOTAL!
>>"%LOG_FILE%" echo Newly installed: !APK_NEW_INSTALLED!
>>"%LOG_FILE%" echo Updated: !APK_UPDATED!
>>"%LOG_FILE%" echo Same installed version skipped: !APK_SAME_SKIPPED!
>>"%LOG_FILE%" echo Newer installed version kept: !APK_NEWER_SKIPPED!
>>"%LOG_FILE%" echo APK failures: !APK_FAILED!
>>"%LOG_FILE%" echo Restorer: !RESTORER_STATUS!
>>"%LOG_FILE%" echo WRITE_SECURE_SETTINGS: !PERMISSION_STATUS!
>>"%LOG_FILE%" echo Setup UI: !SETUP_STATUS!
if defined FATAL_ERROR >>"%LOG_FILE%" echo Fatal error: !FATAL_ERROR!
>>"%LOG_FILE%" echo Finished: %DATE% %TIME%

pause
if defined FATAL_ERROR exit /b 1
if /I not "!RESTORER_STATUS!"=="installed" exit /b 1
if /I not "!PERMISSION_STATUS!"=="granted" exit /b 1
if !APK_FAILED! GTR 0 exit /b 2
exit /b 0

:__APK_METADATA_POWERSHELL__
$ErrorActionPreference = 'Stop'

function Get-U16([byte[]] $Data, [int] $Offset) {
    return [BitConverter]::ToUInt16($Data, $Offset)
}

function Get-U32([byte[]] $Data, [int] $Offset) {
    return [BitConverter]::ToUInt32($Data, $Offset)
}

function Read-Utf8Length([byte[]] $Data, [ref] $Offset) {
    [int] $first = $Data[$Offset.Value]
    $Offset.Value++
    if (($first -band 0x80) -ne 0) {
        [int] $second = $Data[$Offset.Value]
        $Offset.Value++
        return (($first -band 0x7f) -shl 8) -bor $second
    }
    return $first
}

function Read-Utf16Length([byte[]] $Data, [ref] $Offset) {
    [int] $first = Get-U16 $Data $Offset.Value
    $Offset.Value += 2
    if (($first -band 0x8000) -ne 0) {
        [int] $second = Get-U16 $Data $Offset.Value
        $Offset.Value += 2
        return (($first -band 0x7fff) -shl 16) -bor $second
    }
    return $first
}

function Read-StringPool([byte[]] $Data, [int] $ChunkOffset) {
    [int] $headerSize = Get-U16 $Data ($ChunkOffset + 2)
    [int] $stringCount = Get-U32 $Data ($ChunkOffset + 8)
    [uint32] $flags = Get-U32 $Data ($ChunkOffset + 16)
    [int] $stringsStart = Get-U32 $Data ($ChunkOffset + 20)
    [bool] $utf8 = ($flags -band 0x100) -ne 0
    [string[]] $result = New-Object 'string[]' $stringCount

    for ([int] $i = 0; $i -lt $stringCount; $i++) {
        [int] $relativeOffset = Get-U32 $Data ($ChunkOffset + $headerSize + (4 * $i))
        [int] $cursorValue = $ChunkOffset + $stringsStart + $relativeOffset
        [ref] $cursor = [ref] $cursorValue
        if ($utf8) {
            $null = Read-Utf8Length $Data $cursor
            [int] $byteLength = Read-Utf8Length $Data $cursor
            $result[$i] = [Text.Encoding]::UTF8.GetString($Data, $cursor.Value, $byteLength)
        } else {
            [int] $charLength = Read-Utf16Length $Data $cursor
            $result[$i] = [Text.Encoding]::Unicode.GetString($Data, $cursor.Value, $charLength * 2)
        }
    }
    return ,$result
}

function Get-PoolString([string[]] $Pool, [uint32] $Index) {
    if ($Index -eq [uint32]::MaxValue -or $Index -ge $Pool.Length) {
        return $null
    }
    return $Pool[[int] $Index]
}

function Get-ApkManifestMetadata([string] $ApkPath) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [IO.Compression.ZipFile]::OpenRead($ApkPath)
    try {
        $entry = $archive.GetEntry('AndroidManifest.xml')
        if ($null -eq $entry) {
            throw 'AndroidManifest.xml was not found in the APK.'
        }
        $stream = $entry.Open()
        try {
            $memory = New-Object IO.MemoryStream
            $stream.CopyTo($memory)
            [byte[]] $data = $memory.ToArray()
        } finally {
            $stream.Dispose()
        }
    } finally {
        $archive.Dispose()
    }

    if ($data.Length -lt 8 -or (Get-U16 $data 0) -ne 0x0003) {
        throw 'AndroidManifest.xml is not a valid binary Android XML document.'
    }

    [int] $offset = Get-U16 $data 2
    [string[]] $stringPool = $null
    [string] $packageName = $null
    [string] $versionCode = $null

    while ($offset + 8 -le $data.Length) {
        [int] $chunkType = Get-U16 $data $offset
        [int] $headerSize = Get-U16 $data ($offset + 2)
        [long] $chunkSize = Get-U32 $data ($offset + 4)
        if ($headerSize -lt 8 -or $chunkSize -lt $headerSize -or $offset + $chunkSize -gt $data.Length) {
            throw ('Invalid Android XML chunk at offset ' + $offset + '.')
        }

        if ($chunkType -eq 0x0001) {
            $stringPool = Read-StringPool $data $offset
        } elseif ($chunkType -eq 0x0102 -and $null -ne $stringPool) {
            [string] $elementName = Get-PoolString $stringPool (Get-U32 $data ($offset + 20))
            if ($elementName -eq 'manifest') {
                [int] $attributeStart = Get-U16 $data ($offset + 24)
                [int] $attributeSize = Get-U16 $data ($offset + 26)
                [int] $attributeCount = Get-U16 $data ($offset + 28)
                [int] $attributesOffset = $offset + 16 + $attributeStart
                if ($attributeSize -lt 20) {
                    throw 'Invalid manifest attribute size.'
                }

                for ([int] $i = 0; $i -lt $attributeCount; $i++) {
                    [int] $attributeOffset = $attributesOffset + ($i * $attributeSize)
                    [string] $attributeName = Get-PoolString $stringPool (Get-U32 $data ($attributeOffset + 4))
                    [uint32] $rawValueIndex = Get-U32 $data ($attributeOffset + 8)
                    [byte] $dataType = $data[$attributeOffset + 15]
                    [uint32] $typedData = Get-U32 $data ($attributeOffset + 16)

                    if ($attributeName -eq 'package') {
                        if ($rawValueIndex -ne [uint32]::MaxValue) {
                            $packageName = Get-PoolString $stringPool $rawValueIndex
                        } elseif ($dataType -eq 0x03) {
                            $packageName = Get-PoolString $stringPool $typedData
                        }
                    } elseif ($attributeName -eq 'versionCode') {
                        if ($dataType -eq 0x10 -or $dataType -eq 0x11) {
                            $versionCode = $typedData.ToString([Globalization.CultureInfo]::InvariantCulture)
                        } elseif ($rawValueIndex -ne [uint32]::MaxValue) {
                            $versionCode = Get-PoolString $stringPool $rawValueIndex
                        }
                    }
                }
                break
            }
        }
        $offset += [int] $chunkSize
    }

    if ([string]::IsNullOrWhiteSpace($packageName)) {
        throw 'The APK package name could not be read.'
    }
    if ([string]::IsNullOrWhiteSpace($versionCode) -or $versionCode -notmatch '^\d+$') {
        throw 'The APK versionCode could not be read.'
    }
    return [pscustomobject]@{ Package = $packageName; VersionCode = $versionCode }
}

try {
    $metadata = Get-ApkManifestMetadata $env:MITV_APK_PATH
    [string[]] $metadataLines = @(
        ('PACKAGE=' + $metadata.Package)
        ('VERSION_CODE=' + $metadata.VersionCode)
    )
    [IO.File]::WriteAllLines(
        $env:MITV_APK_METADATA_OUTPUT,
        $metadataLines,
        (New-Object Text.UTF8Encoding($false))
    )
    exit 0
} catch {
    [Console]::Error.WriteLine($_.Exception.Message)
    exit 1
}
