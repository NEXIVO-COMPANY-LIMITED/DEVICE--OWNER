package com.example.deviceowner.utils.error

import android.content.Context
import android.util.Log
import com.example.deviceowner.services.reporting.ServerBugAndLogReporter
import com.google.gson.Gson
import com.google.gson.JsonObject
import retrofit2.Response
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Parses Django registration error responses and provides the exact server message.
 * Automatically submits errors and logs to Django APIs.
 */
object RegistrationErrorHandler {
    
    private const val TAG = "RegistrationErrorHandler"
    
    data class ErrorInfo(
        val customerMessage: String,      // Raw message from server
        val technicalMessage: String,     // For logging
        val field: String? = null,
        val httpCode: Int,
        val isRetryable: Boolean,
        val suggestion: String? = null    // Optional suggestion
    )
    
    /**
     * Parse error response and return the exact message from server.
     */
    fun parseError(response: Response<*>, context: Context? = null, loanNumber: String? = null): ErrorInfo {
        val httpCode = response.code()
        val errorBody = response.errorBody()?.string() ?: ""
        
        Log.e(TAG, "Error response (HTTP $httpCode): $errorBody")
        
        val errorInfo = try {
            val json = Gson().fromJson(errorBody, JsonObject::class.java)
            
            // Extract the most descriptive message from the server response
            val serverMessage = when {
                json.has("message") -> json.get("message").asString
                json.has("error") -> json.get("error").asString
                json.has("detail") -> json.get("detail").asString
                json.has("errors") && json.get("errors").isJsonObject -> {
                    val errorsObj = json.getAsJsonObject("errors")
                    val firstEntry = errorsObj.entrySet().firstOrNull()
                    if (firstEntry != null) {
                        val fieldName = firstEntry.key
                        val fieldError = if (firstEntry.value.isJsonArray) {
                            firstEntry.value.asJsonArray.firstOrNull()?.asString ?: "Invalid value"
                        } else {
                            firstEntry.value.asString
                        }
                        "$fieldName: $fieldError"
                    } else "Validation failed"
                }
                else -> null
            }

            val field = if (json.has("field")) json.get("field").asString else null

            if (serverMessage != null) {
                ErrorInfo(
                    customerMessage = serverMessage,
                    technicalMessage = "Server Message: $serverMessage",
                    field = field,
                    httpCode = httpCode,
                    isRetryable = httpCode >= 500,
                    suggestion = if (httpCode == 401) "Please check your connectivity or contact support." else null
                )
            } else {
                // Fallback for non-JSON or unknown JSON structure
                getDefaultErrorInfo(httpCode, errorBody)
            }
        } catch (e: Exception) {
            getDefaultErrorInfo(httpCode, errorBody)
        }
        
        // SUBMIT ERROR TO DJANGO APIS (fire-and-forget)
        if (context != null) {
            submitErrorToServer(errorInfo, loanNumber, errorBody)
        }
        
        return errorInfo
    }

    private fun getDefaultErrorInfo(httpCode: Int, body: String): ErrorInfo {
        return ErrorInfo(
            customerMessage = when(httpCode) {
                401 -> "Authentication failed (401)"
                403 -> "Access denied (403)"
                404 -> "Service not found (404)"
                500 -> "Internal server error (500)"
                else -> "Registration failed (HTTP $httpCode)"
            },
            technicalMessage = "HTTP $httpCode: $body",
            httpCode = httpCode,
            isRetryable = httpCode >= 500
        )
    }
    
    private fun submitErrorToServer(errorInfo: ErrorInfo, loanNumber: String?, errorBody: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ServerBugAndLogReporter.postLog(
                    logType = "registration",
                    logLevel = "Error",
                    message = "Registration Error: ${errorInfo.customerMessage}",
                    extraData = mapOf(
                        "http_code" to errorInfo.httpCode,
                        "loan_number" to (loanNumber ?: "unknown"),
                        "error_body" to errorBody.take(500)
                    )
                )
                
                if (errorInfo.httpCode >= 400) {
                    ServerBugAndLogReporter.postBug(
                        title = "Registration Fail: ${errorInfo.httpCode}",
                        message = "Message: ${errorInfo.customerMessage}\nLoan: $loanNumber\nBody: $errorBody",
                        priority = "high"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to submit error: ${e.message}")
            }
        }
    }
    
    fun buildCustomerErrorMessage(errorInfo: ErrorInfo): String {
        return errorInfo.customerMessage + (if (errorInfo.suggestion != null) "\n\n${errorInfo.suggestion}" else "")
    }

    fun logError(errorInfo: ErrorInfo, context: String = "") {
        Log.e(TAG, "[$context] ${errorInfo.httpCode}: ${errorInfo.customerMessage}")
    }
}
