package com.microspace.payo.data.remote

/**
 * API endpoint paths for Device Owner app.
 * Base URL is loaded from BuildConfig.BASE_URL.
 * All paths are relative to base URL.
 */
object ApiEndpoints {

    // --- Device Registration ---
    /** POST - Mobile device registration */
    const val REGISTER_DEVICE_MOBILE = "api/devices/mobile/register/"

    // --- Device Data ---
    /** POST - Heartbeat / device data sync */
    const val DEVICE_HEARTBEAT = "api/devices/{deviceId}/data/"

    /** GET - Device status */
    const val DEVICE_STATUS = "api/devices/{deviceId}/status/"

    // --- Installation Status ---
    /** POST - Mobile installation status (completed/failed) */
    const val INSTALLATION_STATUS = "api/devices/mobile/{deviceId}/installation-status/"

    // --- Tech Support (Logs & Bugs) ---
    /** POST - Device logs for tech support */
    const val DEVICE_LOGS = "api/tech/devicecategory/logs/"

    /** POST - Bug reports for tech team */
    const val BUG_REPORTS = "api/tech/devicecategory/bugs/"

    // --- Tamper Detection ---
    /** POST - Tamper event reporting */
    const val TAMPER_REPORT = "api/tamper/mobile/{deviceId}/report/"

    // --- Legacy (kept for reference, may not exist in Django) ---
    /** Loan validation - check if Django exposes this */
    const val VALIDATE_LOAN = "api/loans/{loanNumber}/validate/"
}




