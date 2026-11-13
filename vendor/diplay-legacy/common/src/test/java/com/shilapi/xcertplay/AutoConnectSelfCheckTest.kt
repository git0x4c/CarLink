package com.shilapi.xcertplay

import com.shilapi.xcertplay.orchestration.ManualHotspotBand
import com.shilapi.xcertplay.orchestration.ManualHotspotSecurity
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class AutoConnectSelfCheckTest {
    private val app get() = RuntimeEnvironment.getApplication()

    @Test fun reportsPreconditionsWithoutSecrets() {
        AirPlayPersistence.saveManualHotspotProfile(
            app, "GLAP-Test", "secret-pass", ManualHotspotSecurity.WPA2, ManualHotspotBand.AUTO, 0,
        )
        AirPlayPersistence.saveWirelessHotspotMode(app, WirelessHotspotMode.MANUAL)
        DiPlayPreferences.saveAutoConnect(app, true)
        DiPlayPreferences.saveAutoStartHotspot(app, true)
        DiPlayPreferences.savePhone(app, "00:11:22:33:44:55", "Your iPhone")

        val report = AutoConnectSelfCheck.report(app)
        assertTrue(report, report.contains("autoConnect=true"))
        assertTrue(report, report.contains("autoStartHotspot=true"))
        assertTrue(report, report.contains("profileSaved=true"))
        assertTrue(report, report.contains("phoneSelected=true"))
        assertTrue(report, report.contains("hotspotMode=MANUAL"))
        assertFalse(report, report.contains("GLAP-Test"))
        assertFalse(report, report.contains("secret-pass"))
        assertFalse(report, report.contains("00:11:22"))
    }

    @Test fun missingProfileAndPhoneAreNamedInTheVerdict() {
        AirPlayPersistence.saveManualHotspotProfile(
            app, "", "", ManualHotspotSecurity.WPA2, ManualHotspotBand.AUTO, 0,
        )
        AirPlayPersistence.saveWirelessHotspotMode(app, WirelessHotspotMode.MANUAL)
        DiPlayPreferences.saveAutoConnect(app, true)
        val report = AutoConnectSelfCheck.report(app)
        assertTrue(report, report.contains("verdict=hotspot profile incomplete"))
    }
}
