package com.shilapi.xcertplay.legacy

import com.shilapi.xcertplay.AirPlayPersistence
import com.shilapi.xcertplay.DiPlayPreferences
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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class LegacyDiagnosticExportTest {
    private val app get() = RuntimeEnvironment.getApplication()

    @Test
    fun reportCarriesTheConnectionFactsAndNoSecrets() {
        AirPlayPersistence.saveManualHotspotProfile(
            app, "GLAP-Test", "secret-pass", ManualHotspotSecurity.WPA2, ManualHotspotBand.AUTO, 0,
        )
        AirPlayPersistence.saveWirelessHotspotMode(app, WirelessHotspotMode.MANUAL)
        DiPlayPreferences.saveAutoConnect(app, true)
        DiPlayPreferences.savePhone(app, "00:11:22:33:44:55", "Your iPhone")

        val report = LegacyDiagnosticExport.buildReport(app)
        assertTrue(report, report.contains("--- CarLink Legacy diagnostics ---"))
        assertTrue(report, report.contains("Auto-connect self check"))
        assertTrue(report, report.contains("Saved auto settings:"))
        assertFalse("the SSID must not leak", report.contains("GLAP-Test"))
        assertFalse("the passphrase must not leak", report.contains("secret-pass"))
        assertFalse("the phone address must not leak", report.contains("00:11:22"))
    }

    @Test
    fun exportWritesAFileThatCanBeReadBack() {
        val result = LegacyDiagnosticExport.export(app, "CarLinkLegacy-report-test.txt")
        val file = File(result.path)
        assertTrue("export must produce a readable file: ${result.path}", file.isFile)
        assertTrue(file.readText().contains("--- CarLink Legacy diagnostics ---"))
    }
}
