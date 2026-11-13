package com.shilapi.xcertplay.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HotspotAutoStartPolicyTest {
    @Test fun neverSwitchesWithoutOptIn() {
        assertFalse(HotspotAutoStartPolicy.shouldStart(false, false, 1_000L, 0L))
        assertFalse(HotspotAutoStartPolicy.shouldStart(null, false, 1_000L, 0L))
    }

    @Test fun enabledAccessPointNeedsNoSwitch() {
        assertFalse(HotspotAutoStartPolicy.shouldStart(true, true, 1_000L, 0L))
    }

    @Test fun offOrUnknownAccessPointIsSwitchedWhenTheUserOptedIn() {
        assertTrue(HotspotAutoStartPolicy.shouldStart(false, true, 1_000L, 0L))
        assertTrue(HotspotAutoStartPolicy.shouldStart(null, true, 1_000L, 0L))
    }

    @Test fun repeatedAttemptsAreThrottled() {
        val throttle = HotspotAutoStartPolicy.START_THROTTLE_MILLIS
        assertFalse(HotspotAutoStartPolicy.shouldStart(false, true, 10_000L, 9_000L))
        assertTrue(HotspotAutoStartPolicy.shouldStart(false, true, 9_000L + throttle, 9_000L))
    }

    @Test fun aClockThatMovedBackwardsDoesNotPinTheThrottle() {
        assertTrue(HotspotAutoStartPolicy.shouldStart(false, true, 5_000L, 60_000L))
    }
}
