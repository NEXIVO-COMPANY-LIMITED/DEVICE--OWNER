# 🧪 COMPLETE VERIFICATION & TESTING GUIDE
## How to Confirm Firmware Security Works Perfectly

---

## 🎯 WHAT YOU'RE TESTING

After integrating the firmware code, you need to verify:

1. ✅ Native library compiled and included in APK
2. ✅ Native library loads at runtime
3. ✅ Security activation works
4. ✅ System properties are set correctly
5. ✅ Files are created on device
6. ✅ Button blocking works (if rooted)
7. ✅ Security persists after reboot
8. ✅ Violation monitoring works

---

## 📋 PRE-DEPLOYMENT CHECKLIST

### Before Testing on Device:

#### ✅ BUILD VERIFICATION (On Your Computer)

**Step 1: Check Build Success**
```powershell
cd DEVICEOWNER
.\gradlew clean build

# Expected output:
# BUILD SUCCESSFUL in 30s
# 45 actionable tasks: 45 executed
```

**Step 2: Verify Native Library Compiled**
```powershell
# Check if .so files were created
dir app\build\intermediates\cmake\debug\obj\arm64-v8a\libfirmware_security.so
dir app\build\intermediates\cmake\debug\obj\armeabi-v7a\libfirmware_security.so

# Both files should exist!
```

**Step 3: Verify Library in APK**
```powershell
# Extract APK and check contents
Expand-Archive app\build\outputs\apk\debug\app-debug.apk -DestinationPath temp -Force

# Check for native libraries
dir temp\lib\arm64-v8a\libfirmware_security.so
dir temp\lib\armeabi-v7a\libfirmware_security.so

# Both should exist!
# Clean up
Remove-Item temp -Recurse -Force
```

**Expected Output:**
```
✓ libfirmware_security.so (arm64-v8a) - ~45KB
✓ libfirmware_security.so (armeabi-v7a) - ~38KB
```

**Step 4: Check APK Size Increase**
```powershell
# Before integration: ~5-10MB
# After integration: ~5.1-10.1MB (should increase by ~100KB)
dir app\build\outputs\apk\debug\app-debug.apk
```

✅ **If all checks pass: Ready to test on device!**

---

## 📱 ON-DEVICE VERIFICATION

### PHASE 1: INSTALLATION & BASIC CHECKS

#### Test 1: APK Installation ✅

```powershell
# Install APK
adb install -r app\build\outputs\apk\debug\app-debug.apk

# Expected output:
# Performing Streamed Install
# Success
```

**What to Check:**
- ✅ No installation errors
- ✅ App appears in launcher
- ✅ App icon visible

---

#### Test 2: Device Owner Setup ✅

```powershell
# Set as Device Owner (if not already)
adb shell dpm set-device-owner com.example.deviceowner/.receivers.AdminReceiver

# Expected output:
# Success: Device owner set to package com.example.deviceowner
```

**What to Check:**
```powershell
# Verify Device Owner status
adb shell dumpsys device_policy | findstr "device owner"

# Expected output:
# Device owner app: com.example.deviceowner
```

---

#### Test 3: Native Library Loading ✅

```powershell
# Clear logs and monitor
adb logcat -c
adb logcat | findstr "FirmwareSecurity"
```

**Open your app on the device**

**Expected Log Output:**
```
I/FirmwareSecurityJNI: === Firmware Security JNI Library Loaded ===
I/FirmwareSecurityJNI: Version: 2.0 Production
I/FirmwareSecurityJNI: Package: com.example.deviceowner.security.firmware
I/FirmwareSecurityJNI: Target: Real Android Devices
I/FirmwareSecurity: ✓ Native library loaded successfully
```

✅ **CRITICAL: If you see these logs, native library is working!**

❌ **If you see errors:**
```
E/FirmwareSecurity: ✗ Failed to load native library
E/FirmwareSecurity: UnsatisfiedLinkError
```
→ Go back to BUILD VERIFICATION section

---

### PHASE 2: SECURITY ACTIVATION

