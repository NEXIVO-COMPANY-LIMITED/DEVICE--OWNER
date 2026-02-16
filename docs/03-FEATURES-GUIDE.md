# 03 Features Guide

Complete documentation of all Device Owner features and capabilities.

---

## 📋 Feature Overview

Device Owner includes the following major features:

1. **Device Management & Hardening**
2. **Device Registration & Tracking**
3. **Continuous Monitoring (Heartbeat)**
4. **Device Locking & Kiosk Mode**
5. **Tamper Detection & Response**
6. **Auto-Update System**
7. **Logging & Diagnostics**
8. **Deactivation & Cleanup**

---

## 1️⃣ Device Management & Hardening

### Overview

Device Owner enforces enterprise-level security policies to prevent unauthorized modifications and ensure compliance.

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

### Configuration

```kotlin
// Example: Set security mode
deviceOwnerManager.setSecurityMode(SecurityMode.STRICT)

// Example: Block factory reset
deviceOwnerManager.blockFactoryReset(true)

// Example: Disable developer options
deviceOwnerManager.disableDeveloperOptions(true)
```

---

## 2️⃣ Device Registration & Tracking

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
   - OS Version
   - Storage Capacity
   - Battery Status
   - Location

3. **Backend Assignment**
   - Backend validates loan number
   - Assigns unique device_id
   - Stores device metadata
   - Initializ