#!/bin/bash

# Heartbeat Validation Script
# Use this to validate heartbeat functionality on a connected Android device

echo "=========================================="
echo "Heartbeat Validation Script"
echo "=========================================="
echo ""

# Check if device is connected
echo "Checking for connected devices..."
DEVICE=$(adb devices | grep -v "List" | grep "device$" | awk '{print $1}' | head -1)

if [ -z "$DEVICE" ]; then
    echo "❌ No Android device connected"
    echo "Please connect a device and try again"
    exit 1
fi

echo "✓ Device found: $DEVICE"
echo ""

# Function to run adb command
run_adb() {
    adb -s "$DEVICE" "$@"
}

# 1. Check if HeartbeatVerificationService is running
echo "=========================================="
echo "1. Checking HeartbeatVerificationService"
echo "=========================================="
echo ""

run_adb shell dumpsys activity services | grep -i "HeartbeatVerificationService" > /dev/null

if [ $? -eq 0 ]; then
    echo "✓ HeartbeatVerificationService is running"
else
    echo "⚠ HeartbeatVerificationService may not be running"
fi

echo ""

# 2. Check recent logs
echo "=========================================="
echo "2. Recent Heartbeat Logs (last 20 lines)"
echo "=========================================="
echo ""

run_adb logcat -d | grep -i "heartbeat" | tail -20

echo ""

# 3. Check for errors
echo "=========================================="
echo "3. Checking for Heartbeat Errors"
echo "=========================================="
echo ""

ERROR_COUNT=$(run_adb logcat -d | grep -i "heartbeat" | grep -i "error" | wc -l)

if [ "$ERROR_COUNT" -eq 0 ]; then
    echo "✓ No heartbeat errors found"
else
    echo "⚠ Found $ERROR_COUNT error(s):"
    run_adb logcat -d | grep -i "heartbeat" | grep -i "error"
fi

echo ""

# 4. Check device registration
echo "=========================================="
echo "4. Checking Device Registration"
echo "=========================================="
echo ""

run_adb shell dumpsys package com.example.deviceowner | grep -i "device" > /dev/null

if [ $? -eq 0 ]; then
    echo "✓ Device owner app is installed"
else
    echo "❌ Device owner app not found"
fi

echo ""

# 5. Check network connectivity
echo "=========================================="
echo "5. Checking Network Connectivity"
echo "=========================================="
echo ""

run_adb shell ping -c 1 82.29.168.120 > /dev/null 2>&1

if [ $? -eq 0 ]; then
    echo "✓ Backend server is reachable (82.29.168.120)"
else
    echo "⚠ Cannot reach backend server"
fi

echo ""

# 6. Launch debug activity
echo "=========================================="
echo "6. Launching Heartbeat Debug Activity"
echo "=========================================="
echo ""

run_adb shell am start -n com.example.deviceowner/.ui.HeartbeatDebugActivity

if [ $? -eq 0 ]; then
    echo "✓ Debug activity launched"
    echo "  - Click 'Validate Data' to check data collection"
    echo "  - Click 'Validate Payload' to check payload format"
    echo "  - Click 'Test Sending' to send test heartbeat"
    echo "  - Click 'View History' to see recent heartbeats"
else
    echo "❌ Failed to launch debug activity"
fi

echo ""

# 7. Summary
echo "=========================================="
echo "Validation Summary"
echo "=========================================="
echo ""
echo "✓ Device connected: $DEVICE"
echo "✓ Check logs: adb logcat | grep HeartbeatVerificationService"
echo "✓ Test sending: Use HeartbeatDebugActivity"
echo "✓ Monitor network: Android Studio Network Profiler"
echo ""
echo "For detailed validation, see:"
echo "  - docs/HEARTBEAT_VALIDATION_GUIDE.md"
echo "  - docs/HEARTBEAT_QUICK_REFERENCE.md"
echo ""
