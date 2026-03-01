package com.microspace.payo.utils.helpers

import android.content.Context
import android.util.Log
import java.security.MessageDigest

/**
 * HashGenerator - Generates SHA-256 hashes for device integrity verification
 * 
 * Used by heartbeat to detect:
 * - Modified installed apps
 * - Changed system properties
 * - Tampered system files
 */
object HashGenerator {
    
    private const val TAG = "HashGenerator"
    
    /**
     * Generate SHA-256 hash of all installed apps
     * Used to detect if apps were added/removed/modified
     */
    fun generateInstalledAppsHash(context: Context): String {
        return try {
            val pm = context.packageManager
            val packages = pm.getInstalledPackages(0)
            
            // Sort package names for consistent hash
            val packageNames = packages
                .map { it.packageName }
                .sorted()
                .joinToString(",")
            
            val hash = hashString(packageNames)
            Log.d(TAG, "âœ… Apps hash generated: ${hash.take(16)}...")
            hash
        } catch (e: Exception) {
            Log.e(TAG, "âŒ Failed to generate apps hash: ${e.message}")
            "HASH_ERROR_APPS"
        }
    }
    
    /**
     * Generate SHA-256 hash of critical system properties
     * Used to detect if system was modified
     */
    fun generateSystemPropertiesHash(): String {
        return try {
            val properties = StringBuilder()
            
            // Critical system properties to monitor
            val propNames = arrayOf(
                "ro.build.version.release",      // Android version
                "ro.build.version.sdk",          // SDK version
                "ro.serialno",                   // Serial number
                "ro.product.model",              // Device model
                "ro.build.fingerprint",          // Build fingerprint
                "ro.build.type",                 // Build type (user/userdebug)
                "ro.build.tags",                 // Build tags (release-keys/test-keys)
                "ro.bootloader",                 // Bootloader version
                "ro.hardware",                   // Hardware platform
                "ro.product.manufacturer"        // Manufacturer
            )
            
            for (prop in propNames) {
                try {
                    val value = getSystemProperty(prop)
                    if (value.isNotEmpty()) {
                        properties.append(value)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not read property: $prop")
                }
            }
            
            val hash = hashString(properties.toString())
            Log.d(TAG, "âœ… System properties hash generated: ${hash.take(16)}...")
            hash
        } catch (e: Exception) {
            Log.e(TAG, "âŒ Failed to generate system properties hash: ${e.message}")
            "HASH_ERROR_PROPS"
        }
    }
    
    /**
     * Read system property using reflection
     * Works on all Android versions
     */
    private fun getSystemProperty(key: String): String {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java)
            val value = method.invoke(null, key) as String
            value ?: ""
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read property $key: ${e.message}")
            ""
        }
    }
    
    /**
     * Generate SHA-256 hash of input string
     */
    private fun hashString(input: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
            
            // Convert to hex string
            hash.joinToString("") { byte ->
                "%02x".format(byte)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hash string: ${e.message}")
            "HASH_ERROR"
        }
    }
}




