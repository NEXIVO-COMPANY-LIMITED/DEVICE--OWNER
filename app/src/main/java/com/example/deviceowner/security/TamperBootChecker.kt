package com.example.deviceowner.security

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.example.deviceowner.control.RemoteDeviceControlManager
import com.example.deviceowner.security.enforcement.BootloaderLockEnforcer
import com.example.deviceowner.security.response.EnhancedAntiTamperResponse

/**
 * Runs tamper/security check on boot (and optionally elsewhere).
 * Device ikiwa inawaka: check kama tamper security iko poa. Kama iko tatizo → block totally with hard lock.
 * Sends tamper event to server automatically (same as other tamper sources).
 * Used by BootReceiver FIRST so we only continue with "mambo mengine" when security is OK.
 */
object TamperBootChecker {

    private const val TAG = "TamperBootChecker"

    /** Delay before first tamper check on boot (ms) - allows device to settle. */
    private const val BOOT_TAMPER_DELAY_MS = 2000L

    /**
     * Check tamper/security. If any violation (developer options, USB debugging, bootloader unlocked):
     * apply hard lock immediately and return true. Otherwise return false (security iko poa).
     * Waits 2 seconds first so device is ready, then detects and blocks hapo hapo.
     */
    fun runTamperCheck(context: Context): Boolean {
        try {
            Log.d(TAG, "Tamper check: waiting ${BOOT_TAMPER_DELAY_MS}ms for device to settle...")
            Thread.sleep(BOOT_TAMPER_DELAY_MS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            Log.w(TAG, "Tamper check delay interrupted")
        }
        val controlManager = RemoteDeviceControlManager(context)
        val reasons = mutableListOf<String>()
        var tamperType: String? = null

        try {
            // 1. Developer options enabled
            val devOptEnabled = isDeveloperOptionsEnabled(context)
            if (devOptEnabled) {
                reasons.add("Developer options enabled")
                if (tamperType == null) tamperType = "DEVELOPER_MODE"
            }

            // 2. USB debugging enabled
            val usbEnabled = isUsbDebuggingEnabled(context)
            if (usbEnabled) {
                reasons.add("USB debugging enabled")
                if (tamperType == null) tamperType = "USB_DEBUG"
            }

            // 3. Bootloader unlocked
            try {
                val enforcer = BootloaderLockEnforcer(context)
                if (enforcer.isBootloaderUnlocked()) {
                    reasons.add("Bootloader unlocked")
                    tamperType = "BOOTLOADER_UNLOCKED"
                }
            } catch (e: Exception) {
                Log.w(TAG, "Bootloader check failed: ${e.message}")
            }

            if (reasons.isEmpty()) {
                Log.d(TAG, "✅ Tamper check: security iko poa – inaendelea na mambo mengine")
                return false
            }

            val reason = "Security violation: ${reasons.joinToString("; ")}. Device locked."
            Log.e(TAG, "🚨 TAMPER DETECTED ON BOOT: ${reasons.joinToString(", ")} – inablock totally (hard lock)")
            controlManager.applyHardLock(
                reason = reason,
                forceRestart = false,
                forceFromServerOrMismatch = true,
                tamperType = tamperType
            )
            // Send tamper event to server (same as other tamper sources – logs, offline queue if no network)
            val tamperTypeStr = tamperType ?: "DEVELOPER_MODE"
            EnhancedAntiTamperResponse(context).sendTamperToBackendOnly(
                tamperType = tamperTypeStr,
                severity = "CRITICAL",
                description = reason,
                extraData = mapOf(
                    "lock_applied_on_device" to "hard",
                    "lock_type" to "hard",
                    "tamper_source" to "tamper_boot_checker"
                )
            )
            Log.i(TAG, "Tamper event sent to server (boot): $tamperTypeStr")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Tamper check error: ${e.message}", e)
            return false
        }
    }

    private fun isDeveloperOptionsEnabled(context: Context): Boolean {
        return try {
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                0
            ) == 1
        } catch (e: Exception) {
            false
        }
    }

    private fun isUsbDebuggingEnabled(context: Context): Boolean {
        return try {
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.ADB_ENABLED,
                0
            ) == 1
        } catch (e: Exception) {
            false
        }
    }
}
