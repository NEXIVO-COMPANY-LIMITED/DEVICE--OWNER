package com.example.deviceowner.services.reporting

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.deviceowner.data.models.BugReportRequest
import com.example.deviceowner.data.models.DeviceLogRequest
import com.example.deviceowner.data.remote.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Posts bugs and logs to backend tech API. Useful for backend to see device issues and tamper.
 *
 * Endpoints (sponsa_backend):
 * - POST /api/tech/devicecategory/logs/ – device logs (LogType, Loglevel, message, device_id)
 * - POST /api/tech/devicecategory/bugs/ – bug reports (title, message, device, priority)
 *
 * Usage:
 * - Logs: setOnRemoteLogCallback in Application wires LogManager → postLog (Error/Warning).
 * - Bugs: postBug(title, message) or postException(throwable) from anywhere (e.g. catch blocks, "Report bug" UI).
 * - Init: ServerBugAndLogReporter.init(context) in Application.onCreate (already done).
 */
object ServerBugAndLogReporter {

    private const val TAG = "ServerBugAndLogReporter"
    private const val MAX_MESSAGE_LENGTH = 8000
    private const val MAX_TITLE_LENGTH = 250

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val apiClient by lazy { ApiClient() }

    /**
     * Call from Application.onCreate() so reporter can resolve device ID.
     * Optional; posting works without init (uses UNREGISTERED device id).
     */
    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun getDeviceId(): String {
        val ctx = appContext ?: return "UNREGISTERED-${Build.MODEL ?: "unknown"}"
        return try {
            val id = com.example.deviceowner.utils.SharedPreferencesManager(ctx).getDeviceIdForHeartbeat()
                ?: ctx.getSharedPreferences("device_registration", Context.MODE_PRIVATE).getString("device_id", null)
                ?: ctx.getSharedPreferences("device_data", Context.MODE_PRIVATE).getString("device_id_for_heartbeat", null)
            id?.trim()?.takeIf { it.isNotEmpty() } ?: "UNREGISTERED-${Build.MANUFACTURER}-${Build.MODEL}"
        } catch (e: Exception) {
            "UNREGISTERED-${Build.MODEL ?: "unknown"}"
        }
    }

    private fun getDeviceInfoString(): String {
        val ctx = appContext
        val deviceId = getDeviceId()
        return if (ctx != null) {
            "$deviceId | ${Build.MANUFACTURER} ${Build.MODEL} | Android ${Build.VERSION.SDK_INT}"
        } else {
            "$deviceId | ${Build.MANUFACTURER} ${Build.MODEL}"
        }
    }

    /**
     * Map app log category to backend LogType.
     * Backend: heartbeat, registration, installation, authentication, data_sync, error, system, security, network, other
     */
    fun categoryToLogType(category: String): String {
        return when (category) {
            "DEVICE_REGISTRATION" -> "registration"
            "HEARTBEAT" -> "heartbeat"
            "API_CALLS" -> "system"
            "SECURITY" -> "security"
            "PROVISIONING" -> "installation"
            "SYNC" -> "data_sync"
            "ERRORS", "GENERAL" -> "error"
            "DEVICE_OWNER", "DEVICE_INFO", "JSON_LOGS" -> "system"
            else -> "other"
        }
    }

    /**
     * Map app log level to backend Loglevel: Normal, Warning, Error
     */
    fun levelToLoglevel(level: String): String {
        return when (level.uppercase()) {
            "ERROR" -> "Error"
            "WARN", "WARNING" -> "Warning"
            else -> "Normal"
        }
    }

    /**
     * Truncate string to avoid server rejection or huge payloads.
     */
    private fun truncate(s: String, maxLen: Int): String {
        return if (s.length <= maxLen) s else s.take(maxLen - 3) + "..."
    }

    /**
     * Post a log entry to the server. Fire-and-forget; never throws.
     * Call from LogManager when level is Error or Warning (or any level if you want).
     */
    fun postLog(
        logType: String,
        logLevel: String,
        message: String,
        extraData: Map<String, Any?>? = null
    ) {
        scope.launch {
            try {
                val deviceId = getDeviceId()
                val body = DeviceLogRequest(
                    deviceId = deviceId,
                    logType = logType,
                    message = truncate(message, MAX_MESSAGE_LENGTH),
                    logLevel = logLevel,
                    extraData = extraData
                )
                val response = apiClient.postDeviceLog(body)
                if (response.isSuccessful) {
                    Log.d(TAG, "Log posted to server: type=$logType level=$logLevel")
                } else {
                    Log.w(TAG, "Log post failed: HTTP ${response.code()} ${response.errorBody()?.string()?.take(200)}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Log post error (non-fatal): ${e.message}")
            }
        }
    }

    /**
     * Post a bug report (e.g. uncaught exception) to the server. Fire-and-forget; never throws.
     */
    fun postBug(
        title: String,
        message: String,
        deviceInfo: String? = null,
        priority: String = "medium",
        extraData: Map<String, Any?>? = null
    ) {
        scope.launch {
            try {
                val device = deviceInfo ?: getDeviceInfoString()
                val body = BugReportRequest(
                    title = truncate(title, MAX_TITLE_LENGTH),
                    message = truncate(message, MAX_MESSAGE_LENGTH),
                    device = truncate(device, 500),
                    priority = priority.lowercase().takeIf { it in setOf("low", "medium", "high", "critical") } ?: "medium",
                    extraData = extraData
                )
                val response = apiClient.postBugReport(body)
                if (response.isSuccessful) {
                    Log.d(TAG, "Bug report posted to server: ${body.title}")
                } else {
                    Log.w(TAG, "Bug post failed: HTTP ${response.code()} ${response.errorBody()?.string()?.take(200)}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Bug post error (non-fatal): ${e.message}")
            }
        }
    }

    /**
     * Post an exception as a bug report. Stack trace is included in message and extra_data.
     * Never throws.
     */
    fun postException(
        throwable: Throwable,
        contextMessage: String? = null
    ) {
        scope.launch {
            try {
                val title = throwable.javaClass.simpleName
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val stackTrace = sw.toString()
                val message = buildString {
                    if (!contextMessage.isNullOrBlank()) append(contextMessage).append("\n\n")
                    append(throwable.message ?: "No message")
                    append("\n\nStack trace:\n")
                    append(truncate(stackTrace, MAX_MESSAGE_LENGTH - 200))
                }
                val extra = mapOf<String, Any?>(
                    "exception_class" to throwable.javaClass.name,
                    "thread" to (Thread.currentThread().name ?: "unknown")
                )
                postBug(
                    title = title,
                    message = message,
                    deviceInfo = getDeviceInfoString(),
                    priority = "high",  // Django: low, medium, high, critical (lowercase)
                    extraData = extra
                )
            } catch (e: Exception) {
                Log.w(TAG, "postException failed (non-fatal): ${e.message}")
            }
        }
    }
}
