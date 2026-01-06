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
     */
    @POST("api/devices/register/")
    suspend fun registerDevice(
        @Body request: DeviceRegistrationRequest
    ): Response<DeviceRegistrationResponse>
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

data class DeviceRegistrationResponse(
    val message: String? = null,
    val device: DeviceInfo? = null,
    val shop: ShopInfo? = null,
    val company: CompanyInfo? = null,
    val borrower: BorrowerInfo? = null,
    val registered_by: RegisteredByInfo? = null,
    val device_id: String? = null,
    val registration_token: String? = null,
    val status: String? = null,
    val success: Boolean? = null,
    val error: String? = null
)

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

data class ShopInfo(
    val code: String? = null,
    val name: String? = null
)

data class CompanyInfo(
    val code: String? = null,
    val name: String? = null
)

data class BorrowerInfo(
    val user_ref: String? = null,
    val name: String? = null
)

data class RegisteredByInfo(
    val id: Int? = null,
    val username: String? = null,
    val name: String? = null
)
