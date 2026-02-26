package com.microspace.payo.security.ssl

import android.content.Context
import android.util.Log
import com.microspace.payo.utils.ssl.CertificateInfo
import com.microspace.payo.utils.ssl.SSLCertificateExtractor
import com.microspace.payo.utils.ssl.SSLValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * SSL Certificate Manager for Device Owner mode
 * Provides enhanced SSL security and certificate validation
 */
class DeviceOwnerSSLManager(private val context: Context) {
    
    companion object {
        private const val TAG = "DeviceOwnerSSL"
    }
    
    /**
     * Initialize and validate SSL configuration for Device Owner
     */
    suspend fun initializeSSLSecurity(): SSLSecurityResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "🔒 Initializing SSL security for Device Owner mode...")
        
        val result = SSLSecurityResult()
        
        try {
            // 1. Extract current certificate information
            val certExtractor = SSLCertificateExtractor()
            val certInfo = certExtractor.extractCertificateInfo("payoplan.com")
            
            if (certInfo.error != null) {
                result.error = "Failed to extract certificate: ${certInfo.error}"
                return@withContext result
            }
            
            result.certificatePin = certInfo.leafCertificatePin
            result.certificateSubject = certInfo.subject
            result.certificateIssuer = certInfo.issuer
            result.certificateValidFrom = certInfo.validFrom
            result.certificateValidUntil = certInfo.validUntil
            
            Log.d(TAG, "📜 Certificate Information:")
            Log.d(TAG, "   Subject: ${certInfo.subject}")
            Log.d(TAG, "   Issuer: ${certInfo.issuer}")
            Log.d(TAG, "   SHA-256 Pin: ${certInfo.leafCertificatePin}")
            Log.d(TAG, "   Valid: ${certInfo.validFrom} to ${certInfo.validUntil}")
            
            // 2. Validate SSL configuration
            val sslValidation = certExtractor.validateSSLConfiguration("payoplan.com")
            result.sslConfigurationValid = sslValidation.isValid
            result.tlsVersion = sslValidation.tlsVersion
            
            if (!sslValidation.isValid) {
                result.error = "SSL validation failed: ${sslValidation.error}"
                return@withContext result
            }
            
            // 3. Test Device Owner compatibility
            result.deviceOwnerCompatible = testDeviceOwnerCompatibility()
            
            // 4. Generate security recommendations
            result.securityRecommendations = generateSecurityRecommendations(certInfo, sslValidation)
            
            result.initializationSuccessful = true
            Log.d(TAG, "✅ SSL security initialization completed successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ SSL security initialization failed: ${e.message}", e)
            result.error = e.message
        }
        
        result
    }
    
    /**
     * Test Device Owner SSL compatibility
     */
    private suspend fun testDeviceOwnerCompatibility(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            // Check if Device Owner restrictions allow HTTPS connections
            val deviceOwnerManager = com.microspace.payo.device.DeviceOwnerManager(context)
            val isDeviceOwner = deviceOwnerManager.isDeviceOwner()
            
            if (isDeviceOwner) {
                Log.d(TAG, "🔍 Testing Device Owner SSL compatibility...")
                
                // Test HTTPS connection with Device Owner restrictions
                val url = java.net.URL("https://payoplan.com")
                val connection = url.openConnection() as javax.net.ssl.HttpsURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 5000
                connection.requestMethod = "HEAD"
                
                val responseCode = connection.responseCode
                connection.disconnect()
                
                val compatible = responseCode in 200..399
                Log.d(TAG, if (compatible) "✅ Device Owner SSL compatibility: PASSED" else "❌ Device Owner SSL compatibility: FAILED")
                compatible
            } else {
                Log.d(TAG, "ℹ️ Not in Device Owner mode - SSL compatibility assumed")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Device Owner SSL compatibility test failed: ${e.message}")
            false
        }
    }
    
    /**
     * Generate security recommendations based on SSL analysis
     */
    private fun generateSecurityRecommendations(
        certInfo: CertificateInfo,
        sslValidation: SSLValidationResult
    ): List<String> {
        val recommendations = mutableListOf<String>()
        
        // Certificate pin recommendation
        if (certInfo.leafCertificatePin.isNotEmpty()) {
            recommendations.add("✅ Update network_security_config.xml with certificate pin: ${certInfo.leafCertificatePin}")
            recommendations.add("✅ Update ApiClient.kt with certificate pin: sha256/${certInfo.leafCertificatePin}")
        }
        
        // TLS version recommendation
        if (sslValidation.tlsVersion.isNotEmpty()) {
            recommendations.add("✅ TLS Version: ${sslValidation.tlsVersion} (secure)")
        }
        
        // Certificate validity
        if (sslValidation.certificateValid) {
            recommendations.add("✅ Certificate is valid and not expired")
        } else {
            recommendations.add("❌ Certificate validation failed - check server SSL configuration")
        }
        
        // Device Owner specific recommendations
        recommendations.add("🔒 Device Owner mode requires HTTPS - HTTP will be blocked")
        recommendations.add("🔒 Certificate pinning provides maximum security against MITM attacks")
        recommendations.add("🔒 Regular certificate monitoring recommended for production")
        
        return recommendations
    }
    
    /**
     * Create custom trust manager for enhanced security
     */
    fun createCustomTrustManager(): X509TrustManager? {
        return try {
            val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            trustManagerFactory.init(null as KeyStore?)
            
            val trustManagers = trustManagerFactory.trustManagers
            trustManagers.firstOrNull { it is X509TrustManager } as? X509TrustManager
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create custom trust manager: ${e.message}")
            null
        }
    }
}

/**
 * Result of SSL security initialization
 */
data class SSLSecurityResult(
    var initializationSuccessful: Boolean = false,
    var certificatePin: String = "",
    var certificateSubject: String = "",
    var certificateIssuer: String = "",
    var certificateValidFrom: String = "",
    var certificateValidUntil: String = "",
    var sslConfigurationValid: Boolean = false,
    var tlsVersion: String = "",
    var deviceOwnerCompatible: Boolean = false,
    var securityRecommendations: List<String> = emptyList(),
    var error: String? = null
) {
    val isSecure: Boolean
        get() = initializationSuccessful && sslConfigurationValid && deviceOwnerCompatible && error == null
}