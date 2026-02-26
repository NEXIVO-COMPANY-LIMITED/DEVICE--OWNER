package com.microspace.payo.config

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
     * SECURITY CRITICAL - BEST PRACTICES:
     * ✅ Enable 2FA (Two-Factor Authentication)
     * ✅ Use strong unique password (16+ characters, mixed case, numbers, symbols)
     * ✅ Save recovery codes in secure location (encrypted, offline)
     * ✅ Configure recovery email and phone number
     * ✅ Limit access to 2-3 trusted people only
     * ✅ Enable login alerts and suspicious activity notifications
     * ✅ Review account access logs monthly
     * ✅ Rotate password quarterly
     * ✅ Use hardware security key if available
     * ✅ Never share account credentials via email/chat
     * 
     * ACCOUNT ID: 114903008829199638895
     * EMAIL: abubakariabushekhe87@gmail.com
     * STATUS: ✅ ACTIVE AND CONFIGURED
     * PROTECTION: ✅ ENTERPRISE-GRADE
     */
    const val COMPANY_FRP_ACCOUNT_ID = "114903008829199638895"

    /**
     * Backup accounts for redundancy and failover protection.
     * CRITICAL: If primary account is compromised, backup accounts unlock devices.
     * 
     * RECOMMENDED SETUP:
     * 1. Create 2-3 additional Google accounts for your company
     * 2. Get their obfuscated Gaia IDs
     * 3. Add them below
     * 4. Enable 2FA on all backup accounts
     * 5. Store recovery codes securely
     * 
     * FAILOVER LOGIC:
     * - After factory reset, device asks for Google account
     * - Device accepts ANY of these accounts (primary + backups)
     * - If primary account compromised, backups still work
     * - Rotate accounts quarterly for security
     */
    val BACKUP_FRP_ACCOUNTS = listOf<String>(
        // Add your backup accounts here (21-digit Gaia IDs)
        // Example: "123456789012345678901",  // Backup account 1
        // Example: "987654321098765432109"   // Backup account 2
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

    /** FRP verification interval (after activation) - check every 24 hours */
    const val FRP_VERIFICATION_INTERVAL_HOURS = 24L
    const val FRP_VERIFICATION_INTERVAL_MILLIS = FRP_VERIFICATION_INTERVAL_HOURS * 60 * 60 * 1000

    /** GMS availability check interval */
    const val GMS_CHECK_INTERVAL_MINUTES = 30L
    const val GMS_CHECK_INTERVAL_MILLIS = GMS_CHECK_INTERVAL_MINUTES * 60 * 1000

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
        const val FRP_FULLY_ACTIVATED = "frp_fully_activated"
        const val LAST_FRP_VERIFICATION = "last_frp_verification"
        const val LAST_GMS_CHECK = "last_gms_check"
        const val GMS_AVAILABLE = "gms_available"
        const val FRP_POLICY_HASH = "frp_policy_hash"
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

    /** Validates account ID format (21 digits - Gaia ID) */
    fun isValidAccountId(accountId: String): Boolean {
        return accountId.length == ACCOUNT_ID_LENGTH && accountId.all { it.isDigit() }
    }

    /** Validates Android version supports Enterprise FRP */
    fun isAndroidVersionSupported(sdkVersion: Int): Boolean {
        return sdkVersion >= MIN_ANDROID_VERSION
    }

    /** Validates all FRP accounts are properly configured */
    fun validateFrpAccounts(): Boolean {
        // Primary account must be valid
        if (!isValidAccountId(COMPANY_FRP_ACCOUNT_ID)) {
            return false
        }

        // All backup accounts must be valid (if any)
        return BACKUP_FRP_ACCOUNTS.all { isValidAccountId(it) }
    }

    /** Gets total number of FRP accounts (primary + backups) */
    fun getTotalFrpAccountCount(): Int {
        return ALL_FRP_ACCOUNTS.size
    }

    /** Checks if account is a valid FRP account */
    fun isFrpAccount(accountId: String): Boolean {
        return ALL_FRP_ACCOUNTS.contains(accountId)
    }

    /** Gets primary account ID */
    fun getPrimaryAccountId(): String {
        return COMPANY_FRP_ACCOUNT_ID
    }

    /** Gets backup account IDs */
    fun getBackupAccountIds(): List<String> {
        return BACKUP_FRP_ACCOUNTS
    }

    /** Gets account description for logging (obfuscated) */
    fun getAccountDescription(accountId: String): String {
        return when {
            accountId == COMPANY_FRP_ACCOUNT_ID -> "Primary Account"
            BACKUP_FRP_ACCOUNTS.contains(accountId) -> "Backup Account #${BACKUP_FRP_ACCOUNTS.indexOf(accountId) + 1}"
            else -> "Unknown Account"
        }
    }
}
