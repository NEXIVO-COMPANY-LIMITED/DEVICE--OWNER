package com.example.deviceowner.security.monitoring.sim

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.deviceowner.control.RemoteDeviceControlManager
import com.example.deviceowner.data.local.database.DeviceOwnerDatabase
import com.example.deviceowner.data.local.database.entities.sim.SimChangeHistoryEntity
import com.example.deviceowner.security.response.EnhancedAntiTamperResponse
import kotlinx.coroutines.runBlocking

/**
 * SIM CHANGE DETECTION SYSTEM - Enhanced Working Implementation
 *
 * Multiple detection methods for reliability:
 * 1. Broadcast Receiver (SIM_STATE_CHANGED, SIM_CARD_STATE_CHANGED, PHONE_STATE_CHANGED)
 * 2. Polling backup (every 30 seconds)
 * 3. SubscriptionManager fallback for ICCID when simSerialNumber unavailable
 *
 * Detects: SIM removed, inserted, replaced, dual SIM change, carrier/IMSI change.
 * Shows overlay, reports to tamper API, applies lock per policy.
 */
class SIMChangeDetector(private val context: Context) {

    private val prefs by lazy {
        context.getSharedPreferences("sim_detector_prefs", Context.MODE_PRIVATE)
    }

    private val telephonyManager by lazy {
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    }

