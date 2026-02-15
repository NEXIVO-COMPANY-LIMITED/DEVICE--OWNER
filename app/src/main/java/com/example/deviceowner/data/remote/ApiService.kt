package com.example.deviceowner.data.remote

import com.example.deviceowner.data.models.BugReportRequest
import com.example.deviceowner.data.models.BugReportResponse
import com.example.deviceowner.data.models.DeviceLogRequest
import com.example.deviceowner.data.models.DeviceLogResponse
import com.example.deviceowner.data.models.DeviceRegistrationRequest
import com.example.deviceowner.data.models.DeviceRegistrationResponse
import com.example.deviceowner.data.models.HeartbeatRequest
import com.example.deviceowner.data.models.HeartbeatResponse
import com.example.deviceowner.data.models.InstallationStatusRequest
import com.example.deviceowner.data.models.InstallationStatusResponse
import com.example.deviceowner.data.models.TamperEventRequest
import com.example.deviceowner.data.models.TamperEventResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {

    // POST /api/devices/mobile/register/ - send DeviceRegistrationRequest directly
    @POST("api/devices/mobile/register/")
    suspend fun registerDevice(@Body deviceData: DeviceRegistrationRequest): Response<DeviceRegistrationResponse>

    @POST("api/devices/{deviceId}/data/")
    suspend fun sendHeartbeat(
        @Path("deviceId") deviceId: String,
        @Body heartbeatData: HeartbeatRequest
    ): Response<HeartbeatResponse>

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
}