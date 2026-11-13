package com.shilapi.xcertplay

import android.content.Context

/**
 * Last unattended-start decision, kept as coarse text only.
 *
 * The one question a car test has to answer is "why did the app not connect by itself?", and
 * logcat is not reachable on a parked-head-unit test. This snapshot travels in the exported
 * diagnostic report. It never stores an SSID, passphrase, phone name or address.
 */
internal object AutoConnectDiagnostics {
    private const val PREFS = "diplay_auto_connect_diagnostics"

    fun record(context: Context, step: String, detail: String = "") {
        runCatching {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val attempts = prefs.getInt("attempts", 0) + 1
            prefs.edit()
                .putLong("atEpochMs", System.currentTimeMillis())
                .putInt("attempts", attempts)
                .putString("lastStep", step.take(48))
                .putString("lastDetail", detail.take(160))
                .putString("lastAction", prefs.getString("lastAction", "none"))
                .apply()
        }
    }

    fun action(context: Context, action: String) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("lastAction", action.take(48))
                .apply()
        }
    }

    fun report(context: Context): String = runCatching {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val at = prefs.getLong("atEpochMs", 0L)
        if (at == 0L) {
            "Auto-connect diagnostics: none recorded by this install yet."
        } else {
            "Auto-connect: attempts=${prefs.getInt("attempts", 0)} " +
                "atEpochMs=$at lastAction=${prefs.getString("lastAction", "none")} " +
                "lastStep=${prefs.getString("lastStep", "none")} " +
                "detail=${prefs.getString("lastDetail", "none")}; " +
                "an action here is an attempt, not proof that the phone completed the handshake."
        }
    }.getOrDefault("Auto-connect diagnostics unavailable.")
}
