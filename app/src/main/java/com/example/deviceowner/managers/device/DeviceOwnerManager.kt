package com.example.deviceowner.managers

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import com.example.deviceowner.receivers.AdminReceiver

class DeviceOwnerManager(private val context: Context) {

    companion object {
        private const val TAG = "DeviceOwnerManager"
    }

    private val devicePolicyManager: DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private val adminComponent: ComponentName =
        ComponentName(context, AdminReceiver::class.java)

    /**
     * Check if the app is the device owner
     */
    fun isDeviceOwner(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            devicePolicyManager.isDeviceOwnerApp(context.packageName)
        } else {
            @Suppress("DEPRECATION")
            devicePolicyManager.isDeviceOwnerApp(context.packageName)
        }
    }

    /**
     * Check if the app is a device admin
     */
    fun isDeviceAdmin(): Boolean {
        return devicePolicyManager.isAdminActive(adminComponent)
    }

    /**
     * Initialize device owner features
     */
    fun initializeDeviceOwner() {
        Log.d(TAG, "Initializing device owner features")
        if (isDeviceOwner()) {
            Log.d(TAG, "App is device owner - initializing policies")
            // Set default policies
            setDefaultPolicies()
        } else {
            Log.w(TAG, "App is not device owner")
        }
    }

    /**
     * Handle device owner removal
     */
    fun onDeviceOwnerRemoved() {
        Log.d(TAG, "Device owner removed - cleaning up")
    }

    /**
     * Lock the device immediately
     */
    fun lockDevice() {
        try {
            if (isDeviceOwner()) {
                devicePolicyManager.lockNow()
                Log.d(TAG, "Device locked successfully")
            } else {
                Log.w(TAG, "Cannot lock device - not device owner")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error locking device", e)
        }
    }

    /**
     * Set device password policy
     */
    fun setDevicePassword(password: String): Boolean {
        return try {
            if (isDeviceOwner()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    devicePolicyManager.resetPassword(password, 0)
                } else {
                    @Suppress("DEPRECATION")
                    devicePolicyManager.resetPassword(password, 0)
                }
                Log.d(TAG, "Device password set successfully")
                true
            } else {
                Log.w(TAG, "Cannot set password - not device owner")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting device password", e)
            false
        }
    }

    /**
     * Disable camera access
     */
    fun disableCamera(disable: Boolean): Boolean {
        return try {
            if (isDeviceOwner()) {
                devicePolicyManager.setCameraDisabled(adminComponent, disable)
                Log.d(TAG, "Camera disabled: $disable")
                true
            } else {
                Log.w(TAG, "Cannot disable camera - not device owner")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling camera", e)
            false
        }
    }

    /**
     * Disable USB file transfer
     */
    fun disableUSB(disable: Boolean): Boolean {
        return try {
            if (isDeviceOwner()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    devicePolicyManager.setUsbDataSignalingEnabled(!disable)
                    Log.d(TAG, "USB file transfer disabled: $disable")
                    true
                } else {
                    Log.w(TAG, "USB control not available on this API level")
                    false
                }
            } else {
                Log.w(TAG, "Cannot disable USB - not device owner")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling USB", e)
            false
        }
    }

    /**
     * Disable developer options
     */
    fun disableDeveloperOptions(disable: Boolean): Boolean {
        return try {
            if (isDeviceOwner()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    // Use reflection to call setDebuggingFeaturesDisabled for API 28+
                    val method = devicePolicyManager.javaClass.getMethod(
                        "setDebuggingFeaturesDisabled",
                        ComponentName::class.java,
                        Boolean::class.javaPrimitiveType
                    )
                    method.invoke(devicePolicyManager, adminComponent, disable)
                    Log.d(TAG, "Developer options disabled: $disable")
                    true
                } else {
                    Log.w(TAG, "Developer options control not available on this API level")
                    false
                }
            } else {
                Log.w(TAG, "Cannot disable developer options - not device owner")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling developer options", e)
            false
        }
    }

    /**
     * Set password policy requirements
     */
    fun setPasswordPolicy(
        minLength: Int = 8,
        requireUppercase: Boolean = true,
        requireLowercase: Boolean = true,
        requireNumbers: Boolean = true,
        requireSymbols: Boolean = true
    ): Boolean {
        return try {
            if (isDeviceOwner()) {
                devicePolicyManager.setPasswordMinimumLength(adminComponent, minLength)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    var quality = DevicePolicyManager.PASSWORD_QUALITY_COMPLEX
                    if (requireUppercase) {
                        devicePolicyManager.setPasswordMinimumUpperCase(adminComponent, 1)
                    }
                    if (requireLowercase) {
                        devicePolicyManager.setPasswordMinimumLowerCase(adminComponent, 1)
                    }
                    if (requireNumbers) {
                        devicePolicyManager.setPasswordMinimumNumeric(adminComponent, 1)
                    }
                    if (requireSymbols) {
                        devicePolicyManager.setPasswordMinimumSymbols(adminComponent, 1)
                    }
                    devicePolicyManager.setPasswordQuality(adminComponent, quality)
                }
                
                Log.d(TAG, "Password policy set successfully")
                true
            } else {
                Log.w(TAG, "Cannot set password policy - not device owner")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting password policy", e)
            false
        }
    }

    /**
     * Wipe device (factory reset)
     */
    fun wipeDevice(): Boolean {
        return try {
            if (isDeviceOwner()) {
                devicePolicyManager.wipeData(0)
                Log.d(TAG, "Device wipe initiated")
                true
            } else {
                Log.w(TAG, "Cannot wipe device - not device owner")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error wiping device", e)
            false
        }
    }

    /**
     * Reboot device
     */
    fun rebootDevice(): Boolean {
        return try {
            if (isDeviceOwner()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    devicePolicyManager.reboot(adminComponent)
                    Log.d(TAG, "Device reboot initiated")
                    true
                } else {
                    Log.w(TAG, "Reboot not available on this API level")
                    false
                }
            } else {
                Log.w(TAG, "Cannot reboot device - not device owner")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error rebooting device", e)
            false
        }
    }

    /**
     * Set default device owner policies
     */
    private fun setDefaultPolicies() {
        try {
            // Disable camera by default
            disableCamera(true)
            
            // Disable developer options
            disableDeveloperOptions(true)
            
            // Set password policy
            setPasswordPolicy()
            
            Log.d(TAG, "Default policies applied successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying default policies", e)
        }
    }
}
