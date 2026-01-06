package com.example.deviceowner.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.example.deviceowner.managers.IdentifierAuditLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Service for managing overlay UI system
 * Feature 4.6: Pop-up Screens / Overlay UI
 */
class OverlayManager : Service() {

    companion object {
        private const val TAG = "OverlayManager"
        private const val PREFS_NAME = "overlay_prefs"
        private const val KEY_ACTIVE_OVERLAYS = "active_overlays"
        private const val KEY_OVERLAY_HISTORY = "overlay_history"
        private const val KEY_OVERLAY_QUEUE = "overlay_queue"
    }

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: SharedPreferences
    private lateinit var auditLog: IdentifierAuditLog
    private val activeOverlays = mutableMapOf<String, OverlayView>()
    private val overlayQueue = mutableListOf<OverlayData>()
    private var currentOverlay: OverlayData? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "OverlayManager service created")

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        auditLog = IdentifierAuditLog(this)

        // Load persisted overlays
        loadPersistedOverlays()

        // Start overlay processing
        startOverlayProcessing()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "OverlayManager service started")

        when (intent?.action) {
            "SHOW_OVERLAY" -> {
                val overlayJson = intent.getStringExtra("overlay_data")
                if (overlayJson != null) {
                    val overlay = OverlayData.fromJson(overlayJson)
                    if (overlay != null) {
                        showOverlay(overlay)
                    }
                }
            }
            "DISMISS_OVERLAY" -> {
                val overlayId = intent.getStringExtra("overlay_id")
                if (overlayId != null) {
                    dismissOverlay(overlayId)
                }
            }
            "CLEAR_ALL_OVERLAYS" -> {
                clearAllOverlays()
            }
        }

        return START_STICKY
    }

    /**
     * Show overlay on screen
     */
    fun showOverlay(overlay: OverlayData) {
        try {
            Log.d(TAG, "Showing overlay: ${overlay.id} (${overlay.type})")

            // Check if overlay already exists
            if (activeOverlays.containsKey(overlay.id)) {
                Log.w(TAG, "Overlay ${overlay.id} already displayed")
                return
            }

            // Check if overlay is expired
            if (overlay.isExpired()) {
                Log.w(TAG, "Overlay ${overlay.id} is expired")
                auditLog.logAction(
                    "OVERLAY_EXPIRED",
                    "Overlay ${overlay.id} expired before display"
                )
                return
            }

            // Add to queue if another overlay is active
            if (currentOverlay != null) {
                Log.d(TAG, "Overlay queue not empty, adding to queue")
                overlayQueue.add(overlay)
                saveOverlayQueue()
                return
            }

            // Create and display overlay
            val overlayView = createOverlayView(overlay)
            val params = createWindowParams()

            windowManager.addView(overlayView, params)
            activeOverlays[overlay.id] = overlayView
            currentOverlay = overlay

            // Save overlay state
            saveOverlayState(overlay)

            // Log overlay display
            auditLog.logAction(
                "OVERLAY_DISPLAYED",
                "Overlay ${overlay.id} (${overlay.type}) displayed"
            )

            Log.d(TAG, "✓ Overlay ${overlay.id} displayed successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error showing overlay", e)
            auditLog.logIncident(
                type = "OVERLAY_DISPLAY_ERROR",
                severity = "HIGH",
                details = "Failed to display overlay: ${e.message}"
            )
        }
    }

    /**
     * Create overlay view
     */
    private fun createOverlayView(overlay: OverlayData): OverlayView {
        return OverlayView(this, overlay) { overlayId ->
            dismissOverlay(overlayId)
        }
    }

    /**
     * Create window parameters for overlay
     */
    private fun createWindowParams(): WindowManager.LayoutParams {
        val params = WindowManager.LayoutParams()

        params.type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params.format = PixelFormat.TRANSLUCENT
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                      WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                      WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                      WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        params.width = WindowManager.LayoutParams.MATCH_PARENT
        params.height = WindowManager.LayoutParams.MATCH_PARENT
        params.gravity = Gravity.CENTER

        return params
    }

    /**
     * Dismiss overlay
     */
    fun dismissOverlay(overlayId: String) {
        try {
            Log.d(TAG, "Dismissing overlay: $overlayId")

            val overlayView = activeOverlays[overlayId]
            if (overlayView != null) {
                windowManager.removeView(overlayView)
                activeOverlays.remove(overlayId)
                currentOverlay = null

                // Log dismissal
                auditLog.logAction(
                    "OVERLAY_DISMISSED",
                    "Overlay $overlayId dismissed"
                )

                // Show next overlay in queue
                if (overlayQueue.isNotEmpty()) {
                    val nextOverlay = overlayQueue.removeAt(0)
                    saveOverlayQueue()
                    showOverlay(nextOverlay)
                }

                Log.d(TAG, "✓ Overlay $overlayId dismissed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error dismissing overlay", e)
        }
    }

    /**
     * Clear all overlays
     */
    fun clearAllOverlays() {
        try {
            Log.d(TAG, "Clearing all overlays")

            for ((overlayId, overlayView) in activeOverlays) {
                try {
                    windowManager.removeView(overlayView)
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing overlay $overlayId", e)
                }
            }

            activeOverlays.clear()
            overlayQueue.clear()
            currentOverlay = null

            prefs.edit()
                .remove(KEY_ACTIVE_OVERLAYS)
                .remove(KEY_OVERLAY_QUEUE)
                .apply()

            auditLog.logAction(
                "ALL_OVERLAYS_CLEARED",
                "All overlays cleared"
            )

            Log.d(TAG, "✓ All overlays cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing overlays", e)
        }
    }

    /**
     * Save overlay state to preferences
     */
    private fun saveOverlayState(overlay: OverlayData) {
        try {
            val overlaysList = mutableListOf<String>()
            for ((_, overlayView) in activeOverlays) {
                overlaysList.add(overlayView.overlay.toJson())
            }
            overlaysList.add(overlay.toJson())

            val json = com.google.gson.Gson().toJson(overlaysList)
            prefs.edit().putString(KEY_ACTIVE_OVERLAYS, json).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving overlay state", e)
        }
    }

    /**
     * Save overlay queue to preferences
     */
    private fun saveOverlayQueue() {
        try {
            val queueJson = mutableListOf<String>()
            for (overlay in overlayQueue) {
                queueJson.add(overlay.toJson())
            }

            val json = com.google.gson.Gson().toJson(queueJson)
            prefs.edit().putString(KEY_OVERLAY_QUEUE, json).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving overlay queue", e)
        }
    }

    /**
     * Load persisted overlays from preferences
     */
    private fun loadPersistedOverlays() {
        try {
            Log.d(TAG, "Loading persisted overlays")

            val overlaysJson = prefs.getString(KEY_ACTIVE_OVERLAYS, null)
            if (overlaysJson != null) {
                try {
                    val overlaysList = com.google.gson.Gson().fromJson(
                        overlaysJson,
                        Array<String>::class.java
                    ).toList()

                    for (overlayJson in overlaysList) {
                        val overlay = OverlayData.fromJson(overlayJson)
                        if (overlay != null && !overlay.isExpired()) {
                            overlayQueue.add(overlay)
                        }
                    }

                    Log.d(TAG, "Loaded ${overlayQueue.size} persisted overlays")
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing persisted overlays", e)
                }
            }

            // Load overlay queue
            val queueJson = prefs.getString(KEY_OVERLAY_QUEUE, null)
            if (queueJson != null) {
                try {
                    val queueList = com.google.gson.Gson().fromJson(
                        queueJson,
                        Array<String>::class.java
                    ).toList()

                    for (overlayJson in queueList) {
                        val overlay = OverlayData.fromJson(overlayJson)
                        if (overlay != null && !overlay.isExpired()) {
                            overlayQueue.add(overlay)
                        }
                    }

                    Log.d(TAG, "Loaded ${queueList.size} queued overlays")
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing queued overlays", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading persisted overlays", e)
        }
    }

    /**
     * Start overlay processing
     */
    private fun startOverlayProcessing() {
        val scope = CoroutineScope(Dispatchers.Main)
        scope.launch {
            try {
                // Display first overlay in queue
                if (overlayQueue.isNotEmpty()) {
                    val overlay = overlayQueue.removeAt(0)
                    saveOverlayQueue()
                    showOverlay(overlay)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing overlay queue", e)
            }
        }
    }

    /**
     * Get active overlay count
     */
    fun getActiveOverlayCount(): Int {
        return activeOverlays.size
    }

    /**
     * Get queued overlay count
     */
    fun getQueuedOverlayCount(): Int {
        return overlayQueue.size
    }

    /**
     * Check if overlay is active
     */
    fun isOverlayActive(overlayId: String): Boolean {
        return activeOverlays.containsKey(overlayId)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "OverlayManager service destroyed")
        clearAllOverlays()
    }
}

/**
 * Custom view for overlay display
 */
class OverlayView(
    context: Context,
    val overlay: OverlayData,
    private val onDismiss: (String) -> Unit
) : FrameLayout(context) {

    init {
        setupView()
    }

    private fun setupView() {
        // Create background
        setBackgroundColor(0xCC000000.toInt())

        // Create content container
        val contentContainer = LinearLayout(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(40, 40, 40, 40)
        }

        // Create title
        val titleView = TextView(context).apply {
            text = this@OverlayView.overlay.title
            textSize = 24f
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 20
            }
        }

        // Create message
        val messageView = TextView(context).apply {
            text = this@OverlayView.overlay.message
            textSize = 16f
            setTextColor(0xFFCCCCCC.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 40
            }
        }

        // Create action button
        val actionButton = Button(context).apply {
            text = this@OverlayView.overlay.actionButtonText
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(this@OverlayView.overlay.actionButtonColor)
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                80
            )
            textSize = 18f
            setOnClickListener {
                onDismiss(this@OverlayView.overlay.id)
            }
        }
        
        // Create spacer
        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        contentContainer.addView(titleView)
        contentContainer.addView(messageView)
        contentContainer.addView(spacer)
        if (!overlay.dismissible) {
            contentContainer.addView(actionButton)
        }

        addView(contentContainer)
    }
}
