package com.example.deviceowner.data.models

import android.util.Base64
import com.google.gson.Gson
import java.nio.charset.StandardCharsets

/**
 * Secure command model with signature verification
 * Feature 4.10: Secure APIs & Communication
 * 
 * Represents a command from backend with cryptographic signature
 * Ensures command authenticity and prevents tampering
 */
data class SecureCommand(
    val commandId: String,
    val type: String,
    val reason: String,
    val severity: String,
    val parameters: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis(),
    val sequenceNumber: Long = 0,
    val nonce: String = "",
    val signature: String = "",
    val backendPublicKey: String = ""
) {
    
    companion object {
        private const val TAG = "SecureCommand"
        private val gson = Gson()
        
        /**
         * Create secure command from JSON response
         */
        fun fromJson(json: String): SecureCommand? {
            return try {
                gson.fromJson(json, SecureCommand::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Convert to JSON
     */
    fun toJson(): String {
        return gson.toJson(this)
    }
    
    /**
     * Get command payload for signature verification
     */
    fun getPayload(): ByteArray {
        val payload = "$commandId|$type|$timestamp|$sequenceNumber|$nonce"
        return payload.toByteArray(StandardCharsets.UTF_8)
    }
    
    /**
     * Verify command signature with backend public key
     */
    fun verifySignature(): Boolean {
        if (signature.isEmpty() || backendPublicKey.isEmpty()) {
            return false
        }
        
        return try {
            val payload = getPayload()
            val signatureBytes = Base64.decode(signature, Base64.NO_WRAP)
            val publicKeyBytes = Base64.decode(backendPublicKey, Base64.NO_WRAP)
            
            val keyFactory = java.security.KeyFactory.getInstance("RSA")
            val publicKey = keyFactory.generatePublic(
                java.security.spec.X509EncodedKeySpec(publicKeyBytes)
            )
            
            val signatureVerifier = java.security.Signature.getInstance("SHA256withRSA")
            signatureVerifier.initVerify(publicKey)
            signatureVerifier.update(payload)
            
            signatureVerifier.verify(signatureBytes)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check if command is expired
     */
    fun isExpired(maxAgeMs: Long = 5 * 60 * 1000): Boolean {
        val now = System.currentTimeMillis()
        return (now - timestamp) > maxAgeMs
    }
    
    /**
     * Check if command is valid
     */
    fun isValid(): Boolean {
        return !isExpired() && verifySignature()
    }
}

/**
 * Secure command response from backend
 */
data class SecureCommandResponse(
    val success: Boolean,
    val message: String,
    val command: SecureCommand? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val nonce: String = "",
    val signature: String = ""
) {
    
    companion object {
        private val gson = Gson()
        
        fun fromJson(json: String): SecureCommandResponse? {
            return try {
                gson.fromJson(json, SecureCommandResponse::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    fun toJson(): String {
        return gson.toJson(this)
    }
}

/**
 * Command acknowledgment with signature
 */
data class SecureCommandAcknowledgment(
    val commandId: String,
    val status: String,
    val executionTime: Long = System.currentTimeMillis(),
    val details: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val nonce: String = "",
    val signature: String = ""
) {
    
    companion object {
        private val gson = Gson()
    }
    
    fun toJson(): String {
        return gson.toJson(this)
    }
    
    /**
     * Get payload for signing
     */
    fun getPayload(): ByteArray {
        val payload = "$commandId|$status|$timestamp|$nonce"
        return payload.toByteArray(StandardCharsets.UTF_8)
    }
}

/**
 * Device authentication request
 */
data class DeviceAuthenticationRequest(
    val deviceId: String,
    val publicKey: String,
    val timestamp: Long = System.currentTimeMillis(),
    val nonce: String = "",
    val signature: String = ""
) {
    
    companion object {
        private val gson = Gson()
    }
    
    fun toJson(): String {
        return gson.toJson(this)
    }
    
    fun getPayload(): ByteArray {
        val payload = "$deviceId|$publicKey|$timestamp|$nonce"
        return payload.toByteArray(StandardCharsets.UTF_8)
    }
}

/**
 * Device authentication response
 */
data class DeviceAuthenticationResponse(
    val success: Boolean,
    val message: String,
    val accessToken: String = "",
    val refreshToken: String = "",
    val expiresIn: Long = 0,
    val backendPublicKey: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    
    companion object {
        private val gson = Gson()
        
        fun fromJson(json: String): DeviceAuthenticationResponse? {
            return try {
                gson.fromJson(json, DeviceAuthenticationResponse::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    fun toJson(): String {
        return gson.toJson(this)
    }
}
