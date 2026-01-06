package com.deviceowner.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Permission management utilities for scanner and camera access.
 * Handles permission checking and validation for barcode scanning features.
 */
object PermissionManager {

    /**
     * Check if camera permission is granted
     */
    fun isCameraPermissionGranted(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if scanner can be accessed
     * Validates both permission and device capability
     */
    fun canAccessScanner(context: Context): Boolean {
        return isCameraPermissionGranted(context) && hasCameraHardware(context)
    }

    /**
     * Check if device has camera hardware
     */
    fun hasCameraHardware(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }

    /**
     * Check if device has back camera
     */
    fun hasBackCamera(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)
    }

    /**
     * Get required permissions for scanner
     */
    fun getScannerPermissions(): Array<String> {
        return arrayOf(Manifest.permission.CAMERA)
    }

    /**
     * Validate scanner access before opening
     * Returns pair of (canAccess, errorCode)
     */
    fun validateScannerAccess(context: Context): Pair<Boolean, String> {
        return when {
            !isCameraPermissionGranted(context) -> Pair(false, "CAMERA_PERMISSION_DENIED")
            !hasCameraHardware(context) -> Pair(false, "NO_CAMERA_HARDWARE")
            !hasBackCamera(context) -> Pair(false, "NO_BACK_CAMERA")
            else -> Pair(true, "")
        }
    }

    /**
     * Get permission error message
     */
    fun getPermissionErrorMessage(errorCode: String): String {
        return when (errorCode) {
            "CAMERA_PERMISSION_DENIED" -> "Camera permission is required to use the scanner"
            "NO_CAMERA_HARDWARE" -> "This device does not have a camera"
            "NO_BACK_CAMERA" -> "This device does not have a back camera"
            else -> "Permission error occurred"
        }
    }
}
