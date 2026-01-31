# Force build command - builds APK ignoring most errors
# Use this if the simple build fails

Write-Host "=== FORCE BUILDING APK WITH CHECKSUM ===" -ForegroundColor Yellow

# Stop Gradle and clean aggressively
Write-Host "Force stopping all Gradle processes..." -ForegroundColor Gray
& .\gradlew --stop 2>$null
Get-Process | Where-Object {$_.ProcessName -like "*gradle*" -or $_.ProcessName -like "*java*"} | Stop-Process -Force -ErrorAction SilentlyContinue

# Clean everything
Write-Host "Force cleaning..." -ForegroundColor Gray
if (Test-Path "app\build") {
    Remove-Item -Recurse -Force "app\build" -ErrorAction SilentlyContinue
}
if (Test-Path "build") {
    Remove-Item -Recurse -Force "build" -ErrorAction SilentlyContinue
}
if (Test-Path ".gradle") {
    Remove-Item -Recurse -Force ".gradle" -ErrorAction SilentlyContinue
}

# Force build with maximum error tolerance
Write-Host "Force building (ignoring errors)..." -ForegroundColor Green
& .\gradlew assembleRelease --no-daemon --continue --parallel --max-workers=1 --warning-mode=none 2>$null

# Alternative build if first fails
if (!(Test-Path "app\build\outputs\apk\release\app-release.apk")) {
    Write-Host "Trying alternative build method..." -ForegroundColor Yellow
    & .\gradlew :app:assembleRelease --no-daemon --continue 2>$null
}

# Check result
$apkPath = "app\build\outputs\apk\release\app-release.apk"
if (Test-Path $apkPath) {
    Write-Host "`nFORCE BUILD SUCCESS!" -ForegroundColor Green
    
    # Generate checksum
    $hash = Get-FileHash -Path $apkPath -Algorithm SHA256
    $hexHash = $hash.Hash
    $bytes = [byte[]]::new($hexHash.Length / 2)
    for ($i = 0; $i -lt $hexHash.Length; $i += 2) {
        $bytes[$i / 2] = [Convert]::ToByte($hexHash.Substring($i, 2), 16)
    }
    $base64 = [Convert]::ToBase64String($bytes)
    $base64url = $base64.Replace('+', '-').Replace('/', '_').TrimEnd('=')
    
    Write-Host "`n=== FORCE BUILD COMPLETE ===" -ForegroundColor Green
    Write-Host "APK: $((Resolve-Path $apkPath).Path)" -ForegroundColor White
    Write-Host "SHA256: $hexHash" -ForegroundColor White  
    Write-Host "Base64URL Checksum: $base64url" -ForegroundColor Yellow
    
    Write-Host "`nAPK is ready for deployment!" -ForegroundColor Green
    
} else {
    Write-Host "`nFORCE BUILD FAILED" -ForegroundColor Red
    Write-Host "Try fixing compilation errors manually" -ForegroundColor Red
    
    # Show any APKs that might exist
    $allApks = Get-ChildItem -Path "app\build" -Filter "*.apk" -Recurse -ErrorAction SilentlyContinue
    if ($allApks) {
        Write-Host "`nFound these APK files:" -ForegroundColor Yellow
        $allApks | ForEach-Object { Write-Host "  $($_.FullName)" -ForegroundColor White }
    }
}