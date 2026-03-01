# Control Package

## Purpose
This package manages the high-level enforcement of device states, specifically handling the logic for hard locks, soft locks, and kiosk mode activation.

## Key Functionalities
- **Remote Device Control**: Orchestrates the transition between different lock states.
- **Hard Lock Management**: Logic for applying system-level restrictions when a device is non-compliant.
- **Lock Routing**: Determines which specialized lock activity to launch based on the lock reason.
