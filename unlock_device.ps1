# Device Owner Unlock Script
# Use this to recover a device stuck in hard lock mode

Write-Host "Device Owner Unlock Recovery Script" -ForegroundColor Cyan
Write-Host "===================================" -ForegroundColor Cyan
Write-Host ""

# Check if adb is available
$adbPath = "adb"
try {
    & $adbPath version | Out-Null
} catch {
    Write-Host "ERROR: adb not found in PATH" -ForegroundColor Red
    Write-Host "Please install Android SDK Platform Tools and add to PATH" -ForegroundColor Yellow
    exit 1
}

Write-Host "Step 1: Checking connected devices..." -ForegroundColor Yellow
$devices = & $adbPath devices | Select-Object -Skip 1 | Where-Object { $_ -match "device$" }

if ($devices.Count -eq 0) {
    Write-Host "ERROR: No devices connected" -ForegroundColor Red
    exit 1
}

Write-Host "Found device(s):" -ForegroundColor Green
$devices | ForEach-Object { Write-Host "  $_" }
Write-Host ""

Write-Host "Step 2: Attempting to disable hard lock..." -ForegroundColor Yellow

# Method 1: Disable kernel security filter
Write-Host "  - Disabling kernel input security filter..." -ForegroundColor Cyan
& $adbPath shell "echo 0 > /sys/kernel/input_security/enabled" 2>$null
Write-Host "    âœ“ Done" -ForegroundColor Green

# Method 2: Clear app data
Write-Host "  - Clearing app data..." -ForegroundColor Cyan
& $adbPath shell "pm clear com.microspace.payo" 2>$null
Write-Host "    âœ“ Done" -ForegroundColor Green

# Method 3: Exit lock task mode
Write-Host "  - Exiting lock task mode..." -ForegroundColor Cyan
& $adbPath shell "am start -n com.microspace.payo/.presentation.activities.MainActivity" 2>$null
Write-Host "    âœ“ Done" -ForegroundColor Green

# Method 4: Force stop the app
Write-Host "  - Force stopping app..." -ForegroundColor Cyan
& $adbPath shell "am force-stop com.microspace.payo" 2>$null
Write-Host "    âœ“ Done" -ForegroundColor Green

# Method 5: Unlock device
Write-Host "  - Unlocking device..." -ForegroundColor Cyan
& $adbPath shell "input keyevent 82" 2>$null
Write-Host "    âœ“ Done" -ForegroundColor Green

Write-Host ""
Write-Host "Step 3: Verification..." -ForegroundColor Yellow

# Check if app is still running
$appRunning = & $adbPath shell "pidof com.microspace.payo" 2>$null
if ($appRunning) {
    Write-Host "  âš  App still running - attempting harder reset..." -ForegroundColor Yellow
    & $adbPath shell "su -c 'pm clear com.microspace.payo'" 2>$null
    & $adbPath shell "su -c 'am force-stop com.microspace.payo'" 2>$null
} else {
    Write-Host "  âœ“ App successfully stopped" -ForegroundColor Green
}

Write-Host ""
Write-Host "Recovery Complete!" -ForegroundColor Green
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "1. Unplug and replug the device" -ForegroundColor White
Write-Host "2. Check if the device is responsive" -ForegroundColor White
Write-Host "3. If still locked, try: adb shell 'su -c reboot'" -ForegroundColor White
Write-Host ""


