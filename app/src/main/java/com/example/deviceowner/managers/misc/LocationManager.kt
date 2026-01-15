package com.example.deviceowner.managers

import android.content.Context
import android.location.Location
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.OnTokenCanceledListener
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Manager for device location tracking
 * Used for heartbeat verification to track device location
 */
class LocationManager(private val context: Context) {
    
    private val fusedLocationClient: FusedLocationProviderClient = 
        LocationServices.getFusedLocationProviderClient(context)
    
    companion object {
        private const val TAG = "LocationManager"
    }
    
    /**
     * Get current device location
     * Returns null if location is unavailable or permissions not granted
     */
    suspend fun getCurrentLocation(): LocationData? {
        return try {
            // Check if permissions are granted
            if (!hasLocationPermissions()) {
                Log.w(TAG, "Location permissions not granted")
                return null
            }
            
            Log.d(TAG, "Requesting current location...")
            
            val location = suspendCancellableCoroutine<Location?> { continuation ->
                try {
                    val cancellationToken = object : CancellationToken() {
                        override fun onCanceledRequested(callback: OnTokenCanceledListener): CancellationToken {
                            continuation.resume(null)
                            return this
                        }
                        
                        override fun isCancellationRequested(): Boolean = false
                    }
                    
                    fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        cancellationToken
                    ).addOnSuccessListener { location: Location? ->
                        continuation.resume(location)
                    }.addOnFailureListener { exception ->
                        Log.e(TAG, "Failed to get location: ${exception.message}", exception)
                        continuation.resume(null)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error requesting location: ${e.message}", e)
                    continuation.resume(null)
                }
            }
            
            if (location != null) {
                val locationData = LocationData(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = location.accuracy,
                    altitude = location.altitude,
                    bearing = location.bearing,
                    speed = location.speed,
                    timestamp = location.time,
                    provider = location.provider ?: "unknown"
                )
                Log.d(TAG, "✓ Location obtained: ${locationData.latitude}, ${locationData.longitude}")
                locationData
            } else {
                Log.w(TAG, "Location is null")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current location: ${e.message}", e)
            null
        }
    }
    
    /**
     * Get last known location (faster, may be stale)
     */
    suspend fun getLastKnownLocation(): LocationData? {
        return try {
            if (!hasLocationPermissions()) {
                Log.w(TAG, "Location permissions not granted")
                return null
            }
            
            Log.d(TAG, "Requesting last known location...")
            
            val location = suspendCancellableCoroutine<Location?> { continuation ->
                try {
                    fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                        continuation.resume(location)
                    }.addOnFailureListener { exception ->
                        Log.e(TAG, "Failed to get last known location: ${exception.message}", exception)
                        continuation.resume(null)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error requesting last known location: ${e.message}", e)
                    continuation.resume(null)
                }
            }
            
            if (location != null) {
                val locationData = LocationData(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = location.accuracy,
                    altitude = location.altitude,
                    bearing = location.bearing,
                    speed = location.speed,
                    timestamp = location.time,
                    provider = location.provider ?: "unknown"
                )
                Log.d(TAG, "✓ Last known location obtained: ${locationData.latitude}, ${locationData.longitude}")
                locationData
            } else {
                Log.w(TAG, "Last known location is null")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting last known location: ${e.message}", e)
            null
        }
    }
    
    /**
     * Check if location permissions are granted
     */
    private fun hasLocationPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }
}

/**
 * Data class for location information
 */
data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val altitude: Double,
    val bearing: Float,
    val speed: Float,
    val timestamp: Long,
    val provider: String
)
