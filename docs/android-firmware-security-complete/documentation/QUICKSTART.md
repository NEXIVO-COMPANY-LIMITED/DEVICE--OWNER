## For Testing on REAL Physical Android Phones (Not Emulators)

### Prerequisites
- Physical Android phone (Pixel, Samsung, OnePlus, etc.)
- USB cable
- Computer with ADB installed
- USB Debugging enabled on phone
- **NO AOSP BUILD REQUIRED** - Works on production ROMs!

### Step 1: Prepare Your Device (5 minutes)
1. Enable Developer Options on your Android phone:
   - Go to Settings → About Phone
   - Tap "Build Number" 7 times
   - Developer Options will appear

2. Enable USB Debugging:
   - Settings → Developer Options
   - Enable "USB Debugging"
   - Connect phone to computer
   - Authorize the computer when prompted

3. Verify connection:
   ```bash
   adb devices
   ```
   Should show your device

### Step 2: Install Components (10 minutes)

#### A. Install Kernel Module (Button Blocking)
```bash
# Push kernel module source to device
adb push kernel-driver/input_security_filter.c /data/local/tmp/

# Compile on device (requires kernel headers)
adb shell "su -c 'cd /data/local/tmp && compile_module.sh'"

# Load module
adb shell "su -c 'insmod /data/local/tmp/input_security_filter.ko'"

# Verify it's loaded
adb shell "lsmod | grep input_security"
```

#### B. Install Device Owner APK
```bash
# Build APK with security integration
cd device-owner-integration
./gradlew assembleDebug

# Install APK
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

#### C. Set Up Device Owner
```bash
# Remove all accounts from device first (required for Device Owner)
# Then set as Device Owner:
adb shell dpm set-device-owner com.yourcompany.deviceowner/.DeviceAdminReceiver
```

### Step 3: Activate Security (2 minutes)

1. Open your Device Owner app on the phone
2. Complete registration/setup
3. Security activates automatically via:
   ```kotlin
   FirmwareSecurity.activateSecurityMode()
   ```

4. Verify security is active:
   ```bash
   # Check button blocking
   adb shell cat /sys/kernel/input_security/enabled
   # Should output: 1
   
   # Check security property
   adb shell getprop persist.security.mode.enabled
   # Should output: 1
   ```

### Step 4: Test Security Features (5 minutes)

#### Test 1: Button Blocking
- Try pressing Power + Volume Up on your phone
- **Expected**: Nothing happens (recovery mode blocked)

#### Test 2: Check Security Status
```bash
adb shell "su -c 'cat /sys/kernel/input_security/stats'"
```

#### Test 3: View Violations
```bash
adb shell cat /data/local/tmp/security_violations.log
```

### Step 5: Integration with Your App

Add to your Device Registration Manager:

```kotlin
class DeviceRegistrationManager {
    suspend fun completeRegistration(loanId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                // Your existing registration
                val response = apiService.registerDevice(...)
                
                if (response.isSuccessful) {
                    // ACTIVATE SECURITY
                    val secured = FirmwareSecurity.activateSecurityMode()
                    
                    if (secured) {
                        Log.i(TAG, "✓ Device secured")
                        
                        // Report to server
                        val status = FirmwareSecurity.checkSecurityStatus()
                        apiService.reportSecurity(status?.toServerReport())
                    }
                    
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Failed"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
```

### Troubleshooting

**Issue**: Kernel module won't load
- **Solution**: Device kernel may not support loadable modules. Use alternative method (see advanced guide).

**Issue**: Button blocking not working
- **Solution**: Check if /sys/kernel/input_security/ exists. May need device-specific implementation.

**Issue**: Can't set Device Owner
- **Solution**: Remove all Google accounts and other accounts from device first.

**Issue**: ADB unauthorized
- **Solution**: Revoke USB debugging authorizations in Developer Options and reconnect.

### Next Steps

1. Review full documentation in `documentation/` folder
2. Customize security settings for your use case
3. Test on multiple device models
4. Deploy to production devices

### Important Notes

- **This works on REAL PHONES** - No emulator, no AOSP build needed
- Some features require root access (su command)
- Bootloader locking requires unlocked bootloader initially
- Always test on non-critical devices first
- Keep backup firmware for your test devices

### Support

For issues:
1. Check `documentation/TROUBLESHOOTING.md`
2. Review logs: `adb logcat | grep -i security`
3. Test individual components separately