#### Test 4: Register Device & Activate Security ✅

**On the device:**
1. Complete device registration in your app
2. Watch logs during registration

```powershell
# Monitor logs
adb logcat | findstr "FirmwareSecurity\|DeviceRegistration"
```

**Expected Log Output:**
```
I/DeviceRegistrationRepo: Starting device registration...
I/DeviceRegistrationRepo: ✓ Registered with loan ID: LOAN12345
I/DeviceRegistrationRepo: Activating firmware security...
I/FirmwareSecurityJNI: === Enabling Full Security Mode ===
I/FirmwareSecurityJNI: Checking bootloader lock status...
I/FirmwareSecurityJNI: Bootloader: LOCKED (via property)
I/FirmwareSecurityJNI: Security property set: SUCCESS
I/FirmwareSecurityJNI: Security state persisted
I/FirmwareSecurityJNI: === Security Mode: ENABLED ===
I/FirmwareSecurity: ✓ Security mode activated successfully
I/FirmwareSecurity: ✓ Security verification passed
I/FirmwareSecurity:   - Bootloader: LOCKED
I/FirmwareSecurity:   - Button blocking: ACTIVE
I/DeviceRegistrationRepo: ✓ Firmware security activated
I/DeviceRegistrationRepo: Security: true
```

✅ **CRITICAL: All these logs must appear!**

---

#### Test 5: System Property Verification ✅

```powershell
# Check main security property
adb shell getprop persist.security.mode.enabled

# Expected output: 1
```

```powershell
# Check all security-related properties
adb shell getprop | findstr security

# Expected output:
# [persist.security.mode.enabled]: [1]
# [persist.security.test]: [1]  (if test was run)
```

✅ **If property = 1: Security is activated!**

---

#### Test 6: File System Verification ✅

```powershell
# Check security state file
adb shell ls -l /data/local/tmp/security_state.dat

# Expected output:
# -rw-r--r-- 1 u0_a123 u0_a123 256 2026-01-30 12:00 security_state.dat
```

```powershell
# Check file contents
adb shell cat /data/local/tmp/security_state.dat

# Expected output:
# enabled=1
# timestamp=1706620800
```

```powershell
# Check security marker
adb shell ls /data/local/tmp/security_enabled

# Should exist
```

✅ **All files should exist and contain correct data**

---

### PHASE 3: FUNCTIONAL TESTING

#### Test 7: Security Status Check ✅

**In your app code (or test):**
```kotlin
val status = FirmwareSecurity.checkSecurityStatus()

Log.i("TEST", "Bootloader Locked: ${status?.bootloaderLocked}")
Log.i("TEST", "Security Enabled: ${status?.securityEnabled}")
Log.i("TEST", "Button Blocking: ${status?.buttonBlocking}")
Log.i("TEST", "Fully Secured: ${status?.isFullySecured}")
```

**Via ADB (check logs):**
```powershell
adb logcat | findstr "TEST"
```

**Expected Output:**
```
I/TEST: Bootloader Locked: true
I/TEST: Security Enabled: true
I/TEST: Button Blocking: true/false (depends on root)
I/TEST: Fully Secured: true
```

---

#### Test 8: Diagnostics Test ✅

**Run diagnostics:**
```kotlin
val results = FirmwareSecurity.runDiagnostics()
results.forEach { (test, status) ->
    Log.i("DIAGNOSTIC", "$test: $status")
}
```

**Expected Output:**
```
I/DIAGNOSTIC: Sysfs Access: PASS/FAIL (FAIL is OK if not rooted)
I/DIAGNOSTIC: Property System: PASS
I/DIAGNOSTIC: File System Write: PASS
```

**All critical tests should PASS** ✅

---

#### Test 9: Bootloader Lock Verification ✅

```powershell
# Check bootloader lock status via property
adb shell getprop ro.boot.flash.locked

# Expected on locked device: 1
```

```powershell
# Check verified boot state
adb shell getprop ro.boot.verifiedbootstate

# Expected: green or yellow (locked states)
# If "orange" or "red": bootloader unlocked
```

