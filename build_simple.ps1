# Build Release APK - Simple and Reliable
# No complex arguments, just build with 8GB memory

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Building Release APK" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# Clean build
Write-Host "Cleaning build..." -ForegroundColor Yellow
Remove-Item -Recurse -Force app\build -ErrorAction SilentlyContinue

# Stop any existing daemon to avoid conflicts
Write-Host "Stopping any existing Gradle daemons..." -ForegroundColor Yellow
./gradlew --stop

# Set environment with better timeout settings
Write-Host "Setting JVM memory to 16GB with extended timeouts..." -ForegroundColor Yellow
$env:GRADLE_OPTS = "-Xmx16g -XX:MaxMetaspaceSize=4g -XX:+UseG1GC -Dorg.gradle.daemon.idletimeout=120000"

# Build with extended timeout and parallel compilation
Write-Host "Building APK..." -ForegroundColor Yellow
./gradlew assembleRelease --max-workers=4 --parallel

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
        
        # Convert SHA256 Hex to Base64URL (for Device Owner provisioning)
        $bytes = [byte[]]::new($sha256.Length / 2)
        for ($i = 0; $i -lt $sha256.Length; $i += 2) {
            $bytes[$i / 2] = [Convert]::ToByte($sha256.Substring($i, 2), 16)
        }
        $base64 = [Convert]::ToBase64String($bytes)
        $base64url = $base64.Replace('+', '-').Replace('/', '_').TrimEnd('=')
        
        Write-Host "`nChecksums:" -ForegroundColor Cyan
        Write-Host "  SHA256 (Hex):" -ForegroundColor Yellow
        Write-Host "    $sha256" -ForegroundColor Green
        Write-Host ""
        Write-Host "  SHA256 (Base64URL - for provisioning):" -ForegroundColor Yellow
        Write-Host "    $base64url" -ForegroundColor Green
        Write-Host ""
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
SHA256 (Hex): $sha256
SHA256 (Base64URL): $base64url
SHA1:   $sha1
MD5:    $md5

Installation Command:
adb install -r $apkPath

Verification Command:
(Get-FileHash -Path "$apkPath" -Algorithm SHA256).Hash

For Device Owner Provisioning:
Add this to provisioning-config.json:
"android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM": "$base64url"
"@
        
        $checksumContent | Out-File -FilePath $checksumFile -Encoding UTF8
        Write-Host "`n✅ Checksums saved to: $checksumFile" -ForegroundColor Green
        
        # ============================================================
        # AUTO-UPDATE provisioning-config.json with new checksum
        # ============================================================
        $configPath = "provisioning-config.json"
        if (Test-Path $configPath) {
            Write-Host "`nUpdating provisioning-config.json..." -ForegroundColor Yellow
            $json = Get-Content -Path $configPath -Raw -Encoding UTF8
            $pattern = '("android\.app\.extra\.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM"\s*:\s*)"[^"]*"'
            $replacement = "`${1}`"$base64url`""
            $newJson = $json -replace $pattern, $replacement
            
            if ($newJson -eq $json) {
                Write-Host "⚠️  WARNING: Could not find CHECKSUM key in $configPath" -ForegroundColor Yellow
                Write-Host "   Update it manually with: $base64url" -ForegroundColor Yellow
            } else {
                Set-Content -Path $configPath -Value $newJson -NoNewline -Encoding UTF8
                Write-Host "✅ Updated $configPath with new checksum" -ForegroundColor Green
                Write-Host "   Checksum: $base64url" -ForegroundColor Green
            }
        } else {
            Write-Host "⚠️  WARNING: $configPath not found" -ForegroundColor Yellow
            Write-Host "   Add this checksum manually:" -ForegroundColor Yellow
            Write-Host "   $base64url" -ForegroundColor Green
        }
        
        Write-Host "`n========================================" -ForegroundColor Cyan
        Write-Host "Next Steps:" -ForegroundColor Cyan
        Write-Host "  1. Install: adb install -r $apkPath" -ForegroundColor Yellow
        Write-Host "  2. Generate QR: Use provisioning-config.json" -ForegroundColor Yellow
        Write-Host "  3. Test: Scan QR on factory-reset device" -ForegroundColor Yellow
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
