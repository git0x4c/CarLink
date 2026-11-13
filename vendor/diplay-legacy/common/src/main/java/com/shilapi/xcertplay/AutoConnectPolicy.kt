package com.shilapi.xcertplay

import com.shilapi.xcertplay.network.CarHotspotState
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode

/**
 * What the home screen should do when an unattended start was requested (boot, or DiPlay opened
 * with auto-connect on).
 *
 * On the GL19-class units DiPlay is the access point's *client*: the car's own hotspot must be
 * running before the AirPlay listener has an interface to bind. "Hotspot is off" is therefore the
 * most common reason an otherwise correct auto-start silently does nothing, and it is the one
 * prerequisite this app can act on without the user touching the head unit.
 */
internal enum class AutoConnectAction {
    /** Every prerequisite this screen can check is satisfied: hand over to the host. */
    CONNECT,

    /** Turn the car's own access point on first, then connect. */
    START_HOTSPOT,

    /** The access point is still coming up; poll again instead of failing the attempt. */
    WAIT_FOR_HOTSPOT,

    /** Only the user can unblock this (missing profile, no selected iPhone, bad password). */
    NEEDS_SETUP,

    /** A session already exists, so a second start would fight it. */
    SKIP,
}

internal object AutoConnectPolicy {
    /**
     * Pure decision so the unattended flow can be unit tested without a head unit.
     *
     * @param hotspotState last known AP state, or null when the firmware did not answer.
     * @param autoStartHotspot user opt-in; without it an off access point is reported, never switched.
     */
    fun decide(
        hotspotMode: WirelessHotspotMode,
        hotspotState: CarHotspotState?,
        autoStartHotspot: Boolean,
        hotspotProfileSaved: Boolean,
        phoneSelected: Boolean,
        sessionRunning: Boolean,
    ): AutoConnectAction {
        if (sessionRunning) return AutoConnectAction.SKIP
        if (!phoneSelected) return AutoConnectAction.NEEDS_SETUP
        // Only the device-owned AP needs these checks; P2P and LocalOnly own their own bring-up.
        if (hotspotMode != WirelessHotspotMode.MANUAL) return AutoConnectAction.CONNECT
        if (!hotspotProfileSaved) return AutoConnectAction.NEEDS_SETUP
        return when (hotspotState) {
            CarHotspotState.ENABLED -> AutoConnectAction.CONNECT
            CarHotspotState.ENABLING, CarHotspotState.DISABLING ->
                if (autoStartHotspot) AutoConnectAction.WAIT_FOR_HOTSPOT else AutoConnectAction.CONNECT
            CarHotspotState.DISABLED, CarHotspotState.FAILED ->
                if (autoStartHotspot) AutoConnectAction.START_HOTSPOT else AutoConnectAction.NEEDS_SETUP
            // Unreadable state is not proof the AP is down: let the host report the real reason
            // unless the user asked for the automatic switch.
            CarHotspotState.UNKNOWN, null ->
                if (autoStartHotspot) AutoConnectAction.START_HOTSPOT else AutoConnectAction.CONNECT
        }
    }
}
