# Device Owner App

Android enterprise **Device Owner** application for loan/asset-backed device management. The app runs with Device Owner and Device Admin privileges to enforce security, report device status, support remote lock/unlock, and deactivate when the loan is complete.

---

## Overview

- **Purpose**: Manage devices tied to a loan: restrict risky actions (factory reset, developer options, unknown sources), detect tamper (bootloader unlock, root, SIM change, etc.), send heartbeat data to the backend every 30 seconds, and apply hard or soft lock based on server or local policy.
- **Backend**: Registration, heartbeat, tamper reporting, installation status, device logs, and bug reports are sent to a Django backend (e.g. sponsa_backend). Base URL and API key are configured in the app (e.g. `AppConfig`).
- **Minimum Android**: API 31 (Android 12) for policy; provisioning compatibility check requires Android 13+ (API 33).
- **Provisioning**: The app is set as Device Owner via Android managed provisioning (e.g. QR code). It does not set itself as Device Owner; the system does during provisioning.

---

## Main capabilities

| Area | Description |
|------|-------------|
| **Device Owner / Admin** | User restrictions, factory reset block, security modes (STANDARD / STRICT), silent restrictions |
| **Registration** | Loan number entry → device data collection → POST register → store device_id |
| **Heartbeat** | 30s interval (SecurityMonitorService + HeartbeatService); server can return lock, deactivation, next_payment |
| **Hard lock** | Full kiosk (HardLockActivity), persists across reboot; used on server lock or tamper |
| **Soft lock** | Dismissible overlay (payment reminder, SIM change warning) |
| **Tamper** | Boot/runtime/SIM/package-removal/Device-Owner-removal detection → hard lock + POST tamper |
| **Logs & bugs** | Local LogManager + remote tech API (logs, bug reports, uncaught exceptions) |
| **Local DB** | Room (DeviceOwnerDatabase): registration, heartbeat, tamper, offline queue, lock history |
| **Auto-update** | GitHub Releases: check for new APK, download, silent install (Device Owner) |

---

## Documentation

Full technical documentation is in the **[docs/](docs/)** folder. Documents are ordered so you can read them in sequence:

1. Start with **[docs/01-device-owner-overview.md](docs/01-device-owner-overview.md)** for architecture and purpose.  
2. Then **[docs/02-features-implemented.md](docs/02-features-implemented.md)** for a list of features.  
3. Follow the **[docs/README.md](docs/README.md)** index for the full reading order (compatibility → installation → registration → APIs → heartbeat → hard/soft lock → tamper → local databases → logs & bugs → agent update → services → folder structure).

*Unaweza kupitia docs kwa mpangilio wa semantic: overview → features → compatibility → installation → registration → APIs → heartbeat → locks → tamper → databases → logs → update → services → folder structure.*

---

## Project structure (summary)

- **app/src/main/java/com/example/deviceowner/** — Kotlin source (config, control, core, data, device, monitoring, security, services, ui, update, utils, receivers, etc.).  
- **app/src/main/res/** — Layouts, values, xml, drawable.  
- **app/src/main/AndroidManifest.xml** — Activities, services, receivers, permissions.  
- **docs/** — All documentation (01–14 in semantic order).

See **[docs/14-folder-structure.md](docs/14-folder-structure.md)** for the full tree.

---

## Build and run

- Open the project in Android Studio; build with Gradle.  
- Device Owner must be set via provisioning (e.g. QR code for `PROVISION_MANAGED_DEVICE`).  
- Configure backend base URL and API key (e.g. in `AppConfig` / BuildConfig) before release.

---

## License and support

See repository and organization policies. For technical details, use the [docs/](docs/) folder.
