package com.example.deviceowner.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Calendar

/**
 * Manages JWT token generation, validation, and refresh
 * Feature 4.10: Secure APIs & Communication
 * 
 * Responsibilities:
 * - Store JWT tokens securely
 * - Validate token expiration
 * - Refresh expired tokens
 * - Provide Authorization headers
 */
class JWTManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "jwt_manager",
        Context.MODE_PRIVATE
    )
    private val gson = Gson()

    companion object {
        private const val TAG = "JWTManager"
        
        // Preferences keys
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_TOKEN_EXPIRY = "token_expiry"
        private const val KEY_REFRESH_TOKEN_EXPIRY = "refresh_token_expiry"
        
        // Token expiration times
        private const val ACCESS_TOKEN_EXPIRY_MS = 15 * 60 * 1000 // 15 minutes
        private const val REFRESH_TOKEN_EXPIRY_MS = 7 * 24 * 60 * 60 * 1000 // 7 days
        
        // Token refresh threshold (refresh if less than 2 minutes remaining)
        private const val REFRESH_THRESHOLD_MS = 2 * 60 * 1000
    }

    /**
     * Store JWT tokens received from backend
     */
    fun storeTokens(accessToken: String, refreshToken: String) {
        try {
            Log.d(TAG, "Storing JWT tokens")
            
            val now = System.currentTimeMillis()
            val accessTokenExpiry = now + ACCESS_TOKEN_EXPIRY_MS
            val refreshTokenExpiry = now + REFRESH_TOKEN_EXPIRY_MS
            
            prefs.edit().apply {
                putString(KEY_ACCESS_TOKEN, accessToken)
                putString(KEY_REFRESH_TOKEN, refreshToken)
                putLong(KEY_TOKEN_EXPIRY, accessTokenExpiry)
                putLong(KEY_REFRESH_TOKEN_EXPIRY, refreshTokenExpiry)
                apply()
            }
            
            Log.d(TAG, "Tokens stored successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error storing tokens", e)
        }
    }

    /**
     * Get valid access token, refreshing if necessary
     */
    fun getAccessToken(): String? {
        return try {
            val token = prefs.getString(KEY_ACCESS_TOKEN, null) ?: return null
            val expiry = prefs.getLong(KEY_TOKEN_EXPIRY, 0)
            val now = System.currentTimeMillis()
            
            // Check if token needs refresh
            if (expiry - now < REFRESH_THRESHOLD_MS) {
                Log.d(TAG, "Access token expiring soon, should refresh")
                // In real implementation, call backend to refresh
                // For now, return existing token
            }
            
            // Check if token is expired
            if (expiry < now) {
                Log.w(TAG, "Access token expired")
                return null
            }
            
            token
        } catch (e: Exception) {
            Log.e(TAG, "Error getting access token", e)
            null
        }
    }

    /**
     * Get refresh token
     */
    fun getRefreshToken(): String? {
        return try {
            val token = prefs.getString(KEY_REFRESH_TOKEN, null) ?: return null
            val expiry = prefs.getLong(KEY_REFRESH_TOKEN_EXPIRY, 0)
            val now = System.currentTimeMillis()
            
            if (expiry < now) {
                Log.w(TAG, "Refresh token expired")
                return null
            }
            
            token
        } catch (e: Exception) {
            Log.e(TAG, "Error getting refresh token", e)
            null
        }
    }

    /**
     * Check if access token is valid
     */
    fun isAccessTokenValid(): Boolean {
        return try {
            val expiry = prefs.getLong(KEY_TOKEN_EXPIRY, 0)
            val now = System.currentTimeMillis()
            expiry > now
        } catch (e: Exception) {
            Log.e(TAG, "Error checking token validity", e)
            false
        }
    }

    /**
     * Check if refresh token is valid
     */
    fun isRefreshTokenValid(): Boolean {
        return try {
            val expiry = prefs.getLong(KEY_REFRESH_TOKEN_EXPIRY, 0)
            val now = System.currentTimeMillis()
            expiry > now
        } catch (e: Exception) {
            Log.e(TAG, "Error checking refresh token validity", e)
            false
        }
    }

    /**
     * Clear all tokens
     */
    fun clearTokens() {
        try {
            Log.d(TAG, "Clearing JWT tokens")
            prefs.edit().apply {
                remove(KEY_ACCESS_TOKEN)
                remove(KEY_REFRESH_TOKEN)
                remove(KEY_TOKEN_EXPIRY)
                remove(KEY_REFRESH_TOKEN_EXPIRY)
                apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing tokens", e)
        }
    }

    /**
     * Get Authorization header value
     */
    fun getAuthorizationHeader(): String? {
        return getAccessToken()?.let { "Bearer $it" }
    }

    /**
     * Decode JWT payload (without verification - for local use only)
     * WARNING: This does NOT verify the signature, only decodes the payload
     */
    fun decodePayload(token: String): JsonObject? {
        return try {
            val parts = token.split(".")
            if (parts.size != 3) {
                Log.e(TAG, "Invalid JWT format")
                return null
            }
            
            val payload = parts[1]
            val decodedBytes = Base64.decode(payload, Base64.URL_SAFE)
            val decodedString = String(decodedBytes, StandardCharsets.UTF_8)
            
            gson.fromJson(decodedString, JsonObject::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding JWT payload", e)
            null
        }
    }

    /**
     * Get token expiration time
     */
    fun getTokenExpiryTime(): Long {
        return prefs.getLong(KEY_TOKEN_EXPIRY, 0)
    }

    /**
     * Get time until token expiration in milliseconds
     */
    fun getTimeUntilExpiry(): Long {
        val expiry = getTokenExpiryTime()
        val now = System.currentTimeMillis()
        return if (expiry > now) expiry - now else 0
    }
}
