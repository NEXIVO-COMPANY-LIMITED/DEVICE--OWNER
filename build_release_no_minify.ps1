# Build Release APK without R8 minification (faster, less memory)
# This is useful for testing when R8 runs out of memory

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Building Release APK (No Minification)" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# Clean build
Write-Host "Cleaning build..." -ForegroundColor Yellow
Remove-Item -Recurse -Force app\build -ErrorAction SilentlyContinue

# Set environment
Write-Host "Setting JVM memory to 8GB..." -ForegroundColor Yellow
$env:GRADLE_OPTS = "-Xmx8g -XX:MaxMetaspaceSize=2g -XX:+UseG1GC"

# Build with minification disabled
Write-Host "Building APK without minification..." -ForegroundColor Yellow
./gradlew assembleRelease -Pandroid.enableR8=false -Pandroid.enableProguard=false

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
        Write-Host "  Size: $fileSizeMB MB (no minification)" -ForegroundColor Green
        
        # Calculate checksums
        Write-Host "`nCalculating checksums..." -ForegroundColor Yellow
        $sha256 = (Get-FileHash -Path $apkPath -Algorithm SHA256).Hash
        $sha1 = (Get-FileHash -Path $apkPath -Algorithm SHA1).Hash
        $md5 = (Get-FileHash -Path $apkPath -Algorithm MD5).Hash
        
        Write-Host "`nChecksums:" -ForegroundColor Cyan
        Write-Host "  SHA256: $sha256" -ForegroundColor Green
        Write-Host "  SHA1:   $sha1" -ForegroundColor Green
        Write-Host "  MD5:    $md5" -ForegroundColor Green
        
        # Save checksums to file
        $checksumFile = "app-release-checksums.txt"
        $checksumContent = @"
APK Build Information
=====================
Build Date: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')
Build Type: No Minification (Fast Build)
APK File: $apkPath
File Size: $fileSizeMB MB

Checksums:
----------
SHA256: $sha256
SHA1:   $sha1
MD5:    $md5

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
    exit 1
}
