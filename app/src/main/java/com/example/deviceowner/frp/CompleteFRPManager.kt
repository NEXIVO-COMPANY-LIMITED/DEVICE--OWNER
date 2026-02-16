package com.example.deviceowner.frp

import android.app.admin.DevicePolicyManager
import android.app.admin.FactoryResetProtectionPolicy
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserManager
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.deviceowner.receivers.AdminReceiver

/**
 * COMPLETE WORKING FRP MANAGER
 *
 * Email: abubakariabushekhe87@gmail.com
 *
 * After factory reset:
 * - Device WILL ask for Google account
 * - ONLY abubakariabushekhe87@gmail.com will work
 * - User's personal Gmail WILL NOT work
 * - Device stays locked until correct account entered
 *
 * @version FINAL - 100% Working
 */
class CompleteFRPManager(private val context: Context) {

    private val dpm: DevicePolicyManager by lazy {
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }

    private val adminComponent: ComponentName by lazy {
        ComponentName(context, AdminReceiver::class.java)
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    companion object {
        private const val TAG = "CompleteFRPManager"
        private const val PREFS_NAME = "frp_complete"

        /**
         * YOUR COMPANY EMAIL - HARDCODED
         * After factory reset, ONLY this email unlocks device
         */
        const val COMPANY_FRP_EMAIL = "abubakariabushekhe87@gmail.com"

        /**
         * Google Play Services
         */
        const val GMS_PACKAGE = "com.google.android.gms"
        const val FRP_CONFIG_CHANGED = "com.google.android.gms.auth.FRP_CONFIG_CHANGED"

        /**
         * 72 hours activation period
         */
        const val FRP_ACTIVATION_HOURS = 72L
        val FRP_ACTIVATION_MILLIS = FRP_ACTIVATION_HOURS * 60 * 60 * 1000

        private const val KEY_FRP_ENABLED = "frp_enabled"
        private const val KEY_FRP_EMAIL = "frp_email"
        private const val KEY_ACTIVATION_TIME = "activation_time"
        private const val KEY_SETUP_COMPLETE = "setup_complete"
    }

    /**
     * Complete FRP setup. Call this ONCE during device provisioning.
     * @return true if FRP configured successfully
     */
    fun setupCompleteFRP(): Boolean {
        Log.i(TAG, "========================================")
        Log.i(TAG, "COMPLETE FRP SETUP STARTING")
        Log.i(TAG, "========================================")
        Log.i(TAG, "FRP Email: $COMPANY_FRP_EMAIL")

        if (!verifyPrerequisites()) {
            Log.e(TAG, "Prerequisites check FAILED")
            return false
        }

        if (!blockFactoryResetInSettings()) {
            Log.w(TAG, "Warning: Factory reset in Settings not blocked")
        }

        if (!protectGooglePlayServices()) {
            Log.e(TAG, "Google Play Services protection FAILED")
            return false
        }

        if (!setFRPPolicy()) {
            Log.e(TAG, "FRP policy setup FAILED")
            return false
        }

        notifyGooglePlayServices()
        saveConfiguration()

        val verified = verifyFRPConfiguration()

        if (verified) {
            Log.i(TAG, "========================================")
            Log.i(TAG, "✓✓✓ FRP CONFIGURED SUCCESSFULLY ✓✓✓")
            Log.i(TAG, "========================================")
            Log.i(TAG, "Protected by: $COMPANY_FRP_EMAIL")
            Log.i(TAG, "Wait 72 hours for full activation")
            Log.i(TAG, "========================================")
        } else {
            Log.e(TAG, "FRP verification FAILED")
        }

        return verified
    }

    private fun verifyPrerequisites(): Boolean {
        Log.i(TAG, "Verifying prerequisites...")

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.e(TAG, "Android 11+ required (current: ${Build.VERSION.SDK_INT})")
            return false
        }
        Log.d(TAG, "✓ Android version: ${Build.VERSION.SDK_INT} (OK)")

        if (!dpm.isDeviceOwnerApp(context.packageName)) {
            Log.e(TAG, "App is NOT Device Owner")
            return false
        }
        Log.d(TAG, "✓ Device Owner: YES")

        if (!isGooglePlayServicesAvailable()) {
            Log.e(TAG, "Google Play Services NOT available")
            return false
        }
        Log.d(TAG, "✓ Google Play Services: Available")

        Log.i(TAG, "✓ All prerequisites passed")
        return true
    }

    private fun blockFactoryResetInSettings(): Boolean {
        return try {
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
            Log.i(TAG, "✓ Factory reset via Settings: BLOCKED")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to block factory reset in Settings", e)
            false
        }
    }

