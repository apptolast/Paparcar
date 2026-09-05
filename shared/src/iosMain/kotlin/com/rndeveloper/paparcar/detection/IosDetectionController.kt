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
import com.rndeveloper.paparcar.detection.sensor.queryPedometerStepsBetween
import com.rndeveloper.paparcar.domain.ActivityRecognitionManager
import com.rndeveloper.paparcar.domain.detection.PendingArm
import com.rndeveloper.paparcar.domain.detection.coordinator.ingestion.DetectionTraceIngestion
import com.rndeveloper.paparcar.domain.detection.coordinator.ingestion.ReplayStepSource
import com.rndeveloper.paparcar.domain.detection.coordinator.ingestion.TraceEvent
import com.rndeveloper.paparcar.domain.detection.coordinator.ingestion.composeWakeTrace
import com.rndeveloper.paparcar.domain.detection.coordinator.ingestion.reconstructedArmEvidence
import com.rndeveloper.paparcar.domain.repository.VehicleRepository
import com.rndeveloper.paparcar.domain.sensor.DetectionStepAnchors
import com.rndeveloper.paparcar.domain.sensor.StepDetectorSource
import com.rndeveloper.paparcar.domain.usecase.detection.EvaluateGeofenceExitUseCase
import com.rndeveloper.paparcar.domain.usecase.detection.GeofenceExitLookup
import com.rndeveloper.paparcar.domain.usecase.location.GetOneLocationUseCase
import com.rndeveloper.paparcar.domain.usecase.location.ObserveAdaptiveLocationUseCase
import com.rndeveloper.paparcar.domain.detection.DepartureAdjudicationVerdict
import com.rndeveloper.paparcar.domain.detection.ExitDeliveryRecords
import com.rndeveloper.paparcar.domain.detection.OpenDepartureAdjudication
import com.rndeveloper.paparcar.domain.detection.adjudicateDeparture
import com.rndeveloper.paparcar.domain.detection.stillParkedPromptsExplainedByDeparture
import com.rndeveloper.paparcar.domain.notification.AppNotificationManager
import com.rndeveloper.paparcar.domain.detection.DepartureProof
import com.rndeveloper.paparcar.domain.usecase.parking.DepartureCheckOutcome
import com.rndeveloper.paparcar.domain.usecase.parking.EvaluateSafetyNetCheckUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.ProcessConfirmedDepartureUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.RunDepartureCheckUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.RunHonestCloseUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.SafetyNetAction
import com.rndeveloper.paparcar.domain.usecase.parking.VerifyDepartureEvidenceUseCase
import io.github.aakira.napier.Napier
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
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
    private val activityRecognitionManager: ActivityRecognitionManager,
    private val departureEventBus: com.rndeveloper.paparcar.domain.service.DepartureEventBus,
    private val evaluateSafetyNetCheck: EvaluateSafetyNetCheckUseCase,
    private val processConfirmedDeparture: ProcessConfirmedDepartureUseCase,
    private val exitDeliveryRecords: ExitDeliveryRecords,
    private val notificationPort: AppNotificationManager,
    private val safetyNetRecords: IosSafetyNetRecords = IosSafetyNetRecords(),
    /** Builds the virtual-clock coordinator a reconstruction replays into (Koin factory,
     *  `RECONSTRUCTION_COORDINATOR`) — the production single ticks the real clock and recorded
     *  history must be judged on its own time. */
    private val reconstructionCoordinator: (clock: () -> Long, steps: StepDetectorSource) -> CoordinatorParkingDetector,
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

        /** Wake-and-query pass over stale pending arms — process death mid-trip, or a wake the
         *  live session never saw. [IOS-F2-A-WAKE-MUST-QUERY-THE-PAST-001] */
        data class ReconstructWake(val source: String) : Command

        /** One safety-net tick: reconcile every parked session against reality. The iOS mesh's
         *  wakes all funnel here. [IOS-F2-A-WAKE-MUST-QUERY-THE-PAST-001] */
        data class SafetyNetCheck(val source: String) : Command

        /** The user's answer to the still-parked prompt. `departed = true` is a WITNESSED
         *  departure: the user attests the FACT, never the hour — nothing gets published. */
        data class StillParkedAnswered(val geofenceId: String, val departed: Boolean) : Command
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
        intake.trySend(Command.ReconstructWake("app-start"))
        intake.trySend(Command.SafetyNetCheck(SOURCE_APP_START))
        // SLC + visit monitoring relaunch the DEAD app on coarse movement — the iOS mesh's
        // between-trips wake primitive (plan §2.1). Each wake reconstructs, then checks.
        wakeMonitors.start { source ->
            intake.trySend(Command.ReconstructWake(source))
            intake.trySend(Command.SafetyNetCheck(source))
        }
    }

    private val wakeMonitors = IosWakeMonitors()

    /** The still-parked prompt's door (notification actions route here). */
    fun answerStillParked(geofenceId: String, departed: Boolean) {
        intake.trySend(Command.StillParkedAnswered(geofenceId, departed))
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
            is Command.ReconstructWake -> reconstructStaleArms(command.source)
            is Command.SafetyNetCheck -> runSafetyNetCheck(command.source)
            is Command.StillParkedAnswered -> handleStillParkedAnswer(command)
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
                // Detection end is a mesh wake, same as Android's check-now at that moment.
                intake.trySend(Command.SafetyNetCheck(SOURCE_DETECTION_END))
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
        val staleDelivery = decision.staleDepartures.any { it.geofenceId == target.geofenceId }
        if (staleDelivery) {
            // Same rule as Android: only FAR-delivered exits get a durable delivery record — it
            // feeds the safety net's EXIT∧ENTER conjunction. (F0's ExitDeliveryRecords gets its
            // first iOS writer here.)
            exitDeliveryRecords.record(target.geofenceId, clock())
        }
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

    private fun launchDepartureLadder(geofenceId: String, exitAtMs: Long, preconfirmed: Boolean = false) {
        departureLadders.remove(geofenceId)?.cancel()
        departureLadders[geofenceId] = scope.launch {
            try {
                runDepartureLadder(geofenceId, exitAtMs, preconfirmed)
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
    private suspend fun runDepartureLadder(geofenceId: String, exitAtMs: Long, preconfirmed: Boolean) {
        var processFailures = 0
        var attempt = 0
        while (true) {
            when (val outcome = runDepartureCheck(geofenceId, exitAtMs, attempt, preconfirmed = preconfirmed)) {
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

    // ── The safety-net mesh [IOS-F2-A-WAKE-MUST-QUERY-THE-PAST-001] ─────────────────────────────

    /** Fences already cured since this process was born — half of `shouldReregisterCure`'s input. */
    private val curedThisProcess = mutableSetOf<String>()

    /**
     * One tick of the Android worker's loop, on the wakes iOS has (app-start, detection-end,
     * SLC/visit relaunches). Every DECISION is the common evaluator's; this only gathers inputs
     * and executes verdicts. iOS-shaped inputs, each an honest degradation, not a guess:
     * `stepsSinceAnchor` is a pedometer range query from the anchor moment (no cumulative
     * counter), `stepsSinceLastWitness` is null (no witness slot → `backfillBounded` can never be
     * true → the arrival is NEVER backfilled silently here, it is asked about), and the BT gate
     * inputs are the platform truth (no BT identity exists, so the BT veto never fires).
     */
    private suspend fun runSafetyNetCheck(source: String) {
        if (detectionRuntime.isRunning.value) return // a live session owns the situation
        val sessions = runCatching { userParkingRepository.observeActiveSessions().first() }
            .getOrDefault(emptyList())
        safetyNetRecords.pruneAllExcept(sessions.mapNotNull { it.geofenceId }.toSet())
        if (sessions.isEmpty()) {
            notificationPort.dismiss(AppNotificationManager.STILL_PARKED_NOTIFICATION_ID)
            return
        }
        val fix = getOneLocation(maxAgeMs = config.freshFixMaxAgeMs) ?: return
        val now = clock()

        val actions = sessions.mapNotNull { session ->
            val fenceId = session.geofenceId ?: return@mapNotNull null
            val anchorAt = safetyNetRecords.lastSeenNearCarAtMs(fenceId)
            evaluateSafetyNetCheck(
                session = session,
                fix = fix,
                lastSeenNearCarAtMs = anchorAt,
                nowMs = now,
                stepsSinceAnchor = anchorAt?.let { queryPedometerStepsBetween(it, now) },
                stepsSinceLastWitness = null,
                lastVehicleEnteredAtMs = departureEventBus.lastVehicleEnteredAt,
                exitDeliveredAtMs = exitDeliveryRecords.deliveredAt(fenceId),
                userPresent = source == SOURCE_APP_START,
                vehicleBtGated = false,
                lastBtConnectedAtMs = null,
            )
        }

        actions.forEach { action ->
            when (action) {
                is SafetyNetAction.CureGeofence -> executeCure(action, now)
                is SafetyNetAction.DispatchDeparture -> executeDispatch(action, now)
                is SafetyNetAction.PromptStillParked -> Unit // held; resolved against the whole tick below
                SafetyNetAction.None -> Unit
            }
        }

        // [DET-EXPLAINED-RIDE-ASKS-NO-OTHER-CAR-001] A dispatched departure explains the ride —
        // the OTHER sessions' prompts stay quiet for this tick.
        val explained = stillParkedPromptsExplainedByDeparture(actions)
        actions.filterIsInstance<SafetyNetAction.PromptStillParked>()
            .filter { it.geofenceId !in explained }
            .forEach { prompt -> executePrompt(prompt, fix, now) }
    }

    private suspend fun executeCure(action: SafetyNetAction.CureGeofence, nowMs: Long) {
        // The anchor is ALWAYS refreshed (the body is provably near the car right now); the fence
        // re-registration is what gets throttled — same split as the Android worker.
        safetyNetRecords.writeAnchor(action.geofenceId, nowMs)
        val session = userParkingRepository.getActiveSessionByGeofence(action.geofenceId) ?: return
        val shouldReregister = evaluateSafetyNetCheck.shouldReregisterCure(
            alreadyCuredThisProcess = action.geofenceId in curedThisProcess,
            lastCureAtMs = safetyNetRecords.lastCureAtMs(action.geofenceId) ?: 0L,
            nowMs = nowMs,
            sessionAgeMs = nowMs - session.location.timestamp,
        )
        if (!shouldReregister) return
        geofenceManager.createGeofence(
            geofenceId = action.geofenceId,
            latitude = session.location.latitude,
            longitude = session.location.longitude,
            radiusMeters = action.radiusMeters,
        ).onSuccess {
            curedThisProcess += action.geofenceId
            safetyNetRecords.stampCure(action.geofenceId, nowMs)
        }
    }

    private fun executeDispatch(action: SafetyNetAction.DispatchDeparture, nowMs: Long) {
        // [DET-TWO-DISPATCHES] One fact, one adjudication: a second observation of the same
        // departure ADHERES unless it upgrades to preconfirmed. The record is written BEFORE the
        // side-effects, same order as the Android worker.
        val verdict = adjudicateDeparture(
            open = safetyNetRecords.openAdjudication(action.geofenceId),
            nowMs = nowMs,
            observationPreconfirmed = action.preconfirmed,
            windowMs = ADJUDICATION_WINDOW_MS,
        )
        if (verdict == DepartureAdjudicationVerdict.Adhere) return
        safetyNetRecords.writeAdjudication(
            action.geofenceId,
            OpenDepartureAdjudication(openedAtMs = nowMs, preconfirmed = action.preconfirmed),
        )
        val exitAtMs = action.tripStartedAtMs ?: nowMs
        launchDepartureLadder(action.geofenceId, exitAtMs, preconfirmed = action.preconfirmed)
        // Hand the REST of the trip to live detection [DET-ARRIVAL-HANDOFF-001] — through the
        // handoff door, never the manual one. With no witness slot on iOS `backfillBounded` is
        // never true, so the arrival is either followed live (this arm) or asked about — a
        // silent backfill pin cannot happen here by construction.
        startTrackingArrivalHandoff()
    }

    private fun executePrompt(action: SafetyNetAction.PromptStillParked, fix: com.rndeveloper.paparcar.domain.model.GpsPoint, nowMs: Long) {
        val lastPromptAt = safetyNetRecords.lastPromptAtMs(action.geofenceId)
        if (lastPromptAt != null && nowMs - lastPromptAt in 0 until PROMPT_THROTTLE_MS) return
        safetyNetRecords.stampPrompt(action.geofenceId, nowMs)
        notificationPort.showStillParkedPrompt(action.geofenceId, fix.latitude, fix.longitude)
    }

    private suspend fun handleStillParkedAnswer(command: Command.StillParkedAnswered) {
        if (!command.departed) return // "still parked" — the dismissal already happened at the handler
        // Mirror of Android's watchdog-departure handler: the user attests the FACT, never the
        // HOUR — `publishSpot = false` because an unknown exit time is never a recent one
        // (freedSpotIsStillThere(exitAtMs = null) is false by definition). Session + fence close.
        processConfirmedDeparture(
            geofenceId = command.geofenceId,
            publishSpot = false,
            proof = DepartureProof.Witnessed,
        ).onFailure { e ->
            Napier.e("still-parked departure processing failed for ${command.geofenceId.take(8)}", e, tag = TAG)
        }
    }

    // ── Wake-and-query reconstruction [IOS-F2-A-WAKE-MUST-QUERY-THE-PAST-001] ───────────────────

    /** The §4 protocol over every STALE pending arm (heartbeat older than
     *  `config.pendingDetectionDeadMs` — the same staleness rule the Android watchdog applies).
     *  A live session owns the present: reconstruction only touches the past nobody followed. */
    private suspend fun reconstructStaleArms(source: String) {
        if (detectionJob?.isActive == true) return
        val now = clock()
        val stale = runCatching { pendingArmRecords.scanStale(now, config.pendingDetectionDeadMs) }
            .getOrDefault(emptyList())
        if (stale.isEmpty()) return
        Napier.i("reconstruct($source): ${stale.size} stale arm(s)", tag = TAG)
        for (arm in stale) {
            try {
                reconstructArm(arm, now)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Napier.e("reconstruction failed for arm ${arm.armId}", e, tag = TAG)
            } finally {
                // Terminal either way: a reconstruction is this arm's LAST word. Leaving the
                // record would replay the same past on every wake.
                withContext(NonCancellable) { pendingArmRecords.clear(arm.armId) }
            }
        }
    }

    /**
     * Steps 2-6 of the plan's §4: query the recorded past, compose the batch, replay it into a
     * VIRTUAL-CLOCK coordinator instance, and let the evaluators decide with the same
     * admissibility rules as a live session. The confirmation pipeline inside the coordinator is
     * the real one — a reconstruction that proves a park saves it, one that cannot stays silent
     * (asymmetric failure: the cancel path plants nothing).
     */
    private suspend fun reconstructArm(arm: PendingArm, nowMs: Long) {
        val transitions = runCatching {
            activityRecognitionManager.queryTransitions(arm.armedAt, nowMs)
        }.getOrDefault(emptyList())
        val wakeFix = getOneLocation(maxAgeMs = null)
        val lastExitMs = transitions.lastOrNull { it.activity == TraceEvent.Activity.VEHICLE_EXIT }?.tMs
        val steps = lastExitMs?.let { queryPedometerStepsBetween(it, nowMs) }
        val trace = composeWakeTrace(
            armedAtMs = arm.armedAt,
            nowMs = nowMs,
            transitions = transitions,
            wakeFix = wakeFix,
            stepsSinceLastVehicleExit = steps,
        )
        if (trace.isEmpty()) {
            Napier.i("reconstruct: nothing reconstructable for arm ${arm.armId} (trigger=${arm.trigger})", tag = TAG)
            return
        }

        val ingestion = DetectionTraceIngestion(trace)
        val stepSource = ReplayStepSource()
        val recon = reconstructionCoordinator({ ingestion.nowMs }, stepSource)
        val locations = MutableSharedFlow<com.rndeveloper.paparcar.domain.model.GpsPoint>(
            extraBufferCapacity = RECONSTRUCTION_FIX_BUFFER,
        )
        val job = scope.launch {
            recon(
                locations,
                armEvidence = reconstructedArmEvidence(arm.trigger),
            )
        }
        ingestion.replay(
            emitFix = { locations.emit(it) },
            emitStep = { stepSource.emit() },
            emitActivity = { activity, trueTimeMs ->
                when (activity) {
                    TraceEvent.Activity.VEHICLE_ENTER -> departureEventBus.onVehicleEntered(trueTimeMs)
                    TraceEvent.Activity.VEHICLE_EXIT -> recon.onVehicleExit(trueTimeMs)
                    TraceEvent.Activity.BICYCLE_ENTER -> recon.onHumanPoweredRide(trueTimeMs)
                }
            },
        )
        // Same closure as the replay suite: a session still undecided when the recorded past runs
        // out gets cancelled — its virtual clock cannot advance further, and an undecided
        // reconstruction must fail toward the false NEGATIVE (no ghost pins from a guess). Any
        // confirm that fired during the replay already ran NonCancellable. What a cancelled
        // reconstruction should surface to the user (prompt? nudge?) is an OPEN design listed in
        // the ticket doc — silence today is the doctrine-safe floor, not the final answer.
        job.cancelAndJoin()
        Napier.i(
            "reconstruct: arm ${arm.armId} (trigger=${arm.trigger}) → ${recon.lastSessionOutcome}",
            tag = TAG,
        )
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

        /** Replay fixes are emitted into an unconsumed-yet flow; the buffer must hold a whole
         *  reconstruction batch (the composer caps steps, fixes are few). */
        const val RECONSTRUCTION_FIX_BUFFER = 256

        // ── Safety-net mesh ──────────────────────────────────────────────────────────────────
        const val SOURCE_APP_START = "app-start"
        const val SOURCE_DETECTION_END = "detection-end"

        /** Same window as the Android worker: bounds "the same fact", outliving the dispatch
         *  chain's retries and staying far below a human's re-park cadence. [DET-TWO-DISPATCHES] */
        const val ADJUDICATION_WINDOW_MS = 5 * 60 * 1_000L

        /** Same throttle as the Android worker: an unanswered question repeated every tick is
         *  nagging, not asking. */
        const val PROMPT_THROTTLE_MS = 6 * 60 * 60 * 1_000L
    }
}
