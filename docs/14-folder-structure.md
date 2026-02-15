# Folder Structure

Full project layout in tree form (same style as reference). Package: `com.example.deviceowner` (Kotlin). Root name shown as **DeviceOwnerAutoUpdate** for display; your repo folder may be **DEVICEOWNER**.

---

## Full project tree

```
DeviceOwnerAutoUpdate/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/deviceowner/
│   │   │   │   ├── AppConfig.kt
│   │   │   │   ├── DeviceOwnerApplication.kt
│   │   │   │   ├── config/
│   │   │   │   │   ├── ApiConstants.kt
│   │   │   │   │   └── FrpConfig.kt
│   │   │   │   ├── control/
│   │   │   │   │   ├── HardLockManager.kt
│   │   │   │   │   └── RemoteDeviceControlManager.kt
│   │   │   │   ├── core/
│   │   │   │   │   ├── SilentDeviceOwnerManager.kt
│   │   │   │   │   ├── device/
│   │   │   │   │   │   └── DeviceDataCollector.kt
│   │   │   │   │   ├── frp/
│   │   │   │   │   │   ├── FrpManager.kt
│   │   │   │   │   │   └── FrpPolicyManager.kt
│   │   │   │   │   └── sync/
│   │   │   │   │       └── OfflineSyncManager.kt
│   │   │   │   ├── data/
│   │   │   │   │   ├── models/
│   │   │   │   │   │   ├── DeviceRegistrationModels.kt
│   │   │   │   │   │   ├── HeartbeatModels.kt
│   │   │   │   │   │   ├── InstallationStatusModels.kt
│   │   │   │   │   │   ├── PaymentApiModels.kt
│   │   │   │   │   │   ├── SoftLockType.kt
│   │   │   │   │   │   ├── TamperModels.kt
│   │   │   │   │   │   └── TechApiModels.kt
│   │   │   │   │   ├── local/
│   │   │   │   │   │   ├── LoanPreferences.kt
│   │   │   │   │   │   ├── RegistrationDataBackup.kt
│   │   │   │   │   │   ├── database/
│   │   │   │   │   │   │   ├── AppDatabase.kt
│   │   │   │   │   │   │   ├── DeviceOwnerDatabase.kt
│   │   │   │   │   │   │   ├── converters/
│   │   │   │   │   │   │   │   └── JsonConverters.kt
│   │   │   │   │   │   │   ├── dao/
│   │   │   │   │   │   │   │   ├── CompleteDeviceRegistrationDao.kt
│   │   │   │   │   │   │   │   ├── DeviceBaselineDao.kt
│   │   │   │   │   │   │   │   ├── DeviceDataDao.kt
│   │   │   │   │   │   │   │   ├── DeviceRegistrationDao.kt
│   │   │   │   │   │   │   │   ├── HeartbeatDao.kt
│   │   │   │   │   │   │   │   ├── HeartbeatHistoryDao.kt
│   │   │   │   │   │   │   │   ├── LockEventDao.kt
│   │   │   │   │   │   │   │   ├── LockStateRecordDao.kt
│   │   │   │   │   │   │   │   ├── OfflineEventDao.kt
│   │   │   │   │   │   │   │   ├── SimChangeHistoryDao.kt
│   │   │   │   │   │   │   │   └── TamperDetectionDao.kt
│   │   │   │   │   │   │   ├── entities/
│   │   │   │   │   │   │   │   ├── CompleteDeviceRegistrationEntity.kt
│   │   │   │   │   │   │   │   ├── DeviceBaselineEntity.kt
│   │   │   │   │   │   │   │   ├── DeviceDataEntity.kt
│   │   │   │   │   │   │   │   ├── DeviceRegistrationEntity.kt
│   │   │   │   │   │   │   │   ├── HeartbeatEntity.kt
│   │   │   │   │   │   │   │   ├── HeartbeatHistoryEntity.kt
│   │   │   │   │   │   │   │   ├── LockEventEntity.kt
│   │   │   │   │   │   │   │   ├── LockStateRecordEntity.kt
│   │   │   │   │   │   │   │   ├── OfflineEvent.kt
│   │   │   │   │   │   │   │   ├── SimChangeHistoryEntity.kt
│   │   │   │   │   │   │   │   ├── SyncStatus.kt
│   │   │   │   │   │   │   │   └── TamperDetectionEntity.kt
│   │   │   │   │   │   │   └── repository/
│   │   │   │   │   │   │       └── LocalDeviceRepository.kt
│   │   │   │   │   ├── remote/
│   │   │   │   │   │   ├── ApiClient.kt
│   │   │   │   │   │   ├── ApiEndpoints.kt
│   │   │   │   │   │   ├── ApiService.kt
│   │   │   │   │   │   ├── api/
│   │   │   │   │   │   │   ├── ApiHeadersInterceptor.kt
│   │   │   │   │   │   │   ├── HtmlResponseInterceptor.kt
│   │   │   │   │   │   │   ├── InstallationStatusService.kt
│   │   │   │   │   │   │   └── ServerReturnedHtmlException.kt
│   │   │   │   │   │   └── models/
│   │   │   │   │   │       └── InstallmentResponse.kt
│   │   │   │   │   └── repository/
│   │   │   │   │       └── DeviceRegistrationRepository.kt
│   │   │   │   ├── deactivation/
│   │   │   │   │   ├── DeactivationHandler.kt
│   │   │   │   │   └── DeviceOwnerDeactivationManager.kt
│   │   │   │   ├── device/
│   │   │   │   │   ├── DeviceOwnerCompatibilityChecker.kt
│   │   │   │   │   └── DeviceOwnerManager.kt
│   │   │   │   ├── kiosk/
│   │   │   │   │   ├── KioskModeManager.kt
│   │   │   │   │   └── PinManager.kt
│   │   │   │   ├── monitoring/
│   │   │   │   │   └── SecurityMonitorService.kt
│   │   │   │   ├── presentation/
│   │   │   │   │   └── activities/
│   │   │   │   │       ├── DeviceRegistrationActivity.kt
│   │   │   │   │   └── RegistrationErrorViewerActivity.kt
│   │   │   │   ├── provisioning/
│   │   │   │   │   └── ProvisioningValidator.kt
│   │   │   │   ├── receivers/
│   │   │   │   │   ├── AdminReceiver.kt
│   │   │   │   │   ├── BootReceiver.kt
│   │   │   │   │   ├── PackageRemovalReceiver.kt
│   │   │   │   │   └── QRProvisioningReceiver.kt
│   │   │   │   ├── registration/
│   │   │   │   │   └── DeviceRegistrationManager.kt
│   │   │   │   ├── security/
│   │   │   │   │   ├── CompleteSilentMode.kt
│   │   │   │   │   ├── DeviceViolationDetector.kt
│   │   │   │   │   ├── SIMChangeDetector.kt
│   │   │   │   │   ├── StealthModeManager.kt
│   │   │   │   │   ├── TamperBootChecker.kt
│   │   │   │   │   ├── enforcement/
│   │   │   │   │   │   ├── AdbBlocker.kt
│   │   │   │   │   │   ├── BootloaderLockEnforcer.kt
│   │   │   │   │   │   ├── CustomRomBlocker.kt
│   │   │   │   │   │   ├── EnhancedSecurityManager.kt
│   │   │   │   │   │   ├── EnhancedSecurityMonitor.kt
│   │   │   │   │   │   ├── KioskModeManager.kt
│   │   │   │   │   │   └── PowerButtonBlocker.kt
│   │   │   │   │   ├── firmware/
│   │   │   │   │   │   └── FirmwareSecurity.kt
│   │   │   │   │   ├── monitoring/
│   │   │   │   │   │   ├── AccessibilityGuard.kt
│   │   │   │   │   │   ├── BootModeDetector.kt
│   │   │   │   │   │   ├── DeviceOwnerRemovalDetector.kt
│   │   │   │   │   │   └── EnhancedTamperDetector.kt
│   │   │   │   │   ├── response/
│   │   │   │   │   │   ├── EnhancedAntiTamperResponse.kt
│   │   │   │   │   │   └── RemoteWipeManager.kt
│   │   │   │   │   └── ssl/
│   │   │   │   │       └── DeviceOwnerSSLManager.kt
│   │   │   │   ├── services/
│   │   │   │   │   ├── DeviceDataCollector.kt
│   │   │   │   │   ├── FirmwareSecurityMonitorService.kt
│   │   │   │   │   ├── HeartbeatService.kt
│   │   │   │   │   ├── HeartbeatWorker.kt
│   │   │   │   │   ├── LocalDataServerService.kt
│   │   │   │   │   ├── PaymentNotificationManager.kt
│   │   │   │   │   ├── RemoteManagementService.kt
│   │   │   │   │   ├── SoftLockMonitorService.kt
│   │   │   │   │   ├── SoftLockOverlayService.kt
│   │   │   │   │   ├── reporting/
│   │   │   │   │   │   └── ServerBugAndLogReporter.kt
│   │   │   │   │   └── sync/
│   │   │   │   │       ├── EnhancedOfflineSyncService.kt
│   │   │   │   │       └── OfflineSyncWorker.kt
│   │   │   │   ├── ui/
│   │   │   │   │   ├── activities/
│   │   │   │   │   │   ├── DeviceDataCollectionActivity.kt
│   │   │   │   │   │   ├── FrpStatusActivity.kt
│   │   │   │   │   │   ├── LockScreenActivity.kt
│   │   │   │   │   │   ├── MainActivity.kt
│   │   │   │   │   │   ├── RegistrationStatusActivity.kt
│   │   │   │   │   │   ├── RegistrationSuccessActivity.kt
│   │   │   │   │   │   ├── SIMChangeOverlayActivity.kt
│   │   │   │   │   │   ├── lock/
│   │   │   │   │   │   │   ├── EnhancedSoftLockActivity.kt
│   │   │   │   │   │   │   └── HardLockActivity.kt
│   │   │   │   │   │   └── provisioning/
│   │   │   │   │   │       ├── CompatibilityFailureActivity.kt
│   │   │   │   │   │       ├── CompatibilitySuccessActivity.kt
│   │   │   │   │   │       ├── DataPrivacyConsentActivity.kt
│   │   │   │   │   │       ├── DeviceCompatibilityCheckActivity.kt
│   │   │   │   │   │       ├── DeviceCompatibilitySuccessActivity.kt
│   │   │   │   │   │       ├── PolicyComplianceActivity.kt
│   │   │   │   │   │       ├── ProvisioningModeActivity.kt
│   │   │   │   │   │       └── ProvisioningProgressActivity.kt
│   │   │   │   │   ├── screens/
│   │   │   │   │   │   ├── LockScreenManager.kt
│   │   │   │   │   │   ├── LockScreens.kt
│   │   │   │   │   │   ├── LockScreenStrategy.kt
│   │   │   │   │   │   └── PinEntryScreen.kt
│   │   │   │   │   └── theme/
│   │   │   │   │       └── Theme.kt
│   │   │   │   ├── update/
│   │   │   │   │   ├── GitHubUpdateManager.kt
│   │   │   │   │   ├── UpdateCheckWorker.kt
│   │   │   │   │   ├── UpdateConfig.kt
│   │   │   │   │   ├── UpdateReceiver.kt
│   │   │   │   │   └── UpdateScheduler.kt
│   │   │   │   ├── utils/
│   │   │   │   │   ├── CustomToast.kt
│   │   │   │   │   ├── SharedPreferencesManager.kt
│   │   │   │   │   ├── SSLCertificateExtractor.kt
│   │   │   │   │   ├── helpers/
│   │   │   │   │   │   ├── LoanNumberValidator.kt
│   │   │   │   │   │   └── NetworkUtils.kt
│   │   │   │   │   └── logging/
│   │   │   │   │       └── LogManager.kt
│   │   │   │   └── work/
│   │   │   │       └── RestrictionEnforcementWorker.kt
│   │   │   ├── res/
│   │   │   │   ├── layout/
│   │   │   │   │   ├── activity_device_compatibility_check.xml
│   │   │   │   │   ├── activity_device_compatibility_success.xml
│   │   │   │   │   ├── activity_policy_compliance.xml
│   │   │   │   │   └── activity_provisioning_progress.xml
│   │   │   │   ├── values/
│   │   │   │   │   ├── colors.xml
│   │   │   │   │   ├── dashboard_strings.xml
│   │   │   │   │   ├── device_owner_config.xml
│   │   │   │   │   ├── lock_overlay_strings.xml
│   │   │   │   │   ├── strings.xml
│   │   │   │   │   └── themes.xml
│   │   │   │   ├── values-night/
│   │   │   │   │   └── themes.xml
│   │   │   │   ├── xml/
│   │   │   │   │   ├── backup_rules.xml
│   │   │   │   │   ├── data_extraction_rules.xml
│   │   │   │   │   ├── device_admin_receiver.xml
│   │   │   │   │   ├── file_paths.xml
│   │   │   │   │   └── network_security_config.xml
│   │   │   │   ├── drawable/
│   │   │   │   │   ├── button_background.xml
│   │   │   │   │   ├── dashboard_button_background.xml
│   │   │   │   │   ├── dashboard_card_background.xml
│   │   │   │   │   ├── device_data_background.xml
│   │   │   │   │   ├── emergency_button_background.xml
│   │   │   │   │   ├── ic_device_error.xml
│   │   │   │   │   ├── ic_device_pending.xml
│   │   │   │   │   ├── ic_device_success.xml
│   │   │   │   │   ├── ic_launcher_background.xml
│   │   │   │   │   ├── ic_launcher_foreground.xml
│   │   │   │   │   ├── ic_lock.xml
│   │   │   │   │   └── (app icon drawables)
│   │   │   │   ├── menu/
│   │   │   │   │   └── json_viewer_menu.xml
│   │   │   │   └── mipmap-anydpi-v26/
│   │   │   │       ├── ic_launcher.xml
│   │   │   │       └── ic_launcher_round.xml
│   │   │   └── AndroidManifest.xml
│   │   └── build.gradle
│   └── build.gradle
├── docs/
│   ├── README.md
│   ├── 01-device-owner-overview.md
│   ├── 02-features-implemented.md
│   ├── 03-compatibility.md
│   ├── 04-device-installation.md
│   ├── 05-device-registration.md
│   ├── 06-apis.md
│   ├── 07-device-heartbeat.md
│   ├── 08-hard-lock-and-soft-lock.md
│   ├── 09-device-tamper.md
│   ├── 10-local-databases.md
│   ├── 11-device-logs-and-bugs.md
│   ├── 12-agent-update.md
│   ├── 13-services.md
│   └── 14-folder-structure.md
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
└── settings.gradle
```

---

## Notes

- **Root**: Shown as `DeviceOwnerAutoUpdate/` to match your reference; the actual repo folder may be `DEVICEOWNER`.
- **Package**: Sources are under `java/com/example/deviceowner/` (Kotlin `.kt`). Your reference used `com/nexivo/deviceowner` and `.java`; this project uses `com.example.deviceowner` and `.kt`.
- **res/**: `layout/`, `values/`, `values-night/`, `xml/`, `drawable/`, `menu/`, `mipmap-*` live under `app/src/main/res/`.
- **Manifest**: `app/src/main/AndroidManifest.xml`.
- **Build**: Root `settings.gradle`; module `app/build.gradle`; optional `app/src/build.gradle` if present in your setup.

For behavior of each area, see docs [01–13](01-device-owner-overview.md).
