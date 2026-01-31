package com.example.deviceowner.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import com.example.deviceowner.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Network diagnostics specifically for Device Owner mode
 * Helps diagnose SSL/HTTPS connectivity issues that cause ClassCastException
 */
class DeviceOwnerNetworkDiagnostic(private val context: Context) {
    
    companion object {
        private const val TAG = "DeviceOwnerNetworkDiag"
    }
    
    /**
     * Comprehensive network diagnostic for Device Owner mode
     */
    suspend fun runDeviceOwnerNetworkDiagnostic(): DeviceOwnerNetworkResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "🔍 Running Device Owner Network Diagnostic...")
        
        val result = DeviceOwnerNetworkResult()
        
        // 1. Check basic connectivity
        result.hasInternetConnection = checkInternetConnection()
        Log.d(TAG, "Internet connection: ${result.hasInternetConnection}")
        
        // 2. Check if we can reach payoplan.com via HTTPS
        result.canReachPayoplan = testHttpsConnection("https://payoplan.com")
        Log.d(TAG, "Can reach payoplan.com: ${result.canReachPayoplan}")
        
        // 3. Test the specific registration endpoint
        result.canReachRegistrationEndpoint = testRegistrationEndpoint()
        Log.d(TAG, "Can reach registration endpoint: ${result.canReachRegistrationEndpoint}")
        
        // 4. Check SSL certificate validity
        result.sslCertificateValid = checkSslCertificate()
        Log.d(TAG, "SSL certificate valid: ${result.sslCertificateValid}")
        
        // 5. Check Device Owner network restrictions
        result.deviceOwnerRestrictionsActive = checkDeviceOwnerRestrictions()
        Log.d(TAG, "Device Owner restrictions active: ${result.deviceOwnerRestrictionsActive}")
        
        // 6. Generate recommendations
        result.recommendations = generateRecommendations(result)
        
        Log.d(TAG, "✅ Device Owner Network Diagnostic completed")
        result
    }
    
    private fun checkInternetConnection(): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return false
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo
                networkInfo?.isConnected == true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking internet connection: ${e.message}")
            false
        }
    }
    
    private fun testHttpsConnection(url: String): Boolean {
        return try {
            val connection = URL(url).openConnection() as HttpsURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "DeviceOwner-NetworkDiagnostic")
            
            val responseCode = connection.responseCode
            Log.d(TAG, "HTTPS test to $url: HTTP $responseCode")
            
            connection.disconnect()
            responseCode in 200..399
        } catch (e: Exception) {
            Log.e(TAG, "HTTPS connection test failed for $url: ${e.message}")
            false
        }
    }
    
    private fun testRegistrationEndpoint(): Boolean {
        return try {
            val url = "${BuildConfig.BASE_URL}api/devices/mobile/register/"
            Log.d(TAG, "Testing registration endpoint: $url")
            
            val connection = URL(url).openConnection() as HttpsURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "DeviceOwner-NetworkDiagnostic")
            
            // Send empty POST to test endpoint availability
            connection.doOutput = true
            connection.outputStream.use { it.write("{}".toByteArray()) }
            
            val responseCode = connection.responseCode
            Log.d(TAG, "Registration endpoint test: HTTP $responseCode")
            
            // Even 400/422 is good - means endpoint exists and is processing requests
            val endpointExists = responseCode in 200..499
            
            connection.disconnect()
            endpointExists
        } catch (e: Exception) {
            Log.e(TAG, "Registration endpoint test failed: ${e.message}")
            false
        }
    }
    
    private fun checkSslCertificate(): Boolean {
        return try {
            val connection = URL("https://payoplan.com").openConnection() as HttpsURLConnection
            connection.connectTimeout = 10000
            connection.connect()
            
            val certificates = connection.serverCertificates
            val certificateValid = certificates.isNotEmpty()
            
            Log.d(TAG, "SSL certificates found: ${certificates.size}")
            certificates.forEachIndexed { index, cert ->
                Log.d(TAG, "Certificate $index: ${cert.type}")
            }
            
            connection.disconnect()
            certificateValid
        } catch (e: Exception) {
            Log.e(TAG, "SSL certificate check failed: ${e.message}")
            false
        }
    }
    
    private fun checkDeviceOwnerRestrictions(): Boolean {
        return try {
            val deviceOwnerManager = com.example.deviceowner.device.DeviceOwnerManager(context)
            val isDeviceOwner = deviceOwnerManager.isDeviceOwner()
            
            if (isDeviceOwner) {
                Log.d(TAG, "Device Owner mode detected - checking network restrictions")
                // Check if registration is completed to see if restrictions should be active
                val isRegistered = context.getSharedPreferences("device_registration", Context.MODE_PRIVATE)
                    .getBoolean("registration_completed", false)
                Log.d(TAG, "Registration completed: $isRegistered")
                isRegistered
            } else {
                Log.d(TAG, "Not in Device Owner mode")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Device Owner restrictions: ${e.message}")
            false
        }
    }
    
    private fun generateRecommendations(result: DeviceOwnerNetworkResult): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (!result.hasInternetConnection) {
            recommendations.add("❌ No internet connection - check WiFi/mobile data")
        }
        
        if (!result.canReachPayoplan) {
            recommendations.add("❌ Cannot reach payoplan.com - check DNS or firewall")
        }
        
        if (!result.canReachRegistrationEndpoint) {
            recommendations.add("❌ Registration endpoint not reachable - verify API is online")
        }
        
        if (!result.sslCertificateValid) {
            recommendations.add("❌ SSL certificate issues - check server SSL configuration")
        }
        
        if (result.deviceOwnerRestrictionsActive) {
            recommendations.add("⚠️ Device Owner restrictions active - this may affect network access")
        }
        
        if (recommendations.isEmpty()) {
            recommendations.add("✅ All network checks passed - registration should work")
        }
        
        return recommendations
    }
}

/**
 * Result of Device Owner network diagnostic
 */
data class DeviceOwnerNetworkResult(
    var hasInternetConnection: Boolean = false,
    var canReachPayoplan: Boolean = false,
    var canReachRegistrationEndpoint: Boolean = false,
    var sslCertificateValid: Boolean = false,
    var deviceOwnerRestrictionsActive: Boolean = false,
    var recommendations: List<String> = emptyList()
) {
    val allChecksPass: Boolean
        get() = hasInternetConnection && canReachPayoplan && canReachRegistrationEndpoint && sslCertificateValid
}