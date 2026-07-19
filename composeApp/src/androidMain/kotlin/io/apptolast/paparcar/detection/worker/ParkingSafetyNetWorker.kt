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
import io.apptolast.paparcar.detection.PendingDetectionStore
import io.apptolast.paparcar.domain.detection.DetectionRuntimeState
import io.apptolast.paparcar.domain.diagnostics.DetectionEvent
import io.apptolast.paparcar.domain.diagnostics.DetectionEventLogger
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.bluetooth.BtConnectionStore
import io.apptolast.paparcar.domain.bluetooth.BluetoothScanner
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.notification.AppNotificationManager
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.repository.VehicleRepository
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
import androidx.core.content.edit

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
    private val config: ParkingDetectionConfig by inject()
    private val manualParkingDetection: io.apptolast.paparcar.domain.detection.ManualParkingDetection by inject()
    // [DET-BT-IDENTITY-GATE-001] Per-session BT-identity inputs for the evaluator's release veto.
    private val vehicleRepository: VehicleRepository by inject()
    private val bluetoothScanner: BluetoothScanner by inject()

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

        // [DET-NEVER-SILENT-001] Recover a park lost to process death BEFORE the active-session gate:
        // a session that armed and was killed mid-trip never confirmed, so it has no active session.
        // A pending whose heartbeat went stale is exactly that — nudge (real trips only) and clear.
        checkStalePendingDetections()

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
        // the geofencing engine's state machine alive while the phone sits in Doze. FRESH on
        // purpose too: the cache served "inside the fence" after the car had left and a frozen
        // mid-drive position (field 2026-07-07) — a stale fix here poisons the anchor and blinds
        // the whole check, so no fix beats an old fix. [DET-RECONCILE-001]
        val fix = runCatching { getOneLocation(maxAgeMs = config.freshFixMaxAgeMs) }.getOrNull()
        if (fix == null) {
            PaparcarLogger.d(DIAG, "■ no fresh fix within timeout — nothing to evaluate this tick")
            debugNotify("SafetyNet[$source]: sin fix fresco en 15 s — nada que evaluar")
            return Result.success()
        }

        val now = System.currentTimeMillis()
        var anyPromptActive = false
        val debugLines = mutableListOf<String>()

        // [DET-RECONCILE-001] Cumulative hardware step counter: read once per tick; the delta
        // against the value stored with the anchor is the step budget that separates "walked
        // away" from "was driven away" even when the whole trip happened while we slept.
        val cumulativeSteps = runCatching { stepCounterSource.currentCumulativeSteps() }.getOrNull()

        // [DET-BT-IDENTITY-GATE-001] Fleet + BT-adapter state, read once per tick: the release veto
        // needs, per session, whether its vehicle is BT-paired AND Bluetooth is currently on — the
        // BLUETOOTH strategy owns that car's identity, so a real drive of it connects to its MAC.
        val vehicles = runCatching { vehicleRepository.observeVehicles().firstOrNull().orEmpty() }
            .getOrDefault(emptyList())
        val btEnabled = runCatching { bluetoothScanner.isBluetoothEnabled() }.getOrDefault(false)

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
            // [DET-BT-IDENTITY-GATE-001] Identity inputs for THIS session's vehicle: BT-gated only
            // when it is BT-paired AND the adapter is on; last connection stamped by the BT receiver.
            val vehicleBtGated = btEnabled &&
                session.vehicleId != null &&
                vehicles.firstOrNull { it.id == session.vehicleId }?.bluetoothDeviceId != null
            val lastBtConnectedAtMs = session.vehicleId?.let { BtConnectionStore.lastConnectedAt(appContext, it) }
            val action = evaluateSafetyNetCheck(
                session = session,
                fix = fix,
                lastSeenNearCarAtMs = session.geofenceId?.let { readAnchor(prefs, it) },
                nowMs = now,
                stepsSinceAnchor = stepsSinceAnchor,
                // AR boarding stamp: the brain's ride proof for mute-counter devices.
                // [DET-EXIT-TRUST-001]
                lastVehicleEnteredAtMs = departureEventBus.lastVehicleEnteredAt,
                // The fact "the OS delivered an EXIT for this fence" (recorded by the trust
                // triage when delivery came too far away to act on directly) — half of the
                // exit∧enter conjunction proof. [DET-CONJUNCTION-001]
                exitDeliveredAtMs = session.geofenceId?.let { readExitDeliveredAt(prefs, it) },
                // App-start tick = the user is LOOKING at the app right now — the zero-cost
                // moment for the ask-when-blind prompt. [DET-ANCHOR-FREEZE-001]
                userPresent = source == SOURCE_APP_START,
                // [DET-BT-IDENTITY-GATE-001] BT-owned vehicle with no connection since it parked →
                // the reconstructed release degrades to the "still parked?" ask, not an auto-release.
                vehicleBtGated = vehicleBtGated,
                lastBtConnectedAtMs = lastBtConnectedAtMs,
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
                    // Time and steps are an ATOMIC PAIR: refreshing the time while keeping an old
                    // zero-point makes the delta count steps walked BEFORE the anchor (field
                    // 2026-07-07, Oppo 12:17 — 365 phantom steps vetoed a real short-hop verdict).
                    // When the read fails, CLEAR the zero-point: delta=null falls back to the
                    // pedestrian-physics check, which only needs the time anchor. [DET-RECONCILE-001]
                    if (cumulativeSteps != null) {
                        writeAnchorSteps(prefs, action.geofenceId, cumulativeSteps)
                    } else {
                        removeAnchorSteps(prefs, action.geofenceId)
                    }
                    // [DET-ANCHOR-FREEZE-001 F4] Re-registering RESETS Play Services' internal
                    // INSIDE/OUTSIDE state to "unknown" until its next initial evaluation — a
                    // blind window in which a drive-away produces NO EXIT transition. Curing on
                    // EVERY tick inside meant every parked-at-home day re-opened that window
                    // dozens of times; on 2026-07-11 a cure landed ~40 s before drive-off and the
                    // departure was silent. The GMS registration is therefore throttled: once per
                    // process start (fences are wiped by force-stop/app-update — the case the
                    // cure exists for), then at most every [ParkingDetectionConfig.cureReregisterMinIntervalMs]
                    // plus immediately after a dismissed false EXIT poisons the state OUTSIDE
                    // ([clearCureThrottle]). The ANCHOR write above is NOT throttled — its
                    // freshness is what authorises far+evidence departures.
                    val lastCureAt = prefs.getLong(CURE_KEY_PREFIX + action.geofenceId, 0L)
                    val firstCureThisProcess = curedFencesThisProcess.add(action.geofenceId)
                    val mustReregister = evaluateSafetyNetCheck.shouldReregisterCure(
                        alreadyCuredThisProcess = !firstCureThisProcess,
                        lastCureAtMs = lastCureAt,
                        nowMs = now,
                        // [DET-CURE-FRESH-001] Age of the parked session: a fresh fence (manual pin
                        // seconds ago) must not re-register and open the blind window before drive-off.
                        sessionAgeMs = now - session.location.timestamp,
                    )
                    if (!mustReregister) {
                        debugLines += "geof=$geofTag d=${distanceM}m DENTRO(r=${action.radiusMeters.toInt()}m) → ancla resellada (cura throttled, hace ${(now - lastCureAt) / 60_000}min)"
                    } else {
                        prefs.edit { putLong(CURE_KEY_PREFIX + action.geofenceId, now) }
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
                }

                is SafetyNetAction.DispatchDeparture -> {
                    // [DET-RECONCILE-001] preconfirmed = the trip already ENDED — the evaluator
                    // dates it (anchor seal, or the AR boarding when that was the proof) so the
                    // freshness gate measures the real age of the freed spot, not the age of
                    // this wake-up. [DET-EXIT-TRUST-001]
                    val exitAtMs = action.tripStartedAtMs ?: now
                    PaparcarLogger.d(
                        DIAG,
                        "▶ far with vehicle evidence — dispatching departure geofence=${action.geofenceId} " +
                            "(preconfirmed=${action.preconfirmed} steps=${stepsSinceAnchor ?: "?"} d=${distanceM}m)"
                    )
                    val departureChain = WorkManager.getInstance(appContext).beginUniqueWork(
                        "${DepartureDetectionWorker.TAG}_${action.geofenceId}",
                        ExistingWorkPolicy.REPLACE,
                        DepartureDetectionWorker.buildRequest(
                            geofenceId = action.geofenceId,
                            exitTimestampMs = exitAtMs,
                            preconfirmed = action.preconfirmed,
                        ),
                    )
                    // [DET-RECONCILE-001] Backfill the NEW parking only when its position is
                    // BOUNDED — decided by the PURE evaluator ([SafetyNetAction.DispatchDeparture
                    // .backfillBounded]: trusted step budget within the boarding cap + pin-grade
                    // fix accuracy), so the phantom-pin class of go/no-go is unit-tested, not
                    // worker folklore. Anything weaker must NOT guess a position: arrival
                    // placement is the live coordinator's job. Chained AFTER the departure so the
                    // old session resolves (publish+clear) before the confirm replaces the
                    // vehicle's active session.
                    if (action.preconfirmed && action.backfillBounded) {
                        PaparcarLogger.d(DIAG, "  → chaining parking backfill at wake-up fix (steps=${action.trustedStepsSinceAnchor} acc=${fix.accuracy})")
                        departureChain.then(
                            ParkingBackfillWorker.buildRequest(
                                fix = fix,
                                vehicleId = session.vehicleId,
                                reliability = config.reliabilityUnattendedSave,
                            )
                        ).enqueue()
                    } else {
                        departureChain.enqueue()
                    }
                    // [DET-ARRIVAL-HANDOFF-001] A dispatched departure must end in exactly one of:
                    // a backfilled session (position bounded, trip provably over) or LIVE
                    // detection following the rest of the trip so the NEW parking is captured at
                    // full quality. NEVER neither — that orphans the arrival: the evaluator
                    // detected the Oppo's return trip mid-drive (2026-07-08 20:41), cleared the
                    // session, and nobody was listening when the user parked 5 min later.
                    // Background FGS-start may be denied (Android 12+/OEM); then the still-parked
                    // prompt asks the user to place it — a notification beats silence.
                    val backfillChained = action.preconfirmed && action.backfillBounded
                    if (!backfillChained) {
                        runCatching { manualParkingDetection.start() }
                            .onSuccess { PaparcarLogger.d(DIAG, "  → departure dispatched without backfill — tracking service started for the arrival") }
                            .onFailure { e ->
                                PaparcarLogger.w(DIAG, "  ⊘ tracking service start denied (${e.message}) — asking the user via still-parked prompt")
                                // This prompt must survive the end-of-run cleanup: without the
                                // flag, `if (!anyPromptActive) dismissPrompt()` below erased it
                                // milliseconds after showing (field 2026-07-09 13:55, Redmi:
                                // the user saw NO notification for the whole ride home).
                                // [DET-RIDE-PROOF-001]
                                anyPromptActive = true
                                runCatching {
                                    notificationPort.showStillParkedPrompt(
                                        geofenceId = action.geofenceId,
                                        latitude = session.location.latitude,
                                        longitude = session.location.longitude,
                                    )
                                }
                            }
                    }
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
                        prefs.edit { putLong(PROMPT_KEY_PREFIX + action.geofenceId, now) }
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
     * [DET-NEVER-SILENT-001] A pending detection whose heartbeat went stale means the OS killed the
     * process before the session could confirm or abort — a park silently lost. Nudge "where did you
     * park?" once (only for real trips: GEOFENCE_EXIT / MANUAL always, AR_VEHICLE_ENTER if it drove)
     * and clear every stale pending so it fires at most once. Clearing on-nudge is the throttle.
     */
    private fun checkStalePendingDetections() {
        val now = System.currentTimeMillis()
        val stale = PendingDetectionStore.scanStale(appContext, now, config.pendingDetectionDeadMs)
        if (stale.isEmpty()) return
        val shouldNudge = stale.any {
            io.apptolast.paparcar.domain.detection.shouldNudgeForStalePending(it.trigger, it.sawDriving)
        }
        if (shouldNudge) {
            PaparcarLogger.d(DIAG, "▶ [never-silent] ${stale.size} stale pending(s), heartbeat dead → mark-parking nudge")
            notificationPort.showMarkParkingNudge()
        } else {
            PaparcarLogger.d(DIAG, "  [never-silent] ${stale.size} stale pending(s), no drive evidence → clearing silently")
        }
        stale.forEach { PendingDetectionStore.clear(appContext, it.armId) }
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

            prefs.edit {
                putLong(KEY_LAST_ALIVE_AT, now)
                    .putLong(KEY_LAST_ALIVE_ELAPSED, elapsedNow)
            }
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
        prefs.edit { putLong(ANCHOR_KEY_PREFIX + geofenceId, atMs) }
    }

    /** Cumulative step-counter value at the anchor moment — the step budget's zero point.
     *  [DET-RECONCILE-001] */
    private fun readAnchorSteps(prefs: android.content.SharedPreferences, geofenceId: String): Long? =
        prefs.getLong(ANCHOR_STEPS_KEY_PREFIX + geofenceId, -1L).takeIf { it >= 0L }

    private fun writeAnchorSteps(prefs: android.content.SharedPreferences, geofenceId: String, steps: Long) {
        prefs.edit { putLong(ANCHOR_STEPS_KEY_PREFIX + geofenceId, steps) }
    }

    /** Keeps the anchor pair coherent when a cure could not read the counter: a stale zero-point
     *  under a fresh time counts pre-anchor steps into the budget. [DET-RECONCILE-001] */
    private fun removeAnchorSteps(prefs: android.content.SharedPreferences, geofenceId: String) {
        prefs.edit { remove(ANCHOR_STEPS_KEY_PREFIX + geofenceId) }
    }

    /** When the OS delivered an EXIT for this fence too far away to act on directly
     *  (trust triage recorded the FACT instead) — half of the conjunction proof.
     *  [DET-CONJUNCTION-001] */
    private fun readExitDeliveredAt(prefs: android.content.SharedPreferences, geofenceId: String): Long? =
        prefs.getLong(EXIT_KEY_PREFIX + geofenceId, 0L).takeIf { it > 0L }

    /** Removes per-geofence anchor + prompt-throttle + exit-evidence keys for geofences with no
     *  active session left (departed / reverted). */
    private fun pruneStaleAnchors(prefs: android.content.SharedPreferences, liveGeofenceIds: Set<String>) {
        val stale = prefs.all.keys.filter { key ->
            when {
                key.startsWith(ANCHOR_STEPS_KEY_PREFIX) -> key.removePrefix(ANCHOR_STEPS_KEY_PREFIX) !in liveGeofenceIds
                key.startsWith(ANCHOR_KEY_PREFIX) -> key.removePrefix(ANCHOR_KEY_PREFIX) !in liveGeofenceIds
                key.startsWith(PROMPT_KEY_PREFIX) -> key.removePrefix(PROMPT_KEY_PREFIX) !in liveGeofenceIds
                key.startsWith(EXIT_KEY_PREFIX) -> key.removePrefix(EXIT_KEY_PREFIX) !in liveGeofenceIds
                key.startsWith(CURE_KEY_PREFIX) -> key.removePrefix(CURE_KEY_PREFIX) !in liveGeofenceIds
                else -> false
            }
        }
        if (stale.isNotEmpty()) {
            prefs.edit { stale.forEach { remove(it) } }
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
        /** IN_VEHICLE ENTER accelerator — AR rides a PendingIntent and wakes a dead process,
         *  landing the check MID-DRIVE while the ColorOS geofence EXIT is still minutes away.
         *  [DET-RECONCILE-001] */
        const val SOURCE_AR_ENTER = "ar-enter"
        /** IN_VEHICLE EXIT accelerator — "the user just left a vehicle": the trip is over and a
         *  missed departure is at its most decidable. Receivers keep firing through the OEM
         *  freezes that starve WorkManager (field 2026-07-08, cinema arrivals on both devices).
         *  [DET-CONJUNCTION-001] */
        const val SOURCE_AR_EXIT = "ar-exit"
        /** Twin ENTER fence — the user walked back to the parked car; the check re-seals the
         *  anchor and cures the EXIT fence state for the upcoming drive-away. [DET-RETURN-ANCHOR-001] */
        const val SOURCE_GEOFENCE_ENTER = "geofence-enter"
        /** A geofence EXIT delivered FAR from its own fence (OEM batching held it past the whole
         *  trip). Its trust premise — "fired at the boundary of YOUR fence" — is void, so it gets
         *  no direct departure authority: it is just a wake-up for the evaluator, which demands
         *  the anchor + a ride proof like every other reconcile source. [DET-EXIT-TRUST-001] */
        const val SOURCE_GEOFENCE_EXIT_STALE = "exit-stale"
        /** BT connected to the vehicle's paired MAC — deterministic "back at my car", fires with
         *  the engine and needs no Doze luck. Same re-seal job as the ENTER fence. [DET-RETURN-ANCHOR-001] */
        const val SOURCE_BT_CONNECT = "bt-connect"
        /** A BT-path park just confirmed — seal the fresh session's anchor immediately (the
         *  coordinator path gets this from its detection-end check). [DET-RETURN-ANCHOR-001] */
        const val SOURCE_BT_PARK = "bt-park"

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
        /** [DET-CONJUNCTION-001] Delivery timestamp of a far-delivered geofence EXIT, keyed by
         *  geofenceId. Disk-backed like the anchor: the conjunction may only be decidable ticks
         *  (or a process death) later. */
        private const val EXIT_KEY_PREFIX = "exit_delivered_"
        /** [DET-ANCHOR-FREEZE-001 F4] Last GMS re-registration per fence — the cure throttle's
         *  disk half (the in-process half is [curedFencesThisProcess]). */
        private const val CURE_KEY_PREFIX = "cure_registered_"
        /** Fences already re-registered by THIS process — a process start means force-stop/app
         *  update may have wiped GMS registrations, so the first cure after it always runs. */
        private val curedFencesThisProcess: MutableSet<String> =
            java.util.concurrent.ConcurrentHashMap.newKeySet()

        /**
         * Voids the cure throttle for [geofenceId] so the NEXT tick inside re-registers
         * immediately. Called when a delivered EXIT is dismissed as false (walking/GPS drift):
         * that delivery left Play Services' state OUTSIDE — poisoned — and rebuilding it is the
         * cure's founding purpose (field 2026-07-04, Calle Gavia). [DET-ANCHOR-FREEZE-001 F4]
         */
        fun clearCureThrottle(context: Context, geofenceId: String) {
            curedFencesThisProcess.remove(geofenceId)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(CURE_KEY_PREFIX + geofenceId)
                .apply()
        }

        /**
         * Records the FACT that the OS delivered a geofence EXIT for [geofenceId] — called by the
         * trust triage when delivery lands too far from the fence to grant departure authority
         * ([DET-EXIT-TRUST-001]). The evaluator pairs it with an independent AR boarding: the two
         * agreeing within [ParkingDetectionConfig.exitEnterPairWindowMs] prove the drive-away that
         * neither could prove alone (field 2026-07-08, cinema trips on BOTH devices).
         * [DET-CONJUNCTION-001]
         */
        fun recordStaleExitDelivery(context: Context, geofenceId: String, deliveredAtMs: Long) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(EXIT_KEY_PREFIX + geofenceId, deliveredAtMs)
                .apply()
        }

        /**
         * Whether ANY session's fence reported a far-delivered EXIT within [maxAgeMs] of [nowMs].
         * Cheap synchronous read for the AR receiver: a fresh IN_VEHICLE event paired with a
         * recently-broken fence is the live half of the conjunction, and the AR broadcast is the
         * only moment the OS exempts a background foreground-service start — the escalation must
         * happen THERE or not at all (a worker's start is denied — field 2026-07-09 13:55).
         * [DET-RIDE-PROOF-001]
         */
        fun hasRecentStaleExit(context: Context, nowMs: Long, maxAgeMs: Long): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).all.any { (key, value) ->
                key.startsWith(EXIT_KEY_PREFIX) && value is Long && (nowMs - value) in 0..maxAgeMs
            }

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
