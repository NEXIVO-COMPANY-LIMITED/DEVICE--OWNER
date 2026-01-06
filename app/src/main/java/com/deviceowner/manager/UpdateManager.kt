package com.deviceowner.manager

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import com.deviceowner.logging.AuditLogger
import com.deviceowner.models.UpdateInfo
import com.deviceowner.models.VersionInfo
import com.deviceowner.utils.ApiClient
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * UpdateManager handles app updates and rollback capability
 * Feature 4.11: Agent Updates & Rollback
 */
class UpdateManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
    private val auditLogger = AuditLogger(context)
    private val apiClient = ApiClient(context)
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    
    companion object {
        private const val TAG = "UpdateManager"
        private const val PREF_CURRENT_VERSION = "current_version"
        private const val PREF_LAST_UPDATE_CHECK = "last_update_check"
        private const val PREF_PENDING_UPDATE = "pending_update"
        private const val PREF_UPDATE_QUEUE = "update_queue"
        private const val UPDATE_CHECK_INTERVAL = 3600000L // 1 hour
        private const val UPDATES_DIR = "updates"
    }
    
    init {
        ensureUpdatesDirectory()
    }
    
    /**
     * Check if update is available for current device
     */
    fun checkForUpdates(onResult: (UpdateInfo?) -> Unit) {
        scope.launch {
            try {
                val currentVersion = getCurrentVersion()
                val deviceId = getDeviceId()
                
                auditLogger.log(TAG, "Checking for updates", mapOf(
                    "currentVersion" to currentVersion,
                    "deviceId" to deviceId
                ))
                
                val response = apiClient.post(
                    "/api/updates/check",
                    mapOf(
                        "deviceId" to deviceId,
                        "currentVersion" to currentVersion
                    )
                )
                
                if (response != null && response.optBoolean("success", false)) {
                    if (response.optBoolean("available", false)) {
                        val updateInfo = parseUpdateInfo(response)
                        auditLogger.log(TAG, "Update available", mapOf(
                            "targetVersion" to updateInfo.targetVersion,
                            "updateType" to updateInfo.updateType,
                            "critical" to updateInfo.critical.toString()
                        ))
                        
                        // Store pending update
                        storePendingUpdate(updateInfo)
                        
                        withContext(Dispatchers.Main) {
                            onResult(updateInfo)
                        }
                    } else {
                        auditLogger.log(TAG, "No update available", mapOf(
                            "reason" to response.optString("reason", "Unknown")
                        ))
                        withContext(Dispatchers.Main) {
                            onResult(null)
                        }
                    }
                } else {
                    auditLogger.error(TAG, "Update check failed", response?.toString() ?: "No response")
                    withContext(Dispatchers.Main) {
                        onResult(null)
                    }
                }
            } catch (e: Exception) {
                auditLogger.error(TAG, "Update check error", e.message ?: "Unknown error")
                withContext(Dispatchers.Main) {
                    onResult(null)
                }
            }
        }
    }
    
    /**
     * Download update package
     */
    fun downloadUpdate(targetVersion: String, onProgress: (Int) -> Unit, onComplete: (Boolean, String?) -> Unit) {
        scope.launch {
            try {
                val deviceId = getDeviceId()
                
                auditLogger.log(TAG, "Starting update download", mapOf(
                    "targetVersion" to targetVersion,
                    "deviceId" to deviceId
                ))
                
                // Get download URL and metadata
                val response = apiClient.post(
                    "/api/updates/download",
                    mapOf(
                        "deviceId" to deviceId,
                        "targetVersion" to targetVersion
                    )
                )
                
                if (response == null || !response.optBoolean("success", false)) {
                    auditLogger.error(TAG, "Download request failed", response?.toString() ?: "No response")
                    withContext(Dispatchers.Main) {
                        onComplete(false, "Download request failed")
                    }
                    return@launch
                }
                
                val downloadUrl = response.optString("downloadUrl", "")
                val expectedSha256 = response.optString("sha256", "")
                val fileSize = response.optLong("fileSize", 0L)
                
                // Download file
                val updateFile = File(context.getExternalFilesDir(UPDATES_DIR), "app-$targetVersion.apk")
                val success = downloadFile(downloadUrl, updateFile, fileSize, onProgress)
                
                if (!success) {
                    auditLogger.error(TAG, "Download failed", "File download incomplete")
                    withContext(Dispatchers.Main) {
                        onComplete(false, "Download failed")
                    }
                    return@launch
                }
                
                // Verify SHA256
                val actualSha256 = calculateSha256(updateFile)
                if (actualSha256 != expectedSha256) {
                    auditLogger.error(TAG, "SHA256 verification failed", 
                        "Expected: $expectedSha256, Got: $actualSha256")
                    updateFile.delete()
                    withContext(Dispatchers.Main) {
                        onComplete(false, "SHA256 verification failed")
                    }
                    return@launch
                }
                
                auditLogger.log(TAG, "Update downloaded successfully", mapOf(
                    "targetVersion" to targetVersion,
                    "filePath" to updateFile.absolutePath,
                    "fileSize" to fileSize.toString()
                ))
                
                withContext(Dispatchers.Main) {
                    onComplete(true, updateFile.absolutePath)
                }
            } catch (e: Exception) {
                auditLogger.error(TAG, "Download error", e.message ?: "Unknown error")
                withContext(Dispatchers.Main) {
                    onComplete(false, e.message)
                }
            }
        }
    }
    
    /**
     * Install update silently using Device Owner privilege
     */
    fun installUpdate(apkPath: String, targetVersion: String, onComplete: (Boolean, String?) -> Unit) {
        scope.launch {
            try {
                auditLogger.log(TAG, "Starting silent update installation", mapOf(
                    "apkPath" to apkPath,
                    "targetVersion" to targetVersion
                ))
                
                val deviceOwnerManager = DeviceOwnerManager(context)
                
                // Use Device Owner privilege to install
                val success = deviceOwnerManager.installPackageSilently(apkPath)
                
                if (success) {
                    auditLogger.log(TAG, "Update installed successfully", mapOf(
                        "targetVersion" to targetVersion
                    ))
                    
                    // Report installation success to backend
                    reportInstallationStatus(targetVersion, true, null)
                    
                    // Update current version
                    setCurrentVersion(targetVersion)
                    
                    // Clean up old APK
                    File(apkPath).delete()
                    
                    withContext(Dispatchers.Main) {
                        onComplete(true, null)
                    }
                } else {
                    auditLogger.error(TAG, "Update installation failed", "Device Owner installation failed")
                    reportInstallationStatus(targetVersion, false, "Installation failed")
                    withContext(Dispatchers.Main) {
                        onComplete(false, "Installation failed")
                    }
                }
            } catch (e: Exception) {
                auditLogger.error(TAG, "Installation error", e.message ?: "Unknown error")
                reportInstallationStatus(targetVersion, false, e.message)
                withContext(Dispatchers.Main) {
                    onComplete(false, e.message)
                }
            }
        }
    }
    
    /**
     * Queue update for offline installation
     */
    fun queueUpdate(targetVersion: String, priority: String = "NORMAL") {
        scope.launch {
            try {
                val deviceId = getDeviceId()
                
                auditLogger.log(TAG, "Queueing update", mapOf(
                    "targetVersion" to targetVersion,
                    "priority" to priority,
                    "deviceId" to deviceId
                ))
                
                val response = apiClient.post(
                    "/api/updates/queue",
                    mapOf(
                        "deviceId" to deviceId,
                        "targetVersion" to targetVersion,
                        "priority" to priority
                    )
                )
                
                if (response != null && response.optBoolean("success", false)) {
                    val updateId = response.optString("updateId", "")
                    auditLogger.log(TAG, "Update queued successfully", mapOf(
                        "updateId" to updateId
                    ))
                } else {
                    auditLogger.error(TAG, "Queue failed", response?.toString() ?: "No response")
                }
            } catch (e: Exception) {
                auditLogger.error(TAG, "Queue error", e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * Get pending updates for device
     */
    fun getPendingUpdates(onResult: (List<UpdateInfo>) -> Unit) {
        scope.launch {
            try {
                val deviceId = getDeviceId()
                
                val response = apiClient.post(
                    "/api/updates/pending",
                    mapOf("deviceId" to deviceId)
                )
                
                if (response != null && response.optBoolean("success", false)) {
                    val pending = mutableListOf<UpdateInfo>()
                    val pendingArray = response.optJSONArray("pending")
                    
                    if (pendingArray != null) {
                        for (i in 0 until pendingArray.length()) {
                            val updateObj = pendingArray.getJSONObject(i)
                            pending.add(UpdateInfo(
                                updateId = updateObj.optString("updateId", ""),
                                targetVersion = updateObj.optString("targetVersion", ""),
                                updateType = "PENDING",
                                critical = false,
                                priority = updateObj.optString("priority", "NORMAL")
                            ))
                        }
                    }
                    
                    auditLogger.log(TAG, "Retrieved pending updates", mapOf(
                        "count" to pending.size.toString()
                    ))
                    
                    withContext(Dispatchers.Main) {
                        onResult(pending)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onResult(emptyList())
                    }
                }
            } catch (e: Exception) {
                auditLogger.error(TAG, "Get pending updates error", e.message ?: "Unknown error")
                withContext(Dispatchers.Main) {
                    onResult(emptyList())
                }
            }
        }
    }
    
    /**
     * Rollback to previous version
     */
    fun rollback(targetVersion: String, reason: String = "Manual rollback", onComplete: (Boolean, String?) -> Unit) {
        scope.launch {
            try {
                val deviceId = getDeviceId()
                
                auditLogger.warn(TAG, "Initiating rollback", mapOf(
                    "targetVersion" to targetVersion,
                    "reason" to reason,
                    "deviceId" to deviceId
                ))
                
                val response = apiClient.post(
                    "/api/updates/rollback",
                    mapOf(
                        "deviceId" to deviceId,
                        "targetVersion" to targetVersion,
                        "reason" to reason
                    )
                )
                
                if (response != null && response.optBoolean("success", false)) {
                    val rollbackId = response.optString("rollbackId", "")
                    auditLogger.warn(TAG, "Rollback queued", mapOf(
                        "rollbackId" to rollbackId
                    ))
                    withContext(Dispatchers.Main) {
                        onComplete(true, null)
                    }
                } else {
                    auditLogger.error(TAG, "Rollback failed", response?.toString() ?: "No response")
                    withContext(Dispatchers.Main) {
                        onComplete(false, "Rollback failed")
                    }
                }
            } catch (e: Exception) {
                auditLogger.error(TAG, "Rollback error", e.message ?: "Unknown error")
                withContext(Dispatchers.Main) {
                    onComplete(false, e.message)
                }
            }
        }
    }
    
    /**
     * Get available rollback versions
     */
    fun getRollbackVersions(onResult: (List<VersionInfo>) -> Unit) {
        scope.launch {
            try {
                val response = apiClient.get("/api/updates/rollback-versions")
                
                if (response != null && response.optBoolean("success", false)) {
                    val versions = mutableListOf<VersionInfo>()
                    val versionsArray = response.optJSONArray("rollbackVersions")
                    
                    if (versionsArray != null) {
                        for (i in 0 until versionsArray.length()) {
                            val versionObj = versionsArray.getJSONObject(i)
                            versions.add(VersionInfo(
                                version = versionObj.optString("version", ""),
                                releaseDate = versionObj.optString("releaseDate", ""),
                                releaseNotes = versionObj.optString("releaseNotes", "")
                            ))
                        }
                    }
                    
                    auditLogger.log(TAG, "Retrieved rollback versions", mapOf(
                        "count" to versions.size.toString()
                    ))
                    
                    withContext(Dispatchers.Main) {
                        onResult(versions)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onResult(emptyList())
                    }
                }
            } catch (e: Exception) {
                auditLogger.error(TAG, "Get rollback versions error", e.message ?: "Unknown error")
                withContext(Dispatchers.Main) {
                    onResult(emptyList())
                }
            }
        }
    }
    
    /**
     * Get current app version
     */
    fun getCurrentVersion(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (e: PackageManager.NameNotFoundException) {
            "1.0.0"
        }
    }
    
    /**
     * Set current version in preferences
     */
    private fun setCurrentVersion(version: String) {
        prefs.edit().putString(PREF_CURRENT_VERSION, version).apply()
    }
    
    /**
     * Get device ID
     */
    private fun getDeviceId(): String {
        return prefs.getString("device_id", Build.SERIAL) ?: Build.SERIAL
    }
    
    /**
     * Store pending update info
     */
    private fun storePendingUpdate(updateInfo: UpdateInfo) {
        val json = JSONObject().apply {
            put("updateId", updateInfo.updateId)
            put("targetVersion", updateInfo.targetVersion)
            put("updateType", updateInfo.updateType)
            put("critical", updateInfo.critical)
            put("priority", updateInfo.priority)
        }
        prefs.edit().putString(PREF_PENDING_UPDATE, json.toString()).apply()
    }
    
    /**
     * Parse update info from API response
     */
    private fun parseUpdateInfo(response: JSONObject): UpdateInfo {
        val updatePackage = response.optJSONObject("updatePackage") ?: JSONObject()
        val changelogArray = updatePackage.optJSONArray("changelog")
        val changelog = mutableListOf<String>()
        
        if (changelogArray != null) {
            for (i in 0 until changelogArray.length()) {
                changelog.add(changelogArray.optString(i, ""))
            }
        }
        
        return UpdateInfo(
            updateId = "update_${System.currentTimeMillis()}",
            targetVersion = response.optString("targetVersion", ""),
            updateType = response.optString("updateType", ""),
            critical = response.optBoolean("critical", false),
            priority = "NORMAL",
            downloadUrl = updatePackage.optString("downloadUrl", ""),
            fileSize = updatePackage.optLong("fileSize", 0L),
            sha256 = updatePackage.optString("sha256", ""),
            releaseNotes = updatePackage.optString("releaseNotes", ""),
            changelog = changelog
        )
    }
    
    /**
     * Report installation status to backend
     */
    private fun reportInstallationStatus(targetVersion: String, success: Boolean, error: String?) {
        scope.launch {
            try {
                apiClient.post(
                    "/api/updates/install",
                    mapOf(
                        "deviceId" to getDeviceId(),
                        "targetVersion" to targetVersion,
                        "success" to success,
                        "error" to (error ?: "")
                    )
                )
            } catch (e: Exception) {
                auditLogger.error(TAG, "Failed to report installation status", e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * Download file from URL
     */
    private suspend fun downloadFile(url: String, file: File, fileSize: Long, onProgress: (Int) -> Unit): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                connection.connect()
                
                if (connection.responseCode != 200) {
                    return@withContext false
                }
                
                file.parentFile?.mkdirs()
                file.outputStream().use { output ->
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalRead = 0L
                        
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            val progress = ((totalRead * 100) / fileSize).toInt()
                            onProgress(progress)
                        }
                    }
                }
                
                true
            } catch (e: Exception) {
                auditLogger.error(TAG, "Download error", e.message ?: "Unknown error")
                false
            }
        }
    }
    
    /**
     * Calculate SHA256 hash of file
     */
    private fun calculateSha256(file: File): String {
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
     * Ensure updates directory exists
     */
    private fun ensureUpdatesDirectory() {
        val updatesDir = context.getExternalFilesDir(UPDATES_DIR)
        updatesDir?.mkdirs()
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        scope.cancel()
    }
}
