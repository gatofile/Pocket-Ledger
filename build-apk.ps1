$ErrorActionPreference = 'Stop'

$apkSource = Join-Path $PSScriptRoot 'app\build\outputs\apk\debug\app-debug.apk'
$appBuildFile = Join-Path $PSScriptRoot 'app\build.gradle'
$versionMatch = [regex]::Match((Get-Content -LiteralPath $appBuildFile -Raw), 'versionName\s+["'']([^"'']+)["'']')
if (-not $versionMatch.Success) { throw 'Could not read the app version from app/build.gradle.' }
$appVersion = $versionMatch.Groups[1].Value
if ($appVersion -notmatch '^[0-9A-Za-z][0-9A-Za-z._-]*$') { throw 'The app version contains characters that cannot be used in an APK filename.' }
$apkDestination = Join-Path $PSScriptRoot ("Pocket-Ledger-Android-{0}.apk" -f $appVersion)
$workspaceRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$bundledSdk = Join-Path $workspaceRoot 'work\android-sdk'
$bundledGradleHome = Join-Path $workspaceRoot 'work\.gradle-cache'
$debugKeystore = Join-Path $workspaceRoot 'work\pocket-ledger-debug.keystore'

if (-not $env:GRADLE_USER_HOME -and (Test-Path -LiteralPath $bundledGradleHome -PathType Container)) {
    $env:GRADLE_USER_HOME = $bundledGradleHome
}

if (-not $env:ANDROID_HOME -and -not $env:ANDROID_SDK_ROOT -and (Test-Path -LiteralPath $bundledSdk -PathType Container)) {
    $env:ANDROID_HOME = $bundledSdk
    $env:ANDROID_SDK_ROOT = $bundledSdk
}
if (-not $env:JAVA_HOME) {
    $javaRoot = Join-Path $env:ProgramFiles 'Java'
    $installedJdk = Get-ChildItem -LiteralPath $javaRoot -Directory -Filter 'jdk-*' -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending |
        Select-Object -First 1
    if ($installedJdk) { $env:JAVA_HOME = $installedJdk.FullName }
}

if (-not $env:POCKET_LEDGER_DEBUG_KEYSTORE -and (Test-Path -LiteralPath $debugKeystore -PathType Leaf)) {
    $env:POCKET_LEDGER_DEBUG_KEYSTORE = $debugKeystore
}

if (-not $env:ANDROID_HOME -and -not $env:ANDROID_SDK_ROOT) {
    throw 'Set ANDROID_HOME or ANDROID_SDK_ROOT to your Android SDK folder before building.'
}
if (-not $env:JAVA_HOME) {
    throw 'Install a Java 17 or newer JDK and set JAVA_HOME before building.'
}

Push-Location $PSScriptRoot
try {
    & .\gradlew.bat assembleDebug
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed with exit code $LASTEXITCODE." }
} finally {
    Pop-Location
}

Copy-Item -LiteralPath $apkSource -Destination $apkDestination -Force
Write-Host "APK ready: $apkDestination"
