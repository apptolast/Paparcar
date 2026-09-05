package com.rndeveloper.paparcar.detection

import com.rndeveloper.paparcar.domain.detection.ArmEvidence
import com.rndeveloper.paparcar.domain.detection.CoordinatorParkingDetector
import com.rndeveloper.paparcar.domain.detection.DetectionPhase
import com.rndeveloper.paparcar.domain.detection.DetectionTrigger
import com.rndeveloper.paparcar.domain.detection.MutableDetectionRuntimeState
import com.rndeveloper.paparcar.domain.detection.PendingArmRecords
import com.rndeveloper.paparcar.domain.detection.ArrivalResolutionRecord
import com.rndeveloper.paparcar.domain.detection.ServicePresence
import com.rndeveloper.paparcar.domain.detection.TripContext
import com.rndeveloper.paparcar.domain.detection.coordinatorMayArm
import com.rndeveloper.paparcar.domain.detection.ParkingStrategyResolver
import com.rndeveloper.paparcar.domain.detection.reconcileFences
import com.rndeveloper.paparcar.domain.detection.sentry.isArmSuppressedByUserStop
import com.rndeveloper.paparcar.domain.detection.sentry.shouldSupersedeRunningSession
import com.rndeveloper.paparcar.domain.diagnostics.DetectionEvent
import com.rndeveloper.paparcar.domain.diagnostics.DetectionEventLogger
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import com.rndeveloper.paparcar.domain.repository.UserParkingRepository
import com.rndeveloper.paparcar.domain.service.GeofenceEvent
import com.rndeveloper.paparcar.domain.service.GeofenceEventBus
import com.rndeveloper.paparcar.domain.service.GeofenceManager
import com.rndeveloper.paparcar.domain.repository.VehicleRepository
import com.rndeveloper.paparcar.domain.sensor.DetectionStepAnchors
import com.rndeveloper.paparcar.domain.usecase.detection.EvaluateGeofenceExitUseCase
import com.rndeveloper.paparcar.domain.usecase.detection.GeofenceExitLookup
import com.rndeveloper.paparcar.domain.usecase.location.GetOneLocationUseCase
import com.rndeveloper.paparcar.domain.usecase.location.ObserveAdaptiveLocationUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.DepartureCheckOutcome
import com.rndeveloper.paparcar.domain.usecase.parking.RunDepartureCheckUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.RunHonestCloseUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.VerifyDepartureEvidenceUseCase
import io.github.aakira.napier.Napier
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLRegion

/**
 * The iOS orchestrator — the functional mirror of Android's `CoordinatorDetectionService`, NOT a
 * port of it. [IOS-F1-A-CONTROLLER-FOR-THE-HAPPY-PATH-001]
 *
 * What it mirrors is the INVARIANT, [DET-INTAKE-001]: every trigger enters one unlimited channel
 * and is handled to completion before the next is looked at, so two lanes can never interleave
 * their side-effects. Everything decisional stays in commonMain — the quiet period
 * (`UserStopQuietPeriod`), the strategy gate (`ParkingStrategyResolver`), the exit adjudication
 * (`EvaluateGeofenceExitUseCase`), the supersede (`SessionSupersede`), the pre-arm evidence
 * (`VerifyDepartureEvidenceUseCase`) and the WHOLE confirmation pipeline, which lives inside
 * [CoordinatorParkingDetector] (confirm + new fence + notification + diagnostics). This class only
 * does I/O: it builds the fix Flow, writes runtime/side-record state, and executes fence commands.
 *
 * What it deliberately does NOT have (vs the Android service): a foreground service and its
 * notification (the trip-scoped CLLocation session IS what keeps the app alive — no keep-alive
 * beyond it, per the plan's §3 doctrine), SENTRY residency and its cooldowns (`CLCircularRegion` +
 * the OS relaunching the app replace the resident watcher), WorkManager lanes (the departure
 * dispatch and safety-net mesh are F2), the BT override (structurally impossible on iOS,
 * `DeviceCapabilities(false, false)`), and the passive route tap (CoreLocation has no passive
 * provider).
 *
 * F1 boundary, stated where it bites: a geofence EXIT here arms the next-park tracking but does
 * NOT dispatch the departure (publish the freed spot) — that is F2's inline departure check with
 * its retry ladder. The freed-spot half of the loop is knowingly absent, not forgotten.
 */
