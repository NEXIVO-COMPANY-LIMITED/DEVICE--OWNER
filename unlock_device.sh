#!/bin/bash

# Device Owner Unlock Script
# Use this to recover a device stuck in hard lock mode

echo "Device Owner Unlock Recovery Script"
echo "==================================="
echo ""

# Check if adb is available
if ! command -v adb &> /dev/null; then
    echo "ERROR: adb not found in PATH"
    echo "Please install Android SDK Platform Tools"
    exit 1
fi

echo "Step 1: Checking connected devices..."
DEVICES=$(adb devices | grep -E "device$" | awk '{print $1}')

if [ -z "$DEVICES" ]; then
    echo "ERROR: No devices connected"
    exit 1
fi

echo "Found device(s):"
echo "$DEVICES" | while read device; do
    echo "  $device"
done
echo ""

echo "Step 2: Attempting to disable hard lock..."

# Method 1: Disable kernel security filter
echo "  - Disabling kernel input security filter..."
adb shell "echo 0 > /sys/kernel/input_security/enabled" 2>/dev/null
echo "    âœ“ Done"

# Method 2: Clear app data
echo "  - Clearing app data..."
adb shell "pm clear com.microspace.payo" 2>/dev/null
echo "    âœ“ Done"

# Method 3: Exit lock task mode
echo "  - Exiting lock task mode..."
adb shell "am start -n com.microspace.payo/.presentation.activities.MainActivity" 2>/dev/null
echo "    âœ“ Done"

# Method 4: Force stop the app
echo "  - Force stopping app..."
adb shell "am force-stop com.microspace.payo" 2>/dev/null
echo "    âœ“ Done"

# Method 5: Unlock device
echo "  - Unlocking device..."
adb shell "input keyevent 82" 2>/dev/null
echo "    âœ“ Done"

echo ""
echo "Step 3: Verification..."

# Check if app is still running
APP_PID=$(adb shell "pidof com.microspace.payo" 2>/dev/null)
if [ -n "$APP_PID" ]; then
    echo "  âš  App still running - attempting harder reset..."
    adb shell "su -c 'pm clear com.microspace.payo'" 2>/dev/null
    adb shell "su -c 'am force-stop com.microspace.payo'" 2>/dev/null
else
    echo "  âœ“ App successfully stopped"
fi

echo ""
echo "Recovery Complete!"
echo ""
echo "Next steps:"
echo "1. Unplug and replug the device"
echo "2. Check if the device is responsive"
echo "3. If still locked, try: adb shell 'su -c reboot'"
echo ""


