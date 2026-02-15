# Build Release APK with optimized R8 settings
# Uses parallel workers and increased memory for faster builds

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Building Release APK (Optimized)" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# Stop any running Gradle daemons
Write-Host "Stopping Gradle daemon..." -ForegroundColor Yellow
./gradlew --stop

# Clean build
Write-Host "Cleaning build..." -ForegroundColor Yellow
Remove-Item -Recurse -Force app\build -ErrorAction SilentlyContinue

# Build with optimized settings
Write-Host "Building APK with optimized R8 settings..." -ForegroundColor Yellow
$env:GRADLE_OPTS = "-Xmx8g -XX:MaxMetaspaceSize=2g -XX:+UseG1GC"

./gradlew assembleRelease `
    --no-daemon `
    --max-workers=4 `
    --parallel

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
    Write-Host "Try: .\build_release_no_minify.ps1 (disables R8 minification)" -ForegroundColor Yellow
    exit 1
}
