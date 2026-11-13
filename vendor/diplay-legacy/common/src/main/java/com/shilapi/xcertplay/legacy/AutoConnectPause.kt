package com.shilapi.xcertplay.legacy

/**
 * When the home screen may start another unattended attempt.
 *
 * A manual Disconnect must stay disconnected for a while (the driver asked for it), and the home
 * screen must not hammer the hotspot/Bluetooth stack when the iPhone is simply away. Both rules
 * are time-based, so they are unit tested here instead of only on a car.
 */
internal object AutoConnectPause {
    /** After "Disconnect", automatic starts wait this long. */
    const val MANUAL_PAUSE_MILLIS = 10 * 60_000L

    /** Minimum gap between two unattended attempts while the home screen is visible. */
    const val RETRY_INTERVAL_MILLIS = 30_000L

    fun mayRetry(
        nowMillis: Long,
        pausedUntilMillis: Long,
        lastAttemptAtMillis: Long,
        attempts: Int,
        maxAttempts: Int,
    ): Boolean {
        if (nowMillis < pausedUntilMillis) return false
        if (attempts >= maxAttempts) return false
        if (lastAttemptAtMillis <= 0L) return true
        val elapsed = nowMillis - lastAttemptAtMillis
        return elapsed < 0L || elapsed >= RETRY_INTERVAL_MILLIS
    }
}
