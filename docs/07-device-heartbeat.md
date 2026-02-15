# Device Heartbeat

This document describes the **heartbeat** mechanism: how device data is sent to the backend at a fixed interval, what is sent, and how server responses (lock, deactivation, etc.) are processed.

---

## Overview

- **Purpose**: Send device telemetry and status to the backend at a **30-second** interval so the server can track device state, enforce policy (e.g. lock), and signal deactivation when the loan is complete.
- **Primary path**: Heartbeat runs inside **SecurityMonitorService** via **HeartbeatService** (in-process, 30s timer). This keeps the interval exact and avoids WorkManager’s minimum 15-minute constraint for periodic work.
- **Backup path**: **HeartbeatWorker** is used for one-off heartbeat (e.g. after deactivation flow); it is **not** used for the regular 30s schedule.

---

## Endpoint and payload

- **Endpoint**: `POST api/devices/{deviceId}/data/`  
- **Path**: `ApiEndpoints.DEVICE_HEARTBEAT`  
- **Request model**: **HeartbeatRequest** (`HeartbeatModels.kt`)

### Request fields (summary)

- **Identifiers**: `device_id`, `android_id`, `heartbeat_timestamp` (ISO-8601).  
- **Device**: `model`, `manufacturer`, `serial_number` / `serial`, `device_imeis`, `installed_ram`, `total_storage`.  
- **Location**: `latitude`, `longitude`, and/or `location_info`.  
- **Security**: `is_device_rooted`, `is_usb_debugging_enabled`, `is_developer_mode_enabled`, `is_bootloader_unlocked`, `is_custom_rom`.  
- **OS**: `sdk_version`, `os_version`, `security_patch_level`.  
- **Hashes** (optional): `installed_apps_hash`, `system_properties_hash`.  
- **Battery**: `battery_level`.  
- **Nested objects** (optional): `device_info`, `android_info`, `imei_info`, `storage_info`, `location_info`, `security_info`, `system_integrity`, `app_info`.

Data is built by **DeviceDataCollector.collectHeartbeatData(deviceId)** (from `core.device` or services), which fills flat and nested fields as required by the backend.

---

## Response handling

- **Response model**: **HeartbeatResponse** (`HeartbeatModels.kt`).

The app uses the response to:

- **Lock state**: `content.isLocked`, `management.isLocked`, `management.managementStatus`, top-level `is_locked` / `management_status` → **RemoteDeviceControlManager** apply soft/hard lock as instructed.
- **Deactivation**: `deactivation.status == "requested"`, `deactivate_requested`, `payment_complete`, `loan_complete`, `loan_status` (e.g. completed/paid/closed) → trigger deactivation flow (e.g. **DeviceOwnerDeactivationManager** / **DeactivationHandler**).
- **Other**: `next_payment`, `tamper_indicators`, `instructions`, `expected_device_data`, `security_status`, `changes_detected`, `comparison_result` — used for UI, comparison, or further logic.

Helper methods on **HeartbeatResponse**:

- `isDeviceLocked()` — true if server says device should be locked.  
- `getLockReason()` — reason string from `content.reason` or `management.block_reason`.

Processing is implemented in **HeartbeatWorker.processHeartbeatResponse()** and in **HeartbeatService** when it receives the API response.

---

## Where heartbeat runs

### 1. SecurityMonitorService + HeartbeatService (primary)

- **SecurityMonitorService** starts as a **foreground service** and calls **HeartbeatService.schedulePeriodicHeartbeat(deviceId)**.
- **HeartbeatService** uses a **Handler** (or similar) to run every **30 seconds** (constant `HEARTBEAT_INTERVAL_MS = 30_000L`).
- Device ID is resolved from `device_data.device_id_for_heartbeat`, `device_registration.device_id`, or **SharedPreferencesManager** in **resolveDeviceIdForHeartbeat()**.
- If no device ID is available, the service logs that heartbeat will start when the device is registered and does not schedule.

### 2. HeartbeatWorker (one-off / backup)

- **HeartbeatWorker** (WorkManager) is used for **single** heartbeat runs (e.g. deactivation flow), not for the 30s loop.
- It uses **DeviceDataCollector.collectHeartbeatData(deviceId)** and **ApiClient.sendHeartbeat(deviceId, request)**.
- Implements **aggressive retry** (e.g. up to 10 retries with backoff) and on success calls **processHeartbeatResponse()** and updates last heartbeat time in **SharedPreferencesManager**.
- **HeartbeatWorker.scheduleHeartbeat()** is documented as “no longer used” for 30s scheduling; it only cancels any existing HeartbeatWork so that the 30s loop remains the single source of periodic heartbeat.

---

## Offline / sync

- If the device is offline, heartbeat fails; **OfflineEvent** (and **OfflineSyncWorker** / **OfflineSyncManager**) can store events for later sync when the network is available. Heartbeat itself does not persist payloads locally for retry beyond the Worker’s retries; offline sync is handled by the generic offline-event mechanism.

---

## Summary

| Item | Detail |
|------|--------|
| Interval | 30 seconds |
| Primary | SecurityMonitorService → HeartbeatService (in-process) |
| Backup / one-off | HeartbeatWorker (WorkManager) |
| Endpoint | POST api/devices/{deviceId}/data/ |
| Request | HeartbeatRequest (device + security + location + nested maps) |
| Response | HeartbeatResponse (lock, deactivation, next_payment, etc.) |
| Data collection | DeviceDataCollector.collectHeartbeatData(deviceId) |
| Device ID source | device_data, device_registration, SharedPreferencesManager |

For server-driven **lock** and **tamper** reporting, see [Device Tamper](09-device-tamper.md) and the overview in [Device Owner](01-device-owner-overview.md).
