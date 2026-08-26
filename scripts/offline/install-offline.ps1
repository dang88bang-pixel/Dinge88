# SecureGuard Enterprise – Offline-Toolchain (Windows PowerShell)
# Voraussetzung: offline_repo/ liegt bereit (von download-all.sh erzeugt).
#
#   .\scripts\offline\install-offline.ps1
#   $env:OFFLINE_REPO = "E:\offline_repo"; .\scripts\offline\install-offline.ps1
#
# Für Download auf Windows bitte WSL oder den Linux-Online-PC nutzen
# (sdkmanager/pio sind unter Bash robuster). Dieses Skript deckt die
# Offline-Installation von JDK + Android-SDK + Gradle-Cache ab.

$ErrorActionPreference = "Stop"

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$OfflineRepo = if ($env:OFFLINE_REPO) { $env:OFFLINE_REPO } else { Join-Path $RepoRoot "offline_repo" }
$InstallRoot = Join-Path $env:USERPROFILE ".secureguard"

if (-not (Test-Path $OfflineRepo)) {
    throw "OFFLINE_REPO nicht gefunden: $OfflineRepo"
}

Write-Host "▶ Offline-Repo: $OfflineRepo"
Write-Host "▶ Install-Root: $InstallRoot"

# --- JDK ---
$JdkSrc = Join-Path $OfflineRepo "jdk"
$JdkDst = Join-Path $InstallRoot "jdk"
New-Item -ItemType Directory -Force -Path $JdkDst | Out-Null
$archive = Get-ChildItem $JdkSrc -File -Include *.zip,*.tar.gz -ErrorAction SilentlyContinue | Select-Object -First 1
if ($archive) {
    Write-Host "▶ Entpacke JDK: $($archive.Name)"
    if ($archive.Extension -eq ".zip") {
        Expand-Archive -Path $archive.FullName -DestinationPath $JdkDst -Force
    } else {
        Write-Host "⚠ .tar.gz unter Windows: bitte tar.exe nutzen oder WSL"
        tar -xzf $archive.FullName -C $JdkDst
    }
}
$javaExe = Get-ChildItem -Path $JdkDst -Recurse -Filter java.exe -ErrorAction SilentlyContinue | Select-Object -First 1
if ($javaExe) {
    $env:JAVA_HOME = $javaExe.Directory.Parent.FullName
    Write-Host "✔ JAVA_HOME=$env:JAVA_HOME"
    [Environment]::SetEnvironmentVariable("JAVA_HOME", $env:JAVA_HOME, "User")
}

# --- Android SDK ---
$SdkSrc = Join-Path $OfflineRepo "android-sdk"
$SdkDst = Join-Path $InstallRoot "android-sdk"
if (Test-Path $SdkSrc) {
    Write-Host "▶ Kopiere Android-SDK (kann dauern)…"
    New-Item -ItemType Directory -Force -Path $SdkDst | Out-Null
    robocopy $SdkSrc $SdkDst /E /NFL /NDL /NJH /NJS /nc /ns /np | Out-Null
    $env:ANDROID_HOME = $SdkDst
    $env:ANDROID_SDK_ROOT = $SdkDst
    [Environment]::SetEnvironmentVariable("ANDROID_HOME", $SdkDst, "User")
    [Environment]::SetEnvironmentVariable("ANDROID_SDK_ROOT", $SdkDst, "User")
    Write-Host "✔ ANDROID_HOME=$SdkDst"
}

# --- local.properties ---
$lp = Join-Path $RepoRoot "local.properties"
$example = Join-Path $RepoRoot "local.properties.example"
if (-not (Test-Path $lp) -and (Test-Path $example)) {
    Copy-Item $example $lp
}
$sdkDirProp = ($SdkDst -replace "\\", "/")
if (Test-Path $lp) {
    $content = Get-Content $lp | Where-Object { $_ -notmatch '^\s*sdk\.dir=' }
    $content + "sdk.dir=$sdkDirProp" | Set-Content $lp -Encoding UTF8
    Write-Host "✔ local.properties sdk.dir=$sdkDirProp"
} else {
    "sdk.dir=$sdkDirProp" | Set-Content $lp -Encoding UTF8
}

# --- Gradle-Cache ---
$GrSrc = Join-Path $OfflineRepo "gradle"
$GrDst = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE ".gradle" }
if (Test-Path $GrSrc) {
    Write-Host "▶ Gradle-Cache → $GrDst"
    New-Item -ItemType Directory -Force -Path $GrDst | Out-Null
    if (Test-Path (Join-Path $GrSrc "caches")) {
        robocopy (Join-Path $GrSrc "caches") (Join-Path $GrDst "caches") /E /NFL /NDL /NJH /NJS /nc /ns /np | Out-Null
    }
    if (Test-Path (Join-Path $GrSrc "wrapper")) {
        robocopy (Join-Path $GrSrc "wrapper") (Join-Path $GrDst "wrapper") /E /NFL /NDL /NJH /NJS /nc /ns /np | Out-Null
    }
    Write-Host "✔ GRADLE_USER_HOME=$GrDst"
}

Write-Host ""
Write-Host "✔ Fertig. Neue Shell öffnen (User-Env greift), dann:"
Write-Host "    cd $RepoRoot"
Write-Host "    .\gradlew.bat :app:assembleDebug --offline"
Write-Host ""
Write-Host "PlatformIO/ESP32 unter Windows: WSL empfohlen, siehe docs/OFFLINE_SETUP.md"
