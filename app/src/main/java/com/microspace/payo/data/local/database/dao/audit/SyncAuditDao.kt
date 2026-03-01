package com.microspace.payo.data.local.database.dao.audit

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.microspace.payo.data.local.database.entities.audit.SyncAuditEntity

@Dao
interface SyncAuditDao {
    @Insert
    suspend fun insert(audit: SyncAuditEntity)

    @Query("SELECT * FROM sync_audit_log ORDER BY timestamp DESC LIMIT 100")
    suspend fun getRecentAudits(): List<SyncAuditEntity>

    @Query("DELETE FROM sync_audit_log WHERE timestamp < :beforeTimestamp")
    suspend fun pruneOldLogs(beforeTimestamp: Long)
}




