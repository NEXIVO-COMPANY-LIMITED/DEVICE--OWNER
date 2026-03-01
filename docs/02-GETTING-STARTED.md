# 02 - Getting Started

This guide walks you through installation, provisioning, and initial setup of the PAYO Device Owner application.

---

## Prerequisites

Before you begin, ensure you have:

- **Android Device**: Android 5.0 (API 21) or higher (Android 8.0+ recommended)
- **Network Connection**: Internet connectivity for initial setup
- **Backend Server**: Running and accessible
- **Provisioning Method**: QR code, NFC, or ADB capability
- **Admin Access**: Device provisioning permissions

---

## Installation Steps

### Step 1: Build the Application

#### Using Android Studio

1. Open the project in Android Studio
2. Select **Build** → **Build Bundle(s) / APK(s)** → **Build APK(s)**
3. Wait for the build to complete
4. APK will be available in `app/build/outputs/apk/`

#### Using Command Line

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK (Windows)
./gradlew.bat assembleRelease

# Build release APK (Linux/Mac)
./gradlew assembleRelease
```

#### Using Build Scripts

```powershell
# Windows - Complete build and sign
./build_release.ps1

# Calculate APK checksum for provisioning
./calculate_checksum.ps1 -ApkPath "app-release.apk"
```

### Step 2: Install on Device

#### Via Android Studio

1. Connect device via USB
2. Enable USB Debugging on device
3. Click **Run** → **Run 'app'**
4. Select your device
5. Wait for installation to complete

#### Via ADB Command Line

```bash
# Install APK
adb install app-release.apk

# Install and replace existing
adb install -r app-release.apk

