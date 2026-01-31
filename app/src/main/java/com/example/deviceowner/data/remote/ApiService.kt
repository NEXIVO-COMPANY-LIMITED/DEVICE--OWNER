package com.example.deviceowner.data.remote

import com.example.deviceowner.data.models.DeviceRegistrationRequest
import com.example.deviceowner.data.models.DeviceRegistrationResponse
import com.example.deviceowner.data.models.HeartbeatRequest
import com.example.deviceowner.data.models.HeartbeatResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {
    
    // Payoplan production endpoint for device registration (HTTPS compatible)
    @POST("api/devices/mobile/register/")
    suspend fun registerDevice(
        @Body deviceData: DeviceRegistrationRequest
    ): Response<DeviceRegistrationResponse>
    
    @POST("api/devices/{deviceId}/data/")
    suspend fun sendHeartbeat(
        @Path("deviceId") deviceId: String, 
        @Body heartbeatData: HeartbeatRequest
    ): Response<HeartbeatResponse>
    
    @POST("api/devices/{deviceId}/security/violation/")
    suspend fun reportSecurityViolation(
        @Path("deviceId") deviceId: String,
        @Body violationData: Map<String, Any?>
    ): Response<Map<String, Any>>
    
    @POST("api/devices/security/violation/")
    suspend fun reportSecurityViolation(
        @Body violationData: Map<String, Any?>
    ): Response<Map<String, Any>>
}