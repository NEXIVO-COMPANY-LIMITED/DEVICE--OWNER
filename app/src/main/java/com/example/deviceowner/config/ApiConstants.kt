package com.microspace.payo.config

/**
 * API and network constants for device owner app.
 * Used for consistent timeouts, retries, and documented behavior when server is unreachable.
 */
object ApiConstants {

    /** Connect timeout for API calls (seconds). ApiClient uses 120s. */
    const val CONNECT_TIMEOUT_SECONDS = 120L

    /** Read timeout for API calls (seconds). ApiClient uses 120s. */
    const val READ_TIMEOUT_SECONDS = 120L

    /** Call timeout for full request (seconds). ApiClient uses 240s. */
    const val CALL_TIMEOUT_SECONDS = 240L

    /**
     * When server is unreachable (timeout, no network, 5xx):
     * - Heartbeat keeps last known lock state; device does not change lock/unlock on its own.
     * - Offline heartbeats are queued and synced when network returns (OfflineSyncWorker).
     * - Lock state only changes when server explicitly returns is_locked / unlock.
     */
    const val BEHAVIOR_WHEN_SERVER_UNREACHABLE =
        "Device retains last known lock state; heartbeats retry; sync when online."
}
