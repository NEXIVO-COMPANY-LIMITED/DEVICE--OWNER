package com.example.deviceowner.core.frp.verification

import android.app.admin.DevicePolicyManager
import android.app.admin.FactoryResetProtectionPolicy
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.deviceowner.config.FrpConfig
import com.example.deviceowner.receivers.AdminReceiver
import java.security.MessageDigest

/**
 * FRP Verification Service - Continuous FRP Integrity Monitoring
 *
 * Verifies FRP protection remains active after 72-hour activation window.
 * Checks:
 * - FRP policy still applied
 * - Google Play Services available
 * - Account ID unchanged
 * - Device owner status intact
 * - Factory reset still blocked
 *
 * @version 1.0.0
 */
class FrpVerificationService(private val context: Context) {

    private val dpm: DevicePolicyManager by lazy {
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }

    private val adminComponent: ComponentName by lazy {
        ComponentName(context, AdminReceiver::class.java)
    }

    private val frpPrefs by lazy {
        context.getSharedPreferences(FrpConfig.PREFS_NAME, Context.MODE_PRIVATE)
    }

    companion object {
        private const val TAG = "FrpVerificationService"
    }

    /**
     * Performs comprehensive FRP verification
     * Returns true if FRP is fully operational
     */
    fun verifyFrpIntegrity(): FrpVerificationResult {
        Log.i(TAG, "Starting FRP integrity verification...")

        val checks = mutableListOf<VerificationCheck>()

        // Check 1: Device Owner Status
        checks.add(checkDeviceOwnerStatus())

        // Check 2: Factory Reset Blocked
        checks.add(checkFactoryResetBlocked())

        // Check 3: Google Play Services
        checks.add(checkGooglePlayServices())

        // Check 4: FRP Policy Applied
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            checks.add(checkFrpPolicyApplied())
        }

        // Check 5: Account ID Integrity
        checks.add(checkAccountIdIntegrity())

        // Check 6: Developer Options Disabled
        checks.add(checkDeveloperOptionsDisabled())

        val allPassed = checks.all { it.passed }
        val result = FrpVerificationResult(
            passed = allPassed,
            checks = checks,
            timestamp = System.currentTimeMillis()
        )

        logVerificationResult(result)
        recordVerificationResult(result)

