package com.example.deviceowner.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.deviceowner.managers.MismatchAlertQueue
import kotlinx.coroutines.*

/**
 * Mismatch Alert Retry Service
 * Feature 4.2: Mismatch Alert Queue with Retry Logic
 * 
 * Responsibilities:
 * - Process queued mismatch alerts
 * - Retry failed alerts with exponential backoff
 * - Ensure all alerts reach the backend
 */
class MismatchAlertRetryService : Service() {
    
    private lateinit var alertQueue: MismatchAlertQueue
    private var retryJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    companion object {
        private const val TAG = "MismatchAlertRetryService"
        private const val RETRY_INTERVAL = 30000L // 30 seconds
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "MismatchAlertRetryService created")
        
        alertQueue = MismatchAlertQueue(this)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "MismatchAlertRetryService started")
        startRetryLoop()
        return START_STICKY
    }
    
    private fun startRetryLoop() {
        retryJob = serviceScope.launch {
            while (isActive) {
                try {
                    processQueuedAlerts()
                    delay(RETRY_INTERVAL)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Error in retry loop", e)
                    delay(RETRY_INTERVAL)
                }
            }
        }
    }
    
    private suspend fun processQueuedAlerts() {
        try {
            Log.d(TAG, "Processing queued mismatch alerts")
            
            // Process all queued alerts
            alertQueue.processQueuedAlerts()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing queued alerts", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "MismatchAlertRetryService destroyed")
        retryJob?.cancel()
        serviceScope.cancel()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}