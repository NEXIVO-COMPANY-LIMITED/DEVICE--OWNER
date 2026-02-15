package com.example.deviceowner.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Manages automatic app updates from GitHub Releases.
 * Fetches latest release, downloads APK, and installs silently (Device Owner).
 */
class GitHubUpdateManager(private val context: Context) {

    private val appContext = context.applicationContext
    private val notificationManager = NotificationManagerCompat.from(appContext)
    private var notificationBuilder: NotificationCompat.Builder? = null

    init {
        createNotificationChannel()
    }

    /**
     * Main method to check and install updates from GitHub.
     */
    fun checkAndUpdate() {
        try {
            Log.d(TAG, "Starting update check from GitHub...")

            val currentVersion = getCurrentVersion()
            Log.d(TAG, "Current version: $currentVersion")

            val latestRelease = fetchLatestRelease()

            if (latestRelease == null) {
                Log.e(TAG, "Failed to fetch latest release")
                showNotification("Update Check Failed", "Could not connect to GitHub")
                return
            }

            Log.d(TAG, "Latest version on GitHub: ${latestRelease.version}")
            Log.d(TAG, "Download URL: ${latestRelease.downloadUrl}")

            if (isUpdateAvailable(currentVersion, latestRelease.version)) {
                Log.d(TAG, "Update available! Starting download...")
                showNotification("Update Available", "Downloading version ${latestRelease.version}")

                downloadAndInstallUpdate(latestRelease)
            } else {
                Log.d(TAG, "App is up to date")
                showNotification("App Up to Date", "No updates available")

                Thread {
                    try {
                        Thread.sleep(3000)
                        notificationManager.cancel(UpdateConfig.NOTIFICATION_ID)
                    } catch (e: InterruptedException) {
                        Log.e(TAG, "Notification dismiss interrupted", e)
                    }
                }.start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during update check", e)
            showNotification("Update Error", "Error: ${e.message}")
        }
    }

    /**
     * Fetch latest release info from GitHub API.
     */
    private fun fetchLatestRelease(): GitHubRelease? {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(UpdateConfig.GITHUB_API_URL)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.setRequestProperty("User-Agent", "NEXIVO-DeviceOwner-Updater")

            val responseCode = connection.responseCode
            Log.d(TAG, "GitHub API response code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.use { inputStream ->
                    val response = convertStreamToString(inputStream)
                    return parseGitHubRelease(response)
                }
            } else {
                Log.e(TAG, "GitHub API returned error: $responseCode")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching GitHub release", e)
        } finally {
            connection?.disconnect()
        }
        return null
    }

    /**
     * Parse GitHub release JSON using Gson.
     */
    private fun parseGitHubRelease(json: String): GitHubRelease? {
        return try {
            val gson = Gson()
            val releaseObj = gson.fromJson(json, JsonObject::class.java)

            val release = GitHubRelease()
            release.version = releaseObj.get("tag_name")?.asString?.replace("V", "")?.replace("v", "") ?: "0.0"
            release.name = releaseObj.get("name")?.asString ?: ""

            val assets = releaseObj.getAsJsonArray("assets") ?: return null
            for (i in 0 until assets.size()) {
                val asset = assets.get(i).asJsonObject
                val assetName = asset.get("name")?.asString ?: ""

                if (assetName == UpdateConfig.APK_FILE_NAME || assetName.endsWith(".apk")) {
                    release.downloadUrl = asset.get("browser_download_url")?.asString ?: ""
                    release.fileSize = asset.get("size")?.asLong ?: 0L
                    Log.d(TAG, "Found APK: ${release.downloadUrl}")
                    break
                }
            }

            if (release.downloadUrl.isNullOrEmpty()) {
                Log.e(TAG, "No APK file found in release assets")
                return null
            }

            release
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing GitHub release JSON", e)
            null
        }
    }

    /**
     * Download APK and install it.
     */
    private fun downloadAndInstallUpdate(release: GitHubRelease) {
        val apkFile = File(appContext.filesDir, "update-${release.version}.apk")

        try {
            Log.d(TAG, "Downloading from: ${release.downloadUrl}")
            downloadFile(release.downloadUrl!!, apkFile, release.fileSize)

            Log.d(TAG, "Download complete. File size: ${apkFile.length()} bytes")

            if (!apkFile.exists() || apkFile.length() == 0L) {
                throw Exception("Downloaded file is empty or doesn't exist")
            }

            installUpdate(apkFile, release.version)
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading/installing update", e)
            showNotification("Update Failed", "Error: ${e.message}")

            if (apkFile.exists()) {
                apkFile.delete()
            }
        }
    }

