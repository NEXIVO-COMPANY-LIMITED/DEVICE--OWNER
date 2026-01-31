# Complete Integration Guide
## Adding Firmware Security to Your Existing Device Owner App

This guide shows you **exactly** how to add firmware security to your existing Kotlin Device Owner application for loan device management.

---

## Prerequisites

✅ You have an existing Device Owner app in Kotlin  
✅ Your app uses Gradle with Kotlin DSL  
✅ You have basic knowledge of Android development  
✅ You want to add firmware-level security  

---

## Step 1: Copy Native Code (5 minutes)

### 1.1 Create Directory Structure

In your Device Owner project root:

```bash
mkdir -p app/src/main/cpp
mkdir -p app/src/main/java/com/yourcompany/deviceowner/security
```

### 1.2 Copy Files

Copy these files from this package:

```bash
# Native C++ code
cp device-owner-integration/cpp/firmware_security_jni.cpp \
   YourApp/app/src/main/cpp/

# CMake configuration
cp device-owner-integration/config/CMakeLists.txt \
   YourApp/app/src/main/cpp/

# Kotlin interface
cp device-owner-integration/kotlin/FirmwareSecurity.kt \
   YourApp/app/src/main/java/com/yourcompany/deviceowner/security/
```

### 1.3 Update Package Name

Edit `FirmwareSecurity.kt` and replace package name:

```kotlin
// Change this:
package com.yourcompany.deviceowner.security

// To your actual package:
package com.yourcompany.loanapp.security
```

Also update in `firmware_security_jni.cpp`:

```cpp
// Change all instances of:
Java_com_yourcompany_deviceowner_security_FirmwareSecurity_

// To your package:
Java_com_yourcompany_loanapp_security_FirmwareSecurity_
```

---

## Step 2: Update Build Configuration (10 minutes)

### 2.1 Update app/build.gradle.kts

Add these sections to your existing `build.gradle.kts`:

```kotlin
android {
    // ... existing config ...
    
    defaultConfig {
        // ... existing config ...
        
        // ADD THIS: NDK configuration
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }
    
    // ADD THIS: Enable native builds
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    
    // ... rest of config ...
}
```

### 2.2 Sync and Build

```bash
# Sync Gradle
./gradlew --refresh-dependencies

# Build native library
./gradlew build

# Verify library was built
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep libfirmware_security.so
```

You should see:
```
lib/arm64-v8a/libfirmware_security.so
lib/armeabi-v7a/libfirmware_security.so
```

---

## Step 3: Integrate into Your Registration Flow (15 minutes)

### 3.1 Find Your Registration Code

Locate your device registration/setup code. It probably looks like:

```kotlin
class DeviceRegistrationManager {
    suspend fun registerDevice(deviceInfo: DeviceInfo): Result<String> {
        // Your existing registration logic
        val response = apiService.registerDevice(deviceInfo)
        // ...
    }
}
```

### 3.2 Add Security Activation

Modify it to activate security after successful registration:

```kotlin
import com.yourcompany.loanapp.security.FirmwareSecurity
import com.yourcompany.loanapp.security.activateSecurityModeAsync

class DeviceRegistrationManager {
    
    suspend fun registerDevice(deviceInfo: DeviceInfo): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "Starting device registration...")
                
                // EXISTING: Register with your server
                val response = apiService.registerDevice(deviceInfo)
                
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("Registration failed: ${response.code()}")
                    )
                }
                
                val loanId = response.body()?.loanId 
                    ?: return@withContext Result.failure(
                        Exception("No loan ID received")
                    )
                
                Log.i(TAG, "✓ Registered with server. Loan ID: $loanId")
                
                // NEW: Activate firmware security
                Log.i(TAG, "Activating firmware security...")
                val securityResult = FirmwareSecurity.activateSecurityModeAsync()
                
                securityResult.fold(
                    onSuccess = {
                        Log.i(TAG, "✓ Firmware security activated")
                        
                        // Verify security
                        val status = FirmwareSecurity.checkSecurityStatus()
                        
                        if (status?.isFullySecured == true) {
                            Log.i(TAG, "✓ Device fully secured")
                            
                            // Report to your server
                            reportSecurityStatus(loanId, status)
                            
                            Result.success(loanId)
                        } else {
                            Log.w(TAG, "⚠ Partial security")
                            // Decide if this is acceptable
                            Result.success(loanId) // or Result.failure()
                        }
                    },
                    onFailure = { error ->
                        Log.e(TAG, "✗ Security activation failed", error)
                        // Decide: fail registration or continue?
                        Result.success(loanId) // Allow but log
                    }
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Registration error", e)
                Result.failure(e)
            }
        }
    }
    
    private suspend fun reportSecurityStatus(
        loanId: String, 
        status: FirmwareSecurity.SecurityStatus
    ) {
        try {
            apiService.updateDeviceSecurity(
                loanId = loanId,
                bootloaderLocked = status.bootloaderLocked,
                buttonBlocking = status.buttonBlocking,
                violationCount = status.violations.total
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to report security status", e)
        }
    }
}
```

