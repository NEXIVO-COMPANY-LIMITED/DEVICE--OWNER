# Device Owner Firmware Security - Quick Reference & Implementation Checklist

## Executive Summary

This quick reference guide provides streamlined instructions for implementing firmware-level security for your Android Device Owner application. Use this alongside the main technical documentation.

---

## Pre-Implementation Checklist

### ✅ Prerequisites Verification

- [ ] Windows 10/11 Pro with 32GB RAM and 500GB+ free space
- [ ] WSL2 installed and configured
- [ ] Ubuntu 22.04 LTS running in WSL2
- [ ] Android Studio with NDK installed
- [ ] Google Pixel device (Pixel 6, 7, or 8)
- [ ] AOSP source downloaded (100GB+)
- [ ] Device-specific binaries downloaded
- [ ] Legal authorization to modify device
- [ ] User consent documentation prepared
- [ ] Backup and recovery plan established

### ✅ Development Environment

```bash
# Quick environment check
echo "Checking environment..."

# WSL2
wsl --version

# AOSP
ls -d ~/aosp/pixel

# NDK
$ANDROID_NDK_HOME/ndk-build --version

# Build tools
repo --version
make --version

echo "Environment check complete"
```

---

## Implementation Phases

### Phase 1: Environment Setup (Day 1-2)

**Time Required**: 1-2 days (mostly download time)

#### Step 1.1: Windows Configuration
```powershell
# Enable WSL2
dism.exe /online /enable-feature /featurename:Microsoft-Windows-Subsystem-Linux /all /norestart
dism.exe /online /enable-feature /featurename:VirtualMachinePlatform /all /norestart
Restart-Computer
wsl --set-default-version 2
```

#### Step 1.2: Install Ubuntu
```powershell
wsl --install -d Ubuntu-22.04
```

#### Step 1.3: Install Build Dependencies
```bash
sudo apt-get update && sudo apt-get install -y \
    git-core gnupg flex bison build-essential zip curl \
    zlib1g-dev gcc-multilib g++-multilib libc6-dev-i386 \
    libncurses5 lib32ncurses5-dev x11proto-core-dev libx11-dev \
    lib32z1-dev libgl1-mesa-dev libxml2-utils xsltproc unzip \
    fontconfig python3 python3-pip
```

#### Step 1.4: Download AOSP
```bash
mkdir -p ~/aosp/pixel && cd ~/aosp/pixel
repo init -u https://android.googlesource.com/platform/manifest -b android-13.0.0_r52
repo sync -c -j8  # This takes 4-8 hours
```

**Checkpoint**: ✅ AOSP source downloaded and synced

---

### Phase 2: Firmware Development (Day 3-7)

**Time Required**: 4-5 days

#### Step 2.1: Create Project Structure
```bash
mkdir -p ~/device-owner-firmware/{firmware/{bootloader,kernel,hal},tools,device_owner}
```

#### Step 2.2: Implement Bootloader Modifications

**File**: `firmware/bootloader/DeviceOwnerSecurity.c`

**Key Functions to Implement**:
- `LoadSecurityConfig()` - Load security settings from partition
- `IsFastbootAllowed()` - Check if fastboot should be blocked
- `IsRecoveryAllowed()` - Check if recovery should be blocked
- `EnforceOEMLock()` - Force OEM locked state
- `FastbootCommandHandler()` - Filter fastboot commands

**Implementation Status**:
- [ ] Security configuration structure defined
- [ ] Partition I/O functions implemented
- [ ] Boot mode enforcement implemented
- [ ] Fastboot command filtering implemented
- [ ] OEM lock enforcement implemented

#### Step 2.3: Implement Kernel Modules

**Module 1**: Button Security Driver

```bash
# File: firmware/kernel/drivers/input/gpio_keys_security.c
```

**Key Functions**:
- `should_block_combination()` - Detect and block button combos
- `gpio_keys_secure_isr()` - Modified interrupt handler
- `device_owner_active_store()` - Sysfs control interface

**Implementation Status**:
- [ ] Button state tracking implemented
- [ ] Combination detection logic implemented
- [ ] Interrupt handler modified
- [ ] Sysfs interface created
- [ ] Kernel module builds successfully

**Module 2**: Recovery Blocker

```bash
# File: firmware/kernel/drivers/misc/recovery_blocker.c
```

**Implementation Status**:
- [ ] Reboot interception implemented
- [ ] Misc partition write blocking implemented
- [ ] Sysfs interface created

**Module 3**: Factory Reset Protection

