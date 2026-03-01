# PAYO - Device Owner Management System

Android Device Owner application for managing device security, payments, and remote control.

## Overview

PAYO is a comprehensive Device Owner application that provides:
- Device Owner provisioning and management
- Payment tracking and enforcement
- Remote device control
- Security monitoring and tamper detection
- Factory Reset Protection (FRP)
- Offline sync capabilities

## Quick Start

### Prerequisites

- Android Studio Arctic Fox or later
- Android SDK 21+ (Android 5.0+)
- JDK 11 or later
- Gradle 8.0+

### Building the APK

```powershell
# Build release APK
./gradlew.bat assembleRelease

# Or use the build script
./build_release.ps1
```

### Device Provisioning

1. **Calculate APK Checksum**
   ```powershell
   .\calculate_checksum.ps1 -ApkPath "app-release.apk"
   ```

2. **Update Provisioning Config**
   Edit `provisioning-config.json` with the correct checksum

3. **Provision Device**
   - Factory reset device
   - During setup wizard, scan QR code or use NFC
   - Device will download and install the APK
   - Device Owner will be automatically set

## Project Structure

```
DEVICEOWNER/
├── app/
│   ├── src/main/java/com/microspace/payo/
│   │   ├── config/           # Configuration files
│   │   ├── control/          # Device control managers
│   │   ├── core/             # Core functionality
│   │   ├── data/             # Data layer (models, repositories, database)
│   │   ├── device/           # Device Owner management
│   │   ├── receivers/        # Broadcast receivers
│   │   ├── security/         # Security features
│   │   ├── services/         # Background services
│   │   ├── ui/               # UI components
│   │   └── utils/            # Utility classes
│   └── src/main/res/         # Resources
├── docs/                     # Documentation
├── keystore/                 # Release keystore
├── provisioning-config.json  # Provisioning configuration
└── README.md                 # This file
```

## Key Features

### Device Owner Management
- Silent device owner provisioning
- Device policy enforcement
- Factory reset protection
- Developer options blocking

### Payment Management
- Payment tracking and reminders
- Soft lock for payment reminders
- Hard lock for overdue payments
- Offline payment verification

### Security Features
- Tamper detection
- SIM change monitoring
- Boot mode detection
- ADB blocking
- Screen capture prevention
- USB debugging control

### Remote Management
- Remote lock/unlock
- Remote wipe
- Policy updates
- Heartbeat monitoring

## Configuration

### API Configuration

Edit `app/src/main/java/com/microspace/payo/config/ApiConstants.kt`:

```kotlin
object ApiConstants {
    const val BASE_URL = "https://your-api-server.com/"
    const val API_KEY = "your-api-key"
}
```

### Provisioning Configuration

Edit `provisioning-config.json`:

```json
{
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME": "com.microspace.payo/com.microspace.payo.receivers.admin.AdminReceiver",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": "https://github.com/YOUR-ORG/DEVICE--OWNER/releases/download/VERSION/app-release.apk",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM": "YOUR_BASE64URL_CHECKSUM",
  "android.app.extra.PROVISIONING_WIFI_SSID": "Your-WiFi-SSID",
  "android.app.extra.PROVISIONING_WIFI_PASSWORD": "Your-WiFi-Password",
  "android.app.extra.PROVISIONING_WIFI_SECURITY_TYPE": "WPA"
}
```

## Provisioning Setup

### Step 1: Build and Sign APK

```powershell
# Build
./gradlew.bat assembleRelease

# Sign
jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 `
  -keystore keystore/payo-release.keystore `
  app/build/outputs/apk/release/app-release-unsigned.apk `
  payo-release

