package com.microspace.payo.data.local.database.dao.offline

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.microspace.payo.data.local.database.entities.offline.OfflineEvent

@Dao
interface OfflineEventDao {

    @Insert
    suspend fun insertEvent(event: OfflineEvent)

    @Delete
    suspend fun deleteEvent(event: OfflineEvent)

    @Query("SELECT * FROM offline_events ORDER BY timestamp ASC")
    suspend fun getAllEvents(): List<OfflineEvent>

    @Query("SELECT COUNT(*) FROM offline_events")
    suspend fun getEventCount(): Int

    @Query("DELETE FROM offline_events WHERE id = :eventId")
    suspend fun deleteEventById(eventId: Long)
}




