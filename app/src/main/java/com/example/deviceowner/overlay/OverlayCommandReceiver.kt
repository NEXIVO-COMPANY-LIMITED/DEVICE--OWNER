package com.example.deviceowner.overlay

import android.content.Context
import android.graphics.Color
import android.util.Log
import com.example.deviceowner.managers.IdentifierAuditLog
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Receives and processes overlay commands from backend
 * Feature 4.6: Backend-Driven Overlay Display
 */
class OverlayCommandReceiver(private val context: Context) {

    companion object {
        private const val TAG = "OverlayCommandReceiver"
    }

    private val overlayController = OverlayController(context)
    private val auditLog = IdentifierAuditLog(context)
    private val gson = Gson()

    /**
     * Process overlay commands received from backend
     */
    suspend fun processOverlayCommands(commands: List<OverlayCommandResponse>) {
        withContext(Dispatchers.Main) {
            try {
                Log.d(TAG, "Processing ${commands.size} overlay commands from backend")

                for (command in commands) {
                    try {
                        processOverlayCommand(command)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing overlay command: ${command.overlayId}", e)
                        auditLog.logIncident(
                            type = "OVERLAY_COMMAND_ERROR",
                            severity = "HIGH",
                            details = "Failed to process overlay command ${command.overlayId}: ${e.message}"
                        )
                    }
                }

                Log.d(TAG, "✓ All overlay commands processed")
            } catch (e: Exception) {
                Log.e(TAG, "Error processing overlay commands", e)
                auditLog.logIncident(
                    type = "OVERLAY_COMMANDS_ERROR",
                    severity = "HIGH",
                    details = "Failed to process overlay commands: ${e.message}"
                )
            }
        }
    }

    /**
     * Process single overlay command
     */
    private suspend fun processOverlayCommand(command: OverlayCommandResponse) {
        try {
            Log.d(TAG, "Processing overlay command: ${command.overlayId}")

            // Validate overlay type
            val overlayType = try {
                OverlayType.valueOf(command.type)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Invalid overlay type: ${command.type}")
                auditLog.logIncident(
                    type = "INVALID_OVERLAY_TYPE",
                    severity = "MEDIUM",
                    details = "Invalid overlay type received: ${command.type}"
                )
                return
            }

            // Check if overlay has expired
            if (command.expiryTime != null && System.currentTimeMillis() > command.expiryTime) {
                Log.d(TAG, "Overlay expired: ${command.overlayId}")
                auditLog.logAction(
                    "OVERLAY_EXPIRED",
                    "Overlay ${command.overlayId} has expired"
                )
                return
            }

            // Create overlay data
            val overlayData = OverlayData(
                id = command.overlayId,
                type = overlayType,
                title = command.title,
                message = command.message,
                actionButtonText = command.actionButtonText,
                actionButtonColor = parseColor(command.actionButtonColor),
                dismissible = command.dismissible,
                priority = command.priority,
                expiryTime = command.expiryTime,
                metadata = command.metadata
            )

            // Display overlay
            overlayController.showOverlay(overlayData)

            // Log overlay received and displayed
            auditLog.logAction(
                "OVERLAY_RECEIVED_FROM_BACKEND",
                "Overlay received and displayed: ${command.overlayId} (Type: ${command.type})"
            )

            Log.d(TAG, "✓ Overlay displayed: ${command.overlayId}")

        } catch (e: Exception) {
            Log.e(TAG, "Error processing overlay command", e)
            throw e
        }
    }

    /**
     * Parse color string to Int
     */
    private fun parseColor(colorString: String): Int {
        return try {
            Color.parseColor(colorString)
        } catch (e: Exception) {
            Log.w(TAG, "Invalid color string: $colorString, using default blue")
            Color.BLUE
        }
    }
}

/**
 * Overlay command response from backend
 */
data class OverlayCommandResponse(
    val overlayId: String,
    val type: String,
    val title: String,
    val message: String,
    val actionButtonText: String = "OK",
    val actionButtonColor: String = "#0066CC",
    val dismissible: Boolean = false,
    val priority: Int = 5,
    val expiryTime: Long? = null,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Heartbeat verification response with overlay commands
 */
data class HeartbeatVerificationResponseWithOverlays(
    val success: Boolean,
    val message: String,
    val verified: Boolean,
    val dataMatches: Boolean,
    val command: BlockingCommand? = null,
    val overlayCommands: List<OverlayCommandResponse>? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Blocking command (existing)
 */
data class BlockingCommand(
    val commandId: String,
    val type: String,
    val reason: String,
    val severity: String,
    val parameters: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
)
