package com.shilapi.xcertplay.legacy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoConnectPauseTest {
    @Test fun manualDisconnectPausesAllAttempts() {
        assertFalse(AutoConnectPause.mayRetry(1_000L, 600_000L, 0L, 0, 5))
        assertTrue(AutoConnectPause.mayRetry(600_000L, 600_000L, 0L, 0, 5))
    }

    @Test fun attemptsAreSpacedByTheRetryInterval() {
        assertFalse(AutoConnectPause.mayRetry(10_000L, 0L, 5_000L, 1, 5))
        assertTrue(AutoConnectPause.mayRetry(35_000L, 0L, 5_000L, 1, 5))
    }

    @Test fun theAttemptBudgetIsBounded() {
        assertFalse(AutoConnectPause.mayRetry(999_999L, 0L, 0L, 5, 5))
    }

    @Test fun aClockThatMovedBackwardsDoesNotBlockForever() {
        assertTrue(AutoConnectPause.mayRetry(5_000L, 0L, 60_000L, 1, 5))
    }
}
