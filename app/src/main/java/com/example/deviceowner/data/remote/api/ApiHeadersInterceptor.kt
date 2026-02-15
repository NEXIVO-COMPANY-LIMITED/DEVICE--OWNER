package com.example.deviceowner.data.remote.api

import android.util.Log
import com.example.deviceowner.AppConfig
import com.example.deviceowner.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds required headers to all API requests for Django backend:
 * - Accept: application/json
 * - Content-Type: application/json (for request body)
 * - User-Agent: DeviceOwner-Android/1.0
 * - X-Device-Api-Key: Device agent API key (Django: settings.DEVICE_AGENT_API_KEY)
 *
 * Django validates X-Device-Api-Key in _require_device_api_key() for:
 * - POST /api/devices/mobile/register/
 * - POST /api/devices/{device_id}/data/ (heartbeat)
 */
class ApiHeadersInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val deviceApiKey = getDeviceApiKey()

        if (deviceApiKey.isEmpty()) {
            Log.w("ApiHeadersInterceptor", "⚠️ WARNING: X-Device-Api-Key is empty - Django will reject (401/403)")
        }

        val builder = chain.request().newBuilder()
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .addHeader("User-Agent", "DeviceOwner-Android/1.0")
            .addHeader("X-Device-Api-Key", deviceApiKey)

        val request = builder.build()
        if (Log.isLoggable("ApiHeadersInterceptor", Log.DEBUG)) {
            Log.d("ApiHeadersInterceptor", "Request: ${request.method} ${request.url}")
            Log.d("ApiHeadersInterceptor", "  X-Device-Api-Key: ${if (deviceApiKey.isNotEmpty()) "***" else "EMPTY"}")
        }

        return chain.proceed(request)
    }

    companion object {
        private const val DEFAULT_API_KEY = "8f3d2c9a7b1e4f6d5a9c2b3e7f1d4a6c9b8e0f2a1d3c4b5e6f7a8b9c0d1e2f3a"

        /** Resolve API key: BuildConfig (from build.gradle) > AppConfig > default. Must match Django settings.DEVICE_AGENT_API_KEY. */
        fun getDeviceApiKey(): String {
            return try {
                val key = BuildConfig.DEVICE_API_KEY
                if (key.isNotEmpty()) key
                else AppConfig.DEVICE_API_KEY.ifEmpty { DEFAULT_API_KEY }
            } catch (e: Exception) {
                Log.e("ApiHeadersInterceptor", "Error reading DEVICE_API_KEY: ${e.message}")
                AppConfig.DEVICE_API_KEY.ifEmpty { DEFAULT_API_KEY }
            }
        }
    }
}
