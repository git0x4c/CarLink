package com.shilapi.xcertplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Starts the CarPlay host after boot when the user has enabled the startup option. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        // Several head-unit firmwares (MTK quick-boot images among them) never send
        // BOOT_COMPLETED on a cold boot and use a vendor quick-boot action instead.
        if (action != Intent.ACTION_BOOT_COMPLETED && action !in QUICK_BOOT_ACTIONS) return
        val source = if (action == Intent.ACTION_BOOT_COMPLETED) "boot" else "quickboot"
        if (StartupDiagnosticSnapshot.launchedRecently(context, System.currentTimeMillis(), LAUNCH_DEBOUNCE_MILLIS)) {
            StartupDiagnosticSnapshot.duplicate(context, source)
            return
        }
        val launchEnabled = AirPlayPersistence.loadAutoStartOnBoot(context)
        StartupDiagnosticSnapshot.received(context, launchEnabled, source)
        if (!launchEnabled) return

        val launch = Intent(context, com.shilapi.xcertplay.legacy.LegacyHomeActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        }
        try {
            context.startActivity(launch)
            StartupDiagnosticSnapshot.launchResult(context)
        } catch (error: RuntimeException) {
            StartupDiagnosticSnapshot.launchResult(context, error)
            Log.w(TAG, "Boot auto-start could not launch LegacyHomeActivity", error)
        }
    }

    private companion object {
        const val TAG = "xcertplay-boot"
        const val LAUNCH_DEBOUNCE_MILLIS = 30_000L
        val QUICK_BOOT_ACTIONS = setOf(
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
        )
    }
}