# Verify installation
adb shell pm list packages | grep payo
```

#### Via APK File Transfer

1. Transfer APK to device storage
2. Open file manager on device
3. Tap APK file
4. Follow installation prompts
5. Enable "Install from Unknown Sources" if prompted

---

## Device Provisioning

### What is Provisioning?

Provisioning grants the app **Device Owner** privileges, enabling:
- Full device control
- Policy enforcement
- Factory reset protection
- Remote management capabilities

**Important**: Device must be factory reset or never set up before provisioning.

### Provisioning Methods

#### Method 1: QR Code Provisioning (Recommended)

**Best for**: Bulk device deployment

1. **Prepare Provisioning Config**
   - Edit `provisioning-config.json`
   - Set correct APK URL
   - Calculate and set APK checksum
   - Configure WiFi credentials

2. **Generate QR Code**
   - Use online QR generator
   - Input: Contents of `provisioning-config.json`
   - Format: JSON string

3. **Factory Reset Device**
   - Settings → System → Reset → Factory Reset
   - Device will restart to setup wizard

4. **Scan QR Code**
   - During setup wizard, tap screen 6 times
   - Camera opens for QR scanning
   - Scan the provisioning QR code
   - Device downloads and installs APK
   - Device Owner is automatically set

5. **Complete Setup**
   - Device finishes setup automatically
   - App launches and registers with backend
   - Heartbeat begins

#### Method 2: NFC Provisioning

**Best for**: Field deployment

1. **Prepare NFC Tag**
   - Write provisioning JSON to NFC tag
   - Use NFC Tools app or similar

2. **Factory Reset Device**
   - Reset device to setup wizard

3. **Tap NFC Tag**
   - During setup wizard, tap NFC tag to device
   - Device reads provisioning data
   - Follows same flow as QR code

#### Method 3: ADB Provisioning

**Best for**: Development and testing

1. **Factory Reset Device**
   - Reset to clean state

2. **Install APK**
   ```bash
   adb install app-release.apk
   ```

3. **Set Device Owner**
   ```bash
   adb shell dpm set-device-owner com.microspace.payo/.receivers.admin.AdminReceiver
   ```

4. **Verify**
   ```bash
   adb shell dpm get-device-owner
   # Should output: com.microspace.payo/.receivers.admin.AdminReceiver
   ```

5. **Launch App**
   ```bash
   adb shell am start -n com.microspace.payo/.ui.activities.registration.RegistrationStatusActivity
   ```

---

## Initial Setup

### Step 1: Grant Permissions

The app requires these permissions:

| Permission | Purpose | Required | When |
|-----------|---------|----------|------|
| **Device Admin** | Device control and policies | Yes | Provisioning |
| **Device Owner** | Full device management | Yes | Provisioning |
| **Location** | Device tracking | Yes | First launch |
| **Notifications** | User alerts | Yes | First launch |
| **Camera** | QR code scanning | No | When needed |
| **Phone State** | SIM detection | Yes | First launch |
| **Storage** | Local data | Yes | First launch |

### Step 2: Device Registration

After provisioning, the app automatically:

1. **Collects Device Information**
   - IMEI / Serial Number
   - Model and Manufacturer
   - Android Version
   - Storage Capacity
   - Network Information

2. **Registers with Backend**
   - Sends device data to API
   - Receives device_id
   - Stores credentials locally

3. **Starts Heartbeat**
   - Begins 30-second heartbeat cycle
   - Reports device status
   - Receives commands from backend

### Step 3: Verify Setup

Check that everything is working:

1. **Device Owner Status**
   ```bash
   adb shell dpm get-device-owner
   # Should show: com.microspace.payo/.receivers.admin.AdminReceiver
   ```

2. **App Status**
   - Open app
   - Check "Device Status" screen
   - Should show "Registered" and "Active"

3. **Heartbeat Status**
   - Check "Last Heartbeat" timestamp
   - Should update every 30 seconds

4. **Backend Dashboard**
   - Login to backend
   - Find device in device list
   - Check "Last Seen" is recent

---

## Verification Checklist

After setup, verify:

- [ ] App installed successfully
- [ ] Device Owner privileges granted
- [ ] Device Admin privileges granted
- [ ] All permissions granted
- [ ] Device registered with backend
- [ ] Device ID assigned
- [ ] Heartbeat active (updates every 30s)
- [ ] Device appears in backend dashboard
- [ ] Can receive commands from backend
- [ ] Tamper detection active
- [ ] Location reporting works
- [ ] Lock/unlock commands work

---

## Troubleshooting

### Installation Issues

**Problem**: APK installation fails

**Solutions**:
1. Check device storage (need 100MB+ free)
2. Enable "Install from Unknown Sources"
3. Uninstall previous version first
4. Check APK is not corrupted
5. Try different installation method

**Problem**: Build fails

**Solutions**:
1. Clean project: `./gradlew.bat clean`
2. Rebuild: `./gradlew.bat assembleRelease`
3. Check Gradle sync
4. Verify JDK version (need JDK 11+)
5. Check `local.properties` has correct SDK path

### Provisioning Issues

**Problem**: "Contact your IT admin for help" error

**Solutions**:
1. **Checksum mismatch** (most common):
   ```powershell
   # Recalculate checksum
   ./calculate_checksum.ps1 -ApkPath "app-release.apk"
   # Update provisioning-config.json
   ```
2. Verify APK URL is accessible
3. Check WiFi credentials are correct
4. Ensure device is factory reset
5. Try ADB provisioning method instead

**Problem**: Device Owner permission denied

**Solutions**:
1. Device must be factory reset first
2. No Google accounts should be added yet
3. Check Android version (need 5.0+)
4. Some devices don't support Device Owner
5. Try manufacturer-specific provisioning

**Problem**: QR code won't scan

**Solutions**:
1. Ensure good lighting
2. Hold device steady
3. Verify QR code is valid JSON
4. Try regenerating QR code
5. Use NFC or ADB method instead

### Connection Issues

**Problem**: Cannot connect to backend

**Solutions**:
1. Check internet connection
2. Verify server URL in `ApiConstants.kt`
3. Check SSL certificate validity
4. Verify firewall allows connection
5. Check backend server is running
6. Test URL in browser first

**Problem**: Heartbeat not sending

**Solutions**:
1. Check network connectivity
2. Verify backend URL is correct
3. Check app logs:
   ```bash
   adb logcat | grep "HeartbeatService"
   ```
4. Restart app
5. Check backend server logs
6. Verify device is registered

### Permission Issues

**Problem**: Location permission denied

**Solutions**:
1. Settings → Apps → PAYO
2. Tap Permissions
3. Enable Location → "Allow all the time"
4. Restart app

**Problem**: Notification permission denied

**Solutions**:
1. Settings → Apps → PAYO
2. Tap Permissions
3. Enable Notifications
4. Restart app

---

## Monitoring Setup

### Check Heartbeat Status

**In App**:
1. Open PAYO app
2. Go to "Device Status" screen
3. Check "Last Heartbeat" timestamp
4. Should update every 30 seconds

**Via ADB**:
```bash
adb logcat | grep "HeartbeatService"
# Should see heartbeat logs every 30 seconds
```

**In Backend Dashboard**:
1. Navigate to Devices
2. Find device by ID or IMEI
3. Check "Last Seen" timestamp
4. Should be recent (within 1 minute)

### View Device Logs

**Local Logs**:
```bash
# All app logs
adb logcat | grep "PAYO"

