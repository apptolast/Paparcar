package com.rndeveloper.paparcar.detection

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.edit
import androidx.work.WorkManager
import com.rndeveloper.paparcar.detection.receiver.ExactHeartbeatReceiver
import com.rndeveloper.paparcar.detection.service.CoordinatorDetectionService
import com.rndeveloper.paparcar.detection.worker.ParkingSafetyNetWorker
import com.rndeveloper.paparcar.domain.detection.sentry.HeartbeatLane
import com.rndeveloper.paparcar.domain.detection.sentry.heartbeatDeliveryLane
import com.rndeveloper.paparcar.domain.detection.sentry.isExactHeartbeatLaneDead
import com.rndeveloper.paparcar.domain.detection.sentry.nextExactHeartbeatMissStreak
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import com.rndeveloper.paparcar.domain.util.PaparcarLogger

/**
 * Exact-alarm polling net while a session is parked. [DET-EXACT-HEARTBEAT-001]
 *
 * WorkManager's 15-min periodic is Doze-batched into maintenance windows, and the event triggers
 * (AR / geofence / significant motion) depend on Play Services delivering to a possibly-dead
 * process — exactly what fails in the field FNs. An exact alarm (`setExactAndAllowWhileIdle`)
 * fires even in Doze (stretched to ~9-15 min in deep Doze, but it FIRES), and its receiver is an
 * exempted context on Android 12+. This is the Driversnote heartbeat: not a new evaluator, just a
 * more punctual trigger for the existing [com.rndeveloper.paparcar.detection.worker.ParkingSafetyNetWorker]
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
 *
 * **Two lanes since [DET-HEARTBEAT-LANE-REPAIR-001].** On the Oppo CPH2371 the broadcast lane died
 * at an exact second while the service lane kept delivering the same physical events, so the tick
 * now travels by `getForegroundService` into the single intake — but only where that is both legal
 * and free. [com.rndeveloper.paparcar.domain.detection.sentry.heartbeatDeliveryLane] holds the two
 * guards and the measurements behind them; this object only reads their inputs and arms the
 * PendingIntent they choose.
 */
object ExactHeartbeatScheduler {

