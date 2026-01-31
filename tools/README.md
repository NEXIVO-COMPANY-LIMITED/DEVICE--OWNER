# Tools and Scripts

This directory contains build, deployment, and utility scripts for the Device Owner Firmware Security project.

## Structure

- **build/**: Build automation scripts
  - `build_all.ps1` / `build_all.sh`: Complete build process
  - `verify_build.sh`: Verify build outputs

- **deployment/**: Device deployment scripts
  - `prepare_device.sh`: Prepare device for firmware installation
  - `flash_firmware.sh` / `flash_firmware.ps1`: Flash firmware to device
  - `verify_installation.sh`: Verify installation success
  - `rollback.sh`: Rollback to stock firmware

- **aosp/**: AOSP integration scripts
  - `copy_to_aosp.sh`: Copy modifications to AOSP tree
  - `patch_aosp.sh`: Apply patches to AOSP
  - `aosp_config.sh`: Configure AOSP build

- **utilities/**: Utility scripts
  - `check_dependencies.sh`: Check build dependencies
  - `setup_environment.sh`: Setup development environment
  - `clean_build.sh`: Clean build artifacts

## Usage

All scripts should be run from the project root directory. See individual script headers for usage instructions.

## Requirements

- Windows: PowerShell 5.1+ or WSL2
- Linux/WSL: Bash shell
- AOSP source tree (for firmware builds)
- Android SDK/NDK (for app builds)
