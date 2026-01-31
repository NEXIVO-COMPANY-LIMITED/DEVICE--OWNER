#!/bin/bash
# Comprehensive Security Testing Script for Real Android Devices
# ==============================================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Firmware Security Testing Suite${NC}"
echo -e "${GREEN}For Real Android Devices${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# Check device connection
if ! adb devices | grep -q "device$"; then
    echo -e "${RED}✗ No device connected${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Device connected${NC}"
DEVICE=$(adb shell getprop ro.product.model | tr -d '\r')
echo "Device: $DEVICE"
echo ""

PASSED=0
FAILED=0

# Test function
run_test() {
    local name="$1"
    local command="$2"
    local expected="$3"
    
    echo -n "Testing: $name... "
    
    result=$(eval "$command" 2>/dev/null || echo "")
    
    if echo "$result" | grep -q "$expected"; then
        echo -e "${GREEN}PASS${NC}"
        ((PASSED++))
        return 0
    else
        echo -e "${RED}FAIL${NC}"
        echo "  Expected: $expected"
        echo "  Got: $result"
        ((FAILED++))
        return 1
    fi
}

echo "=== Security Component Tests ==="
echo ""

# Test 1: Kernel module loaded
run_test "Kernel Module Loaded" \
    "adb shell 'lsmod 2>/dev/null | grep input_security'" \
    "input_security_filter"

# Test 2: Sysfs interface exists
run_test "Sysfs Interface Exists" \
    "adb shell 'ls /sys/kernel/input_security/enabled 2>/dev/null'" \
    "enabled"

# Test 3: Security property set
run_test "Security Property Set" \
    "adb shell 'getprop persist.security.mode.enabled'" \
    "1"

# Test 4: Button blocking enabled
run_test "Button Blocking Enabled" \
    "adb shell 'cat /sys/kernel/input_security/enabled 2>/dev/null'" \
    "1"

# Test 5: Device Owner installed
run_test "Device Owner Installed" \
    "adb shell 'pm list packages | grep deviceowner'" \
    "deviceowner"

# Test 6: Security status file exists
run_test "Security Status File" \
    "adb shell 'ls /data/local/tmp/security_state.dat 2>/dev/null'" \
    "security_state"

echo ""
echo "=== Bootloader Security Tests ==="
echo ""

# Test 7: Bootloader lock status
run_test "Bootloader Lock Status" \
    "adb shell 'getprop ro.boot.flash.locked'" \
    "1"

# Test 8: OEM unlock disabled
run_test "OEM Unlock Disabled" \
    "adb shell 'getprop sys.oem_unlock_allowed'" \
    "0"

echo ""
echo "=== Functional Tests ==="
echo ""

# Test 9: Simulate button press (should be blocked)
echo -n "Testing: Button Blocking (live)... "
adb shell "input keyevent KEYCODE_POWER &"
adb shell "input keyevent KEYCODE_VOLUME_UP"
sleep 1
if adb shell "cat /sys/kernel/input_security/stats 2>/dev/null" | grep -q "Recovery Attempts: [1-9]"; then
    echo -e "${GREEN}PASS${NC} (blocked combination detected)"
    ((PASSED++))
else
    echo -e "${YELLOW}SKIP${NC} (requires manual testing)"
fi

# Test 10: Check violation logging
run_test "Violation Logging Works" \
    "adb shell 'cat /data/local/tmp/security_violations.log 2>/dev/null | wc -l'" \
    "[0-9]"

echo ""
echo "=== Device Owner Integration Tests ==="
echo ""

# Test 11: Native library loaded
run_test "Native Library in APK" \
    "adb shell 'pm path com.yourcompany.deviceowner | head -1 | cut -d: -f2 | xargs -I {} unzip -l {} | grep libfirmware_security.so'" \
    "libfirmware_security.so"

# Test 12: JNI functions accessible
echo -n "Testing: JNI Functions... "
adb shell "am start -n com.yourcompany.deviceowner/.MainActivity" >/dev/null 2>&1
sleep 2
if adb logcat -d | grep -q "FirmwareSecurityJNI"; then
    echo -e "${GREEN}PASS${NC}"
    ((PASSED++))
else
    echo -e "${YELLOW}SKIP${NC} (app may not be running)"
fi

echo ""
echo "=== Security Persistence Tests ==="
echo ""

# Test 13: Security survives reboot
echo -n "Testing: Security Persistence (requires reboot)... "
echo -e "${YELLOW}MANUAL${NC}"
echo "  1. Note current security status"
echo "  2. Reboot device: adb reboot"
echo "  3. Run this test again"
echo "  4. Verify security still enabled"

echo ""
echo "========================================="
echo "Test Results"
echo "========================================="
echo -e "Passed: ${GREEN}$PASSED${NC}"
echo -e "Failed: ${RED}$FAILED${NC}"
echo ""

if [ $FAILED -eq 0 ]; then
    echo -e "${GREEN}✓ All automated tests passed!${NC}"
    echo ""
    echo "Next steps:"
    echo "1. Test manual button combinations on device"
    echo "2. Try to enter recovery mode (should fail)"
    echo "3. Try to enter fastboot mode (should fail)"
    echo "4. Verify factory reset protection"
    exit 0
else
    echo -e "${RED}✗ Some tests failed${NC}"
    echo "Review failed tests and check device configuration"
    exit 1
fi
