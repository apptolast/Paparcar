package com.rndeveloper.paparcar.detection

import android.content.Context
import androidx.core.content.edit

/**
 * [DET-STOP-BUTTON-001] Durable "the user stopped detection at T" stamp, in the SAME
 * SharedPreferences the safety-net worker and [SentryResidenceStore] already own.
 *
 * It has to outlive the process: the whole point of the quiet period is that the NEXT automatic
 * trigger must not arm, and those triggers (geofence EXIT PendingIntent, AR transition) routinely
 * arrive at a service that was torn down in between — an in-memory field would be wiped exactly
 * when the stamp matters. Reading it back is what makes the button hold.
 *
 * Single slot on purpose: the quiet period is a property of the user's last intent, not of a
 * session — stopping twice simply re-stamps and restarts the clock. Cleared by a MANUAL arm ("I'm
 * driving" is the same user retracting the stop), so an explicit restart is never blocked.
 */
object UserStopStore {
    private const val PREFS = "parking_safety_net"
    private const val KEY = "user_stopped_at"

    /** Persist the stop moment (epoch-ms). */
    fun stamp(context: Context, stoppedAtMs: Long) {
        prefs(context).edit { putLong(KEY, stoppedAtMs) }
    }

    /** Epoch-ms of the last user stop, or null when there is none on record. */
    fun read(context: Context): Long? =
        prefs(context).getLong(KEY, NO_STAMP).takeIf { it != NO_STAMP }

    fun clear(context: Context) {
        prefs(context).edit { remove(KEY) }
    }

    private const val NO_STAMP = -1L

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
