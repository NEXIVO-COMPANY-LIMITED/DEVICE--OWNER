# 03 - Features Guide

Complete documentation of all PAYO Device Owner features and capabilities.

---

## Feature Overview

PAYO includes the following major features:

1. **Device Management & Hardening**
2. **Device Registration & Tracking**
3. **Continuous Monitoring (Heartbeat)**
4. **Device Locking & Kiosk Mode**
5. **Tamper Detection & Response**
6. **Auto-Update System**
7. **Logging & Diagnostics**
8. **Deactivation & Cleanup**

---

## 1. Device Management & Hardening

### Overview

PAYO enforces enterprise-level security policies to prevent unauthorized modifications and ensure compliance.

### Security Modes

#### STANDARD Mode
- **Factory Reset**: Blocked
- **Safe Boot**: Blocked
- **Developer Options**: Visible but limited
- **USB Debugging**: Disabled
- **User Management**: Restricted

**Use Case**: General loan devices with standard security requirements

#### STRICT Mode
- **Factory Reset**: Blocked
- **Safe Boot**: Blocked
- **Developer Options**: Hidden
- **USB Debugging**: Disabled
- **ADB**: Completely disabled
- **User Management**: Fully restricted

**Use Case**: High-value devices or sensitive deployments

### Enforced Restrictions

| Restriction | Purpose | Impact |
|------------|---------|--------|
| **Factory Reset Block** | Prevent data wipe | Device cannot be reset by user |
| **Safe Boot Block** | Prevent bypass mode | Device boots normally only |
| **Developer Options** | Prevent debugging | No ADB or debugging access |
| **USB Debugging** | Prevent data access | USB restricted to charging |
| **User Management** | Prevent account changes | No new user accounts |
| **App Installation** | Control software | Only approved apps installable |

### Silent Operation

- Management messages suppressed
- Policy dialogs hidden
- No visible security prompts
- Seamless user experience

### Implementation

```kotlin
// Set security mode
val deviceOwnerManager = DeviceOwnerManager(context)
deviceOwnerManager.applyAllCriticalRestrictions()

// Block factory reset
deviceOwnerManager.blockFactoryReset()

// Disable developer options
deviceOwnerManager.disableDeveloperOptions(true)

// Enable silent mode
CompleteSilentMode(context).enableCompleteSilentMode()
```

**Location**: `app/src/main/java/com/microspace/payo/device/DeviceOwnerManager.kt`

---

## 2. Device Registration & Tracking

### Overview

Devices are registered with the backend using a loan number, creating a persistent link between physical device and loan account.

### Registration Process

1. **Loan Number Entry**
   - User enters loan number
   - App validates format
   - Sends to backend

2. **Device Information Collection**
   - IMEI (International Mobile Equipment Identity)
   - Serial Number
   - Model Name
   - Android Version
   - Storage Capacity
   - Battery Status
   - Location (if permitted)

3. **Backend Assignment**
   - Backend validates loan number
   - Assigns unique `device_id`
   - Stores device metadata
   - Initializes heartbeat tracking

4. **Local Storage**
   - Stores `device_id` in encrypted preferences
   - Saves registration data to local database
   - Creates backup for offline access

### Registration Flow

```
User Opens App
    ↓
Enter Loan Number
    ↓
Validate Format
    ↓
Collect Device Data
    ↓
Send to Backend (POST /api/device/register)
    ↓
Receive device_id
    ↓
Store Locally
    ↓
Start Heartbeat
```

### Implementation

**Activity**: `RegistrationStatusActivity.kt`
**Repository**: `DeviceRegistrationRepository.kt`
**API**: `POST /api/devices/mobile/register/`

**Location**: `app/src/main/java/com/microspace/payo/presentation/activities/`

---

## 3. Continuous Monitoring (Heartbeat)

### Overview

30-second heartbeat system that continuously monitors device status and communicates with backend.

### Heartbeat Payload

