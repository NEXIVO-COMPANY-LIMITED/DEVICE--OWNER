# Provisioning Configuration Validator
# This script validates your provisioning setup and identifies issues

param(
    [string]$ApkPath = "app-release.apk",
    [string]$ConfigPath = "provisioning-config.json"
)

Write-Host "=== PAYO Device Provisioning Validator ===" -ForegroundColor Cyan
Write-Host ""

# Color functions
function Write-Success { Write-Host "✅ $args" -ForegroundColor Green }
function Write-Error { Write-Host "❌ $args" -ForegroundColor Red }
function Write-Warning { Write-Host "⚠️  $args" -ForegroundColor Yellow }
function Write-Info { Write-Host "ℹ️  $args" -ForegroundColor Blue }

# 1. Check APK file
Write-Host "1. Checking APK File..." -ForegroundColor Cyan
if (Test-Path $ApkPath) {
    Write-Success "APK file found: $ApkPath"
    $apkSize = (Get-Item $ApkPath).Length / 1MB
    Write-Info "APK size: $([Math]::Round($apkSize, 2)) MB"
} else {
    Write-Error "APK file not found: $ApkPath"
    Write-Info "Expected location: $((Get-Location).Path)\$ApkPath"
}

# 2. Calculate APK Checksum
Write-Host ""
Write-Host "2. Calculating APK Checksum..." -ForegroundColor Cyan
if (Test-Path $ApkPath) {
    try {
        $hash = (Get-FileHash -Path $ApkPath -Algorithm SHA256).Hash
        Write-Success "SHA-256 Hash: $hash"
        
        # Convert to Base64URL
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($hash)
        $base64 = [Convert]::ToBase64String($bytes)
        $base64url = $base64 -replace '\+', '-' -replace '/', '_' -replace '=', ''
        Write-Success "Base64URL: $base64url"
        Write-Info "Use this checksum in provisioning-config.json"
    } catch {
        Write-Error "Failed to calculate checksum: $_"
    }
}

# 3. Check Provisioning Config
Write-Host ""
Write-Host "3. Validating Provisioning Config..." -ForegroundColor Cyan
if (Test-Path $ConfigPath) {
    Write-Success "Config file found: $ConfigPath"
    try {
        $config = Get-Content $ConfigPath | ConvertFrom-Json
        Write-Success "Config is valid JSON"
        
        # Check required fields
        $requiredFields = @(
            "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME",
            "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION",
            "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM",
            "android.app.extra.PROVISIONING_WIFI_SSID",
            "android.app.extra.PROVISIONING_WIFI_PASSWORD",
            "android.app.extra.PROVISIONING_WIFI_SECURITY_TYPE"
        )
        
        foreach ($field in $requiredFields) {
            if ($config.PSObject.Properties.Name -contains $field) {
                $value = $config.$field
                if ($value) {
                    Write-Success "✓ $field"
                } else {
                    Write-Warning "⚠ $field is empty"
                }
            } else {
                Write-Error "✗ Missing: $field"
            }
        }
        
        # Validate component name format
        $componentName = $config."android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME"
        if ($componentName -match "^[a-z0-9.]+/[a-z0-9.]+$") {
            Write-Success "Component name format is valid"
        } else {
            Write-Error "Component name format invalid: $componentName"
            Write-Info "Expected format: com.package/com.package.ClassName"
        }
        
        # Validate APK URL
        $apkUrl = $config."android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION"
        if ($apkUrl -match "^https://") {
            Write-Success "APK URL uses HTTPS"
        } else {
            Write-Error "APK URL must use HTTPS: $apkUrl"
        }
        
        # Validate checksum format
        $checksum = $config."android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM"
        if ($checksum -match "^[A-Za-z0-9\-_]+=*$") {
            Write-Success "Checksum format is valid (Base64URL)"
        } else {
            Write-Error "Checksum format invalid: $checksum"
            Write-Info "Checksum must be Base64URL encoded"
        }
        
        # Validate WiFi security type
        $wifiSecurity = $config."android.app.extra.PROVISIONING_WIFI_SECURITY_TYPE"
        if ($wifiSecurity -in @("WPA", "WEP", "OPEN")) {
            Write-Success "WiFi security type is valid: $wifiSecurity"
        } else {
            Write-Error "Invalid WiFi security type: $wifiSecurity (must be WPA, WEP, or OPEN)"
        }
        
        # Validate locale format
        $locale = $config."android.app.extra.PROVISIONING_LOCALE"
        if ($locale -match "^[a-z]{2}_[A-Z]{2}$") {
            Write-Success "Locale format is valid: $locale"
        } else {
            Write-Warning "Locale format may be invalid: $locale (expected: xx_XX)"
        }
        
    } catch {
        Write-Error "Failed to parse config: $_"
    }
} else {
    Write-Error "Config file not found: $ConfigPath"
}

# 4. Check Manifest
Write-Host ""
Write-Host "4. Checking AndroidManifest.xml..." -ForegroundColor Cyan
$manifestPath = "app/src/main/AndroidManifest.xml"
if (Test-Path $manifestPath) {
    Write-Success "Manifest found"
    $manifest = Get-Content $manifestPath -Raw
    
    if ($manifest -match 'android:name=".receivers.admin.AdminReceiver"') {
        Write-Success "AdminReceiver is declared"
    } else {
        Write-Error "AdminReceiver not found in manifest"
    }
    
    if ($manifest -match 'android.permission.BIND_DEVICE_ADMIN') {
        Write-Success "BIND_DEVICE_ADMIN permission is set"
    } else {
        Write-Error "BIND_DEVICE_ADMIN permission not found"
    }
    
    if ($manifest -match 'device_admin_receiver') {
        Write-Success "Device admin meta-data is configured"
    } else {
        Write-Error "Device admin meta-data not found"
    }
} else {
    Write-Error "Manifest not found: $manifestPath"
}

# 5. Check Device Admin XML
Write-Host ""
Write-Host "5. Checking Device Admin XML..." -ForegroundColor Cyan
$deviceAdminPath = "app/src/main/res/xml/device_admin_receiver.xml"
if (Test-Path $deviceAdminPath) {
    Write-Success "Device admin XML found"
    $deviceAdmin = Get-Content $deviceAdminPath -Raw
    
    $policies = @(
        "limit-password",
        "force-lock",
        "wipe-data",
        "disable-camera",
        "disable-uninstall-apps",
        "disable-account-management"
    )
    
    foreach ($policy in $policies) {
        if ($deviceAdmin -match "<$policy" -or $deviceAdmin -match "<$policy />") {
            Write-Success "Policy found: $policy"
        } else {
            Write-Warning "Policy not found: $policy"
        }
    }
} else {
    Write-Error "Device admin XML not found: $deviceAdminPath"
}

# 6. Summary
Write-Host ""
Write-Host "=== Summary ===" -ForegroundColor Cyan
Write-Info "Next steps:"
Write-Host "1. Verify the calculated checksum matches your APK"
Write-Host "2. Update provisioning-config.json with correct checksum if needed"
Write-Host "3. Rebuild the APK: ./gradlew.bat assembleRelease"
Write-Host "4. Sign the APK with your release keystore"
Write-Host "5. Upload to GitHub release and update config URL"
Write-Host "6. Test provisioning on a clean device"
Write-Host ""
Write-Info "For detailed troubleshooting, see: PROVISIONING_TROUBLESHOOTING.md"
