package com.example.deviceowner.utils

import android.content.Context
import com.example.deviceowner.data.remote.api.ServerReturnedHtmlException
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes registration errors to a folder on the phone as HTML files.
 * Folder: DeviceOwner/RegistrationErrors (under app external files dir).
 * Helps Device Owner / support understand what caused the error and how to fix it.
 */
object RegistrationErrorLogger {

    private const val TAG = "RegistrationErrorLogger"
    private const val FOLDER_NAME = "DeviceOwner/RegistrationErrors"
    private const val FILE_PREFIX = "registration_error_"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    /**
     * Get the folder where registration error HTML files are stored.
     * Path on device: Android/data/<package>/files/DeviceOwner/RegistrationErrors/
     */
    fun getErrorFolder(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        val folder = File(base, FOLDER_NAME)
        if (!folder.exists()) folder.mkdirs()
        return folder
    }

    /**
     * Get the absolute path of the error folder (for display / Toast).
     */
    fun getErrorFolderPath(context: Context): String = getErrorFolder(context).absolutePath

    /**
     * List all registration error HTML files (newest first).
     */
    fun listErrorFiles(context: Context): List<File> {
        val folder = getErrorFolder(context)
        if (!folder.exists()) return emptyList()
        return folder.listFiles { _, name -> name.endsWith(".html") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    /**
     * Log a registration error to an HTML file in the folder.
     * @param context Application/Activity context
     * @param errorType Short label (e.g. "ClassCastException", "Network error")
     * @param errorMessage Exception message or description
     * @param throwable Optional exception for stack trace
     * @param deviceId Optional device ID for context
     * @param loanNumber Optional loan number for context
     * @param serverResponse Optional raw server response (e.g. error body)
     * @param additionalInfo Optional map of additional debug information
     */
    fun logRegistrationError(
        context: Context,
        errorType: String,
        errorMessage: String,
        throwable: Throwable? = null,
        deviceId: String? = null,
        loanNumber: String? = null,
        serverResponse: String? = null,
        additionalInfo: Map<String, String>? = null
    ): File? {
        val (causeExplanation, fixSteps) = getCauseAndFix(errorType, throwable)
        val stackTrace = throwable?.let { android.util.Log.getStackTraceString(it) } ?: ""
        val timestamp = displayDateFormat.format(Date())
        val fileName = "${FILE_PREFIX}${dateFormat.format(Date())}.html"
        val folder = getErrorFolder(context)
        val file = File(folder, fileName)

        val html = buildHtmlReport(
            timestamp = timestamp,
            errorType = errorType,
            errorMessage = errorMessage,
            causeExplanation = causeExplanation,
            fixSteps = fixSteps,
            stackTrace = stackTrace,
            deviceId = deviceId,
            loanNumber = loanNumber,
            serverResponse = serverResponse,
            additionalInfo = additionalInfo
        )

        return try {
            FileOutputStream(file).use { it.write(html.toByteArray(Charsets.UTF_8)) }
            Log.d(TAG, "Registration error saved: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write registration error HTML", e)
            null
        }
    }

    private fun getCauseAndFix(errorType: String, throwable: Throwable?): Pair<String, String> {
        return when {
            throwable is ServerReturnedHtmlException -> Pair(
                "The server responded with an HTML page (e.g. 404 Not Found or 500 Server Error) instead of JSON. The API URL may be wrong or the endpoint may not be deployed.",
                "1. Verify BASE_URL in app config matches the backend (host, scheme, trailing slash).\n2. Ensure the registration endpoint (api/devices/register/) is deployed and reachable.\n3. Run Network Diagnostics in the app to see the HTML preview and confirm the server error."
            )
            errorType.contains("ServerReturnedHtml", ignoreCase = true) -> Pair(
                "The server responded with HTML instead of JSON. Check BASE_URL and that the registration API is deployed.",
                "1. Verify BASE_URL and run Network Diagnostics in the app.\n2. Ensure api/devices/register/ is reachable and returns JSON."
            )
            throwable is ClassCastException -> Pair(
                "The server returned HTTP 200 but the response body is not in the format the app expects (DeviceRegistrationResponse). For example, the API might return {\"detail\": \"...\"} or {\"error\": \"...\"} instead of fields like success, message, device, loan.",
                "1. On the backend: Ensure the registration API returns JSON that matches DeviceRegistrationResponse (success, message, device, loan, registered_by, etc.).\n2. Or update the app model DeviceRegistrationResponse to match the actual API response.\n3. Check API version and base URL; wrong endpoint can return a different format."
            )
            throwable is java.net.UnknownHostException -> Pair(
                "The device cannot resolve the server hostname. Usually means no internet, wrong DNS, or the server URL is incorrect.",
                "1. Check Wi‑Fi or mobile data is on and working.\n2. Verify BASE_URL in app config (e.g. BuildConfig.BASE_URL) is correct.\n3. Try opening the API URL in a browser on the same network."
            )
            throwable is java.net.ConnectException -> Pair(
                "The app could not connect to the server. Server may be down, firewall blocking, or wrong port/host.",
                "1. Ensure the backend server is running and reachable from the device network.\n2. Check firewall/security rules allow the app to connect.\n3. Verify BASE_URL (host and port) in the app."
            )
            throwable is java.net.SocketTimeoutException -> Pair(
                "The server took too long to respond. Connection or read timeout was reached.",
                "1. Check server performance and network latency.\n2. Increase timeout in ApiClient (connectTimeout/readTimeout) if server is slow.\n3. Ensure backend responds within the timeout (e.g. 30 seconds)."
            )
            throwable is retrofit2.HttpException -> Pair(
                "The server returned an HTTP error (4xx or 5xx). The response body may contain the reason (e.g. validation error, forbidden, server error).",
                "1. Check serverResponse in this file for the exact error body.\n2. Fix validation or permissions on the backend for the status code shown.\n3. For 5xx, check server logs and fix the backend bug."
            )
            throwable is com.google.gson.JsonSyntaxException -> Pair(
                "The server response is not valid JSON, or the JSON structure does not match the expected types (e.g. string vs number, wrong nesting).",
                "1. Ensure the API returns valid JSON.\n2. Match response fields and types to DeviceRegistrationResponse in the app.\n3. Check for BOM or non-JSON content in the response."
            )
            throwable is SecurityException -> Pair(
                "A required permission was denied (e.g. READ_PHONE_STATE, location). Or the app is not Device Owner when it tried to use Device Owner APIs.",
                "1. Grant the requested permissions in Settings > App > Permissions.\n2. For Device Owner features: ensure the app is set as Device Owner (provisioning).\n3. Do not revoke permissions needed for registration."
            )
            errorType.contains("ClassCast", ignoreCase = true) -> Pair(
                "The server returned data in a format the app did not expect. Gson or the app tried to cast a value to the wrong type (e.g. message as array instead of string).",
                "1. Align backend response with DeviceRegistrationResponse: same field names and types.\n2. Add ProGuard keep rules for data models if using release build.\n3. Test with the same API URL and version the app expects."
            )
            errorType.contains("Network", ignoreCase = true) || errorType.contains("Connection", ignoreCase = true) -> Pair(
                "Network or connection problem: no internet, wrong URL, or server not reachable.",
                "1. Turn on Wi‑Fi or mobile data and retry.\n2. Verify BASE_URL and that the server is running.\n3. Try from another network to rule out firewall/carrier blocking."
            )
            else -> Pair(
                "An unexpected error occurred during registration. The stack trace and message below give technical details.",
                "1. Read the error message and stack trace below.\n2. Fix the backend or app code at the reported line.\n3. If unclear, share this HTML file with support for diagnosis."
            )
        }
    }

    private fun buildHtmlReport(
        timestamp: String,
        errorType: String,
        errorMessage: String,
        causeExplanation: String,
        fixSteps: String,
        stackTrace: String,
        deviceId: String?,
        loanNumber: String?,
        serverResponse: String?,
        additionalInfo: Map<String, String>?
    ): String {
        val stackSection = if (stackTrace.isNotBlank()) """
            <h3>Stack trace</h3>
            <pre class="box">${escapeHtml(stackTrace)}</pre>
        """ else ""
        val deviceSection = if (deviceId != null || loanNumber != null || !additionalInfo.isNullOrEmpty()) """
            <h3>Context</h3>
            <ul>
                ${if (deviceId != null) "<li><strong>Device ID:</strong> ${escapeHtml(deviceId)}</li>" else ""}
                ${if (loanNumber != null) "<li><strong>Loan number:</strong> ${escapeHtml(loanNumber)}</li>" else ""}
                ${additionalInfo?.entries?.joinToString("") { "<li><strong>${escapeHtml(it.key)}:</strong> ${escapeHtml(it.value)}</li>" } ?: ""}
            </ul>
        """ else ""
        val serverSection = if (!serverResponse.isNullOrBlank()) """
            <h3>Server response</h3>
            <pre class="box">${escapeHtml(serverResponse.take(2000))}${if (serverResponse.length > 2000) "\n..." else ""}</pre>
        """ else ""

        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Registration Error - $errorType</title>
    <style>
        body { font-family: sans-serif; margin: 16px; background: #f5f5f5; color: #222; }
        h1 { color: #c62828; font-size: 1.3em; }
        h2 { color: #333; font-size: 1.1em; margin-top: 20px; }
        h3 { color: #555; font-size: 1em; margin-top: 16px; }
        .box { background: #fff; border: 1px solid #ccc; padding: 12px; overflow-x: auto; font-size: 12px; white-space: pre-wrap; word-break: break-all; }
        .cause { background: #fff3e0; border-left: 4px solid #ff9800; padding: 12px; margin: 12px 0; }
        .fix { background: #e8f5e9; border-left: 4px solid #4caf50; padding: 12px; margin: 12px 0; white-space: pre-line; }
        .meta { color: #666; font-size: 14px; margin-bottom: 16px; }
        ul { margin: 4px 0; padding-left: 20px; }
    </style>
</head>
<body>
    <h1>Registration error: $errorType</h1>
    <p class="meta">Time: $timestamp</p>
    <p><strong>Message:</strong> ${escapeHtml(errorMessage)}</p>

    <h2>What caused this error</h2>
    <div class="cause">$causeExplanation</div>

    <h2>How to fix / remove this error</h2>
    <div class="fix">$fixSteps</div>

    $deviceSection
    $serverSection
    $stackSection
</body>
</html>
        """.trimIndent()
    }

    private fun escapeHtml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
