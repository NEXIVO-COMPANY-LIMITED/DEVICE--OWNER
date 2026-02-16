package com.example.deviceowner.data.models.registration

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue
import com.google.gson.annotations.SerializedName

data class DeviceRegistrationRequest(
    @SerializedName("loan_number")
    val loanNumber: String,

    /** Mobile: "phone" | "tablet". Django mobile endpoint expects this; defaults to "phone" if missing. */
    @SerializedName("device_type")
    val deviceType: String? = "phone",

    @SerializedName("device_id")
    val deviceId: String? = null,

    @SerializedName("device_info")
    val deviceInfo: Map<String, Any?>? = null,
    
    @SerializedName("android_info")
    val androidInfo: Map<String, Any?>? = null,
    
    @SerializedName("imei_info")
    val imeiInfo: Map<String, Any?>? = null,
    
    @SerializedName("storage_info")
    val storageInfo: Map<String, Any?>? = null,
    
    @SerializedName("location_info")
    val locationInfo: Map<String, Any?>? = null,
    
    @SerializedName("security_info")
    val securityInfo: Map<String, Any?>? = null,
    
    @SerializedName("system_integrity")
    val systemIntegrity: Map<String, Any?>? = null,
    
    @SerializedName("app_info")
    val appInfo: Map<String, Any?>? = null
) {
    /**
     * Build flat payload for Django POST /api/devices/mobile/register/.
     * Django DeviceRegistrationSerializer expects top-level fields (no nested device_info).
     */
    fun toDjangoPayload(): Map<String, Any?> {
        val di = deviceInfo ?: emptyMap()
        val ai = androidInfo ?: emptyMap()
        val ii = imeiInfo ?: emptyMap()
        val si = storageInfo ?: emptyMap()
        val li = locationInfo ?: emptyMap()
        val sec = securityInfo ?: emptyMap()
        val sys = systemIntegrity ?: emptyMap()
        val imeis = when (val v = ii["device_imeis"]) {
            is List<*> -> v.map { it?.toString() ?: "" }
            else -> listOf("NO_IMEI_FOUND")
        }
        val sdk = ai["version_sdk_int"]
        val sdkInt = when (sdk) {
            is Number -> sdk.toInt()
            is String -> sdk.toIntOrNull()
            else -> null
        }
        return mapOf(
            "loan_number" to loanNumber,
            "device_id" to deviceId,
            "device_type" to (deviceType ?: "phone"),
            "manufacturer" to (di["manufacturer"] ?: di["model"]),
            "model" to (di["model"] ?: di["manufacturer"]),
            "serial_number" to (di["serial_number"] ?: di["serial"]),
            "serial" to (di["serial"] ?: di["serial_number"]),
            "android_id" to (di["android_id"] ?: ai["android_id"]),
            "device_fingerprint" to di["fingerprint"],
            "bootloader" to di["bootloader"],
            "os_version" to ai["version_release"],
            "os_edition" to ai["version_incremental"],
            "sdk_version" to sdkInt,
            "security_patch_level" to ai["security_patch"],
            "device_imeis" to imeis,
            "installed_ram" to si["installed_ram"],
            "total_storage" to si["total_storage"],
            "latitude" to (li["latitude"] as? Number)?.toDouble(),
            "longitude" to (li["longitude"] as? Number)?.toDouble(),
            "is_device_rooted" to (sec["is_device_rooted"] as? Boolean),
            "is_usb_debugging_enabled" to (sec["is_usb_debugging_enabled"] as? Boolean),
            "is_developer_mode_enabled" to (sec["is_developer_mode_enabled"] as? Boolean),
            "is_bootloader_unlocked" to (sec["is_bootloader_unlocked"] as? Boolean),
            "is_custom_rom" to (sec["is_custom_rom"] as? Boolean),
            "installed_apps_hash" to sys["installed_apps_hash"],
            "system_properties_hash" to sys["system_properties_hash"],
            "android_info" to ai,
            "imei_info" to ii,
            "storage_info" to si,
            "location_info" to li,
            "security_info" to sec,
            "system_integrity" to sys,
            "app_info" to appInfo
        ).filter { (_, v) -> v != null && (v !is String || v.isNotEmpty()) }
    }
}

@Parcelize
data class DeviceRegistrationResponse(
    val success: Boolean? = null,
    val message: String? = null,
    
    /** Server-assigned device ID; required for heartbeat. Example from server: DEV-B5AF7F0BEDEB (not ANDROID-*). */
    @SerializedName("device_id")
    val deviceId: String? = null,
    
    val device: @RawValue Map<String, Any?>? = null,
    val data: @RawValue Map<String, Any?>? = null,
    val errors: @RawValue Map<String, Any?>? = null,
    @SerializedName("expected_fields") val expectedFields: List<String>? = null,
    @SerializedName("extra_fields") val extraFields: List<String>? = null,
    
    // Legacy fields for backward compatibility
    val loanNumber: String? = null,
    val model: String? = null,
    val manufacturer: String? = null,
    val field: String? = null
) : Parcelable {
    
    /**
     * Extract device_id from Django registration response.
     * Handles: top-level device_id, nested device/data, and legacy id/pk.
     */
    fun extractDeviceId(): String? {
        // 1. Top-level (Django mobile register returns response_data["device_id"])
        if (!deviceId.isNullOrEmpty()) return deviceId?.trim()

        // 2. Nested 'device' object
        val fromDevice = device?.let {
            (it["device_id"] ?: it["id"] ?: it["pk"])?.toString()?.trim()
        }
        if (!fromDevice.isNullOrEmpty()) return fromDevice

        // 3. 'data' wrapper (Django returns data = normalized payload; device_id may be inside)
        val fromData = data?.let {
            (it["device_id"] ?: it["id"])?.toString()?.trim()
        }
        if (!fromData.isNullOrEmpty()) return fromData

        return null
    }
    
    fun isValid(): Boolean {
        // A response is valid if success is true OR if we have a valid device ID
        return success == true || !extractDeviceId().isNullOrEmpty()
    }
    
    fun getAllErrors(): Map<String, Any?> = errors ?: emptyMap()
}

@Parcelize
data class LoanValidationResponse(
    val success: Boolean,
    @SerializedName("loan_number")
    val loanNumber: String?,
    val status: String?,
    val customer: CustomerInfo?,
    val shop: ShopInfo?,
    val company: CompanyInfo?,
    @SerializedName("device_price")
    val devicePrice: Double?,
    @SerializedName("total_amount")
    val totalAmount: Double?,
    val message: String?
) : Parcelable

@Parcelize
data class CustomerInfo(val id: Int?, val name: String?, val user_ref: String?) : Parcelable
@Parcelize
data class ShopInfo(val id: Int?, val code: String?, val name: String?) : Parcelable
@Parcelize
data class CompanyInfo(val id: Int?, val code: String?, val name: String?) : Parcelable

// HeartbeatResponse is defined in HeartbeatModels.kt (full Django response with content, next_payment, etc.)
