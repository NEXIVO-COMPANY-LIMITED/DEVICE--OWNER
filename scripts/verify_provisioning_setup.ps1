# Verify Provisioning Setup - Checks all common causes of "Can't set up device" error
# Usage: .\scripts\verify_provisioning_setup.ps1

$ErrorActionPreference = "Continue"
$ProjectRoot = if ($PSScriptRoot) { Resolve-Path (Join-Path $PSScriptRoot "..") } else { Get-Location }
Set-Location $ProjectRoot

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Provisioning Setup Verification" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$allChecksPassed = $true

# 1. Check package name matches
Write-Host "1. Checking package name..." -ForegroundColor Yellow
$buildGradle = "app\build.gradle.kts"
if (Test-Path $buildGradle) {
    $namespace = Select-String -Path $buildGradle -Pattern 'namespace\s*=\s*"([^"]+)"' | ForEach-Object { $_.Matches.Groups[1].Value }
    $applicationId = Select-String -Path $buildGradle -Pattern 'applicationId\s*=\s*"([^"]+)"' | ForEach-Object { $_.Matches.Groups[1].Value }
    $expectedPackage = if ($namespace) { $namespace } else { $applicationId }
    
    $configJson = "provisioning-config.json"
    if (Test-Path $configJson) {
        $componentName = Get-Content $configJson -Raw | ConvertFrom-Json | Select-Object -ExpandProperty "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME" -ErrorAction SilentlyContinue
        if ($componentName) {
            $packageFromConfig = $componentName.Split('/')[0]
            if ($packageFromConfig -eq $expectedPackage) {
                Write-Host "   ✓ Package name matches: $expectedPackage" -ForegroundColor Green
            } else {
                Write-Host "   ✗ Package name mismatch!" -ForegroundColor Red
                Write-Host "     Build.gradle: $expectedPackage" -ForegroundColor Gray
                Write-Host "     QR Config:    $packageFromConfig" -ForegroundColor Gray
                $allChecksPassed = $false
            }
        } else {
            Write-Host "   ⚠ Could not find COMPONENT_NAME in config" -ForegroundColor Yellow
        }
    } else {
        Write-Host "   ⚠ provisioning-config.json not found" -ForegroundColor Yellow
    }
} else {
    Write-Host "   ⚠ build.gradle.kts not found" -ForegroundColor Yellow
}

# 2. Check component name format
Write-Host ""
Write-Host "2. Checking component name..." -ForegroundColor Yellow
if (Test-Path $configJson) {
    $componentName = Get-Content $configJson -Raw | ConvertFrom-Json | Select-Object -ExpandProperty "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME" -ErrorAction SilentlyContinue
    if ($componentName) {
        if ($componentName -match '^[^/]+/[^/]+\.[^/]+\.[^/]+$') {
            Write-Host "   ✓ Component name format is valid: $componentName" -ForegroundColor Green
        } else {
            Write-Host "   ✗ Component name format invalid: $componentName" -ForegroundColor Red
            Write-Host "     Expected: package/package.Class" -ForegroundColor Gray
            $allChecksPassed = $false
        }
    }
}

# 3. Check layout file exists
Write-Host ""
Write-Host "3. Checking PolicyComplianceActivity layout..." -ForegroundColor Yellow
$layoutFile = "app\src\main\res\layout\activity_policy_compliance.xml"
if (Test-Path $layoutFile) {
    $hasButton = Select-String -Path $layoutFile -Pattern 'android:id="@\+id/continue_button"' -Quiet
    if ($hasButton) {
        Write-Host "   ✓ Layout file exists with continue_button" -ForegroundColor Green
    } else {
        Write-Host "   ✗ Layout file missing continue_button ID" -ForegroundColor Red
        $allChecksPassed = $false
    }
} else {
    Write-Host "   ✗ Layout file not found: $layoutFile" -ForegroundColor Red
    $allChecksPassed = $false
}

# 4. Check activities in manifest
Write-Host ""
Write-Host "4. Checking Android 12+ activities in manifest..." -ForegroundColor Yellow
$manifest = "app\src\main\AndroidManifest.xml"
if (Test-Path $manifest) {
    $hasProvisioningMode = Select-String -Path $manifest -Pattern "ProvisioningModeActivity" -Quiet
    $hasPolicyCompliance = Select-String -Path $manifest -Pattern "PolicyComplianceActivity" -Quiet
    $hasGetProvisioningMode = Select-String -Path $manifest -Pattern 'android.app.action.GET_PROVISIONING_MODE' -Quiet
    $hasAdminPolicyCompliance = Select-String -Path $manifest -Pattern 'android.app.action.ADMIN_POLICY_COMPLIANCE' -Quiet
    
    if ($hasProvisioningMode -and $hasPolicyCompliance -and $hasGetProvisioningMode -and $hasAdminPolicyCompliance) {
        Write-Host "   ✓ All Android 12+ intent handlers declared" -ForegroundColor Green
    } else {
        Write-Host "   ✗ Missing Android 12+ intent handlers:" -ForegroundColor Red
        if (-not $hasProvisioningMode) { Write-Host "     - ProvisioningModeActivity" -ForegroundColor Gray }
        if (-not $hasPolicyCompliance) { Write-Host "     - PolicyComplianceActivity" -ForegroundColor Gray }
        if (-not $hasGetProvisioningMode) { Write-Host "     - GET_PROVISIONING_MODE intent filter" -ForegroundColor Gray }
        if (-not $hasAdminPolicyCompliance) { Write-Host "     - ADMIN_POLICY_COMPLIANCE intent filter" -ForegroundColor Gray }
        $allChecksPassed = $false
    }
} else {
    Write-Host "   ⚠ AndroidManifest.xml not found" -ForegroundColor Yellow
}

