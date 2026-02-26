package com.microspace.payo.data.local.database.entities.sim

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * SIM Change History Entity - Stores SIM change events locally (no backend).
 *
 * Records each time the phone number, operator, or SIM serial changes.
 * Used to detect unauthorized SIM swaps and display history to the user.
 */
@Entity(tableName = "sim_change_history")
data class SimChangeHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "original_phone_number")
    val originalPhoneNumber: String,

    @ColumnInfo(name = "new_phone_number")
    val newPhoneNumber: String,

    @ColumnInfo(name = "original_operator")
    val originalOperator: String,

    @ColumnInfo(name = "new_operator")
    val newOperator: String,

    @ColumnInfo(name = "original_serial")
    val originalSerial: String,

    @ColumnInfo(name = "new_serial")
    val newSerial: String,

    @ColumnInfo(name = "changed_at")
    val changedAt: Long = System.currentTimeMillis()
)
