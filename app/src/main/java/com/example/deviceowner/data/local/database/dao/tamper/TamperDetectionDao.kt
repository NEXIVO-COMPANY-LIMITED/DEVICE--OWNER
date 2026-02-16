package com.example.deviceowner.data.local.database.dao.tamper

import androidx.room.*
import com.example.deviceowner.data.local.database.entities.tamper.TamperDetectionEntity

@Dao
interface TamperDetectionDao {

    @Query("SELECT * FROM tamper_detections WHERE deviceId = :deviceId ORDER BY detectedAt DESC")
    suspend fun getTamperDetectionsByDeviceId(deviceId: String): List<TamperDetectionEntity>

    @Query("SELECT * FROM tamper_detections WHERE syncStatus = 'pending' ORDER BY detectedAt ASC")
    suspend fun getPendingSyncTamperDetections(): List<TamperDetectionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTamperDetection(tamperDetection: TamperDetectionEntity): Long

    @Update
    suspend fun updateTamperDetection(tamperDetection: TamperDetectionEntity)

    @Query("UPDATE tamper_detections SET syncStatus = 'synced' WHERE id = :id")
    suspend fun markAsSynced(id: Long)

    @Query("DELETE FROM tamper_detections WHERE detectedAt < :cutoffTime")
    suspend fun deleteOldTamperDetections(cutoffTime: Long)
}
