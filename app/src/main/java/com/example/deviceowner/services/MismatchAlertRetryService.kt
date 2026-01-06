package com.example.deviceowner.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.deviceowner.managers.MismatchAlertQueue
import com.example.deviceowner.data.api.HeartbeatApiService
import kotlinx.coroutines.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

/**
 * Service to retry sending queued mismatch alerts
 * Runs periodically to check for pending alerts and retry sending
 */
class MismatchAlertRetryService : Service() {
    
    private lateinit var alertQueue: MismatchAlertQueue
    private lateinit var apiService: HeartbeatApiService
    private var retryJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    
    companion object {
        private const val TAG = "MismatchAlertRetryService"
        private const val RETRY_INTERVAL = 300000L // 5 minutes
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "MismatchAlertRetryService created")
        
        alertQueue = MismatchAlertQueue(this)
        
        // Initialize Retrofit API service
        val retrofit = Retrofit.Builder()
            .baseUrl("http://82.29.168.120/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(createOkHttpClient())
            .build()
        
        apiService = retrofit.create(HeartbeatApiService::class.java)
    }
    
    /**
     * Create OkHttpClient with proper timeout and retry configuration
     */
    private fun createOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "MismatchAlertRetryService started")
        
        // Start retry loop
        startRetryLoop()
        
        // Return START_STICKY to ensure service restarts if killed
        return START_STICKY
    }
    
    /**
     * Start periodic retry loop
     */
    private fun startRetryLoop() {
        if (retryJob?.isActive == true) {
            Log.d(TAG, "Retry loop already running")
            return
        }
        
        retryJob = serviceScope.launch {
            try {
                while (isActive) {
                    try {
                        // Check for pending alerts
                        if (alertQueue.hasPendingAlerts()) {
                            Log.d(TAG, "Found ${alertQueue.getQueueSize()} pending alerts, retrying...")
                            retryPendingAlerts()
                        }
                        
                        delay(RETRY_INTERVAL)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in retry loop", e)
                        delay(RETRY_INTERVAL)
                    }
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Retry loop cancelled")
            }
        }
    }
    
    /**
     * Retry sending all pending alerts
     */
    private suspend fun retryPendingAlerts() {
        try {
            val pendingAlerts = alertQueue.getPendingAlerts()
            
            if (pendingAlerts.isEmpty()) {
                Log.d(TAG, "No pending alerts to retry")
                return
            }
            
            Log.d(TAG, "Retrying ${pendingAlerts.size} pending alerts...")
            
            for (alert in pendingAlerts) {
                try {
                    val response = apiService.reportMismatch(alert.deviceId, alert)
                    
                    if (response.isSuccessful) {
                        Log.d(TAG, "✓ Alert retry successful for device: ${alert.deviceId}")
                        alertQueue.removeAlert(alert)
                    } else {
                        Log.w(TAG, "✗ Alert retry failed: ${response.code()} - ${response.message()}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error retrying alert for device: ${alert.deviceId}", e)
                }
            }
            
            Log.d(TAG, "Alert retry cycle completed. Remaining: ${alertQueue.getQueueSize()}")
        } catch (e: Exception) {
            Log.e(TAG, "Error retrying pending alerts", e)
        }
    }
    
    /**
     * Manually trigger retry (can be called from other components)
     */
    fun triggerRetry() {
        serviceScope.launch {
            retryPendingAlerts()
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "MismatchAlertRetryService destroyed")
        retryJob?.cancel()
        serviceScope.cancel()
    }
}
