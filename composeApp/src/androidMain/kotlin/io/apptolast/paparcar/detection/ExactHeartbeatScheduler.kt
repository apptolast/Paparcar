package io.apptolast.paparcar.detection

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.edit
import io.apptolast.paparcar.detection.receiver.ExactHeartbeatReceiver
import io.apptolast.paparcar.domain.detection.isExactHeartbeatLaneDead
import io.apptolast.paparcar.domain.detection.nextExactHeartbeatMissStreak
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
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
 */
object ExactHeartbeatScheduler {

    fun sync(context: Context, shouldBeArmed: Boolean, config: ParkingDetectionConfig) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val previousNextAt = prefs(context).getLong(KEY_NEXT_AT, 0L).takeIf { it > 0L }
        val wasArmed = previousNextAt != null
        if (!shouldBeArmed) {
            alarmManager.cancel(pendingIntent(context))
            prefs(context).edit { remove(KEY_NEXT_AT); remove(KEY_MISS_STREAK) }
            if (wasArmed) PaparcarLogger.d(TAG, "⏰ exact net DISARMED — no parked session to watch")
            return
        }
        val now = System.currentTimeMillis()
        // [DET-HEARTBEAT-MISS-IS-EVIDENCE-001] Read the OUTGOING arm before overwriting it. A tick
        // whose moment passed without the receiver coming back to push this forward is one the lane
        // lost — the only place in the app where that fact is observable, since the measurement
        // used to live inside the receiver that never runs.
        recordTick(context, previousNextAt, now, config)
        val exact = canScheduleExact(alarmManager)
        val triggerAt = now + INTERVAL_MS
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

    /**
     * [DET-HEARTBEAT-MISS-IS-EVIDENCE-001] Has this device's fast lane stopped working?
     *
     * `true` means the safety net has degraded to the 15-minute periodic grid alone. Stamped into
     * every session header from here on, so a trip lost inside one of those 15-minute cells can be
     * attributed from Firestore instead of from a cable and `dumpsys`.
     */
    fun isLaneDead(context: Context, config: ParkingDetectionConfig): Boolean =
        isExactHeartbeatLaneDead(prefs(context).getInt(KEY_MISS_STREAK, 0), config)

    /**
     * [DET-HEARTBEAT-MISS-IS-EVIDENCE-001] A tick was actually delivered: the lane works here.
     * Called from the receiver, which by definition only runs when it does.
     */
    fun markLaneAlive(context: Context, config: ParkingDetectionConfig) {
        val before = prefs(context).getInt(KEY_MISS_STREAK, 0)
        if (before == 0) return
        prefs(context).edit { putInt(KEY_MISS_STREAK, 0) }
        if (isExactHeartbeatLaneDead(before, config)) {
            PaparcarLogger.d(TAG, "⏰ exact net RECOVERED after $before lost ticks [DET-HEARTBEAT-MISS-IS-EVIDENCE-001]")
        }
    }

    /**
     * Folds the outgoing arm into the lost-tick streak and announces the two transitions that
     * matter. Called from [sync] only — one writer, so the streak cannot drift.
     */
    private fun recordTick(context: Context, previousNextAt: Long?, nowMs: Long, config: ParkingDetectionConfig) {
        val before = prefs(context).getInt(KEY_MISS_STREAK, 0)
        val after = nextExactHeartbeatMissStreak(before, previousNextAt, nowMs, config)
        if (after == before) return
        prefs(context).edit { putInt(KEY_MISS_STREAK, after) }
        val wasDead = isExactHeartbeatLaneDead(before, config)
        val isDead = isExactHeartbeatLaneDead(after, config)
        when {
            isDead && !wasDead -> PaparcarLogger.w(
                TAG,
                "⏰ exact net DEAD — $after ticks armed and never delivered; the safety net is down to " +
                    "the 15-min periodic on this device [DET-HEARTBEAT-MISS-IS-EVIDENCE-001]",
            )
            wasDead && !isDead -> PaparcarLogger.d(
                TAG,
                "⏰ exact net RECOVERED — a tick came back [DET-HEARTBEAT-MISS-IS-EVIDENCE-001]",
            )
            after > before -> PaparcarLogger.d(
                TAG,
                "⏰ exact tick LOST (streak=$after, overdue ${(nowMs - (previousNextAt ?: nowMs)) / 1000}s) " +
                    "[DET-HEARTBEAT-MISS-IS-EVIDENCE-001]",
            )
        }
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

    /** [DET-HEARTBEAT-MISS-IS-EVIDENCE-001] Consecutive armed ticks that never came back. Disk-backed
     *  alongside the arm it judges, so an OEM process kill between ticks cannot reset the count and
     *  hide a lane that has been dead for hours. */
    private const val KEY_MISS_STREAK = "miss_streak"
}
