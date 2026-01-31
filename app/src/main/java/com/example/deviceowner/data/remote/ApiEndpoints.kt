package com.example.deviceowner.data.remote

object ApiEndpoints {
    // Base URL will be loaded from BuildConfig
    const val VALIDATE_LOAN = "api/loans/{loanNumber}/validate/"
    const val REGISTER_DEVICE = "api/devices/register/"
    const val DEVICE_STATUS = "api/devices/{deviceId}/status/"
    const val DEVICE_HEARTBEAT = "api/devices/{deviceId}/data/"
}