package com.microspace.payo.services.reporting

import android.content.Context
import android.os.Build
import android.util.Log
import com.microspace.payo.core.device.DeviceDataCollector
import com.microspace.payo.data.DeviceIdProvider
import com.microspace.payo.data.models.tech.BugReportRequest
import com.microspace.payo.data.models.tech.DeviceLogRequest
import com.microspace.payo.data.remote.ApiClient
import com.microspace.payo.utils.storage.SharedPreferencesManager
import kotlinx.coroutines.*
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Posts bugs and logs to backend tech API with enriched device and company context.
 * Follows the binding strategy from DEVICE_AND_COMPANY_INFO_FOR_LOGS.md
 */
object ServerBugAndLogReporter {

    private const val TAG = "ServerBugAndLogReporter"
    private const val MAX_MESSAGE_LENGTH = 8000
    private const val MAX_TITLE_LENGTH = 250

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val apiClient by lazy { ApiClient() }

    @Volatile
    private var appContext: Context? = null
    private var cachedDeviceId: String? = null
    private var dataCollector: DeviceDataCollector? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        dataCollector = DeviceDataCollector(appContext!!)
        
        // Proactively fetch and cache the device ID
        scope.launch {
            cachedDeviceId = DeviceIdProvider.getDeviceId(appContext!!)
            if (cachedDeviceId != null) {
                Log.i(TAG, "Cached Device ID for bug reporting: $cachedDeviceId")
            }
        }
    }

    private suspend fun getDeviceIdAsync(): String? {
        if (cachedDeviceId != null) return cachedDeviceId
        val ctx = appContext ?: return null
        val deviceId = DeviceIdProvider.getDeviceId(ctx)
        if (deviceId != null) {
            cachedDeviceId = deviceId
        }
        return deviceId
    }

    /**
     * Gathers a comprehensive context object for binding with logs.
     */
    private suspend fun gatherBindingContext(): Map<String, Any?> {
        val ctx = appContext ?: return emptyMap()
        val prefs = SharedPreferencesManager(ctx)
        
        val deviceInfo = mutableMapOf<String, Any?>(
            "serial_number" to (prefs.getSerialNumber() ?: Build.SERIAL),
            "device_type" to (prefs.getDeviceType() ?: "phone"),
            "manufacturer" to Build.MANUFACTURER,
            "model" to Build.MODEL,
            "os_version" to Build.VERSION.RELEASE,
            "sdk_version" to Build.VERSION.SDK_INT
        )

        val deviceStatus = mutableMapOf<String, Any?>(
            "is_online" to true, // If we are sending a log, we are online
            "installation_completed" to prefs.isRegistrationComplete(),
            "loan_status" to (prefs.getRegistrationStatus() ?: "unknown")
        )

        val companyInfo = mutableMapOf<String, Any?>(
            "company_name" to prefs.getOrganizationName(),
            "company_code" to (prefs.getLoanNumber()?.take(5) ?: "unknown"),
            "app_version" to getAppVersion()
        )

        return mapOf(
            "device_info" to deviceInfo,
            "device_status" to deviceStatus,
            "company_info" to companyInfo,
            "timestamp" to getCurrentTimestamp()
        )
    }

    private fun getAppVersion(): String {
        return try {
            val pInfo = appContext?.packageManager?.getPackageInfo(appContext!!.packageName, 0)
            pInfo?.versionName ?: "1.0.0"
        } catch (e: Exception) { "1.0.0" }
    }

    private fun getCurrentTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    fun postLog(
        logType: String,
        logLevel: String,
        message: String,
        extraData: Map<String, Any?>? = null,
        explicitDeviceId: String? = null
    ) {
        scope.launch {
            try {
                val deviceId = explicitDeviceId ?: getDeviceIdAsync()
                
                // CRITICAL: Don't send logs if device not registered
                if (deviceId.isNullOrBlank()) {
                    Log.w(TAG, "âš ï¸ Cannot send log: device not registered (no device_id)")
                    return@launch
                }
                
                // CRITICAL: Don't send logs if device_id is locally generated
                if (deviceId.startsWith("ANDROID-") || deviceId.startsWith("UNREGISTERED-")) {
                    Log.w(TAG, "âš ï¸ Cannot send log: device_id is locally generated: $deviceId")
                    return@launch
                }
                
                Log.d(TAG, "ðŸ“¤ Sending log with device_id: $deviceId")
                
                // Merge context with provided extraData
                val enrichedExtraData = gatherBindingContext().toMutableMap()
                extraData?.let { enrichedExtraData.putAll(it) }

                val body = DeviceLogRequest(
                    deviceId = deviceId,
                    logType = logType,
                    message = if (message.length <= MAX_MESSAGE_LENGTH) message else message.take(MAX_MESSAGE_LENGTH - 3) + "...",
                    logLevel = logLevel,
                    extraData = enrichedExtraData
                )
                apiClient.postDeviceLog(body)
                Log.i(TAG, "âœ… Log sent successfully")
            } catch (e: Exception) {
                Log.w(TAG, "Log post error: ${e.message}")
            }
        }
    }

    fun postBug(
        title: String,
        message: String,
        deviceInfo: String? = null,
        priority: String = "medium",
        extraData: Map<String, Any?>? = null,
        explicitDeviceId: String? = null
    ) {
        scope.launch {
            try {
                val deviceId = explicitDeviceId ?: getDeviceIdAsync()
                
                // CRITICAL: Don't send bug reports if device not registered
                if (deviceId.isNullOrBlank()) {
                    Log.w(TAG, "âš ï¸ Cannot send bug report: device not registered (no device_id)")
                    return@launch
                }
                
                // CRITICAL: Don't send bug reports if device_id is locally generated
                if (deviceId.startsWith("ANDROID-") || deviceId.startsWith("UNREGISTERED-")) {
                    Log.w(TAG, "âš ï¸ Cannot send bug report: device_id is locally generated: $deviceId")
                    return@launch
                }
                
                Log.d(TAG, "ðŸ“¤ Sending bug report with device_id: $deviceId")
                
                val deviceDescriptor = deviceInfo ?: "$deviceId | ${Build.MANUFACTURER} ${Build.MODEL}"
                
                // Merge context with provided extraData
                val enrichedExtraData = gatherBindingContext().toMutableMap()
                extraData?.let { enrichedExtraData.putAll(it) }

                val body = BugReportRequest(
                    title = if (title.length <= MAX_TITLE_LENGTH) title else title.take(MAX_TITLE_LENGTH - 3) + "...",
                    message = if (message.length <= MAX_MESSAGE_LENGTH) message else message.take(MAX_MESSAGE_LENGTH - 3) + "...",
                    device = deviceDescriptor,
                    priority = priority.lowercase(),
                    extraData = enrichedExtraData
                )
                apiClient.postBugReport(body)
                Log.i(TAG, "âœ… Bug report sent successfully")
            } catch (e: Exception) {
                Log.w(TAG, "Bug post error: ${e.message}")
            }
        }
    }

    fun postException(
        throwable: Throwable,
        contextMessage: String? = null,
        explicitDeviceId: String? = null
    ) {
        scope.launch {
            try {
                val title = throwable.javaClass.simpleName
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))

                val message = if (!contextMessage.isNullOrBlank()) "$contextMessage: ${throwable.message}" else (throwable.message ?: "No message")
                
                val errorDetails = mapOf(
                    "exception" to throwable.javaClass.name,
                    "stacktrace" to sw.toString()
                )

                postBug(
                    title = title,
                    message = message,
                    deviceInfo = null,
                    priority = "high",
                    extraData = mapOf("error_details" to errorDetails),
                    explicitDeviceId = explicitDeviceId
                )
            } catch (e: Exception) {}
        }
    }
}




