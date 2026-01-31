package com.example.deviceowner.data.remote.api

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Ensures API requests ask for JSON responses and match PowerShell test script headers.
 * Reduces "server returning HTML instead of JSON" errors when server
 * would otherwise return an HTML error page (e.g. 400/422 validation).
 * Content-Type is set by Retrofit for @Body requests.
 */
class ApiHeadersInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .addHeader("Accept", "application/json")
            .addHeader("User-Agent", "DeviceOwner-Android/1.0")
            .build()
        return chain.proceed(request)
    }
}
