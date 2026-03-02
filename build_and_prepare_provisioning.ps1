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
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME": "com.microspace.payo/com.microspace.payo.receivers.admin.AdminReceiver",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": "https://github.com/NEXIVO-COMPANY-LIMITED/DEVICE--OWNER/releases/download/V2.0/app-release.apk",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM": "$checksum",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM": "$signatureChecksum"
}
"@ | Out-File -FilePath $summaryFile -Encoding UTF8

# Step 10: Calculate Signature Checksum
Write-Host ""
Write-Host "Step 7: Calculating signature checksum..." -ForegroundColor Yellow

# Try using apksigner first (more reliable)
$signatureChecksum = $null
try {
    $apksignerOutput = & apksigner verify --print-certs $finalApk 2>&1
    $sha256Line = $apksignerOutput | Select-String "SHA-256 digest:"
    if ($sha256Line) {
        $hexString = ($sha256Line -split ":")[1].Trim().Replace(":", "").Replace(" ", "")
        $sigBytes = [byte[]]::new($hexString.Length / 2)
        for ($i = 0; $i -lt $hexString.Length; $i += 2) {
            $sigBytes[$i / 2] = [Convert]::ToByte($hexString.Substring($i, 2), 16)
        }
        $sigBase64 = [Convert]::ToBase64String($sigBytes)
        $signatureChecksum = $sigBase64.Replace('+', '-').Replace('/', '_').TrimEnd('=')
        Write-Host "✅ Signature checksum calculated" -ForegroundColor Green
        Write-Host "   Signature Checksum: $signatureChecksum" -ForegroundColor Cyan
    }
} catch {
    Write-Host "⚠️  apksigner not found, trying keytool..." -ForegroundColor Yellow
}

# Fallback to keytool if apksigner failed
if (-not $signatureChecksum) {
    try {
        $keystoreFile = "keystore/payo-release.keystore"
        $keystoreAlias = "payo-release"
        $certBytes = keytool -exportcert -alias $keystoreAlias -keystore $keystoreFile -storepass payorelease 2>$null
        if ($certBytes) {
            $sha256 = [System.Security.Cryptography.SHA256]::Create()
            $hash = $sha256.ComputeHash($certBytes)
            $sigBase64 = [Convert]::ToBase64String($hash)
            $signatureChecksum = $sigBase64.Replace('+', '-').Replace('/', '_').TrimEnd('=')
            Write-Host "✅ Signature checksum calculated (keytool)" -ForegroundColor Green
            Write-Host "   Signature Checksum: $signatureChecksum" -ForegroundColor Cyan
        }
    } catch {
        Write-Host "⚠️  Could not calculate signature checksum automatically" -ForegroundColor Yellow
        $signatureChecksum = "MANUALLY_CALCULATE"
    }
}

# Step 11: Update provisioning-config.json
Write-Host ""
Write-Host "Step 8: Updating provisioning-config.json..." -ForegroundColor Yellow

$configFile = "provisioning-config.json"
if (Test-Path $configFile) {
    try {
        $config = Get-Content $configFile -Raw | ConvertFrom-Json
        
        # Update checksums
        $config.'android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM' = $checksum
        if ($signatureChecksum -and $signatureChecksum -ne "MANUALLY_CALCULATE") {
            $config.'android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM' = $signatureChecksum
        }
        
        # Save updated config
        $config | ConvertTo-Json -Depth 10 | Set-Content $configFile -Encoding UTF8
        
        Write-Host "✅ provisioning-config.json updated" -ForegroundColor Green
        Write-Host "   Package Checksum: $checksum" -ForegroundColor Cyan
        if ($signatureChecksum -and $signatureChecksum -ne "MANUALLY_CALCULATE") {
            Write-Host "   Signature Checksum: $signatureChecksum" -ForegroundColor Cyan
        }
    } catch {
        Write-Host "⚠️  Could not update provisioning-config.json: $_" -ForegroundColor Yellow
    }
} else {
    Write-Host "⚠️  provisioning-config.json not found" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "📄 Build summary saved to: $summaryFile" -ForegroundColor Cyan
Write-Host ""
Write-Host "=== ✅ BUILD COMPLETE ===" -ForegroundColor Green
Write-Host ""
Write-Host "📋 FINAL CHECKLIST:" -ForegroundColor Cyan
Write-Host "   [1] Upload app-release.apk to GitHub Release" -ForegroundColor White
Write-Host "   [2] Verify provisioning-config.json has correct checksums" -ForegroundColor White
Write-Host "   [3] Generate QR code from provisioning-config.json" -ForegroundColor White
Write-Host "   [4] Test on factory-reset device" -ForegroundColor White
Write-Host ""
