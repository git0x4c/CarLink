package com.shilapi.xcertplay.network

/**
 * Decides whether the wireless bring-up may switch the head unit's own access point on.
 *
 * Recovery is the reason this exists: after the first successful start the host keeps retrying by
 * itself, and the car's AP can be off again by then (sleep, a firmware Wi-Fi cycle). Retrying the
 * connection without re-checking the AP would loop forever on "the car hotspot is off".
 *
 * Deliberately pure: the head unit is the only place that can prove the setter works, so the
 * decision itself is unit tested instead of the hardware path.
 */
internal object HotspotAutoStartPolicy {
    /** One switch request per minute: repeating the hidden setter fights the firmware's AP policy. */
    const val START_THROTTLE_MILLIS = 60_000L

    fun shouldStart(
        enabled: Boolean?,
        autoStart: Boolean,
        nowMillis: Long,
        lastAttemptAtMillis: Long,
        throttleMillis: Long = START_THROTTLE_MILLIS,
    ): Boolean {
        if (!autoStart) return false
        if (enabled == true) return false
        if (lastAttemptAtMillis <= 0L) return true
        val elapsed = nowMillis - lastAttemptAtMillis
        // A clock that moved backwards must not pin the throttle forever.
        return elapsed < 0L || elapsed >= throttleMillis
    }
}
