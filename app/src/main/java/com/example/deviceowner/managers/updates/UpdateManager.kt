package com.example.deviceowner.managers

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.io.File
import java.security.MessageDigest

/**
 * Manages app updates and version checking
 * Feature 4.11: Agent Updates & Rollback
 * 
 * Responsibilities:
 * - Check for available updates during heartbeat
 * - Download updates silently
 * - Verify update signatures
 * - Queue updates for offline devices
 * - Trigger installation
 */
class UpdateManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "update_manager",
        Context.MODE_PRIVATE
    )
    private val gson = Gson()
    private val packageManager = context.packageManager
    private val updateScope = CoroutineScope(Dispatchers.IO + Job())
    
    // Protected cache directory for updates
    private val protectedUpdateDir: File by lazy {
        val updateDir = File(context.cacheDir, "protected_updates")
        if (!updateDir.exists()) {
            updateDir.mkdirs()
            setDirectoryProtection(updateDir)
        }
        updateDir
    }
    
    companion object {
        private const val TAG = "UpdateManager"
        private const val KEY_CURRENT_VERSION = "current_version"
        private const val KEY_LAST_CHECK = "last_check"
        private const val KEY_PENDING_UPDATE = "pending_update"
        private const val KEY_UPDATE_HISTORY = "update_history"
        private const val CHECK_INTERVAL_MS = 3600000L // 1 hour
        
        // Update file names
        private const val UPDATE_FILE = "pending_update.apk"
        private const val UPDATE_METADATA_FILE = "update_metadata.json"
        private const val ROLLBACK_FILE = "rollback_backup.apk"
    }
    
    /**
     * Get current app version
     */
    fun getCurrentVersion(): String {
        return try {
            val packageInfo = packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current version: ${e.message}")
            "1.0.0"
        }
    }
    
    /**
     * Get current version code
     */
    fun getCurrentVersionCode(): Long {
        return try {
            val packageInfo = packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting version code: ${e.message}")
            1L
        }
    }
    
    /**
     * Check for available updates
     */
    suspend fun checkForUpdates(apiService: UpdateApiService): UpdateCheckResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Checking for updates...")
                
                val currentVersion = getCurrentVersion()
                val deviceId = getDeviceId()
                
                val response = apiService.checkUpdate(
                    UpdateCheckRequest(
                        deviceId = deviceId,
                        currentVersion = currentVersion
                    )
                )
                
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    
                    if (body.available) {
                        Log.d(TAG, "✓ Update available: ${body.targetVersion}")
                        Log.d(TAG, "  Type: ${body.updateType}")
                        Log.d(TAG, "  Critical: ${body.critical}")
                        
                        UpdateCheckResult(
                            available = true,
                            targetVersion = body.targetVersion,
                            updateType = body.updateType,
                            critical = body.critical,
                            updatePackage = body.updatePackage
                        )
                    } else {
                        Log.d(TAG, "✓ No update available: ${body.reason}")
                        UpdateCheckResult(available = false, reason = body.reason)
                    }
                } else {
                    Log.e(TAG, "Update check failed: ${response.code()}")
                    UpdateCheckResult(available = false, reason = "Check failed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for updates: ${e.message}", e)
                UpdateCheckResult(available = false, reason = e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * Download update package
     */
    suspend fun downloadUpdate(
        apiService: UpdateApiService,
        targetVersion: String
    ): DownloadResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Downloading update: $targetVersion")
                
                val deviceId = getDeviceId()
                
                val response = apiService.downloadUpdate(
                    UpdateDownloadRequest(
                        deviceId = deviceId,
                        targetVersion = targetVersion
                    )
                )
                
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    Log.d(TAG, "✓ Download info received")
                    Log.d(TAG, "  URL: ${body.downloadUrl}")
                    Log.d(TAG, "  Size: ${body.fileSize} bytes")
                    Log.d(TAG, "  SHA256: ${body.sha256}")
                    
                    DownloadResult(
                        success = true,
                        downloadUrl = body.downloadUrl,
                        fileSize = body.fileSize,
                        sha256 = body.sha256,
                        versionCode = body.versionCode,
                        versionName = body.versionName
                    )
                } else {
                    Log.e(TAG, "Download request failed: ${response.code()}")
                    DownloadResult(success = false, error = "Download request failed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading update: ${e.message}", e)
                DownloadResult(success = false, error = e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * Verify update package integrity
     */
    suspend fun verifyUpdate(
        apiService: UpdateApiService,
        targetVersion: String,
        filePath: String
    ): VerifyResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Verifying update: $targetVersion")
                
                // Calculate SHA256 of downloaded file
                val file = File(filePath)
                if (!file.exists()) {
                    Log.e(TAG, "Update file not found: $filePath")
                    return@withContext VerifyResult(success = false, error = "File not found")
                }
                
                val sha256 = calculateSHA256(file)
                Log.d(TAG, "Calculated SHA256: $sha256")
                
                val deviceId = getDeviceId()
                
                val response = apiService.verifyUpdate(
                    UpdateVerifyRequest(
                        deviceId = deviceId,
                        targetVersion = targetVersion,
                        sha256 = sha256
                    )
                )
                
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    
                    if (body.verified) {
                        Log.d(TAG, "✓ Update verified successfully")
                        VerifyResult(success = true, verified = true)
                    } else {
                        Log.e(TAG, "✗ Update verification failed")
                        VerifyResult(success = true, verified = false, error = "Verification failed")
                    }
                } else {
                    Log.e(TAG, "Verify request failed: ${response.code()}")
                    VerifyResult(success = false, error = "Verify request failed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error verifying update: ${e.message}", e)
                VerifyResult(success = false, error = e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * Queue update for offline installation
     */
    fun queueUpdate(targetVersion: String, filePath: String): Boolean {
        return try {
            Log.d(TAG, "Queueing update: $targetVersion")
            
            val updateMetadata = UpdateMetadata(
                targetVersion = targetVersion,
                filePath = filePath,
                queuedAt = System.currentTimeMillis(),
                status = "PENDING"
            )
            
            prefs.edit().putString(
                KEY_PENDING_UPDATE,
                gson.toJson(updateMetadata)
            ).apply()
            
            Log.d(TAG, "✓ Update queued successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error queueing update: ${e.message}", e)
            false
        }
    }
    
    /**
     * Get pending update
     */
    fun getPendingUpdate(): UpdateMetadata? {
        return try {
            val json = prefs.getString(KEY_PENDING_UPDATE, null) ?: return null
            gson.fromJson(json, UpdateMetadata::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting pending update: ${e.message}")
            null
        }
    }
    
    /**
     * Clear pending update
     */
    fun clearPendingUpdate() {
        prefs.edit().remove(KEY_PENDING_UPDATE).apply()
    }
    
    /**
     * Calculate SHA256 hash of file
     */
    private fun calculateSHA256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Get device ID
     */
    private fun getDeviceId(): String {
        return prefs.getString("device_id", Build.SERIAL) ?: Build.SERIAL
    }
    
    /**
     * Set directory protection
     */
    private fun setDirectoryProtection(dir: File) {
        try {
            dir.setReadable(false, false)
            dir.setReadable(true, true)
            dir.setWritable(false, false)
            dir.setWritable(true, true)
            dir.setExecutable(false, false)
            dir.setExecutable(true, true)
        } catch (e: Exception) {
            Log.w(TAG, "Error setting directory protection: ${e.message}")
        }
    }
}

/**
 * API Service for update operations
 */
interface UpdateApiService {
    
    @POST("api/updates/check")
    suspend fun checkUpdate(@Body request: UpdateCheckRequest): retrofit2.Response<UpdateCheckResponse>
    
    @POST("api/updates/download")
    suspend fun downloadUpdate(@Body request: UpdateDownloadRequest): retrofit2.Response<UpdateDownloadResponse>
    
    @POST("api/updates/verify")
    suspend fun verifyUpdate(@Body request: UpdateVerifyRequest): retrofit2.Response<UpdateVerifyResponse>
    
    @POST("api/updates/install")
    suspend fun reportInstallation(@Body request: UpdateInstallRequest): retrofit2.Response<UpdateInstallResponse>
    
    @POST("api/updates/queue")
    suspend fun queueUpdate(@Body request: UpdateQueueRequest): retrofit2.Response<UpdateQueueResponse>
    
    @POST("api/updates/rollback")
    suspend fun queueRollback(@Body request: RollbackQueueRequest): retrofit2.Response<RollbackQueueResponse>
}

// Request/Response DTOs
data class UpdateCheckRequest(
    val deviceId: String,
    val currentVersion: String
)

data class UpdateCheckResponse(
    val success: Boolean,
    val available: Boolean,
    val targetVersion: String? = null,
    val updateType: String? = null,
    val critical: Boolean = false,
    val updatePackage: Map<String, Any>? = null,
    val reason: String? = null
)

data class UpdateDownloadRequest(
    val deviceId: String,
    val targetVersion: String
)

data class UpdateDownloadResponse(
    val success: Boolean,
    val downloadUrl: String,
    val fileSize: Long,
    val sha256: String,
    val versionCode: Int,
    val versionName: String,
    val releaseNotes: String,
    val changelog: List<String>
)

data class UpdateVerifyRequest(
    val deviceId: String,
    val targetVersion: String,
    val sha256: String
)

data class UpdateVerifyResponse(
    val success: Boolean,
    val verified: Boolean,
    val message: String
)

data class UpdateInstallRequest(
    val deviceId: String,
    val targetVersion: String,
    val success: Boolean,
    val error: String? = null
)

data class UpdateInstallResponse(
    val success: Boolean,
    val message: String
)

data class UpdateQueueRequest(
    val deviceId: String,
    val targetVersion: String,
    val priority: String = "NORMAL"
)

data class UpdateQueueResponse(
    val success: Boolean,
    val updateId: String,
    val message: String
)

data class RollbackQueueRequest(
    val deviceId: String,
    val targetVersion: String,
    val reason: String
)

data class RollbackQueueResponse(
    val success: Boolean,
    val rollbackId: String,
    val message: String
)

// Result classes
data class UpdateCheckResult(
    val available: Boolean,
    val targetVersion: String? = null,
    val updateType: String? = null,
    val critical: Boolean = false,
    val updatePackage: Map<String, Any>? = null,
    val reason: String? = null
)

data class DownloadResult(
    val success: Boolean,
    val downloadUrl: String? = null,
    val fileSize: Long = 0,
    val sha256: String? = null,
    val versionCode: Int = 0,
    val versionName: String? = null,
    val error: String? = null
)

data class VerifyResult(
    val success: Boolean,
    val verified: Boolean = false,
    val error: String? = null
)

data class UpdateMetadata(
    val targetVersion: String,
    val filePath: String,
    val queuedAt: Long,
    val status: String
)