        return result
    }

    private fun checkDeviceOwnerStatus(): VerificationCheck {
        return try {
            val isOwner = dpm.isDeviceOwnerApp(context.packageName)
            VerificationCheck(
                name = "Device Owner Status",
                passed = isOwner,
                details = if (isOwner) "Device owner active" else "Device owner NOT active - CRITICAL"
            )
        } catch (e: Exception) {
            VerificationCheck(
                name = "Device Owner Status",
                passed = false,
                details = "Error checking: ${e.message}"
            )
        }
    }

    private fun checkFactoryResetBlocked(): VerificationCheck {
        return try {
            val isBlocked = isFactoryResetBlocked()
            VerificationCheck(
                name = "Factory Reset Blocked",
                passed = isBlocked,
                details = if (isBlocked) "Factory reset blocked" else "Factory reset NOT blocked - CRITICAL"
            )
        } catch (e: Exception) {
            VerificationCheck(
                name = "Factory Reset Blocked",
                passed = false,
                details = "Error checking: ${e.message}"
            )
        }
    }

    private fun checkGooglePlayServices(): VerificationCheck {
        return try {
            val isAvailable = isGooglePlayServicesAvailable()
            val isEnabled = isGooglePlayServicesEnabled()
            val isInstalled = isGooglePlayServicesInstalled()

            val passed = isAvailable && isEnabled && isInstalled
            VerificationCheck(
                name = "Google Play Services",
                passed = passed,
                details = "Available: $isAvailable, Enabled: $isEnabled, Installed: $isInstalled"
            )
        } catch (e: Exception) {
            VerificationCheck(
                name = "Google Play Services",
                passed = false,
                details = "Error checking: ${e.message}"
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun checkFrpPolicyApplied(): VerificationCheck {
        return try {
            val policy = dpm.getFactoryResetProtectionPolicy(adminComponent)
            val accounts = policy?.factoryResetProtectionAccounts ?: emptyList()
            val isPolicySet = policy != null && accounts.isNotEmpty()

            VerificationCheck(
                name = "FRP Policy Applied",
                passed = isPolicySet,
                details = if (isPolicySet) {
                    "FRP policy active with ${accounts.size} accounts"
                } else {
                    "FRP policy NOT applied - CRITICAL"
                }
            )
        } catch (e: Exception) {
            VerificationCheck(
                name = "FRP Policy Applied",
                passed = false,
                details = "Error checking: ${e.message}"
            )
        }
    }

    private fun checkAccountIdIntegrity(): VerificationCheck {
        return try {
            val storedAccountId = frpPrefs.getString(FrpConfig.PrefKeys.FRP_ACCOUNT_ID, "") ?: ""
            val isValid = FrpConfig.isValidAccountId(storedAccountId)

            VerificationCheck(
                name = "Account ID Integrity",
                passed = isValid,
                details = if (isValid) "Account ID valid" else "Account ID invalid or missing"
            )
        } catch (e: Exception) {
            VerificationCheck(
                name = "Account ID Integrity",
                passed = false,
                details = "Error checking: ${e.message}"
            )
        }
    }

    private fun checkDeveloperOptionsDisabled(): VerificationCheck {
        return try {
            val devSettingsEnabled = android.provider.Settings.Global.getInt(
                context.contentResolver,
                android.provider.Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                0
            ) == 1

            val adbEnabled = android.provider.Settings.Global.getInt(
                context.contentResolver,
                android.provider.Settings.Global.ADB_ENABLED,
                0
            ) == 1

            val passed = !devSettingsEnabled && !adbEnabled
            VerificationCheck(
                name = "Developer Options Disabled",
                passed = passed,
                details = "Dev Settings: ${if (devSettingsEnabled) "ENABLED" else "disabled"}, ADB: ${if (adbEnabled) "ENABLED" else "disabled"}"
            )
        } catch (e: Exception) {
            VerificationCheck(
                name = "Developer Options Disabled",
                passed = false,
                details = "Error checking: ${e.message}"
            )
        }
    }

    private fun isFactoryResetBlocked(): Boolean {
        return try {
            val restrictions = dpm.getUserRestrictions(adminComponent)
            restrictions.getBoolean(android.os.UserManager.DISALLOW_FACTORY_RESET, false)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking factory reset block", e)
            false
        }
    }

    private fun isGooglePlayServicesAvailable(): Boolean {
        return try {
            val pm = context.packageManager
            pm.getPackageInfo(FrpConfig.GMS_PACKAGE_NAME, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun isGooglePlayServicesEnabled(): Boolean {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(FrpConfig.GMS_PACKAGE_NAME, 0)
            appInfo.enabled
        } catch (e: Exception) {
            false
        }
    }

    private fun isGooglePlayServicesInstalled(): Boolean {
        return try {
            val pm = context.packageManager
            !dpm.isApplicationHidden(adminComponent, FrpConfig.GMS_PACKAGE_NAME)
        } catch (e: Exception) {
            false
        }
    }

    private fun logVerificationResult(result: FrpVerificationResult) {
        Log.i(TAG, "========================================")
        Log.i(TAG, "FRP VERIFICATION RESULT")
        Log.i(TAG, "========================================")
        Log.i(TAG, "Overall Status: ${if (result.passed) "✅ PASSED" else "❌ FAILED"}")
        Log.i(TAG, "Timestamp: ${result.timestamp}")
        Log.i(TAG, "")

        result.checks.forEach { check ->
            val status = if (check.passed) "✅" else "❌"
            Log.i(TAG, "$status ${check.name}")
            Log.i(TAG, "   ${check.details}")
        }

        Log.i(TAG, "========================================")
    }

    private fun recordVerificationResult(result: FrpVerificationResult) {
        frpPrefs.edit()
            .putLong(FrpConfig.PrefKeys.LAST_FRP_VERIFICATION, result.timestamp)
            .apply()
        if (result.passed) {
            frpPrefs.edit()
                .putBoolean(FrpConfig.PrefKeys.FRP_FULLY_ACTIVATED, true)
                .apply()
        }
    }

    /**
     * Checks if verification is due (based on interval)
     */
    fun isVerificationDue(): Boolean {
        val lastVerification = frpPrefs.getLong(FrpConfig.PrefKeys.LAST_FRP_VERIFICATION, 0L)
        val now = System.currentTimeMillis()
        return (now - lastVerification) >= FrpConfig.FRP_VERIFICATION_INTERVAL_MILLIS
    }

    /**
     * Gets hours since last verification
     */
    fun getHoursSinceLastVerification(): Long {
        val lastVerification = frpPrefs.getLong(FrpConfig.PrefKeys.LAST_FRP_VERIFICATION, 0L)
        val now = System.currentTimeMillis()
        val intervalMs = 60L * 60L * 1000L
        return (now - lastVerification) / intervalMs
    }
}

/**
 * Individual verification check result
 */
data class VerificationCheck(
    val name: String,
    val passed: Boolean,
    val details: String
)

/**
 * Overall FRP verification result
 */
data class FrpVerificationResult(
    val passed: Boolean,
    val checks: List<VerificationCheck>,
    val timestamp: Long
) {
    val failedChecks: List<VerificationCheck>
        get() = checks.filter { !it.passed }

    val passedCount: Int
        get() = checks.count { it.passed }

    val totalCount: Int
        get() = checks.size
}
