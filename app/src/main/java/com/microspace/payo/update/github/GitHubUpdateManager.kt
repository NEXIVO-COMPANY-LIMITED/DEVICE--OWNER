package com.microspace.payo.update.github

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.microspace.payo.data.DeviceIdProvider
import com.microspace.payo.data.repository.DeviceRegistrationRepository
import com.microspace.payo.services.reporting.ServerBugAndLogReporter
import com.microspace.payo.update.config.UpdateConfig
import com.microspace.payo.update.receiver.UpdateReceiver
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Manages automatic app updates from GitHub Releases.
 * Fixes: INSTALL_FAILED_VERIFICATION_FAILURE by using Policy Authority.
 */
class GitHubUpdateManager(private val context: Context) {

    private val appContext = context.applicationContext
    private val registrationRepository = DeviceRegistrationRepository(appContext)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun checkAndUpdate() {
        CoroutineScope(Dispatchers.IO).launch {
            val deviceId = DeviceIdProvider.getDeviceId(appContext)
            try {
                Log.d(TAG, "Checking for updates...")
                val currentVersion = getCurrentVersion()
                val latestRelease = fetchLatestReleaseWithRetry(deviceId)

                if (latestRelease == null) {
                    Log.w(TAG, "Could not fetch latest release after retries.")
                    return@launch
                }

                if (isNewUpdateAvailable(currentVersion, latestRelease.version)) {
                    Log.i(TAG, "ðŸš€ New version ${latestRelease.version} available.")
                    downloadAndInstallUpdate(latestRelease, deviceId)
                } else {
                    Log.d(TAG, "âœ… Up to date (v$currentVersion).")
                }
            } catch (e: Exception) {
                handleException(e, "checkAndUpdate", deviceId)
            }
        }
    }

    private fun isNewUpdateAvailable(current: String, latest: String): Boolean {
        if (current == latest) return false
        return try {
            val currentClean = current.replace(Regex("[^0-9.]"), "").split(".").map { it.toIntOrNull() ?: 0 }
            val latestClean = latest.replace(Regex("[^0-9.]"), "").split(".").map { it.toIntOrNull() ?: 0 }
            val maxLength = maxOf(currentClean.size, latestClean.size)
            for (i in 0 until maxLength) {
                val c = currentClean.getOrElse(i) { 0 }
                val l = latestClean.getOrElse(i) { 0 }
                if (l > c) return true
                if (l < c) return false
            }
            false
        } catch (e: Exception) { false }
    }