**On Pixel phones:**
```powershell
# Try to reboot to bootloader
adb reboot bootloader

# Expected with security:
# - Phone reboots normally (NOT to bootloader)
# - Or bootloader opens but shows "Device is LOCKED"
```

---

### PHASE 4: BUTTON BLOCKING (Rooted Devices Only)

#### Test 10: Kernel Module Check ✅

**Only if device is rooted:**

```powershell
# Check if kernel module is loaded
adb shell lsmod | findstr input_security

# Expected output (if rooted):
# input_security_filter 16384 0
```

```powershell
# Check sysfs interface
adb shell cat /sys/kernel/input_security/enabled

# Expected output: 1
```

```powershell
# Check statistics
adb shell cat /sys/kernel/input_security/stats

# Expected output:
# Total Blocked: 0
# Recovery Attempts: 0
# Fastboot Attempts: 0
```

---

#### Test 11: Button Combination Testing ✅

**Simulated Test (via ADB):**
```powershell
# Simulate Power + Volume Up
adb shell input keyevent KEYCODE_POWER &
adb shell input keyevent KEYCODE_VOLUME_UP

# Wait 2 seconds, then check stats
timeout /t 2
adb shell cat /sys/kernel/input_security/stats

# Expected output (if module loaded):
# Total Blocked: 1
# Recovery Attempts: 1
# Fastboot Attempts: 0
```

**Physical Test (on actual device):**
1. Reboot device: `adb reboot`
2. During boot, hold **Power + Volume Up**
3. Observe result:
   - ✅ With security: Phone boots normally
   - ❌ Without security: Phone enters recovery mode

**CRITICAL: If phone enters recovery, security is NOT active!**

---

### PHASE 5: PERSISTENCE & REBOOT TESTING

#### Test 12: Reboot Persistence ✅

```powershell
# 1. Verify security is active
adb shell getprop persist.security.mode.enabled
# Output: 1

# 2. Reboot device
adb reboot

# 3. Wait for device to come back online
adb wait-for-device

# 4. Check property again
adb shell getprop persist.security.mode.enabled
# Expected: Still 1 ✓
```

**Properties should persist across reboots!**

---

#### Test 13: Violation Logging ✅

```powershell
# Generate a violation (simulate button press)
adb shell input keyevent KEYCODE_POWER &
adb shell input keyevent KEYCODE_VOLUME_UP

# Check property
adb shell getprop persist.security.violation

# Expected: Some violation info
```

```powershell
# Check violation log file
adb shell cat /data/local/tmp/security_violations.log

# Should contain violation entries
```

---

### PHASE 6: MONITORING SERVICE

#### Test 14: Service Running ✅

```powershell
# Check if monitoring service is running
adb shell dumpsys activity services | findstr FirmwareSecurityMonitor

# Expected output:
# ServiceRecord{...FirmwareSecurityMonitorService}
```

```powershell
# Check service logs
adb logcat | findstr FirmwareSecurityMonitor

# Expected output:
# I/FirmwareSecurityMonitor: Firmware security monitoring started
# D/FirmwareSecurityMonitor: Security Status: Secured=true, Violations=0
```

---

#### Test 15: Violation Detection ✅

**Create a test violation:**
```powershell
# Trigger a simulated violation
adb shell input keyevent KEYCODE_POWER &
adb shell input keyevent KEYCODE_VOLUME_UP

# Monitor service response
adb logcat | findstr FirmwareSecurityMonitor
```

**Expected Output:**
```
W/FirmwareSecurityMonitor: Security violation detected: RECOVERY_ATTEMPT
W/FirmwareSecurityMonitor:   Severity: HIGH
W/FirmwareSecurityMonitor:   Details: Power+VolUp combination blocked
```

---

## 🎯 COMPLETE VERIFICATION CHECKLIST

### ✅ ESSENTIAL CHECKS (Must Pass)

