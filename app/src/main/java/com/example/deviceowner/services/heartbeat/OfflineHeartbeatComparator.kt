package com.example.deviceowner.services.heartbeat

import com.example.deviceowner.data.models.heartbeat.HeartbeatRequest

/**
 * Compares current heartbeat data against registration baseline using the same logic as Django
 * (_compare_heartbeat_with_registration). Used when device is offline so we can still detect
 * tamper/mismatch and apply lock locally.
 *
 * Fields and severity match devices/views.py:
 * HIGH: serial_number, device_imeis, is_device_rooted, is_usb_debugging_enabled,
 *       is_developer_mode_enabled, is_bootloader_unlocked
 * MEDIUM: installed_ram, total_storage, is_custom_rom
 */
object OfflineHeartbeatComparator {

    private const val TAG = "OfflineHeartbeatCompare"

    private val HIGH_SEVERITY_FIELDS = setOf(
        "serial_number",
        "device_imeis",
        "is_device_rooted",
        "is_usb_debugging_enabled",
        "is_developer_mode_enabled",
        "is_bootloader_unlocked"
    )

    private val MISMATCH_REASONS = mapOf(
        "serial_number" to "Device serial number mismatch detected",
        "device_imeis" to "Device IMEI mismatch detected",
        "is_device_rooted" to "Device rooting status changed",
        "is_usb_debugging_enabled" to "USB debugging status changed",
        "is_developer_mode_enabled" to "Developer mode status changed",
        "is_bootloader_unlocked" to "Bootloader unlock status changed",
        "is_custom_rom" to "Custom ROM status changed",
        "installed_ram" to "Device RAM configuration changed",
        "total_storage" to "Device storage configuration changed"
    )

    data class ComparisonResult(
        val mismatches: List<MismatchEntry>,
        val highSeverityCount: Int,
        val mediumSeverityCount: Int,
        val totalMismatches: Int,
        val shouldAutoLock: Boolean,
        val lockReason: String?,
        val comparisonDetails: Map<String, Any>,
        val baselineStatus: String
    )

    data class MismatchEntry(
        val field: String,
        val severity: String,
        val reason: String
    )

    /**
     * Compare current heartbeat request against baseline. Returns same shape as Django comparison.
     */
    fun compare(
        baseline: Map<String, Any?>,
        request: HeartbeatRequest
    ): ComparisonResult {
        if (baseline.isEmpty()) {
            return ComparisonResult(
                mismatches = emptyList(),
                highSeverityCount = 0,
                mediumSeverityCount = 0,
                totalMismatches = 0,
                shouldAutoLock = false,
                lockReason = null,
                comparisonDetails = emptyMap(),
                baselineStatus = "empty_baseline"
            )
        }

        val current = HeartbeatBaselineManager.buildBaselineFromHeartbeatRequest(request)
        val baselineFiltered = baseline.filterKeys { HeartbeatBaselineManager.TRACKED_FIELDS.contains(it) }
        val hasBaselineData = baselineFiltered.values.any { v -> v != null && v != "" && v != (emptyList<String>()) }
        if (!hasBaselineData) {
            return ComparisonResult(
                mismatches = emptyList(),
                highSeverityCount = 0,
                mediumSeverityCount = 0,
                totalMismatches = 0,
                shouldAutoLock = false,
                lockReason = null,
                comparisonDetails = emptyMap(),
                baselineStatus = "empty_baseline"
            )
        }

        val mismatches = mutableListOf<MismatchEntry>()
        var highCount = 0
        var mediumCount = 0
        val details = mutableMapOf<String, Any>()

        for (field in HeartbeatBaselineManager.TRACKED_FIELDS) {
            if (field !in current) continue
            val registered = baselineFiltered[field]
            val cur = current[field]

            if (field == "device_imeis") {
                val (ok, _) = compareImeiLists(registered, cur)
                if (!ok) {
                    val severity = if (field in HIGH_SEVERITY_FIELDS) "high" else "medium"
                    val reason = MISMATCH_REASONS[field] ?: "Field '$field' value changed"
                    mismatches.add(MismatchEntry(field, severity, reason))
                    if (severity == "high") highCount++ else mediumCount++
                    details[field] = mapOf(
                        "status" to "mismatch",
                        "severity" to severity,
                        "registered" to registered,
                        "current" to cur,
                        "reason" to reason
                    )
                } else {
                    details[field] = mapOf("status" to "matched", "registered" to registered, "current" to cur)
                }
                continue
            }

            val regNorm = normalizeForComparison(registered)
            val curNorm = normalizeForComparison(cur)
            if (regNorm == curNorm) {
                details[field] = mapOf("status" to "matched", "registered" to registered, "current" to cur)
                continue
            }
            if (regNorm == null && curNorm == null) {
                details[field] = mapOf("status" to "both_empty", "registered" to registered, "current" to cur)
                continue
            }

            val severity = if (field in HIGH_SEVERITY_FIELDS) "high" else "medium"
            val reason = MISMATCH_REASONS[field] ?: "Field '$field' value changed"
            mismatches.add(MismatchEntry(field, severity, reason))
            if (severity == "high") highCount++ else mediumCount++
            details[field] = mapOf(
                "status" to "mismatch",
                "severity" to severity,
                "registered" to registered,
                "current" to cur,
                "reason" to reason
            )
        }

        val shouldAutoLock = highCount > 0
        val lockReason = if (shouldAutoLock) {
            if (highCount > 0) "Security issue" else "Device data mismatch detected"
        } else null

        return ComparisonResult(
            mismatches = mismatches,
            highSeverityCount = highCount,
            mediumSeverityCount = mediumCount,
            totalMismatches = mismatches.size,
            shouldAutoLock = shouldAutoLock,
            lockReason = lockReason,
            comparisonDetails = details,
            baselineStatus = "compared"
        )
    }

    /**
     * IMEI comparison: all current IMEIs must be in baseline. New IMEI = mismatch.
     */
    private fun compareImeiLists(registered: Any?, current: Any?): Pair<Boolean, String?> {
        val regList = toImeiList(registered) ?: return Pair(true, null)
        val curList = toImeiList(current) ?: return Pair(true, null)
        val regNorm = regList.map { it.strip().lowercase() }
        val curNorm = curList.map { it.strip().lowercase() }
        for (c in curNorm) {
            if (c !in regNorm) return Pair(false, null)
        }
        val warning = if (curNorm.size < regNorm.size) {
            "IMEI count decreased from ${regNorm.size} to ${curNorm.size}"
        } else null
        return Pair(true, warning)
    }

    private fun toImeiList(any: Any?): List<String>? {
        when (any) {
            is List<*> -> return any.mapNotNull { it?.toString() }
            is String -> return listOf(any)
            else -> return null
        }
    }

    private fun normalizeForComparison(value: Any?): Any? {
        when (value) {
            null -> return null
            is List<*> -> {
                val items = value.map { normalizeForComparison(it).toString().lowercase().trim() }.sorted()
                return items.joinToString(",")
            }
            is String -> {
                if (value.uppercase().contains("GB") || value.uppercase().contains("MB")) {
                    return value.replace(" ", "").lowercase()
                }
                return value.lowercase().trim()
            }
            is Boolean -> return value
            is Number -> return value
            else -> return value.toString().lowercase().trim()
        }
    }
}
