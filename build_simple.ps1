# Simple build command - builds APK and generates checksum
# This will work even with minor compilation warnings

Write-Host "=== BUILDING APK WITH CHECKSUM ===" -ForegroundColor Yellow

# Stop any running Gradle processes
Write-Host "Stopping Gradle daemon..." -ForegroundColor Gray
& .\gradlew --stop 2>$null

# Clean build directory
Write-Host "Cleaning build..." -ForegroundColor Gray
if (Test-Path "app\build") {
    Remove-Item -Recurse -Force "app\build" -ErrorAction SilentlyContinue
}

# Build release APK
Write-Host "Building release APK..." -ForegroundColor Green
& .\gradlew assembleRelease --no-daemon --continue

# Check if APK was created
$apkPath = "app\build\outputs\apk\release\app-release.apk"
if (Test-Path $apkPath) {
    Write-Host "`nSUCCESS! APK built successfully" -ForegroundColor Green
    
    # Generate checksum
    Write-Host "Generating checksum..." -ForegroundColor Cyan
    $hash = Get-FileHash -Path $apkPath -Algorithm SHA256
    $hexHash = $hash.Hash
    $bytes = [byte[]]::new($hexHash.Length / 2)
    for ($i = 0; $i -lt $hexHash.Length; $i += 2) {
        $bytes[$i / 2] = [Convert]::ToByte($hexHash.Substring($i, 2), 16)
    }
    $base64 = [Convert]::ToBase64String($bytes)
    $base64url = $base64.Replace('+', '-').Replace('/', '_').TrimEnd('=')
    
    Write-Host "`n=== BUILD COMPLETE ===" -ForegroundColor Green
    Write-Host "APK: $((Resolve-Path $apkPath).Path)" -ForegroundColor White
    Write-Host "SHA256: $hexHash" -ForegroundColor White
    Write-Host "Base64URL Checksum: $base64url" -ForegroundColor Yellow
    
    # Update provisioning config if it exists
    $configPath = "provisioning-config.json"
    if (Test-Path $configPath) {
        try {
            $json = Get-Content -Path $configPath -Raw -Encoding UTF8
            $pattern = '("android\.app\.extra\.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM"\s*:\s*)"[^"]*"'
            $replacement = "`${1}`"$base64url`""
            $newJson = $json -replace $pattern, $replacement
            if ($newJson -ne $json) {
                Set-Content -Path $configPath -Value $newJson -NoNewline -Encoding UTF8
                Write-Host "Updated $configPath with new checksum" -ForegroundColor Green
            }
        }
        catch {
            Write-Host "Could not update provisioning config: $($_.Exception.Message)" -ForegroundColor Yellow
        }
    }
    
} else {
    Write-Host "`nBUILD FAILED - APK not found" -ForegroundColor Red
    Write-Host "Check the build output above for errors" -ForegroundColor Red
}