# Heartbeat logs
adb logcat | grep "HeartbeatService"

# Registration logs
adb logcat | grep "DeviceRegistration"

# Lock logs
adb logcat | grep "LockManager"
```

**Backend Logs**:
1. Login to backend dashboard
2. Navigate to Device Logs
3. Filter by device ID
4. Review recent entries

### Test Commands

**Lock Device**:
1. Backend: Send lock command via API
2. Device: Should lock immediately
3. Check logs for confirmation

**Unlock Device**:
1. Backend: Send unlock command
2. Device: Should unlock
3. Verify in logs

---

## Security Configuration

### SSL/TLS Setup

The app uses HTTPS with certificate validation:

1. **Certificate Validation**
   - Validates server certificate
   - Prevents man-in-the-middle attacks
   - Configured in `network_security_config.xml`

2. **Certificate Pinning** (Optional)
   - Add certificate hash to config
   - Prevents certificate substitution
   - See `DeviceOwnerSSLManager.kt`

### API Key Management

1. **Configuration**
   - Set in `ApiConstants.kt`
   - Stored in encrypted preferences
   - Never logged or exposed

2. **Rotation**
   - Backend generates new key
   - Update `ApiConstants.kt`
   - Rebuild and redeploy

---

## Next Steps

After successful setup:

1. **Understand Features** → [03-FEATURES-GUIDE.md](03-FEATURES-GUIDE.md)
2. **Learn About APIs** → [6.0-APIS.md](6.0-APIS.md)
3. **Understand Heartbeat** → [7.0-DEVICE-HEARTBEAT.md](7.0-DEVICE-HEARTBEAT.md)
4. **Learn Lock Mechanisms** → [8.0-HARD-LOCK-AND-SOFT-LOCK.md](8.0-HARD-LOCK-AND-SOFT-LOCK.md)
5. **Review Security** → [9.0-DEVICE-TAMPER.md](9.0-DEVICE-TAMPER.md)

---

## Getting Help

- **Setup Issues**: Check [Troubleshooting](#troubleshooting) section above
- **API Questions**: See [6.0-APIS.md](6.0-APIS.md)
- **Logging Help**: Review [11.0-DEVICE-LOGS-AND-BUGS.md](11.0-DEVICE-LOGS-AND-BUGS.md)
- **Architecture**: Read [14.0-FOLDER-STRUCTURE.md](14.0-FOLDER-STRUCTURE.md)
- **Support**: support@nexivo.io

---

**Last Updated:** March 2026  
**Version:** 1.1
