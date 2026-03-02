# Build Release APK - Production Build Script
# This is the main release build script with all optimizations

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Building Release APK (Production)" -ForegroundColor Cyan
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

        # Convert SHA256 Hex to Base64URL (for Device Owner provisioning)
        $bytes = [byte[]]::new($sha256.Length / 2)
        for ($i = 0; $i -lt $sha256.Length; $i += 2) {
            $bytes[$i / 2] = [Convert]::ToByte($sha256.Substring($i, 2), 16)
        }
        $base64 = [Convert]::ToBase64String($bytes)
        $base64url = $base64.Replace('+', '-').Replace('/', '_').TrimEnd('=')

        # Calculate Signature Checksum dynamically
        Write-Host "`nCalculating signature checksum..." -ForegroundColor Yellow
        $signatureChecksum = $null
        
        # Try using apksigner first (most reliable)
        try {
            $apksignerOutput = & apksigner verify --print-certs $apkPath 2>&1
            $sha256Line = $apksignerOutput | Select-String "SHA-256 digest:"
            if ($sha256Line) {
                $hexString = ($sha256Line -split ":")[1].Trim().Replace(":", "").Replace(" ", "")
                $sigBytes = [byte[]]::new($hexString.Length / 2)
                for ($i = 0; $i -lt $hexString.Length; $i += 2) {
                    $sigBytes[$i / 2] = [Convert]::ToByte($hexString.Substring($i, 2), 16)
                }
                $sigBase64 = [Convert]::ToBase64String($sigBytes)
                $signatureChecksum = $sigBase64.Replace('+', '-').Replace('/', '_').TrimEnd('=')
            }
        } catch {
            Write-Host "  apksigner not found, trying keytool..." -ForegroundColor Gray
        }
        
        # Fallback to keytool if apksigner failed
        if (-not $signatureChecksum) {
            try {
                # Read keystore properties
                $keystoreProps = @{}
                Get-Content "keystore.properties" | ForEach-Object {
                    if ($_ -match '^\s*([^#][^=]+?)\s*=\s*(.+?)\s*$') {
                        $keystoreProps[$matches[1]] = $matches[2]
                    }
                }
                
                $keystoreFile = $keystoreProps['storeFile']
                $keystoreAlias = $keystoreProps['keyAlias']
                $keystorePassword = $keystoreProps['storePassword']
                
                if ($keystoreFile -and $keystoreAlias -and $keystorePassword) {
                    # Export certificate and calculate hash
                    $tempCert = [System.IO.Path]::GetTempFileName()
                    & keytool -exportcert -alias $keystoreAlias -keystore $keystoreFile -storepass $keystorePassword -file $tempCert 2>$null
                    
                    if (Test-Path $tempCert) {
                        $certBytes = [System.IO.File]::ReadAllBytes($tempCert)
                        $sha256Obj = [System.Security.Cryptography.SHA256]::Create()
                        $hash = $sha256Obj.ComputeHash($certBytes)
                        $sigBase64 = [Convert]::ToBase64String($hash)
                        $signatureChecksum = $sigBase64.Replace('+', '-').Replace('/', '_').TrimEnd('=')
                        Remove-Item $tempCert -ErrorAction SilentlyContinue
                    }
                }
            } catch {
                Write-Host "  Could not calculate signature checksum: $_" -ForegroundColor Yellow
            }
        }
        
        # Fallback to known value if calculation failed
        if (-not $signatureChecksum) {
            $signatureChecksum = "Zy8Y37_S457_faQF8VVbbF3m8MbMaO4iTkTZGlgH2O4"
            Write-Host "  Using fallback signature checksum" -ForegroundColor Yellow
        }
        
        Write-Host "`nChecksums:" -ForegroundColor Cyan
        Write-Host "  Package Checksum (Base64URL): $base64url" -ForegroundColor Green
        Write-Host "  Signature Checksum:           $signatureChecksum" -ForegroundColor Green
        
        # Update provisioning-config.json
        $configPath = "provisioning-config.json"
        if (Test-Path $configPath) {
            Write-Host "`nUpdating $configPath..." -ForegroundColor Yellow
            $json = Get-Content -Path $configPath -Raw -Encoding UTF8

            # Update Package Checksum
            $patternPkg = '("android\.app\.extra\.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM"\s*:\s*)"[^"]*"'
            $replacementPkg = "`${1}`"$base64url`""
            $json = $json -replace $patternPkg, $replacementPkg

            # Update Signature Checksum
            $patternSig = '("android\.app\.extra\.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM"\s*:\s*)"[^"]*"'
            $replacementSig = "`${1}`"$signatureChecksum`""

            if ($json -match $patternSig) {
                $json = $json -replace $patternSig, $replacementSig
            } else {
                # If not present, add it after the package checksum
                $json = $json -replace $patternPkg, "`${0},`n  `"android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM`": `"$signatureChecksum`""
            }

            Set-Content -Path $configPath -Value $json -NoNewline -Encoding UTF8
            Write-Host "✅ Updated $configPath with new checksums" -ForegroundColor Green
        }
        
        Write-Host "`n========================================" -ForegroundColor Cyan
        Write-Host "✅ Build Complete and Config Updated!" -ForegroundColor Green
        Write-Host "========================================" -ForegroundColor Cyan
        
        Write-Host "`n📋 NEXT STEPS:" -ForegroundColor Yellow
        Write-Host "  1. Upload app-release.apk to GitHub Releases" -ForegroundColor White
        Write-Host "     URL: https://github.com/NEXIVO-COMPANY-LIMITED/DEVICE--OWNER/releases" -ForegroundColor Gray
        Write-Host ""
        Write-Host "  2. Verify provisioning-config.json has correct checksums" -ForegroundColor White
        Write-Host "     Package:   $base64url" -ForegroundColor Gray
        Write-Host "     Signature: $signatureChecksum" -ForegroundColor Gray
        Write-Host ""
        Write-Host "  3. Generate QR code from provisioning-config.json" -ForegroundColor White
        Write-Host ""
        Write-Host "  4. Test on factory-reset device" -ForegroundColor White
        Write-Host "     Command: adb shell am broadcast -a android.intent.action.FACTORY_RESET" -ForegroundColor Gray
        Write-Host ""
        Write-Host "📚 Documentation:" -ForegroundColor Yellow
        Write-Host "  - PROVISIONING_COMPLETE_GUIDE.md - Full workflow" -ForegroundColor Gray
        Write-Host "  - QR_CODE_VERIFICATION_GUIDE.md - QR code setup" -ForegroundColor Gray
        Write-Host "  - QUICK_FIX_REFERENCE.md - Troubleshooting" -ForegroundColor Gray
        Write-Host ""
    }
} else {
    Write-Host "❌ Build failed!" -ForegroundColor Red
    exit 1
}
