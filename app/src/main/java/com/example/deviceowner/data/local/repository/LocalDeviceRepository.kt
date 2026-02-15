package com.example.deviceowner.data.local.repository

import android.util.Log
import com.example.deviceowner.data.local.database.AppDatabase
import com.example.deviceowner.data.local.database.entities.DeviceDataEntity
import com.example.deviceowner.data.local.database.entities.HeartbeatHistoryEntity
import com.example.deviceowner.data.models.DeviceRegistrationRequest
import com.example.deviceowner.data.models.HeartbeatRequest
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow

/**
 * Local Device Repository - Manages all local database operations
 * 
 * Handles:
 * - Storing device registration data
 * - Storing heartbeat history
 * - Retrieving device data
 * - Syncing status management
 */
class LocalDeviceRepository(private val database: AppDatabase) {
    
    companion object {
        private const val TAG = "LocalDeviceRepository"
    }
    
    private val deviceDataDao = database.deviceDataDao()
    private val heartbeatHistoryDao = database.heartbeatHistoryDao()
    private val gson = Gson()
    
    // ==================== Device Data Operations ====================
    
    /**
     * Save device registration data locally
     */
    suspend fun saveDeviceData(
        loanNumber: String,
        registrationRequest: DeviceRegistrationRequest,
        serverDeviceId: String? = null
    ): Long {
        return try {
            Log.d(TAG, "💾 Saving device data locally for loan: $loanNumber")
            
            val deviceData = DeviceDataEntity(
                loanNumber = loanNumber,
                serverDeviceId = serverDeviceId,
                androidId = registrationRequest.deviceInfo?.get("android_id") as? String,
                model = registrationRequest.deviceInfo?.get("model") as? String,
                manufacturer = registrationRequest.deviceInfo?.get("manufacturer") as? String,
                fingerprint = registrationRequest.deviceInfo?.get("fingerprint") as? String,
                bootloader = registrationRequest.deviceInfo?.get("bootloader") as? String,
                serialNumber = registrationRequest.deviceInfo?.get("serial") as? String,
                deviceImeis = gson.toJson(registrationRequest.imeiInfo?.get("device_imeis")),
                osVersion = registrationRequest.androidInfo?.get("version_release") as? String,
                osEdition = registrationRequest.androidInfo?.get("version_incremental") as? String,
                sdkVersion = (registrationRequest.androidInfo?.get("version_sdk_int") as? Number)?.toInt(),
                securityPatchLevel = registrationRequest.androidInfo?.get("security_patch") as? String,
                installedRam = registrationRequest.storageInfo?.get("installed_ram") as? String,
                totalStorage = registrationRequest.storageInfo?.get("total_storage") as? String,
                latitude = (registrationRequest.locationInfo?.get("latitude") as? Number)?.toDouble(),
                longitude = (registrationRequest.locationInfo?.get("longitude") as? Number)?.toDouble(),
                isDeviceRooted = registrationRequest.securityInfo?.get("is_device_rooted") as? Boolean,
                isUsbDebuggingEnabled = registrationRequest.securityInfo?.get("is_usb_debugging_enabled") as? Boolean,
                isDeveloperModeEnabled = registrationRequest.securityInfo?.get("is_developer_mode_enabled") as? Boolean,
                isBootloaderUnlocked = registrationRequest.securityInfo?.get("is_bootloader_unlocked") as? Boolean,
                isCustomRom = registrationRequest.securityInfo?.get("is_custom_rom") as? Boolean,
                installedAppsHash = registrationRequest.systemIntegrity?.get("installed_apps_hash") as? String,
                systemPropertiesHash = registrationRequest.systemIntegrity?.get("system_properties_hash") as? String,
                fullDataJson = gson.toJson(registrationRequest),
                syncStatus = if (serverDeviceId != null) "synced" else "pending"
            )
            
            val id = deviceDataDao.insertDeviceData(deviceData)
            Log.i(TAG, "✅ Device data saved successfully with ID: $id")
            id
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving device data: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Update device data with server device ID
     */
    suspend fun updateServerDeviceId(localId: Int, serverDeviceId: String) {
        try {
            Log.d(TAG, "🔄 Updating server device ID for local ID: $localId")
            deviceDataDao.updateServerDeviceId(localId, serverDeviceId)
            deviceDataDao.updateSyncStatus(localId, "synced")
            Log.i(TAG, "✅ Server device ID updated")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating server device ID: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Get device data by server device ID
     */
    suspend fun getDeviceDataByServerDeviceId(serverDeviceId: String): DeviceDataEntity? {
        return try {
            Log.d(TAG, "🔍 Retrieving device data for server ID: $serverDeviceId")
            deviceDataDao.getDeviceDataByServerDeviceId(serverDeviceId)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error retrieving device data: ${e.message}", e)
            null
        }
    }
    
    /**
     * Get latest device data
     */
    suspend fun getLatestDeviceData(): DeviceDataEntity? {
        return try {
            Log.d(TAG, "🔍 Retrieving latest device data")
            deviceDataDao.getLatestDeviceData()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error retrieving latest device data: ${e.message}", e)
            null
        }
    }
    
    /**
     * Get latest device data as Flow
     */
    fun getLatestDeviceDataFlow(): Flow<DeviceDataEntity?> {
        return deviceDataDao.getLatestDeviceDataFlow()
    }
    
    /**
     * Update lock status
     */
    suspend fun updateLockStatus(serverDeviceId: String, isLocked: Boolean, reason: String?) {
        try {
            Log.d(TAG, "🔒 Updating lock status for device: $serverDeviceId, locked=$isLocked")
            val device = deviceDataDao.getDeviceDataByServerDeviceId(serverDeviceId)
            if (device != null) {
                deviceDataDao.updateLockStatus(device.id, isLocked, reason)
                Log.i(TAG, "✅ Lock status updated")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating lock status: ${e.message}", e)
        }
    }
    
    /**
     * Update online status
     */
    suspend fun updateOnlineStatus(serverDeviceId: String, isOnline: Boolean) {
        try {
            Log.d(TAG, "🌐 Updating online status for device: $serverDeviceId, online=$isOnline")
            val device = deviceDataDao.getDeviceDataByServerDeviceId(serverDeviceId)
            if (device != null) {
                deviceDataDao.updateOnlineStatus(device.id, isOnline, System.currentTimeMillis())
                Log.i(TAG, "✅ Online status updated")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating online status: ${e.message}", e)
        }
    }
    
    // ==================== Heartbeat History Operations ====================
    
    /**
     * Save heartbeat record locally
     */
    suspend fun saveHeartbeat(
        serverDeviceId: String,
        heartbeatRequest: HeartbeatRequest,
        response: String? = null
    ): Long {
        return try {
            Log.d(TAG, "💾 Saving heartbeat locally for device: $serverDeviceId")
            
            val device = deviceDataDao.getDeviceDataByServerDeviceId(serverDeviceId)
            if (device == null) {
                Log.w(TAG, "⚠️ Device not found for server ID: $serverDeviceId")
                return -1
            }
            
            val heartbeat = HeartbeatHistoryEntity(
                deviceDataId = device.id,
                heartbeatDataJson = gson.toJson(heartbeatRequest),
                serverResponseJson = response,
                syncStatus = "pending"
            )
            
            val id = heartbeatHistoryDao.insertHeartbeat(heartbeat)
            Log.i(TAG, "✅ Heartbeat saved successfully with ID: $id")
            id
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving heartbeat: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Update heartbeat with server response
     */
    suspend fun updateHeartbeatResponse(
        heartbeatId: Long,
        isLocked: Boolean,
        lockReason: String?,
        mismatchesDetected: Boolean,
        highSeverityCount: Int,
        mediumSeverityCount: Int,
        mismatchesJson: String?,
        responseJson: String?
    ) {
        try {
            Log.d(TAG, "🔄 Updating heartbeat response for ID: $heartbeatId")
            
            val heartbeat = heartbeatHistoryDao.getHeartbeatById(heartbeatId.toInt())
            if (heartbeat != null) {
                val updated = heartbeat.copy(
                    isLocked = isLocked,
                    lockReason = lockReason,
                    mismatchesDetected = mismatchesDetected,
                    highSeverityCount = highSeverityCount,
                    mediumSeverityCount = mediumSeverityCount,
                    totalMismatches = highSeverityCount + mediumSeverityCount,
                    mismatchesJson = mismatchesJson,
                    serverResponseJson = responseJson,
                    receivedAt = System.currentTimeMillis(),
                    syncStatus = "synced"
                )
                heartbeatHistoryDao.updateHeartbeat(updated)
                Log.i(TAG, "✅ Heartbeat response updated")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating heartbeat response: ${e.message}", e)
        }
    }
    
    /**
     * Get latest heartbeat for device
     */
    suspend fun getLatestHeartbeat(serverDeviceId: String): HeartbeatHistoryEntity? {
        return try {
            Log.d(TAG, "🔍 Retrieving latest heartbeat for device: $serverDeviceId")
            val device = deviceDataDao.getDeviceDataByServerDeviceId(serverDeviceId)
            if (device != null) {
                heartbeatHistoryDao.getLatestHeartbeat(device.id)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error retrieving latest heartbeat: ${e.message}", e)
            null
        }
    }
    
    /**
     * Get heartbeat history for device
     */
    suspend fun getHeartbeatHistory(serverDeviceId: String): List<HeartbeatHistoryEntity> {
        return try {
            Log.d(TAG, "🔍 Retrieving heartbeat history for device: $serverDeviceId")
            val device = deviceDataDao.getDeviceDataByServerDeviceId(serverDeviceId)
            if (device != null) {
                heartbeatHistoryDao.getHeartbeatsByDeviceId(device.id)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error retrieving heartbeat history: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Get heartbeat history as Flow
     */
    fun getHeartbeatHistoryFlow(serverDeviceId: String): Flow<List<HeartbeatHistoryEntity>> {
        return kotlinx.coroutines.flow.flow {
            try {
                val device = database.deviceDataDao().getDeviceDataByServerDeviceId(serverDeviceId)
                if (device != null) {
                    database.heartbeatHistoryDao().getHeartbeatsByDeviceIdFlow(device.id).collect { heartbeats ->
                        emit(heartbeats)
                    }
                } else {
                    emit(emptyList())
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error retrieving heartbeat history flow: ${e.message}", e)
                emit(emptyList())
            }
        }
    }
    
    /**
     * Get unsynced heartbeats
     */
    suspend fun getUnsyncedHeartbeats(): List<HeartbeatHistoryEntity> {
        return try {
            Log.d(TAG, "🔍 Retrieving unsynced heartbeats")
            heartbeatHistoryDao.getUnsyncedHeartbeats()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error retrieving unsynced heartbeats: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Update heartbeat sync status
     */
    suspend fun updateHeartbeatSyncStatus(heartbeatId: Int, status: String, error: String? = null) {
        try {
            Log.d(TAG, "🔄 Updating heartbeat sync status for ID: $heartbeatId, status=$status")
            heartbeatHistoryDao.updateSyncStatus(heartbeatId, status, error)
            Log.i(TAG, "✅ Heartbeat sync status updated")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating heartbeat sync status: ${e.message}", e)
        }
    }
    
    // ==================== Statistics ====================
    
    /**
     * Get device statistics
     */
    suspend fun getDeviceStats(): Map<String, Any> {
        return try {
            val totalDevices = deviceDataDao.countDeviceData()
            val lockedDevices = deviceDataDao.getLockedDevices().size
            val totalHeartbeats = heartbeatHistoryDao.countAllHeartbeats()
            val heartbeatsWithMismatches = heartbeatHistoryDao.getHeartbeatsWithMismatches().size
            
            mapOf(
                "total_devices" to totalDevices,
                "locked_devices" to lockedDevices,
                "total_heartbeats" to totalHeartbeats,
                "heartbeats_with_mismatches" to heartbeatsWithMismatches
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting device stats: ${e.message}", e)
            emptyMap()
        }
    }
}
