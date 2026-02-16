package com.example.deviceowner.utils.ssl

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

    suspend fun extractCertificateInfo(domain: String = "payoplan.com"): CertificateInfo = withContext(Dispatchers.IO) {
        Log.d(TAG, "Extracting SSL certificate info for: $domain")
        try {
            val url = URL("https://$domain")
            val connection = url.openConnection() as HttpsURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.connect()
            val certificates = connection.serverCertificates
            Log.d(TAG, "Found ${certificates.size} certificates")
            val certificateInfo = CertificateInfo()
            certificates.forEachIndexed { index, cert ->
                if (cert is X509Certificate) {
                    val sha256Pin = calculateSHA256Pin(cert)
                    if (index == 0) {
                        certificateInfo.leafCertificatePin = sha256Pin
                        certificateInfo.subject = cert.subjectDN.toString()
                        certificateInfo.issuer = cert.issuerDN.toString()
                        certificateInfo.validFrom = cert.notBefore.toString()
                        certificateInfo.validUntil = cert.notAfter.toString()
                    }
                    certificateInfo.allPins.add(sha256Pin)
                }
            }
            connection.disconnect()
            Log.d(TAG, "Certificate extraction completed")
            certificateInfo
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract certificate info: ${e.message}", e)
            CertificateInfo(error = e.message)
        }
    }

    private fun calculateSHA256Pin(certificate: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val publicKeyBytes = certificate.publicKey.encoded
        val hash = digest.digest(publicKeyBytes)
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    suspend fun getCertificatePinForConfiguration(domain: String = "payoplan.com"): String = withContext(Dispatchers.IO) {
        try {
            val certInfo = extractCertificateInfo(domain)
            if (certInfo.error != null) return@withContext ""
            return@withContext certInfo.leafCertificatePin
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get certificate pin: ${e.message}", e)
            return@withContext ""
        }
    }

    suspend fun validateSSLConfiguration(domain: String = "payoplan.com"): SSLValidationResult = withContext(Dispatchers.IO) {
        val result = SSLValidationResult()
        try {
            val url = URL("https://$domain")
            val connection = url.openConnection() as HttpsURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.connect()
            result.connectionSuccessful = true
            result.responseCode = connection.responseCode
            val certificates = connection.serverCertificates
            result.certificateCount = certificates.size
            result.hasCertificates = certificates.isNotEmpty()
            result.tlsVersion = connection.cipherSuite
            if (certificates.isNotEmpty() && certificates[0] is X509Certificate) {
                val cert = certificates[0] as X509Certificate
                val now = System.currentTimeMillis()
                result.certificateValid = now >= cert.notBefore.time && now <= cert.notAfter.time
                result.certificateExpiry = cert.notAfter.toString()
            }
            connection.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "SSL validation failed: ${e.message}", e)
            result.error = e.message
        }
        result
    }
}

data class CertificateInfo(
    var leafCertificatePin: String = "",
    var subject: String = "",
    var issuer: String = "",
    var validFrom: String = "",
    var validUntil: String = "",
    var allPins: MutableList<String> = mutableListOf(),
    var error: String? = null
)

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
