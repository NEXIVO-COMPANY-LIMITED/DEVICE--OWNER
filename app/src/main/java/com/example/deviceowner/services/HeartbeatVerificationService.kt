package com.example.deviceowner.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.deviceowner.managers.HeartbeatDataManager
import com.deviceowner.verification.DeviceOwnerVerificationService
import kotlinx.coroutines.*

/**
 * Heartbeat Verification Service
 * Handles device verification through heartbeat data
 * 
 * Features:
 * - Periodic heartbeat verification
 * - Device status validation
 * - Backend communication for verification
 */
class HeartbeatVerificationService : Service() {
    
    private lateinit var heartbeatDataManager: HeartbeatDataManager
    private lateinit var verificationService: DeviceOwnerVerificationService
    private var verificationJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    companion object {
        private const val TAG = "HeartbeatVerificationService"
        private const val VERIFICATION_INTERVAL = 60000L // 1 minute
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "HeartbeatVerificationService created")
        
        heartbeatDataManager = HeartbeatDataManager(this)
        verificationService = DeviceOwnerVerificationService(this)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "HeartbeatVerificationService started")
        startVerificationLoop()
        return START_STICKY
    }
    
    private fun startVerificationLoop() {
        verificationJob = serviceScope.launch {
            while (isActive) {
                try {
                    performVerification()
                    delay(VERIFICATION_INTERVAL)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Error in verification loop", e)
                    delay(VERIFICATION_INTERVAL)
                }
            }
        }
    }
    
    private suspend fun performVerification() {
        try {
            Log.d(TAG, "Performing heartbeat verification")
            
            // Collect current device data
            val heartbeatData = heartbeatDataManager.collectHeartbeatData()
            
            // Perform verification
            val verificationResult = verificationService.verifyDeviceOwner()
            
            Log.d(TAG, "Verification completed: ${verificationResult.result}")
        } catch (e: Exception) {
            Log.e(TAG, "Error performing verification", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "HeartbeatVerificationService destroyed")
        verificationJob?.cancel()
        serviceScope.cancel()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}