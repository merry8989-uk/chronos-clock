package com.osintgram.chronos.diagnostics

/**
 * The trail of what the user actually did, in the order they did it.
 *
 * A stack trace says where the app fell over; breadcrumbs say what the user was
 * doing when it happened. Every call is one short line, so the trail is cheap
 * enough to leave on all the time.
 */
object Breadcrumbs {

    private const val NAV = "Nav"
    private const val UI = "UI"

    /** A screen or tab became visible. */
    fun screen(name: String) {
        DiagnosticsLogger.log(NAV, "screen=$name")
    }

    /** A user action worth remembering. */
    fun action(name: String, detail: String = "") {
        DiagnosticsLogger.log(
            UI,
            if (detail.isBlank()) "action=$name" else "action=$name $detail",
        )
    }
}
