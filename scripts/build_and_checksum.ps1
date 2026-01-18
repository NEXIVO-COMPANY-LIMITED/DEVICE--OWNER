# Build Release APK and Generate Base64URL Checksum for Android Provisioning
# Usage: .\scripts\build_and_checksum.ps1

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Building Signed Release APK..." -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# Build the release APK
& .\gradlew assembleRelease

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

# Generate SHA256 hash
$hash = Get-FileHash -Path $apkPath -Algorithm SHA256
$hexHash = $hash.Hash

# Convert hex to Base64URL
$bytes = [byte[]]::new($hexHash.Length / 2)
for ($i = 0; $i -lt $hexHash.Length; $i += 2) {
    $bytes[$i / 2] = [Convert]::ToByte($hexHash.Substring($i, 2), 16)
}
$base64 = [Convert]::ToBase64String($bytes)
$base64url = $base64.Replace('+', '-').Replace('/', '_').TrimEnd('=')

Write-Host "APK Location:" -ForegroundColor Yellow
Write-Host "  $((Resolve-Path $apkPath).Path)" -ForegroundColor White
Write-Host ""
Write-Host "SHA256 (Hex):" -ForegroundColor Yellow
Write-Host "  $hexHash" -ForegroundColor White
Write-Host ""
Write-Host "Base64URL Checksum (for JSON):" -ForegroundColor Yellow
Write-Host "  $base64url" -ForegroundColor Green
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Copy this to your provisioning config:" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "packageChecksum: '$base64url'" -ForegroundColor Green
Write-Host ""
