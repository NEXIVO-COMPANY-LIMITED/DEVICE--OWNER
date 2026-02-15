# Device Owner — Overview

## Purpose

The **Device Owner** app is an Android enterprise device management application that runs with **Device Owner** and **Device Admin** privileges. It provides:

- **Loan/asset-backed device control**: Devices are tied to a loan; the app enforces security, reports status, and supports remote lock/unlock and deactivation when the loan is complete.
- **Security hardening**: Restricts factory reset, developer options, USB debugging, unknown sources, and other high-risk actions.
- **Tamper detection and response**: Detects bootloader unlock, root, developer mode, USB debug, SIM change, and similar events; applies hard lock and reports to the backend.
- **Heartbeat and sync**: Sends device data to the backend at a fixed interval (e.g. 30 seconds) and processes server instructions (lock, deactivation, etc.).
- **Silent operation**: Management messages can be suppressed (Complete Silent Mode) so the user does not see policy dialogs.

## Architecture Summary

| Layer | Responsibility |
|-------|----------------|
| **Device Owner / Device Admin** | `DeviceOwnerManager`, `SilentDeviceOwnerManager`, `AdminReceiver` — policy application, compatibility, silent restrictions |
| **Security & tamper** | `TamperBootChecker`, `EnhancedAntiTamperResponse`, `SIMChangeDetector`, `DeviceOwnerRemovalDetector`, `AccessibilityGuard` — detect and respond to tamper |
| **Monitoring & heartbeat** | `SecurityMonitorService`, `HeartbeatService`, `HeartbeatWorker` — foreground monitoring, 30s heartbeat, policy checks |
| **Lock control** | `RemoteDeviceControlManager`, `HardLockManager`, `SoftLockOverlayService` — soft lock overlay, hard lock (kiosk) |
| **Registration & data** | `DeviceRegistrationRepository`, `DeviceDataCollectionActivity`, `DeviceDataCollector` — loan number, device ID, data collection for backend |
| **Backend communication** | `ApiClient`, `ApiEndpoints`, interceptors — registration, heartbeat, tamper, logs, bugs, installation status |

## Security Modes

- **STANDARD**: ADB/developer options allowed for support; factory reset and other critical actions blocked.
- **STRICT**: ADB and USB debugging blocked; maximum lockdown.

Mode is stored in `device_owner_config` and applied via `DeviceOwnerManager.applySecurityMode()`.

## Key Constraints

- **Minimum SDK**: API 31 (Android 12).
- **Device Owner** must be set via provisioning (e.g. QR code, `PROVISION_MANAGED_DEVICE`) so the app can apply user restrictions and system policies.
- **Foreground service**: Heartbeat runs inside `SecurityMonitorService` (foreground) to maintain a stable 30s interval when the app is in background or closed.

## Related Documentation

Docs are ordered so you can follow the flow from overview → features → setup → APIs → behavior → reference:

- [Features Implemented](02-features-implemented.md) — What the app does
- [Compatibility](03-compatibility.md) — Device requirements
- [Device Installation](04-device-installation.md) — Provisioning and installation
- [Device Registration](05-device-registration.md) — Registration flow
- [APIs](06-apis.md) — Backend endpoints reference
- [Device Heartbeat](07-device-heartbeat.md) — Heartbeat mechanism
- [Hard Lock and Soft Lock](08-hard-lock-and-soft-lock.md) — Lock modes
- [Device Tamper](09-device-tamper.md) — Tamper detection and response
- [Local Databases](10-local-databases.md) — Room and persistence
- [Device Logs and Bugs](11-device-logs-and-bugs.md) — Logging and bug reporting
- [Agent Update](12-agent-update.md) — Auto-update from GitHub
- [Services](13-services.md) — All configured Android services
- [Folder Structure](14-folder-structure.md) — Project layout
