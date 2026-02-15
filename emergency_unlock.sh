#!/bin/bash

# Emergency Device Owner Unlock Script
# This script forcefully removes the hard lock state from SharedPreferences

echo "╔════════════════════════════════════════════════════════════╗"
echo "║  EMERGENCY DEVICE OWNER UNLOCK - HARD LOCK RECOVERY       ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""

# Check if adb is available
if ! command -v adb &> /dev/null; then
    echo "❌ ERROR: adb not found in PATH"
    echo "Please install Android SDK Platform Tools"
    exit 1
fi

echo "Step 1: Checking connected devices..."
DEVICES=$(adb devices | grep -E "device$" | awk '{print $1}')

if [ -z "$DEVICES" ]; then
    echo "❌ ERROR: No devices connected"
    exit 1
fi

echo "✓ Found device(s):"
echo "$DEVICES" | while read device; do
    echo "  $device"
done
echo ""

echo "Step 2: Stopping Device Owner app..."
adb shell "am force-stop com.example.deviceowner" 2>/dev/null
sleep 1
echo "✓ App stopped"

echo ""
echo "Step 3: Clearing hard lock state from SharedPreferences..."

# Get the device's shared preferences directory
SHARED_PREFS_PATH="/data/data/com.example.deviceowner/shared_prefs/control_prefs.xml"

echo "  - Backing up current preferences..."
adb pull "$SHARED_PREFS_PATH" "control_prefs_backup.xml" 2>/dev/null
echo "    ✓ Backup saved to: control_prefs_backup.xml"

echo "  - Creating new preferences file (unlocked state)..."

# Create new XML with unlocked state
cat > control_prefs_new.xml << 'EOF'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="state">unlocked</string>
    <string name="reason"></string>
    <long name="lock_timestamp" value="0" />
    <boolean name="hard_lock_requested" value="false" />
</map>
EOF

echo "    ✓ New preferences file created"

echo "  - Pushing new preferences to device..."
adb push control_prefs_new.xml "$SHARED_PREFS_PATH" 2>/dev/null
echo "    ✓ Preferences updated"

# Clean up temp file
rm -f control_prefs_new.xml

echo ""
echo "Step 4: Disabling kernel security filter..."
adb shell "echo 0 > /sys/kernel/input_security/enabled" 2>/dev/null
echo "✓ Kernel filter disabled"

echo ""
echo "Step 5: Clearing app cache and data..."
adb shell "pm clear com.example.deviceowner" 2>/dev/null
echo "✓ App cache cleared"

echo ""
echo "Step 6: Restarting device..."
adb shell "reboot" 2>/dev/null
echo "✓ Device rebooting..."

echo ""
echo "╔════════════════════════════════════════════════════════════╗"
echo "║  ✓ UNLOCK COMPLETE - Device is rebooting                  ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""
echo "What to do next:"
echo "1. Wait 30-60 seconds for device to reboot"
echo "2. Device should now be unlocked and responsive"
echo "3. If still locked, try: adb shell 'su -c reboot recovery'"
echo ""
echo "Backup file saved: control_prefs_backup.xml"
echo ""