### 3.3 Update Your API Service

Add security status reporting to your API:

```kotlin
interface YourApiService {
    
    // Your existing endpoints...
    
    // NEW: Report security status
    @POST("devices/{loanId}/security")
    suspend fun updateDeviceSecurity(
        @Path("loanId") loanId: String,
        @Body security: SecurityStatusRequest
    ): Response<Unit>
}

data class SecurityStatusRequest(
    val bootloaderLocked: Boolean,
    val buttonBlocking: Boolean,
    val violationCount: Long,
    val timestamp: Long = System.currentTimeMillis()
)
```

---

## Step 4: Add Security Monitoring (10 minutes)

### 4.1 Create Monitoring Service

Add a background service to monitor violations:

```kotlin
import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.*

class SecurityMonitoringService : Service() {
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "Security monitoring started")
        
        scope.launch {
            FirmwareSecurity.monitorViolations(this@SecurityMonitoringService) { violation ->
                handleViolation(violation)
            }
        }
        
        return START_STICKY
    }
    
    private fun handleViolation(violation: FirmwareSecurity.Violation) {
        Log.w(TAG, "Security violation: ${violation.type}")
        
        // Report to server
        scope.launch {
            try {
                apiService.reportViolation(
                    type = violation.type,
                    details = violation.details,
                    timestamp = violation.timestamp,
                    severity = violation.severity
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to report violation", e)
            }
        }
        
        // Check if action needed
        val status = FirmwareSecurity.checkSecurityStatus()
        if (status?.violations?.total ?: 0 > 10) {
            // Too many violations - take action
            lockDevice()
        }
    }
    
    private fun lockDevice() {
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        dpm.lockNow()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
    
    companion object {
        private const val TAG = "SecurityMonitoring"
    }
}
```

### 4.2 Start Service After Registration

```kotlin
// In your MainActivity or after successful registration
startService(Intent(this, SecurityMonitoringService::class.java))
```

### 4.3 Add to AndroidManifest.xml

```xml
<service
    android:name=".SecurityMonitoringService"
    android:enabled="true"
    android:exported="false" />
```

---

## Step 5: Update Your Server (Backend)

### 5.1 Add Security Status Endpoint

Add to your backend API:

```python
# Python/Flask example
@app.route('/devices/<loan_id>/security', methods=['POST'])
def update_device_security(loan_id):
    data = request.json
    
    # Save to database
    device = Device.query.filter_by(loan_id=loan_id).first()
    device.bootloader_locked = data['bootloaderLocked']
    device.button_blocking = data['buttonBlocking']
    device.violation_count = data['violationCount']
    device.last_security_update = datetime.now()
    
    db.session.commit()
    
    return jsonify({'success': True})

@app.route('/devices/violations', methods=['POST'])
def report_violation():
    data = request.json
    
    # Log violation
    violation = SecurityViolation(
        device_id=data['deviceId'],
        type=data['type'],
        details=data['details'],
        severity=data['severity'],
        timestamp=datetime.fromisoformat(data['timestamp'])
    )
    
    db.session.add(violation)
    db.session.commit()
    
    # Alert if critical
    if data['severity'] == 'CRITICAL':
        send_alert_notification(violation)
    
    return jsonify({'success': True})
```

---

## Step 6: Test Integration (10 minutes)

### 6.1 Build and Install

