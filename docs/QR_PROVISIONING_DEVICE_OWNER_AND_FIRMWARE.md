# Install via QR Code Provisioning (Device Owner + Firmware Together)

Use **QR code provisioning** so the device gets your app and **Device Owner** in one step. No manual `adb install` or `dpm set-device-owner` needed. All functions (Device Owner + firmware security) work the same after provisioning.

---

## How it works

1. **You:** Build an APK that includes both Device Owner logic and (optional) firmware security integration.
2. **You:** Put that APK at a URL and put the URL + checksum in `provisioning-config.json`.
3. **You:** Generate a QR code from `provisioning-config.json`.
4. **Device:** Factory reset (or no user accounts). During setup, scan the QR code.
5. **Device:** Downloads APK → installs → sets your app as **Device Owner** → system sends `PROVISIONING_SUCCESSFUL`.
6. **Your app:** `QRProvisioningReceiver` runs → applies restrictions → launches registration. Firmware code (if integrated) is already in the app and runs as normal.

So: **QR provisioning = install + Device Owner in one go**. Firmware security works if it’s already integrated into that same APK.

---

## Step 1: One APK with Device Owner + Firmware (optional)

- Device Owner code is already in your app (`receivers/AdminReceiver`, `QRProvisioningReceiver`, etc.).
- To have **firmware security** work with the same flow, integrate it into this app **before** building the APK you’ll use for QR:
  - Use **`docs/deviceowner-firmware-integration-complete`** and follow its `INSTALLATION_GUIDE.md` to add `FirmwareSecurity.kt`, JNI, and any activation calls (e.g. in `DeviceRegistrationRepository` or after provisioning).

The APK you build in the next step should be this single app (Device Owner + firmware if you want both).

---

## Step 2: Build release APK and checksum

From project root:

```powershell
.\scripts\build_and_checksum.ps1
```

This:

- Builds signed `app/build/outputs/apk/release/app-release.apk`
- Updates `provisioning-config.json` with the correct **Base64URL SHA256 checksum** of that APK

If you use a **no-WiFi** config:

```powershell
.\scripts\build_and_checksum.ps1 -NoWifi
```

---

## Step 3: Upload APK to the URL in the config

Your `provisioning-config.json` has:

- `android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION`: URL of the APK
- `android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM`: checksum (updated by `build_and_checksum.ps1`)

1. Upload `app-release.apk` to **exactly** that URL (e.g. GitHub Releases, your server, or a file host that returns the raw APK).
2. Ensure the checksum in the JSON matches the uploaded APK (don’t change the APK after running `build_and_checksum.ps1` without re-running it and re-uploading).

---

## Step 4: Generate QR code from provisioning config

Android expects the QR code to encode the **provisioning JSON** (or a URL that returns this JSON).

- **Option A – JSON in QR:** Use any QR generator to encode the **entire contents** of `provisioning-config.json` (as a single JSON string). Some tools require the JSON in one line (no line breaks).
- **Option B – URL in QR:** Serve `provisioning-config.json` from a URL (same as `PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION` host or another). Generate a QR code that contains **only that URL**. The device will fetch the JSON from the URL.

Use a QR size that’s easy for the phone camera to read (e.g. not too small).

---

## Step 5: Use a real Pixel (or any Android device) with no accounts

- **Device:** Real Android phone (e.g. **Google Pixel**) with Android 8+ (API 26+; API 34 recommended).
- **State:** **No user accounts** (no Google account, etc.). Easiest: **Factory reset** the device.
- After reset, go through setup until you see the **Welcome / “Set up your device”** screen where it offers **“Set up as work device”** or **scan a QR code**.

---

## Step 6: Scan QR and complete provisioning

1. On the Welcome screen, choose the option to **set up as work device** or **scan QR code** (wording depends on Android version).
2. Scan the QR code you generated from `provisioning-config.json` (or from its URL).
3. If WiFi is in the config, device may connect first; then it will download the APK, verify checksum, install, and set your app as **Device Owner**.
4. When provisioning finishes, the system sends `PROVISIONING_SUCCESSFUL`; your **QRProvisioningReceiver** runs, applies restrictions, and launches registration. Firmware code in the same app runs as usual (e.g. on first registration or in Application).

No `adb` or manual `dpm set-device-owner` is needed when you use this flow.

---

## Verify setup

- Run the project’s provisioning checks:

  ```powershell
  .\scripts\verify_provisioning_setup.ps1
  ```

- On the device: open your app and confirm Device Owner behavior (e.g. restrictions, registration). If you integrated firmware, check logs for your FirmwareSecurity/JNI messages (e.g. `adb logcat | findstr "FirmwareSecurity"`).

---

## Summary

| Goal | Action |
|------|--------|
| Install + Device Owner in one go | Use QR provisioning (this doc); no adb install / set-device-owner. |
| Firmware security works too | Integrate firmware into the same app (`docs/deviceowner-firmware-integration-complete`), then build that app and use it as the APK in provisioning-config.json. |
| Correct checksum | Always run `.\scripts\build_and_checksum.ps1` after building release APK and use the updated `provisioning-config.json` for the QR. |
| Real Pixel / Android device | Factory reset (or no accounts), then at setup choose “work device” / QR and scan. |

Using **`docs/android-firmware-security-complete`** is for reference (e.g. kernel/bootloader); the **app-side** integration that works with your Device Owner app and QR provisioning is **`docs/deviceowner-firmware-integration-complete`**.