```json
{
  "device_id": "[DEVICE_ID]",
  "timestamp": "[TIMESTAMP]",
  "device_info": {
    "imei": "[DEVICE_IMEI]",
    "model": "[DEVICE_MODEL]",
    "android_version": "[ANDROID_VERSION]",
    "battery_level": [BATTERY_LEVEL],
    "storage_free": [STORAGE_FREE]
  },
  "security_status": {
    "developer_options": false,
    "usb_debugging": false,
    "device_owner_active": true,
    "tamper_detected": false
  },
  "location": {
    "latitude": [LATITUDE],
    "longitude": [LONGITUDE],
    "accuracy": [ACCURACY]
  }
}
```

### Server Response Handling

The backend can send commands in heartbeat response:

| Command | Action | Implementation |
|---------|--------|----------------|
| `lock` | Hard lock device | `RemoteDeviceControlManager.applyHardLock()` |
| `unlock` | Unlock device | `RemoteDeviceControlManager.unlockDevice()` |
| `deactivate` | Remove Device Owner | `DeactivationHandler.handleDeactivation()` |
| `update_config` | Update settings | Apply new configuration |

### Implementation

**Service**: `HeartbeatService.kt`
**Manager**: `HeartbeatManager.kt`
**Worker**: `HeartbeatWorker.kt`
**API**: `POST /api/device/heartbeat`

**Location**: `app/src/main/java/com/microspace/payo/services/heartbeat/`

---

## 4. Device Locking & Kiosk Mode

### Lock Types

#### Soft Lock (Payment Reminder)
- Dismissible overlay
- Shows payment information
- User can still use device
- Used for payment reminders

**Implementation**: `SoftLockOverlayService.kt`

#### Hard Lock (Complete Lockdown)
- Full-screen kiosk mode
- Cannot be dismissed
- Blocks all device functions
- Used for overdue payments or tamper detection

**Implementation**: `HardLockGenericActivity.kt`

### Lock Triggers

| Trigger | Lock Type | Reason |
|---------|-----------|--------|
| Payment overdue | Hard Lock | Payment not received |
| Payment reminder | Soft Lock | Payment due soon |
| Tamper detected | Hard Lock | Security violation |
| SIM change | Soft Lock | SIM card changed |
| Server command | Hard/Soft | Remote management |

### Lock Persistence

Locks persist across:
- App restarts
- Device reboots
- Network disconnections

**Storage**: Device-protected storage + SharedPreferences

### Implementation

**Manager**: `RemoteDeviceControlManager.kt`
**Hard Lock**: `HardLockGenericActivity.kt`
**Soft Lock**: `SoftLockOverlayService.kt`

**Location**: `app/src/main/java/com/microspace/payo/control/`

---

## 5. Tamper Detection & Response

### Detected Tampering

| Tamper Type | Detection Method | Response |
|-------------|------------------|----------|
| Developer Options | Runtime check | Hard lock + report |
| USB Debugging | Runtime check | Hard lock + report |
| Bootloader Unlock | Boot check | Hard lock + report |
| Root Detection | File system check | Hard lock + report |
| SIM Change | Telephony monitor | Soft lock + report |
| App Removal | Package monitor | Hard lock + report |
| Device Owner Removal | Periodic check | Hard lock + wipe |

### Tamper Response Flow

```
Tamper Detected
    ↓
Apply Hard Lock
    ↓
Log Event Locally
    ↓
Send to Backend (POST /api/device/tamper)
    ↓
If Offline: Queue for Sync
    ↓
Continue Monitoring
```

### Implementation

**Detector**: `EnhancedTamperDetector.kt`
**Response**: `EnhancedAntiTamperResponse.kt`
**Boot Check**: `TamperBootChecker.kt`
**Monitor**: `SecurityMonitorService.kt`

**Location**: `app/src/main/java/com/microspace/payo/security/`

---

## 6. Auto-Update System

### Overview

Automatic app updates from GitHub releases using Device Owner privileges.

### Update Process

1. **Check for Updates**
   - Periodic check (every 6 hours)
   - Compare current version with latest release
   - Download APK if newer version available

2. **Download APK**
   - Download from GitHub releases
   - Verify checksum
   - Store in app directory

3. **Install Silently**
   - Use Device Owner privileges
   - Install without user interaction
   - Restart app automatically

### Configuration

```kotlin
object UpdateConfig {
    const val GITHUB_OWNER = "[YOUR_GITHUB_ORG]"
    const val GITHUB_REPO = "[YOUR_REPO_NAME]"
    const val APK_ASSET_NAME = "app-release.apk"
    const val CHECK_INTERVAL_HOURS = 6
}
```

