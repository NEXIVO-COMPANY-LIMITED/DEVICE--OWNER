package com.example.deviceowner.utils

import android.content.Context
import android.util.Log
import com.example.deviceowner.BuildConfig
import com.example.deviceowner.data.remote.ApiEndpoints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Diagnostic tool to test API endpoints and identify connection issues
 */
class EndpointDiagnostic(private val context: Context) {
    
    companion object {
        private const val TAG = "EndpointDiagnostic"
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    
    /**
     * Test the current API endpoint
     */
    suspend fun testCurrentEndpoint(): DiagnosticResult = withContext(Dispatchers.IO) {
        val url = BuildConfig.BASE_URL + ApiEndpoints.REGISTER_DEVICE
        Log.d(TAG, "Testing current endpoint: $url")
        testEndpoint(url)
    }
    
    /**
     * Test all possible endpoints to find the working one
     */
    suspend fun testAllPossibleEndpoints(): List<DiagnosticResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<DiagnosticResult>()
        
        // Generate possible endpoint URLs
        val possibleEndpoints = getPossibleEndpoints()
        
        possibleEndpoints.forEach { url ->
            Log.d(TAG, "Testing endpoint: $url")
            val result = testEndpoint(url)
            results.add(result)
            
            // Log result immediately
            when (result.status) {
                EndpointStatus.SUCCESS -> Log.i(TAG, "✅ SUCCESS: $url")
                EndpointStatus.CLIENT_ERROR -> Log.w(TAG, "⚠️ CLIENT ERROR: $url - ${result.message}")
                EndpointStatus.SERVER_ERROR -> Log.w(TAG, "⚠️ SERVER ERROR: $url - ${result.message}")
                EndpointStatus.NETWORK_ERROR -> Log.e(TAG, "❌ NETWORK ERROR: $url - ${result.message}")
                EndpointStatus.SSL_ERROR -> Log.e(TAG, "❌ SSL ERROR: $url - ${result.message}")
            }
        }
        
        results
    }
    
    /**
     * Generate possible endpoint URLs to test
     */
    private fun getPossibleEndpoints(): List<String> {
        val baseUrl = BuildConfig.BASE_URL
        val endpoint = ApiEndpoints.REGISTER_DEVICE
        
        return listOf(
            baseUrl + endpoint,
            baseUrl.replace("https://", "http://") + endpoint,
            baseUrl.replace(":8000", ":80") + endpoint,
            baseUrl.replace(":8000", ":443") + endpoint,
            baseUrl.replace(":8000", "") + endpoint
        ).distinct()
    }
    
    /**
     * Test a specific endpoint
     */
    private suspend fun testEndpoint(url: String): DiagnosticResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .head() // Use HEAD request to avoid sending data
                .addHeader("User-Agent", "DeviceOwner-Diagnostic/1.0")
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.peekBody(1024).string()
            
