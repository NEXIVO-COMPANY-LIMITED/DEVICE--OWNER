package com.example.deviceowner.managers

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Comprehensive multi-layer verification system
 * Performs cross-validation of all protection mechanisms
 * Provides detailed verification results with failure reasons
 */
class ComprehensiveVerificationManager(private val context: Context) {
    
    private val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val packageManager = context.packageManager
    private val prefs: SharedPreferences = context.getSharedPreferences("verification", Context.MODE_PRIVATE)
    private val auditLog = IdentifierAuditLog(context)
    private val gson = Gson()
    private val lastVerificationTime = AtomicLong(0)
    
    // Protected verification cache
    private val verificationCacheFile: File by lazy {
        val file = File(context.cacheDir, "verification_cache.dat")
        file.setReadable(true, true)
        file.setWritable(true, true)
        file
    }
    
    companion object {
        private const val TAG = "ComprehensiveVerificationManager"
        private const val KEY_LAST_VERIFICATION = "last_verification"
        private const val KEY_VERIFICATION_HISTORY = "verification_history"
        private const val MAX_HISTORY_SIZE = 100
        private const val VERIFICATION_CACHE_DURATION = 60000L // 1 minute
    }
    
    /**
     * Perform comprehensive multi-layer verification
     * Validates all protection mechanisms and cross-checks results
     */
    suspend fun comprehensiveVerification(): VerificationResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting comprehensive verification...")
            val startTime = System.currentTimeMillis()
            val failureReasons = mutableListOf<String>()
            
            // Check 1: App Installation
            val appInstalled = verifyAppInstalled()
            if (!appInstalled) {
                failureReasons.add("App not installed")
                Log.w(TAG, "✗ Check 1 Failed: App not installed")
            } else {
                Log.d(TAG, "✓ Check 1 Passed: App installed")
            }
            
            // Check 2: Device Owner Status
            val deviceOwnerEnabled = verifyDeviceOwnerEnabled()
            if (!deviceOwnerEnabled) {
                failureReasons.add("Device owner not enabled")
                Log.w(TAG, "✗ Check 2 Failed: Device owner not enabled")
            } else {
                Log.d(TAG, "✓ Check 2 Passed: Device owner enabled")
            }
            
            // Check 3: Uninstall Block
            val uninstallBlocked = verifyUninstallBlocked()
            if (!uninstallBlocked) {
                failureReasons.add("Uninstall not blocked")
                Log.w(TAG, "✗ Check 3 Failed: Uninstall not blocked")
            } else {
                Log.d(TAG, "✓ Check 3 Passed: Uninstall blocked")
            }
            
            // Check 4: Force-Stop Block
            val forceStopBlocked = verifyForceStopBlocked()
            if (!forceStopBlocked) {
                failureReasons.add("Force-stop not blocked")
                Log.w(TAG, "✗ Check 4 Failed: Force-stop not blocked")
            } else {
                Log.d(TAG, "✓ Check 4 Passed: Force-stop blocked")
            }
            
            // Check 5: System App Status
            val isSystemApp = verifySystemAppStatus()
            if (!isSystemApp) {
                failureReasons.add("App not set as system app")
                Log.w(TAG, "✗ Check 5 Failed: App not system app")
            } else {
                Log.d(TAG, "✓ Check 5 Passed: App is system app")
            }
            
            // Check 6: App Disable Block
            val appDisableBlocked = verifyAppDisableBlocked()
            if (!appDisableBlocked) {
                failureReasons.add("App disable not blocked")
                Log.w(TAG, "✗ Check 6 Failed: App disable not blocked")
            } else {
                Log.d(TAG, "✓ Check 6 Passed: App disable blocked")
            }
            
            // Cross-validation: Check consistency
            val consistencyCheck = performCrossValidation(
                appInstalled, deviceOwnerEnabled, uninstallBlocked,
                forceStopBlocked, isSystemApp, appDisableBlocked
            )
            
            if (!consistencyCheck.isConsistent) {
                failureReasons.addAll(consistencyCheck.inconsistencies)
                Log.w(TAG, "✗ Cross-validation Failed: ${consistencyCheck.inconsistencies.joinToString(", ")}")
            } else {
                Log.d(TAG, "✓ Cross-validation Passed: All checks consistent")
            }
            
            val allChecksPassed = failureReasons.isEmpty()
            val duration = System.currentTimeMillis() - startTime
            
            val result = VerificationResult(
                allChecksPassed = allChecksPassed,
                appInstalled = appInstalled,
                deviceOwnerEnabled = deviceOwnerEnabled,
                uninstallBlocked = uninstallBlocked,
                forceStopBlocked = forceStopBlocked,
                isSystemApp = isSystemApp,
                appDisableBlocked = appDisableBlocked,
                timestamp = System.currentTimeMillis(),
                failureReasons = failureReasons,
                verificationDuration = duration,
                consistencyValid = consistencyCheck.isConsistent
            )
            
