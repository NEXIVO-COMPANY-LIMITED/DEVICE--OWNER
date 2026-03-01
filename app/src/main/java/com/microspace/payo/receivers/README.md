# Receivers Package

## Purpose
Handles incoming system broadcasts and event triggers from the Android OS and internal app events.

## Key Functionalities
- **Admin Receiver**: The primary bridge for Device Policy Manager events.
- **Boot Receiver**: Restores system state and restarts security services immediately after a device reboot.
- **Lock Event Receivers**: Listeners for payment and tamper-related triggers.
