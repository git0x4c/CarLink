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
    fun report(context: Context): String {
        val mode = AirPlayPersistence.loadWirelessHotspotMode(context)
        val ssid = AirPlayPersistence.loadManualHotspotSsid(context)
        val passphrase = AirPlayPersistence.loadManualHotspotPassphrase(context)
        val profileError = ManualHotspotValidation.error(ssid, passphrase)
        val apState = if (mode == WirelessHotspotMode.MANUAL) {
            CarHotspotStatus.isEnabled(context) ?: CarHotspotTools.readState(context)?.let {
                it == com.shilapi.xcertplay.network.CarHotspotState.ENABLED
            }
        } else {
            null
        }
        val adapter = context.systemService(BluetoothManager::class.java, "bluetooth")?.adapter
        val bluetoothEnabled = runCatching { adapter?.isEnabled == true }.getOrDefault(false)
        val bondedIPhones = runCatching {
            adapter?.bondedDevices?.count { it.name?.contains("iPhone", ignoreCase = true) == true } ?: 0
        }.getOrDefault(0)
        val selected = DiPlayPreferences.phoneAddress(context) != null
        val wireless = AirPlayPersistence.loadWirelessEnabled(context)

        val verdict = when {
            !DiPlayPreferences.autoConnect(context) -> "auto-connect is off"
            mode == WirelessHotspotMode.MANUAL && profileError != null -> "hotspot profile incomplete"
            !selected -> "no iPhone selected"
            !bluetoothEnabled -> "Bluetooth is off"
            mode == WirelessHotspotMode.MANUAL && apState == false &&
                !DiPlayPreferences.autoStartHotspot(context) -> "car hotspot is off and auto-start is off"
            else -> "ready to start unattended"
        }
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
