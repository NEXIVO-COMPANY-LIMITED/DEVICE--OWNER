# Device Tamper

This document describes **tamper detection** and **response**: what is detected, how the app reacts (hard lock, server report), and how tamper events are reported to the backend.

---

## Overview

**Tamper** means any action or state that violates the security policy: developer options, USB debugging, bootloader unlock, root, custom ROM, SIM change, app removal, etc. The app:

1. **Detects** tamper from multiple sources (boot, runtime monitoring, SIM, package removal).  
2. **Responds locally** by applying **hard lock** (kiosk) immediately—without waiting for the server.  
3. **Reports** the tamper event to the backend via **POST api/tamper/mobile/{device_id}/report/** (or queues it offline if no network).

---

## Tamper types (backend)

The backend expects a fixed set of **tamper_type** values. The app maps internal names to these (see **TamperEventRequest.mapToDjangoTamperType()** in `TamperModels.kt`):

- `BOOTLOADER_UNLOCKED`  
- `ROOT_DETECTED` (e.g. MAGISK_DETECTED)  
- `DEVELOPER_MODE` (e.g. DEVELOPER_OPTIONS)  
- `USB_DEBUG` (e.g. USB_DEBUGGING, UNAUTHORIZED_ACCESS)  
- `CUSTOM_ROM`  
- `SYSTEM_MODIFIED` (e.g. DEVICE_OWNER_REMOVED, POLICY_VIOLATION)  
- `SECURITY_PATCH_OLD`  
- `UNKNOWN_SOURCE`  
- `ADB_ENABLED`  
- `MOCK_LOCATION` (e.g. LOCATION_VIOLATION)  
- `SIM_CHANGE` (e.g. SIM_SWAP)  
- `PACKAGE_REMOVED` (e.g. PACKAGE_REMOVAL)

Severity is sent as **CRITICAL** (Django only accepts CRITICAL for these events).

---

## Detection sources

### 1. Boot (TamperBootChecker)

- **Trigger**: **BootReceiver** runs **TamperBootChecker.runTamperCheck(context)** after boot (with a short delay, e.g. 2 seconds).
- **Checks**: Developer options, USB debugging, bootloader unlock (**BootloaderLockEnforcer**).
- **Response**: If any violation → **RemoteDeviceControlManager.applyHardLock()** with `tamperType`; **EnhancedAntiTamperResponse.sendTamperToBackendOnly()** sends the tamper event to the server.

### 2. Runtime (SecurityMonitorService)

- **SecurityMonitorService** runs a loop (e.g. every 2 seconds) checking Developer Options, USB debugging, and tamper (bootloader, etc.).  
- If a violation is detected → soft lock or escalation to hard lock and tamper report, depending on implementation (e.g. **DeviceViolationDetector**, **EnhancedTamperDetector**).

### 3. Device Owner removal (DeviceOwnerRemovalDetector)

- **DeviceOwnerRemovalDetector** (part of the “Security Mesh” in SecurityMonitorService) checks periodically (e.g. every 5 seconds) whether the app is still Device Owner.  
- If Device Owner was removed → hard lock + remote wipe and tamper report (e.g. SYSTEM_MODIFIED / DEVICE_OWNER_REMOVED).

### 4. SIM change (SIMChangeDetector)

- **SIMChangeDetector** monitors SIM state. On change → **SIMChangeOverlayActivity** (warning screen) and tamper report with type **SIM_CHANGE**.

### 5. Package removal (PackageRemovalReceiver)

- **PackageRemovalReceiver** listens for **PACKAGE_REMOVED**, **PACKAGE_REPLACED**, **PACKAGE_FULLY_REMOVED**, **PACKAGE_DATA_CLEARED**.  
- If the Device Owner app (or critical component) is removed or data cleared → tamper type **PACKAGE_REMOVED** and appropriate response (hard lock / report).

### 6. Firmware / integrity (FirmwareSecurityMonitorService, EnhancedTamperDetector)

- **FirmwareSecurityMonitorService** and **EnhancedTamperDetector** (or similar) detect root, custom ROM, system modifications → tamper type ROOT_DETECTED, CUSTOM_ROM, SYSTEM_MODIFIED, etc.

---

## Response pipeline (EnhancedAntiTamperResponse)

**EnhancedAntiTamperResponse** centralizes the response:

1. **respondToTamper(tamperType, severity, description, extraData)**  
   - Dispatches by severity (CRITICAL/HIGH/MEDIUM/LOW); in practice all go to **respondWithHardLockAndNotify**.

2. **respondWithHardLockAndNotify**  
   - **Step 1**: Apply **hard lock** immediately via **RemoteDeviceControlManager.applyHardLock()**; apply **DeviceOwnerManager** restrictions. No wait for server.  
   - **Step 2**: In background, **notifyRemoteServer()** — build **TamperEventRequest.forDjango()**, call **ApiClient** to POST to **api/tamper/mobile/{device_id}/report/**. On failure or offline, persist to **OfflineEvent** for later sync.

3. **sendTamperToBackendOnly**  
   - Used when hard lock was already applied elsewhere (e.g. TamperBootChecker). Only sends the tamper event to the backend (or queues it).

---

## API

- **Endpoint**: `POST api/tamper/mobile/{deviceId}/report/`  
- **Request**: **TamperEventRequest** — `tamper_type`, `severity` (CRITICAL), `description`, `timestamp` (ISO-8601), `extra_data`.  
- **Response**: **TamperEventResponse** — `locked` (boolean).  
- **Client**: **ApiClient** (e.g. `reportTamper(deviceId, request)` or equivalent).

---

## Local persistence

- **TamperDetectionDao** / **TamperDetectionEntity** store tamper events locally for history or offline sync.  
- **OfflineEvent** is used when the network is unavailable so the tamper report is sent later by **OfflineSyncWorker** / **OfflineSyncManager**.

---

## Summary

| Topic | Detail |
|-------|--------|
| Detection | Boot (TamperBootChecker), runtime (SecurityMonitorService), Device Owner removal, SIM change, package removal, firmware/integrity |
| Local response | Hard lock + restrictions immediately; no server wait |
| Server report | POST api/tamper/mobile/{device_id}/report/ |
| Request | TamperEventRequest (tamper_type, severity, description, timestamp, extra_data) |
| Types | BOOTLOADER_UNLOCKED, ROOT_DETECTED, DEVELOPER_MODE, USB_DEBUG, SIM_CHANGE, PACKAGE_REMOVED, etc. |
| Central handler | EnhancedAntiTamperResponse (respondToTamper, sendTamperToBackendOnly) |

For **logs** and **bugs** (which can include tamper-related messages), see [Device Logs and Bugs](11-device-logs-and-bugs.md).