            when (response.code) {
                in 200..299 -> DiagnosticResult(
                    url = url,
                    status = EndpointStatus.SUCCESS,
                    httpCode = response.code,
                    message = "Endpoint is reachable",
                    responsePreview = responseBody.take(200),
                    contentType = response.header("Content-Type")
                )
                in 400..499 -> DiagnosticResult(
                    url = url,
                    status = EndpointStatus.CLIENT_ERROR,
                    httpCode = response.code,
                    message = "Client error - endpoint exists but may require different method/headers",
                    responsePreview = responseBody.take(200),
                    contentType = response.header("Content-Type")
                )
                in 500..599 -> DiagnosticResult(
                    url = url,
                    status = EndpointStatus.SERVER_ERROR,
                    httpCode = response.code,
                    message = "Server error - endpoint exists but server has issues",
                    responsePreview = responseBody.take(200),
                    contentType = response.header("Content-Type")
                )
                else -> DiagnosticResult(
                    url = url,
                    status = EndpointStatus.NETWORK_ERROR,
                    httpCode = response.code,
                    message = "Unexpected HTTP code: ${response.code}",
                    responsePreview = responseBody.take(200),
                    contentType = response.header("Content-Type")
                )
            }
        } catch (e: javax.net.ssl.SSLHandshakeException) {
            DiagnosticResult(
                url = url,
                status = EndpointStatus.SSL_ERROR,
                httpCode = null,
                message = "SSL Handshake failed: ${e.message}",
                responsePreview = null,
                contentType = null,
                exception = e
            )
        } catch (e: javax.net.ssl.SSLException) {
            DiagnosticResult(
                url = url,
                status = EndpointStatus.SSL_ERROR,
                httpCode = null,
                message = "SSL Error: ${e.message}",
                responsePreview = null,
                contentType = null,
                exception = e
            )
        } catch (e: java.net.UnknownHostException) {
            DiagnosticResult(
                url = url,
                status = EndpointStatus.NETWORK_ERROR,
                httpCode = null,
                message = "Cannot resolve host: ${e.message}",
                responsePreview = null,
                contentType = null,
                exception = e
            )
        } catch (e: java.net.ConnectException) {
            DiagnosticResult(
                url = url,
                status = EndpointStatus.NETWORK_ERROR,
                httpCode = null,
                message = "Connection refused: ${e.message}",
                responsePreview = null,
                contentType = null,
                exception = e
            )
        } catch (e: java.net.SocketTimeoutException) {
            DiagnosticResult(
                url = url,
                status = EndpointStatus.NETWORK_ERROR,
                httpCode = null,
                message = "Connection timeout: ${e.message}",
                responsePreview = null,
                contentType = null,
                exception = e
            )
        } catch (e: Exception) {
            DiagnosticResult(
                url = url,
                status = EndpointStatus.NETWORK_ERROR,
                httpCode = null,
                message = "Network error: ${e.javaClass.simpleName} - ${e.message}",
                responsePreview = null,
                contentType = null,
                exception = e
            )
        }
    }
    
    /**
     * Generate a comprehensive diagnostic report
     */
    suspend fun generateDiagnosticReport(): String = withContext(Dispatchers.IO) {
        val report = StringBuilder()
        report.appendLine("=== ENDPOINT DIAGNOSTIC REPORT ===")
        report.appendLine("Generated at: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}")
        report.appendLine()
        
        // Test current endpoint
        report.appendLine("🔍 TESTING CURRENT ENDPOINT:")
        val currentResult = testCurrentEndpoint()
        report.appendLine("URL: ${currentResult.url}")
        report.appendLine("Status: ${currentResult.status}")
        report.appendLine("HTTP Code: ${currentResult.httpCode ?: "N/A"}")
        report.appendLine("Message: ${currentResult.message}")
        report.appendLine("Content-Type: ${currentResult.contentType ?: "N/A"}")
        if (currentResult.responsePreview != null) {
            report.appendLine("Response Preview: ${currentResult.responsePreview}")
        }
        report.appendLine()
        
        // Test all possible endpoints
        report.appendLine("🔍 TESTING ALL POSSIBLE ENDPOINTS:")
        val allResults = testAllPossibleEndpoints()
        
        val successfulEndpoints = allResults.filter { it.status == EndpointStatus.SUCCESS }
        val workingEndpoints = allResults.filter { it.status in listOf(EndpointStatus.SUCCESS, EndpointStatus.CLIENT_ERROR) }
        
        if (successfulEndpoints.isNotEmpty()) {
            report.appendLine("✅ SUCCESSFUL ENDPOINTS:")
            successfulEndpoints.forEach { result ->
                report.appendLine("  - ${result.url} (HTTP ${result.httpCode})")
            }
            report.appendLine()
        }
        
        if (workingEndpoints.isNotEmpty() && workingEndpoints != successfulEndpoints) {
            report.appendLine("⚠️ PARTIALLY WORKING ENDPOINTS (may need different method/headers):")
            workingEndpoints.filter { it.status != EndpointStatus.SUCCESS }.forEach { result ->
                report.appendLine("  - ${result.url} (HTTP ${result.httpCode}) - ${result.message}")
            }
            report.appendLine()
        }
        
        val failedEndpoints = allResults.filter { it.status !in listOf(EndpointStatus.SUCCESS, EndpointStatus.CLIENT_ERROR) }
        if (failedEndpoints.isNotEmpty()) {
            report.appendLine("❌ FAILED ENDPOINTS:")
            failedEndpoints.forEach { result ->
                report.appendLine("  - ${result.url} - ${result.message}")
            }
            report.appendLine()
        }
        
        // Recommendations
        report.appendLine("📋 RECOMMENDATIONS:")
        when {
            successfulEndpoints.isNotEmpty() -> {
                report.appendLine("✅ Found working endpoints! Update BuildConfig.BASE_URL to use:")
                successfulEndpoints.forEach { result ->
                    val baseUrl = result.url.substringBefore("/api/devices/register/") + "/"
                    report.appendLine("  const val BASE_URL = \"$baseUrl\"")
                }
            }
            workingEndpoints.isNotEmpty() -> {
                report.appendLine("⚠️ Found partially working endpoints. These may work with POST requests:")
                workingEndpoints.forEach { result ->
                    val baseUrl = result.url.substringBefore("/api/devices/register/") + "/"
                    report.appendLine("  const val BASE_URL = \"$baseUrl\" // HTTP ${result.httpCode}")
                }
            }
            else -> {
                report.appendLine("❌ No working endpoints found. Check:")
                report.appendLine("  1. Internet connection")
                report.appendLine("  2. Server is online")
                report.appendLine("  3. Correct domain name")
                report.appendLine("  4. Firewall/proxy settings")
                report.appendLine("  5. SSL certificate configuration")
            }
        }
        
        report.toString()
    }
}

data class DiagnosticResult(
    val url: String,
    val status: EndpointStatus,
    val httpCode: Int?,
    val message: String,
    val responsePreview: String?,
    val contentType: String?,
    val exception: Exception? = null
)

enum class EndpointStatus {
    SUCCESS,        // 2xx responses
    CLIENT_ERROR,   // 4xx responses (endpoint exists but may need different method)
    SERVER_ERROR,   // 5xx responses (endpoint exists but server has issues)
    NETWORK_ERROR,  // Connection issues, DNS issues, timeouts
    SSL_ERROR       // SSL/Certificate issues
}