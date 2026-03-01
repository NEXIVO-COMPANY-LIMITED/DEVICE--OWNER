package com.microspace.payo.update.config

/**
 * Configuration for auto-update from GitHub Releases.
 * Device Owner app checks for new APK releases and installs silently.
 */
object UpdateConfig {

    // GitHub Configuration
    const val GITHUB_OWNER = "NEXIVO-COMPANY-LIMITED"
    const val GITHUB_REPO = "DEVICE--OWNER"
    val GITHUB_API_URL: String
        get() = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    // APK file name in GitHub releases
    const val APK_FILE_NAME = "app-release.apk"

    // Update check intervals
    const val UPDATE_CHECK_INTERVAL_SECONDS = 30L // AGGRESSIVE: 30 seconds
    const val INITIAL_DELAY_MINUTES = 1L 

    // WorkManager configuration (Keep as backup)
    const val WORK_NAME = "github_update_check"

    // Notification configuration
    const val NOTIFICATION_CHANNEL_ID = "device_owner_updates"
    const val NOTIFICATION_CHANNEL_NAME = "Device Owner Updates"
    const val NOTIFICATION_ID = 1001

    // Update behavior
    const val AUTO_INSTALL_UPDATES = true
    const val REQUIRE_WIFI = false
    const val REQUIRE_CHARGING = false

    // Version control
    const val ALLOW_DOWNGRADE = false
    const val SKIP_MAJOR_VERSIONS = false

    // Install complete broadcast action
    const val ACTION_INSTALL_COMPLETE = "com.microspace.payo.INSTALL_COMPLETE"
}




