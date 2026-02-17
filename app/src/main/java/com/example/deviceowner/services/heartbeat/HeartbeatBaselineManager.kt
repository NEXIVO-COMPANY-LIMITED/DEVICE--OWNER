package com.example.deviceowner.services.heartbeat

import android.content.Context
import android.util.Log
import com.example.deviceowner.data.models.heartbeat.HeartbeatRequest
import com.example.deviceowner.data.models.registration.DeviceRegistrationRequest
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONObject

/**
 * Manages the local registration baseline for heartbeat comparison (same role as Django's
 * _get_registration_baseline). When device is offline we compare current heartbeat data
 * against this baseline and apply lock if mismatches detected.
 *
 * Baseline is saved:
 * 1. When registration succeeds (from registration payload)
 * 2. Optionally when first successful heartbeat is accepted by server (from request we sent)
 */
object HeartbeatBaselineManager {

    private const val TAG = "HeartbeatBaseline"
    private const val PREFS_NAME = "heartbeat_baseline"
    private const val KEY_BASELINE_JSON = "baseline_json"
    private const val KEY_BASELINE_SAVED_AT = "baseline_saved_at"
    private const val KEY_LAST_OFFLINE_COMPARISON = "last_offline_comparison"
    private const val KEY_LAST_OFFLINE_AT = "last_offline_heartbeat_at"
    private const val KEY_LOCAL_HISTORY = "local_heartbeat_history"
    private const val MAX_LOCAL_HISTORY = 100

    private val gson = Gson()
    private val mapType = object : TypeToken<Map<String, Any?>>() {}.type

    /** Same fields Django tracks for comparison (devices/views.py HEARTBEAT_TRACKED_FIELDS). */
    val TRACKED_FIELDS = listOf(
        "device_imeis",
        "serial_number",
        "installed_ram",
        "total_storage",
        "is_device_rooted",
        "is_usb_debugging_enabled",
        "is_developer_mode_enabled",
        "is_bootloader_unlocked",
        "is_custom_rom"
    )

    /**
     * Save baseline from registration success. Builds same normalized map Django uses.
     */
    fun saveBaselineFromRegistration(context: Context, request: DeviceRegistrationRequest) {
        val baseline = buildBaselineFromRegistrationRequest(request) ?: return
        saveBaseline(context, baseline)
        Log.i(TAG, "✅ Baseline saved from registration (${baseline.keys.size} fields)")
    }

    /**
     * Save baseline from a heartbeat request (e.g. after first successful server response).
     */
    fun saveBaselineFromHeartbeat(context: Context, request: HeartbeatRequest) {
        val baseline = buildBaselineFromHeartbeatRequest(request)
        saveBaseline(context, baseline)
        Log.d(TAG, "Baseline updated from heartbeat (${baseline.keys.size} fields)")
    }

    fun saveBaseline(context: Context, baseline: Map<String, Any?>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_BASELINE_JSON, gson.toJson(baseline))
            .putLong(KEY_BASELINE_SAVED_AT, System.currentTimeMillis())
            .apply()
    }

    fun getBaseline(context: Context): Map<String, Any?>? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_BASELINE_JSON, null) ?: return null
        return try {
            @Suppress("UNCHECKED_CAST")
            gson.fromJson<Map<String, Any?>>(json, mapType)?.filter { it.value != null }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse baseline: ${e.message}")
            null
        }
    }

    fun hasBaseline(context: Context): Boolean {
        val baseline = getBaseline(context) ?: return false
        return baseline.values.any { v -> v != null && v != "" && v != (emptyList<String>()) }
    }

    fun getBaselineSavedAt(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_BASELINE_SAVED_AT, 0L)
    }

    fun saveLastOfflineComparison(context: Context, comparisonJson: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_LAST_OFFLINE_COMPARISON, comparisonJson)
            .putLong(KEY_LAST_OFFLINE_AT, System.currentTimeMillis())
            .apply()
    }

    fun getLastOfflineComparison(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_OFFLINE_COMPARISON, null)

    /**
     * Append a local heartbeat record (offline) for history. Keeps last MAX_LOCAL_HISTORY.
     */
    fun appendLocalHeartbeatRecord(context: Context, recordJson: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_LOCAL_HISTORY, "[]") ?: "[]"
        val list = try {
            gson.fromJson<List<String>>(existing, object : TypeToken<List<String>>() {}.type).toMutableList()
        } catch (_: Exception) {
            mutableListOf<String>()
        }
        list.add(0, recordJson)
        while (list.size > MAX_LOCAL_HISTORY) list.removeAt(list.size - 1)
        prefs.edit().putString(KEY_LOCAL_HISTORY, gson.toJson(list)).apply()
    }

    private fun buildBaselineFromRegistrationRequest(request: DeviceRegistrationRequest): Map<String, Any?>? {
        val deviceInfo = request.deviceInfo ?: return null
        val imeiInfo = request.imeiInfo
        val storageInfo = request.storageInfo
        val securityInfo = request.securityInfo ?: return null

        val deviceImeis = when (val v = imeiInfo?.get("device_imeis")) {
            is List<*> -> v.mapNotNull { it?.toString() }
            is String -> listOf(v)
            else -> listOf("NO_IMEI_FOUND")
        }
        val serial = (deviceInfo["serial"] ?: deviceInfo["serial_number"])?.toString()
        val ram = (storageInfo?.get("installed_ram") ?: deviceInfo["installed_ram"])?.toString()?.replace(" ", "")
        val storage = (storageInfo?.get("total_storage") ?: deviceInfo["total_storage"])?.toString()?.replace(" ", "")

        return mapOf(
            "device_imeis" to deviceImeis.ifEmpty { listOf("NO_IMEI_FOUND") },
            "serial_number" to serial,
            "installed_ram" to ram,
            "total_storage" to storage,
            "is_device_rooted" to (securityInfo["is_device_rooted"] == true),
            "is_usb_debugging_enabled" to (securityInfo["is_usb_debugging_enabled"] == true),
            "is_developer_mode_enabled" to (securityInfo["is_developer_mode_enabled"] == true),
            "is_bootloader_unlocked" to (securityInfo["is_bootloader_unlocked"] == true),
            "is_custom_rom" to (securityInfo["is_custom_rom"] == true)
        ).filterValues { it != null }
    }

    /** Build same normalized map from heartbeat request for comparison (used by OfflineHeartbeatComparator). */
    fun buildBaselineFromHeartbeatRequest(request: HeartbeatRequest): Map<String, Any?> {
        val imeis = request.deviceImeis?.ifEmpty { null } ?: listOf("NO_IMEI_FOUND")
        val ram = request.installedRam?.replace(" ", "")
        val storage = request.totalStorage?.replace(" ", "")
        return mapOf(
            "device_imeis" to imeis,
            "serial_number" to (request.serialNumber ?: request.serial),
            "installed_ram" to ram,
            "total_storage" to storage,
            "is_device_rooted" to request.isDeviceRooted,
            "is_usb_debugging_enabled" to request.isUsbDebuggingEnabled,
            "is_developer_mode_enabled" to request.isDeveloperModeEnabled,
            "is_bootloader_unlocked" to request.isBootloaderUnlocked,
            "is_custom_rom" to request.isCustomRom
        ).filterValues { it != null }
    }
}