    /**
     * Download file with progress tracking.
     */
    private fun downloadFile(downloadUrl: String, outputFile: File, totalSize: Long) {
        var connection: HttpURLConnection? = null
        var input: BufferedInputStream? = null
        var output: java.io.FileOutputStream? = null

        try {
            val url = URL(downloadUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty("User-Agent", "NEXIVO-DeviceOwner-Updater")
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("Server returned HTTP ${connection.responseCode}")
            }

            var size = totalSize
            if (size <= 0L) {
                size = connection.contentLengthLong
            }

            input = BufferedInputStream(connection.inputStream)
            output = java.io.FileOutputStream(outputFile)

            val buffer = ByteArray(8192)
            var downloadedSize = 0L
            var bytesRead: Int
            var lastProgress = 0

            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                downloadedSize += bytesRead

                if (size > 0) {
                    val progress = ((downloadedSize * 100) / size).toInt()
                    if (progress >= lastProgress + 5) {
                        lastProgress = progress
                        updateNotificationProgress(progress)
                        Log.d(TAG, "Download progress: $progress%")
                    }
                }
            }

            output.flush()
            updateNotificationProgress(100)
            Log.d(TAG, "Download completed successfully")
        } finally {
            output?.close()
            input?.close()
            connection?.disconnect()
        }
    }

    /**
     * Install APK using PackageInstaller (Device Owner silent install).
     */
    private fun installUpdate(apkFile: File, version: String) {
        try {
            Log.d(TAG, "Installing update version: $version")
            showNotification("Installing Update", "Installing version $version")

            val packageInstaller = appContext.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)

            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)

            session.openWrite("package", 0, -1).use { out ->
                FileInputStream(apkFile).use { input ->
                    val buffer = ByteArray(65536)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        out.write(buffer, 0, bytesRead)
                    }
                }
                session.fsync(out)
            }

            val intent = Intent(appContext, UpdateReceiver::class.java).apply {
                action = UpdateConfig.ACTION_INSTALL_COMPLETE
                putExtra("version", version)
                putExtra("apkPath", apkFile.absolutePath)
            }

            val pendingIntent = android.app.PendingIntent.getBroadcast(
                appContext,
                0,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
            )

            session.commit(pendingIntent.intentSender)
            session.close()

            Log.d(TAG, "Installation session committed")
        } catch (e: Exception) {
            Log.e(TAG, "Error installing update", e)
            showNotification("Installation Failed", "Error: ${e.message}")
        }
    }

    /**
     * Compare version strings (e.g., "1.0" vs "2.0").
     */
    private fun isUpdateAvailable(currentVersion: String, latestVersion: String): Boolean {
        return try {
            val current = currentVersion.replace("V", "").replace("v", "")
            val latest = latestVersion.replace("V", "").replace("v", "")

            val currentParts = current.split(".")
            val latestParts = latest.split(".")
            val length = maxOf(currentParts.size, latestParts.size)

            for (i in 0 until length) {
                val currentPart = if (i < currentParts.size) {
                    currentParts[i].replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
                } else 0
                val latestPart = if (i < latestParts.size) {
                    latestParts[i].replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
                } else 0

                when {
                    latestPart > currentPart -> return true
                    latestPart < currentPart -> return false
                }
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error comparing versions", e)
            false
        }
    }

    private fun getCurrentVersion(): String {
        return try {
            val pInfo: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.packageManager.getPackageInfo(appContext.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            }
            pInfo.versionName ?: "0.0"
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Could not get current version", e)
            "0.0"
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                UpdateConfig.NOTIFICATION_CHANNEL_ID,
                UpdateConfig.NOTIFICATION_CHANNEL_NAME,
                android.app.NotificationManager.IMPORTANCE_DEFAULT
            )
            channel.description = "Notifications for app updates"
            val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(title: String, message: String) {
        notificationBuilder = NotificationCompat.Builder(appContext, UpdateConfig.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(false)

        notificationManager.notify(UpdateConfig.NOTIFICATION_ID, notificationBuilder!!.build())
    }

    private fun updateNotificationProgress(progress: Int) {
        notificationBuilder?.let { builder ->
            builder
                .setProgress(100, progress, false)
                .setContentText("Downloading... $progress%")
            notificationManager.notify(UpdateConfig.NOTIFICATION_ID, builder.build())
        }
    }

    private fun convertStreamToString(inputStream: InputStream): String {
        return inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    }

    private class GitHubRelease {
        var version: String = ""
        var name: String = ""
        var downloadUrl: String? = null
        var fileSize: Long = 0L
    }

    companion object {
        private const val TAG = "GitHubUpdateManager"
    }
}
