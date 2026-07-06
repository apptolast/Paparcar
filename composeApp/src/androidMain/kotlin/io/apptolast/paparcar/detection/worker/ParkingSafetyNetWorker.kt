@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.detection.worker

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.apptolast.paparcar.BuildConfig
import io.apptolast.paparcar.detection.SignificantMotionMonitor
import io.apptolast.paparcar.domain.detection.DetectionRuntimeState
import io.apptolast.paparcar.domain.diagnostics.DetectionEvent
import io.apptolast.paparcar.domain.diagnostics.DetectionEventLogger
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.notification.AppNotificationManager
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.sensor.StepCounterSource
import io.apptolast.paparcar.domain.service.DepartureEventBus
import io.apptolast.paparcar.domain.service.GeofenceManager
import io.apptolast.paparcar.domain.usecase.location.GetOneLocationUseCase
import io.apptolast.paparcar.domain.usecase.parking.EvaluateSafetyNetCheckUseCase
import io.apptolast.paparcar.domain.usecase.parking.SafetyNetAction
import io.apptolast.paparcar.domain.util.PaparcarLogger
import io.apptolast.paparcar.domain.util.haversineMeters
import io.apptolast.paparcar.notification.ForegroundNotificationProvider
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.firstOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * The parked-session safety net: the one departure guarantee that does not depend on Play
 * Services delivering anything. [DET-SAFETY-NET-001]
 *
 * Runs every 15 min while the app is installed (WorkManager survives process death and OEM kills
 * of the app process) and on demand from the significant-motion hardware trigger
 * ([SignificantMotionMonitor] → [enqueueCheckNow]). When a session is parked and detection is
 * idle it samples ONE active balanced-priority fix and lets the pure evaluator decide
 * ([EvaluateSafetyNetCheckUseCase]):
 *
 *  - **The fix itself is half the job.** Play Services' geofencing engine only updates its
 *    INSIDE/OUTSIDE state when a fix reaches the fused provider; with the phone still and no app
 *    requesting location it starves for tens of minutes. Every tick feeds it.
 *  - **Inside the fence → cure.** Re-register the geofence (idempotent, `FLAG_UPDATE_CURRENT`,
 *    no initial trigger) so a state poisoned OUTSIDE by a false walking-EXIT is rebuilt INSIDE —
 *    without this, the next real drive-away produces no EXIT transition at all (field incident
 *    2026-07-04, Calle Gavia).
 *  - **Far + vehicle evidence → dispatch** the normal [DepartureDetectionWorker] pipeline (its
 *    verdict/retry/publish/clear machinery is the single brain for departures).
 *  - **Far, no evidence → prompt.** Distance alone NEVER releases (walked / bus / friend's car —
 *    BUG-WALK-DEPART-001); the "still parked?" notification lets the user disambiguate, throttled
 *    so it cannot nag.
 *
 * Each tick also mirrors the parked-idle state onto the significant-motion trigger, which is the
 * immediacy layer of the same net (the sensor listener dies with the process; this worker is what
 * resurrects it). Replaces the old `DetectionHeartbeatWorker` watchdog, whose passive-fix
 * isolation predates DET-SOLID-001 — false EXITs are now dismissed cleanly by the worker chain,
 * so provoking the geofence engine with an active fix is exactly what we want.
 */
