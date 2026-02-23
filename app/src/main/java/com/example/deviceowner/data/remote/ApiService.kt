package com.example.deviceowner.data.remote

import com.example.deviceowner.data.models.heartbeat.HeartbeatLogRequest
import com.example.deviceowner.data.models.heartbeat.HeartbeatLogResponse
import com.example.deviceowner.data.models.heartbeat.HeartbeatRequest
import com.example.deviceowner.data.models.heartbeat.HeartbeatResponse
import com.example.deviceowner.data.models.installation.InstallationStatusRequest
import com.example.deviceowner.data.models.installation.InstallationStatusResponse
import com.example.deviceowner.data.models.registration.DeviceRegistrationRequest
import com.example.deviceowner.data.models.registration.DeviceRegistrationResponse
import com.example.deviceowner.data.models.tamper.TamperEventRequest
import com.example.deviceowner.data.models.tamper.TamperEventResponse
import com.example.deviceowner.data.models.tech.BugReportRequest
import com.example.deviceowner.data.models.tech.BugReportResponse
import com.example.deviceowner.data.models.tech.DeviceLogRequest
import com.example.deviceowner.data.models.tech.DeviceLogResponse
import com.example.deviceowner.data.models.payment.InstallmentResponse
import com.example.deviceowner.data.models.payment.PaymentRequest
import com.example.deviceowner.data.models.payment.PaymentResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {

    // POST /api/devices/mobile/register/ - send DeviceRegistrationRequest directly
    @POST("api/devices/mobile/register/")
    suspend fun registerDevice(@Body deviceData: DeviceRegistrationRequest): Response<DeviceRegistrationResponse>

    @POST("api/devices/{device_id}/data/")
    suspend fun sendHeartbeat(
        @Path("device_id") deviceId: String,
        @Body heartbeatData: HeartbeatRequest
    ): Response<HeartbeatResponse>

    /**
     * POST heartbeat logs online.
     * Suggested endpoint: /api/devices/{device_id}/logs/
     */
    @POST("api/devices/{device_id}/logs/")
    suspend fun postHeartbeatLog(
        @Path("device_id") deviceId: String,
        @Body logData: HeartbeatLogRequest
    ): Response<HeartbeatLogResponse>

    @POST("api/devices/mobile/{deviceId}/installation-status/")
    suspend fun sendInstallationStatus(
        @Path("deviceId") deviceId: String,
        @Body statusData: InstallationStatusRequest
    ): Response<InstallationStatusResponse>

    /** POST /api/tech/devicecategory/logs/ - device logs for tech support (sponsa_backend) */
    @POST("api/tech/devicecategory/logs/")
    suspend fun postDeviceLog(@Body body: DeviceLogRequest): Response<DeviceLogResponse>

    /** POST /api/tech/devicecategory/bugs/ - bug reports for tech team (sponsa_backend) */
    @POST("api/tech/devicecategory/bugs/")
    suspend fun postBugReport(@Body body: BugReportRequest): Response<BugReportResponse>

    /** POST tamper event - Django /api/tamper/mobile/{device_id}/report/ */
    @POST("api/tamper/mobile/{deviceId}/report/")
    suspend fun postTamperEvent(
        @Path("deviceId") deviceId: String,
        @Body body: TamperEventRequest
    ): Response<TamperEventResponse>

    /** GET device installments - Django /api/devices/device/{device_id}/installments/ */
    @GET("api/devices/device/{deviceId}/installments/")
    suspend fun getDeviceInstallments(
        @Path("deviceId") deviceId: String
    ): Response<com.example.deviceowner.data.models.payment.InstallmentsResponse>

    /** POST /api/payments/public/loans/pay/ - Process payment and get gateway URL */
    @POST("api/payments/public/loans/pay/")
    suspend fun processPayment(@Body paymentRequest: PaymentRequest): Response<PaymentResponse>

    /** POST /api/devices/{device_id}/confirm-deactivation/ - Confirm Device Owner deactivation */
    @POST("api/devices/{deviceId}/confirm-deactivation/")
    suspend fun confirmDeactivation(
        @Path("deviceId") deviceId: String,
        @Body confirmationData: Map<String, Any>
    ): Response<Map<String, Any>>
}
