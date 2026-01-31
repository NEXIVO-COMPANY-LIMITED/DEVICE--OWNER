package com.example.deviceowner.utils

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import java.net.URL

/**
 * SSL Certificate extractor and validator for Device Owner security
 * Extracts certificate pins and validates SSL configuration
 */
class SSLCertificateExtractor {
    
    companion object {
        private const val TAG = "SSLCertExtractor"
    }
    
    /**
     * Extract SSL certificate information from payoplan.com
     * This is the MAIN method to get certificate pins for your configuration
     */
    suspend fun extractCertificateInfo(domain: String = "payoplan.com"): CertificateInfo = withContext(Dispatchers.IO) {
        Log.d(TAG, "🔍 Extracting SSL certificate info for: $domain")
        
        try {
            val url = URL("https://$domain")
            val connection = url.openConnection() as HttpsURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.connect()
            
            val certificates = connection.serverCertificates
            Log.d(TAG, "✅ Found ${certificates.size} certificates")
            
            val certificateInfo = CertificateInfo()
            
            certificates.forEachIndexed { index, cert ->
                if (cert is X509Certificate) {
                    Log.d(TAG, "📜 Certificate $index:")
                    Log.d(TAG, "   Subject: ${cert.subjectDN}")
                    Log.d(TAG, "   Issuer: ${cert.issuerDN}")
                    Log.d(TAG, "   Valid from: ${cert.notBefore}")
                    Log.d(TAG, "   Valid until: ${cert.notAfter}")
                    
                    // Calculate SHA-256 pin
                    val sha256Pin = calculateSHA256Pin(cert)
                    Log.d(TAG, "   SHA-256 Pin: $sha256Pin")
                    
                    if (index == 0) { // Leaf certificate
                        certificateInfo.leafCertificatePin = sha256Pin
                        certificateInfo.subject = cert.subjectDN.toString()
                        certificateInfo.issuer = cert.issuerDN.toString()
                        certificateInfo.validFrom = cert.notBefore.toString()
                        certificateInfo.validUntil = cert.notAfter.toString()
                        
                        // IMPORTANT: Log the configuration code you need to copy
                        Log.d(TAG, "")
                        Log.d(TAG, "🔑 COPY THIS CERTIFICATE PIN TO YOUR CONFIGURATION:")
                        Log.d(TAG, "")
                        Log.d(TAG, "For network_security_config.xml:")
                        Log.d(TAG, "<pin digest=\"SHA-256\">$sha256Pin</pin>")
                        Log.d(TAG, "")
                        Log.d(TAG, "For ApiClient.kt:")
                        Log.d(TAG, ".add(\"$domain\", \"sha256/$sha256Pin\")")
                        Log.d(TAG, "")
                    }
                    
                    certificateInfo.allPins.add(sha256Pin)
                }
            }
            
            connection.disconnect()
            
            Log.d(TAG, "✅ Certificate extraction completed")
            certificateInfo
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to extract certificate info: ${e.message}", e)
            CertificateInfo(error = e.message)
        }
    }
    
    /**
     * Calculate SHA-256 pin for certificate pinning
     */
    private fun calculateSHA256Pin(certificate: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val publicKeyBytes = certificate.publicKey.encoded
        val hash = digest.digest(publicKeyBytes)
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }
    
    /**
     * Quick method to get certificate pin for configuration
     * Returns the SHA-256 pin ready to use in your config files
     */
    suspend fun getCertificatePinForConfiguration(domain: String = "payoplan.com"): String = withContext(Dispatchers.IO) {
        try {
            val certInfo = extractCertificateInfo(domain)
            if (certInfo.error != null) {
                Log.e(TAG, "❌ Error getting certificate pin: ${certInfo.error}")
                return@withContext ""
            }
            
            val pin = certInfo.leafCertificatePin
            if (pin.isNotEmpty()) {
                Log.d(TAG, "")
                Log.d(TAG, "🎯 CERTIFICATE PIN READY FOR CONFIGURATION:")
                Log.d(TAG, "")
                Log.d(TAG, "📋 Copy this pin: $pin")
                Log.d(TAG, "")
                Log.d(TAG, "🔧 Update network_security_config.xml:")
                Log.d(TAG, "<pin digest=\"SHA-256\">$pin</pin>")
                Log.d(TAG, "")
                Log.d(TAG, "🔧 Update ApiClient.kt:")
                Log.d(TAG, ".add(\"$domain\", \"sha256/$pin\")")
                Log.d(TAG, "")
            }
            
            return@withContext pin
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to get certificate pin: ${e.message}", e)
            return@withContext ""
        }
    }

    /**
     * Validate current SSL configuration
     */
    suspend fun validateSSLConfiguration(domain: String = "payoplan.com"): SSLValidationResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "🔍 Validating SSL configuration for: $domain")
        
        val result = SSLValidationResult()
        
        try {
            val url = URL("https://$domain")
            val connection = url.openConnection() as HttpsURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            // Test connection
            connection.connect()
            result.connectionSuccessful = true
            result.responseCode = connection.responseCode
            
            // Check certificates
            val certificates = connection.serverCertificates
            result.certificateCount = certificates.size
            result.hasCertificates = certificates.isNotEmpty()
            
            // Check TLS version
            result.tlsVersion = connection.cipherSuite
            
            // Check if certificate is valid
            if (certificates.isNotEmpty() && certificates[0] is X509Certificate) {
                val cert = certificates[0] as X509Certificate
                val now = System.currentTimeMillis()
                result.certificateValid = now >= cert.notBefore.time && now <= cert.notAfter.time
                result.certificateExpiry = cert.notAfter.toString()
            }
            
            connection.disconnect()
            
            Log.d(TAG, "✅ SSL validation completed successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ SSL validation failed: ${e.message}", e)
            result.error = e.message
        }
        
        result
    }
}

/**
 * Certificate information extracted from server
 */
data class CertificateInfo(
    var leafCertificatePin: String = "",
    var subject: String = "",
    var issuer: String = "",
    var validFrom: String = "",
    var validUntil: String = "",
    var allPins: MutableList<String> = mutableListOf(),
    var error: String? = null
)

/**
 * SSL validation result
 */
data class SSLValidationResult(
    var connectionSuccessful: Boolean = false,
    var responseCode: Int = 0,
    var certificateCount: Int = 0,
    var hasCertificates: Boolean = false,
    var certificateValid: Boolean = false,
    var certificateExpiry: String = "",
    var tlsVersion: String = "",
    var error: String? = null
) {
    val isValid: Boolean
        get() = connectionSuccessful && hasCertificates && certificateValid && error == null
}