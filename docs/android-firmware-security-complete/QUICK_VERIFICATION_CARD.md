# ⚡ QUICK VERIFICATION CARD
## 5-Minute Security Check

---

## 🎯 ESSENTIAL CHECKS (Run These First)

### ✅ Check 1: Native Library in APK (30 seconds)
```powershell
# Extract and check
Expand-Archive app\build\outputs\apk\debug\app-debug.apk -DestinationPath temp -Force
dir temp\lib\arm64-v8a\libfirmware_security.so
dir temp\lib\armeabi-v7a\libfirmware_security.so
Remove-Item temp -Recurse -Force
```
**Expected:** Both files exist (~40-50KB each)
**Pass:** ✅ | **Fail:** ❌ Go rebuild project

---

### ✅ Check 2: Library Loads (1 minute)
```powershell
adb logcat -c
# Open your app
adb logcat | findstr "FirmwareSecurity"
```
**Expected Log:**
```
I/FirmwareSecurityJNI: === Firmware Security JNI Library Loaded ===
I/FirmwareSecurity: ✓ Native library loaded successfully
```
**Pass:** ✅ | **Fail:** ❌ Check package names match

---

### ✅ Check 3: Security Activated (1 minute)
```powershell
# After device registration in app
adb shell getprop persist.security.mode.enabled
```
**Expected:** `1`
**Pass:** ✅ | **Fail:** ❌ Check Device Owner status

---

### ✅ Check 4: Files Created (30 seconds)
```powershell
adb shell ls /data/local/tmp/security_state.dat
```
**Expected:** File exists
**Pass:** ✅ | **Fail:** ❌ Check activation logs

---

### ✅ Check 5: Status Check Works (1 minute)
```powershell
# In your app or test, call:
# FirmwareSecurity.checkSecurityStatus()

adb logcat | findstr "Security"
```
**Expected Log:**
```
I/FirmwareSecurity: Security: Bootloader=true, Blocking=true
```
**Pass:** ✅ | **Fail:** ❌ Check JNI functions

---

### ✅ Check 6: Survives Reboot (2 minutes)
```powershell
# Before reboot
adb shell getprop persist.security.mode.enabled
# Output: 1

adb reboot
adb wait-for-device

# After reboot
adb shell getprop persist.security.mode.enabled
# Output: Still 1
```
**Expected:** Property persists
**Pass:** ✅ | **Fail:** ❌ Check property name

---

## 🎯 QUICK STATUS SUMMARY

**Run all 6 checks. Count your passes:**

- **6/6 passes** → ✅ PERFECT! Ready to deploy
- **5/6 passes** → ✅ GOOD! Deploy with monitoring
- **4/6 passes** → ⚠️ ACCEPTABLE! Fix issues before scale
- **<4 passes** → ❌ ISSUES! Don't deploy yet

---

## 🚀 ADVANCED CHECKS (For Rooted Devices)

### ✅ Check 7: Kernel Module Loaded
```powershell
adb shell lsmod | findstr input_security
```
**Expected:** `input_security_filter 16384 0`
**Pass:** ✅ | **Fail:** ❌ Load module manually

---

### ✅ Check 8: Button Blocking Works
```powershell
# Simulate button press
adb shell input keyevent KEYCODE_POWER &
adb shell input keyevent KEYCODE_VOLUME_UP

# Check stats
adb shell cat /sys/kernel/input_security/stats
```
**Expected:**
```
Total Blocked: 1
Recovery Attempts: 1
```
**Pass:** ✅ | **Fail:** ❌ Check module loaded

---

## 📊 ONE-COMMAND CHECK

**Copy-paste this to check everything at once:**

```powershell
Write-Host "`n=== FIRMWARE SECURITY STATUS ===" -ForegroundColor Cyan;
Write-Host "1. Security Property: " -NoNewline; adb shell getprop persist.security.mode.enabled;
Write-Host "2. State File: " -NoNewline; if (adb shell "test -f /data/local/tmp/security_state.dat && echo OK || echo MISSING") { Write-Host "OK" -ForegroundColor Green } else { Write-Host "MISSING" -ForegroundColor Red };
Write-Host "3. Bootloader Lock: " -NoNewline; adb shell getprop ro.boot.flash.locked;
Write-Host "4. Service Running: " -NoNewline; if (adb shell dumpsys activity services | Select-String "FirmwareSecurityMonitor") { Write-Host "YES" -ForegroundColor Green } else { Write-Host "NO" -ForegroundColor Red };
Write-Host "================================`n" -ForegroundColor Cyan;
```

**Expected Output:**
```
=== FIRMWARE SECURITY STATUS ===
1. Security Property: 1
2. State File: OK
3. Bootloader Lock: 1
4. Service Running: YES
================================
```

**All OK?** → ✅ **WORKING PERFECTLY!**

---

## 🎯 WHAT EACH INDICATES

| Check | What It Confirms | Critical? |
|-------|------------------|-----------|
| Library in APK | Build configured correctly | ✅ YES |
| Library loads | JNI working | ✅ YES |
| Property = 1 | Security activated | ✅ YES |
| Files exist | Permissions OK | ✅ YES |
| Status works | Full integration | ✅ YES |
| Survives reboot | Persistence working | ✅ YES |
| Module loaded | Kernel access (root) | ⭐ Bonus |
| Buttons blocked | Hardware security | ⭐ Bonus |

---

## ❌ QUICK TROUBLESHOOTING

**If Check 1 fails:**
→ Add `externalNativeBuild` to build.gradle.kts
→ Run `.\gradlew clean build`

**If Check 2 fails:**
→ Package names don't match
→ Check `firmware_security_jni.cpp` function names

**If Check 3 fails:**
→ App not Device Owner
→ Run: `adb shell dpm set-device-owner com.example.deviceowner/.receivers.AdminReceiver`

**If Check 4 fails:**
→ Activation didn't run
→ Check `DeviceRegistrationRepository.kt` code

**If Check 5 fails:**
→ JNI functions not found
→ Rebuild and reinstall APK

**If Check 6 fails:**
→ Wrong property name
→ Use `persist.` prefix

---

## ✅ DEPLOYMENT DECISION

**Based on your results:**

**6/6 Essential + 2/2 Advanced** → 🎉 PERFECT! Deploy to all devices
**6/6 Essential + 0/2 Advanced** → ✅ GOOD! Deploy to non-rooted devices  
**5-6/6 Essential** → ⚠️ ACCEPTABLE! Fix minor issues
**<5/6 Essential** → ❌ NOT READY! Fix critical issues

---

## 🎓 REMEMBER

**What creates firmware security:**
1. C++ code compiles to `.so` library
2. Library loads via JNI when app starts
3. Sets system properties (`persist.security.mode.enabled`)
4. Creates state files (`/data/local/tmp/security_state.dat`)
5. Monitors via background service

**How to confirm it works:**
1. Check library in APK ✅
2. Check it loads ✅
3. Check property set ✅
4. Check files created ✅
5. Check status works ✅
6. Check persistence ✅

**All 6 checks pass = IT WORKS!** 🚀

---

## 📞 NEED HELP?

**Check these in order:**
1. Review logs: `adb logcat | findstr "Firmware\|Security\|Error"`
2. Check build output for errors
3. Verify package names match exactly
4. Ensure Device Owner is set
5. Confirm CMake configuration in build.gradle.kts

**Still stuck?** Review the complete VERIFICATION_TESTING_GUIDE.md

---

**Print this card and keep it handy!** 📋