```bash
# File: firmware/kernel/drivers/misc/factory_reset_blocker.c
```

**Implementation Status**:
- [ ] Partition write protection implemented
- [ ] Format operation blocking implemented
- [ ] Device mapper hooks implemented

#### Step 2.4: Native Library Integration (REMOVED)

**Status**: ❌ **REMOVED** - Direct firmware integration components have been removed

**Previous Implementation**: JNI bridge for firmware communication has been removed to maintain separation between app and firmware layers.

**Note**: Firmware security is now handled independently of the Kotlin application.
- `nativeEnableSecurity()` - Enable all security features
- `nativeDisableSecurity()` - Disable with authorization
- `nativeCheckStatus()` - Query security status
- `nativeConfigureBootloader()` - Set bootloader config

**Implementation Status**:
- [ ] JNI functions implemented
- [ ] Sysfs I/O functions implemented
- [ ] Error handling implemented
- [ ] CMakeLists.txt configured
- [ ] Native library builds successfully

**Checkpoint**: ✅ All firmware components implemented

---

### Phase 3: Integration & Building (Day 8-10)

**Time Required**: 2-3 days

#### Step 3.1: Copy Firmware to AOSP
```bash
# Copy bootloader mods
cp -r ~/device-owner-firmware/firmware/bootloader/* \
      ~/aosp/pixel/bootable/bootloader/

# Copy kernel mods
cp -r ~/device-owner-firmware/firmware/kernel/* \
      ~/aosp/pixel/kernel/google/gs201/

# Copy HAL mods (if any)
cp -r ~/device-owner-firmware/firmware/hal/* \
      ~/aosp/pixel/hardware/
```

#### Step 3.2: Configure Build
```bash
cd ~/aosp/pixel
source build/envsetup.sh
lunch aosp_panther-userdebug  # For Pixel 7
export TARGET_KERNEL_CONFIG=device_owner_defconfig
```

#### Step 3.3: Build Firmware
```bash
# Full build (takes 2-4 hours on powerful machine)
make -j$(nproc)

# Or build specific components
make bootimage -j$(nproc)
make vendorbootimage -j$(nproc)
make systemimage -j$(nproc)
```

**Build Verification**:
- [ ] Build completes without errors
- [ ] boot.img created
- [ ] vendor_boot.img created
- [ ] system.img created
- [ ] Kernel modules (.ko files) created

**Checkpoint**: ✅ Firmware built successfully

---

### Phase 4: Device Owner App Integration (Day 11-13)

**Time Required**: 2-3 days

#### Step 4.1: Add Native Library to Project

**File**: `device_owner/app/build.gradle.kts`

```kotlin
android {
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
}
```

#### Step 4.2: Implement Firmware Security Manager

**File**: `device_owner/app/src/main/java/.../FirmwareSecurityManager.kt`

**Key Methods**:
- `enableFirmwareSecurity()` - Activate all protections
- `checkSecurityStatus()` - Verify security is active
- `configureBootloaderSecurity()` - Set bootloader config
- `disableFirmwareSecurity()` - Authorized removal

**Implementation Status**:
- [ ] FirmwareSecurityManager class created
- [ ] JNI method declarations added
- [ ] Coroutine-based async methods implemented
- [ ] Error handling implemented
- [ ] Sysfs interaction implemented

#### Step 4.3: Integrate with Device Owner Application

**File**: `device_owner/app/src/main/java/.../DeviceOwnerApplication.kt`

```kotlin
class DeviceOwnerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize firmware security
        lifecycleScope.launch {
            FirmwareSecurityManager.getInstance(this@DeviceOwnerApplication)
                .enableFirmwareSecurity()
        }
    }
}
```

**Implementation Status**:
- [ ] Application class modified
- [ ] Firmware security initialization added
- [ ] Lifecycle management implemented
- [ ] Error handling implemented

**Checkpoint**: ✅ Device Owner app integrated with firmware

---

### Phase 5: Testing & Validation (Day 14-17)

**Time Required**: 3-4 days

#### Step 5.1: Flash Test Device

```bash
# Unlock bootloader (will wipe device)
fastboot flashing unlock

# Flash custom firmware
fastboot flash boot boot.img
fastboot flash vendor_boot vendor_boot.img
fastboot flash system system.img
fastboot flash vendor vendor.img

# Configure Device Owner security
fastboot oem device-owner-config "enable=1 fastboot=0 recovery=0 oemlock=1"

# Reboot
fastboot reboot
```

#### Step 5.2: Verify Kernel Modules

