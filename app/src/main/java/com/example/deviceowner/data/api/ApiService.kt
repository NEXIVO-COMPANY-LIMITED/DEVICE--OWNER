package com.example.deviceowner.data.api

import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    
    /**
     * Register a new device with complete device information
     * POST http://82.29.168.120/api/devices/register/
     * 
     * Sends comprehensive device data including:
     * - Loan ID (primary identifier)
     * - Device identifiers (IMEI, serial number, device fingerprint)
     * - Device specifications (manufacturer, model, OS version)
     * - Hardware details (storage, RAM, battery)
     * - Comparison data for heartbeat verification
     * - Tamper status at registration
     */
    @POST("api/devices/register/")
    suspend fun registerDevice(
        @Body request: DeviceRegistrationRequest
    ): Response<DeviceRegistrationResponse>
    
    /**
     * Register device with all comparison data (NEW)
     * POST http://82.29.168.120/api/devices/register/
     * 
     * Enhanced registration with all fields needed for heartbeat comparison
     */
    @POST("api/devices/register/")
    suspend fun registerDeviceWithComparison(
        @Body request: DeviceRegistrationRequestWithComparison
    ): Response<DeviceRegistrationResponseWithComparison>
}

// ===== REQUEST MODELS =====

data class DeviceRegistrationRequest(
    val device_id: String,
    val serial_number: String,
    val device_type: String = "phone",
    val manufacturer: String,
    val system_type: String = "Mobile",
    val model: String,
    val platform: String = "Android",
    val os_version: String,
    val os_edition: String = "Mobile",
    val processor: String,
    val installed_ram: String,
    val total_storage: String,
    val build_number: Int,
    val sdk_version: Int,
    val device_imeis: List<String>,
    val loan_number: String
) {
    /**
     * Validate request data before sending
     */
    fun validate(): ValidationResult {
        return when {
            loan_number.isBlank() -> ValidationResult.Error("Loan number is required")
            device_id.isBlank() -> ValidationResult.Error("Device ID is required")
            device_imeis.isEmpty() -> ValidationResult.Error("At least one IMEI is required")
            serial_number.isBlank() -> ValidationResult.Error("Serial number is required")
            else -> ValidationResult.Success
        }
    }
}

sealed class ValidationResult {
    object Success : ValidationResult()
    data class Error(val message: String) : ValidationResult()
}

// ===== RESPONSE MODELS =====

data class DeviceInfo(
    val id: Int? = null,
    val device_id: String? = null,
    val serial_number: String? = null,
    val loan_status: String? = null,
    val type: String? = null,
    val model: String? = null,
    val is_online: Boolean? = null,
    val is_trusted: Boolean? = null
)

// ===== NEW REGISTRATION MODELS WITH COMPARISON DATA =====

/**
 * Enhanced device registration request with all comparison data
 * Includes all fields needed for heartbeat verification
 */
data class DeviceRegistrationRequestWithComparison(
    // Basic info
    val loan_number: String,
    val device_id: String,
    val device_type: String = "phone",
    val manufacturer: String,
    val system_type: String = "Mobile",
    val model: String,
    val platform: String = "Android",
    val os_version: String,
    val os_edition: String = "Mobile",
    val processor: String,
    val installed_ram: String,
    val total_storage: String,
    val build_number: Int,
    val sdk_version: Int,
    val machine_name: String,
    
    // Comparison data (12 fields for heartbeat verification)
    val android_id: String,
    val device_fingerprint: String,
    val bootloader: String,
    val hardware: String,
    val product: String,
    val device: String,
    val brand: String,
    val security_patch_level: String,
    val system_uptime: Long,
    val battery_level: Int,
    val installed_apps_hash: String,
    val system_properties_hash: String,
    
    // Tamper status at registration
    val is_device_rooted: Boolean = false,
    val is_usb_debugging_enabled: Boolean = false,
    val is_developer_mode_enabled: Boolean = false,
    val is_bootloader_unlocked: Boolean = false,
    val is_custom_rom: Boolean = false,
    
    // Location
    val latitude: Double? = null,
    val longitude: Double? = null,
    val registration_timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Validate request data before sending
     */
    fun validate(): ValidationResult {
        return when {
            loan_number.isBlank() -> ValidationResult.Error("Loan Number is required")
            device_id.isBlank() -> ValidationResult.Error("Device ID is required")
            manufacturer.isBlank() -> ValidationResult.Error("Manufacturer is required")
            model.isBlank() -> ValidationResult.Error("Model is required")
            android_id.isBlank() -> ValidationResult.Error("Android ID is required")
            device_fingerprint.isBlank() -> ValidationResult.Error("Device fingerprint is required")
            else -> ValidationResult.Success
        }
    }
}

/**
 * Enhanced device registration response
 */
data class DeviceRegistrationResponseWithComparison(
    val success: Boolean,
    val message: String,
    val device_id: String,
    val registered_at: Long,
    val verification_status: String, // VERIFIED, PENDING, FAILED
    val stored_data_hash: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
