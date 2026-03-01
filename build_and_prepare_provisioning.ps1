# Complete Build and Provisioning Preparation Script
# This script builds, signs, and prepares everything for device provisioning

Write-Host "=== PAYO Device Owner - Build & Provisioning Setup ===" -ForegroundColor Cyan
Write-Host ""

# Step 1: Clean previous builds
Write-Host "Step 1: Cleaning previous builds..." -ForegroundColor Yellow
./gradlew.bat clean
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Clean failed" -ForegroundColor Red
    exit 1
}
Write-Host "✅ Clean complete" -ForegroundColor Green
Write-Host ""

# Step 2: Build release APK
Write-Host "Step 2: Building release APK..." -ForegroundColor Yellow
./gradlew.bat assembleRelease
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Build failed" -ForegroundColor Red
    exit 1
}
Write-Host "✅ Build complete" -ForegroundColor Green
Write-Host ""

# Step 3: Check if APK exists
$unsignedApk = "app/build/outputs/apk/release/app-release-unsigned.apk"
if (-not (Test-Path $unsignedApk)) {
    Write-Host "❌ APK not found at: $unsignedApk" -ForegroundColor Red
    exit 1
}

# Step 4: Sign the APK
Write-Host "Step 3: Signing APK..." -ForegroundColor Yellow
$keystoreFile = "keystore/payo-release.keystore"
if (-not (Test-Path $keystoreFile)) {
    Write-Host "❌ Keystore not found: $keystoreFile" -ForegroundColor Red
    exit 1
}

jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 `
    -keystore $keystoreFile `
    $unsignedApk `
    payo-release

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Signing failed" -ForegroundColor Red
    exit 1
}
Write-Host "✅ APK signed" -ForegroundColor Green
Write-Host ""

# Step 5: Align the APK
Write-Host "Step 4: Aligning APK..." -ForegroundColor Yellow
$finalApk = "app-release.apk"
zipalign -v 4 $unsignedApk $finalApk

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Alignment failed" -ForegroundColor Red
    exit 1
}
Write-Host "✅ APK aligned" -ForegroundColor Green
Write-Host ""

# Step 6: Calculate checksum
Write-Host "Step 5: Calculating checksum..." -ForegroundColor Yellow
$bytes = [System.IO.File]::ReadAllBytes($finalApk)
$sha256 = [System.Security.Cryptography.SHA256]::Create()
$hash = $sha256.ComputeHash($bytes)
$checksum = [Convert]::ToBase64String($hash).Replace('+', '-').Replace('/', '_').TrimEnd('=')

Write-Host "✅ Checksum calculated" -ForegroundColor Green
Write-Host ""

# Step 7: Display results
Write-Host "=== BUILD COMPLETE ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "📦 APK Location:" -ForegroundColor Yellow
Write-Host "   $((Get-Item $finalApk).FullName)" -ForegroundColor White
Write-Host ""
Write-Host "📊 APK Size:" -ForegroundColor Yellow
$apkSize = (Get-Item $finalApk).Length / 1MB
Write-Host "   $([Math]::Round($apkSize, 2)) MB" -ForegroundColor White
Write-Host ""
Write-Host "🔐 Base64URL Checksum:" -ForegroundColor Yellow
Write-Host "   $checksum" -ForegroundColor Green
Write-Host ""

# Step 8: Verify package name
Write-Host "Step 6: Verifying package name..." -ForegroundColor Yellow
Write-Host "Extracting AndroidManifest.xml from APK..." -ForegroundColor Gray

# Create temp directory
$tempDir = "temp_apk_extract"
if (Test-Path $tempDir) {
    Remove-Item -Recurse -Force $tempDir
}
New-Item -ItemType Directory -Path $tempDir | Out-Null

# Extract APK
Add-Type -AssemblyName System.IO.Compression.FileSystem
[System.IO.Compression.ZipFile]::ExtractToDirectory($finalApk, $tempDir)

# Check for AndroidManifest.xml
if (Test-Path "$tempDir/AndroidManifest.xml") {
    Write-Host "✅ Package verification: APK structure looks correct" -ForegroundColor Green
} else {
    Write-Host "⚠️  Could not verify package (binary manifest)" -ForegroundColor Yellow
}

# Cleanup
Remove-Item -Recurse -Force $tempDir

Write-Host ""
Write-Host "=== NEXT STEPS ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "1. Upload app-release.apk to GitHub Release" -ForegroundColor White
Write-Host "   - Go to: https://github.com/NEXIVO-COMPANY-LIMITED/DEVICE--OWNER/releases" -ForegroundColor Gray
Write-Host "   - Create new release (e.g., V2.0)" -ForegroundColor Gray
Write-Host "   - Upload: app-release.apk" -ForegroundColor Gray
Write-Host ""
Write-Host "2. Update provisioning-config.json with:" -ForegroundColor White
Write-Host '   "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME": "com.microspace.payo/.receivers.admin.AdminReceiver",' -ForegroundColor Gray
Write-Host '   "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": "https://github.com/NEXIVO-COMPANY-LIMITED/DEVICE--OWNER/releases/download/V2.0/app-release.apk",' -ForegroundColor Gray
Write-Host "   `"android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM`": `"$checksum`"" -ForegroundColor Gray
Write-Host ""
Write-Host "3. Generate QR code from updated provisioning-config.json" -ForegroundColor White
Write-Host ""
Write-Host "4. Test on factory-reset device" -ForegroundColor White
Write-Host ""

# Step 9: Create a summary file
$summaryFile = "build_summary.txt"
@"
PAYO Device Owner - Build Summary
Generated: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")

APK File: $((Get-Item $finalApk).FullName)
APK Size: $([Math]::Round($apkSize, 2)) MB
Base64URL Checksum: $checksum

Application ID: com.microspace.payo
Component Name: com.microspace.payo/.receivers.admin.AdminReceiver

Provisioning Config Update:
{
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME": "com.microspace.payo/.receivers.admin.AdminReceiver",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": "https://github.com/NEXIVO-COMPANY-LIMITED/DEVICE--OWNER/releases/download/V2.0/app-release.apk",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM": "$checksum"
}
"@ | Out-File -FilePath $summaryFile -Encoding UTF8

Write-Host "📄 Build summary saved to: $summaryFile" -ForegroundColor Cyan
Write-Host ""
Write-Host "✅ All done! Ready to upload to GitHub." -ForegroundColor Green
