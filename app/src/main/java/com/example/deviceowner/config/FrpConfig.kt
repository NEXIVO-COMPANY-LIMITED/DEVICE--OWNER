package com.example.deviceowner.config

/**
 * Enterprise FRP Configuration - Production Ready
 *
 * Factory Reset Protection with Google's servers.
 * Company account unlocks ALL devices after factory reset.
 *
 * @version 1.0.0
 */
object FrpConfig {

    // ========================================
    // COMPANY ACCOUNT (HARDCODED - READY TO USE)
    // ========================================

    /**
     * Company's Google Account obfuscatedGaiaId.
     * This account unlocks ALL devices after factory reset.
     *
     * SECURITY CRITICAL:
     * - Enable 2FA on this account
     * - Use strong unique password (16+ characters)
     * - Save recovery codes securely
     * - Configure recovery email and phone
     * - Limit access to 2-3 trusted people
     */
    const val COMPANY_FRP_ACCOUNT_ID = "114903008829199638895"

    /**
     * Optional backup accounts for redundancy.
     */
    val BACKUP_FRP_ACCOUNTS = listOf<String>(
        // "123456789012345678901",  // Backup account 1
        // "987654321098765432109"   // Backup account 2
    )

    /**
     * All FRP accounts (primary + backups).
     */
    val ALL_FRP_ACCOUNTS: List<String>
        get() = listOf(COMPANY_FRP_ACCOUNT_ID) + BACKUP_FRP_ACCOUNTS

    // ========================================
    // GOOGLE PLAY SERVICES
    // ========================================

    /** Google Play Services package name */
    const val GMS_PACKAGE_NAME = "com.google.android.gms"

    /** FRP configuration changed broadcast - required for FRP activation */
    const val FRP_CONFIG_CHANGED_ACTION = "com.google.android.gms.auth.FRP_CONFIG_CHANGED"

    // ========================================
    // TIMING CONSTANTS
    // ========================================

    /** FRP activation wait time (Google requirement) */
    const val FRP_ACTIVATION_HOURS = 72L
    const val FRP_ACTIVATION_MILLIS = FRP_ACTIVATION_HOURS * 60 * 60 * 1000

    /** Security scan interval */
    const val SECURITY_SCAN_INTERVAL_MINUTES = 5L
    const val SECURITY_SCAN_INTERVAL_MILLIS = SECURITY_SCAN_INTERVAL_MINUTES * 60 * 1000

    // ========================================
    // VALIDATION
    // ========================================

    /** Expected account ID length (obfuscated Gaia ID) */
    const val ACCOUNT_ID_LENGTH = 21

    /** Minimum Android version for Enterprise FRP (Android 11) */
    const val MIN_ANDROID_VERSION = 30

    // ========================================
    // LOGGING
    // ========================================

    const val LOG_TAG_PREFIX = "FRP"

    object LogTags {
        const val FRP = "${LOG_TAG_PREFIX}_Manager"
        const val POLICY = "${LOG_TAG_PREFIX}_Policy"
        const val SECURITY = "${LOG_TAG_PREFIX}_Security"
        const val NETWORK = "${LOG_TAG_PREFIX}_Network"
        const val UI = "${LOG_TAG_PREFIX}_UI"
        const val SERVICE = "${LOG_TAG_PREFIX}_Service"
    }

    // ========================================
    // STORAGE
    // ========================================

    const val PREFS_NAME = "frp_preferences"

    object PrefKeys {
        const val FRP_ENABLED = "frp_enabled"
        const val FRP_ACTIVATION_TIME = "frp_activation_time"
        const val FRP_ACCOUNT_ID = "frp_account_id"
        const val FRP_SETUP_COMPLETE = "frp_setup_complete"
    }

    // ========================================
    // ROOT DETECTION (for ThreatDetector)
    // ========================================

    val ROOT_DETECTION_PATHS = listOf(
        "/system/app/Superuser.apk",
        "/sbin/su",
        "/system/bin/su",
        "/system/xbin/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/su",
        "/su/bin/su"
    )

    val ROOT_MANAGEMENT_APPS = listOf(
        "com.noshufou.android.su",
        "com.noshufou.android.su.elite",
        "eu.chainfire.supersu",
        "com.koushikdutta.superuser",
        "com.thirdparty.superuser",
        "com.yellowes.su",
        "com.topjohnwu.magisk"
    )

    // ========================================
    // VALIDATION FUNCTIONS
    // ========================================

    /** Validates account ID format (21 digits) */
    fun isValidAccountId(accountId: String): Boolean {
        return accountId.length == ACCOUNT_ID_LENGTH && accountId.all { it.isDigit() }
    }

    /** Validates Android version supports Enterprise FRP */
    fun isAndroidVersionSupported(sdkVersion: Int): Boolean {
        return sdkVersion >= MIN_ANDROID_VERSION
    }
}
