#!/bin/bash

# Heartbeat Status Checker
# Usage: ./check_heartbeat_status.sh

echo "=========================================="
echo "HEARTBEAT STATUS CHECKER"
echo "=========================================="
echo ""

# Check if device is connected
echo "1ï¸âƒ£ Checking ADB connection..."
if ! adb devices | grep -q "device$"; then
    echo "âŒ No device connected"
    exit 1
fi
echo "âœ… Device connected"
echo ""

# Check if app is installed
echo "2ï¸âƒ£ Checking if app is installed..."
if ! adb shell pm list packages | grep -q "com.microspace.payo"; then
    echo "âŒ App not installed"
    exit 1
fi
echo "âœ… App installed"
echo ""

# Check device registration
echo "3ï¸âƒ£ Checking device registration..."
DEVICE_ID=$(adb shell run-as com.microspace.payo cat /data/data/com.microspace.payo/shared_prefs/device_data.xml 2>/dev/null | grep -oP 'device_id_for_heartbeat" value="\K[^"]+')
if [ -z "$DEVICE_ID" ]; then
    echo "âŒ Device not registered (no device_id found)"
else
    echo "âœ… Device registered: $DEVICE_ID"
fi
echo ""

# Check if SecurityMonitorService is running
echo "4ï¸âƒ£ Checking if SecurityMonitorService is running..."
if adb shell dumpsys activity services | grep -q "SecurityMonitorService"; then
    echo "âœ… SecurityMonitorService is running"
else
    echo "âŒ SecurityMonitorService is NOT running"
fi
echo ""

# Check recent heartbeat logs
echo "5ï¸âƒ£ Checking recent heartbeat logs (last 20 lines)..."
echo "Clearing logcat buffer..."
adb logcat -c
echo "Waiting 5 seconds for logs..."
sleep 5
echo ""
echo "Recent logs:"
adb logcat -d | grep -E "HeartbeatService|HeartbeatAlarm|ApiClient|SecurityMonitor" | tail -20
echo ""

# Check network connectivity
echo "6ï¸âƒ£ Checking network connectivity..."
if adb shell ping -c 1 8.8.8.8 > /dev/null 2>&1; then
    echo "âœ… Device has internet connection"
else
    echo "âŒ Device has NO internet connection"
fi
echo ""

# Check API key
echo "7ï¸âƒ£ Checking API key configuration..."
API_KEY=$(adb shell run-as com.microspace.payo grep -r "DEVICE_API_KEY" /data/data/com.microspace.payo/ 2>/dev/null | head -1)
if [ -z "$API_KEY" ]; then
    echo "âš ï¸  Could not verify API key (may be in compiled code)"
else
    echo "âœ… API key found in configuration"
fi
echo ""

echo "=========================================="
echo "SUMMARY"
echo "=========================================="
echo ""
echo "To see real-time heartbeat logs, run:"
echo "  adb logcat | grep -E 'HeartbeatService|HeartbeatAlarm|ApiClient'"
echo ""
echo "To manually trigger a heartbeat, run:"
if [ -n "$DEVICE_ID" ]; then
    echo "  adb shell am broadcast -a com.microspace.payo.HEARTBEAT_ALARM -e device_id '$DEVICE_ID'"
else
    echo "  adb shell am broadcast -a com.microspace.payo.HEARTBEAT_ALARM"
fi
echo ""
echo "To restart the app, run:"
echo "  adb shell am force-stop com.microspace.payo"
echo "  adb shell am start -n com.microspace.payo/.ui.activities.main.MainActivity"
echo ""


