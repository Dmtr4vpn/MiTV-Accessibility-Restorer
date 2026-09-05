param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$AndroidSdkRoot = $env:ANDROID_SDK_ROOT,
    [string]$Keystore = "",
    [string]$KeystorePassword = $env:MITV_RESTORER_KEYSTORE_PASSWORD,
    [string]$KeyAlias = $env:MITV_RESTORER_KEY_ALIAS
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$BuildRoot = Join-Path $ProjectRoot ".build"
$ClassesDir = Join-Path $BuildRoot "classes"
$DexDir = Join-Path $BuildRoot "dex"
$CompiledResDir = Join-Path $BuildRoot "compiled-res"
$DistDir = Join-Path $ProjectRoot "dist"

if ([string]::IsNullOrWhiteSpace($JavaHome)) {
    throw "JAVA_HOME is not set. Pass -JavaHome or set JAVA_HOME."
}
if ([string]::IsNullOrWhiteSpace($AndroidSdkRoot)) {
    throw "ANDROID_SDK_ROOT is not set. Pass -AndroidSdkRoot or set ANDROID_SDK_ROOT."
}

$JavaBin = Join-Path $JavaHome "bin"
$Javac = Join-Path $JavaBin "javac.exe"
$Jar = Join-Path $JavaBin "jar.exe"
$BuildTools = Join-Path $AndroidSdkRoot "build-tools\35.0.0"
$AndroidJar = Join-Path $AndroidSdkRoot "platforms\android-35\android.jar"
$Aapt2 = Join-Path $BuildTools "aapt2.exe"
$D8 = Join-Path $BuildTools "d8.bat"
$Zipalign = Join-Path $BuildTools "zipalign.exe"
$Apksigner = Join-Path $BuildTools "apksigner.bat"

foreach ($RequiredFile in @($Javac, $Jar, $AndroidJar, $Aapt2, $D8, $Zipalign, $Apksigner)) {
    if (-not (Test-Path -LiteralPath $RequiredFile)) {
        throw "Required tool not found: $RequiredFile"
    }
}

# d8.bat and apksigner.bat locate Java through JAVA_HOME even when -JavaHome is passed.
$env:JAVA_HOME = $JavaHome

New-Item -ItemType Directory -Force -Path $BuildRoot, $ClassesDir, $DexDir, $CompiledResDir, $DistDir | Out-Null
Get-ChildItem -LiteralPath $ClassesDir -Force | Remove-Item -Recurse -Force
Get-ChildItem -LiteralPath $DexDir -Force | Remove-Item -Recurse -Force
Get-ChildItem -LiteralPath $CompiledResDir -Force | Remove-Item -Recurse -Force

$Manifest = Join-Path $ProjectRoot "app\src\main\AndroidManifest.xml"
$ResDir = Join-Path $ProjectRoot "app\src\main\res"
$JavaSourceDir = Join-Path $ProjectRoot "app\src\main\java"
$ResourcesApk = Join-Path $BuildRoot "resources.apk"
$ClassesJar = Join-Path $BuildRoot "classes.jar"
$UnsignedApk = Join-Path $BuildRoot "unsigned.apk"
$AlignedApk = Join-Path $BuildRoot "aligned.apk"
$FinalApk = Join-Path $DistDir "FOX-MiTV-Restorer-4.0.0.apk"

& $Aapt2 compile --dir $ResDir -o $CompiledResDir
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }

$CompiledResources = Get-ChildItem -LiteralPath $CompiledResDir -File | ForEach-Object { $_.FullName }
& $Aapt2 link -I $AndroidJar --manifest $Manifest --min-sdk-version 21 --target-sdk-version 28 --version-code 17 --version-name "4.0.0" -o $ResourcesApk $CompiledResources
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

$JavaSources = Get-ChildItem -LiteralPath $JavaSourceDir -Recurse -Filter "*.java" | ForEach-Object { $_.FullName }
& $Javac -encoding UTF-8 -source 8 -target 8 -bootclasspath $AndroidJar -d $ClassesDir $JavaSources
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Push-Location $ClassesDir
try {
    & $Jar cf $ClassesJar .
    if ($LASTEXITCODE -ne 0) { throw "jar failed" }
}
finally {
    Pop-Location
}

& $D8 --release --min-api 21 --lib $AndroidJar --output $DexDir $ClassesJar
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

Copy-Item -LiteralPath $ResourcesApk -Destination $UnsignedApk -Force
& $Jar uf $UnsignedApk -C $DexDir "classes.dex"
if ($LASTEXITCODE -ne 0) { throw "Adding classes.dex failed" }

& $Zipalign -f -p 4 $UnsignedApk $AlignedApk
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

if ([string]::IsNullOrWhiteSpace($Keystore)) {
    throw "Pass the existing Restorer keystore with -Keystore. A new key must not be generated."
}
if (-not (Test-Path -LiteralPath $Keystore)) {
    throw "Existing Restorer keystore not found: $Keystore"
}
if ([string]::IsNullOrWhiteSpace($KeystorePassword)) {
    throw "Pass -KeystorePassword or set MITV_RESTORER_KEYSTORE_PASSWORD."
}
if ([string]::IsNullOrWhiteSpace($KeyAlias)) {
    throw "Pass -KeyAlias or set MITV_RESTORER_KEY_ALIAS."
}

& $Apksigner sign --ks $Keystore --ks-pass "pass:$KeystorePassword" --key-pass "pass:$KeystorePassword" --ks-key-alias $KeyAlias --out $FinalApk $AlignedApk
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }

& $Apksigner verify --verbose --print-certs $FinalApk
if ($LASTEXITCODE -ne 0) { throw "APK signature verification failed" }

Write-Output "Built: $FinalApk"
