package com.example.deviceowner.managers.device

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.*

/**
 * Service Health Manager
 * Monitors and verifies that all critical services are running
 * Provides health checks and automatic recovery
 */
class ServiceHealthManager(private val context: Context) {

    companion object {
        private const val TAG = "ServiceHealthManager"
        
        // Critical services that must be running
        private val CRITICAL_SERVICES = listOf(
            "com.example.deviceowner.services.UnifiedHeartbeatService",
            "com.example.deviceowner.services.ComprehensiveSecurityService",
            "com.example.deviceowner.services.DeviceOwnerRecoveryService",
            "com.example.deviceowner.services.TamperDetectionService",
        )
    }

    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Verify all critical services are running
     */
    fun verifyAllServicesRunning(): ServiceHealthReport {
        val report = ServiceHealthReport()
        
        try {
            val runningServices = getRunningServices()
            
            CRITICAL_SERVICES.forEach { serviceName ->
                val isRunning = runningServices.any { it.contains(serviceName) }
                report.services[serviceName] = isRunning
                
                if (isRunning) {
                    Log.d(TAG, "✓ $serviceName is running")
                } else {
                    Log.w(TAG, "✗ $serviceName is NOT running")
                    report.failedServices.add(serviceName)
                }
            }
            
            report.allHealthy = report.failedServices.isEmpty()
            
            if (report.allHealthy) {
                Log.d(TAG, "✓ All critical services are running")
            } else {
                Log.w(TAG, "⚠ ${report.failedServices.size} service(s) are not running")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying services", e)
            report.error = e.message
        }
        
        return report
    }

    /**
     * Get list of running services
     */
    private fun getRunningServices(): List<String> {
        return try {
            activityManager.getRunningServices(Integer.MAX_VALUE)
                .map { it.service.className }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting running services", e)
            emptyList()
        }
    }

    /**
     * Start monitoring service health
     * Periodically checks and restarts failed services
     */
    fun startHealthMonitoring(intervalMs: Long = 30000) {
        scope.launch {
            while (isActive) {
                try {
                    val report = verifyAllServicesRunning()
                    
                    if (!report.allHealthy) {
                        Log.w(TAG, "Service health check failed - attempting recovery")
                        recoverFailedServices(report.failedServices)
                    }
                    
                    delay(intervalMs)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Error in health monitoring", e)
                    delay(intervalMs)
                }
            }
        }
    }

    /**
     * Recover failed services by restarting them
     */
    private fun recoverFailedServices(failedServices: List<String>) {
        Log.d(TAG, "Attempting to recover ${failedServices.size} failed service(s)")
        
        failedServices.forEach { serviceName ->
            try {
                val serviceClass = Class.forName(serviceName)
                val intent = android.content.Intent(context, serviceClass)
                context.startService(intent)
                Log.d(TAG, "✓ Restarted $serviceName")
            } catch (e: ClassNotFoundException) {
                Log.e(TAG, "Service class not found: $serviceName", e)
            } catch (e: Exception) {
                Log.e(TAG, "Error restarting $serviceName", e)
            }
        }
    }

    /**
     * Data class for service health report
     */
    data class ServiceHealthReport(
        val services: MutableMap<String, Boolean> = mutableMapOf(),
        val failedServices: MutableList<String> = mutableListOf(),
        var allHealthy: Boolean = false,
        var error: String? = null
    )
}
