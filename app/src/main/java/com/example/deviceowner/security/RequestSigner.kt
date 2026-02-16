package com.example.deviceowner.security

import android.util.Log
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Request signer for HMAC-SHA256 signing of API requests.
 * Prevents request tampering and replay attacks.
 *
 * Signature includes:
 * - Request body (JSON)
 * - Timestamp (prevents replay)
 * - Device ID (prevents cross-device attacks)
 * - Nonce (prevents duplicate requests)
 */
object RequestSigner {
    private const val TAG = "RequestSigner"
    
    private const val ALGORITHM = "HmacSHA256"
    private const val CHARSET = "UTF-8"
    
    /**
     * Sign a request with HMAC-SHA256.
     * Returns signature header value.
     */
    fun signRequest(
        requestBody: String,
        apiKey: String,
        deviceId: String,
        timestamp: Long = System.currentTimeMillis(),
        nonce: String = generateNonce()
    ): String? {
        return try {
            // Create signing payload: body|timestamp|deviceId|nonce
            val payload = "$requestBody|$timestamp|$deviceId|$nonce"
            
            // Create HMAC-SHA256 signature
            val signature = hmacSha256(payload, apiKey)
            
            // Return header format: signature:timestamp:nonce
            val headerValue = "$signature:$timestamp:$nonce"
            Log.d(TAG, "✅ Request signed: ${signature.take(16)}...")
            headerValue
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to sign request: ${e.message}", e)
            null
        }
    }
    
    /**
     * Verify a response signature.
     * Returns true if signature is valid and timestamp is recent.
     */
    fun verifyResponse(
        responseBody: String,
        signature: String,
        apiKey: String,
        maxAgeMs: Long = 300_000L // 5 minutes
    ): Boolean {
        return try {
            // Parse signature header: signature:timestamp:nonce
            val parts = signature.split(":")
            if (parts.size != 3) {
                Log.w(TAG, "❌ Invalid signature format")
                return false
            }
            
            val (receivedSignature, timestampStr, nonce) = parts
            val timestamp = timestampStr.toLongOrNull() ?: return false
            
            // Check timestamp freshness
            val age = System.currentTimeMillis() - timestamp
            if (age > maxAgeMs) {
                Log.w(TAG, "❌ Response signature too old: ${age}ms")
                return false
            }
            
            // Recreate signature
            val payload = "$responseBody|$timestamp|$nonce"
            val expectedSignature = hmacSha256(payload, apiKey)
            
            // Compare signatures (constant-time comparison to prevent timing attacks)
            val isValid = constantTimeEquals(receivedSignature, expectedSignature)
            
            if (isValid) {
                Log.d(TAG, "✅ Response signature verified")
            } else {
                Log.w(TAG, "❌ Response signature mismatch")
            }
            
            isValid
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to verify response: ${e.message}", e)
            false
        }
    }
    
    /**
     * Generate HMAC-SHA256 signature.
     */
    private fun hmacSha256(data: String, key: String): String {
        return try {
            val mac = Mac.getInstance(ALGORITHM)
            val secretKey = SecretKeySpec(
                key.toByteArray(StandardCharsets.UTF_8),
                0,
                key.length,
                ALGORITHM
            )
            mac.init(secretKey)
            val signature = mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
            
            // Return Base64-encoded signature
            Base64.getEncoder().encodeToString(signature)
        } catch (e: Exception) {
            Log.e(TAG, "❌ HMAC-SHA256 failed: ${e.message}", e)
            ""
        }
    }
    
    /**
     * Constant-time string comparison to prevent timing attacks.
     */
    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        
        return result == 0
    }
    
    /**
     * Generate a random nonce for request uniqueness.
     */
    private fun generateNonce(): String {
        return (0..15).map { (0..255).random().toChar() }
            .joinToString("")
            .let { Base64.getEncoder().encodeToString(it.toByteArray()) }
            .take(16)
    }
    
    /**
     * Create signature header for OkHttp interceptor.
     */
    fun createSignatureHeader(
        requestBody: String,
        apiKey: String,
        deviceId: String
    ): Pair<String, String>? {
        val signature = signRequest(requestBody, apiKey, deviceId)
        return if (signature != null) {
            Pair("X-Device-Signature", signature)
        } else {
            null
        }
    }
}