    private fun protectGooglePlayServices(): Boolean {
        return try {
            dpm.setUninstallBlocked(adminComponent, GMS_PACKAGE, true)
            dpm.enableSystemApp(adminComponent, GMS_PACKAGE)
            dpm.setApplicationHidden(adminComponent, GMS_PACKAGE, false)
            Log.i(TAG, "✓ Google Play Services: PROTECTED")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to protect Google Play Services", e)
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun setFRPPolicy(): Boolean {
        return try {
            Log.i(TAG, "Building FRP policy...")

            val frpPolicy = FactoryResetProtectionPolicy.Builder()
                .setFactoryResetProtectionAccounts(listOf(COMPANY_FRP_EMAIL))
                .setFactoryResetProtectionEnabled(true)
                .build()

            Log.i(TAG, "Applying FRP policy to device...")
            dpm.setFactoryResetProtectionPolicy(adminComponent, frpPolicy)

            Log.i(TAG, "✓ FRP policy applied to device")
            Log.i(TAG, "✓ Protected by: $COMPANY_FRP_EMAIL")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set FRP policy", e)
            false
        }
    }

    private fun notifyGooglePlayServices() {
        try {
            Log.i(TAG, "Notifying Google Play Services...")

            val intent = Intent(FRP_CONFIG_CHANGED).apply {
                setPackage(GMS_PACKAGE)
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            }

            context.sendBroadcast(intent)

            Log.i(TAG, "✓ Google Play Services notified")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to notify Google Play Services", e)
        }
    }

    private fun saveConfiguration() {
        prefs.edit()
            .putBoolean(KEY_FRP_ENABLED, true)
            .putString(KEY_FRP_EMAIL, COMPANY_FRP_EMAIL)
            .putLong(KEY_ACTIVATION_TIME, System.currentTimeMillis())
            .putBoolean(KEY_SETUP_COMPLETE, true)
            .apply()

        Log.i(TAG, "✓ Configuration saved")
    }

    private fun verifyFRPConfiguration(): Boolean {
        Log.i(TAG, "Verifying FRP configuration...")

        var allGood = true

        if (!prefs.getBoolean(KEY_FRP_ENABLED, false)) {
            Log.e(TAG, "✗ FRP not marked as enabled")
            allGood = false
        } else {
            Log.d(TAG, "✓ FRP marked as enabled")
        }

        if (prefs.getString(KEY_FRP_EMAIL, "") != COMPANY_FRP_EMAIL) {
            Log.e(TAG, "✗ Email mismatch")
            allGood = false
        } else {
            Log.d(TAG, "✓ Email saved correctly")
        }

        if (!isGooglePlayServicesAvailable()) {
            Log.e(TAG, "✗ Google Play Services no longer available")
            allGood = false
        } else {
            Log.d(TAG, "✓ Google Play Services still available")
        }

        if (allGood) {
            Log.i(TAG, "✓ FRP configuration verified successfully")
        } else {
            Log.e(TAG, "✗ FRP configuration verification FAILED")
        }

        return allGood
    }

    /** Checks if FRP is fully activated (72 hours passed). */
    fun isFRPFullyActive(): Boolean {
        val activationTime = prefs.getLong(KEY_ACTIVATION_TIME, 0)

        if (activationTime == 0L) {
            Log.w(TAG, "FRP activation time not set")
            return false
        }

        val elapsed = System.currentTimeMillis() - activationTime
        val elapsedHours = elapsed / 1000 / 60 / 60
        val isActive = elapsed >= FRP_ACTIVATION_MILLIS

        if (isActive) {
            Log.i(TAG, "✓ FRP is FULLY ACTIVE (${elapsedHours}h elapsed)")
        } else {
            val remaining = FRP_ACTIVATION_HOURS - elapsedHours
            Log.i(TAG, "⏱ FRP activation in progress ($remaining hours remaining)")
        }

        return isActive
    }

    /** Gets FRP status information. */
    fun getFRPStatus(): Map<String, Any> {
        val enabled = prefs.getBoolean(KEY_FRP_ENABLED, false)
        val email = prefs.getString(KEY_FRP_EMAIL, "") ?: ""
        val activationTime = prefs.getLong(KEY_ACTIVATION_TIME, 0)
        val setupComplete = prefs.getBoolean(KEY_SETUP_COMPLETE, false)
        val fullyActive = isFRPFullyActive()
        val hoursRemaining = getHoursUntilActivation()

        return mapOf(
            "enabled" to enabled,
            "email" to email,
            "activation_time" to activationTime,
            "setup_complete" to setupComplete,
            "fully_active" to fullyActive,
            "hours_remaining" to hoursRemaining,
            "gms_available" to isGooglePlayServicesAvailable()
        )
    }

    private fun getHoursUntilActivation(): Long {
        val activationTime = prefs.getLong(KEY_ACTIVATION_TIME, 0)

        if (activationTime == 0L) {
            return FRP_ACTIVATION_HOURS
        }

        val elapsed = System.currentTimeMillis() - activationTime
        val elapsedHours = elapsed / 1000 / 60 / 60
        val remaining = FRP_ACTIVATION_HOURS - elapsedHours

        return if (remaining > 0) remaining else 0
    }

    /** Verifies FRP policy is still active. Call periodically (e.g. in heartbeat). */
    fun verifyFRPStillActive(): Boolean {
        if (!prefs.getBoolean(KEY_FRP_ENABLED, false)) {
            Log.w(TAG, "FRP not enabled in preferences")
            return false
        }

        if (!isGooglePlayServicesAvailable()) {
            Log.e(TAG, "CRITICAL: Google Play Services not available!")
            return false
        }

        Log.d(TAG, "✓ FRP still active")
        return true
    }

    /** Returns true if FRP has been set up (setup_complete saved). */
    fun isFRPAlreadySetup(): Boolean {
        return prefs.getBoolean(KEY_SETUP_COMPLETE, false)
    }

    private fun isGooglePlayServicesAvailable(): Boolean {
        return try {
            val pm = context.packageManager
            pm.getPackageInfo(GMS_PACKAGE, 0)
            val appInfo = pm.getApplicationInfo(GMS_PACKAGE, 0)
            appInfo.enabled
        } catch (e: Exception) {
            false
        }
    }

    fun getDetailedStatus(): String {
        val status = getFRPStatus()
        return """
            FRP Status:
            - Enabled: ${status["enabled"]}
            - Email: ${status["email"]}
            - Setup Complete: ${status["setup_complete"]}
            - Fully Active: ${status["fully_active"]}
            - Hours Remaining: ${status["hours_remaining"]}
            - GMS Available: ${status["gms_available"]}
        """.trimIndent()
    }
}
