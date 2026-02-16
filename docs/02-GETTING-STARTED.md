# 02 Getting Started

This guide walks you through installation, provisioning, and initial setup of the Device Owner application.

---

## 📋 Prerequisites

Before you begin, ensure you have:

- **Android Device**: Android 12 (API 31) or higher
- **Network Connection**: Continuous internet connectivity
- **Backend Server**: Running and accessible
- **Provisioning Method**: QR code or NFC capability
- **Admin Access**: Device provisioning permissions

---

## 🚀 Installation Steps

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

# Build release APK
./gradlew assembleRelease

# Build with specific configuration
./gradlew assembleRelease -Pbuild_type=production
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
adb install app/build/outputs/apk/release/app-release.apk

# Install and run
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am start -n com.example.deviceowner/.MainActivity
```

#### Via APK File

1. Transfer APK to device
2. Open file manager
3. Tap APK file
4. Follow installation prompts

---

## 🔧 Device Provisioning

### What is Provisioning?

Provisioning grants the app Device Owner privileges, enabling full device control and security enforcement.

### Provisioning Methods

#### Method 1: QR Code Provisioning (Recommended)

1. **Generate QR Code**
   ```
   Backend generates provisioning QR code with:
   - Device ID
   - Server URL
   - API Key
   - Loan Number
   ```

2. **Scan QR Code**
   - Open Device Owner app
   - Tap "Provision Device"
   - Scan QR code with device camera
   - Confirm provisioning

3. **Grant Permissions**
   - Accept Device Owner request
   - Accept Device Admin request
   - Allow location access
   - Allow notification permissions

4. **Verification**
   - App confirms provisioning success
   - Device registers with backend
   - Heartbeat begins

#### Method 2: NFC Provisioning

1. **Prepare NFC Tag**
   - Backend writes provisioning data to NFC tag
   - Includes device ID, server URL, API key

2. **Tap NFC Tag**
   - Open Device Owner app
   - Tap NFC tag with device
   - App reads provisioning data

3. **Complete Setup**
   - Follow same steps as QR code method
   - Grant required permissions
   - Verify registration

#### Method 3: Manual Provisioning

1. **Open App Settings**
   - Launch Device Owner app
   - Navigate to Settings
   - Select "Manual Provisioning"

2. **Enter Details**
   - Device ID (from backend)
   - Server URL
   - API Key
   - Loan Number

3. **Complete Setup**
   - Tap "Provision"
   - Grant permissions
   - Verify registration

---

## 📱 Initial Setup

### Step 1: Grant Permissions

The app requires these permissions:

| Permission | Purpose | Required |
|-----------|---------|----------|
| **Device Admin** | Device control and policies | Yes |
| **Device Owner** | Full device management | Yes |
| **Location** | Heartbeat reporting | Yes |
| **Notifications** | User alerts and reminders | Yes |
| **Camera** | QR code scanning | Optional |
| **Phone State** | SIM change detection | Yes |

### Step 2: Configure Backend Connection

1. **Enter Server URL**
   ```
   https://your-backend-server.com/api
   ```

2. **Verify SSL Certificate**
   - App validates certificate
   - Ensures secure connection
   - Blocks insecure connections

3. **Test Connection**
   - Tap "Test Connection"
   - Verify successful response
   - Check network logs

### Step 3: Register Device

1. **Enter Loan Number**
   - Unique identifier for device
   - Links device to loan account

2. **Collect Device Information**
   - IMEI
   - Serial Number
   - Model
   - OS Version
   - Storage capacity

3. **Submit Registration**
   - App sends data to backend
   - Backend assigns device_id
   - App stores device_id locally

4. **Verify Registration**
   - Check device status in backend dashboard
   - Confirm heartbeat is active
   - Verify device appears in device list

---

## ✅ Verification Checklist

After setup, verify everything is working:

- [ ] App installed successfully
- [ ] Device Owner privileges granted
- [ ] Device Admin privileges granted
- [ ] Location permissions enabled
- [ ] Notification permissions enabled
- [ ] Backend connection established
- [ ] Device registered with loan number
- [ ] Device ID assigned by backend
- [ ] Heartbeat active (check logs)
- [ ] Device appears in backend dashboard
- [ ] Can receive commands from backend
- [ ] Tamper detection active

---

## 🔍 Troubleshooting

### Installation Issues

**Problem**: APK installation fails
```
Solution:
1. Check device storage (need 100MB+ free)
2. Enable "Unknown Sources" in settings
3. Uninstall previous version
4. Try installing again
```

**Problem**: Build fails in Android Studio
```
Solution:
1. Clean project: Build → Clean Project
2. Rebuild: Build → Rebuild Project
3. Check Gradle sync
4. Verify SDK versions in build.gradle
```

### Provisioning Issues

**Problem**: QR code won't scan
```
Solution:
1. Ensure good lighting
2. Hold device steady
3. Check QR code is valid
4. Try manual provisioning instead
```

**Problem**: Device Owner permission denied
```
Solution:
1. Device may not support Device Owner
2. Check Android version (need 12+)
3. Try factory reset and re-provision
4. Contact device manufacturer
```

### Connection Issues

**Problem**: Cannot connect to backend
```
Solution:
1. Check internet connection
2. Verify server URL is correct
3. Check SSL certificate validity
4. Verify firewall rules
5. Check backend server status
```

**Problem**: Heartbeat not sending
```
Solution:
1. Check network connectivity
2. Verify backend URL in settings
3. Check app logs for errors
4. Restart app
5. Check backend server logs
```

### Permission Issues

**Problem**: Location permission denied
```
Solution:
1. Go to Settings → Apps → Device Owner
2. Tap Permissions
3. Enable Location
4. Restart app
```

**Problem**: Notification permission denied
```
Solution:
1. Go to Settings → Apps → Device Owner
2. Tap Permissions
3. Enable Notifications
4. Restart app
```

---

## 📊 Monitoring Setup

### Check Heartbeat Status

1. **In App**
   - Open Device Owner app
   - Go to Status screen
   - Check "Last Heartbeat" timestamp
   - Should update every 30 seconds

2. **In Backend Dashboard**
   - Navigate to Devices
   - Find your device by ID
   - Check "Last Seen" timestamp
   - Should be recent (within 1 minute)

### View Device Logs

1. **Local Logs**
   ```bash
   adb logcat | grep DeviceOwner
   ```

2. **Backend Logs**
   - Login to backend dashboard
   - Navigate to Device Logs
   - Filter by device ID
   - Review recent entries

### Test Commands

1. **Lock Device**
   - Backend: Send lock command
   - Device: Should lock immediately
   - Check logs for confirmation

2. **Unlock Device**
   - Backend: Send unlock command
   - Device: Should unlock
   - Check logs for confirmation

---

## 🔐 Security Configuration

### SSL/TLS Setup

1. **Certificate Validation**
   - App validates server certificate
   - Prevents man-in-the-middle attacks
   - Blocks self-signed certificates in production

2. **Certificate Pinning** (Optional)
   ```
   Configure in app settings:
   - Server certificate hash
   - Public key hash
   - Backup certificate hash
   ```

### API Key Management

1. **Generate API Key**
   - Backend generates unique key
   - Store securely in encrypted storage
   - Never share or expose

2. **Rotate API Key**
   - Backend: Generate new key
   - Device: Update in settings
   - Old key: Revoke after verification

---

## 📚 Next Steps

After successful setup:

1. **Read Features Guide** → [03-FEATURES-GUIDE.md](./03-FEATURES-GUIDE.md)
2. **Understand Heartbeat** → [06-HEARTBEAT-SYSTEM.md](./06-HEARTBEAT-SYSTEM.md)
3. **Learn Device Locking** → [07-DEVICE-LOCKING.md](./07-DEVICE-LOCKING.md)
4. **Review API Reference** → [04-API-REFERENCE.md](./04-API-REFERENCE.md)

---

## 🆘 Getting Help

- **Setup Issues**: Check [Troubleshooting](#troubleshooting) section
- **API Questions**: See [API Reference](./04-API-REFERENCE.md)
- **Logging Help**: Review [Logging and Debugging](./12-LOGGING-AND-DEBUGGING.md)
- **Architecture Questions**: Read [Architecture](./05-ARCHITECTURE.md)

---

**Last Updated:** February 2026  
**Version:** 2.0
