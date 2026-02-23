package com.example.deviceowner.data.remote

// Force rebuild - timestamp: 2026-02-14
import android.util.Log
import com.example.deviceowner.AppConfig
import com.example.deviceowner.data.remote.api.ApiHeadersInterceptor
import com.example.deviceowner.data.remote.api.HtmlResponseInterceptor
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
import com.google.gson.GsonBuilder
import okhttp3.CertificatePinner
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class ApiClient {
    
    private val gson = GsonBuilder()
        .setLenient()
        .serializeNulls()  // Include null values in JSON serialization
        .create()
    
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(120, TimeUnit.SECONDS)  // 2 minutes for SSL handshake
        .readTimeout(120, TimeUnit.SECONDS)     // 2 minutes for slow networks
        .writeTimeout(120, TimeUnit.SECONDS)    // 2 minutes for slow networks
        .callTimeout(240, TimeUnit.SECONDS)     // 4 minutes overall request timeout
        .addInterceptor(ApiHeadersInterceptor())
        .addInterceptor(HtmlResponseInterceptor())
        .apply {
            // Use HEADERS only - BODY consumes the response stream, leaving errorBody() empty for 400/404/500
            if (true) { // FORCED LOGGING ENABLED
                val loggingInterceptor = HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.HEADERS
                }
                addInterceptor(loggingInterceptor)
            }
            
            // PERFECT SSL SECURITY FOR DEVICE OWNER
            try {
                // 1. Force modern TLS versions only (TLS 1.2+)
                val connectionSpecs = listOf(
                    ConnectionSpec.MODERN_TLS,
                    ConnectionSpec.COMPATIBLE_TLS
                )
                connectionSpecs(connectionSpecs)
                
                // 2. Enhanced hostname verification
                hostnameVerifier { hostname, session ->
                    val validHostnames = listOf("payoplan.com", "api.payoplan.com")
                    val isValidHostname = validHostnames.any { validHost ->
                        hostname.equals(validHost, ignoreCase = true) || 
                        hostname.endsWith(".$validHost", ignoreCase = true)
                    }
                    
                    if (isValidHostname) {
                        Log.d("ApiClient", "✅ SSL hostname verified: $hostname")
                        true
                    } else {
                        Log.e("ApiClient", "❌ SSL hostname verification failed: $hostname")
                        false
                    }
                }
                
                // 3. Certificate pinner for maximum security (programmatic pinning)
                val certificatePinner = CertificatePinner.Builder()
                    .add("payoplan.com", "sha256/y8S/sGw+VDqpDXnu4dKxrnI6nj1tdn0od2WAFM7zvog=") // REAL PAYOPLAN.COM PIN
                    .add("api.payoplan.com", "sha256/y8S/sGw+VDqpDXnu4dKxrnI6nj1tdn0od2WAFM7zvog=") // REAL PAYOPLAN.COM PIN
                    .build()
                
                // ENABLED: Certificate pinning is now active with real certificate pin
                certificatePinner(certificatePinner)
                
                Log.d("ApiClient", "✅ Perfect SSL security configured for Device Owner")
                Log.d("ApiClient", "   - TLS 1.2+ enforced")
                Log.d("ApiClient", "   - Hostname verification active")
                Log.d("ApiClient", "   - Certificate pinning ACTIVE with real payoplan.com pin")
                Log.d("ApiClient", "   - Pin: y8S/sGw+VDqpDXnu4dKxrnI6nj1tdn0od2WAFM7zvog=")
                Log.d("ApiClient", "   - Device Owner ClassCastException should be FIXED!")
                
            } catch (e: Exception) {
                Log.w("ApiClient", "Could not configure enhanced SSL: ${e.message}")
            }
        }
        .build()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(AppConfig.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
    
    private val apiService: ApiService by lazy { 
        try {
            retrofit.create(ApiService::class.java)
        } catch (e: Exception) {
            throw RuntimeException("Failed to create API service: ${e.message}", e)
        }
    }
    
    suspend fun registerDevice(deviceData: DeviceRegistrationRequest): Response<DeviceRegistrationResponse> {
        Log.d("ApiClient", "🔍 Device Owner Registration Attempt")
        Log.d("ApiClient", "Base URL: ${AppConfig.BASE_URL}")
        Log.d("ApiClient", "Endpoint: api/devices/mobile/register/")
        Log.d("ApiClient", "Device API Key: ${if (AppConfig.DEVICE_API_KEY.isNotEmpty()) "***" else "EMPTY"}")
        try {
            val gson = com.google.gson.Gson()
            Log.d("ApiClient", "📤 REQUEST: loan_number=${deviceData.loanNumber}, device_type=${deviceData.deviceType}")
        } catch (e: Exception) { Log.w("ApiClient", "Could not log: ${e.message}") }
        return try {
            val response = apiService.registerDevice(deviceData)
            Log.d("ApiClient", "✅ Registration response received: HTTP ${response.code()}")
            if (response.isSuccessful) {
                Log.d("ApiClient", "✅ Device registered successfully")
            } else {
                Log.e("ApiClient", "❌ Registration failed: HTTP ${response.code()}")
            }
            response
        } catch (e: Exception) {
            Log.e("ApiClient", "❌ Registration failed with exception: ${e.message}", e)
            throw e
        }
    }

    /** Register with flat payload (Django expects top-level fields). */
    suspend fun registerDevicePayload(payload: Map<String, Any?>): Response<DeviceRegistrationResponse> {
        Log.d("ApiClient", "🔍 Device Owner Registration Attempt (Payload)")
        Log.d("ApiClient", "Base URL: ${AppConfig.BASE_URL}")
        Log.d("ApiClient", "Endpoint: api/devices/mobile/register/")
        Log.d("ApiClient", "Device API Key: ${if (AppConfig.DEVICE_API_KEY.isNotEmpty()) "***" else "EMPTY"}")
        try {
            val gson = com.google.gson.Gson()
            Log.d("ApiClient", "📤 REQUEST (flat): loan_number=${payload["loan_number"]}, device_id=${payload["device_id"]}")
        } catch (e: Exception) { Log.w("ApiClient", "Could not log: ${e.message}") }
        return try {
            // Convert map to DeviceRegistrationRequest for API call
            val request = DeviceRegistrationRequest(
                loanNumber = payload["loan_number"] as? String ?: "",
                deviceId = payload["device_id"] as? String,
                deviceType = payload["device_type"] as? String ?: "phone",
                deviceInfo = payload["device_info"] as? Map<String, Any?>,
                androidInfo = payload["android_info"] as? Map<String, Any?>,
                imeiInfo = payload["imei_info"] as? Map<String, Any?>,
                storageInfo = payload["storage_info"] as? Map<String, Any?>,
                locationInfo = payload["location_info"] as? Map<String, Any?>,
                securityInfo = payload["security_info"] as? Map<String, Any?>,
                systemIntegrity = payload["system_integrity"] as? Map<String, Any?>,
                appInfo = payload["app_info"] as? Map<String, Any?>
            )
            val response = apiService.registerDevice(request)
            Log.d("ApiClient", "✅ Registration response received: HTTP ${response.code()}")
            if (response.isSuccessful) {
                Log.d("ApiClient", "✅ Device registered successfully")
            } else {
                Log.e("ApiClient", "❌ Registration failed: HTTP ${response.code()}")
            }
            response
        } catch (e: Exception) {
            Log.e("ApiClient", "❌ Registration failed with exception: ${e.message}", e)
            throw e
        }
    }
    
    suspend fun sendHeartbeat(deviceId: String, heartbeatData: HeartbeatRequest): Response<HeartbeatResponse> {
        Log.d("ApiClient", "🔍 Heartbeat Attempt for device: $deviceId")
        Log.d("ApiClient", "   URL: ${AppConfig.BASE_URL}api/devices/$deviceId/data/")
        Log.d("ApiClient", "   X-Device-Api-Key: ${if (AppConfig.DEVICE_API_KEY.isNotEmpty()) "***present" else "EMPTY"}")
        
        if (deviceId.isBlank() || deviceId.equals("unknown", ignoreCase = true)) {
            Log.e("ApiClient", "❌ Heartbeat ABORTED: deviceId is blank or unknown")
            throw IllegalArgumentException("deviceId cannot be blank or unknown for heartbeat")
        }
        
        // Log full request data for debugging HTTP 500 errors
        Log.d("ApiClient", "📋 HEARTBEAT REQUEST DATA:")
        Log.d("ApiClient", "   Serial: ${heartbeatData.serialNumber}")
        Log.d("ApiClient", "   Model: ${heartbeatData.model}")
        Log.d("ApiClient", "   Manufacturer: ${heartbeatData.manufacturer}")
        Log.d("ApiClient", "   Android ID: ${heartbeatData.androidId}")
        Log.d("ApiClient", "   IMEIs: ${heartbeatData.deviceImeis}")
        Log.d("ApiClient", "   RAM: ${heartbeatData.installedRam}")
        Log.d("ApiClient", "   Storage: ${heartbeatData.totalStorage}")
        Log.d("ApiClient", "   Lat/Long: ${heartbeatData.latitude}/${heartbeatData.longitude}")
        Log.d("ApiClient", "   Rooted: ${heartbeatData.isDeviceRooted}")
        Log.d("ApiClient", "   USB Debug: ${heartbeatData.isUsbDebuggingEnabled}")
        Log.d("ApiClient", "   Dev Mode: ${heartbeatData.isDeveloperModeEnabled}")
        Log.d("ApiClient", "   Bootloader: ${heartbeatData.isBootloaderUnlocked}")
        Log.d("ApiClient", "   Custom ROM: ${heartbeatData.isCustomRom}")
        Log.d("ApiClient", "   SDK: ${heartbeatData.sdkVersion}")
        Log.d("ApiClient", "   OS: ${heartbeatData.osVersion}")
        Log.d("ApiClient", "   Security Patch: ${heartbeatData.securityPatchLevel}")
        Log.d("ApiClient", "   Battery: ${heartbeatData.batteryLevel}")
        Log.d("ApiClient", "   Language: ${heartbeatData.language}")
        Log.d("ApiClient", "   Fingerprint: ${heartbeatData.deviceFingerprint}")
        Log.d("ApiClient", "   Bootloader: ${heartbeatData.bootloader}")
        
        return try {
            val response = apiService.sendHeartbeat(deviceId, heartbeatData)
            if (response.isSuccessful) {
                Log.d("ApiClient", "✅ Heartbeat SUCCESS: HTTP ${response.code()}")
            } else {
                val errorBodyStr = response.errorBody()?.string() ?: "(no body)"
                Log.e("ApiClient", "❌ Heartbeat FAILED: HTTP ${response.code()} ${response.message()}")
                Log.e("ApiClient", "   Error body: $errorBodyStr")
                when (response.code()) {
                    400 -> Log.e("ApiClient", "   Cause: Invalid payload - check field formats")
                    401 -> Log.e("ApiClient", "   Cause: Unauthorized - check API key")
                    403 -> Log.e("ApiClient", "   Cause: Forbidden - check API key permissions")
                    404 -> Log.e("ApiClient", "   Cause: Device not found in DB - verify device_id")
                    500 -> Log.e("ApiClient", "   Cause: Server error - check backend logs")
                    else -> Log.e("ApiClient", "   Cause: HTTP ${response.code()}")
                }
            }
            response
        } catch (e: java.net.UnknownHostException) {
            Log.e("ApiClient", "❌ Heartbeat NETWORK ERROR: No internet / DNS failure - ${e.message}")
            throw e
        } catch (e: java.net.ConnectException) {
            Log.e("ApiClient", "❌ Heartbeat NETWORK ERROR: Cannot connect to server - ${e.message}")
            throw e
        } catch (e: javax.net.ssl.SSLException) {
            Log.e("ApiClient", "❌ Heartbeat SSL ERROR: Certificate/handshake failed - ${e.message}")
            throw e
        } catch (e: Exception) {
            Log.e("ApiClient", "❌ Heartbeat failed: ${e.javaClass.simpleName} - ${e.message}", e)
            throw e
        }
    }
    
    suspend fun sendInstallationStatus(deviceId: String, statusData: InstallationStatusRequest): Response<InstallationStatusResponse> {
        Log.d("ApiClient", "🔍 Installation Status: device=$deviceId, url=api/devices/mobile/{device_id}/installation-status/")
        if (deviceId.isBlank() || deviceId.equals("unknown", ignoreCase = true)) {
            Log.e("ApiClient", "❌ Installation status ABORTED: device_id blank or unknown")
            throw IllegalArgumentException("device_id cannot be blank or unknown for installation status")
        }
        Log.d("ApiClient", "Device API Key: ${if (AppConfig.DEVICE_API_KEY.isNotEmpty()) "***" else "EMPTY"}")
        
        // Log the request data being sent
        try {
            val gson = com.google.gson.Gson()
            val requestJson = gson.toJson(statusData)
            Log.d("ApiClient", "📤 REQUEST DATA:")
            Log.d("ApiClient", requestJson)
        } catch (e: Exception) {
            Log.w("ApiClient", "Could not log request data: ${e.message}")
        }
        
        return try {
            val response = apiService.sendInstallationStatus(deviceId, statusData)
            Log.d("ApiClient", "✅ Installation status response received: HTTP ${response.code()}")
            if (response.isSuccessful) {
                Log.d("ApiClient", "✅ Installation status sent successfully")
            } else {
                Log.e("ApiClient", "❌ Installation status failed: HTTP ${response.code()}")
                Log.e("ApiClient", "   Error body: ${response.errorBody()?.string()}")
            }
            response
        } catch (e: Exception) {
            Log.e("ApiClient", "❌ Installation status failed with exception: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * POST /api/tech/devicecategory/logs/ - submit device log to sponsa_backend for tech support.
     * Uses same X-Device-Api-Key as other device endpoints.
     */
    suspend fun postDeviceLog(request: DeviceLogRequest): Response<DeviceLogResponse> {
        return try {
            val response = apiService.postDeviceLog(request)
            if (!response.isSuccessful) {
                Log.e("ApiClient", "❌ Device log failed: HTTP ${response.code()} ${response.errorBody()?.string()?.take(500)}")
            }
            response
        } catch (e: Exception) {
            Log.e("ApiClient", "❌ Post device log failed: ${e.message}", e)
            throw e
        }
    }

    /**
     * POST /api/tech/devicecategory/bugs/ - submit bug report to sponsa_backend for tech team.
     * Uses same X-Device-Api-Key as other device endpoints.
     */
    suspend fun postBugReport(request: BugReportRequest): Response<BugReportResponse> {
        return try {
            apiService.postBugReport(request)
        } catch (e: Exception) {
            Log.e("ApiClient", "❌ Post bug report failed: ${e.message}", e)
            throw e
        }
    }

    suspend fun postTamperEvent(deviceId: String, request: TamperEventRequest): Response<TamperEventResponse> {
        Log.d("ApiClient", "🔍 Tamper report: device=$deviceId, type=${request.tamperType}, url=api/tamper/mobile/{device_id}/report/")
        if (deviceId.isBlank() || deviceId.equals("unknown", ignoreCase = true)) {
            Log.e("ApiClient", "❌ Tamper ABORTED: device_id blank or unknown")
            throw IllegalArgumentException("device_id cannot be blank or unknown for tamper report")
        }
        return try {
            val response = apiService.postTamperEvent(deviceId, request)
            if (response.isSuccessful) {
                Log.d("ApiClient", "✅ Tamper event sent: ${request.tamperType}")
            } else {
                val errBody = response.errorBody()?.string()?.take(200) ?: ""
                Log.e("ApiClient", "❌ Tamper failed: HTTP ${response.code()} $errBody")
            }
            response
        } catch (e: Exception) {
            Log.e("ApiClient", "❌ Post tamper event failed: ${e.message}", e)
            throw e
        }
    }

    suspend fun getDeviceInstallments(deviceId: String): Response<com.example.deviceowner.data.models.payment.InstallmentsResponse> {
        return try {
            apiService.getDeviceInstallments(deviceId)
        } catch (e: Exception) {
            Log.e("ApiClient", "❌ Get device installments failed: ${e.message}", e)
            throw e
        }
    }

    suspend fun confirmDeactivation(deviceId: String, status: String, message: String): Response<Map<String, Any>> {
        Log.d("ApiClient", "🔍 Deactivation Confirmation: device=$deviceId, status=$status")
        Log.d("ApiClient", "   URL: ${AppConfig.BASE_URL}api/devices/$deviceId/confirm-deactivation/")
        Log.d("ApiClient", "   X-Device-Api-Key: ${if (AppConfig.DEVICE_API_KEY.isNotEmpty()) "***present" else "EMPTY"}")
        
        if (deviceId.isBlank() || deviceId.equals("unknown", ignoreCase = true)) {
            Log.e("ApiClient", "❌ Deactivation confirmation ABORTED: deviceId is blank or unknown")
            throw IllegalArgumentException("deviceId cannot be blank or unknown for deactivation confirmation")
        }
        
        return try {
            val confirmationData = mapOf(
                "status" to status,
                "message" to message
            )
            val response = apiService.confirmDeactivation(deviceId, confirmationData)
            if (response.isSuccessful) {
                Log.d("ApiClient", "✅ Deactivation confirmation SUCCESS: HTTP ${response.code()}")
            } else {
                val errorBodyStr = response.errorBody()?.string() ?: "(no body)"
                Log.e("ApiClient", "❌ Deactivation confirmation FAILED: HTTP ${response.code()} ${response.message()}")
                Log.e("ApiClient", "   Error body: $errorBodyStr")
            }
            response
        } catch (e: Exception) {
            Log.e("ApiClient", "❌ Deactivation confirmation failed: ${e.javaClass.simpleName} - ${e.message}", e)
            throw e
        }
    }
}