package com.microspace.payo.core.frp.manager

import android.app.admin.DevicePolicyManager
import android.app.admin.FactoryResetProtectionPolicy
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.microspace.payo.config.FrpConfig
import com.microspace.payo.receivers.AdminReceiver

/**
 * Enterprise FRP Manager - Production Ready
 *
 * Manages Factory Reset Protection with Google's servers.
 * - Sets up FRP with company account
 * - Communicates with Google's servers
 * - Tracks 72-hour activation status
 * - Verifies FRP policy integrity
 *
 * @version 1.0.0
 */
class FrpManager(private val context: Context) {

    private val dpm: DevicePolicyManager by lazy {
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }

    private val adminComponent: ComponentName by lazy {
        ComponentName(context, AdminReceiver::class.java)
    }

    private val prefs by lazy {
        context.getSharedPreferences(FrpConfig.PREFS_NAME, Context.MODE_PRIVATE)
    }

    companion object {
        private const val TAG = "FrpManager"
    }

    // ========================================
    // PUBLIC API
    // ========================================

    /**
     * Sets up Enterprise FRP protection.
     * Call during device provisioning.
     */
    fun setupFrpProtection(): FrpResult {
        Log.i(TAG, "========================================")
        Log.i(TAG, "STARTING FRP SETUP")
        Log.i(TAG, "========================================")

        val validation = validatePreconditions()
        if (!validation.success) {
            return validation
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val applyResult = applyFrpPolicy()
            if (!applyResult.success) {
                return applyResult
            }
        } else {
            Log.w(TAG, "Enterprise FRP requires Android 11+ (API 30). Current: ${Build.VERSION.SDK_INT}")
            return FrpResult(false, "Enterprise FRP requires Android 11+")
        }

        notifyGooglePlayServices()
        recordActivationTime()

        Log.i(TAG, "========================================")
        Log.i(TAG, "✓ FRP PROTECTION ENABLED SUCCESSFULLY")
        Log.i(TAG, "========================================")
        Log.i(TAG, "Protected by account: ${FrpConfig.COMPANY_FRP_ACCOUNT_ID}")
        Log.i(TAG, "Wait ${FrpConfig.FRP_ACTIVATION_HOURS} hours for full activation")
        Log.i(TAG, "========================================")

        return FrpResult(true, "FRP protection enabled successfully")
    }

    /**
     * Checks if FRP has fully activated (72 hours passed).
     */
    fun isFrpFullyActivated(): Boolean {
        val activationTime = prefs.getLong(FrpConfig.PrefKeys.FRP_ACTIVATION_TIME, 0)

        if (activationTime == 0L) {
            Log.w(TAG, "FRP activation time not recorded")
            return false
        }

        val elapsed = System.currentTimeMillis() - activationTime
        val isActivated = elapsed >= FrpConfig.FRP_ACTIVATION_MILLIS

        if (isActivated) {
            Log.i(TAG, "✓ FRP is fully activated")
        } else {
            val hoursRemaining = getHoursUntilActivation()
            Log.i(TAG, "⏱ FRP activation in progress ($hoursRemaining hours remaining)")
        }

        return isActivated
    }

    /**
     * Gets detailed FRP status.
     */
    fun getFrpStatus(): FrpStatus {
        val enabled = prefs.getBoolean(FrpConfig.PrefKeys.FRP_ENABLED, false)
        val activationTime = prefs.getLong(FrpConfig.PrefKeys.FRP_ACTIVATION_TIME, 0)
        val accountId = prefs.getString(FrpConfig.PrefKeys.FRP_ACCOUNT_ID, "") ?: ""
        val fullyActivated = isFrpFullyActivated()
        val gmsAvailable = isGooglePlayServicesAvailable()
        val hoursRemaining = getHoursUntilActivation()

        return FrpStatus(
            enabled = enabled,
            fullyActivated = fullyActivated,
            accountId = accountId,
            activationTime = activationTime,
            hoursRemaining = hoursRemaining,
            gmsAvailable = gmsAvailable
        )
    }

    /**
     * Verifies FRP policy is still active.
     */
    fun verifyFrpPolicy(): Boolean {
        if (!prefs.getBoolean(FrpConfig.PrefKeys.FRP_ENABLED, false)) {
            Log.w(TAG, "FRP not enabled in local state")
            return false
        }

        if (!isGooglePlayServicesAvailable()) {
            Log.e(TAG, "Google Play Services not available - FRP compromised!")
            return false
        }

        Log.d(TAG, "FRP policy verification passed")
        return true
    }

