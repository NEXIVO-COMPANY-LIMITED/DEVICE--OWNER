package com.microspace.payo.update.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import com.microspace.payo.services.reporting.ServerBugAndLogReporter
import java.io.File
import java.io.FileInputStream

/**
 * Receives PackageInstaller callbacks when an update installation completes.
 * Features:
 * 1. Cleans up downloaded APK files.
 * 2. Automatic ROLLBACK to previous version if installation fails.
 * 3. Silent operations.
 */
class UpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val version = intent.getStringExtra("version")
        val apkPath = intent.getStringExtra("apkPath")
        val rollbackApkPath = intent.getStringExtra("rollbackApkPath")

        Log.d(TAG, "Installation status received: $status, message: $message")

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                Log.d(TAG, "Installation pending user action")
                @Suppress("DEPRECATION")
                val confirmIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirmIntent != null) {
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(confirmIntent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start confirmation activity", e)
                    }
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                Log.i(TAG, "✅ Installation SUCCESSFUL! Updated to version: $version")
                
                ServerBugAndLogReporter.postLog(
                    logType = "installation",
                    logLevel = "Normal",
                    message = "✅ Success: Device updated to v$version successfully (Silent).",
                    extraData = mapOf("update_version" to version, "status" to "installed")
                )
                
                // Cleanup both update and rollback APKs on success
                cleanupFile(apkPath)
                cleanupFile(rollbackApkPath)
            }

            PackageInstaller.STATUS_FAILURE,
            PackageInstaller.STATUS_FAILURE_ABORTED,
            PackageInstaller.STATUS_FAILURE_BLOCKED,
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
            PackageInstaller.STATUS_FAILURE_INVALID,
            PackageInstaller.STATUS_FAILURE_STORAGE -> {
                val errorMsg = "Update failed: $message (Status: $status)"
                Log.e(TAG, "❌ $errorMsg")
                
                ServerBugAndLogReporter.postBug(
                    title = "Auto-Update Failed - Initiating Rollback",
                    message = errorMsg,
                    priority = "high",
                    extraData = mapOf("failed_version" to version, "status_code" to status)
                )
                
                // INITIATE AUTOMATIC ROLLBACK
                if (!rollbackApkPath.isNullOrEmpty()) {
                    rollbackToPreviousVersion(context, rollbackApkPath)
                } else {
                    Log.e(TAG, "Rollback failed: No backup APK found")
                }
                
                cleanupFile(apkPath)
            }

            else -> Log.d(TAG, "Unknown status: $status")
        }
    }

    private fun rollbackToPreviousVersion(context: Context, backupPath: String) {
        try {
            Log.i(TAG, "🔄 Starting automatic rollback to previous version...")
            val backupFile = File(backupPath)
            if (!backupFile.exists()) {
                Log.e(TAG, "Rollback aborted: Backup file missing at $backupPath")
                return
            }

            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)
            
            session.openWrite("rollback_package", 0, -1).use { out ->
                FileInputStream(backupFile).use { input ->
                    input.copyTo(out)
                }
                session.fsync(out)
            }
            
            val intent = Intent("com.microspace.payo.ROLLBACK_COMPLETE")
            val intentSender = android.app.PendingIntent.getBroadcast(
                context, 0, intent, 
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            ).intentSender
            session.commit(intentSender)
            session.close()
            Log.i(TAG, "✅ Rollback session committed")
        } catch (e: Exception) {
            Log.e(TAG, "Rollback failed critically: ${e.message}", e)
            ServerBugAndLogReporter.postException(e, "Auto-Update: Rollback failed critically")
        }
    }

    private fun cleanupFile(path: String?) {
        if (path == null) return
        try {
            val file = File(path)
            if (file.exists()) {
                file.delete()
                Log.d(TAG, "File cleaned up: $path")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up file: $path", e)
        }
    }

    companion object {
        private const val TAG = "UpdateReceiver"
    }
}