### Implementation

**Manager**: `GitHubUpdateManager.kt`
**Worker**: `UpdateCheckWorker.kt`
**Scheduler**: `UpdateScheduler.kt`

**Location**: `app/src/main/java/com/microspace/payo/update/`

---

## 7. Logging & Diagnostics

### Log Categories

| Category | Purpose | Location |
|----------|---------|----------|
| Registration | Device registration events | `DeviceOwnerLogs/registration/` |
| Heartbeat | Heartbeat activity | `DeviceOwnerLogs/heartbeat/` |
| Security | Security events | `DeviceOwnerLogs/security/` |
| Tamper | Tamper detection | `DeviceOwnerLogs/tamper/` |
| Errors | Error logs | `DeviceOwnerLogs/errors/` |
| Sync | Offline sync | `DeviceOwnerLogs/sync/` |

### Remote Logging

Logs are automatically sent to backend:
- Error logs: Immediate
- Warning logs: Batched
- Info logs: On request

**API**: `POST /api/tech/devicecategory/logs/`

### Bug Reporting

Uncaught exceptions automatically reported:
- Stack trace
- Device information
- App state
- Recent logs

**API**: `POST /api/tech/devicecategory/bugs/`

### Implementation

**Manager**: `LogManager.kt`
**Reporter**: `ServerBugAndLogReporter.kt`
**Logger**: `SecureLogger.kt`

**Location**: `app/src/main/java/com/microspace/payo/utils/logging/`

---

## 8. Deactivation & Cleanup

### Deactivation Triggers

| Trigger | Source | Action |
|---------|--------|--------|
| Loan Complete | Backend | Remove Device Owner |
| Payment Complete | Backend | Remove Device Owner |
| Manual Deactivation | User/Admin | Remove Device Owner |

### Deactivation Process

1. **Receive Deactivation Command**
   - Via heartbeat response
   - Or manual trigger

2. **Remove Restrictions**
   - Clear all user restrictions
   - Restore factory settings access
   - Enable developer options

3. **Remove Device Owner**
   - Call `clearDeviceOwnerApp()`
   - Remove admin privileges
   - Clean up app data

4. **Notify Backend**
   - Send deactivation confirmation
   - Update device status

### Implementation

**Manager**: `DeviceOwnerDeactivationManager.kt`
**Handler**: `DeactivationHandler.kt`
**Activity**: `DeactivationActivity.kt`

**Location**: `app/src/main/java/com/microspace/payo/deactivation/`

---

## Feature Matrix

| Feature | Status | API Level | Notes |
|---------|--------|-----------|-------|
| Device Owner | ✅ | 21+ | Core feature |
| Heartbeat | ✅ | 21+ | 30-second interval |
| Hard Lock | ✅ | 21+ | Kiosk mode |
| Soft Lock | ✅ | 21+ | Overlay |
| Tamper Detection | ✅ | 21+ | Multiple checks |
| Auto-Update | ✅ | 21+ | GitHub releases |
| FRP | ✅ | 21+ | Factory Reset Protection |
| Offline Sync | ✅ | 21+ | Queue and sync |
| Encryption | ✅ | 23+ | Database encryption |
| Location Tracking | ✅ | 21+ | GPS/Network |

---

## Next Steps

- **Installation**: [4.0-DEVICE-INSTALLATION.md](4.0-DEVICE-INSTALLATION.md)
- **Registration**: [5.0-DEVICE-REGISTRATION.md](5.0-DEVICE-REGISTRATION.md)
- **APIs**: [6.0-APIS.md](6.0-APIS.md)
- **Heartbeat**: [7.0-DEVICE-HEARTBEAT.md](7.0-DEVICE-HEARTBEAT.md)
- **Locks**: [8.0-HARD-LOCK-AND-SOFT-LOCK.md](8.0-HARD-LOCK-AND-SOFT-LOCK.md)
- **Tamper**: [9.0-DEVICE-TAMPER.md](9.0-DEVICE-TAMPER.md)

---

**Last Updated:** [CURRENT_DATE]  
**Version:** [CURRENT_VERSION]
