# Build Release APK and Generate Base64URL Checksum for Android Device Owner Provisioning
# Usage: .\scripts\build_and_checksum.ps1
#        .\scripts\build_and_checksum.ps1 -NoWifi   # Also generate no-WiFi config for troubleshooting

param(
    [switch]$NoWifi
)

$ErrorActionPreference = "Stop"
$ProjectRoot = if ($PSScriptRoot) { Resolve-Path (Join-Path $PSScriptRoot "..") } else { Get-Location }
Set-Location $ProjectRoot

# Verify keystore.properties exists and storeFile is valid (release signing)
$keystoreProps = "keystore.properties"
if (!(Test-Path $keystoreProps)) {
    Write-Host "ERROR: $keystoreProps not found. Create it with:" -ForegroundColor Red
    Write-Host "  storeFile=../your-release.jks" -ForegroundColor Gray
    Write-Host "  storePassword=your_store_password" -ForegroundColor Gray
    Write-Host "  keyAlias=your_key_alias" -ForegroundColor Gray
    Write-Host "  keyPassword=your_key_password" -ForegroundColor Gray
    exit 1
}
$props = @{}
Get-Content $keystoreProps | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith("#") -and $line -match '^([^=]+)=(.*)$') {
        $props[$matches[1].Trim()] = $matches[2].Trim()
    }
}
$storeFile = $props["storeFile"]
if (!$storeFile) {
    Write-Host "ERROR: storeFile missing in $keystoreProps" -ForegroundColor Red
    exit 1
}
$storePath = [System.IO.Path]::GetFullPath((Join-Path $ProjectRoot $storeFile))
if (!(Test-Path $storePath)) {
    Write-Host "ERROR: Keystore not found: $storePath (storeFile=$storeFile in $keystoreProps)" -ForegroundColor Red
    exit 1
}
Write-Host "Using keystore: $keystoreProps -> $storePath" -ForegroundColor DarkGray
Write-Host ""

# Reduce "file is being used by another process" (R8/classes.dex) on Windows
Write-Host "Stopping Gradle daemon..." -ForegroundColor DarkGray
& .\gradlew --stop 2>$null
if (Test-Path "app\build") {
    Write-Host "Removing app\build to clear locked intermediates..." -ForegroundColor DarkGray
    Remove-Item -Recurse -Force "app\build" -ErrorAction SilentlyContinue
}
Write-Host ""

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Building Signed Release APK..." -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

& .\gradlew assembleRelease --no-daemon

if ($LASTEXITCODE -ne 0) {
    Write-Host "BUILD FAILED!" -ForegroundColor Red
    exit 1
}

$apkPath = "app\build\outputs\apk\release\app-release.apk"
if (!(Test-Path $apkPath)) {
    Write-Host "APK not found at: $apkPath" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "BUILD SUCCESSFUL!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""

# Generate SHA256 and Base64URL checksum (required by Android managed provisioning)
$hash = Get-FileHash -Path $apkPath -Algorithm SHA256
$hexHash = $hash.Hash
$bytes = [byte[]]::new($hexHash.Length / 2)
for ($i = 0; $i -lt $hexHash.Length; $i += 2) {
    $bytes[$i / 2] = [Convert]::ToByte($hexHash.Substring($i, 2), 16)
}
$base64 = [Convert]::ToBase64String($bytes)
$base64url = $base64.Replace('+', '-').Replace('/', '_').TrimEnd('=')

# Get file size
$fileInfo = Get-Item $apkPath
$fileSizeMB = [math]::Round($fileInfo.Length / 1MB, 2)

Write-Host "APK Details:" -ForegroundColor Cyan
Write-Host "  Location: $((Resolve-Path $apkPath).Path)" -ForegroundColor Green
Write-Host "  Size: $fileSizeMB MB" -ForegroundColor Green
Write-Host ""

Write-Host "Checksums:" -ForegroundColor Cyan
Write-Host "  SHA256 (Hex):" -ForegroundColor Yellow
Write-Host "    $hexHash" -ForegroundColor White
Write-Host ""
Write-Host "  SHA256 (Base64URL):" -ForegroundColor Yellow
Write-Host "    $base64url" -ForegroundColor Green
Write-Host ""

Write-Host "For Provisioning Config:" -ForegroundColor Cyan
Write-Host "  packageChecksum: '$base64url'" -ForegroundColor Green
Write-Host ""

# Auto-update provisioning-config.json with new checksum (CRITICAL: must match APK at download URL)
$configPath = "provisioning-config.json"
if (Test-Path $configPath) {
    $json = Get-Content -Path $configPath -Raw -Encoding UTF8
    $pattern = '("android\.app\.extra\.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM"\s*:\s*)"[^"]*"'
    $replacement = "`${1}`"$base64url`""
    $newJson = $json -replace $pattern, $replacement
    if ($newJson -eq $json) {
        Write-Host "WARNING: Could not find CHECKSUM key in $configPath. Update it manually." -ForegroundColor Yellow
    } else {
        Set-Content -Path $configPath -Value $newJson -NoNewline -Encoding UTF8
        Write-Host "Updated $configPath with new checksum." -ForegroundColor Green
    }
} else {
    Write-Host "WARNING: $configPath not found. Add checksum to your provisioning JSON manually." -ForegroundColor Yellow
}

# Optional: generate no-WiFi config for "Can't set up device owner" troubleshooting
if ($NoWifi -and (Test-Path $configPath)) {
    $noWifiPath = "provisioning-config-no-wifi.json"
    $wifiKeys = @(
        "android.app.extra.PROVISIONING_WIFI_SSID",
        "android.app.extra.PROVISIONING_WIFI_PASSWORD",
        "android.app.extra.PROVISIONING_WIFI_SECURITY_TYPE",
        "android.app.extra.PROVISIONING_WIFI_HIDDEN"
    )
    $obj = Get-Content -Path $configPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $filtered = $obj | Select-Object * -ExcludeProperty $wifiKeys
    $filtered | ConvertTo-Json -Depth 10 | Set-Content -Path $noWifiPath -Encoding UTF8
    Write-Host "Generated $noWifiPath (no WiFi). Use for troubleshooting if setup fails." -ForegroundColor Cyan
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "NEXT STEPS (Device Owner QR provisioning)" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "1. Upload this EXACT APK to your release URL (same as in provisioning-config.json)" -ForegroundColor White
Write-Host "2. Generate QR from provisioning-config.json (e.g. https://qr.io or similar)" -ForegroundColor White
Write-Host "3. Factory reset device, then scan QR at 'Set up your device' / Welcome screen" -ForegroundColor White
Write-Host "4. Device must NOT be set up yet; WiFi must be reachable (or use -NoWifi + mobile data)" -ForegroundColor White
Write-Host ""
Write-Host "If you get 'Can''t set up device owner':" -ForegroundColor Yellow
Write-Host "  - Checksum must match the APK at the download URL exactly." -ForegroundColor Gray
Write-Host "  - Try provisioning-config-no-wifi.json + mobile hotspot (WiFi config often causes failures)." -ForegroundColor Gray
Write-Host "  - Ensure device is factory reset and QR scanned before any setup." -ForegroundColor Gray
Write-Host "  - See PROVISIONING_CHECKLIST.md for full troubleshooting." -ForegroundColor Gray
Write-Host ""
