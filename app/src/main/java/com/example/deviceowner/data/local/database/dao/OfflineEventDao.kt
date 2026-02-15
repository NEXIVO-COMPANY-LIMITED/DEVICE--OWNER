package com.example.deviceowner.data.local.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.example.deviceowner.data.local.database.entities.OfflineEvent

/**
 * Data Access Object for OfflineEvent entity.
 * Handles database operations for storing and retrieving offline events
 * that need to be synchronized when the device comes back online.
 */
@Dao
interface OfflineEventDao {
    
    /**
     * Insert a new offline event into the database.
     * 
     * @param event The OfflineEvent to insert
     */
    @Insert
    suspend fun insertEvent(event: OfflineEvent)
    
    /**
     * Delete an offline event from the database.
     * 
     * @param event The OfflineEvent to delete
     */
    @Delete
    suspend fun deleteEvent(event: OfflineEvent)
    
    /**
     * Retrieve all offline events from the database, ordered by timestamp (oldest first).
     * 
     * @return List of all OfflineEvent records
     */
    @Query("SELECT * FROM offline_events ORDER BY timestamp ASC")
    suspend fun getAllEvents(): List<OfflineEvent>
    
    /**
     * Get the total count of offline events in the database.
     * 
     * @return Number of events stored
     */
    @Query("SELECT COUNT(*) FROM offline_events")
    suspend fun getEventCount(): Int
    
    /**
     * Delete a specific offline event by its ID.
     * 
     * @param eventId The ID of the event to delete
     */
    @Query("DELETE FROM offline_events WHERE id = :eventId")
    suspend fun deleteEventById(eventId: Long)
}
