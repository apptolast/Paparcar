package io.apptolast.paparcar.detection

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.edit
import io.apptolast.paparcar.detection.receiver.ExactHeartbeatReceiver
import io.apptolast.paparcar.domain.util.PaparcarLogger

/**
 * Exact-alarm polling net while a session is parked. [DET-EXACT-HEARTBEAT-001]
 *
 * WorkManager's 15-min periodic is Doze-batched into maintenance windows, and the event triggers
 * (AR / geofence / significant motion) depend on Play Services delivering to a possibly-dead
 * process — exactly what fails in the field FNs. An exact alarm (`setExactAndAllowWhileIdle`)
 * fires even in Doze (stretched to ~9-15 min in deep Doze, but it FIRES), and its receiver is an
 * exempted context on Android 12+. This is the Driversnote heartbeat: not a new evaluator, just a
 * more punctual trigger for the existing [io.apptolast.paparcar.detection.worker.ParkingSafetyNetWorker]
 * check chain. Waking never confirms anything — the evaluator demands the same proofs as always.
 *
 * Lifecycle mirrors the significant-motion sync: the safety-net worker calls [sync] on EVERY tick
 * with "parked and idle", so arming, re-arming after a fire, and disarming when the session ends
 * all live in ONE place and self-heal through process kills (the next periodic tick re-arms).
 * One-shot by design (`setExactAndAllowWhileIdle` cannot repeat): each armed tick re-arms the next.
 *
 * Permission: `SCHEDULE_EXACT_ALARM` is auto-granted up to targetSdk 32 and a user-revocable
 * special access from 33+. [canScheduleExact] gates every arm; without it the net degrades to
 * `setAndAllowWhileIdle` (inexact, still Doze-piercing) — degradation, never breakage.
 *
 * **Field reality + accepted design [IOS-F0-09, decisión 2026-08-13]:** with targetSdk 36 the
 * special access is DENIED by default, so on real installs `exact=false` is the NORMAL case and
 * this net effectively runs the inexact allow-while-idle variant (OS-batched, still fires in
 * Doze). That is ACCEPTED: no permission-request UI will be added for it — the ask isn't worth
 * the friction when the 15-min safety-net periodic remains the floor and the evaluator demands
 * the same proofs regardless of when it wakes. "Exact" in these names describes the capability
 * CEILING when the user grants the special access, never a field guarantee — no consumer may
 * assume the 5-min cadence. (Deliberately not renamed: the receiver is a manifest component and
 * the `exact_heartbeat` prefs file is pinned by the backup rules.)
 */
object ExactHeartbeatScheduler {

    fun sync(context: Context, shouldBeArmed: Boolean) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val wasArmed = prefs(context).getLong(KEY_NEXT_AT, 0L) > 0L
        if (!shouldBeArmed) {
            alarmManager.cancel(pendingIntent(context))
            prefs(context).edit { remove(KEY_NEXT_AT) }
            if (wasArmed) PaparcarLogger.d(TAG, "⏰ exact net DISARMED — no parked session to watch")
            return
        }
        val exact = canScheduleExact(alarmManager)
        val triggerAt = System.currentTimeMillis() + INTERVAL_MS
        val pi = pendingIntent(context)
        if (exact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            // Special access revoked/not granted: inexact allow-while-idle still pierces Doze,
            // just with OS batching. The 15-min periodic remains the floor either way.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
        prefs(context).edit { putLong(KEY_NEXT_AT, triggerAt) }
        if (!wasArmed) PaparcarLogger.d(TAG, "⏰ exact net ARMED (exact=$exact, every ${INTERVAL_MS / 60_000} min)")
    }

    /** Doze/OEM stretch of the last armed alarm: fired-at minus scheduled-at, or null when unknown.
     *  THE field metric of how punctual the net really is per device. [DET-EXACT-HEARTBEAT-001] */
    fun firedDelayMs(context: Context, nowMs: Long): Long? {
        val nextAt = prefs(context).getLong(KEY_NEXT_AT, 0L)
        return if (nextAt > 0L) nowMs - nextAt else null
    }

    fun canScheduleExact(alarmManager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, ExactHeartbeatReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private const val TAG = "PARKDIAG/ExactNet"
    private const val REQUEST_CODE = 41_005

    /** Target cadence while parked — Driversnote's decompiled heartbeatInterval is 300 s; deep
     *  Doze legally stretches allow-while-idle to ~9-15 min, which is still 'far' better than the
     *  Doze-batched WorkManager periodic. */
    private const val INTERVAL_MS = 5 * 60 * 1_000L

    private const val PREFS_NAME = "exact_heartbeat"
    private const val KEY_NEXT_AT = "next_at"
}