            // Log result
            if (allChecksPassed) {
                Log.d(TAG, "✓ Comprehensive verification PASSED (${duration}ms)")
                auditLog.logAction(
                    "VERIFICATION_PASSED",
                    "All checks passed in ${duration}ms"
                )
            } else {
                Log.e(TAG, "✗ Comprehensive verification FAILED: ${failureReasons.joinToString(", ")}")
                auditLog.logAction(
                    "VERIFICATION_FAILED",
                    "Failures: ${failureReasons.joinToString(", ")}"
                )
            }
            
            // Save result to cache
            saveVerificationResult(result)
            lastVerificationTime.set(System.currentTimeMillis())
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error in comprehensive verification", e)
            auditLog.logIncident(
                type = "VERIFICATION_ERROR",
                severity = "HIGH",
                details = "Verification error: ${e.message}"
            )
            
            VerificationResult(
                allChecksPassed = false,
                appInstalled = false,
                deviceOwnerEnabled = false,
                uninstallBlocked = false,
                forceStopBlocked = false,
                isSystemApp = false,
                appDisableBlocked = false,
                timestamp = System.currentTimeMillis(),
                failureReasons = listOf("Verification error: ${e.message}"),
                verificationDuration = 0,
                consistencyValid = false
            )
        }
    }
    
    /**
     * Verify app is installed
     */
    private fun verifyAppInstalled(): Boolean {
        return try {
            packageManager.getPackageInfo(context.packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "App not found in package manager")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking app installation", e)
            false
        }
    }
    
    /**
     * Verify device owner is enabled
     */
    private fun verifyDeviceOwnerEnabled(): Boolean {
        return try {
            val adminComponent = ComponentName(context, com.example.deviceowner.receivers.DeviceAdminReceiver::class.java)
            devicePolicyManager.isAdminActive(adminComponent).also {
                Log.d(TAG, "Device owner enabled: $it")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking device owner status", e)
            false
        }
    }
    
    /**
     * Verify uninstall is blocked
     */
    private fun verifyUninstallBlocked(): Boolean {
        return try {
            val adminComponent = ComponentName(context, com.example.deviceowner.receivers.DeviceAdminReceiver::class.java)
            val isBlocked = devicePolicyManager.isUninstallBlocked(adminComponent, context.packageName)
            Log.d(TAG, "Uninstall blocked: $isBlocked")
            isBlocked
        } catch (e: Exception) {
            Log.e(TAG, "Error checking uninstall block", e)
            false
        }
    }
    
    /**
     * Verify force-stop is blocked
     */
    private fun verifyForceStopBlocked(): Boolean {
        return try {
            // Check if app is protected from force-stop
            val appInfo = packageManager.getApplicationInfo(context.packageName, 0)
            val isProtected = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            Log.d(TAG, "Force-stop blocked (system app): $isProtected")
            isProtected
        } catch (e: Exception) {
            Log.e(TAG, "Error checking force-stop block", e)
            false
        }
    }
    
    /**
     * Verify app is set as system app
     */
    private fun verifySystemAppStatus(): Boolean {
        return try {
            val appInfo = packageManager.getApplicationInfo(context.packageName, 0)
            val isSystemApp = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            Log.d(TAG, "App is system app: $isSystemApp")
            isSystemApp
        } catch (e: Exception) {
            Log.e(TAG, "Error checking system app status", e)
            false
        }
    }
    
    /**
     * Verify app disable is blocked
     */
    private fun verifyAppDisableBlocked(): Boolean {
        return try {
            val adminComponent = ComponentName(context, com.example.deviceowner.receivers.DeviceAdminReceiver::class.java)
            // Check if app is protected from being disabled
            val isProtected = (packageManager.getApplicationInfo(context.packageName, 0).flags 
                and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            Log.d(TAG, "App disable blocked: $isProtected")
            isProtected
        } catch (e: Exception) {
            Log.e(TAG, "Error checking app disable block", e)
            false
        }
    }
    
    /**
     * Perform cross-validation of all checks
     * Ensures consistency between different protection mechanisms
     */
    private fun performCrossValidation(
        appInstalled: Boolean,
        deviceOwnerEnabled: Boolean,
        uninstallBlocked: Boolean,
        forceStopBlocked: Boolean,
        isSystemApp: Boolean,
        appDisableBlocked: Boolean
    ): CrossValidationResult {
        val inconsistencies = mutableListOf<String>()
        
        // Rule 1: If device owner is enabled, uninstall should be blocked
        if (deviceOwnerEnabled && !uninstallBlocked) {
            inconsistencies.add("Device owner enabled but uninstall not blocked")
        }
        
        // Rule 2: If app is installed, device owner should be enabled
        if (appInstalled && !deviceOwnerEnabled) {
            inconsistencies.add("App installed but device owner not enabled")
        }
        
        // Rule 3: If uninstall is blocked, app should be installed
        if (uninstallBlocked && !appInstalled) {
            inconsistencies.add("Uninstall blocked but app not installed")
        }
        
        // Rule 4: If system app, force-stop should be blocked
        if (isSystemApp && !forceStopBlocked) {
            inconsistencies.add("App is system app but force-stop not blocked")
        }
        
        // Rule 5: If system app, app disable should be blocked
        if (isSystemApp && !appDisableBlocked) {
            inconsistencies.add("App is system app but app disable not blocked")
        }
        
        // Rule 6: If device owner enabled, app should be system app
        if (deviceOwnerEnabled && !isSystemApp) {
            inconsistencies.add("Device owner enabled but app not system app")
        }
        
        return CrossValidationResult(
            isConsistent = inconsistencies.isEmpty(),
            inconsistencies = inconsistencies
        )
    }
    
    /**
     * Get verification history
     */
    fun getVerificationHistory(): List<VerificationResult> {
        return try {
            // Try file first
            if (verificationCacheFile.exists()) {
                val json = verificationCacheFile.readText()
                val type = object : com.google.gson.reflect.TypeToken<List<VerificationResult>>() {}.type
                return gson.fromJson(json, type) ?: emptyList()
            }
            
            // Fallback to SharedPreferences
            val json = prefs.getString(KEY_VERIFICATION_HISTORY, "[]") ?: "[]"
            val type = object : com.google.gson.reflect.TypeToken<List<VerificationResult>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving verification history", e)
            emptyList()
        }
    }
    
    /**
     * Get last verification result
     */
    fun getLastVerificationResult(): VerificationResult? {
        return try {
            val history = getVerificationHistory()
            history.lastOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting last verification result", e)
            null
        }
    }
    
    /**
     * Check if verification is needed (based on cache duration)
     */
    fun isVerificationNeeded(): Boolean {
        val timeSinceLastVerification = System.currentTimeMillis() - lastVerificationTime.get()
        return timeSinceLastVerification > VERIFICATION_CACHE_DURATION
    }
    
    /**
     * Save verification result to cache
     */
    private fun saveVerificationResult(result: VerificationResult) {
        try {
            val history = getVerificationHistory().toMutableList()
            history.add(result)
            
            // Keep only recent results
            if (history.size > MAX_HISTORY_SIZE) {
                history.removeAt(0)
            }
            
            val json = gson.toJson(history)
            
            // Save to file
            verificationCacheFile.writeText(json)
            
            // Also save to SharedPreferences as backup
            prefs.edit().putString(KEY_VERIFICATION_HISTORY, json).apply()
            
            Log.d(TAG, "Verification result saved to cache")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving verification result", e)
        }
    }
    
    /**
     * Get verification statistics
     */
    fun getVerificationStatistics(): VerificationStatistics {
        val history = getVerificationHistory()
        
        if (history.isEmpty()) {
            return VerificationStatistics(
                totalVerifications = 0,
                passedVerifications = 0,
                failedVerifications = 0,
                successRate = 0.0,
                averageDuration = 0L,
                lastVerificationTime = 0L
            )
        }
        
        val passed = history.count { it.allChecksPassed }
        val failed = history.size - passed
        val successRate = (passed.toDouble() / history.size) * 100
        val averageDuration = history.map { it.verificationDuration }.average().toLong()
        val lastTime = history.lastOrNull()?.timestamp ?: 0L
        
        return VerificationStatistics(
            totalVerifications = history.size,
            passedVerifications = passed,
            failedVerifications = failed,
            successRate = successRate,
            averageDuration = averageDuration,
            lastVerificationTime = lastTime
        )
    }
}

/**
 * Data class for comprehensive verification result
 */
data class VerificationResult(
    val allChecksPassed: Boolean,
    val appInstalled: Boolean,
    val deviceOwnerEnabled: Boolean,
    val uninstallBlocked: Boolean,
    val forceStopBlocked: Boolean,
    val isSystemApp: Boolean = false,
    val appDisableBlocked: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val failureReasons: List<String> = emptyList(),
    val verificationDuration: Long = 0,
    val consistencyValid: Boolean = true
)

/**
 * Data class for cross-validation result
 */
data class CrossValidationResult(
    val isConsistent: Boolean,
    val inconsistencies: List<String>
)

/**
 * Data class for verification statistics
 */
data class VerificationStatistics(
    val totalVerifications: Int,
    val passedVerifications: Int,
    val failedVerifications: Int,
    val successRate: Double,
    val averageDuration: Long,
    val lastVerificationTime: Long
)
