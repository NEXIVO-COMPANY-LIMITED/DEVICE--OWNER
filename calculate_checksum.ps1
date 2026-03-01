# Calculate correct Base64URL checksum for APK
# Usage: .\calculate_checksum.ps1 -ApkPath "app-release.apk"

param(
    [string]$ApkPath = "app-release.apk"
)

if (-not (Test-Path $ApkPath)) {
    Write-Host "❌ APK file not found: $ApkPath" -ForegroundColor Red
    exit 1
}

Write-Host "Calculating checksum for: $ApkPath" -ForegroundColor Cyan

# Read file bytes
$bytes = [System.IO.File]::ReadAllBytes($ApkPath)

# Calculate SHA-256
$sha256 = [System.Security.Cryptography.SHA256]::Create()
$hash = $sha256.ComputeHash($bytes)

# Convert to Base64URL (Android provisioning format)
$base64url = [Convert]::ToBase64String($hash).Replace('+', '-').Replace('/', '_').TrimEnd('=')

Write-Host ""
Write-Host "✅ Base64URL Checksum:" -ForegroundColor Green
Write-Host $base64url -ForegroundColor Yellow
Write-Host ""
Write-Host "📋 Copy this value to provisioning-config.json:" -ForegroundColor Cyan
Write-Host '"android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM": "'$base64url'"' -ForegroundColor Gray

# Also show hex for reference
$hexHash = [System.BitConverter]::ToString($hash).Replace("-", "")
Write-Host ""
Write-Host "📋 SHA-256 (hex, for reference):" -ForegroundColor Cyan
Write-Host $hexHash -ForegroundColor Gray
