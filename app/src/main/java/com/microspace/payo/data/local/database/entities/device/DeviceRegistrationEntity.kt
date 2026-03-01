package com.microspace.payo.data.local.database.entities.device

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_registrations")
data class DeviceRegistrationEntity(
    @PrimaryKey
    val deviceId: String,
    val loanNumber: String,
    val registrationStatus: String,
    val registeredAt: Long,
    val lastSyncAt: Long? = null
)




