package com.example.deviceowner.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.deviceowner.BuildConfig
import com.example.deviceowner.data.remote.ApiEndpoints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Connection Testing Utility
 * Helps diagnose network connectivity issues
 */
class ConnectionTester(private val context: Context) {
    
    companion object {
        private const val TAG = "ConnectionTester"
    }
    
    /**
     * Comprehensive connection test
     */
    suspend fun performFullConnectionTest(): ConnectionTestResult = withContext(Dispatchers.IO) {
        val results = mutableListOf<String>()
        var hasInternet = false
        var canReachServer = false
        var serverResponds = false
        
        try {
            // Test 1: Check network availability
            results.add("=== NETWORK CONNECTIVITY TEST ===")
            val networkAvailable = isNetworkAvailable()
            results.add("Network Available: $networkAvailable")
            
            if (!networkAvailable) {
                results.add("❌ No network connection detected")
                return@withContext ConnectionTestResult(false, false, false, results)
            }
            
            // Test 2: Check internet connectivity
            results.add("\n=== INTERNET CONNECTIVITY TEST ===")
            hasInternet = testInternetConnectivity()
            results.add("Internet Access: $hasInternet")
            
            if (!hasInternet) {
                results.add("❌ No internet access - check WiFi/Mobile Data")
                return@withContext ConnectionTestResult(false, false, false, results)
            }
            
            // Test 3: DNS Resolution
            results.add("\n=== DNS RESOLUTION TEST ===")
            val serverHost = URL(BuildConfig.BASE_URL).host
            canReachServer = testDnsResolution(serverHost)
            results.add("DNS Resolution for $serverHost: $canReachServer")
            
            if (!canReachServer) {
                results.add("❌ Cannot resolve server hostname - DNS issue")
                return@withContext ConnectionTestResult(hasInternet, false, false, results)
            }
            
            // Test 4: Server HTTP Response
            results.add("\n=== SERVER RESPONSE TEST ===")
            serverResponds = testServerResponse()
            results.add("Server Responds: $serverResponds")
            
            if (!serverResponds) {
                results.add("❌ Server does not respond - check server status")
            } else {
                results.add("✅ All connection tests passed")
            }
            
        } catch (e: Exception) {
            results.add("❌ Connection test failed: ${e.message}")
            Log.e(TAG, "Connection test error", e)
        }
        
        ConnectionTestResult(hasInternet, canReachServer, serverResponds, results)
    }
    
    /**
     * Check if network is available
     */
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            networkInfo?.isConnected == true
        }
    }
    
    /**
     * Test internet connectivity by pinging Google DNS
     */
    private suspend fun testInternetConnectivity(): Boolean = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
            
            val request = Request.Builder()
                .url("https://8.8.8.8")
                .head()
                .build()
            
            val response = client.newCall(request).execute()
            response.isSuccessful || response.code in 200..499 // Any response means internet works
        } catch (e: Exception) {
            Log.d(TAG, "Internet test failed: ${e.message}")
            false
        }
    }
    
    /**
     * Test DNS resolution
     */
    private suspend fun testDnsResolution(hostname: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val address = InetAddress.getByName(hostname)
            Log.d(TAG, "DNS resolved $hostname to ${address.hostAddress}")
            true
        } catch (e: Exception) {
            Log.d(TAG, "DNS resolution failed for $hostname: ${e.message}")
            false
        }
    }
    
    /**
     * Test server response
     */
    private suspend fun testServerResponse(): Boolean = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
            
            val request = Request.Builder()
                .url(BuildConfig.BASE_URL)
                .head()
                .addHeader("User-Agent", "DeviceOwner-ConnectionTest/1.0")
                .build()
            
            val response = client.newCall(request).execute()
            Log.d(TAG, "Server response: ${response.code} ${response.message}")
            Log.d(TAG, "Response headers: ${response.headers}")
            
            // Any response (even 404) means server is reachable
            response.code in 200..599
        } catch (e: Exception) {
            Log.d(TAG, "Server test failed: ${e.message}")
            false
        }
    }
    
    /**
     * Test specific API endpoint
     */
    suspend fun testApiEndpoint(): ApiEndpointTestResult = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
            
            val fullUrl = BuildConfig.BASE_URL + ApiEndpoints.REGISTER_DEVICE
            val request = Request.Builder()
                .url(fullUrl)
                .get() // Try GET first to see if endpoint exists
                .addHeader("Accept", "application/json")
                .addHeader("User-Agent", "DeviceOwner-EndpointTest/1.0")
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            
            Log.d(TAG, "API endpoint test:")
            Log.d(TAG, "  URL: $fullUrl")
            Log.d(TAG, "  Response Code: ${response.code}")
            Log.d(TAG, "  Content-Type: ${response.header("Content-Type")}")
            Log.d(TAG, "  Response Body: ${responseBody.take(200)}")
            
            ApiEndpointTestResult(
                url = fullUrl,
                responseCode = response.code,
                contentType = response.header("Content-Type") ?: "unknown",
                responsePreview = responseBody.take(200),
                isReachable = true,
                error = null
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "API endpoint test failed", e)
            ApiEndpointTestResult(
                url = BuildConfig.BASE_URL + ApiEndpoints.REGISTER_DEVICE,
                responseCode = -1,
                contentType = "error",
                responsePreview = "",
                isReachable = false,
                error = e.message
            )
        }
    }
    
    data class ConnectionTestResult(
        val hasInternet: Boolean,
        val canReachServer: Boolean,
        val serverResponds: Boolean,
        val details: List<String>
    )
    
    data class ApiEndpointTestResult(
        val url: String,
        val responseCode: Int,
        val contentType: String,
        val responsePreview: String,
        val isReachable: Boolean,
        val error: String?
    )
}