    fun sync(context: Context, shouldBeArmed: Boolean, config: ParkingDetectionConfig) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val previousNextAt = prefs(context).getLong(KEY_NEXT_AT, 0L).takeIf { it > 0L }
        val wasArmed = previousNextAt != null
        if (!shouldBeArmed) {
            cancelEveryLane(context, alarmManager)
            prefs(context).edit { remove(KEY_NEXT_AT); remove(KEY_MISS_STREAK); remove(KEY_LANE) }
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
        // [DET-HEARTBEAT-LANE-REPAIR-001] The decision is a pure function in commonMain; this reads
        // the two inputs it judges. Residency is the stamp the service itself writes, so it is also
        // the only honest answer to "is a sentry watching this parked state" from outside it.
        val lane = heartbeatDeliveryLane(
            canScheduleExact = exact,
            sentryResident = SentryResidenceStore.read(context) != null,
        )
        val previousLane = armedLane(context)
        val triggerAt = now + INTERVAL_MS
        // ⛔ The OTHER lane's alarm has to go, and this is not belt-and-braces: `AlarmManager.cancel`
        // matches by PendingIntent, and the two lanes are two different records (different type,
        // different Intent). Re-arming on a lane switch without cancelling would leave the old
        // alarm pending — a zombie tick entering by the door we just decided not to use. The switch
        // is not hypothetical: residency flips every time a Bluetooth car parks beside a Coordinator
        // one, and the permission can be revoked between two ticks.
        cancelEveryLane(context, alarmManager, except = lane)
        val pi = pendingIntent(context, lane)
        if (exact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            // Special access revoked/not granted: inexact allow-while-idle still pierces Doze,
            // just with OS batching. The 15-min periodic remains the floor either way.
            // (And it is why such a device stays on BROADCAST: see [heartbeatDeliveryLane].)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
        prefs(context).edit { putLong(KEY_NEXT_AT, triggerAt); putString(KEY_LANE, lane.name) }
        when {
            !wasArmed -> PaparcarLogger.d(
                TAG,
                "⏰ exact net ARMED (exact=$exact, lane=$lane, every ${INTERVAL_MS / 60_000} min)",
            )
            previousLane != null && previousLane != lane -> PaparcarLogger.d(
                TAG,
                "⏰ exact net lane SWITCHED $previousLane → $lane (exact=$exact) " +
                    "[DET-HEARTBEAT-LANE-REPAIR-001]",
            )
        }
    }

    /**
     * What a delivered tick does, in ONE place. [DET-HEARTBEAT-LANE-REPAIR-001]
     *
     * Two doors now reach it — the receiver and the service's `ACTION_HEARTBEAT` — and the triple
     * below is the whole behaviour of a tick: say the lane works, re-arm the next one, hand the
     * check to the evaluator that always decides. Duplicating it per door is how the two would
     * drift, and a drifted heartbeat is invisible until a trip is already lost.
     *
     * ⛔ It does NOT arm the coordinator. Waking is not evidence: the tick only asks the existing
     * safety-net chain to look, and that chain demands the same proofs as always.
     */
    fun onTickDelivered(context: Context, config: ParkingDetectionConfig, lane: HeartbeatLane) {
        val delay = firedDelayMs(context, System.currentTimeMillis())
        PaparcarLogger.d(TAG, "⏰ exact heartbeat fired via $lane (doze stretch: ${delay ?: "?"} ms)")
        markLaneAlive(context, config)
        sync(context, shouldBeArmed = true, config = config)
        ParkingSafetyNetWorker.enqueueCheckNow(
            WorkManager.getInstance(context),
            source = ParkingSafetyNetWorker.SOURCE_EXACT_ALARM,
        )
    }

    /** The lane the currently armed tick will arrive by, or null when nothing is armed.
     *  Stamped into the session header so "the fix did not work" and "the fix never applied" stop
     *  looking identical from Firestore. [DET-HEARTBEAT-LANE-REPAIR-001] */
    fun armedLane(context: Context): HeartbeatLane? =
        prefs(context).getString(KEY_LANE, null)
            ?.let { name -> HeartbeatLane.entries.firstOrNull { it.name == name } }

    private fun cancelEveryLane(context: Context, alarmManager: AlarmManager, except: HeartbeatLane? = null) {
        HeartbeatLane.entries
            .filter { it != except }
            .forEach { alarmManager.cancel(pendingIntent(context, it)) }
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

    /**
     * ⛔ The broadcast branch keeps its original request code and Intent **byte for byte**. An app
     * updated in place can have a tick already armed with that exact PendingIntent, and only an
     * equal one cancels or replaces it; "tidying" it would orphan the alarm instead of moving it.
     */
    private fun pendingIntent(context: Context, lane: HeartbeatLane): PendingIntent = when (lane) {
        HeartbeatLane.BROADCAST -> PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, ExactHeartbeatReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        HeartbeatLane.SERVICE -> PendingIntent.getForegroundService(
            context,
            REQUEST_CODE_SERVICE,
            Intent(context, CoordinatorDetectionService::class.java)
                .setAction(CoordinatorDetectionService.ACTION_HEARTBEAT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private const val TAG = "PARKDIAG/ExactNet"
    private const val REQUEST_CODE = 41_005

    /** Its own code so the two lanes are two cancellable records, never one that overwrites the
     *  other. [DET-HEARTBEAT-LANE-REPAIR-001] */
    private const val REQUEST_CODE_SERVICE = 41_006

    /** Target cadence while parked — Driversnote's decompiled heartbeatInterval is 300 s; deep
     *  Doze legally stretches allow-while-idle to ~9-15 min, which is still 'far' better than the
     *  Doze-batched WorkManager periodic. */
    private const val INTERVAL_MS = 5 * 60 * 1_000L

    private const val PREFS_NAME = "exact_heartbeat"
    private const val KEY_NEXT_AT = "next_at"

    /** [DET-HEARTBEAT-LANE-REPAIR-001] Which lane the armed tick will come by. Disk-backed beside
     *  the arm it describes: the process can die between arming and firing, and the handler has to
     *  know which door it came through to report it. */
    private const val KEY_LANE = "armed_lane"

    /** [DET-HEARTBEAT-MISS-IS-EVIDENCE-001] Consecutive armed ticks that never came back. Disk-backed
     *  alongside the arm it judges, so an OEM process kill between ticks cannot reset the count and
     *  hide a lane that has been dead for hours. */
    private const val KEY_MISS_STREAK = "miss_streak"
}
