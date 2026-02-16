package com.example.deviceowner.update.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import android.widget.Toast
import java.io.File

/**
 * Receives PackageInstaller callbacks when an update installation completes.
 * Cleans up the downloaded APK file and notifies the user.
 */
class UpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val version = intent.getStringExtra("version")
        val apkPath = intent.getStringExtra("apkPath")

        Log.d(TAG, "Installation status: $status, message: $message")

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                Log.d(TAG, "Installation pending user action")
                @Suppress("DEPRECATION")
                val confirmIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirmIntent?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(it)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start confirmation activity", e)
                    }
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                Log.d(TAG, "Installation successful! Version: $version")
                Toast.makeText(context, "Update installed successfully: v$version", Toast.LENGTH_LONG).show()
                cleanupApkFile(apkPath)
            }

            PackageInstaller.STATUS_FAILURE,
            PackageInstaller.STATUS_FAILURE_ABORTED,
            PackageInstaller.STATUS_FAILURE_BLOCKED,
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
            PackageInstaller.STATUS_FAILURE_INVALID,
            PackageInstaller.STATUS_FAILURE_STORAGE -> {
                Log.e(TAG, "Installation failed: $message")
                Toast.makeText(context, "Update failed: $message", Toast.LENGTH_LONG).show()
                cleanupApkFile(apkPath)
            }

            else -> Log.d(TAG, "Unknown installation status: $status")
        }
    }

    private fun cleanupApkFile(apkPath: String?) {
        apkPath?.let { path ->
            val apkFile = File(path)
            if (apkFile.exists()) {
                val deleted = apkFile.delete()
                Log.d(TAG, "APK cleanup: ${if (deleted) "success" else "failed"}")
            }
        }
    }

    companion object {
        private const val TAG = "UpdateReceiver"
    }
}
