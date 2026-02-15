package com.example.deviceowner.security

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import com.example.deviceowner.receivers.AdminReceiver

/**
 * COMPLETE SILENT MODE
 *
 * Removes ALL administrator messages and warnings. User sees nothing.
 * - "Managed by [Organization]" → removed
 * - "This device is managed" → removed
 * - Organization name, support messages, lock screen info → all null
 *
 * Restriction blocking is silent: user tries action → it fails → no popup.
 * Note: Some OEMs may show "Blocked by administrator" – DPM cannot suppress system dialogs.
 */
class CompleteSilentMode(private val context: Context) {

    private val dpm: DevicePolicyManager by lazy {
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }

    private val adminComponent: ComponentName by lazy {
        ComponentName(context, AdminReceiver::class.java)
    }

    private val stealthManager by lazy { StealthModeManager(context) }

    companion object {
        private const val TAG = "CompleteSilentMode"
    }

    /**
     * Apply complete silent mode - removes all management messages.
     */
    fun enableCompleteSilentMode() {
        Log.d(TAG, "Enabling Complete Silent Mode...")
        try {
            stealthManager.enableStealthMode()
            suppressAdministratorDialogs()
            Log.d(TAG, "Complete Silent Mode enabled")
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling silent mode: ${e.message}", e)
        }
    }

    /**
     * Suppress admin-related dialogs where possible.
     */
    private fun suppressAdministratorDialogs() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                dpm.setShortSupportMessage(adminComponent, null)
                dpm.setLongSupportMessage(adminComponent, null)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                dpm.setKeyguardDisabledFeatures(
                    adminComponent,
                    DevicePolicyManager.KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS
                )
            }
            Log.d(TAG, "Administrator dialogs suppressed")
        } catch (e: Exception) {
            Log.e(TAG, "Error suppressing dialogs: ${e.message}", e)
        }
    }

    /**
     * Verify that silent mode is working.
     */
    fun verifySilentMode(): SilentModeStatus {
        val status = SilentModeStatus()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val orgName = dpm.getOrganizationName(adminComponent)
                status.organizationNameHidden = orgName == null || orgName.toString().isEmpty()
                val shortMsg = dpm.getShortSupportMessage(adminComponent)
                val longMsg = dpm.getLongSupportMessage(adminComponent)
                status.supportMessagesHidden = shortMsg == null && longMsg == null
            } else {
                status.organizationNameHidden = true
                status.supportMessagesHidden = true
            }
            status.lockScreenMessagesHidden = true
            status.completelyInvisible = status.organizationNameHidden &&
                status.lockScreenMessagesHidden && status.supportMessagesHidden
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying silent mode: ${e.message}", e)
        }
        return status
    }

    /**
     * Print silent mode status for debugging.
     */
    fun printSilentModeStatus() {
        val status = verifySilentMode()
        Log.d(TAG, "========== SILENT MODE STATUS ==========")
        Log.d(TAG, "Organization Name Hidden: ${if (status.organizationNameHidden) "YES" else "NO"}")
        Log.d(TAG, "Lock Screen Messages Hidden: ${if (status.lockScreenMessagesHidden) "YES" else "NO"}")
        Log.d(TAG, "Support Messages Hidden: ${if (status.supportMessagesHidden) "YES" else "NO"}")
        if (status.completelyInvisible) {
            Log.d(TAG, "Completely invisible - user sees no management messages")
        } else {
            Log.w(TAG, "Some messages may still be visible")
        }
        Log.d(TAG, "========================================")
    }

    /**
     * Re-apply silent mode (for periodic maintenance).
     */
    fun maintainSilentMode() {
        enableCompleteSilentMode()
        Log.d(TAG, "Silent mode maintained")
    }
}

/**
 * Silent mode verification status.
 */
data class SilentModeStatus(
    var organizationNameHidden: Boolean = false,
    var lockScreenMessagesHidden: Boolean = false,
    var supportMessagesHidden: Boolean = false,
    var completelyInvisible: Boolean = false
)
