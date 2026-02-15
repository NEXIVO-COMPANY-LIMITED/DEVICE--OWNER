package com.example.deviceowner.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local record of lock state with timestamp for boot and "problem not solved" logic.
 * When device boots, we check last state (e.g. hard lock saa ngapi) and if unresolved
 * we keep showing hard lock.
 *
 * - applyHardLock: insert row with lockState=HARD_LOCK, resolvedAt=null, createdAt=now()
 * - unlockDevice: mark latest unresolved HARD_LOCK with resolvedAt=now()
 * - Boot: get latest unresolved HARD_LOCK; if present, tatizo haijatatuliwa → keep hard lock
 */
@Entity(tableName = "lock_state_records")
data class LockStateRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** HARD_LOCK, SOFT_LOCK, UNLOCKED */
    val lockState: String,
    /** Reason shown on lock screen */
    val reason: String,
    /** Tamper type if lock was due to tamper (e.g. BOOTLOADER_UNLOCKED, PACKAGE_REMOVED) */
    val tamperType: String? = null,
    /** When this lock was applied */
    val createdAt: Long,
    /** When lock was resolved (unlock or violation fixed); null = not resolved */
    val resolvedAt: Long? = null
)
