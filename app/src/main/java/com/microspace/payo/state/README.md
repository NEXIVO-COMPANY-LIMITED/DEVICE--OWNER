# State Package

## Purpose
Provides a centralized, thread-safe system for managing and validating the device's lock state.

## Key Functionalities
- **Lock State Manager**: The single source of truth for the current device state (Hard Lock, Soft Lock, etc.).
- **Transition Management**: Ensures smooth and logical progression between different management states.
- **State Validation**: Continuously verifies that the local state matches system-level enforcement.
