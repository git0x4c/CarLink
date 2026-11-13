package com.shilapi.xcertplay

import android.bluetooth.BluetoothManager
import android.content.Context
import com.shilapi.xcertplay.compat.systemService
import com.shilapi.xcertplay.network.CarHotspotStatus
import com.shilapi.xcertplay.network.CarHotspotTools
import com.shilapi.xcertplay.orchestration.ManualHotspotValidation
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode

/**
 * One read-only answer to "why did DiPlay not connect by itself?".
 *
 * The unattended path has several independent preconditions and only the head unit can satisfy
 * them. Collecting them here means a car test produces a verdict from the exported report instead
 * of another round trip for logcat. Nothing here changes state, and no SSID, passphrase, device
 * name or address is printed.
 */
internal object AutoConnectSelfCheck {
    /** Machine-readable verdict; the home screen maps it to its own wording. */
    enum class Readiness(val label: String) {
        AUTO_CONNECT_OFF("auto-connect is off"),
        PROFILE_INCOMPLETE("hotspot profile incomplete"),
        NO_PHONE("no iPhone selected"),
        BLUETOOTH_OFF("Bluetooth is off"),
        HOTSPOT_OFF("car hotspot is off and auto-start is off"),
        READY("ready to start unattended"),
    }

    fun readiness(context: Context): Readiness {
        val mode = AirPlayPersistence.loadWirelessHotspotMode(context)
        val profileError = ManualHotspotValidation.error(
            AirPlayPersistence.loadManualHotspotSsid(context),
            AirPlayPersistence.loadManualHotspotPassphrase(context),
        )
        val apState = hotspotEnabled(context, mode)
        val bluetoothEnabled = runCatching {
            context.systemService(BluetoothManager::class.java, "bluetooth")?.adapter?.isEnabled == true
        }.getOrDefault(false)
        return when {
            !DiPlayPreferences.autoConnect(context) -> Readiness.AUTO_CONNECT_OFF
            mode == WirelessHotspotMode.MANUAL && profileError != null -> Readiness.PROFILE_INCOMPLETE
            DiPlayPreferences.phoneAddress(context) == null -> Readiness.NO_PHONE
            !bluetoothEnabled -> Readiness.BLUETOOTH_OFF
            mode == WirelessHotspotMode.MANUAL && apState == false &&
                !DiPlayPreferences.autoStartHotspot(context) -> Readiness.HOTSPOT_OFF
            else -> Readiness.READY
        }
    }

    private fun hotspotEnabled(context: Context, mode: WirelessHotspotMode): Boolean? =
        if (mode != WirelessHotspotMode.MANUAL) {
            null
        } else {
            CarHotspotStatus.isEnabled(context) ?: CarHotspotTools.readState(context)?.let {
                it == com.shilapi.xcertplay.network.CarHotspotState.ENABLED
            }
        }

    fun report(context: Context): String {
        val mode = AirPlayPersistence.loadWirelessHotspotMode(context)
        val profileError = ManualHotspotValidation.error(
            AirPlayPersistence.loadManualHotspotSsid(context),
            AirPlayPersistence.loadManualHotspotPassphrase(context),
        )
        val apState = hotspotEnabled(context, mode)
        val adapter = context.systemService(BluetoothManager::class.java, "bluetooth")?.adapter
        val bluetoothEnabled = runCatching { adapter?.isEnabled == true }.getOrDefault(false)
        val bondedIPhones = runCatching {
            adapter?.bondedDevices?.count { it.name?.contains("iPhone", ignoreCase = true) == true } ?: 0
        }.getOrDefault(0)
        val selected = DiPlayPreferences.phoneAddress(context) != null
        val wireless = AirPlayPersistence.loadWirelessEnabled(context)

        val verdict = readiness(context).label
        return "Auto-connect self check: verdict=$verdict transport=${if (wireless) "wireless" else "USB"} " +
            "autoConnect=${DiPlayPreferences.autoConnect(context)} " +
            "openAfterBoot=${AirPlayPersistence.loadAutoStartOnBoot(context)} " +
            "autoStartHotspot=${DiPlayPreferences.autoStartHotspot(context)} " +
            "hotspotMode=$mode apState=${apState ?: "unreadable"} " +
            "profileSaved=${profileError == null} " +
            "phoneSelected=$selected bluetoothEnabled=$bluetoothEnabled bondedIPhones=$bondedIPhones; " +
            "'ready to start unattended' still needs the car's AP and the iPhone side to answer."
    }
}
