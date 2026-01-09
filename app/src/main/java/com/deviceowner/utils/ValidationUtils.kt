package com.deviceowner.utils

/**
 * Validation utilities for input fields.
 * Provides validation methods for all user inputs with field-level validation.
 */
object ValidationUtils {

    /**
     * Validate Loan ID format
     * Requirements: Must start with "LN-", followed by date (YYYYMMDD) and numeric ID
     * Format: LN-YYYYMMDD-XXXXX (e.g., "LN-20260107-00001")
     */
    fun validateLoanId(loanId: String?): Pair<Boolean, String> {
        return when {
            loanId.isNullOrEmpty() -> Pair(false, "EMPTY_FIELD")
            loanId.trim().isEmpty() -> Pair(false, "EMPTY_FIELD")
            !loanId.startsWith("LN-", ignoreCase = true) -> Pair(false, "INVALID_LOAN_ID_PREFIX")
            !loanId.matches(Regex("^LN-\\d{8}-\\d+$", RegexOption.IGNORE_CASE)) ->
                Pair(false, "INVALID_LOAN_ID")
            else -> Pair(true, "")
        }
    }

   

    /**
     * Validate IMEI format
     * Requirements: 14-16 digits, numeric only
     */
    fun validateIMEI(imei: String?): Pair<Boolean, String> {
        return when {
            imei.isNullOrEmpty() -> Pair(false, "EMPTY_FIELD")
            imei.trim().isEmpty() -> Pair(false, "EMPTY_FIELD")
            !imei.matches(Regex("^\\d{14,16}$")) -> Pair(false, "INVALID_IMEI")
            else -> Pair(true, "")
        }
    }

    /**
     * Validate Serial Number format
     * Requirements: non-empty, minimum 2 characters
     */
    fun validateSerialNumber(serialNumber: String?): Pair<Boolean, String> {
        return when {
            serialNumber.isNullOrEmpty() -> Pair(false, "EMPTY_FIELD")
            serialNumber.trim().isEmpty() -> Pair(false, "EMPTY_FIELD")
            serialNumber.length < 2 -> Pair(false, "INVALID_SERIAL")
            else -> Pair(true, "")
        }
    }

    /**
     * Validate Model name
     * Requirements: non-empty
     */
    fun validateModel(model: String?): Pair<Boolean, String> {
        return when {
            model.isNullOrEmpty() -> Pair(false, "EMPTY_FIELD")
            model.trim().isEmpty() -> Pair(false, "EMPTY_FIELD")
            else -> Pair(true, "")
        }
    }

    /**
     * Validate Android Version
     * Requirements: non-empty
     */
    fun validateAndroidVersion(version: String?): Pair<Boolean, String> {
        return when {
            version.isNullOrEmpty() -> Pair(false, "EMPTY_FIELD")
            version.trim().isEmpty() -> Pair(false, "EMPTY_FIELD")
            else -> Pair(true, "")
        }
    }

    /**
     * Validate all user info fields
     */


    /**
     * Validate all device info fields
     */
    fun validateDeviceInfo(
        imei: String?,
        serialNumber: String?,
        model: String?,
        androidVersion: String?
    ): Boolean {
        val imeiValid = validateIMEI(imei).first
        val serialValid = validateSerialNumber(serialNumber).first
        val modelValid = validateModel(model).first
        val versionValid = validateAndroidVersion(androidVersion).first
        return imeiValid && serialValid && modelValid && versionValid
    }

    /**
     * Validate scanner field before opening scanner
     * Ensures field is not empty before attempting to scan
     */
    fun validateScannerField(fieldValue: String?, fieldName: String): Pair<Boolean, String> {
        return when {
            fieldValue.isNullOrEmpty() -> Pair(true, "") // Allow scanning into empty field
            fieldValue.trim().isEmpty() -> Pair(true, "") // Allow scanning into whitespace field
            else -> {
                // Field already has value, validate it
                when (fieldName) {
                    "imei" -> validateIMEI(fieldValue)
                    "serialNumber" -> validateSerialNumber(fieldValue)
                    else -> Pair(true, "")
                }
            }
        }
    }

    /**
     * Validate scanned barcode value
     * Ensures scanned value matches expected format
     */
    fun validateScannedValue(scannedValue: String?, fieldType: String): Pair<Boolean, String> {
        return when {
            scannedValue.isNullOrEmpty() -> Pair(false, "EMPTY_SCAN")
            scannedValue.trim().isEmpty() -> Pair(false, "EMPTY_SCAN")
            else -> {
                when (fieldType) {
                    "imei" -> validateIMEI(scannedValue)
                    "serialNumber" -> validateSerialNumber(scannedValue)
                    else -> Pair(false, "INVALID_SCAN_TYPE")
                }
            }
        }
    }

    /**
     * Get validation error message
     */
    fun getValidationError(fieldName: String, value: String?): String? {
        return when (fieldName) {

            "imei" -> {
                val result = validateIMEI(value)
                if (!result.first) ErrorHandler.getErrorMessage(result.second) else null
            }
            "serialNumber" -> {
                val result = validateSerialNumber(value)
                if (!result.first) ErrorHandler.getErrorMessage(result.second) else null
            }
            else -> null
        }
    }


}
