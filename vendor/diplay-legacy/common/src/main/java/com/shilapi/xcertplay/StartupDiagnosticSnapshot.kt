package com.shilapi.xcertplay

import android.content.Context

/** Only the latest received boot event and launch result; no Intent extras or device identifiers. */
internal object StartupDiagnosticSnapshot {
    private const val PREFS = "diplay_startup_diagnostics"

    /**
     * True when another boot broadcast already launched this app inside [windowMillis]. Persisted
     * so a receiver delivered in a second process still sees the first launch.
     */
    fun launchedRecently(context: Context, now: Long, windowMillis: Long): Boolean = runCatching {
        val last = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("launchAt", 0L)
        now - last in 0 until windowMillis
    }.getOrDefault(false)

    fun received(context: Context, launchEnabled: Boolean, source: String = "boot") {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong("receivedAt", System.currentTimeMillis())
                .putLong("launchAt", System.currentTimeMillis())
                .putBoolean("launchEnabled", launchEnabled)
                .putString("source", source)
                .putInt("duplicates", 0)
                .putString("launchResult", if (launchEnabled) "pending" else "disabled")
                .remove("failureClass").apply()
        }
    }

    /** A second boot broadcast within the debounce window: recorded, not launched again. */
    fun duplicate(context: Context, source: String) {
        runCatching {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit()
                .putInt("duplicates", prefs.getInt("duplicates", 0) + 1)
                .putString("duplicateSource", source)
                .apply()
        }
    }

    fun launchResult(context: Context, failure: RuntimeException? = null) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("launchResult", if (failure == null) "startActivity-returned" else "failed")
                .putString("failureClass", failure?.javaClass?.simpleName?.take(80)
                    ?.replace(Regex("[^A-Za-z0-9_$]"), "?")).apply()
        }
    }

    fun report(context: Context): String = runCatching {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val receivedAt = prefs.getLong("receivedAt", 0)
        if (receivedAt == 0L) {
            "Boot diagnostics received=false; no boot delivery has been recorded by this version."
        } else {
            "Boot diagnostics received=true receivedAtEpochMs=$receivedAt " +
                "source=${prefs.getString("source", "boot")} " +
                "duplicateBoots=${prefs.getInt("duplicates", 0)} " +
                "launchEnabledAtBoot=${prefs.getBoolean("launchEnabled", false)} " +
                "launchResult=${prefs.getString("launchResult", "unknown")} " +
                "failureClass=${prefs.getString("failureClass", "none") ?: "none"}; " +
                "startActivity-returned does not prove the OEM displayed the app."
        }
    }.getOrDefault("Boot diagnostics unavailable.")
}
