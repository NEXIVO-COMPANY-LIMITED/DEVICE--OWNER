package com.example.deviceowner.overlay

import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * OverlayManager handles lock overlay display
 */
class OverlayManager(private val context: Context) {
    
    companion object {
        private const val TAG = "OverlayManager"
    }
    
    private val overlayController = OverlayController(context)
    
    /**
     * Show lock overlay
     */
    fun showLockOverlay(reason: String) {
        try {
            overlayController.showLockNotification(reason, System.currentTimeMillis().toString())
            Log.d(TAG, "✓ Lock overlay shown")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing lock overlay", e)
        }
    }
    
    /**
     * Dismiss overlay
     */
    fun dismissOverlay() {
        try {
            overlayController.clearAllOverlays()
            Log.d(TAG, "✓ Overlay dismissed")
        } catch (e: Exception) {
            Log.e(TAG, "Error dismissing overlay", e)
        }
    }
}
