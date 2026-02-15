package com.example.deviceowner

/**
 * App configuration constants.
 * Mirrors BuildConfig values - use this when BuildConfig is not yet generated (e.g. clean builds).
 * Values match build.gradle.kts buildConfigField.
 */
object AppConfig {
    /** Base URL for API requests */
    const val BASE_URL: String = "https://payoplan.com/"

    /** Device API key for backend authentication */
    const val DEVICE_API_KEY: String = "8f3d2c9a7b1e4f6d5a9c2b3e7f1d4a6c9b8e0f2a1d3c4b5e6f7a8b9c0d1e2f3a"

    /** Enable HTTP logging for debugging */
    const val ENABLE_LOGGING: Boolean = true

    /** Debug mode flag */
    const val DEBUG: Boolean = true
}
