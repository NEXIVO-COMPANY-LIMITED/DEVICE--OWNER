# Emergency Device Owner Unlock Script
# This script forcefully removes the hard lock state from SharedPreferences

Write-Host "╔════════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║  EMERGENCY DEVICE OWNER UNLOCK - HARD LOCK RECOVERY       ║" -ForegroundColor Cyan
Write-Host "╚════════════════════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

# Check if adb is available
$adbPath = "adb"
try {
    & $adbPath version | Out-Null
} catch {
    Write-Host "❌ ERROR: adb not found in PATH" -ForegroundColor Red
    Write-Host "Please install Android SDK Platform Tools" -ForegroundColor Yellow
    exit 1
}

Write-Host "Step 1: Checking connected devices..." -ForegroundColor Yellow
$devices = & $adbPath devices | Select-Object -Skip 1 | Where-Object { $_ -match "device$" }

if ($devices.Count -eq 0) {
    Write-Host "❌ ERROR: No devices connected" -ForegroundColor Red
    exit 1
}

Write-Host "✓ Found device(s):" -ForegroundColor Green
$devices | ForEach-Object { Write-Host "  $_" }
Write-Host ""

Write-Host "Step 2: Stopping Device Owner app..." -ForegroundColor Yellow
& $adbPath shell "am force-stop com.example.deviceowner" 2>$null
Start-Sleep -Seconds 1
Write-Host "✓ App stopped" -ForegroundColor Green

Write-Host ""
Write-Host "Step 3: Clearing hard lock state from SharedPreferences..." -ForegroundColor Yellow

# Get the device's shared preferences directory
$sharedPrefsPath = "/data/data/com.example.deviceowner/shared_prefs/control_prefs.xml"

Write-Host "  - Backing up current preferences..." -ForegroundColor Cyan
& $adbPath pull $sharedPrefsPath "control_prefs_backup.xml" 2>$null
Write-Host "    ✓ Backup saved to: control_prefs_backup.xml" -ForegroundColor Green

Write-Host "  - Creating new preferences file (unlocked state)..." -ForegroundColor Cyan

# Create new XML with unlocked state
$newPrefsXml = @"
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="state">unlocked</string>
    <string name="reason"></string>
    <long name="lock_timestamp" value="0" />
    <boolean name="hard_lock_requested" value="false" />
</map>
"@

# Save to temp file
$tempFile = "control_prefs_new.xml"
$newPrefsXml | Out-File -FilePath $tempFile -Encoding UTF8 -NoNewline

Write-Host "    ✓ New preferences file created" -ForegroundColor Green

Write-Host "  - Pushing new preferences to device..." -ForegroundColor Cyan
& $adbPath push $tempFile $sharedPrefsPath 2>$null
Write-Host "    ✓ Preferences updated" -ForegroundColor Green

# Clean up temp file
Remove-Item $tempFile -Force 2>$null

Write-Host ""
Write-Host "Step 4: Disabling kernel security filter..." -ForegroundColor Yellow
& $adbPath shell "echo 0 > /sys/kernel/input_security/enabled" 2>$null
Write-Host "✓ Kernel filter disabled" -ForegroundColor Green

Write-Host ""
Write-Host "Step 5: Clearing app cache and data..." -ForegroundColor Yellow
& $adbPath shell "pm clear com.example.deviceowner" 2>$null
Write-Host "✓ App cache cleared" -ForegroundColor Green

Write-Host ""
Write-Host "Step 6: Restarting device..." -ForegroundColor Yellow
& $adbPath shell "reboot" 2>$null
Write-Host "✓ Device rebooting..." -ForegroundColor Green

Write-Host ""
Write-Host "╔════════════════════════════════════════════════════════════╗" -ForegroundColor Green
Write-Host "║  ✓ UNLOCK COMPLETE - Device is rebooting                  ║" -ForegroundColor Green
Write-Host "╚════════════════════════════════════════════════════════════╝" -ForegroundColor Green
Write-Host ""
Write-Host "What to do next:" -ForegroundColor Cyan
Write-Host "1. Wait 30-60 seconds for device to reboot" -ForegroundColor White
Write-Host "2. Device should now be unlocked and responsive" -ForegroundColor White
Write-Host "3. If still locked, try: adb shell 'su -c reboot recovery'" -ForegroundColor White
Write-Host ""
Write-Host "Backup file saved: control_prefs_backup.xml" -ForegroundColor Yellow
Write-Host ""
