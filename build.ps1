now # Build Release APK with Checksum - Final Working Version

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Building Release APK" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# Clean build
Write-Host "Cleaning build..." -ForegroundColor Yellow
Remove-Item -Recurse -Force app\build -ErrorAction SilentlyContinue

# Set environment
Write-Host "Setting JVM memory to 8GB..." -ForegroundColor Yellow
$env:GRADLE_OPTS = "-Xmx8g -XX:MaxMetaspaceSize=2g -XX:+UseG1GC"

# Build
Write-Host "Building APK..." -ForegroundColor Yellow
./gradlew assembleRelease

if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ Build successful!" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Cyan
    
    $apkPath = "app\build\outputs\apk\release\app-release.apk"
    
    if (Test-Path $apkPath) {
        # Get file info
        $fileInfo = Get-Item $apkPath
        $fileSizeMB = [math]::Round($fileInfo.Length / 1MB, 2)
        
        Write-Host "APK Details:" -ForegroundColor Cyan
        Write-Host "  Location: $apkPath" -ForegroundColor Green
        Write-Host "  Size: $fileSizeMB MB" -ForegroundColor Green
        
        # Calculate checksums
        Write-Host "`nCalculating checksums..." -ForegroundColor Yellow
        $sha256Hex = (Get-FileHash -Path $apkPath -Algorithm SHA256).Hash
        $sha1 = (Get-FileHash -Path $apkPath -Algorithm SHA1).Hash
        $md5 = (Get-FileHash -Path $apkPath -Algorithm MD5).Hash
        
        # Convert SHA256 hex to Base64URL for provisioning config
        $sha256Bytes = [System.Convert]::FromHexString($sha256Hex)
        $sha256Base64 = [System.Convert]::ToBase64String($sha256Bytes)
        # Convert to Base64URL (replace + with -, / with _, remove padding)
        $sha256Base64URL = $sha256Base64.Replace('+', '-').Replace('/', '_').TrimEnd('=')
        
        Write-Host "`nChecksums:" -ForegroundColor Cyan
        Write-Host "  SHA256 (Hex):      $sha256Hex" -ForegroundColor Green
        Write-Host "  SHA256 (Base64URL): $sha256Base64URL" -ForegroundColor Green
        Write-Host "  SHA1:              $sha1" -ForegroundColor Green
        Write-Host "  MD5:               $md5" -ForegroundColor Green
        
        # Save checksums to file
        $checksumFile = "app-release-checksums.txt"
        $checksumContent = @"
APK Build Information
=====================
Build Date: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')
APK File: $apkPath
File Size: $fileSizeMB MB

Checksums:
----------
SHA256 (Hex): $sha256Hex
SHA256 (Base64URL): $sha256Base64URL

SHA1:   $sha1
MD5:    $md5

For Provisioning Config:
packageChecksum: '$sha256Base64URL'

Installation Command:
adb install -r $apkPath

Verification Command:
(Get-FileHash -Path "$apkPath" -Algorithm SHA256).Hash
"@
        
        $checksumContent | Out-File -FilePath $checksumFile -Encoding UTF8
        Write-Host "`n✅ Checksums saved to: $checksumFile" -ForegroundColor Green
        
        Write-Host "`n========================================" -ForegroundColor Cyan
        Write-Host "Next Steps:" -ForegroundColor Cyan
        Write-Host "  1. Install: adb install -r $apkPath" -ForegroundColor Yellow
        Write-Host "  2. Test: Open app and register device" -ForegroundColor Yellow
        Write-Host "  3. Verify: adb logcat | grep DeviceRegistration" -ForegroundColor Yellow
        Write-Host "========================================" -ForegroundColor Cyan
    } else {
        Write-Host "❌ APK not found at: $apkPath" -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "❌ Build failed!" -ForegroundColor Red
    Write-Host "`nTrying without minification..." -ForegroundColor Yellow
    Write-Host "Run: .\build_release_no_minify.ps1" -ForegroundColor Yellow
    exit 1
}
