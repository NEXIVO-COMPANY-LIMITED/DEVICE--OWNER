package com.example.deviceowner.security.monitoring.sim

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.deviceowner.services.reporting.ServerBugAndLogReporter
import com.example.deviceowner.ui.activities.overlay.SIMChangeOverlayActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * SIMInfo - Basic SIM identifier data.
 */
data class SIMInfo(val operatorName: String, val identifier: String)

/**
 * SIMStatus - Comparison between original and current SIM.
 */
data class SIMStatus(val originalSIM: SIMInfo, val currentSIM: SIMInfo)

/**
 * SIMChangeDetector - PERFECTED v8.1
 * 
 * ✅ Uses EncryptedSharedPreferences for secure IMSI storage.
 * ✅ Supports Dual SIM detection and UI status reporting.
 * ✅ Triggers a clear Soft Lock screen on mismatch.
 */
class SIMChangeDetector(private val context: Context) {

    companion object {
        private const val TAG = "SIMChangeDetector"
        private const val PREF_NAME = "sim_secure_prefs"
        private const val KEY_ORIGINAL_SIM_DATA = "original_sim_data"
    }

    private val securePrefs by lazy {
        try {
            val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            EncryptedSharedPreferences.create(
                context, PREF_NAME, masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create secure prefs: ${e.message}")
            null
        }
    }

    /**
     * Saves the current SIM card's identifiers securely.
     */
    fun initialize() {
        CoroutineScope(Dispatchers.IO).launch {
            val currentSims = getCurrentSimIdentifiers()
            if (currentSims.isNotEmpty()) {
                val existingSims = getOriginalSimIdentifiers()
                if (existingSims.isEmpty()) { 
                    saveOriginalSimIdentifiers(currentSims)
                    Log.i(TAG, "✅ Original SIM card data saved securely")
                }
            }
        }
    }

    /**
     * Checks for a mismatch and triggers the overlay if necessary.
     */
    fun checkForSIMChange() {
        CoroutineScope(Dispatchers.IO).launch {
            val originalSims = getOriginalSimIdentifiers()
            if (originalSims.isEmpty()) return@launch

            val currentSims = getCurrentSimIdentifiers()
            if (currentSims.isEmpty()) return@launch

            val isMismatch = !originalSims.any { it in currentSims }

            if (isMismatch) {
                Log.e(TAG, "🚨 SIM MISMATCH! Triggering Overlay.")
                
                ServerBugAndLogReporter.postBug(
                    title = "Security Alert: Unauthorized SIM Change",
                    message = "An unauthorized SIM card was detected.",
                    priority = "high",
                    extraData = mapOf("original" to originalSims, "current" to currentSims)
                )

                val intent = Intent(context, SIMChangeOverlayActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                }
                context.startActivity(intent)
            }
        }
    }

    /**
     * Provides status for the UI Overlay.
     */
    fun getSIMStatus(): SIMStatus {
        val original = getOriginalSimIdentifiers().firstOrNull() ?: "Unknown"
        val current = getCurrentSimIdentifiers().firstOrNull() ?: "No SIM"
        
        return SIMStatus(
            originalSIM = SIMInfo(
                operatorName = getOperatorName(original),
                identifier = original
            ),
            currentSIM = SIMInfo(
                operatorName = getOperatorName(current),
                identifier = current
            )
        )
    }

    private fun getOperatorName(id: String): String {
        // In a real scenario, you'd look up the operator name from the ID.
        // For now, we return a clean version of the identifier.
        return id.substringAfter(":")
    }

    @SuppressLint("MissingPermission")
    private fun getCurrentSimIdentifiers(): Set<String> {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return emptySet()
        }

        val identifiers = mutableSetOf<String>()
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            subManager.activeSubscriptionInfoList?.forEach { info ->
                try {
                    val imsi = telephonyManager.createForSubscriptionId(info.subscriptionId).subscriberId
                    if (!imsi.isNullOrBlank()) identifiers.add("IMSI:$imsi")
                    else if (!info.iccId.isNullOrBlank()) identifiers.add("ICCID:${info.iccId}")
                } catch (e: Exception) {
                    if (!info.iccId.isNullOrBlank()) identifiers.add("ICCID:${info.iccId}")
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val imsi = telephonyManager.subscriberId
            if (!imsi.isNullOrBlank()) identifiers.add("IMSI:$imsi")
        }
        return identifiers
    }

    private fun saveOriginalSimIdentifiers(identifiers: Set<String>) {
        securePrefs?.edit()?.putStringSet(KEY_ORIGINAL_SIM_DATA, identifiers)?.apply()
    }

    private fun getOriginalSimIdentifiers(): Set<String> {
        return securePrefs?.getStringSet(KEY_ORIGINAL_SIM_DATA, emptySet()) ?: emptySet()
    }
}