    private fun verifyDownloadedApkMark(apkFile: File): Boolean {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.packageManager.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                appContext.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
            }
            isNewUpdateAvailable(getCurrentVersion(), pInfo?.versionName ?: "")
        } catch (e: Exception) { false }
    }

    private fun getCurrentVersion(): String {
        return try {
            val pInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            pInfo.versionName ?: "0.0.0"
        } catch (e: Exception) { "0.0.0" }
    }

    private suspend fun fetchLatestReleaseWithRetry(deviceId: String?, retries: Int = 3, delayMillis: Long = 5000): GitHubRelease? {
        for (i in 1..retries) {
            try {
                return fetchLatestRelease(deviceId)
            } catch (e: IOException) {
                Log.w(TAG, "Attempt $i to fetch release failed: ${e.message}. Retrying in ${delayMillis / 1000}s...")
                if (i < retries) delay(delayMillis)
            }
        }
        return null
    }

    private fun fetchLatestRelease(deviceId: String?): GitHubRelease? {
        val request = Request.Builder()
            .url(UpdateConfig.GITHUB_API_URL)
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "NEXIVO-Updater")
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")

                val responseBody = response.body?.string()
                if (responseBody.isNullOrEmpty()) throw IOException("Empty response body")

                val releaseObj = Gson().fromJson(responseBody, JsonObject::class.java)
                val release = GitHubRelease()
                release.version = releaseObj.get("tag_name")?.asString?.replace(Regex("[Vv]"), "") ?: "0.0.0"
                val assets = releaseObj.getAsJsonArray("assets")
                for (i in 0 until (assets?.size() ?: 0)) {
                    val asset = assets.get(i).asJsonObject
                    if (asset.get("name")?.asString?.endsWith(".apk") == true) {
                        release.downloadUrl = asset.get("browser_download_url")?.asString
                        break
                    }
                }
                return if (release.downloadUrl != null) release else null
            }
        } catch (e: Exception) {
            handleException(e, "fetchLatestRelease", deviceId)
        }
        return null
    }


    private fun downloadAndInstallUpdate(release: GitHubRelease, deviceId: String?) {
        val apkFile = File(appContext.filesDir, "update_v${release.version}.apk")
        try {
            downloadFile(release.downloadUrl!!, apkFile, deviceId)
            if (apkFile.exists() && apkFile.length() > 0) {
                if (!verifyDownloadedApkMark(apkFile)) {
                    apkFile.delete()
                    return
                }
                CoroutineScope(Dispatchers.IO).launch {
                    registrationRepository.backupRegistrationData()
                    backupCurrentApk()
                    installUpdate(apkFile, release.version)
                }
            }
        } catch (e: Exception) { 
            Log.e(TAG, "Download failed: ${e.message}") 
            handleException(e, "downloadAndInstallUpdate", deviceId)
        }
    }

    private fun backupCurrentApk() {
        try {
            val backupFile = File(appContext.filesDir, "rollback_backup.apk")
            FileInputStream(File(appContext.packageCodePath)).use { input ->
                FileOutputStream(backupFile).use { output -> input.copyTo(output) }
            }
        } catch (e: Exception) {}
    }

    private fun downloadFile(urlStr: String, outputFile: File, deviceId: String?) {
        val request = Request.Builder().url(urlStr).build()
        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")
                FileOutputStream(outputFile).use { output ->
                    response.body?.byteStream()?.copyTo(output)
                }
            }
        } catch (e: Exception) {
            handleException(e, "downloadFile", deviceId)
        }
    }

    private fun installUpdate(apkFile: File, version: String) {
        try {
            val packageInstaller = appContext.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)

            // Bypass Samsung/Google verification blocks by asserting Policy Authority
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                params.setInstallReason(PackageManager.INSTALL_REASON_POLICY)
            }

            val sessionId = packageInstaller.createSession(params)
            packageInstaller.openSession(sessionId).use { session ->
                session.openWrite("package", 0, -1).use { out ->
                    FileInputStream(apkFile).use { input -> input.copyTo(out) }
                    session.fsync(out)
                }

                val intent = Intent(appContext, UpdateReceiver::class.java).apply {
                    action = UpdateConfig.ACTION_INSTALL_COMPLETE
                    putExtra("version", version)
                    putExtra("apkPath", apkFile.absolutePath)
                    putExtra("rollbackApkPath", File(appContext.filesDir, "rollback_backup.apk").absolutePath)
                }

                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }

                val pendingIntent = PendingIntent.getBroadcast(appContext, 0, intent, flags)
                session.commit(pendingIntent.intentSender)
            }
            Log.i(TAG, "âœ… Update session committed for v$version")
        } catch (e: Exception) {
            Log.e(TAG, "Install error: ${e.message}")
        }
    }

    private fun handleException(e: Exception, source: String, deviceId: String?) {
        if (e is UnknownHostException || e is SocketTimeoutException || e is IOException) {
            Log.w(TAG, "Network issue during $source: ${e.message}")
        } else {
            Log.e(TAG, "Error in $source: ${e.message}")
            ServerBugAndLogReporter.postException(
                throwable = e,
                contextMessage = "Update: $source failed",
                explicitDeviceId = deviceId
            )
        }
    }

    private class GitHubRelease {
        var version: String = ""
        var downloadUrl: String? = null
    }

    companion object {
        private const val TAG = "GitHubUpdateManager"
    }
}




