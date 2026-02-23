# Security Package

## Purpose
The primary defense layer of the application, responsible for anti-tamper logic and firmware integrity monitoring.

## Key Functionalities
- **SIM Change Detection**: Detects and responds to unauthorized SIM card removal or replacement.
- **Firmware Monitoring**: Monitors bootloader, ADB, and system property integrity.
- **Anti-Tamper Response**: High-priority actions triggered by security violations.