```bash
adb wait-for-device
adb shell lsmod | grep -E "gpio_keys_security|recovery_blocker|factory_reset_blocker"
```

**Expected Output**:
```
gpio_keys_security     16384  0
recovery_blocker       16384  0
factory_reset_blocker  16384  0
```

#### Step 5.3: Test Security Features

| Test | Command | Expected Result | Status |
|------|---------|-----------------|--------|
| Button Combo | Press Vol- + Power | Device does NOT enter fastboot | [ ] |
| Button Combo | Press Vol+ + Power | Device does NOT enter recovery | [ ] |
| Fastboot Unlock | `fastboot flashing unlock` | "Command blocked" | [ ] |
| Fastboot Erase | `fastboot erase userdata` | "Command blocked" | [ ] |
| ADB Recovery | `adb reboot recovery` | Boots to normal mode | [ ] |
| Factory Reset | Settings → Reset | Blocked by Device Owner | [ ] |
| EDL Mode | Button combo for EDL | Device refuses EDL | [ ] |
| OEM Status | `fastboot getvar unlocked` | Returns "no" | [ ] |

#### Step 5.4: Security Status Check

```bash
# Check via Device Owner app
adb shell am start -n com.example.deviceowner/.SecurityStatusActivity

# Or check via command line
adb shell cat /sys/kernel/device_owner_security/device_owner_active
adb shell cat /sys/kernel/recovery_security/device_owner_active
adb shell cat /sys/kernel/factory_reset_protection/device_owner_active
```

**All should return**: `1` (active)

#### Step 5.5: Persistence Test

```bash
# Power off device
adb shell reboot -p

# Wait 1 minute

# Power on manually

# Verify security is still active
adb wait-for-device
adb shell cat /sys/kernel/device_owner_security/device_owner_active
```

**Expected**: `1` (security persists across power cycle)

**Checkpoint**: ✅ All tests passing

---

### Phase 6: Documentation & Deployment (Day 18-20)

**Time Required**: 2-3 days

#### Step 6.1: Create Deployment Package

```bash
mkdir -p release_package
cd release_package

# Copy firmware images
cp ~/aosp/pixel/out/target/product/panther/*.img .

# Copy Device Owner APK
cp ~/device-owner-firmware/device_owner/app/build/outputs/apk/release/app-release.apk .

# Create flash script
cat > flash_device_owner_firmware.sh << 'EOF'
#!/bin/bash
fastboot flash boot boot.img
fastboot flash vendor_boot vendor_boot.img
fastboot flash system system.img
fastboot flash vendor vendor.img
fastboot oem device-owner-config "enable=1 fastboot=0 recovery=0 oemlock=1"
fastboot reboot
EOF

chmod +x flash_device_owner_firmware.sh

# Create verification script
cat > verify_installation.sh << 'EOF'
#!/bin/bash
adb wait-for-device
echo "Checking kernel modules..."
adb shell lsmod | grep -E "security|blocker"
echo "Checking sysfs..."
adb shell cat /sys/kernel/device_owner_security/device_owner_active
echo "Installation verification complete"
EOF

chmod +x verify_installation.sh

# Package everything
zip -r device_owner_firmware_$(date +%Y%m%d).zip *
```

#### Step 6.2: Document Known Issues

**Known Issues Log**:
- [ ] Document any device-specific quirks
- [ ] Document workarounds for edge cases
- [ ] Document update/rollback procedures
- [ ] Document support contacts

#### Step 6.3: Create User Documentation

Create simplified guide for end users:
- How to identify if security is active
- What restrictions are in place
- How to contact support
- Emergency procedures (if any)

**Checkpoint**: ✅ Deployment package ready

---

## Production Deployment Checklist

### Pre-Deployment
- [ ] All tests passing on multiple test devices
- [ ] Security audit completed
- [ ] Legal review completed
- [ ] User agreements prepared
- [ ] Support infrastructure ready
- [ ] Rollback plan documented
- [ ] Backup procedures tested

### Deployment
- [ ] Pilot group identified (5-10 devices)
- [ ] Pilot deployment successful
- [ ] Pilot feedback collected and addressed
- [ ] Full deployment approved
- [ ] Deployment monitoring active
- [ ] Support team briefed

### Post-Deployment
- [ ] Device status monitoring implemented
- [ ] User feedback channel established
- [ ] Incident response plan active
- [ ] Update schedule defined
- [ ] Security review schedule set

---

## Quick Command Reference