    private val subscriptionManager: SubscriptionManager? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
        } else null
    }

    private val handler = Handler(Looper.getMainLooper())
    private var pollingRunnable: Runnable? = null

    companion object {
        private const val TAG = "SIMChangeDetector"
        @Volatile
        private var receiverRegistered = false
        private var staticReceiver: BroadcastReceiver? = null
        private var registrationContext: Context? = null
        private const val PREF_ORIGINAL_SIM_SERIAL = "original_sim_serial"
        private const val PREF_ORIGINAL_SIM_OPERATOR = "original_sim_operator"
        private const val PREF_ORIGINAL_PHONE_NUMBER = "original_phone_number"
        private const val PREF_ORIGINAL_SIM_SERIAL_SLOT2 = "original_sim_serial_slot2"
        private const val PREF_ORIGINAL_IMSI = "original_imsi"
        /** Policy: true = hard lock on SIM change, false = soft lock (default). Set via control_prefs. */
        private const val PREF_SIM_CHANGE_HARD_LOCK = "sim_change_hard_lock"
        private const val POLLING_INTERVAL_MS = 30_000L
    }

    private val simChangeDao by lazy {
        DeviceOwnerDatabase.getDatabase(context.applicationContext).simChangeHistoryDao()
    }

    fun initialize() {
        Log.i(TAG, "Initializing SIM change detection (broadcast + polling)...")
        if (!hasOriginalSIMInfo()) {
            saveOriginalSIMInfo()
        }
        startSIMChangeMonitoring()
        startPolling()
        checkForSIMChange()
        Log.i(TAG, "SIM change detection initialized")
    }

    private fun startSIMChangeMonitoring() {
        synchronized(SIMChangeDetector::class.java) {
            if (receiverRegistered) {
                Log.d(TAG, "SIM receiver already registered (global)")
                return
            }
            try {
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context, intent: Intent) {
                        val action = intent.action
                        if (action == "android.telephony.action.SIM_CARD_STATE_CHANGED" ||
                            action == "android.telephony.action.SIM_APPLICATION_STATE_CHANGED" ||
                            action == "android.intent.action.SIM_STATE_CHANGED" ||
                            action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
                            Log.d(TAG, "SIM/phone state changed - checking...")
                            SIMChangeDetector(ctx.applicationContext).handleSIMStateChangeInternal()
                        }
                    }
                }

                val filter = IntentFilter().apply {
                    addAction("android.telephony.action.SIM_CARD_STATE_CHANGED")
                    addAction("android.telephony.action.SIM_APPLICATION_STATE_CHANGED")
                    addAction("android.intent.action.SIM_STATE_CHANGED")
                    addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
                }

                val appContext = context.applicationContext
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
                } else {
                    appContext.registerReceiver(receiver, filter)
                }
                staticReceiver = receiver
                registrationContext = appContext
                receiverRegistered = true
                Log.d(TAG, "SIM change receiver registered (+ PHONE_STATE_CHANGED)")
            } catch (e: Exception) {
                Log.e(TAG, "Error registering SIM receiver: ${e.message}", e)
            }
        }
    }

    private fun startPolling() {
        if (pollingRunnable != null) return
        pollingRunnable = object : Runnable {
            override fun run() {
                try {
                    checkForSIMChange()
                } catch (e: Exception) {
                    Log.e(TAG, "Polling check error: ${e.message}")
                }
                handler.postDelayed(this, POLLING_INTERVAL_MS)
            }
        }
        handler.postDelayed(pollingRunnable!!, POLLING_INTERVAL_MS)
        Log.d(TAG, "Polling started (${POLLING_INTERVAL_MS / 1000}s interval)")
    }

    private fun stopPolling() {
        pollingRunnable?.let { handler.removeCallbacks(it) }
        pollingRunnable = null
        Log.d(TAG, "Polling stopped")
    }

    private fun handleSIMStateChangeInternal() {
        handleSIMStateChange()
    }

    fun stopMonitoring() {
        stopPolling()
        synchronized(SIMChangeDetector::class.java) {
            if (receiverRegistered && staticReceiver != null && registrationContext != null) {
                try {
                    registrationContext!!.unregisterReceiver(staticReceiver!!)
                } catch (e: Exception) {
                    Log.w(TAG, "Error unregistering SIM receiver: ${e.message}")
                }
            }
            staticReceiver = null
            registrationContext = null
            receiverRegistered = false
            Log.d(TAG, "SIM monitoring stopped")
        }
    }

    fun checkForSIMChange(): SIMChangeResult {
        Log.d(TAG, "Checking for SIM change...")
        val currentSIM = getCurrentSIMInfo()
        val originalSIM = getOriginalSIMInfo()

        val hasChanged = when {
            // Scenario 1: SIM removed (had SIM, now empty)
            normalizeSerial(originalSIM.serialNumber).isNotEmpty() && normalizeSerial(currentSIM.serialNumber).isEmpty() -> {
                Log.w(TAG, "SIM REMOVED: ${originalSIM.serialNumber} -> (empty)")
                true
            }
            // Scenario 2: SIM inserted (was empty, now has SIM)
            normalizeSerial(originalSIM.serialNumber).isEmpty() && normalizeSerial(currentSIM.serialNumber).isNotEmpty() -> {
                Log.w(TAG, "SIM INSERTED: (empty) -> ${currentSIM.serialNumber}")
                true
            }
            // Scenario 3: SIM replaced (different ICCID/serial)
            normalizeSerial(originalSIM.serialNumber).isNotEmpty() && normalizeSerial(currentSIM.serialNumber).isNotEmpty() &&
                normalizeSerial(currentSIM.serialNumber) != normalizeSerial(originalSIM.serialNumber) -> {
                Log.w(TAG, "SIM REPLACED: ${originalSIM.serialNumber} -> ${currentSIM.serialNumber}")
                true
            }
            // Scenario 4: Dual SIM - second slot changed
            getOriginalSerialSlot2() != getCurrentSerialSlot2() && (getOriginalSerialSlot2().isNotEmpty() || getCurrentSerialSlot2().isNotEmpty()) -> {
                Log.w(TAG, "SIM 2 CHANGED: ${getOriginalSerialSlot2()} -> ${getCurrentSerialSlot2()}")
                true
            }
            // Scenario 5: IMSI/carrier changed (same physical SIM, different carrier)
            getOriginalIMSI().isNotEmpty() && getCurrentIMSI().isNotEmpty() && getOriginalIMSI() != getCurrentIMSI() -> {
                Log.w(TAG, "CARRIER/IMSI CHANGED: ${getOriginalIMSI().take(6)}*** -> ${getCurrentIMSI().take(6)}***")
                true
            }
            // Scenario 6: Operator or phone number change
            currentSIM.operatorName != originalSIM.operatorName && originalSIM.operatorName != "UNKNOWN" -> {
                Log.w(TAG, "Operator changed: ${originalSIM.operatorName} -> ${currentSIM.operatorName}")
                true
            }
            currentSIM.phoneNumber.isNotEmpty() && originalSIM.phoneNumber.isNotEmpty() &&
                currentSIM.phoneNumber != originalSIM.phoneNumber -> {
                Log.w(TAG, "Phone number changed")
                true
            }
            else -> {
                Log.d(TAG, "No SIM change detected")
                false
            }
        }

        return if (hasChanged) {
            handleSIMChanged(currentSIM, originalSIM)
        } else {
            SIMChangeResult(
                changed = false,
                currentSIM = currentSIM,
                originalSIM = originalSIM,
                changeCount = getSIMChangeCount()
            )
        }
    }

    private fun normalizeSerial(s: String): String = s.replace("UNKNOWN", "").trim()

    private fun getOriginalSerialSlot2(): String = prefs.getString(PREF_ORIGINAL_SIM_SERIAL_SLOT2, "") ?: ""
    private fun getOriginalIMSI(): String = prefs.getString(PREF_ORIGINAL_IMSI, "") ?: ""

    private fun handleSIMStateChange() {
        Handler(Looper.getMainLooper()).postDelayed({
            val result = checkForSIMChange()
            if (result.changed) {
                Log.e(TAG, "SIM CHANGE DETECTED!")
                showSIMChangeOverlay()
            }
        }, 2000)
    }

    private fun handleSIMChanged(currentSIM: SIMInfo, originalSIM: SIMInfo): SIMChangeResult {
        Log.e(TAG, "SIM CHANGE CONFIRMED!")
        runBlocking {
            simChangeDao.insert(
                SimChangeHistoryEntity(
                    originalPhoneNumber = originalSIM.phoneNumber,
                    newPhoneNumber = currentSIM.phoneNumber,
                    originalOperator = originalSIM.operatorName,
                    newOperator = currentSIM.operatorName,
                    originalSerial = originalSIM.serialNumber,
                    newSerial = currentSIM.serialNumber
                )
            )
        }
        val changeCount = getSIMChangeCount()
        showSIMChangeOverlay()

        // Determine lock type from policy (before applying)
        val hardLock = context.getSharedPreferences("control_prefs", Context.MODE_PRIVATE)
            .getBoolean(PREF_SIM_CHANGE_HARD_LOCK, false)
        val lockType = if (hardLock) "hard" else "soft"

        // Report SIM change to tamper API (always) - with full status for backend
        reportSimChangeToTamperApi(currentSIM, originalSIM, changeCount, lockType)

        // Apply lock based on policy: hard lock or soft lock
        applyLockForSIMChange(currentSIM, originalSIM)

        return SIMChangeResult(
            changed = true,
            currentSIM = currentSIM,
            originalSIM = originalSIM,
            changeCount = changeCount
        )
    }

    /**
     * Sends SIM change event to tamper API with full status (always called on tamper).
     * Backend receives: tamper_type=SIM_CHANGE, lock_applied_on_device, and SIM details.
     * Queued for offline sync if device not registered or network unavailable.
     */
    private fun reportSimChangeToTamperApi(
        currentSIM: SIMInfo,
        originalSIM: SIMInfo,
        changeCount: Int,
        lockType: String = "soft"
    ) {
        val description = "SIM card changed: ${originalSIM.operatorName} -> ${currentSIM.operatorName} (serial/operator/phone)"
        val extraData = mapOf<String, Any?>(
            "original_sim_serial" to originalSIM.serialNumber,
            "new_sim_serial" to currentSIM.serialNumber,
            "original_operator" to originalSIM.operatorName,
            "new_operator" to currentSIM.operatorName,
            "original_phone" to (if (originalSIM.phoneNumber.isNotEmpty()) "***" else ""),
            "new_phone" to (if (currentSIM.phoneNumber.isNotEmpty()) "***" else ""),
            "sim_change_count" to changeCount,
            "lock_applied_on_device" to lockType,
            "lock_type" to lockType,
            "tamper_source" to "sim_change_detector"
        )
        EnhancedAntiTamperResponse(context).sendTamperToBackendOnly(
            tamperType = "SIM_CHANGE",
            severity = "CRITICAL",
            description = description,
            extraData = extraData
        )
        Log.i(TAG, "SIM change tamper sent to API (lock_type=$lockType)")
    }

    /**
     * Applies lock based on policy: hard lock (tamper) or soft lock (reminder).
     */
    private fun applyLockForSIMChange(currentSIM: SIMInfo, originalSIM: SIMInfo) {
        val hardLock = context.getSharedPreferences("control_prefs", Context.MODE_PRIVATE)
            .getBoolean(PREF_SIM_CHANGE_HARD_LOCK, false)
        if (hardLock) {
            applyHardLockForSIMChange()
        } else {
            applySoftLockForSIMChange()
        }
    }

    /** Set policy: true = hard lock on SIM change, false = soft lock. */
    fun setSimChangeHardLockPolicy(hardLock: Boolean) {
        context.getSharedPreferences("control_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean(PREF_SIM_CHANGE_HARD_LOCK, hardLock).apply()
        Log.d(TAG, "SIM change policy set: hard_lock=$hardLock")
    }

    /** Get current policy. */
    fun isSimChangeHardLockPolicy(): Boolean {
        return context.getSharedPreferences("control_prefs", Context.MODE_PRIVATE)
            .getBoolean(PREF_SIM_CHANGE_HARD_LOCK, false)
    }

    private fun showSIMChangeOverlay() {
        Log.d(TAG, "Showing SIM change overlay...")
        try {
            val intent = Intent(context, com.example.deviceowner.ui.activities.overlay.SIMChangeOverlayActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing overlay: ${e.message}", e)
        }
    }

    /**
     * Apply soft lock when SIM change is detected (reminder overlay; user can dismiss with Continue).
     */
    private fun applySoftLockForSIMChange() {
        try {
            val reason = "Unauthorized SIM card detected. This activity has been logged."
            RemoteDeviceControlManager(context).applySoftLock(reason, triggerAction = "sim_change")
            Log.i(TAG, "Soft lock applied for SIM change")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying soft lock for SIM change: ${e.message}", e)
        }
    }

    /**
     * Apply hard lock for SIM change (used when policy = hard lock).
     */
    private fun applyHardLockForSIMChange() {
        Log.w(TAG, "Applying HARD LOCK for SIM change (policy)...")
        try {
            RemoteDeviceControlManager(context).applyHardLock(
                reason = "Unauthorized SIM card detected. This has been reported.",
                forceRestart = false,
                forceFromServerOrMismatch = true,
                tamperType = "SIM_CHANGE"
            )
            Log.i(TAG, "Hard lock applied for SIM change")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying hard lock for SIM change: ${e.message}", e)
        }
    }

    /**
     * Lock device due to SIM change (call from overlay "Lock Device" button or detector).
     */
    fun lockDeviceForSIMChange() {
        Log.w(TAG, "Locking device due to SIM change...")
        try {
            RemoteDeviceControlManager(context).applyHardLock(
                reason = "Unauthorized SIM card detected",
                forceRestart = false,
                forceFromServerOrMismatch = true,
                tamperType = "SIM_CHANGE"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error locking device: ${e.message}", e)
        }
    }

    @SuppressLint("HardwareIds", "MissingPermission")
    fun getCurrentSIMInfo(): SIMInfo {
        return try {
            val serial = getICCID()
            SIMInfo(
                serialNumber = serial.ifEmpty { "UNKNOWN" },
                operatorName = telephonyManager.simOperatorName?.ifBlank { "UNKNOWN" } ?: "UNKNOWN",
                operatorCode = telephonyManager.simOperator ?: "UNKNOWN",
                phoneNumber = telephonyManager.line1Number ?: "",
                country = telephonyManager.simCountryIso?.uppercase() ?: "UNKNOWN",
                timestamp = System.currentTimeMillis()
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied getting SIM info: ${e.message}")
            SIMInfo(
                serialNumber = "PERMISSION_DENIED",
                operatorName = "UNKNOWN",
                operatorCode = "UNKNOWN",
                phoneNumber = "",
                country = "UNKNOWN",
                timestamp = System.currentTimeMillis()
            )
        }
    }

    /** Gets ICCID (SIM serial) - primary via TelephonyManager, fallback via SubscriptionManager. */
    @SuppressLint("HardwareIds", "MissingPermission")
    private fun getICCID(): String {
        return try {
            @Suppress("DEPRECATION")
            val fromTm = telephonyManager.simSerialNumber
            if (!fromTm.isNullOrBlank()) fromTm else getICCIDViaSubscriptionManager()
        } catch (e: SecurityException) {
            getICCIDViaSubscriptionManager()
        } catch (e: Exception) {
            Log.w(TAG, "getICCID error: ${e.message}")
            getICCIDViaSubscriptionManager()
        }
    }

    @SuppressLint("HardwareIds", "MissingPermission")
    private fun getICCIDViaSubscriptionManager(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) return ""
        return try {
            val list = subscriptionManager?.activeSubscriptionInfoList ?: return ""
            list.firstOrNull()?.iccId ?: ""
        } catch (e: Exception) {
            Log.d(TAG, "SubscriptionManager ICCID: ${e.message}")
            ""
        }
    }

    @SuppressLint("HardwareIds", "MissingPermission")
    private fun getCurrentSerialSlot2(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) return ""
        return try {
            val list = subscriptionManager?.activeSubscriptionInfoList ?: return ""
            if (list.size >= 2) list[1].iccId ?: "" else ""
        } catch (e: Exception) {
            ""
        }
    }

    @SuppressLint("HardwareIds", "MissingPermission")
    private fun getCurrentIMSI(): String {
        return try {
            @Suppress("DEPRECATION")
            telephonyManager.subscriberId ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    /** Force immediate SIM check (for debugging). */
    fun forceCheck() {
        Log.i(TAG, "Force checking SIM...")
        checkForSIMChange()
    }

    private fun saveOriginalSIMInfo() {
        val simInfo = getCurrentSIMInfo()
        prefs.edit().apply {
            putString(PREF_ORIGINAL_SIM_SERIAL, simInfo.serialNumber)
            putString(PREF_ORIGINAL_SIM_OPERATOR, simInfo.operatorName)
            putString(PREF_ORIGINAL_PHONE_NUMBER, simInfo.phoneNumber)
            putString(PREF_ORIGINAL_SIM_SERIAL_SLOT2, getCurrentSerialSlot2())
            putString(PREF_ORIGINAL_IMSI, getCurrentIMSI())
            apply()
        }
        Log.i(TAG, "Original SIM saved: ICCID=${simInfo.serialNumber.take(10)}..., slot2=${getCurrentSerialSlot2()?.take(6) ?: "-"}, IMSI=${getCurrentIMSI()?.take(6) ?: "-"}***")
    }

    private fun getOriginalSIMInfo(): SIMInfo {
        return SIMInfo(
            serialNumber = prefs.getString(PREF_ORIGINAL_SIM_SERIAL, "UNKNOWN") ?: "UNKNOWN",
            operatorName = prefs.getString(PREF_ORIGINAL_SIM_OPERATOR, "UNKNOWN") ?: "UNKNOWN",
            operatorCode = "",
            phoneNumber = prefs.getString(PREF_ORIGINAL_PHONE_NUMBER, "") ?: "",
            country = "",
            timestamp = 0
        )
    }

    private fun hasOriginalSIMInfo(): Boolean = prefs.contains(PREF_ORIGINAL_SIM_SERIAL)

    fun getSIMChangeCount(): Int = runBlocking { simChangeDao.getChangeCount() }

    fun getLastSIMChangeTime(): Long = runBlocking { simChangeDao.getLastChangeTime() ?: 0L }

    fun getSIMChangeHistory(): List<SIMInfo> = runBlocking {
        simChangeDao.getRecent(50).map { record ->
            SIMInfo(
                serialNumber = record.newSerial,
                operatorName = record.newOperator,
                operatorCode = "",
                phoneNumber = record.newPhoneNumber,
                country = "",
                timestamp = record.changedAt
            )
        }
    }

    fun getSIMStatus(): SIMStatus {
        val current = getCurrentSIMInfo()
        val original = getOriginalSIMInfo()
        return SIMStatus(
            currentSIM = current,
            originalSIM = original,
            hasChanged = current.serialNumber != original.serialNumber,
            changeCount = getSIMChangeCount(),
            lastChangeTime = getLastSIMChangeTime(),
            history = getSIMChangeHistory()
        )
    }

    fun resetSIMTracking() {
        Log.w(TAG, "Resetting SIM tracking...")
        runBlocking { simChangeDao.deleteAll() }
        prefs.edit().clear().apply()
        saveOriginalSIMInfo()
        Log.d(TAG, "SIM tracking reset")
    }
}

data class SIMInfo(
    val serialNumber: String,
    val operatorName: String,
    val operatorCode: String,
    val phoneNumber: String,
    val country: String,
    val timestamp: Long
)

data class SIMChangeResult(
    val changed: Boolean,
    val currentSIM: SIMInfo,
    val originalSIM: SIMInfo,
    val changeCount: Int
)

data class SIMStatus(
    val currentSIM: SIMInfo,
    val originalSIM: SIMInfo,
    val hasChanged: Boolean,
    val changeCount: Int,
    val lastChangeTime: Long,
    val history: List<SIMInfo>
)
