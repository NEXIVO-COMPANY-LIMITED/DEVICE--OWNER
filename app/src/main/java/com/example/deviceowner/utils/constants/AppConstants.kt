package com.example.deviceowner.utils.constants

object AppConstants {
    
    // API Configuration
    const val API_TIMEOUT_SECONDS = 30L
    const val API_RETRY_COUNT = 3
    
    // Loan Number Validation
    const val LOAN_NUMBER_PATTERN = "^LN-\\d{8}-\\d{5}$"
    const val LOAN_NUMBER_FORMAT_EXAMPLE = "LN-20260128-00001"
    const val LOAN_NUMBER_MIN_LENGTH = 17 // LN-YYYYMMDD-NNNNN
    const val LOAN_NUMBER_MAX_LENGTH = 17
    
    // Permissions
    const val PERMISSION_REQUEST_CODE = 1001
    
    // Device Types
    const val DEVICE_TYPE_PHONE = "phone"
    const val DEVICE_TYPE_TABLET = "tablet"
    const val DEVICE_TYPE_LAPTOP = "laptop"
    const val DEVICE_TYPE_DESKTOP = "desktop"
    const val DEVICE_TYPE_OTHER = "other"
    
    // Security Levels
    const val SECURITY_LEVEL_CLEAN = "clean"
    const val SECURITY_LEVEL_LOW = "low"
    const val SECURITY_LEVEL_MEDIUM = "medium"
    const val SECURITY_LEVEL_HIGH = "high"
    const val SECURITY_LEVEL_CRITICAL = "critical"
    
    // Tamper Flags
    const val TAMPER_FLAG_ROOTED = "device_rooted"
    const val TAMPER_FLAG_USB_DEBUG = "usb_debugging_enabled"
    const val TAMPER_FLAG_DEV_MODE = "developer_mode_enabled"
    const val TAMPER_FLAG_BOOTLOADER = "bootloader_unlocked"
    const val TAMPER_FLAG_CUSTOM_ROM = "custom_rom_detected"
    
    // SharedPreferences Keys
    const val PREF_DEVICE_REGISTERED = "device_registered"
    const val PREF_LOAN_NUMBER = "loan_number"
    const val PREF_DEVICE_ID = "device_id"
    const val PREF_REGISTRATION_COMPLETED = "registration_completed"
    
    // Intent Extras
    const val EXTRA_LOAN_NUMBER = "loan_number"
    const val EXTRA_LOAN_DATA = "loan_data"
    const val EXTRA_DEVICE_DATA = "device_data"
    
    // HTTP Status Codes
    const val HTTP_OK = 200
    const val HTTP_CREATED = 201
    const val HTTP_BAD_REQUEST = 400
    const val HTTP_UNAUTHORIZED = 401
    const val HTTP_FORBIDDEN = 403
    const val HTTP_NOT_FOUND = 404
    const val HTTP_INTERNAL_SERVER_ERROR = 500
}