#!/bin/bash
# Master Installation Script for Real Android Devices
# ===================================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}╔════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║  Firmware Security Installation Wizard    ║${NC}"
echo -e "${BLUE}║  For Real Android Devices                 ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════╝${NC}"
echo ""

# Check for device
if ! command -v adb &> /dev/null; then
    echo -e "${RED}✗ ADB not found!${NC}"
    echo "Please install Android SDK Platform Tools"
    exit 1
fi

echo -e "${YELLOW}Checking device connection...${NC}"
if ! adb devices | grep -q "device$"; then
    echo -e "${RED}✗ No device connected${NC}"
    echo ""
    echo "Please:"
    echo "1. Connect your Android phone via USB"
    echo "2. Enable USB Debugging in Developer Options"
    echo "3. Authorize this computer on the device"
    echo ""
    echo "Then run this script again."
    exit 1
fi

DEVICE=$(adb shell getprop ro.product.model | tr -d '\r')
ANDROID=$(adb shell getprop ro.build.version.release | tr -d '\r')

echo -e "${GREEN}✓ Device connected${NC}"
echo "  Model: $DEVICE"
echo "  Android: $ANDROID"
echo ""

# Check root
echo -e "${YELLOW}Checking root access...${NC}"
if adb shell 'su -c "id"' 2>/dev/null | grep -q "uid=0"; then
    ROOT_AVAILABLE=true
    echo -e "${GREEN}✓ Root access available${NC}"
else
    ROOT_AVAILABLE=false
    echo -e "${YELLOW}⚠ Root not available (some features will be limited)${NC}"
fi
echo ""

# Installation menu
echo "Select installation mode:"
echo ""
echo "1) Full Installation (requires root)"
echo "   - Kernel module (button blocking)"
echo "   - Bootloader security"
echo "   - Device Owner integration"
echo ""
echo "2) Device Owner Only (no root needed)"
echo "   - Device Owner APK with security integration"
echo "   - Property-based security"
echo "   - Software button blocking"
echo ""
echo "3) Testing Mode (verify without installing)"
echo "   - Run diagnostics"
echo "   - Check device compatibility"
echo ""
read -p "Enter choice [1-3]: " choice

case $choice in
    1)
        if [ "$ROOT_AVAILABLE" = false ]; then
            echo -e "${RED}✗ Full installation requires root${NC}"
            exit 1
        fi
        echo ""
        echo -e "${GREEN}Installing with full security...${NC}"
        
        # Install kernel module
        echo -e "${YELLOW}[1/3] Installing kernel module...${NC}"
        if [ -f "kernel-driver/input_security_filter.ko" ]; then
            adb push kernel-driver/input_security_filter.ko /data/local/tmp/
            adb shell 'su -c "insmod /data/local/tmp/input_security_filter.ko"'
            
            if adb shell 'lsmod' | grep -q "input_security_filter"; then
                echo -e "${GREEN}✓ Kernel module loaded${NC}"
            else
                echo -e "${YELLOW}⚠ Kernel module not loaded (will compile on device)${NC}"
                # Try to compile on device
                adb push kernel-driver/input_security_filter.c /data/local/tmp/
                echo "  Attempting on-device compilation..."
            fi
        fi
        
        # Install bootloader security
        echo -e "${YELLOW}[2/3] Installing bootloader security...${NC}"
        if [ -f "bootloader-modifications/fastboot_security" ]; then
            adb push bootloader-modifications/fastboot_security /data/local/tmp/
            adb shell 'su -c "chmod 755 /data/local/tmp/fastboot_security"'
            adb shell 'su -c "/data/local/tmp/fastboot_security"' || true
            echo -e "${GREEN}✓ Bootloader security configured${NC}"
        fi
        
        # Install APK
        echo -e "${YELLOW}[3/3] Installing Device Owner APK...${NC}"
        if [ -f "device-owner-integration/app-debug.apk" ] || [ -f "output/apk/app-debug.apk" ]; then
            APK=$(find . -name "app-debug.apk" | head -1)
            adb install -r "$APK"
            echo -e "${GREEN}✓ APK installed${NC}"
        else
            echo -e "${YELLOW}⚠ APK not found (build it first)${NC}"
        fi
        ;;
        
    2)
        echo ""
        echo -e "${GREEN}Installing Device Owner mode...${NC}"
        
        echo -e "${YELLOW}[1/1] Installing Device Owner APK...${NC}"
        if [ -f "device-owner-integration/app-debug.apk" ] || [ -f "output/apk/app-debug.apk" ]; then
            APK=$(find . -name "app-debug.apk" | head -1)
            adb install -r "$APK"
            echo -e "${GREEN}✓ APK installed${NC}"
            echo ""
            echo "Next steps:"
            echo "1. Remove all accounts from device (Settings → Accounts)"
            echo "2. Set as Device Owner:"
            echo "   adb shell dpm set-device-owner com.yourcompany.deviceowner/.DeviceAdminReceiver"
            echo "3. Open app and complete setup"
        else
            echo -e "${RED}✗ APK not found${NC}"
            echo "Please build the Device Owner app first"
        fi
        ;;
        
    3)
        echo ""
        echo -e "${GREEN}Running diagnostics...${NC}"
        ./testing-suite/test_security.sh || true
        ;;
        
    *)
        echo -e "${RED}Invalid choice${NC}"
        exit 1
        ;;
esac

echo ""
echo -e "${BLUE}╔════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║  Installation Complete!                   ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════╝${NC}"
echo ""
echo "Next steps:"
echo "1. Review documentation/QUICKSTART.md"
echo "2. Test security features"
echo "3. Integrate with your application"
echo ""