```bash
# Build APK with native library
./gradlew assembleDebug

# Install on device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 6.2 Set as Device Owner

```bash
# Remove all accounts from device first
# Then set as Device Owner
adb shell dpm set-device-owner com.yourcompany.loanapp/.DeviceAdminReceiver
```

### 6.3 Test Security Activation

1. Open your app
2. Complete device registration
3. Check logs for security activation:

```bash
adb logcat | grep -i "firmware\|security"
```

You should see:
```
I/FirmwareSecurityJNI: === Firmware Security JNI Library Loaded ===
I/DeviceRegistration: Activating firmware security...
I/FirmwareSecurityJNI: Security mode ENABLED
I/DeviceRegistration: ✓ Firmware security activated
```

### 6.4 Test Button Blocking

Press Power + Volume Up on the device.

**Expected**: Nothing happens (recovery blocked!)

**Verify**:
```bash
adb shell cat /sys/kernel/input_security/stats
```

Should show:
```
Total Blocked: 1
Recovery Attempts: 1
```

---

## Step 7: Production Deployment

### 7.1 Release Build

Update `build.gradle.kts` for release:

```kotlin
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
        
        // Keep native symbols
        ndk {
            debugSymbolLevel = "FULL"
        }
    }
}
```

### 7.2 Add ProGuard Rules

In `proguard-rules.pro`:

```proguard
# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep FirmwareSecurity class
-keep class com.yourcompany.loanapp.security.FirmwareSecurity {
    *;
}

# Keep data classes
-keep class com.yourcompany.loanapp.security.FirmwareSecurity$** {
    *;
}
```

### 7.3 Build Release APK

```bash
./gradlew assembleRelease

# Sign APK
jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 \
    -keystore your-keystore.jks \
    app/build/outputs/apk/release/app-release-unsigned.apk \
    your-key-alias

# Zipalign
zipalign -v 4 app/build/outputs/apk/release/app-release-unsigned.apk \
    app-release.apk
```

---

## Troubleshooting

### Problem: Native library not found

**Error**: `UnsatisfiedLinkError: dlopen failed: library "libfirmware_security.so" not found`

**Solution**:
```bash
# Verify library is in APK
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep libfirmware_security

# If missing, check CMake output
./gradlew clean build --info | grep -i cmake

# Ensure NDK is configured
echo $ANDROID_NDK_HOME
```

### Problem: JNI function not found

**Error**: `UnsatisfiedLinkError: No implementation found for...`

**Solution**: Package name mismatch. Verify:

1. Kotlin package matches JNI signature
2. Function names are exact (case-sensitive)
3. JNI signature matches Kotlin declaration

### Problem: Security not activating

**Error**: Security activation fails silently

**Solution**:
```bash
# Check if sysfs exists
adb shell ls /sys/kernel/input_security/

# Check kernel module
adb shell lsmod | grep input_security

# Check property
adb shell getprop persist.security.mode.enabled

# Check logs
adb logcat | grep -i security
```

### Problem: Device Owner setup fails

**Error**: `Not allowed to set the device owner`

**Solution**:
```bash
# Remove all accounts
adb shell pm list users  # Should show only one user

# Factory reset if needed
adb shell am broadcast -a android.intent.action.MASTER_CLEAR

# Try again
adb shell dpm set-device-owner com.yourcompany.loanapp/.DeviceAdminReceiver
```

---

## Complete Integration Checklist

- [ ] Native code copied to `app/src/main/cpp/`
- [ ] Package names updated in Kotlin and C++
- [ ] CMakeLists.txt configured
- [ ] build.gradle.kts updated with NDK settings
- [ ] Project builds successfully
- [ ] Native library appears in APK
- [ ] Security activation added to registration flow
- [ ] Monitoring service implemented
- [ ] API endpoints updated on backend
- [ ] Tested on real device
- [ ] Button blocking verified
- [ ] Violations logging working
- [ ] ProGuard rules added for release
- [ ] Release APK built and tested

---

## Next Steps

1. **Deploy to Test Devices**: Test on multiple phone models
2. **Monitor Production**: Watch for issues in production
3. **Iterate**: Adjust based on real-world usage
4. **Documentation**: Document any device-specific issues

---

## Support

- **Documentation**: See `documentation/` folder
- **Examples**: Check `device-owner-integration/kotlin/IntegrationExample.kt`
- **Testing**: Use `testing-suite/test_security.sh`
- **Logs**: Always check `adb logcat` for issues

---

## Summary

You've now integrated firmware-level security into your Device Owner app!

Your app can now:
- ✅ Block recovery mode access
- ✅ Prevent fastboot mode
- ✅ Monitor security violations
- ✅ Report status to your server
- ✅ Take automated actions on violations

All with just a few lines of code:

```kotlin
FirmwareSecurity.activateSecurityMode()
val status = FirmwareSecurity.checkSecurityStatus()
```

**Congratulations!** 🎉