- [ ] **Build**: Native library compiles successfully
- [ ] **APK**: libfirmware_security.so in APK
- [ ] **Install**: App installs without errors
- [ ] **Load**: Native library loads (see logs)
- [ ] **Activate**: Security mode activates
- [ ] **Property**: persist.security.mode.enabled = 1
- [ ] **Files**: security_state.dat created
- [ ] **Status**: checkSecurityStatus() returns valid data
- [ ] **Persist**: Property survives reboot
- [ ] **Service**: Monitoring service running

**If all 10 essential checks pass: BASIC SECURITY WORKING ✅**

---

### ⭐ ADVANCED CHECKS (For Rooted Devices)

- [ ] **Module**: Kernel module loads
- [ ] **Sysfs**: /sys/kernel/input_security/enabled = 1
- [ ] **Blocking**: Button combinations blocked
- [ ] **Stats**: Violations counted correctly
- [ ] **Physical**: Power+VolUp doesn't boot to recovery

**If all advanced checks pass: MAXIMUM SECURITY ACTIVE ✅**

---

## 🔍 DETAILED TEST SCRIPT

### Run This Complete Test:

```powershell
# COMPLETE VERIFICATION SCRIPT
# Save as: test-firmware-security.ps1

Write-Host "=== FIRMWARE SECURITY VERIFICATION ===" -ForegroundColor Cyan
Write-Host ""

# Test 1: Check native library in APK
Write-Host "Test 1: Checking native library in APK..." -ForegroundColor Yellow
$apk = "app\build\outputs\apk\debug\app-debug.apk"
Expand-Archive $apk -DestinationPath temp -Force
$arm64 = Test-Path "temp\lib\arm64-v8a\libfirmware_security.so"
$arm32 = Test-Path "temp\lib\armeabi-v7a\libfirmware_security.so"
Remove-Item temp -Recurse -Force

if ($arm64 -and $arm32) {
    Write-Host "✓ PASS: Native libraries found in APK" -ForegroundColor Green
} else {
    Write-Host "✗ FAIL: Native libraries missing!" -ForegroundColor Red
    exit 1
}

# Test 2: Check property
Write-Host "`nTest 2: Checking security property..." -ForegroundColor Yellow
$prop = adb shell getprop persist.security.mode.enabled
if ($prop -eq "1") {
    Write-Host "✓ PASS: Security property set (value=1)" -ForegroundColor Green
} else {
    Write-Host "✗ FAIL: Security property not set (value=$prop)" -ForegroundColor Red
}

# Test 3: Check files
Write-Host "`nTest 3: Checking security files..." -ForegroundColor Yellow
$file = adb shell ls /data/local/tmp/security_state.dat 2>&1
if ($file -notmatch "No such file") {
    Write-Host "✓ PASS: Security state file exists" -ForegroundColor Green
} else {
    Write-Host "✗ FAIL: Security state file missing!" -ForegroundColor Red
}

# Test 4: Check service
Write-Host "`nTest 4: Checking monitoring service..." -ForegroundColor Yellow
$service = adb shell dumpsys activity services | Select-String "FirmwareSecurityMonitor"
if ($service) {
    Write-Host "✓ PASS: Monitoring service running" -ForegroundColor Green
} else {
    Write-Host "⚠ WARNING: Monitoring service not found" -ForegroundColor Yellow
}

# Test 5: Check for violations (should be 0 initially)
Write-Host "`nTest 5: Checking violations..." -ForegroundColor Yellow
if (adb shell ls /sys/kernel/input_security/stats 2>&1 | Select-String "No such file") {
    Write-Host "⚠ INFO: Kernel module not loaded (OK if not rooted)" -ForegroundColor Yellow
} else {
    $stats = adb shell cat /sys/kernel/input_security/stats
    Write-Host "✓ PASS: Kernel module active" -ForegroundColor Green
    Write-Host "  Stats: $stats" -ForegroundColor Cyan
}