### Device Flashing
```bash
# Enter fastboot
adb reboot bootloader

# Unlock bootloader
fastboot flashing unlock

# Flash firmware
fastboot flash boot boot.img
fastboot flash vendor_boot vendor_boot.img

# Configure security
fastboot oem device-owner-config "enable=1 fastboot=0 recovery=0 oemlock=1"

# Reboot
fastboot reboot
```

### Security Status Check
```bash
# Check kernel modules
adb shell lsmod | grep security

# Check sysfs
adb shell cat /sys/kernel/device_owner_security/device_owner_active

# Check from app
adb shell am start -n com.example.deviceowner/.MainActivity
```

### Enable/Disable Security
```bash
# Enable
adb shell "echo 1 > /sys/kernel/device_owner_security/device_owner_active"

# Disable (requires authorization)
adb shell am broadcast -a com.example.deviceowner.DISABLE_SECURITY \
    --es auth_token "AUTHORIZED_TOKEN"
```

### Debug Logging
```bash
# Kernel logs
adb logcat -b kernel | grep -E "gpio_keys|recovery|factory"

# Native library logs
adb logcat -s "FirmwareSecurity:*"

# Device Owner logs
adb logcat -s "DeviceOwner:*" "FirmwareSecurityManager:*"
```

---

## Troubleshooting Quick Reference

### Issue: Kernel modules not loading

**Symptoms**: Sysfs files don't exist

**Fix**:
```bash
# Check if modules exist
adb shell ls /system/vendor/lib/modules/

# Load manually
adb shell su -c "insmod /system/vendor/lib/modules/gpio_keys_security.ko"

# Check logs
adb logcat -b kernel | grep "module"
```

### Issue: Button blocking not working

**Symptoms**: Can still enter fastboot/recovery

**Fix**:
```bash
# Check module status
adb shell cat /sys/kernel/device_owner_security/device_owner_active

# Should return 1, if 0:
adb shell "echo 1 > /sys/kernel/device_owner_security/device_owner_active"

# Check interrupt handling
adb shell cat /proc/interrupts | grep gpio
```

### Issue: OEM lock not enforced

**Symptoms**: Bootloader shows as unlocked

**Fix**:
```bash
# Check bootloader config
fastboot getvar all | grep locked

# Reconfigure via fastboot
fastboot oem device-owner-config "oemlock=1"

# Or reflash bootloader
fastboot flash bootloader bootloader.img
```

### Issue: Device boot loop

**Symptoms**: Device continuously reboots

**Fix**:
```bash
# Boot to safe mode if possible
# Hold Volume Down during boot

# Flash stock firmware
fastboot flashall

# Or flash stock boot
fastboot flash boot boot_stock.img
fastboot reboot
```

---

## Success Criteria

Your implementation is successful when:

✅ **Security Features**
- [ ] Button combinations are blocked
- [ ] Fastboot commands are filtered
- [ ] Recovery mode is inaccessible
- [ ] OEM lock is enforced
- [ ] Factory reset is prevented
- [ ] EDL mode is disabled

✅ **Persistence**
- [ ] Security survives reboot
- [ ] Security survives power off/on
- [ ] Security survives Device Owner removal attempt

✅ **User Experience**
- [ ] Normal phone functions work
- [ ] Users can install apps
- [ ] Network connectivity works
- [ ] Calls and SMS work
- [ ] Performance is acceptable

✅ **Monitoring**
- [ ] Security status can be queried
- [ ] Logs are accessible
- [ ] Remote status reporting works
- [ ] Alerts trigger on security events

---

## Timeline Summary

| Phase | Duration | Milestone |
|-------|----------|-----------|
| Phase 1: Setup | 1-2 days | Environment ready |
| Phase 2: Development | 4-5 days | Firmware components complete |
| Phase 3: Building | 2-3 days | Firmware built |
| Phase 4: Integration | 2-3 days | App integrated |
| Phase 5: Testing | 3-4 days | All tests passing |
| Phase 6: Deployment | 2-3 days | Production ready |
| **Total** | **14-20 days** | **Complete implementation** |

---

## Support Resources

### Documentation
- Main Technical Guide: `Android_Firmware_Security_Implementation_Guide.md`
- Device Owner Docs: `Device_Owner_Documentation.md`
- API Reference: `API_Reference.md`

### Code Repositories
- Firmware modifications: `firmware/`
- Device Owner app: `device_owner/`
- Build tools: `tools/`

### Community Support
- Internal wiki: [link]
- Support email: support@example.com
- Emergency hotline: [number]

---

**Document Version**: 1.0  
**Last Updated**: January 2026  
**For**: Device Owner Firmware Implementation  
**Status**: Ready for Production Use

