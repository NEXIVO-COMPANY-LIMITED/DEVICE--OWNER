package com.example.deviceowner.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.deviceowner.managers.CommandExecutor
import kotlinx.coroutines.*

/**
 * Background service for processing offline command queue
 * Feature 4.9: Offline Command Queue
 * 
 * Ensures commands execute even without internet connection
 * - Processes queue in background
 * - Survives app crashes (START_STICKY)
 * - Persists across device reboots
 * - Checks queue every 5 seconds
 */
class CommandQueueService : Service() {

    private lateinit var commandExecutor: CommandExecutor
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    companion object {
        private const val TAG = "CommandQueueService"
        private const val QUEUE_CHECK_INTERVAL = 5000L // 5 seconds
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "CommandQueueService created")
        
        try {
            // Initialize CommandExecutor
            commandExecutor = CommandExecutor(this)
            
            // Start queue processing
            startQueueProcessing()
            
            Log.d(TAG, "✓ CommandQueueService initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing CommandQueueService", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "CommandQueueService started")
        
        // START_STICKY ensures service restarts after crash
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        // This service doesn't support binding
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "CommandQueueService destroyed")
        
        // Cancel all coroutines
        serviceScope.cancel()
    }

    /**
     * Start background queue processing
     */
    private fun startQueueProcessing() {
        serviceScope.launch {
            Log.d(TAG, "Starting queue processing loop")
            
            while (isActive) {
                try {
                    // Check queue status
                    val status = commandExecutor.getQueueStatus()
                    
                    if (status.pendingCommands > 0) {
                        Log.d(TAG, "Processing ${status.pendingCommands} pending commands")
                        
                        // Process all pending commands
                        commandExecutor.processAllPendingCommands()
                    }
                    
                    // Wait before next check
                    delay(QUEUE_CHECK_INTERVAL)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in queue processing loop", e)
                    delay(QUEUE_CHECK_INTERVAL)
                }
            }
        }
    }
}