# 5. Check device admin XML
Write-Host ""
Write-Host "5. Checking device admin configuration..." -ForegroundColor Yellow
$deviceAdminXml = "app\src\main\res\xml\device_admin_receiver.xml"
if (Test-Path $deviceAdminXml) {
    $hasPolicies = Select-String -Path $deviceAdminXml -Pattern "<uses-policies>" -Quiet
    if ($hasPolicies) {
        Write-Host "   ✓ Device admin XML exists with policies" -ForegroundColor Green
    } else {
        Write-Host "   ⚠ Device admin XML exists but may be missing policies" -ForegroundColor Yellow
    }
} else {
    Write-Host "   ✗ Device admin XML not found: $deviceAdminXml" -ForegroundColor Red
    $allChecksPassed = $false
}

# 6. Check checksum format
Write-Host ""
Write-Host "6. Checking checksum format..." -ForegroundColor Yellow
if (Test-Path $configJson) {
    $checksum = Get-Content $configJson -Raw | ConvertFrom-Json | Select-Object -ExpandProperty "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM" -ErrorAction SilentlyContinue
    if ($checksum) {
        # Base64URL format: A-Z, a-z, 0-9, -, _ (no +, /, or =)
        if ($checksum -match '^[A-Za-z0-9_-]+$') {
            Write-Host "   ✓ Checksum format is valid (Base64URL)" -ForegroundColor Green
            Write-Host "     Checksum: $checksum" -ForegroundColor Gray
        } else {
            Write-Host "   ✗ Checksum format invalid (should be Base64URL: A-Z, a-z, 0-9, -, _)" -ForegroundColor Red
            Write-Host "     Current: $checksum" -ForegroundColor Gray
            $allChecksPassed = $false
        }
    } else {
        Write-Host "   ⚠ Checksum not found in config" -ForegroundColor Yellow
    }
}

# 7. Check if APK exists and matches checksum
Write-Host ""
Write-Host "7. Checking APK and checksum match..." -ForegroundColor Yellow
$apkPath = "app\build\outputs\apk\release\app-release.apk"
if (Test-Path $apkPath) {
    Write-Host "   ✓ Release APK found" -ForegroundColor Green
    
    # Calculate checksum
    $hash = Get-FileHash -Path $apkPath -Algorithm SHA256
    $hexHash = $hash.Hash
    $bytes = [byte[]]::new($hexHash.Length / 2)
    for ($i = 0; $i -lt $hexHash.Length; $i += 2) {
        $bytes[$i / 2] = [Convert]::ToByte($hexHash.Substring($i, 2), 16)
    }
    $base64 = [Convert]::ToBase64String($bytes)
    $calculatedChecksum = $base64.Replace('+', '-').Replace('/', '_').TrimEnd('=')
    
    if (Test-Path $configJson) {
        $configChecksum = Get-Content $configJson -Raw | ConvertFrom-Json | Select-Object -ExpandProperty "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM" -ErrorAction SilentlyContinue
        if ($configChecksum) {
            if ($calculatedChecksum -eq $configChecksum) {
                Write-Host "   ✓ APK checksum matches config" -ForegroundColor Green
            } else {
                Write-Host "   ✗ APK checksum MISMATCH!" -ForegroundColor Red
                Write-Host "     APK checksum:    $calculatedChecksum" -ForegroundColor Gray
                Write-Host "     Config checksum: $configChecksum" -ForegroundColor Gray
                Write-Host "     Run: .\scripts\build_and_checksum.ps1 to fix" -ForegroundColor Yellow
                $allChecksPassed = $false
            }
        }
    }
} else {
    Write-Host "   ⚠ Release APK not found. Build it first: .\scripts\build_and_checksum.ps1" -ForegroundColor Yellow
}

# Summary
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
if ($allChecksPassed) {
    Write-Host "✓ All checks passed!" -ForegroundColor Green
    Write-Host ""
    Write-Host "Next steps:" -ForegroundColor Cyan
    Write-Host "1. Upload app-release.apk to GitHub release URL" -ForegroundColor White
    Write-Host "2. Generate QR code from provisioning-config.json" -ForegroundColor White
    Write-Host "3. Factory reset device and scan QR at Welcome screen" -ForegroundColor White
} else {
    Write-Host "✗ Some checks failed. Fix the issues above." -ForegroundColor Red
    Write-Host ""
    Write-Host "Common fixes:" -ForegroundColor Yellow
    Write-Host "- Run: .\scripts\build_and_checksum.ps1 (rebuilds APK and updates checksum)" -ForegroundColor White
    Write-Host "- See DEBUG_PROVISIONING.md for detailed troubleshooting" -ForegroundColor White
}
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