class ParkingSafetyNetWorker(
    private val appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {

    private val userParkingRepository: UserParkingRepository by inject()
    private val foregroundNotificationProvider: ForegroundNotificationProvider by inject()
    private val getOneLocation: GetOneLocationUseCase by inject()
    private val notificationPort: AppNotificationManager by inject()
    private val detectionRuntime: DetectionRuntimeState by inject()
    private val departureEventBus: DepartureEventBus by inject()
    private val evaluateSafetyNetCheck: EvaluateSafetyNetCheckUseCase by inject()
    private val geofenceManager: GeofenceManager by inject()
    private val significantMotionMonitor: SignificantMotionMonitor by inject()
    private val detectionEventLogger: DetectionEventLogger by inject()
    private val stepCounterSource: StepCounterSource by inject()

    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(
            AppNotificationManager.DETECTION_NOTIFICATION_ID,
            foregroundNotificationProvider.buildDetectionNotification(),
        )

    override suspend fun doWork(): Result {
        // What woke this check up — rides into the debug notification and the DEPARTURE_VERDICT
        // telemetry so a field trace shows WHICH layer (periodic / sensor / teardown) saw what.
        val source = inputData.getString(KEY_SOURCE) ?: SOURCE_PERIODIC

        val sessions = runCatching { userParkingRepository.observeActiveSessions().firstOrNull().orEmpty() }
            .getOrElse {
                PaparcarLogger.e(DIAG, "✗ failed to read active sessions", it)
                return Result.success()
            }

        // [OEM-KILL-001] Heartbeat: measure the gap since the previous safety-net run BEFORE
        // stamping the new one. An hours-long gap with a session active means the OEM froze
        // background execution for that whole window — surface it (telemetry + contextual fix ask).
        detectBackgroundKill(sessions)
        detectConfirmedForceStop(sessions)

        // [DET-SIGMOTION-001] Mirror the parked-idle state onto the hardware trigger on EVERY
        // tick — this is also what re-arms it after a process kill or after it fired one-shot.
        val parkedAndIdle = sessions.isNotEmpty() && !detectionRuntime.isRunning.value
        runCatching { significantMotionMonitor.sync(shouldBeArmed = parkedAndIdle) }

        if (sessions.isEmpty()) {
            dismissPrompt()
            // No debug notif on the eternal periodic no-op — it would live in the shade forever.
            if (source != SOURCE_PERIODIC) debugNotify("SafetyNet[$source]: sin sesión activa — nada que vigilar")
            return Result.success()
        }
        // Mid-trip: a live coordinator session owns the situation; a repark also self-heals the
        // old session (replaceActiveSession per vehicle). Don't second-guess it.
        if (detectionRuntime.isRunning.value) {
            PaparcarLogger.d(DIAG, "■ detection running — skipping check")
            debugNotify("SafetyNet[$source]: detección en curso — skip")
            return Result.success()
        }
        if (!hasLocationPermission()) {
            PaparcarLogger.w(DIAG, "■ no location permission — skipping check")
            debugNotify("SafetyNet[$source]: sin permiso de ubicación — skip")
            return Result.success()
        }

        // ACTIVE fix on purpose (not passive last-known): feeding the fused provider is what keeps
        // the geofencing engine's state machine alive while the phone sits in Doze.
        val fix = runCatching { getOneLocation() }.getOrNull()
        if (fix == null) {
            PaparcarLogger.d(DIAG, "■ no fix within timeout — nothing to evaluate this tick")
            debugNotify("SafetyNet[$source]: sin fix en 15 s — nada que evaluar")
            return Result.success()
        }

        val now = System.currentTimeMillis()
        var anyPromptActive = false
        val debugLines = mutableListOf<String>()

        // [DET-RECONCILE-001] Cumulative hardware step counter: read once per tick; the delta
        // against the value stored with the anchor is the step budget that separates "walked
        // away" from "was driven away" even when the whole trip happened while we slept.
        val cumulativeSteps = runCatching { stepCounterSource.currentCumulativeSteps() }.getOrNull()

        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Drop anchors of geofences that no longer have an active session (departed / reverted).
        pruneStaleAnchors(prefs, sessions.mapNotNullTo(mutableSetOf()) { it.geofenceId })

        for (session in sessions) {
            val anchorSteps = session.geofenceId?.let { readAnchorSteps(prefs, it) }
            // Negative delta = reboot reset the counter → budget unknown, never a verdict.
            val stepsSinceAnchor = if (cumulativeSteps != null && anchorSteps != null && cumulativeSteps >= anchorSteps) {
                cumulativeSteps - anchorSteps
            } else {
                null
            }
            val action = evaluateSafetyNetCheck(
                session = session,
                fix = fix,
                lastSeenNearCarAtMs = session.geofenceId?.let { readAnchor(prefs, it) },
                nowMs = now,
                stepsSinceAnchor = stepsSinceAnchor,
            )
            val distanceM = haversineMeters(
                fix.latitude, fix.longitude,
                session.location.latitude, session.location.longitude,
            ).toInt()
            val geofTag = session.geofenceId?.take(8) ?: "sin-geof"
            when (action) {
                is SafetyNetAction.CureGeofence -> {
                    // Position anchor: the phone is provably AT the car right now — this is what
                    // authorises a later far+evidence auto-dispatch (movement started at the car).
                    // Persisted to disk (NOT in-memory): an aggressive OEM kills the process between
                    // parking and driving away, so the anchor MUST survive process death or a real
                    // drive-away wakes in a fresh process with no anchor and only prompts — the spot
                    // is then lost while the user drives (field incident 2026-07-05, Oppo: 69 km/h
                    // departure degraded to prompt because the in-memory anchor was empty). [ANCHOR-PERSIST-001]
                    writeAnchor(prefs, action.geofenceId, now)
                    // The counter value AT the anchor moment — the step budget's zero point.
                    if (cumulativeSteps != null) writeAnchorSteps(prefs, action.geofenceId, cumulativeSteps)
                    PaparcarLogger.d(DIAG, "▶ inside fence — re-registering geofence=${action.geofenceId} (cure, steps@anchor=${cumulativeSteps ?: "?"})")
                    val result = geofenceManager.createGeofence(
                        geofenceId = action.geofenceId,
                        latitude = session.location.latitude,
                        longitude = session.location.longitude,
                        radiusMeters = action.radiusMeters,
                    )
                    runCatching {
                        detectionEventLogger.log(
                            DetectionEvent.GeofenceRegistration(
                                sessionId = action.geofenceId,
                                timestampMs = now,
                                success = result.isSuccess,
                                radiusMeters = action.radiusMeters,
                                location = fix,
                            )
                        )
                    }
                    debugLines += "geof=$geofTag d=${distanceM}m DENTRO(r=${action.radiusMeters.toInt()}m) → fence curada${if (result.isFailure) " ✗FALLO" else ""}"
                }

                is SafetyNetAction.DispatchDeparture -> {
                    // [DET-RECONCILE-001] preconfirmed = the trip already ENDED (step budget /
                    // pedestrian physics proved it) — date the exit back to the anchor, the last
                    // moment the phone was provably at the car, so the freshness gate measures
                    // the real age of the freed spot, not the age of this wake-up.
                    val exitAtMs = if (action.preconfirmed) {
                        session.geofenceId?.let { readAnchor(prefs, it) } ?: now
                    } else {
                        now
                    }
                    PaparcarLogger.d(
                        DIAG,
                        "▶ far with vehicle evidence — dispatching departure geofence=${action.geofenceId} " +
                            "(preconfirmed=${action.preconfirmed} steps=${stepsSinceAnchor ?: "?"} d=${distanceM}m)"
                    )
                    WorkManager.getInstance(appContext).enqueueUniqueWork(
                        "${DepartureDetectionWorker.TAG}_${action.geofenceId}",
                        ExistingWorkPolicy.REPLACE,
                        DepartureDetectionWorker.buildRequest(
                            geofenceId = action.geofenceId,
                            exitTimestampMs = exitAtMs,
                            preconfirmed = action.preconfirmed,
                        ),
                    )
                    logVerdict(
                        action.geofenceId,
                        verdict = if (action.preconfirmed) "safety_net_dispatch_stepbudget" else "safety_net_dispatch",
                        source = source,
                        fixSpeedKmh = fix.speed * KMH_PER_MPS,
                        now = now,
                    )
                    debugLines += "geof=$geofTag d=${distanceM}m LEJOS+evidencia → SALIDA despachada" +
                        if (action.preconfirmed) " (step-budget ${stepsSinceAnchor ?: "?"} pasos)" else ""
                }

                is SafetyNetAction.PromptStillParked -> {
                    anyPromptActive = true
                    // Throttle persisted to disk (NOT in-memory): the OEM kills the process, so an
                    // in-memory throttle is empty on every app-start and re-nags the same prompt each
                    // time (field incident 2026-07-05). [ANCHOR-PERSIST-001]
                    val lastPromptAt = prefs.getLong(PROMPT_KEY_PREFIX + action.geofenceId, 0L)
                    val throttled = now - lastPromptAt < PROMPT_THROTTLE_MS
                    if (!throttled) {
                        PaparcarLogger.d(DIAG, "▶ moving far without anchor — still-parked prompt geofence=${action.geofenceId}")
                        prefs.edit().putLong(PROMPT_KEY_PREFIX + action.geofenceId, now).apply()
                        notificationPort.showStillParkedPrompt(
                            geofenceId = action.geofenceId,
                            latitude = session.location.latitude,
                            longitude = session.location.longitude,
                        )
                        logVerdict(action.geofenceId, verdict = "safety_net_prompt", source = source, fixSpeedKmh = fix.speed * KMH_PER_MPS, now = now)
                    }
                    debugLines += "geof=$geofTag d=${distanceM}m LEJOS sin evidencia → prompt${if (throttled) " (throttled)" else ""}"
                }

                SafetyNetAction.None -> {
                    debugLines += "geof=$geofTag d=${distanceM}m → anillo ambiguo, solo fix"
                }
            }
        }

        // Back near the car (or ambiguity resolved) → any lingering prompt is stale.
        if (!anyPromptActive) dismissPrompt()

        // File-visible mirror of the debug notification: the notification shade rotates, the
        // parkdiag capture is what field forensics actually reads.
        PaparcarLogger.d(DIAG, "[$source] ${debugLines.joinToString(" · ")}")
        debugNotify("SafetyNet[$source]: ${debugLines.joinToString(" · ")}")

        return Result.success()
    }

    /** DEBUG-build breadcrumb of what each safety-net wake-up saw and did. [DET-SAFETY-NET-001] */
    private fun debugNotify(message: String) {
        if (BuildConfig.DEBUG) notificationPort.showDebug(message)
    }

    /**
     * [OEM-KILL-001] Compares "now" against the previous heartbeat and logs a
     * [DetectionEvent.BackgroundKillSuspected] when the gap exceeds [KILL_GAP_THRESHOLD_MS] with a
     * session active — SILENT telemetry only, so we can measure per-OEM background freezing.
     *
     * It does NOT notify the user. A heartbeat gap cannot tell a harmful OEM hard-kill from
     * ordinary Doze (a phone idle/charging overnight legitimately defers a 15-min periodic for
     * hours) — and even a real freeze, while the car sits parked, causes NO harm. Warning here
     * cried wolf (field incident 2026-07-05: "your phone is blocking Paparcar" fired at 02:00 after
     * a night at home, no departure missed). Per the "earn the ask" rule, the battery/settings
     * prompt must be surfaced only after a freeze DEMONSTRABLY degrades an outcome — that will be
     * wired to a real harm signal, not to this gap. A reboot (elapsedRealtime went backwards)
     * explains the gap innocently — skip. Always re-stamps the heartbeat. [BATTERY-ASK-001]
     */
    private suspend fun detectBackgroundKill(sessions: List<UserParking>) {
        runCatching {
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val elapsedNow = SystemClock.elapsedRealtime()
            val lastAliveAt = prefs.getLong(KEY_LAST_ALIVE_AT, 0L)
            val lastAliveElapsed = prefs.getLong(KEY_LAST_ALIVE_ELAPSED, 0L)
            val rebootedSince = elapsedNow < lastAliveElapsed
            val gapMs = now - lastAliveAt

            if (lastAliveAt > 0L && !rebootedSince && sessions.isNotEmpty() && gapMs > KILL_GAP_THRESHOLD_MS) {
                PaparcarLogger.w(DIAG, "⚠ background gap ${gapMs / 60_000} min with session active — logging (silent) [OEM-KILL-001]")
                runCatching {
                    detectionEventLogger.log(
                        DetectionEvent.BackgroundKillSuspected(
                            sessionId = sessions.firstNotNullOfOrNull { it.geofenceId } ?: "system",
                            timestampMs = now,
                            gapMs = gapMs,
                        )
                    )
                }
            }

            prefs.edit()
                .putLong(KEY_LAST_ALIVE_AT, now)
                .putLong(KEY_LAST_ALIVE_ELAPSED, elapsedNow)
                .apply()
        }
    }

    /**
     * [OEM-KILL-001] Deterministic complement of the heartbeat heuristic: on Android 16+ the
     * platform records whether the app was force-stopped before the current process start
     * (`ApplicationStartInfo.wasForceStopped()`), which is exactly what an OEM "deep optimization"
     * kill amounts to (they invoke `forceStopPackage`). Unlike the gap heuristic this cannot
     * confuse deep Doze with a kill — and a force-stop is the harmful case: it WIPES registered
     * geofences, alarms and pending intents, so a departure in that window was undetectable.
     * Checked once per process start, logged only while a session is active (when a kill can
     * actually cost a spot). SILENT telemetry, same "earn the ask" rule as the heuristic.
     */
    private suspend fun detectConfirmedForceStop(sessions: List<UserParking>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return
        if (forceStopCheckedThisProcess || sessions.isEmpty()) return
        forceStopCheckedThisProcess = true
        runCatching {
            val activityManager = appContext.getSystemService(ActivityManager::class.java)
            val wasForceStopped = activityManager
                ?.getHistoricalProcessStartReasons(1)
                ?.firstOrNull()
                ?.wasForceStopped() == true
            if (wasForceStopped) {
                PaparcarLogger.w(DIAG, "⚠ platform confirms a FORCE-STOP before this process start [OEM-KILL-001]")
                detectionEventLogger.log(
                    DetectionEvent.ForceStopConfirmed(
                        sessionId = sessions.firstNotNullOfOrNull { it.geofenceId } ?: "system",
                        timestampMs = System.currentTimeMillis(),
                    )
                )
            }
        }
    }

    private suspend fun logVerdict(geofenceId: String, verdict: String, source: String, fixSpeedKmh: Float, now: Long) {
        runCatching {
            detectionEventLogger.log(
                DetectionEvent.DepartureVerdict(
                    sessionId = geofenceId,
                    timestampMs = now,
                    verdict = verdict,
                    source = "safety-net:$source",
                    speedKmh = fixSpeedKmh,
                    enterAgeMs = departureEventBus.lastVehicleEnteredAt?.let { now - it },
                )
            )
        }
    }

    // ── Position anchor persistence [ANCHOR-PERSIST-001] ──────────────────────────
    // Disk-backed so it survives the OEM process kills that are the norm on the very devices
    // the safety net exists for. Keyed by geofenceId under the same prefs file as the heartbeat.

    private fun readAnchor(prefs: android.content.SharedPreferences, geofenceId: String): Long? =
        prefs.getLong(ANCHOR_KEY_PREFIX + geofenceId, 0L).takeIf { it > 0L }

    private fun writeAnchor(prefs: android.content.SharedPreferences, geofenceId: String, atMs: Long) {
        prefs.edit().putLong(ANCHOR_KEY_PREFIX + geofenceId, atMs).apply()
    }

    /** Cumulative step-counter value at the anchor moment — the step budget's zero point.
     *  [DET-RECONCILE-001] */
    private fun readAnchorSteps(prefs: android.content.SharedPreferences, geofenceId: String): Long? =
        prefs.getLong(ANCHOR_STEPS_KEY_PREFIX + geofenceId, -1L).takeIf { it >= 0L }

    private fun writeAnchorSteps(prefs: android.content.SharedPreferences, geofenceId: String, steps: Long) {
        prefs.edit().putLong(ANCHOR_STEPS_KEY_PREFIX + geofenceId, steps).apply()
    }

    /** Removes per-geofence anchor + prompt-throttle keys for geofences with no active session left
     *  (departed / reverted). */
    private fun pruneStaleAnchors(prefs: android.content.SharedPreferences, liveGeofenceIds: Set<String>) {
        val stale = prefs.all.keys.filter { key ->
            when {
                key.startsWith(ANCHOR_STEPS_KEY_PREFIX) -> key.removePrefix(ANCHOR_STEPS_KEY_PREFIX) !in liveGeofenceIds
                key.startsWith(ANCHOR_KEY_PREFIX) -> key.removePrefix(ANCHOR_KEY_PREFIX) !in liveGeofenceIds
                key.startsWith(PROMPT_KEY_PREFIX) -> key.removePrefix(PROMPT_KEY_PREFIX) !in liveGeofenceIds
                else -> false
            }
        }
        if (stale.isNotEmpty()) {
            prefs.edit().apply { stale.forEach { remove(it) } }.apply()
        }
    }

    private fun dismissPrompt() =
        notificationPort.dismiss(AppNotificationManager.STILL_PARKED_NOTIFICATION_ID)

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        /** WorkManager unique-work name — NEVER rename (a rename orphans the installed periodic). */
        const val TAG = "ParkingSafetyNetWorker"
        /** File-log tag: FileAntilog only persists PARKDIAG-prefixed tags, and this worker was
         *  invisible in field captures without it (2026-07-06). */
        private const val DIAG = "PARKDIAG/SafetyNet"
        /** Unique-work name of the pre-[DET-SAFETY-NET-001] watchdog — cancelled on enqueue so the
         *  renamed class never leaves a stale periodic pointing at a missing worker. */
        private const val LEGACY_TAG = "DetectionHeartbeatWorker"
        private const val INTERVAL_MINUTES = 15L
        private const val KMH_PER_MPS = 3.6f

        /** What woke the check up — debug + telemetry breadcrumb (`DEPARTURE_VERDICT.source`). */
        private const val KEY_SOURCE = "source"
        const val SOURCE_PERIODIC = "periodic"
        const val SOURCE_SIG_MOTION = "sig-motion"
        const val SOURCE_APP_START = "app-start"
        const val SOURCE_DETECTION_END = "detection-end"

        /** Min interval between "still parked?" prompts per geofence. Persisted to disk (see the
         *  prompt branch) so an OEM process kill can't reset it and re-nag on every app-start. */
        private const val PROMPT_THROTTLE_MS = 6 * 60 * 60 * 1_000L
        /** Per-geofence prompt-throttle timestamp keys, in the same prefs file as the anchor. */
        private const val PROMPT_KEY_PREFIX = "prompt_"

        // [OEM-KILL-001] Heartbeat persistence (must survive process death — SharedPreferences).
        private const val PREFS_NAME = "parking_safety_net"
        private const val KEY_LAST_ALIVE_AT = "last_alive_at"
        private const val KEY_LAST_ALIVE_ELAPSED = "last_alive_elapsed"
        /** [ANCHOR-PERSIST-001] Per-geofence position anchor keys live in the SAME prefs file. */
        private const val ANCHOR_KEY_PREFIX = "anchor_"
        /** [DET-RECONCILE-001] Cumulative step-counter value stored alongside each anchor.
         *  MUST prune before ANCHOR_KEY_PREFIX checks (it shares the prefix). */
        private const val ANCHOR_STEPS_KEY_PREFIX = "anchor_steps_"

        /** Heartbeat gap above which a background freeze is logged (SILENT telemetry). Deep Doze
         *  legitimately defers 15-min periodics for hours, so this cannot distinguish a harmful
         *  OEM kill from ordinary idle — hence telemetry only, never a user warning. */
        private const val KILL_GAP_THRESHOLD_MS = 3 * 60 * 60 * 1_000L

        /** [OEM-KILL-001] `wasForceStopped()` describes the CURRENT process start — check it once
         *  per process, not on every 15-min tick of a long-lived process. */
        @Volatile
        private var forceStopCheckedThisProcess = false

        fun buildPeriodicRequest(): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<ParkingSafetyNetWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES)
                .addTag(TAG)
                .build()

        fun enqueueKeep(workManager: WorkManager) {
            workManager.cancelUniqueWork(LEGACY_TAG)
            workManager.enqueueUniquePeriodicWork(
                TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                buildPeriodicRequest(),
            )
        }

        /**
         * Immediate one-shot check, distinct from the 15-min periodic: enqueued by the
         * significant-motion trigger [DET-SIGMOTION-001] and by detection teardown (so the sensor
         * is re-armed seconds after a park instead of waiting for the next periodic tick).
         * Expedited where quota allows — a sensor callback cannot legally start an FGS on
         * Android 12+, and expedited work is the sanctioned fast lane.
         */
        fun enqueueCheckNow(workManager: WorkManager, source: String) {
            workManager.enqueueUniqueWork(
                "${TAG}_now",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ParkingSafetyNetWorker>()
                    .setInputData(workDataOf(KEY_SOURCE to source))
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .addTag(TAG)
                    .build(),
            )
        }
    }
}
