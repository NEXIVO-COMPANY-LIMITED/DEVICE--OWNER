# Device Registration

This document describes how a device is **registered** with the backend: loan number entry, device data collection, API registration, and persistence of the device ID.

---

## Overview

Registration ties the device to a **loan** and to the backend:

1. User enters a **loan number** (validated when required).
2. App collects **device data** (IMEI, model, storage, etc.) and optionally shows it for verification.
3. App calls **POST api/devices/mobile/register/** with loan number and device data.
4. Backend returns a **device_id**; the app stores it and uses it for heartbeat, tamper, logs, and all subsequent API calls.

Until registration succeeds, heartbeat and server-driven lock are not active (or use a placeholder); after registration, `device_id` is stored and used everywhere.

---

## Registration Flow (User Journey)

### 1. Entry point

- **RegistrationStatusActivity** (launcher) checks whether the device is already registered (e.g. `device_id` in SharedPreferences). If not, it routes to registration.
- **DeviceRegistrationActivity** is the screen where the user enters the **loan number**.

### 2. Loan number input

- **DeviceRegistrationActivity**  
  - Uses **LoanNumberValidator** for format/validation if applicable.  
  - On submit: saves loan number (e.g. via **DeviceRegistrationRepository.saveLoanNumberForRegistration()**) and navigates to **DeviceDataCollectionActivity** with the loan number as extra.

### 3. Device data collection

- **DeviceDataCollectionActivity**  
  - Collects device information (IMEI(s), model, manufacturer, storage, Android version, etc.) using **DeviceDataCollector** or **DeviceDataCollectionService**.  
  - Displays data for user verification.  
  - On confirm, calls the registration API with loan number + device data and handles success/error.

### 4. Registration API call

- **Endpoint**: `POST api/devices/mobile/register/`  
- **Client**: `ApiClient` (and related **ApiService**).  
- **Models**: `DeviceRegistrationModels` (request/response with `device_id`, etc.).  
- **Repository**: **DeviceRegistrationRepository** coordinates saving the loan number, calling the API, and persisting the returned **device_id** in:
  - `device_registration` SharedPreferences (e.g. `device_id`)
  - `device_data` SharedPreferences (e.g. `device_id_for_heartbeat`)
  - **SharedPreferencesManager** (e.g. `setDeviceIdForHeartbeat(deviceId)`).

### 5. Post-registration

- **RegistrationSuccessActivity** is shown after successful registration.  
- **DeviceOwnerApplication** reads `device_id` and:
  - Starts **SecurityMonitorService** (which starts the 30s heartbeat).
  - Initializes **SIMChangeDetector** when the app is Device Owner.

---

## Data Storage

| Storage | Key(s) | Purpose |
|---------|--------|---------|
| SharedPreferences `device_registration` | `device_id`, loan number | Primary device ID and loan info |
| SharedPreferences `device_data` | `device_id_for_heartbeat` | Device ID used for heartbeat and API calls |
| SharedPreferences `device_owner_prefs` / **SharedPreferencesManager** | `device_id_for_heartbeat` | Same device ID for consistency |
| Local DB (optional) | **DeviceRegistrationEntity**, **CompleteDeviceRegistrationEntity** | Persistence and history |

The app resolves "current device ID" from these sources (e.g. in **HeartbeatWorker**, **ServerBugAndLogReporter**, **EnhancedAntiTamperResponse**): typically `device_data.device_id_for_heartbeat` → `device_registration.device_id` → **SharedPreferencesManager.getDeviceIdForHeartbeat()**.

---

## Registration errors

- **RegistrationErrorViewerActivity** — Lists and opens saved registration error files (e.g. HTML) from a dedicated folder (e.g. `DeviceOwner/RegistrationErrors`) so support can inspect failed registration attempts.

---

## Summary

| Step | Component | Description |
|------|-----------|-------------|
| 1 | RegistrationStatusActivity | Decides if registration is needed; routes to DeviceRegistrationActivity |
| 2 | DeviceRegistrationActivity | Loan number input; validates and saves; navigates to data collection |
| 3 | DeviceDataCollectionActivity | Collects device data; shows for verification; calls register API |
| 4 | DeviceRegistrationRepository | Saves loan number; calls API; persists device_id |
| 5 | ApiClient / ApiService | POST api/devices/mobile/register/ |
| 6 | RegistrationSuccessActivity | Shown on success |
| 7 | DeviceOwnerApplication | Starts SecurityMonitorService and SIM change detection when device_id exists |

For **heartbeat** (which uses `device_id`), see [Device Heartbeat](07-device-heartbeat.md).