# Align
zipalign -v 4 app/build/outputs/apk/release/app-release-unsigned.apk app-release.apk
```

### Step 2: Calculate Checksum

```powershell
$bytes = [System.IO.File]::ReadAllBytes("app-release.apk")
$sha256 = [System.Security.Cryptography.SHA256]::Create()
$hash = $sha256.ComputeHash($bytes)
[Convert]::ToBase64String($hash).Replace('+', '-').Replace('/', '_').TrimEnd('=')
```

### Step 3: Update Config and Test

1. Update `provisioning-config.json` with the checksum
2. Upload APK to GitHub releases
3. Factory reset test device
4. Scan QR code during setup wizard
5. Verify device owner: `adb shell dpm get-device-owner`

## Troubleshooting

### "Contact your IT admin for help" Error

This usually means checksum mismatch. Fix:

1. Download the APK from the URL in your config
2. Calculate its checksum using the script above
3. Update `provisioning-config.json` with the correct checksum
4. Try provisioning again

### Device Owner Not Set

```bash
# Check if device owner is set
adb shell dpm get-device-owner

# Check logs
adb logcat | grep -i "AdminReceiver\|provisioning"

# Manually set device owner (for testing)
adb shell dpm set-device-owner com.microspace.payo/.receivers.admin.AdminReceiver
```

### Build Errors

```powershell
# Clean build
./gradlew.bat clean

# Rebuild
./gradlew.bat assembleRelease
```

## Scripts

- `build_release.ps1` - Build and sign release APK
- `calculate_checksum.ps1` - Calculate APK checksum for provisioning
- `validate_provisioning.ps1` - Validate provisioning configuration
- `build_and_prepare_provisioning.ps1` - Complete build and provisioning workflow

## Documentation

Detailed documentation is available in the `docs/` folder:

- [Getting Started](docs/02-GETTING-STARTED.md)
- [Device Installation](docs/4.0-DEVICE-INSTALLATION.md)
- [Device Registration](docs/5.0-DEVICE-REGISTRATION.md)
- [APIs](docs/6.0-APIS.md)
- [Device Heartbeat](docs/7.0-DEVICE-HEARTBEAT.md)
- [Hard Lock and Soft Lock](docs/8.0-HARD-LOCK-AND-SOFT-LOCK.md)
- [FRP](docs/15.0-FRP.md)
- [Deactivation](docs/16.0-DEACTIVATION.md)

## API Endpoints

### Device Registration
```
POST /api/device/register
```

### Heartbeat
```
POST /api/device/heartbeat
```

### Payment Status
```
GET /api/payment/status/{deviceId}
```

### Remote Commands
```
POST /api/device/command
```

## Security

- All API communications use HTTPS with certificate pinning
- Sensitive data is encrypted using Android Keystore
- Database is encrypted using SQLCipher
- Device identifiers are securely stored

## Requirements

### Minimum Requirements
- Android 5.0 (API 21) or higher
- 2GB RAM
- 100MB storage space

### Recommended
- Android 8.0 (API 26) or higher
- 4GB RAM
- 500MB storage space

## Permissions

The app requires the following permissions:

- `INTERNET` - API communication
- `READ_PHONE_STATE` - Device identification
- `ACCESS_FINE_LOCATION` - Location tracking
- `RECEIVE_BOOT_COMPLETED` - Auto-start on boot
- `SYSTEM_ALERT_WINDOW` - Lock screen overlay
- Device Admin permissions - Device Owner capabilities

## License

Proprietary - NEXIVO COMPANY LIMITED

## Support

For support, contact:
- Email: support@nexivo.io
- Website: https://nexivo.io

## Version History

### v1.1 (Current)
- Fixed provisioning setup
- Enhanced AdminReceiver with advanced policies
- Added checksum calculation scripts
- Improved documentation

### v1.0
- Initial release
- Device Owner provisioning
- Payment management
- Security features
- Remote management

## Contributing

This is a proprietary project. For contribution guidelines, contact the development team.

## Authors

NEXIVO COMPANY LIMITED Development Team

---

**Note:** This application requires Device Owner privileges and must be provisioned during device setup or via ADB commands on a factory-reset device.
