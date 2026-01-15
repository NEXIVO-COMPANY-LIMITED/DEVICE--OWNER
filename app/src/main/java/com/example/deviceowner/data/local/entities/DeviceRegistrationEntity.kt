package com.example.deviceowner.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_registrations")
data class DeviceRegistrationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val device_id: String,
    val serial_number: String,
    val device_type: String,
    val manufacturer: String,
    val system_type: String,
    val model: String,
    val platform: String,
    val os_version: String,
    val os_edition: String,
    val processor: String,
    val installed_ram: String,
    val total_storage: String,
    val build_number: Int,
    val sdk_version: Int,
    val device_imeis: String,
    val loan_number: String,
    val machine_name: String,
    val android_id: String,
    val device_fingerprint: String,
    val bootloader: String,
    val security_patch_level: String,
    val system_uptime: Long,
    val installed_apps_hash: String,
    val system_properties_hash: String,
    val is_device_rooted: Boolean,
    val is_usb_debugging_enabled: Boolean,
    val is_developer_mode_enabled: Boolean,
    val is_bootloader_unlocked: Boolean,
    val is_custom_rom: Boolean,
    val latitude: Double,
    val longitude: Double,
    val tamper_severity: String,
    val tamper_flags: String,
    val battery_level: Int,
    val registrationDate: Long = System.currentTimeMillis(),
    val isActive: Boolean = true,
    val isProtected: Boolean = true
)
