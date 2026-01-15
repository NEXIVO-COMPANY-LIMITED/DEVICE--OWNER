# Device Registration Payload Update

## Summary
Updated the device registration payload to match the backend API schema exactly.

## Changes Made

### 1. **DeviceRegistrationPayload Model** (`UnifiedHeartbeatApiModels.kt`)
- Changed `device_imeis` from `List<String>` to `String`
  - Now sends IMEIs as a comma-separated string
  - Example: `"358533130366352,359278280366351"`
  
- Changed `tamper_flags` from `List<String>` to `String`
  - Now sends as empty string `""` instead of empty list
  - Matches backend schema

### 2. **AuthRepository Registration** (`AuthRepository.kt`)
- Updated payload construction to use correct types:
  ```kotlin
  device_imeis = imeiList?.filter { it.isNotBlank() }?.joinToString(",") ?: ""
  tamper_flags = ""
  ```

## API Schema (Correct Format)
```json
{
  "device_id": "string",
  "serial_number": "string",
  "device_type": "laptop",
  "manufacturer": "string",
  "system_type": "string",
  "model": "string",
  "platform": "string",
  "os_version": "string",
  "os_edition": "string",
  "processor": "string",
  "installed_ram": "string",
  "total_storage": "string",
  "build_number": 2147483647,
  "sdk_version": 2147483647,
  "device_imeis": "string",
  "loan_number": "string",
  "machine_name": "string",
  "android_id": "string",
  "device_fingerprint": "string",
  "bootloader": "string",
  "security_patch_level": "string",
  "system_uptime": 9223372036854776000,
  "installed_apps_hash": "string",
  "system_properties_hash": "string",
  "is_device_rooted": true,
  "is_usb_debugging_enabled": true,
  "is_developer_mode_enabled": true,
  "is_bootloader_unlocked": true,
  "is_custom_rom": true,
  "latitude": 0,
  "longitude": 0,
  "tamper_severity": "string",
  "tamper_flags": "string",
  "battery_level": 2147483647
}
```

## Files Modified
1. `app/src/main/java/com/example/deviceowner/data/api/models/UnifiedHeartbeatApiModels.kt`
2. `app/src/main/java/com/example/deviceowner/data/repository/AuthRepository.kt`

## Testing
- Rebuild the app
- Try registration again
- The payload should now match the backend schema exactly
- Should resolve the `company_id` null constraint error (backend issue, but correct payload format helps)

## Note
The `company_id` null constraint error is still a backend issue. The backend needs to either:
1. Make `company_id` nullable
2. Set a default `company_id` based on loan_id or authentication context
3. Require the client to send a valid `company_id`

Contact your backend team to resolve this.