class IosDetectionController(
    private val coordinator: CoordinatorParkingDetector,
    private val observeAdaptiveLocation: ObserveAdaptiveLocationUseCase,
    private val getOneLocation: GetOneLocationUseCase,
    private val evaluateGeofenceExit: EvaluateGeofenceExitUseCase,
    private val verifyDepartureEvidence: VerifyDepartureEvidenceUseCase,
    private val strategyResolver: ParkingStrategyResolver,
    private val userParkingRepository: UserParkingRepository,
    private val geofenceManager: GeofenceManager,
    private val geofenceEventBus: GeofenceEventBus,
    private val detectionRuntime: MutableDetectionRuntimeState,
    private val pendingArmRecords: PendingArmRecords,
    private val arrivalResolutionRecord: ArrivalResolutionRecord,
    private val userStopStore: IosUserStopStore,
    private val detectionEventLogger: DetectionEventLogger,
    private val config: ParkingDetectionConfig,
    // ── F2 lanes [IOS-F2-A-WAKE-MUST-QUERY-THE-PAST-001] ────────────────────────────────────────
    private val runDepartureCheck: RunDepartureCheckUseCase,
    private val runHonestClose: RunHonestCloseUseCase,
    private val detectionStepAnchors: DetectionStepAnchors,
    private val vehicleRepository: VehicleRepository,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {

    private sealed interface Command {
        data class StartTracking(
            val trigger: DetectionTrigger,
            val evidence: ArmEvidence,
            val trip: TripContext? = null,
            val staleExitDelivery: Boolean = false,
            val armingGeofenceId: String? = null,
        ) : Command

        /** Internal cancel — the user marked a park manually. No quiet period. [DET-MANUAL-CANCEL-001] */
        data object StopTracking : Command

        /** "Stop detection" on the live session — stamps the outcome AND opens the quiet period.
         *  [DET-STOP-BUTTON-001] */
        data object UserStop : Command

        data class PromptAnswered(val parked: Boolean) : Command

        data class GeofenceDelivery(val event: GeofenceEvent) : Command

        data class Reconcile(val source: String) : Command
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val intake = Channel<Command>(Channel.UNLIMITED)
    private var detectionJob: Job? = null
    private var started = false

    /** One departure ladder per fence, REPLACE semantics — the same dedup WorkManager's
     *  `enqueueUniqueWork(REPLACE)` gave the Android worker. The real cross-wake dedup lives in
     *  the adjudication records, not here. */
    private val departureLadders = mutableMapOf<String, Job>()

    /**
     * Idempotent. Subscribes to the geofence bus BEFORE any region delegate can fire (the bus has
     * no replay — an event with no subscriber is dropped by contract) and runs the start-of-life
     * fence reconcile. Called once from the app entry point, after Koin is up.
     */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            for (command in intake) {
                try {
                    handle(command)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    // A handler that blows up must not kill the loop — same contract as the
                    // Android intake consumer: log, swallow, keep serving.
                    Napier.e("intake handler failed for $command", e, tag = TAG)
                }
            }
        }
        scope.launch {
            geofenceEventBus.events.collect { intake.trySend(Command.GeofenceDelivery(it)) }
        }
        intake.trySend(Command.Reconcile("app-start"))
    }

    // ── The ports' single doors — one per meaning [DET-HANDOFF-NOT-MANUAL-001] ──────────────────

    fun startTrackingManual() {
        intake.trySend(Command.StartTracking(DetectionTrigger.MANUAL, ArmEvidence.Manual))
    }

    fun startTrackingArrivalHandoff() {
        intake.trySend(
            Command.StartTracking(DetectionTrigger.ARRIVAL_HANDOFF, ArmEvidence.ArrivalHandoff),
        )
    }

    fun stopTracking() {
        intake.trySend(Command.StopTracking)
    }

    fun stopByUser() {
        intake.trySend(Command.UserStop)
    }

    fun answerPrompt(parked: Boolean) {
        intake.trySend(Command.PromptAnswered(parked))
    }

    /** The "watcher" on iOS is the OS-held region: a resume request reconciles the inventory and
     *  answers whether the parked session's fence is standing. [DET-WATCH-REACTIVATE-001] */
    suspend fun resumeWatch(): Boolean {
        intake.trySend(Command.Reconcile("resume-watch"))
        val parked = userParkingRepository.observeActiveSessions().first()
        val fenceIds = parked.mapNotNull { it.geofenceId }
        if (fenceIds.isEmpty()) return false
        return monitoredRegionIds().any { it in fenceIds }
    }

    // ── Intake handlers — one command handled to completion at a time ────────────────────────────

    private suspend fun handle(command: Command) {
        when (command) {
            is Command.StartTracking -> handleStart(command)
            Command.StopTracking -> cancelDetectionJob()
            Command.UserStop -> handleUserStop()
            is Command.PromptAnswered ->
                if (command.parked) coordinator.onUserConfirmedParking()
                else coordinator.onUserDeniedParking()
            is Command.GeofenceDelivery -> handleGeofenceDelivery(command.event)
            is Command.Reconcile -> reconcileRegions(command.source)
        }
    }

    private suspend fun handleStart(command: Command.StartTracking) {
        if (detectionJob?.isActive == true) return // idempotent, same as every Android arm lane
        val now = clock()
        if (isArmSuppressedByUserStop(command.trigger, userStopStore.stoppedAtMs(), now, config)) {
            log(DetectionEvent.SessionEnded("arm_$now", now, outcome = "suppressed_user_stop"))
            return
        }
        if (command.trigger == DetectionTrigger.MANUAL) userStopStore.clear()
        // Same gate as Android; on iOS the resolver can only ever answer COORDINATOR (no bonded
        // MACs exist), so the degradation is emergent — same code, no special case. MANUAL exempt.
        if (command.trigger != DetectionTrigger.MANUAL &&
            !coordinatorMayArm(strategyResolver.resolve(), command.trigger)
        ) {
            return
        }

        val armId = now.toString()
        log(
            DetectionEvent.SessionStarted(
                sessionId = "arm_$now",
                timestampMs = now,
                strategy = "ARM:${command.trigger.name}",
                evidence = command.evidence.persistLabel,
            ),
        )
        detectionRuntime.setRunning(true)
        detectionRuntime.setPresence(ServicePresence.Active)
        detectionRuntime.setTrip(command.trip)
        pendingArmRecords.arm(armId, armedAt = now, trigger = command.trigger.name)

        detectionJob = scope.launch {
            val heartbeat = launch {
                launch {
                    detectionRuntime.phase.collect { phase ->
                        if (phase == DetectionPhase.Candidate) {
                            pendingArmRecords.heartbeat(armId, clock(), sawDriving = true)
                        }
                    }
                }
                while (isActive) {
                    delay(config.pendingHeartbeatMs)
                    pendingArmRecords.heartbeat(armId, clock(), sawDriving = false)
                }
            }
            try {
                coordinator(
                    observeAdaptiveLocation(),
                    armEvidence = command.evidence,
                    nominatingVehicleId = command.trip?.departingVehicleId,
                    staleExitDelivery = command.staleExitDelivery,
                    armingGeofenceId = command.armingGeofenceId,
                )
                withContext(NonCancellable) {
                    // Same epilogue order as the Android service: honest close first, then the
                    // arrival stamp. The witness-fix seal has no iOS analogue (no cumulative
                    // counter) — see maybeRunHonestClose. [IOS-F2-A-WAKE-MUST-QUERY-THE-PAST-001]
                    maybeRunHonestClose()
                    maybeStampArrivalResolution()
                }
            } finally {
                heartbeat.cancel()
                withContext(NonCancellable) { pendingArmRecords.clear(armId) }
                detectionRuntime.setRunning(false)
                // No sentry on iOS: the OS-held regions are the between-trips watcher.
                detectionRuntime.setPresence(ServicePresence.Dead)
            }
        }
    }

    private fun handleUserStop() {
        val job = detectionJob
        if (job?.isActive != true) return // stale tap: nothing live, no quiet period opens
        // Order is semantic, inherited from the Android handler: the hook stamps `stopped_by_user`
        // and releases any held confirm BEFORE cancellation reaches the finally.
        coordinator.onUserStoppedDetection()
        userStopStore.stamp(clock())
        job.cancel()
    }

    private fun cancelDetectionJob() {
        detectionJob?.cancel()
    }

    private fun maybeStampArrivalResolution() {
        if (!coordinator.lastOutcome.resolvesTheArrival) return
        val fix = coordinator.lastSessionFix ?: return
        arrivalResolutionRecord.stamp(clock(), fix.latitude, fix.longitude)
    }

    // ── The EXIT lane — on iOS the bus IS the delivery channel [IOS-F0-04] ───────────────────────

    private suspend fun handleGeofenceDelivery(event: GeofenceEvent) {
        when (event) {
            is GeofenceEvent.Error -> {
                Napier.w("region monitoring error: ${event.error}", tag = TAG)
                reconcileRegions("monitoring-error")
            }
            is GeofenceEvent.Exited -> handleGeofenceExit(event)
        }
    }

    private suspend fun handleGeofenceExit(event: GeofenceEvent.Exited) {
        // Same three-cases lookup as Android: a FAILED read is indeterminate and must never be
        // collapsed into "no session" (that once removed a live fence mid-exit).
        val lookup = try {
            when (val session = userParkingRepository.getActiveSessionByGeofence(event.geofenceId)) {
                null -> GeofenceExitLookup.NoSession(event.geofenceId)
                else -> GeofenceExitLookup.Found(event.geofenceId, session)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Napier.e("session lookup failed for ${event.geofenceId}", e, tag = TAG)
            GeofenceExitLookup.LookupFailed(event.geofenceId)
        }
        // CLRegion exits carry no trigger location — the split then classifies as stale, which is
        // the conservative lane by design (unmeasurable falls to stale).
        val decision = evaluateGeofenceExit(
            lookups = listOf(lookup),
            activeVehicleId = null,
            triggerLatitude = null,
            triggerLongitude = null,
        )
        decision.orphanGeofenceIds.forEach { orphanId ->
            geofenceManager.removeGeofence(orphanId)
            log(DetectionEvent.OrphanCleaned(sessionId = orphanId, timestampMs = clock()))
        }
        val target = decision.armTarget ?: return

        // [IOS-F2-A-WAKE-MUST-QUERY-THE-PAST-001] The freed-spot half of the exit, inline: the
        // ladder runs in parallel with the arm below (never inside the intake — a 105 s ladder
        // must not freeze the loop). The trip-scoped GPS session the arm opens is what keeps the
        // app alive through the ladder's window, exactly the plan's §2.5 substitution for the
        // Android worker. Launched for boundary AND stale targets, before the strategy gate —
        // the gate guards the RE-ARM, never the departure (same order as the Android service).
        launchDepartureLadder(target.geofenceId, exitAtMs = event.timestamp)

        if (!coordinatorMayArm(strategyResolver.resolve(), DetectionTrigger.GEOFENCE_EXIT)) return

        // Supersede-or-suppress against a live session; the hand-over question and what travels
        // with it are both common. Order is semantic: ask the session BEFORE cancelling it.
        var inherited: ArmEvidence.InheritedDrive? = null
        if (detectionJob?.isActive == true) {
            val fenceRadius = config.geofenceRadiusFor(
                target.session.sizeCategory,
                target.session.location.accuracy,
            )
            val runningAnchor = detectionRuntime.trip.value?.departurePoint ?: coordinator.lastSessionFix
            if (!shouldSupersedeRunningSession(target.session.location, runningAnchor, fenceRadius)) {
                return // same area: the running session already owns this trip
            }
            inherited = coordinator.notifySuperseded()
            cancelDetectionJob()
        }

        val now = clock()
        val fresh = getOneLocation(maxAgeMs = config.freshFixMaxAgeMs)
        val evidence = inherited ?: verifyDepartureEvidence(
            exitTimestampMs = now,
            currentSpeedKmh = fresh?.speed?.times(MPS_TO_KMH),
            currentAccuracyM = fresh?.accuracy,
            currentFixTimestampMs = fresh?.timestamp,
            sessionStartMs = target.session.location.timestamp,
        )
        val stale = decision.staleDepartures.any { it.geofenceId == target.geofenceId }
        intake.trySend(
            Command.StartTracking(
                trigger = DetectionTrigger.GEOFENCE_EXIT,
                evidence = evidence,
                trip = TripContext(target.session.location, target.session.vehicleId),
                staleExitDelivery = stale,
                armingGeofenceId = target.geofenceId,
            ),
        )
    }

    // ── The departure ladder — the freed spot gets published [IOS-F2-A-WAKE-MUST-QUERY-THE-PAST-001] ──

    private fun launchDepartureLadder(geofenceId: String, exitAtMs: Long) {
        departureLadders.remove(geofenceId)?.cancel()
        departureLadders[geofenceId] = scope.launch {
            try {
                runDepartureLadder(geofenceId, exitAtMs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Napier.e("departure ladder failed for ${geofenceId.take(8)}", e, tag = TAG)
            }
        }
    }

    /** The Android worker's retry ladder, inline: attempts at t=0/+15s/+45s/+105s (~2 min window
     *  for AR delivery), each one a full `RunDepartureCheckUseCase` pass — the decision, the
     *  publish gate and the session close all live in that common use case. Two deliberate
     *  differences vs Android, stated because each is a decision: `ProcessFailedRetry` gets an
     *  EXPLICIT cap (Android leans on WorkManager's implicit max-run-attempts), and a Dismissed
     *  fence is only logged — the fence-poison → cure repair is the safety-net mesh's job (F2.4). */
    private suspend fun runDepartureLadder(geofenceId: String, exitAtMs: Long) {
        var processFailures = 0
        var attempt = 0
        while (true) {
            when (val outcome = runDepartureCheck(geofenceId, exitAtMs, attempt, preconfirmed = false)) {
                DepartureCheckOutcome.Retry -> {
                    delay(LADDER_DELAYS_MS[attempt.coerceAtMost(LADDER_DELAYS_MS.lastIndex)])
                    attempt++
                }
                DepartureCheckOutcome.ProcessFailedRetry -> {
                    if (++processFailures > PROCESS_FAILED_MAX_RETRIES) {
                        Napier.w("departure processing kept failing for ${geofenceId.take(8)} — giving up this wake; the reconcile owns the session", tag = TAG)
                        return
                    }
                    delay(PROCESS_FAILED_RETRY_DELAY_MS)
                }
                DepartureCheckOutcome.Dismissed -> {
                    Napier.i("departure dismissed for ${geofenceId.take(8)} (fence cure is the mesh's job, F2.4)", tag = TAG)
                    return
                }
                is DepartureCheckOutcome.Processed -> {
                    outcome.followTrip?.let { follow ->
                        // [DET-A-JUST-DEPARTED-CAR-IS-NOT-NO-SESSION-001] The departure cleared
                        // the pin — somebody must follow the trip or the detector goes deaf.
                        // Race guard as in the Android handler: something may have armed between
                        // the check and this hand-off.
                        if (detectionJob?.isActive == true) return
                        val closedPin = runCatching {
                            userParkingRepository.getSessionById(follow.geofenceId)
                        }.getOrNull()
                        intake.trySend(
                            Command.StartTracking(
                                trigger = DetectionTrigger.GEOFENCE_EXIT,
                                evidence = ArmEvidence.DepartureFollowed(
                                    speedKmh = follow.speedKmh,
                                    accuracyM = follow.accuracyM,
                                ),
                                trip = closedPin?.let { TripContext(it.location, it.vehicleId) },
                            ),
                        )
                    }
                    return
                }
            }
        }
    }

    /** Mirror of the Android service's `maybeRunHonestClose`, minus the witness slot: iOS has no
     *  cumulative counter to stamp a witness with, so the ladder runs on the pedometer budget
     *  alone (`stepsSinceSeal` is a real CMPedometer range query since this ticket) and the
     *  witness inputs stay null — the evaluator already treats an absent witness as "no
     *  refutation", never as proof. [DET-STEP-BUDGET-ORIGIN-001][DET-TRIP-WITNESS-001] */
    private suspend fun maybeRunHonestClose() {
        if (!coordinator.lastOutcome.triggersHonestClose) return
        val abortFix = coordinator.lastSessionFix ?: return
        val vehicleId = runCatching {
            vehicleRepository.observeActiveVehicle().first()?.id
        }.getOrNull() ?: return
        val stalePin = runCatching {
            userParkingRepository.getActiveSessionByVehicle(vehicleId)
        }.getOrNull()
        val staleGeofence = stalePin?.geofenceId ?: return
        val budget = runCatching { detectionStepAnchors.stepsSinceSeal(staleGeofence) }.getOrNull()
        val sealAgeMs = budget?.sealedAtMs?.let { clock() - it }
        runCatching {
            runHonestClose(
                vehicleId = vehicleId,
                abortFix = abortFix,
                stepsSinceStalePin = budget?.steps,
                stepSealPoint = budget?.sealPoint,
                sealAgeMs = sealAgeMs,
                lastWitnessedFix = null,
                witnessAgeMs = null,
                sessionStepEvents = coordinator.lastSessionStepEvents,
                sessionMaxSpeedMps = coordinator.lastSessionMaxSpeedMps,
            )
        }.onFailure { e -> Napier.w("honest close failed (continuing)", e, tag = TAG) }
    }

    // ── Reconcile — Room truth vs OS-monitored regions ───────────────────────────────────────────

    private suspend fun reconcileRegions(source: String) {
        val sessions = try {
            userParkingRepository.observeActiveSessions().first()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Napier.e("reconcile($source): session read failed", e, tag = TAG)
            return // indeterminate — never tear fences down on a failed read
        }
        val actions = reconcileFences(sessions, monitoredRegionIds())
        if (actions.isInSync) return
        Napier.i(
            "reconcile($source): +${actions.toRegister.size} fences, -${actions.toRemove.size} orphans",
            tag = TAG,
        )
        actions.toRegister.forEach { session ->
            val fenceId = session.geofenceId ?: return@forEach
            geofenceManager.createGeofence(
                geofenceId = fenceId,
                latitude = session.location.latitude,
                longitude = session.location.longitude,
                radiusMeters = config.geofenceRadiusFor(session.sizeCategory, session.location.accuracy),
            )
        }
        actions.toRemove.forEach { geofenceManager.removeGeofence(it) }
    }

    /** Listing regions is an iOS-only affordance (any CLLocationManager sees the app's shared
     *  set), so it lives here instead of widening the common GeofenceManager port. */
    private fun monitoredRegionIds(): Set<String> =
        CLLocationManager().monitoredRegions
            .filterIsInstance<CLRegion>()
            .map { it.identifier }
            .toSet()

    private suspend fun log(event: DetectionEvent) {
        detectionEventLogger.log(event)
    }

    private companion object {
        const val TAG = "IosDetectionController"
        const val MPS_TO_KMH = 3.6f

        /** Inter-attempt delays of the departure ladder: attempts land at t=0/+15s/+45s/+105s —
         *  the same ~2 min AR-delivery window the Android worker's exponential backoff yields. */
        val LADDER_DELAYS_MS = listOf(15_000L, 30_000L, 60_000L)

        /** Android leans on WorkManager's implicit max-run-attempts for a failing PROCESS step;
         *  inline there is no such net, so the cap is explicit. Giving up never loses the
         *  session: the wake-and-query reconcile re-derives an unprocessed departure. */
        const val PROCESS_FAILED_MAX_RETRIES = 3
        const val PROCESS_FAILED_RETRY_DELAY_MS = 30_000L
    }
}
