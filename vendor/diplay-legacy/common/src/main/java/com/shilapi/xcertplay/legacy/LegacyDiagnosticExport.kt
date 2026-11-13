package com.shilapi.xcertplay.legacy

import android.content.Context
import android.os.Build
import com.shilapi.xcertplay.AirPlayPersistence
import com.shilapi.xcertplay.AudioBehaviorDiagnostics
import com.shilapi.xcertplay.AutoConnectDiagnostics
import com.shilapi.xcertplay.AutoConnectSelfCheck
import com.shilapi.xcertplay.DiagnosticExportStore
import com.shilapi.xcertplay.DiagnosticRedactor
import com.shilapi.xcertplay.DiagnosticStorageTargets
import com.shilapi.xcertplay.DiPlayBootstrap
import com.shilapi.xcertplay.DiPlayPreferences
import com.shilapi.xcertplay.SessionLogFile
import com.shilapi.xcertplay.StartupDiagnosticSnapshot
import com.shilapi.xcertplay.network.CarHotspotStatus
import com.shilapi.xcertplay.orchestration.MfiTarget
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CarLink Legacy diagnostic report, written the way a parked head unit can retrieve it.
 *
 * The car test has no adb, so the report travels on a USB stick: a secondary writable volume wins,
 * the primary volume is next, and the app's private directory is the last resort. Contents are the
 * same kinds of facts the engine already exports; nothing here stores an SSID, passphrase, device
 * name or address.
 */
internal object LegacyDiagnosticExport {
    data class Result(val path: String, val usedPrivateStorage: Boolean)

    fun export(context: Context, fileName: String): Result {
        val report = buildReport(context)
        val writable = DiagnosticStorageTargets.discover(context).filter { !it.readOnly }
        val chosen = writable.firstOrNull { !it.primary } ?: writable.firstOrNull()
        if (chosen != null) {
            val saved = runCatching {
                DiagnosticExportStore.saveSelected(context, chosen, fileName, report)
            }
            saved.getOrNull()?.let { return Result(it.path ?: chosen.directory.path, false) }
        }
        val directory = File(context.filesDir, "diagnostics").apply { mkdirs() }
        val file = File(directory, fileName)
        file.writeText(report)
        return Result(file.path, true)
    }

    fun buildReport(context: Context): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "unknown"
        val code = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode
        }.getOrNull() ?: 0
        val mode = AirPlayPersistence.loadWirelessHotspotMode(context)
        val apState = if (mode == WirelessHotspotMode.MANUAL) {
            CarHotspotStatus.isEnabled(context)
        } else {
            null
        }
        val authentication = runCatching { DiPlayBootstrap.ensure(context) }.isSuccess

        return buildString {
            appendLine("--- CarLink Legacy diagnostics ---")
            appendLine("Generated: $stamp")
            appendLine("Application: ${context.packageName} version=$version code=$code")
            appendLine("Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
            appendLine("Head unit: ${Build.MANUFACTURER} ${Build.MODEL}; board=${Build.BOARD}")
            appendLine("Build: ${Build.DISPLAY}; hardware=${Build.HARDWARE}")
            appendLine(
                "Transport: ${if (AirPlayPersistence.loadWirelessEnabled(context)) "wireless" else "USB"}; " +
                    "hotspotMode=$mode apEnabled=${apState ?: "unreadable"}",
            )
            appendLine(
                "Saved auto settings: connectWhenOpened=${DiPlayPreferences.autoConnect(context)} " +
                    "openAfterBoot=${AirPlayPersistence.loadAutoStartOnBoot(context)} " +
                    "autoStartHotspot=${DiPlayPreferences.autoStartHotspot(context)}",
            )
            appendLine(
                "Selected phone present: ${DiPlayPreferences.phoneAddress(context) != null}; " +
                    "hotspot profile saved: ${AirPlayPersistence.loadManualHotspotSsid(context) != null}",
            )
            appendLine(
                "Display: scale=${AirPlayPersistence.loadDisplayScaleTenths(context) * 10}% " +
                    "fps=${AirPlayPersistence.loadFps(context)} " +
                    "hevc=${AirPlayPersistence.loadHevcEnabled(context)}",
            )
            appendLine(
                "Audio: bufferMs=${AirPlayPersistence.loadMediaBufferMillis(context)} " +
                    "geelyRouting=${AirPlayPersistence.loadGeelyAudioRouting(context)}",
            )
            appendLine("MFi target: ${mfiTargetName(AirPlayPersistence.loadMfiTarget(context))}; authentication ready: $authentication")
            appendLine()
            appendLine(AutoConnectSelfCheck.report(context))
            appendLine(AutoConnectDiagnostics.report(context))
            appendLine(StartupDiagnosticSnapshot.report(context))
            appendLine(AudioBehaviorDiagnostics.exportSummary(false))
            appendLine()
            for (name in SessionLogFile.REPORT_NAMES) {
                val file = File(context.filesDir, "logs/$name")
                if (!file.isFile) continue
                appendLine("--- $name ---")
                file.useLines { lines -> lines.forEach { line -> DiagnosticRedactor.redact(line)?.let { appendLine(it) } } }
            }
        }
    }

    private fun mfiTargetName(target: MfiTarget): String = when (target) {
        MfiTarget.LOCAL -> "LOCAL"
        MfiTarget.USB_CH341 -> "USB_CH341"
        MfiTarget.I2C -> "I2C"
        MfiTarget.REMOTE -> "REMOTE"
    }
}
