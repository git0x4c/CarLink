package com.shilapi.xcertplay

import com.shilapi.xcertplay.network.CarHotspotState
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The unattended start must never invent a connection the user did not ask for, and must never
 * switch the car's access point without the explicit opt-in.
 */
class AutoConnectPolicyTest {
    private fun decide(
        mode: WirelessHotspotMode = WirelessHotspotMode.MANUAL,
        state: CarHotspotState? = CarHotspotState.ENABLED,
        autoStart: Boolean = true,
        profile: Boolean = true,
        phone: Boolean = true,
        running: Boolean = false,
    ) = AutoConnectPolicy.decide(mode, state, autoStart, profile, phone, running)

    @Test fun runningSessionIsNotStartedTwice() {
        assertEquals(AutoConnectAction.SKIP, decide(running = true))
    }

    @Test fun missingPhoneNeedsTheUser() {
        assertEquals(AutoConnectAction.NEEDS_SETUP, decide(phone = false))
    }

    @Test fun otherHotspotModesLeaveBringUpToTheHost() {
        assertEquals(
            AutoConnectAction.CONNECT,
            decide(mode = WirelessHotspotMode.WIFI_P2P, state = CarHotspotState.DISABLED),
        )
    }

    @Test fun manualModeWithoutSavedProfileNeedsSetup() {
        assertEquals(AutoConnectAction.NEEDS_SETUP, decide(profile = false))
    }

    @Test fun enabledHotspotConnects() {
        assertEquals(AutoConnectAction.CONNECT, decide(state = CarHotspotState.ENABLED))
    }

    @Test fun disabledHotspotIsSwitchedOnOnlyWithOptIn() {
        assertEquals(
            AutoConnectAction.START_HOTSPOT,
            decide(state = CarHotspotState.DISABLED, autoStart = true),
        )
        assertEquals(
            AutoConnectAction.NEEDS_SETUP,
            decide(state = CarHotspotState.DISABLED, autoStart = false),
        )
    }

    @Test fun failedHotspotIsRetriedOnlyWithOptIn() {
        assertEquals(
            AutoConnectAction.START_HOTSPOT,
            decide(state = CarHotspotState.FAILED, autoStart = true),
        )
        assertEquals(
            AutoConnectAction.NEEDS_SETUP,
            decide(state = CarHotspotState.FAILED, autoStart = false),
        )
    }

    @Test fun transitioningHotspotIsWaitedForInsteadOfFailing() {
        assertEquals(
            AutoConnectAction.WAIT_FOR_HOTSPOT,
            decide(state = CarHotspotState.ENABLING, autoStart = true),
        )
        assertEquals(
            AutoConnectAction.WAIT_FOR_HOTSPOT,
            decide(state = CarHotspotState.DISABLING, autoStart = true),
        )
        assertEquals(
            AutoConnectAction.CONNECT,
            decide(state = CarHotspotState.ENABLING, autoStart = false),
        )
    }

    @Test fun unreadableStateDoesNotBlockTheHostWhenTheUserDidNotOptIn() {
        assertEquals(AutoConnectAction.CONNECT, decide(state = null, autoStart = false))
        assertEquals(AutoConnectAction.START_HOTSPOT, decide(state = null, autoStart = true))
    }
}
