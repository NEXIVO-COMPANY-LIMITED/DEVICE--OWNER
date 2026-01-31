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
 * Utility to test API endpoints and diagnose connection issues
 * Use this to troubleshoot the "server returned HTML" error
 */
class ApiEndpointTester(private val context: Context) {
    
    companion object {
        private const val TAG = "ApiEndpointTester"
    }
    
    /**
     * Test the registration endpoint and provide detailed diagnostics
     */
    suspend fun testRegistrationEndpoint(): String = withContext(Dispatchers.IO) {
        val report = StringBuilder()
        report.appendLine("🔍 API ENDPOINT DIAGNOSTIC REPORT")
        report.appendLine("=" * 50)
        report.appendLine()
        
        val baseUrl = BuildConfig.BASE_URL
        val fullUrl = baseUrl + ApiEndpoints.REGISTER_DEVICE
        
        report.appendLine("📡 Testing URL: $fullUrl")
        report.appendLine("📡 Base URL: $baseUrl")
        report.appendLine("📡 Endpoint: ${ApiEndpoints.REGISTER_DEVICE}")
        report.appendLine()
        
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
        
        // Test 1: HEAD request to base URL
        report.appendLine("🧪 TEST 1: Base URL connectivity")
        try {
            val baseRequest = Request.Builder()
                .url(baseUrl)
                .head()
                .build()
            
            val baseResponse = client.newCall(baseRequest).execute()
            report.appendLine("   ✅ Base URL Status: ${baseResponse.code}")
            report.appendLine("   ✅ Base URL Headers: ${baseResponse.headers}")
            baseResponse.close()
        } catch (e: Exception) {
            report.appendLine("   ❌ Base URL Error: ${e.message}")
            report.appendLine("   ❌ Error Type: ${e.javaClass.simpleName}")
        }
        report.appendLine()
        
        // Test 2: GET request to registration endpoint
        report.appendLine("🧪 TEST 2: Registration endpoint GET request")
        try {
            val getRequest = Request.Builder()
                .url(fullUrl)
                .get()
                .build()
            
            val getResponse = client.newCall(getRequest).execute()
            val responseBody = getResponse.body?.string() ?: "No body"
            
            report.appendLine("   📊 GET Status: ${getResponse.code}")
            report.appendLine("   📊 GET Headers: ${getResponse.headers}")
            report.appendLine("   📊 Content-Type: ${getResponse.header("Content-Type") ?: "Not specified"}")
            report.appendLine("   📊 Response Length: ${responseBody.length} characters")
            
            // Analyze response content
            when {
                responseBody.trim().startsWith("<") || responseBody.contains("<html", ignoreCase = true) -> {
                    report.appendLine("   🚨 PROBLEM DETECTED: Server returned HTML instead of JSON!")
                    report.appendLine("   🚨 This is the cause of your ParameterizedType error!")
                    
                    // Extract title from HTML if possible
                    val titleMatch = Regex("<title[^>]*>(.*?)</title>", RegexOption.IGNORE_CASE).find(responseBody)
                    titleMatch?.let {
                        report.appendLine("   📄 HTML Title: ${it.groupValues[1].trim()}")
                    }
                    
                    // Show first 300 characters of HTML
                    report.appendLine("   📄 HTML Preview:")
                    report.appendLine("   " + responseBody.take(300).replace("\n", "\n   "))
                    if (responseBody.length > 300) {
                        report.appendLine("   ... (truncated)")
                    }
                }
                responseBody.trim().startsWith("{") || responseBody.trim().startsWith("[") -> {
                    report.appendLine("   ✅ Response appears to be JSON")
                    report.appendLine("   📄 JSON Preview:")
                    report.appendLine("   " + responseBody.take(200).replace("\n", "\n   "))
                }
                responseBody.isBlank() -> {
                    report.appendLine("   ⚠️ Empty response body")
                }
                else -> {
                    report.appendLine("   ❓ Unknown response format")
                    report.appendLine("   📄 Content Preview:")
                    report.appendLine("   " + responseBody.take(200).replace("\n", "\n   "))
                }
            }
            
            getResponse.close()
        } catch (e: Exception) {
            report.appendLine("   ❌ GET Request Error: ${e.message}")
            report.appendLine("   ❌ Error Type: ${e.javaClass.simpleName}")
        }
        report.appendLine()
        
        // Test 3: OPTIONS request (check CORS and allowed methods)
        report.appendLine("🧪 TEST 3: OPTIONS request (CORS check)")
        try {
            val optionsRequest = Request.Builder()
                .url(fullUrl)
                .method("OPTIONS", null)
                .build()
            
            val optionsResponse = client.newCall(optionsRequest).execute()
            report.appendLine("   📊 OPTIONS Status: ${optionsResponse.code}")
            report.appendLine("   📊 Allowed Methods: ${optionsResponse.header("Allow") ?: "Not specified"}")
            report.appendLine("   📊 CORS Headers: ${optionsResponse.header("Access-Control-Allow-Methods") ?: "Not specified"}")
            optionsResponse.close()
        } catch (e: Exception) {
            report.appendLine("   ❌ OPTIONS Error: ${e.message}")
        }
        report.appendLine()
        
        // Test 4: DNS resolution
        report.appendLine("🧪 TEST 4: DNS Resolution")
        try {
            val host = java.net.URL(baseUrl).host
            val addresses = java.net.InetAddress.getAllByName(host)
            report.appendLine("   ✅ Host: $host")
            report.appendLine("   ✅ IP Addresses:")
            addresses.forEach { addr ->
                report.appendLine("     - ${addr.hostAddress}")
            }
        } catch (e: Exception) {
            report.appendLine("   ❌ DNS Error: ${e.message}")
        }
        report.appendLine()
        
        // Recommendations
        report.appendLine("💡 RECOMMENDATIONS:")
        report.appendLine("=" * 30)
        
        if (report.toString().contains("Server returned HTML")) {
            report.appendLine("🔧 CRITICAL: Your API endpoint is returning HTML instead of JSON!")
            report.appendLine("   Possible causes:")
            report.appendLine("   1. Wrong URL - the endpoint doesn't exist (404 error page)")
            report.appendLine("   2. Server error - internal server error (500 error page)")
            report.appendLine("   3. Authentication required - login page being returned")
            report.appendLine("   4. Maintenance mode - maintenance page being shown")
            report.appendLine()
            report.appendLine("🔧 SOLUTIONS:")
            report.appendLine("   1. Verify the correct API URL with your backend team")
            report.appendLine("   2. Check if the server is running and accessible")
            report.appendLine("   3. Test the endpoint with Postman or curl")
            report.appendLine("   4. Check server logs for errors")
        }
        
        if (report.toString().contains("DNS Error") || report.toString().contains("Unable to resolve host")) {
            report.appendLine("🔧 DNS/Network Issue:")
            report.appendLine("   1. Check internet connection")
            report.appendLine("   2. Verify the domain name is correct")
            report.appendLine("   3. Try accessing the URL in a web browser")
        }
        
        report.appendLine()
        report.appendLine("📋 Report generated at: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())}")
        
        val fullReport = report.toString()
        Log.d(TAG, fullReport)
        return@withContext fullReport
    }
    
    /**
     * Test a custom URL endpoint
     */
    suspend fun testCustomEndpoint(url: String): String = withContext(Dispatchers.IO) {
        val report = StringBuilder()
        report.appendLine("🔍 CUSTOM ENDPOINT TEST: $url")
        report.appendLine("=" * 50)
        
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
        
        try {
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: "No body"
            
            report.appendLine("Status: ${response.code}")
            report.appendLine("Headers: ${response.headers}")
            report.appendLine("Content-Type: ${response.header("Content-Type") ?: "Not specified"}")
            report.appendLine("Response Length: ${responseBody.length} characters")
            report.appendLine()
            report.appendLine("Response Preview:")
            report.appendLine(responseBody.take(500))
            
            response.close()
        } catch (e: Exception) {
            report.appendLine("Error: ${e.message}")
            report.appendLine("Error Type: ${e.javaClass.simpleName}")
        }
        
        val fullReport = report.toString()
        Log.d(TAG, fullReport)
        return@withContext fullReport
    }
}

private operator fun String.times(n: Int): String = this.repeat(n)