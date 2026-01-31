package com.example.deviceowner.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue
import com.google.gson.annotations.SerializedName

data class DeviceRegistrationRequest(
    @SerializedName("loan_number")
    val loanNumber: String,
    
    @SerializedName("device_id")
    val deviceId: String? = null,
    
    // Flat properties for easy access
    val manufacturer: String? = null,
    val model: String? = null,
    @SerializedName("android_id")
    val androidId: String? = null,
    @SerializedName("build_id")
    val buildId: String? = null,
    @SerializedName("build_type")
    val buildType: String? = null,
    val fingerprint: String? = null,
    val bootloader: String? = null,
    
    // Nested format (Backend accepts both flat and nested)
    @SerializedName("device_info")
    val deviceInfo: Map<String, Any?>? = null,
    
    @SerializedName("android_info")
    val androidInfo: Map<String, Any?>? = null,
    
    @SerializedName("imei_info")
    val imeiInfo: Map<String, Any?>? = null,
    
    @SerializedName("storage_info")
    val storageInfo: Map<String, String>? = null,
    
    @SerializedName("location_info")
    val locationInfo: Map<String, Any?>? = null,
    
    @SerializedName("security_info")
    val securityInfo: Map<String, Any?>? = null,
    
    @SerializedName("system_integrity")
    val systemIntegrity: Map<String, String>? = null,
    
    @SerializedName("app_info")
    val appInfo: Map<String, String>? = null
)

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
data class CustomerInfo(
    val id: Int?,
    val name: String?,
    @SerializedName("user_ref")
    val userRef: String?
) : Parcelable

@Parcelize
data class ShopInfo(
    val id: Int?,
    val code: String?,
    val name: String?
) : Parcelable

@Parcelize
data class CompanyInfo(
    val id: Int?,
    val code: String?,
    val name: String?
) : Parcelable

@Parcelize
data class DeviceRegistrationResponse(
    @SerializedName("loan_number")
    val loanNumber: String? = null,
    
    @SerializedName("device_id")
    val deviceId: String? = null,
    
    @SerializedName("android_id")
    val androidId: String? = null,
    
    val model: String? = null,
    val manufacturer: String? = null,
    val brand: String? = null,
    val product: String? = null,
    val device: String? = null,
    val board: String? = null,
    val hardware: String? = null,
    
    @SerializedName("build_id")
    val buildId: String? = null,
    
    @SerializedName("build_type")
    val buildType: String? = null,
    
    @SerializedName("build_tags")
    val buildTags: String? = null,
    
    @SerializedName("build_time")
    val buildTime: Long? = null,
    
    @SerializedName("build_user")
    val buildUser: String? = null,
    
    @SerializedName("build_host")
    val buildHost: String? = null,
    
    val fingerprint: String? = null,
    val bootloader: String? = null,
    
    @SerializedName("android_info")
    val androidInfo: Map<String, String>? = null,
    
    @SerializedName("imei_info")
    val imeiInfo: Map<String, String>? = null,
    
    @SerializedName("storage_info")
    val storageInfo: Map<String, String>? = null,
    
    @SerializedName("location_info")
    val locationInfo: Map<String, String>? = null,
    
    @SerializedName("app_info")
    val appInfo: Map<String, String>? = null,
    
    @SerializedName("security_info")
    val securityInfo: Map<String, String>? = null,
    
    @SerializedName("system_integrity")
    val systemIntegrity: Map<String, String>? = null
) : Parcelable

@Parcelize
data class LoanInfo(
    @SerializedName("loan_number")
    val loanNumber: String?,
    val status: String?,
    val customer: CustomerInfo?,
    val shop: ShopInfo?,
    val company: CompanyInfo?,
    @SerializedName("device_price")
    val devicePrice: Double?,
    @SerializedName("total_loan_amount")
    val totalLoanAmount: Double?
) : Parcelable

@Parcelize
data class UserInfo(
    val id: Int?,
    val username: String?,
    val name: String?
) : Parcelable