Write-Host "`n=== VERIFICATION COMPLETE ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "Summary:" -ForegroundColor White
Write-Host "If all essential tests passed, firmware security is working!" -ForegroundColor Green
```

**Run the script:**
```powershell
.\test-firmware-security.ps1
```

---

## 📊 WHAT EACH CHECK CONFIRMS

### Build Checks:
- **Native library compiles** → C++ code is valid
- **Library in APK** → Gradle configured correctly
- **APK size increase** → Files actually included

### Runtime Checks:
- **Library loads** → JNI bridge working
- **Activation succeeds** → Native functions work
- **Property set** → System access working
- **Files created** → File system permissions OK

### Functional Checks:
- **Status returns data** → Full integration working
- **Bootloader locked** → Device hardware cooperating
- **Buttons blocked** → Kernel module active (if rooted)
- **Service running** → Background monitoring active

### Persistence Checks:
- **Property survives reboot** → Security permanent
- **Files persist** → State maintained
- **Service restarts** → Monitoring continuous

---

## ❌ COMMON ISSUES & FIXES

### Issue: Native library not in APK

**Check:**
```powershell
# Verify CMake ran
.\gradlew clean build --info | findstr "CMake"
```

**Fix:**
- Ensure `externalNativeBuild` in build.gradle.kts
- Check CMakeLists.txt path is correct
- Sync project in Android Studio

---

### Issue: Library loads but functions fail

**Check logs for:**
```
E/FirmwareSecurityJNI: No implementation found
```

**Fix:**
- Package names must match EXACTLY
- In FirmwareSecurity.kt: `com.example.deviceowner.security.firmware`
- In firmware_security_jni.cpp: `Java_com_example_deviceowner_security_firmware_`

---

### Issue: Property doesn't set

**Check:**
```powershell
adb shell getprop persist.security.mode.enabled
```

**Fix:**
- Ensure app is Device Owner
- Check logs for permission errors
- Verify property name is correct

---

### Issue: Buttons not blocked

**Check if rooted:**
```powershell
adb shell su -c "id"
```

**If not rooted:** Button blocking won't work at hardware level (expected)

**If rooted:**
- Check if kernel module loaded: `adb shell lsmod`
- Load module manually if needed
- Verify sysfs accessible

---

## ✅ FINAL CONFIRMATION

**Your firmware security is working perfectly if:**

1. ✅ All logs show success messages
2. ✅ `persist.security.mode.enabled` = 1
3. ✅ Security state file exists
4. ✅ `checkSecurityStatus()` returns `isFullySecured = true`
5. ✅ Properties persist after reboot
6. ✅ Monitoring service is running
7. ✅ (If rooted) Button combinations are blocked

**Deployment readiness:**
- **70-85% of checks pass** → Ready for non-rooted deployment
- **90-100% of checks pass** → Ready for rooted deployment
- **All checks pass** → Perfect implementation! 🎉

---

## 📞 QUICK VERIFICATION COMMAND

**One-liner to check everything:**

```powershell
Write-Host "Native lib in APK:" (Test-Path (Expand-Archive app\build\outputs\apk\debug\app-debug.apk -DestinationPath temp -Force; Test-Path temp\lib\arm64-v8a\libfirmware_security.so; Remove-Item temp -Recurse -Force)); `
Write-Host "Security property:" (adb shell getprop persist.security.mode.enabled); `
Write-Host "State file:" (adb shell "test -f /data/local/tmp/security_state.dat && echo EXISTS || echo MISSING"); `
Write-Host "Service running:" (adb shell dumpsys activity services | Select-String "FirmwareSecurityMonitor" | Measure-Object).Count
```

---

## 🎓 SUMMARY

**What creates the firmware security:**
- ✅ C++ native code (firmware_security_jni.cpp)
- ✅ Compiled to .so library
- ✅ Loaded by Kotlin via JNI
- ✅ Sets system properties
- ✅ Creates security state files
- ✅ Monitors via background service

**How to confirm it works:**
- ✅ Check build artifacts
- ✅ Verify runtime logs
- ✅ Test system properties
- ✅ Validate file creation
- ✅ Test button blocking
- ✅ Verify persistence

**If all checks pass: You're ready to deploy!** 🚀
