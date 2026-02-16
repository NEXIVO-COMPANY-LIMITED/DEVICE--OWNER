# DEVICE OWNER - Enterprise Device Management App

This project is a specialized Android application designed to run with **Device Owner** (DO) and **Device Admin** privileges. It is primarily used for asset management, loan compliance, and device security hardening.

## 🚀 Overview

The application acts as a Device Policy Controller (DPC) to enforce enterprise-level security policies on Android devices. It ensures that devices remain in a secure state, reports health status (heartbeat) to a central server, and provides remote control capabilities like locking or deactivating the device based on loan status.

## 🛠 Features Implemented

### 1. Device Management & Hardening
- **Device Owner Provisioning**: Deployed via QR code or NFC to gain full control over the device.
- **Enterprise Restrictions**: Blocks factory reset, developer options, USB debugging, safe boot, and manual user management.
- **Security Modes**: 
  - `STANDARD`: Critical restrictions active (Factory Reset, Safe Boot blocked).
  - `STRICT`: Maximum lockdown, including blocking ADB and Developer Options.
- **Silent Operation**: Management messages and policy dialogs are suppressed for a seamless user experience.

### 2. Device Registration & Tracking
- **Loan Integration**: Devices are registered using a loan number.
- **Data Collection**: Automatically gathers device metadata (IMEI, Serial, Model, OS Version, Storage, Battery status).
- **Unique Identification**: Stores a persistent `device_id` from the backend for all communications.

### 3. Monitoring & Heartbeat
- **30-Second Heartbeat**: Continuous background reporting of device status, security flags, and location.
- **Server-Driven Instructions**: Receives and processes real-time commands from the backend (Lock, Unlock, Deactivate, Wipe).

### 4. Lock & Kiosk Mechanisms
- **Hard Lock (Kiosk Mode)**: Forces the device into a dedicated lock screen activity that persists across reboots. Blocks navigation and status bar.
- **Soft Lock**: Overlays reminders or warnings (e.g., SIM change or payment reminders) without completely blocking the device.
- **PIN Unlock**: Supports remote or local PIN entry for authorized unlocking.

### 5. Advanced Tamper Detection
- **Boot-time Integrity Checks**: Checks for unlocked bootloaders, developer options, and debugging on every boot.
- **Runtime Monitoring**: Real-time detection of SIM changes, package removal, or attempts to remove Device Owner status.
- **Anti-Tamper Response**: Automatic escalation to Hard Lock if tampering is detected.

### 6. Maintenance & Updates
- **Silent Auto-Update**: Downloads and installs updates from GitHub Releases in the background without user intervention.
- **Logging & Bug Reporting**: Comprehensive local log management and remote technical reporting for troubleshooting.

## 📂 Project Structure

- `app/src/main/java/com/example/deviceowner/device/`: Core `DeviceOwnerManager` and policy logic.
- `app/src/main/java/com/example/deviceowner/services/`: Background monitors and heartbeat services.
- `app/src/main/java/com/example/deviceowner/ui/`: Activities for registration, status, and lock screens.
- `app/src/main/java/com/example/deviceowner/data/`: Room database and API clients.

## 📖 Detailed Documentation

Comprehensive documentation for developers is located in the [docs/](./docs/) directory:
- [01 Device Owner Overview](./docs/01-device-owner-overview.md)
- [02 Features Implemented](./docs/02-features-implemented.md)
- [06 API Reference](./docs/06-apis.md)
- [07 Heartbeat Mechanism](./docs/07-device-heartbeat.md)
- [09 Tamper Detection](./docs/09-device-tamper.md)
- [13 Services Overview](./docs/13-services.md)

---
*Note: This app requires Android 12 (API 31) or higher.*