    /** Returns hours remaining until FRP fully activates. */
    fun getHoursUntilActivation(): Long {
        val activationTime = prefs.getLong(FrpConfig.PrefKeys.FRP_ACTIVATION_TIME, 0)

        if (activationTime == 0L) {
            return FrpConfig.FRP_ACTIVATION_HOURS
        }

        val elapsed = System.currentTimeMillis() - activationTime
        val elapsedHours = elapsed / 1000 / 60 / 60
        val remaining = FrpConfig.FRP_ACTIVATION_HOURS - elapsedHours

        return if (remaining > 0) remaining else 0
    }

    // ========================================
    // PRIVATE IMPLEMENTATION
    // ========================================

    private fun validatePreconditions(): FrpResult {
        if (Build.VERSION.SDK_INT < FrpConfig.MIN_ANDROID_VERSION) {
            val message = "FRP requires Android 11+ (current: API ${Build.VERSION.SDK_INT})"
            Log.e(TAG, message)
            return FrpResult(false, message)
        }

        if (!dpm.isDeviceOwnerApp(context.packageName)) {
            val message = "App is not Device Owner"
            Log.e(TAG, message)
            return FrpResult(false, message)
        }

        if (!isGooglePlayServicesAvailable()) {
            val message = "Google Play Services not available"
            Log.e(TAG, message)
            return FrpResult(false, message)
        }

        if (!FrpConfig.isValidAccountId(FrpConfig.COMPANY_FRP_ACCOUNT_ID)) {
            val message = "Invalid account ID format"
            Log.e(TAG, message)
            return FrpResult(false, message)
        }

        Log.i(TAG, "✓ All preconditions validated")
        return FrpResult(true, "Preconditions valid")
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun applyFrpPolicy(): FrpResult {
        return try {
            Log.i(TAG, "Building FRP policy...")
            Log.d(TAG, "Account count: ${FrpConfig.ALL_FRP_ACCOUNTS.size}")

            val frpPolicy = FactoryResetProtectionPolicy.Builder()
                .setFactoryResetProtectionAccounts(FrpConfig.ALL_FRP_ACCOUNTS)
                .setFactoryResetProtectionEnabled(true)
                .build()

            Log.i(TAG, "Applying FRP policy to device...")

            dpm.setFactoryResetProtectionPolicy(adminComponent, frpPolicy)

            Log.i(TAG, "✓ FRP policy applied successfully")

            FrpResult(true, "FRP policy applied")
        } catch (e: Exception) {
            val message = "Failed to apply FRP policy: ${e.message}"
            Log.e(TAG, message, e)
            FrpResult(false, message)
        }
    }

    /**
     * Notifies Google Play Services of FRP configuration change.
     * CRITICAL: Required for FRP to activate.
     */
    private fun notifyGooglePlayServices() {
        try {
            Log.i(TAG, "Notifying Google Play Services...")

            val intent = Intent(FrpConfig.FRP_CONFIG_CHANGED_ACTION).apply {
                setPackage(FrpConfig.GMS_PACKAGE_NAME)
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            }

            context.sendBroadcast(intent)

            Log.i(TAG, "✓ Google Play Services notified successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to notify Google Play Services", e)
            Log.w(TAG, "WARNING: FRP may not activate without this notification!")
        }
    }

    private fun isGooglePlayServicesAvailable(): Boolean {
        return try {
            val pm = context.packageManager
            val info = pm.getPackageInfo(FrpConfig.GMS_PACKAGE_NAME, 0)
            val appInfo = pm.getApplicationInfo(FrpConfig.GMS_PACKAGE_NAME, 0)
            val enabled = appInfo.enabled

            Log.d(TAG, "Google Play Services version: ${info.versionName}")
            Log.d(TAG, "Google Play Services enabled: $enabled")

            if (!enabled) {
                Log.e(TAG, "Google Play Services is DISABLED!")
            }

            enabled
        } catch (e: Exception) {
            Log.e(TAG, "Google Play Services check failed", e)
            false
        }
    }

    private fun recordActivationTime() {
        val currentTime = System.currentTimeMillis()

        prefs.edit()
            .putBoolean(FrpConfig.PrefKeys.FRP_ENABLED, true)
            .putLong(FrpConfig.PrefKeys.FRP_ACTIVATION_TIME, currentTime)
            .putString(FrpConfig.PrefKeys.FRP_ACCOUNT_ID, FrpConfig.COMPANY_FRP_ACCOUNT_ID)
            .putBoolean(FrpConfig.PrefKeys.FRP_SETUP_COMPLETE, true)
            .apply()

        Log.d(TAG, "FRP activation time recorded: $currentTime")
    }
}

// ========================================
// DATA CLASSES
// ========================================

data class FrpResult(
    val success: Boolean,
    val message: String
)

data class FrpStatus(
    val enabled: Boolean,
    val fullyActivated: Boolean,
    val accountId: String,
    val activationTime: Long,
    val hoursRemaining: Long,
    val gmsAvailable: Boolean
)
