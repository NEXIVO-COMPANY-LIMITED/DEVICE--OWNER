package com.deviceowner.models

/**
 * Update information model
 */
data class UpdateInfo(
    val updateId: String,
    val targetVersion: String,
    val updateType: String, // STABLE, BETA, CRITICAL
    val critical: Boolean,
    val priority: String = "NORMAL", // CRITICAL, HIGH, NORMAL, LOW
    val downloadUrl: String? = null,
    val fileSize: Long = 0L,
    val sha256: String? = null,
    val releaseNotes: String? = null,
    val changelog: List<String> = emptyList()
)

/**
 * Version information model
 */
data class VersionInfo(
    val version: String,
    val releaseDate: String,
    val releaseNotes: String,
    val versionCode: Int = 0,
    val minSdkVersion: Int = 24,
    val targetSdkVersion: Int = 36,
    val rollbackSupported: Boolean = false
)

/**
 * Update status model
 */
data class UpdateStatus(
    val updateId: String,
    val status: String, // PENDING, DOWNLOADING, INSTALLING, COMPLETED, FAILED
    val progress: Int = 0,
    val targetVersion: String,
    val error: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Rollback request model
 */
data class RollbackRequest(
    val targetVersion: String,
    val reason: String,
    val timestamp: Long = System.currentTimeMillis()
)
