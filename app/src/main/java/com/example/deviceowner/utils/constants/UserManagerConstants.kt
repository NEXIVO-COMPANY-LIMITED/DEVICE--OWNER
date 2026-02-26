package com.microspace.payo.utils.constants

import android.os.UserManager

/**
 * Compatibility constants for UserManager restrictions.
 * Some constants are only available in newer Android versions.
 */
object UserManagerConstants {

    /**
     * DISALLOW_CONFIG_DEVELOPER_OPTS - Disables access to developer options.
     * Available since Android 8.0 (API 26)
     */
    /** AOSP restriction string - hides Developer Options menu. Not in public UserManager SDK. */
    const val DISALLOW_CONFIG_DEVELOPER_OPTS = "no_config_developer_options"

    /**
     * Get all setup-only restrictions (minimal restrictions during registration)
     */
    fun getSetupRestrictions(): Array<String> = arrayOf(
        UserManager.DISALLOW_FACTORY_RESET,
        UserManager.DISALLOW_SAFE_BOOT,
        UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA,
        UserManager.DISALLOW_DEBUGGING_FEATURES,
        DISALLOW_CONFIG_DEVELOPER_OPTS
    )

    /**
     * Get critical restrictions (STANDARD mode)
     */
    fun getCriticalRestrictions(): Array<String> = arrayOf(
        UserManager.DISALLOW_FACTORY_RESET,
        UserManager.DISALLOW_SAFE_BOOT,
        UserManager.DISALLOW_ADD_USER,
        UserManager.DISALLOW_REMOVE_USER,
        UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
        UserManager.DISALLOW_CONFIG_WIFI,
        UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS,
        UserManager.DISALLOW_CONFIG_BLUETOOTH,
        UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA,
        UserManager.DISALLOW_DEBUGGING_FEATURES,
        DISALLOW_CONFIG_DEVELOPER_OPTS
    )
}
