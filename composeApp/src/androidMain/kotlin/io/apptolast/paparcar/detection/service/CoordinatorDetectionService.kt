package io.apptolast.paparcar.detection.service

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.google.android.gms.location.GeofencingEvent
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.apptolast.paparcar.BuildConfig
import io.apptolast.paparcar.detection.SentryResidenceStore
import io.apptolast.paparcar.detection.SignificantMotionMonitor
import io.apptolast.paparcar.detection.UserStopStore
import io.apptolast.paparcar.detection.worker.DepartureDetectionWorker
import io.apptolast.paparcar.detection.worker.ParkingSafetyNetWorker
import io.apptolast.paparcar.domain.coordinator.CoordinatorParkingDetector
import io.apptolast.paparcar.domain.detection.ArmEvidence
import io.apptolast.paparcar.domain.detection.DetectionTrigger
import io.apptolast.paparcar.domain.detection.coordinatorMayArm
import io.apptolast.paparcar.domain.detection.isArmSuppressedByUserStop
import io.apptolast.paparcar.domain.detection.userStopQuietPeriodRemainingMs
import io.apptolast.paparcar.domain.detection.MutableDetectionRuntimeState
import io.apptolast.paparcar.domain.detection.ParkingStrategy
import io.apptolast.paparcar.domain.detection.ParkingStrategyResolver
import io.apptolast.paparcar.domain.detection.PostDetectionLifecycle
import io.apptolast.paparcar.domain.detection.SentryKillVerdict
import io.apptolast.paparcar.domain.detection.ServicePresence
import io.apptolast.paparcar.domain.detection.nextSentryWakeAbortStreak
import io.apptolast.paparcar.domain.detection.resolvePostDetectionLifecycle
import io.apptolast.paparcar.domain.detection.sentryWakeRearmCooldownMs
import io.apptolast.paparcar.domain.preferences.AppPreferences
import io.apptolast.paparcar.domain.detection.resolveSentryKillVerdict
import io.apptolast.paparcar.domain.diagnostics.DetectionEvent
import io.apptolast.paparcar.domain.detection.TripContext
import io.apptolast.paparcar.domain.diagnostics.DetectionEventLogger
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.displayName
import io.apptolast.paparcar.domain.notification.AppNotificationManager
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.repository.VehicleRepository
import io.apptolast.paparcar.domain.sensor.DetectionStepAnchors
import io.apptolast.paparcar.domain.service.DepartureEventBus
import io.apptolast.paparcar.domain.service.GeofenceEvent
import io.apptolast.paparcar.domain.service.GeofenceEventBus
import io.apptolast.paparcar.domain.service.GeofenceManager
import io.apptolast.paparcar.domain.usecase.detection.ArEnterDecision
import io.apptolast.paparcar.domain.usecase.detection.EvaluateArEnterArmUseCase
import io.apptolast.paparcar.domain.usecase.detection.EvaluateGeofenceExitUseCase
import io.apptolast.paparcar.domain.usecase.detection.GeofenceExitLookup
import io.apptolast.paparcar.domain.usecase.location.GetOneLocationUseCase
import io.apptolast.paparcar.domain.usecase.location.ObserveAdaptiveLocationUseCase
import io.apptolast.paparcar.domain.usecase.parking.ProcessConfirmedDepartureUseCase
import io.apptolast.paparcar.domain.usecase.parking.RevertParkingUseCase
import io.apptolast.paparcar.domain.usecase.parking.RunHonestCloseUseCase
import io.apptolast.paparcar.domain.usecase.parking.VerifyDepartureEvidenceUseCase
import io.apptolast.paparcar.domain.util.PaparcarLogger
import io.apptolast.paparcar.domain.util.haversineMeters
import io.apptolast.paparcar.notification.ForegroundNotificationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

class CoordinatorDetectionService : LifecycleService() {

    private val parkingDetectionCoordinator: CoordinatorParkingDetector by inject()
    private val observeAdaptiveLocation: ObserveAdaptiveLocationUseCase by inject()
    private val foregroundNotificationProvider: ForegroundNotificationProvider by inject()
    private val notificationPort: AppNotificationManager by inject()
    private val vehicleRepository: VehicleRepository by inject()
    private val revertParking: RevertParkingUseCase by inject() // [REFACTOR-300]
    private val processConfirmedDeparture: ProcessConfirmedDepartureUseCase by inject() // [DET-AR-REARM-001]
    private val geofenceEventBus: GeofenceEventBus by inject() // [DET-G-01]
    private val strategyResolver: ParkingStrategyResolver by inject() // [DET-G-01]
    private val detectionRuntime: MutableDetectionRuntimeState by inject() // [DET-READY-001c]
    private val userParkingRepository: UserParkingRepository by inject()
    private val departureEventBus: DepartureEventBus by inject()
    private val detectionEventLogger: DetectionEventLogger by inject()
    private val geofenceService: GeofenceManager by inject() // [orphan-geofence cleanup]
    // [DET-G-05] Pre-arm departure verification: an ACTIVE one-shot fix (speed) + the AR ENTER
    // bus decide whether the exit has vehicle evidence before the coordinator is seeded.
    private val verifyDepartureEvidence: VerifyDepartureEvidenceUseCase by inject()
    private val getOneLocation: GetOneLocationUseCase by inject()
    // Dense tracked route persisted so Home redraws the real trip after background / cold-start,
    // instead of reconstructing it from the parked spot. Fed from the tracking stream below,
    // cleared when the trip terminates. [DET-ROUTE-TRACK-001]
    private val drivingRouteStore: io.apptolast.paparcar.domain.detection.DrivingRouteStore by inject()
    private val locationDataSource: io.apptolast.paparcar.domain.location.LocationDataSource by inject() // [ROUTE-PASSIVE-FILL-001]
    private val detectionConfig: ParkingDetectionConfig by inject()
    // [DET-AR-FIRST-001] Arm ladder for the AR ENTER decision lane.
    private val evaluateArEnterArm: EvaluateArEnterArmUseCase by inject()
    private val evaluateGeofenceExit: EvaluateGeofenceExitUseCase by inject() // [AUDIT-A9-KMP-001]
    // [DET-HONEST-CLOSE-001] Honest close of a silent abort: release the stale pin the car drove
    // away from + leave an approximate zone/pin + nudge, run at the abort from the live FGS.
    private val runHonestClose: RunHonestCloseUseCase by inject()
    private val detectionStepAnchors: DetectionStepAnchors by inject()
    // [DET-RESIDENT-FGS-001] Armed when the service degrades to SENTRY so a departure that Play
    // Services starves still wakes the (now live) process. Same singleton the safety-net worker syncs.
    private val significantMotionMonitor: SignificantMotionMonitor by inject()
    // [DET-RESIDENT-FGS-001 · F3] The Settings auto-detect toggle governs residency — the sentry has
    // no switch of its own (user decision 2026-08-06: one concept, one switch).
    private val appPreferences: AppPreferences by inject()

    // [REFACTOR: extract FGS lifecycle into ForegroundServiceController]
    private val fgs by lazy { ForegroundServiceController(this) }

    // Main-thread-only — lifecycleScope's default dispatcher is Main.immediate. @Volatile is
    // belt-and-braces against potential cross-thread reads from diagnostic code. [audit C-2]
    @Volatile private var detectionJob: Job? = null

    /** [DET-RESIDENT-FGS-001 · F3] Alive only while resident in SENTRY — see [watchSentryPreconditions]. */
    private var sentryWatchJob: Job? = null

    /**
     * [DET-INTAKE-001] The service receives many independent trigger intents (AR ENTER decision
     * lane, GEOFENCE_EXIT, manual, notification actions). Each used to be handled in its own
     * concurrent coroutine, and each decided the service's fate on its own — so one trigger's
     * "nothing to do → stop" beheaded another trigger's in-flight handling (field 2026-07-11
     * 00:38: an AR TickOnly stop destroyed the service 10 ms after the on-time real GEOFENCE_EXIT
     * was delivered; its session lookup got cancelled mid-flight and the EXIT was discarded as
     * orphan). ONE intake, strictly in arrival order: a command is fully handled before the next
     * is looked at, and teardown is decided in exactly one place per command.
     */
    private sealed interface Command {
        data class Deliver(val intent: Intent, val startId: Int) : Command

        /** Sent by the detection job's `finally`. Carries the latest startId KNOWN AT SEND TIME:
         *  if a newer intent lands before this is processed, `stopSelfResult` mismatches and the
         *  stop is vetoed — the newer command's own epilogue then decides. */
        data class DetectionEnded(val startId: Int) : Command
    }

    private val intake = Channel<Command>(Channel.UNLIMITED)

    /** Most recent startId delivered — captured by [Command.DetectionEnded] senders. */
    @Volatile private var lastStartId = 0

    /** [DET-SENTRY-COOLDOWN-001] Trigger of the most recently ARMED session. Consumed (nulled) by
     *  [resolveIdleEpilogue] when it folds the ended session's outcome into the walking-abort
     *  streak — the null-out guarantees one fold per session even though the epilogue has two call
     *  sites. In-memory on purpose: the streak damps a storm that only exists on a live resident
     *  process. */
    private var lastEndedArmTrigger: DetectionTrigger? = null

    /** [DET-SENTRY-COOLDOWN-001] Consecutive sentry-wake sessions refuted as walking aborts —
     *  input to `sentryWakeRearmCooldownMs`, reset by any other ended session. */
    private var sentryWakeAbortStreak = 0

    override fun onCreate() {
        super.onCreate()
        PaparcarLogger.d(DIAG, "▶ Service onCreate")
        // [DET-INTAKE-001] Single consumer — the serialization point for every trigger. A failing
        // handler must not kill the loop (a dead consumer + live service = zombie FGS): log, apply
        // the teardown rule for that command, keep consuming.
        lifecycleScope.launch {
            for (command in intake) {
                when (command) {
                    is Command.Deliver -> try {
                        processIntent(command.intent, command.startId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        PaparcarLogger.e(DIAG, "  ✗ intake command failed (action=${command.intent.action})", e)
                        stopIfIdle("command-error", command.startId)
                    }
                    // Same guard as Deliver: a throw here would kill the consumer loop and leave
                    // a zombie FGS — the teardown must never be the thing that breaks teardown.
                    is Command.DetectionEnded -> try {
                        resolveIdleEpilogue("detection-ended", command.startId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        PaparcarLogger.e(DIAG, "  ✗ detection-ended teardown failed (startId=${command.startId})", e)
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        lastStartId = startId

        PaparcarLogger.d(DIAG, "▶ onStartCommand action=${intent?.action} flags=$flags startId=$startId")

        // [DET-B-02] A null intent is a START_STICKY auto-restart after a process kill. The
        // coordinator's detection state lives only in memory and is gone, so there is no session to
        // resume — promoting the FGS here would glue a detection notification on with no work behind
        // it (the orphan-FGS bug). Stop WITHOUT promoting; a genuine drive re-arms detection via a
        // fresh trigger (AR ENTER today, GEOFENCE_EXIT after DET-G-01). Missing one park is a
        // zero-cost false negative; a hung notification is not.
        if (intent == null) {
            PaparcarLogger.d(DIAG, "  ⊘ null intent (sticky restart) — no recoverable session; stop without promoting FGS [DET-B-02]")
            fgs.stopForegroundAndSelf(startId) // never promoted → internal stopForeground is a no-op
            return START_STICKY
        }

        // Promote to foreground immediately — Android 8+ enforces a 5 s window for any
        // startForegroundService() call, including those from notification action receivers.
        // Use FOREGROUND_SERVICE_TYPE_LOCATION only when we actually hold location permission:
        // on Android 14+ calling startForeground() with type LOCATION without the runtime
        // permission throws SecurityException. [BUG-FGS-001a]
        val hasPerms = hasRequiredPermissions()
        try {
            fgs.promote(
                notificationId = AppNotificationManager.DETECTION_NOTIFICATION_ID,
                notification = foregroundNotificationProvider.buildDetectionNotification(),
                withLocationPermission = hasPerms,
            )
        } catch (e: IllegalStateException) {
            // [DET-G-01] On Android 12+ a non-privileged FGS start throws
            // ForegroundServiceStartNotAllowedException (an IllegalStateException). This is the ONE
            // open unknown for the geofence path: if Play Services does not deliver the geofence
            // PendingIntent with the same FGS privilege it grants AR (BUG-FGS-001), the promote fails
            // here. Degrade gracefully — log + stop, never crash. Validated on real devices: Play
            // Services does grant the privileged start, so the getBroadcast fallback receiver was
            // removed. If a future OEM regresses this, re-add a BroadcastReceiver + getBroadcast path.
            PaparcarLogger.e(DIAG, "  ✗ FGS promote blocked (action=${intent.action}) — start not foreground-eligible", e)
            fgs.stopForegroundAndSelf(startId) // yielding — never behead an older command's in-flight work
            return START_NOT_STICKY
        }
        PaparcarLogger.d(DIAG, "  ✓ startForeground done (locationPermission=$hasPerms)")
        updateCrashlyticsContext(intent.action, hasPerms)

        // [DET-INTAKE-001] Enqueue only — all handling (and the teardown decision) happens in the
        // serialized intake consumer.
        intake.trySend(Command.Deliver(intent, startId))

        return START_STICKY
    }

    /**
     * [DET-INTAKE-001] Handles ONE delivered intent to completion, then applies the single
     * teardown rule. Handlers no longer stop the service themselves: whether the service lives is
     * decided HERE, once per command — and only when (a) no detection job is running and (b) no
     * newer command has been delivered (`stopSelfResult` vetoes stale stops). A negative verdict
     * for one trigger can therefore never kill another trigger's work: a running coordinator
     * blocks the stop via (a); a queued-but-unprocessed intent blocks it via (b).
     */
    private suspend fun processIntent(intent: Intent, startId: Int) {
        when (val action = intent.action) {
            ACTION_START_TRACKING -> handleStartTracking()
            ACTION_SENTRY_WAKE -> handleSentryWake() // [DET-RESIDENT-FGS-001]
            // [DET-WATCH-HONEST-001] App-launch self-heal: an OEM kill drops the resident watcher while
            // a car stays parked; a manual app-open is a legal (foreground) moment to rebuild it. No work
            // of its own — the epilogue below re-enters SENTRY iff a Coordinator car is parked and
            // auto-detect is on, else stops. Makes "Vigilando tu sitio" true again instead of a silent lie.
            ACTION_RESUME_SENTRY -> PaparcarLogger.d(
                DIAG,
                "  → RESUME_SENTRY (${intent.getStringExtra(EXTRA_RESUME_SOURCE) ?: "unknown"}) — " +
                    "epilogue decides sentry vs stop [DET-WATCH-HONEST-001][DET-WATCH-REACTIVATE-001]",
            )
            ACTION_GEOFENCE_EXIT -> handleGeofenceExit(intent)
            ACTION_AR_TRANSITION -> handleArTransition(intent) // [DET-AR-FIRST-001]
            ACTION_PARKING_CONFIRMED -> handleUserConfirmed()
            ACTION_PARKING_DENIED -> handleUserDenied()
            ACTION_PARKING_ACK -> handlePostSaveAck() // [REFACTOR-300]
            ACTION_PARKING_REVERT -> handlePostSaveRevert(intent.getStringExtra(EXTRA_PARKING_ID)) // [REFACTOR-300]
            ACTION_DEPARTURE_CONFIRMED -> handleWatchdogDeparture(intent.getStringExtra(EXTRA_GEOFENCE_ID)) // [DET-AR-REARM-001]
            ACTION_STOP_TRACKING -> {
                PaparcarLogger.d(DIAG, "  → STOP_TRACKING — cancelling detection")
                cancelDetectionJob()
            }
            // [DET-STOP-BUTTON-001] The user pressed "Parar detección" on the live session. Distinct
            // from STOP_TRACKING (an internal cancel): this one stamps the session's own terminal
            // outcome, drops any held confirm so nothing is planted, and opens the quiet period.
            ACTION_USER_STOP -> handleUserStop()
            // [DET-TIERS-001] Bluetooth arbitrated: a paired-car BT edge SUPERSEDES this
            // probabilistic session (disconnect → the BT path confirms deterministically; connect in
            // Candidate → the user is back in the car, veto the pending pin). Either way the
            // coordinator must step aside — abort exactly like STOP so no ladder/prompt/pin survives.
            // The decision was made by the pure EvaluateBtArbitrationUseCase in the BT receiver; the
            // service only executes the abort. BT never enters coordinator scoring — it overrides.
            ACTION_BT_OVERRIDE -> {
                PaparcarLogger.d(DIAG, "  → BT_OVERRIDE (${intent.getStringExtra(EXTRA_BT_OVERRIDE_REASON)}) — Bluetooth supersedes, aborting session")
                cancelDetectionJob()
            }
            // [DET-B-01] Unknown action: we already promoted to satisfy the 5 s window; the
            // epilogue below tears down if idle instead of leaving the FGS notification hanging.
            else -> PaparcarLogger.d(DIAG, "  ⊘ unhandled action=$action [DET-B-01]")
        }
        resolveIdleEpilogue("post-${intent.action?.substringAfterLast('.') ?: "null"}", startId)
    }

    /**
     * [DET-RESIDENT-FGS-001] Direct SENTRY→ACTIVE wake from the significant-motion sensor. The
     * monitor only routes here when the service is resident in [ServicePresence.Sentry], so this is
     * NOT a background FGS start — the process is already foreground, and starting a live service
     * re-delivers legally. This is the immediacy Q2 chose over the WorkManager tick: no Play
     * Services, no 15-min latency.
     *
     * SigMotion cannot tell a walk from a drive, so it arms with [ArmEvidence.Unverified] — every
     * anti-walking guard active, no seed. If it was just a walk past the car the coordinator's
     * false-enter / no-movement aborts end it in ~75 s–4 min; a real drive-away is followed live to
     * the next park. The parked session anchors the trip so Home binds to the right car.
     */
    private suspend fun handleSentryWake() {
        // A tracking job already owns the service (a trigger raced in first) — the wake is redundant.
        if (detectionJob?.isActive == true) {
            PaparcarLogger.d(DIAG, "  ↻ SENTRY_WAKE ignored — detectionJob already active")
            return
        }
        if (!guardPermissions("SENTRY_WAKE")) return
        val sessions = runCatching { userParkingRepository.observeActiveSessions().firstOrNull().orEmpty() }
            .getOrElse { emptyList() }
        val activeVehicleId = runCatching { vehicleRepository.observeActiveVehicle().firstOrNull()?.id }.getOrNull()
        val session = sessions.firstOrNull { it.vehicleId == activeVehicleId } ?: sessions.firstOrNull()
        if (session == null) {
            // Nothing parked to watch — the session was cleared elsewhere. The epilogue tears the
            // (now purposeless) resident service down. [DET-RESIDENT-FGS-001]
            PaparcarLogger.d(DIAG, "  ⊘ SENTRY_WAKE — no parked session; standing down")
            return
        }
        PaparcarLogger.d(DIAG, "  → SENTRY_WAKE — significant motion on live process, arming Coordinator (Unverified) [DET-RESIDENT-FGS-001]")
        cancelDetectionJob()
        startParkingDetection(
            DetectionTrigger.SIGNIFICANT_MOTION,
            detail = "sentry-wake geof=${session.geofenceId?.take(8) ?: "?"}",
            trip = TripContext(session.location, session.vehicleId),
            armEvidence = ArmEvidence.Unverified,
        )
    }

    private suspend fun handleStartTracking() {
        if (!guardPermissions("START_TRACKING")) return
        // [FIX BUG-SERVICE-109: stop relying on stale hasDetectedMovement across sessions]
        // Active-job check alone is the right idempotency guard — a session that has already
        // started owns the work. hasDetectedMovement only makes sense in-session.
        if (detectionJob?.isActive == true) {
            PaparcarLogger.d(DIAG, "  ↻ START_TRACKING ignored — detectionJob already active")
            return
        }
        PaparcarLogger.d(DIAG, "  → START_TRACKING — (re)starting detection")
        cancelDetectionJob()
        startParkingDetection(DetectionTrigger.MANUAL)
    }

    private fun handleUserConfirmed() {
        PaparcarLogger.d(DIAG, "  → PARKING_CONFIRMED delivered to coordinator")
        parkingDetectionCoordinator.onUserConfirmedParking()
        // [FIX BUG-FGS-103] A confirm that arrives with no active job is a stale tap
        // (auto-confirm already wrote the spot) — the intake epilogue tears the FGS down.
    }

    /**
     * [DET-STOP-BUTTON-001] "Parar detección" — from the Home row or the service notification.
     *
     * Three things, in this order: tell the coordinator (which stamps `stopped_by_user` and drops
     * any held confirm BEFORE the cancellation reaches its finally, so the watchdog cannot finalize
     * the pin the user just refused), open the quiet period, cancel the job. The intake epilogue
     * then resolves the ordinary teardown — sentry if a car is still parked, stop otherwise:
     * stopping a session is not turning the feature off.
     *
     * A tap with NO live session is a stale notification tap. It cancels nothing and — deliberately
     * — opens no quiet period: muting the next real departure because of a leftover notification
     * would be a false negative the user never asked for.
     */
    private fun handleUserStop() {
        if (detectionJob?.isActive != true) {
            PaparcarLogger.d(DIAG, "  ⊘ USER_STOP with no live session — stale tap, no quiet period [DET-STOP-BUTTON-001]")
            notificationPort.dismiss(AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID)
            return
        }
        PaparcarLogger.d(DIAG, "  → USER_STOP — the user stopped the live session [DET-STOP-BUTTON-001]")
        parkingDetectionCoordinator.onUserStoppedDetection()
        UserStopStore.stamp(applicationContext, System.currentTimeMillis())
        cancelDetectionJob()
        if (BuildConfig.DEBUG) {
            notificationPort.showDebug(
                "Detección PARADA por ti: cierro el viaje sin guardar plaza y no haré caso a los " +
                    "avisos automáticos durante ${detectionConfig.userStopQuietPeriodMs / 60_000} min",
            )
        }
    }

    private fun handleUserDenied() {
        PaparcarLogger.d(DIAG, "  → PARKING_DENIED delivered to coordinator")
        parkingDetectionCoordinator.onUserDeniedParking()
        // [FIX BUG-FGS-103] Same stale-tap handling as confirm — epilogue tears down when idle.
    }

    /**
     * [REFACTOR-300] "Sí, confirmar" on the post-save notification.
     * The save already happened; nothing to do except dismiss the notif (epilogue tears down).
     */
    private fun handlePostSaveAck() {
        PaparcarLogger.d(DIAG, "  → PARKING_ACK — user acknowledged auto-confirm")
        notificationPort.dismiss(AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID)
    }

    /**
     * [REFACTOR-300] "No, cancelar" on the post-save notification.
     * Runs the [RevertParkingUseCase] for the parkingId carried in the intent extras.
     * The use case dismisses the notification and removes the geofence + clears active session;
     * we tear down the FGS after it returns.
     */
    private suspend fun handlePostSaveRevert(parkingId: String?) {
        if (parkingId.isNullOrBlank()) {
            PaparcarLogger.w(DIAG, "  ✗ PARKING_REVERT received without parkingId — dismissing notif")
            notificationPort.dismiss(AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID)
            return
        }
        PaparcarLogger.d(DIAG, "  → PARKING_REVERT — running RevertParkingUseCase(parkingId=$parkingId)")
        // Whether revert succeeds or fails (best-effort), the intake epilogue tears down so the
        // FGS notif does not stay glued on. The user can retry from the history screen if needed.
        runCatching { revertParking(parkingId) }
            .onFailure { e -> PaparcarLogger.e(DIAG, "    ✗ revert failed", e) }
    }

    /**
     * [DET-AR-REARM-001] Watchdog "I've left" tap: the user confirmed a departure the geofence EXIT
     * missed. Release the spot for the given geofence via [ProcessConfirmedDepartureUseCase] (report
     * freed + clear session + remove geofence + unregister AR arming), dismiss the prompt, tear down.
     */
    private suspend fun handleWatchdogDeparture(geofenceId: String?) {
        if (geofenceId.isNullOrBlank()) {
            PaparcarLogger.w(DIAG, "  ✗ DEPARTURE_CONFIRMED without geofenceId — dismiss")
            notificationPort.dismiss(AppNotificationManager.STILL_PARKED_NOTIFICATION_ID)
            return
        }
        PaparcarLogger.d(DIAG, "  → DEPARTURE_CONFIRMED (watchdog) geofenceId=$geofenceId")
        runCatching { processConfirmedDeparture(geofenceId) }
            .onFailure { e -> PaparcarLogger.e(DIAG, "    ✗ watchdog departure failed", e) }
        notificationPort.dismiss(AppNotificationManager.STILL_PARKED_NOTIFICATION_ID)
    }

    /**
     * [DET-G-01] Geofence-exit delivered directly to the service (privileged FGS start via the
     * `getForegroundService` PendingIntent — same mechanism Play Services uses for AR). Two jobs —
     * both ONLY for exits delivered at the fence boundary; an exit delivered kilometers away lost
     * its trust premise to OEM batching and is routed to the reconcile evaluator instead
     * ([DET-EXIT-TRUST-001], step 3 below):
     *  1. **Dispatch departure** — emit [GeofenceEvent.Exited] + enqueue [DepartureDetectionWorker].
     *  2. **Arm the next parking detection** (strategy-aware) — leaving the user's OWN parked-car
     *     geofence is a far more specific "I'm now driving MY car" signal than AR IN_VEHICLE_ENTER
     *     (which fires on any vehicle: a bus, a friend's car). This is what eliminates that class of
     *     false-positive sessions.
     */
    private suspend fun handleGeofenceExit(intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent)
        if (event == null || event.hasError()) {
            PaparcarLogger.w(DIAG, "  ✗ GEOFENCE_EXIT — null or error event (code=${event?.errorCode})")
            event?.let {
                geofenceEventBus.emit(GeofenceEvent.Error("GeofencingEvent error code: ${it.errorCode}", System.currentTimeMillis()))
            }
            return
        }
        val triggering = event.triggeringGeofences
        if (triggering.isNullOrEmpty()) {
            PaparcarLogger.w(DIAG, "  ✗ GEOFENCE_EXIT — no triggering geofences")
            return
        }
        val triggerLoc = event.triggeringLocation
        val now = System.currentTimeMillis()
        // [AUDIT-A9-KMP-001] The three decisions (orphan-vs-real-vs-skip, active-vehicle
        // attribution, boundary-vs-stale split) live in the pure EvaluateGeofenceExitUseCase
        // (commonMain, tested, iOS-reusable). The service does only the I/O + side effects: resolve
        // each fence against Room here, then execute the returned decision.
        val lookups = triggering.map { geofence ->
            val id = geofence.requestId
            val result = runCatching { userParkingRepository.getActiveSessionByGeofence(id) }
            val failure = result.exceptionOrNull()
            when {
                // A FAILED read is NOT "no session" — indeterminate, never destructively cleaned
                // (field 2026-07-11 00:38: a cancelled lookup classified a LIVE fence as orphan).
                failure is CancellationException -> throw failure
                failure != null -> {
                    PaparcarLogger.w(DIAG, "  ⚠ GEOFENCE_EXIT session lookup FAILED geof=$id — skipping, NOT orphan (${failure.message})")
                    GeofenceExitLookup.LookupFailed(id)
                }
                result.getOrNull() == null -> GeofenceExitLookup.NoSession(id)
                else -> GeofenceExitLookup.Found(id, result.getOrThrow()!!)
            }
        }
        val activeVehicleId = runCatching { vehicleRepository.observeActiveVehicle().firstOrNull()?.id }.getOrNull()
        val decision = evaluateGeofenceExit(
            lookups = lookups,
            activeVehicleId = activeVehicleId,
            triggerLatitude = triggerLoc?.latitude,
            triggerLongitude = triggerLoc?.longitude,
        )

        // Self-clean orphan fences (registered NEVER_EXPIRE by a re-park; fire spurious exits).
        for (id in decision.orphanGeofenceIds) {
            PaparcarLogger.w(DIAG, "  ✗ GEOFENCE_EXIT orphan geof=$id — removing")
            if (BuildConfig.DEBUG) {
                notificationPort.showDebug("Valla huérfana (geof=${id.take(8)}): llegó un aviso de salida de una plaza que ya no existe → borro esa valla y NO arranco detección")
            }
            runCatching { geofenceService.removeGeofence(id) }
            runCatching { detectionEventLogger.log(DetectionEvent.OrphanCleaned(sessionId = id, timestampMs = now)) }
        }

        // Every triggering fence was an orphan or a failed lookup → nothing real happened.
        if (!decision.hasRealExit) {
            return
        }

        val boundaryExits = decision.boundaryDepartures.map { it.geofenceId to it.session }
        val staleExits = decision.staleDepartures.map { it.geofenceId to it.session }

        // 3a. Fast path: dispatch departure (speed-gated release) ONLY for the departing
        //     vehicle(s). Emitting the in-process Exited event only for the departing geofence
        //     keeps any UI observer from clearing the still-parked inactive car.
        for ((id, _) in boundaryExits) {
            geofenceEventBus.emit(GeofenceEvent.Exited(geofenceId = id, timestamp = now))
            WorkManager.getInstance(this@CoordinatorDetectionService).enqueueUniqueWork(
                "${DepartureDetectionWorker.TAG}_$id",
                ExistingWorkPolicy.REPLACE,
                DepartureDetectionWorker.buildRequest(geofenceId = id, exitTimestampMs = now),
            )
        }

        // 3b. Far-delivered path: the EXIT still fires the SAME speed-gated departure worker —
        //     the delivery position only removes the right to an INSTANT release, never the
        //     duty to look. Physics says a real drive-away is delivered far by construction
        //     (the car is moving + OEM lag), while a walking exit is delivered at the
        //     boundary; treating "far" as dead archaeology inverted the selection and went
        //     silent on every real departure of the field-test devices (2026-07-09: Redmi
        //     13:48 ride home d=657 m demoted; Oppo mute since 07-08). The worker samples
        //     LIVE speed: driving → confirmed release; stationary/walking → dismissed. The
        //     delivery is ALSO recorded for the reconcile's conjunction — the backstop for
        //     trips that ended entirely inside the delivery lag. [DET-RIDE-PROOF-001]
        if (staleExits.isNotEmpty()) {
            val detail = staleExits.joinToString(" · ") { (id, session) ->
                val d = triggerLoc?.let {
                    haversineMeters(it.latitude, it.longitude, session.location.latitude, session.location.longitude).toInt()
                }
                "geof=${id.take(8)} d=${d ?: "?"}m"
            }
            PaparcarLogger.w(DIAG, "  ⚑ GEOFENCE_EXIT delivered FAR from fence ($detail) — no instant authority; live re-check + reconcile record [DET-RIDE-PROOF-001]")
            if (BuildConfig.DEBUG) {
                notificationPort.showDebug("Aviso de salida entregado LEJOS de la plaza ($detail): huele a entrega retrasada del sistema → compruebo ahora tu velocidad real; la plaza NO se libera todavía")
            }
            for ((id, _) in staleExits) {
                ParkingSafetyNetWorker.recordStaleExitDelivery(this@CoordinatorDetectionService, id, now)
                // Same machinery as the boundary path (speed-gated, retries, corroborated
                // fall-through) — only the in-process Exited emission is withheld so UI
                // observers don't clear a session the live check may yet dismiss.
                WorkManager.getInstance(this@CoordinatorDetectionService).enqueueUniqueWork(
                    "${DepartureDetectionWorker.TAG}_$id",
                    ExistingWorkPolicy.REPLACE,
                    DepartureDetectionWorker.buildRequest(geofenceId = id, exitTimestampMs = now),
                )
            }
            ParkingSafetyNetWorker.enqueueCheckNow(
                WorkManager.getInstance(this@CoordinatorDetectionService),
                source = ParkingSafetyNetWorker.SOURCE_GEOFENCE_EXIT_STALE,
            )
        }

        // 4. Arm the next-park detection ONCE, anchored to the departing (active-preferred) session.
        when (strategyResolver.resolve()) {
            ParkingStrategy.COORDINATOR -> {
                if (!guardPermissions("GEOFENCE_EXIT")) return
                // Loop guard: if the coordinator is already running, a fresh exit (e.g. one its
                // own active GPS stream provoked) must NOT cancel + restart it — that would reset
                // the no-movement abort timer and, fed by more bad fixes, spin a restart loop.
                // Departure was already dispatched above; just don't re-arm. [DET-AR-REARM-001]
                if (detectionJob?.isActive == true) {
                    // [DET-SUPERSEDE-001] The blind "already running" drop loses a genuinely DIFFERENT
                    // next-park: a spurious fence left ~100 m away blocked WA YUKI (field 2026-07-12).
                    // Supersede when the new geofence's car is beyond its own fence from the running
                    // anchor; otherwise keep suppressing (same place → don't reset the abort timer,
                    // [DET-AR-REARM-001]).
                    val newSession = (boundaryExits + staleExits).firstOrNull()?.second
                    val runningAnchor = detectionRuntime.trip.value?.departurePoint
                    val radius = newSession?.let {
                        detectionConfig.geofenceRadiusFor(it.sizeCategory, it.location.accuracy)
                    }
                    val supersede = newSession != null && radius != null &&
                        io.apptolast.paparcar.domain.detection.shouldSupersedeRunningSession(
                            newSession.location, runningAnchor, radius,
                        )
                    if (!supersede) {
                        PaparcarLogger.d(DIAG, "  ↻ GEOFENCE_EXIT — coordinator already running (same area); not re-arming [DET-AR-REARM-001]")
                        return
                    }
                    val supersedeDist = haversineMeters(
                        newSession!!.location.latitude, newSession.location.longitude,
                        runningAnchor!!.latitude, runningAnchor.longitude,
                    )
                    PaparcarLogger.d(DIAG, "  ⤳ GEOFENCE_EXIT ${supersedeDist.toInt()}m from running anchor → superseding zombie session [DET-SUPERSEDE-001]")
                    runCatching {
                        detectionEventLogger.log(
                            DetectionEvent.SessionSuperseded(
                                sessionId = newSession.geofenceId ?: newSession.id,
                                timestampMs = now,
                                distanceMeters = supersedeDist,
                                ageMs = runningAnchor.timestamp.takeIf { it > 0L }?.let { now - it },
                            )
                        )
                    }
                    // fall through to cancelDetectionJob() + startParkingDetection() below
                }
                // The coordinator arms for far-delivered exits too — the service is ALREADY
                // alive inside the event's FGS-start exemption window, and this is the only
                // moment the OS grants it: a mid-drive exit gets its trip followed live to
                // the next park (the arrival the reconcile could never escalate to — its
                // worker start is denied outside event windows). A zombie delivery costs one
                // no-movement abort (~4 min of GPS). [DET-RIDE-PROOF-001]
                val (id, session) = (boundaryExits + staleExits).first()
                // Distance of the exit fix from the parked car + its accuracy — the on-device
                // diagnostic for "real drive-away" (large d) vs "GPS jitter" (small d / huge acc).
                val dist = triggerLoc?.let {
                    haversineMeters(it.latitude, it.longitude, session.location.latitude, session.location.longitude).toInt()
                }
                val acc = triggerLoc?.accuracy?.toInt()
                // [DET-G-05] Pre-arm verification. The exit only proves the PHONE left the
                // radius — walking away after a real park fires it too, and an unconditionally
                // seeded session re-confirmed a bogus park at the pedestrian's position
                // (BUG-REPARK-WALK-001). Only vehicle evidence (recent AR IN_VEHICLE_ENTER or a
                // fix at driving speed) may arm the coordinator as a confirmed departure;
                // unverified exits arm with the legacy anti-walking guards active, and the
                // departure worker upgrades the live session if its verdict confirms later.
                // Fresh fix only: this speed sample decides verified_speed arm evidence — a
                // cached driving-speed fix would verify a stale exit. [DET-RECONCILE-001]
                val exitFix = runCatching { getOneLocation(maxAgeMs = detectionConfig.freshFixMaxAgeMs) }.getOrNull()
                val speedKmh = exitFix?.speed?.times(KMH_PER_MPS)
                val armEvidence = verifyDepartureEvidence(
                    exitTimestampMs = now,
                    currentSpeedKmh = speedKmh,
                    currentAccuracyM = exitFix?.accuracy,
                    // A boarding that predates this parking is the inbound trip's — it must
                    // not label a walking exit "verified" (field 2026-07-08 18:52: a
                    // re-delivered ENTER seeded the coordinator and a phantom spot).
                    // [DET-SESSION-BIRTH-001]
                    sessionStartMs = session.location.timestamp,
                    // Corroboration inputs: an AR boarding only verifies when the position
                    // has outrun pedestrian reach since it (a phantom ENTER while walking
                    // released a spot — field 2026-07-09 11:53). [DET-RIDE-PROOF-001]
                    distanceFromCarMeters = exitFix?.let {
                        haversineMeters(it.latitude, it.longitude, session.location.latitude, session.location.longitude)
                    },
                    fenceRadiusMeters = detectionConfig.geofenceRadiusFor(
                        session.sizeCategory,
                        session.location.accuracy,
                    ),
                )
                // [DET-SOLID-001] Observability: the pre-arm verdict, traced by geofenceId.
                runCatching {
                    detectionEventLogger.log(
                        DetectionEvent.DepartureVerdict(
                            sessionId = id,
                            timestampMs = now,
                            verdict = armEvidence.persistLabel,
                            source = "pre-arm",
                            speedKmh = speedKmh,
                            enterAgeMs = departureEventBus.lastVehicleEnteredAt?.let { now - it },
                        )
                    )
                }
                val detail = "geof=${id.take(8)} d=${dist ?: "?"}m acc=${acc ?: "?"}m " +
                    "exitLoc=${triggerLoc?.latitude ?: "?"},${triggerLoc?.longitude ?: "?"} " +
                    "dep=${armEvidence.persistLabel}"
                PaparcarLogger.d(DIAG, "  → GEOFENCE_EXIT — arming Coordinator ($detail) [DET-G-01][DET-G-05]")
                cancelDetectionJob()
                // Anchor the trip to the departing vehicle's exact session so Home's origin dot +
                // puck bind to the car that actually left. [DEPART-CONSISTENCY-001]
                startParkingDetection(
                    DetectionTrigger.GEOFENCE_EXIT,
                    detail,
                    trip = TripContext(session.location, session.vehicleId),
                    armEvidence = armEvidence,
                    // [DET-ZOMBIE-PROBE-001] Far-delivered arm → short no-movement probe: a zombie
                    // delivery (phone at home, hours late) aborts in ~75 s instead of 4 min of GPS.
                    staleExitDelivery = staleExits.any { (staleId, _) -> staleId == id },
                )
            }
            ParkingStrategy.BLUETOOTH, ParkingStrategy.NONE -> {
                PaparcarLogger.d(DIAG, "  → GEOFENCE_EXIT — strategy not COORDINATOR; not arming")
            }
        }
    }

    /**
     * [DET-AR-FIRST-001] AR IN_VEHICLE ENTER delivered on the DECISION lane (privileged
     * `getForegroundService` start — the mechanism the geofence lane proves in the field; NOT the
     * BUG-FGS-001 app-side start that crashed from the receiver). AR is the LOW-latency nominator:
     * the field EXITs arrive minutes late on OEMs (951–2 192 m on 2026-07-10), so waiting for them
     * arms detection AFTER the trip is over. The ladder ([EvaluateArEnterArmUseCase]) only arms
     * when the boarding is tied to the user's OWN car — bus/taxi ENTERs never arm (the reason the
     * legacy AR-proximity arm was purged stays honored):
     *  - boarding INSIDE the own fence → arm "waiting for ride proof" (no seed, aborts armed);
     *  - boarding far + this fence's broken-EXIT recorded → arm mid-trip + speed-gated departure;
     *  - anything else → the safety-net evaluator (already ticked by the evidence lane) decides.
     */
    private suspend fun handleArTransition(intent: Intent) {
        if (!com.google.android.gms.location.ActivityTransitionResult.hasResult(intent)) {
            PaparcarLogger.d(DIAG, "  ⊘ AR_TRANSITION without transition result")
            return
        }
        val result = com.google.android.gms.location.ActivityTransitionResult.extractResult(intent)
        val enter = result?.transitionEvents?.lastOrNull {
            it.activityType == com.google.android.gms.location.DetectedActivity.IN_VEHICLE &&
                it.transitionType == com.google.android.gms.location.ActivityTransition.ACTIVITY_TRANSITION_ENTER
        }
        if (enter == null) {
            PaparcarLogger.d(DIAG, "  ⊘ AR_TRANSITION without IN_VEHICLE ENTER event")
            return
        }
        val trueEpochMs = System.currentTimeMillis() -
            (android.os.SystemClock.elapsedRealtimeNanos() - enter.elapsedRealTimeNanos) / NANOS_PER_MS
        // Stamp the bus here too: the decision lane can outrun the evidence receiver, and the
        // pre-arm verifier reads the bus. Idempotent — both lanes stamp the same true time.
        departureEventBus.onVehicleEntered(trueEpochMs)
        if (!guardPermissions("AR_TRANSITION")) return
        val now = System.currentTimeMillis()
        val sessions = runCatching { userParkingRepository.observeActiveSessions().firstOrNull().orEmpty() }
            .getOrElse { emptyList() }
        val activeVehicleId = runCatching { vehicleRepository.observeActiveVehicle().firstOrNull()?.id }.getOrNull()
        val session = sessions.firstOrNull { it.vehicleId == activeVehicleId } ?: sessions.firstOrNull()
        if (detectionJob?.isActive == true) {
            // [DET-SUPERSEDE-001] Same policy as handleGeofenceExit: supersede a running session that
            // is a zombie relative to this ENTER (its car beyond its own fence from the running
            // anchor); otherwise keep suppressing to avoid a same-place restart loop [DET-AR-REARM-001].
            val runningAnchor = detectionRuntime.trip.value?.departurePoint
            val radius = session?.let {
                detectionConfig.geofenceRadiusFor(it.sizeCategory, it.location.accuracy)
            }
            val supersede = session != null && radius != null &&
                io.apptolast.paparcar.domain.detection.shouldSupersedeRunningSession(
                    session.location, runningAnchor, radius,
                )
            if (!supersede) {
                PaparcarLogger.d(DIAG, "  ↻ AR_TRANSITION — coordinator already running (same area); not re-arming [DET-AR-REARM-001]")
                return
            }
            val supersedeDist = haversineMeters(
                session!!.location.latitude, session.location.longitude,
                runningAnchor!!.latitude, runningAnchor.longitude,
            )
            PaparcarLogger.d(DIAG, "  ⤳ AR_TRANSITION ${supersedeDist.toInt()}m from running anchor → superseding zombie session [DET-SUPERSEDE-001]")
            runCatching {
                detectionEventLogger.log(
                    DetectionEvent.SessionSuperseded(
                        sessionId = session.geofenceId ?: session.id,
                        timestampMs = now,
                        distanceMeters = supersedeDist,
                        ageMs = runningAnchor.timestamp.takeIf { it > 0L }?.let { now - it },
                    )
                )
            }
            // fall through to the arm ladder below
        }
        val recentStaleExitRecorded = ParkingSafetyNetWorker.hasRecentStaleExit(
            this@CoordinatorDetectionService,
            nowMs = now,
            maxAgeMs = detectionConfig.exitEnterPairWindowMs,
        )
        // [DET-INTAKE-001] The fresh-fix sample is the expensive step of this handler (up to 15 s
        // of GPS wait). Run the pure ladder WITHOUT it first: only when everything else passes
        // (NoFix) is the sample worth taking. A stale redelivery — GMS re-sends the last ENTER to
        // both lanes on every re-registration — thus costs milliseconds in the serialized intake
        // instead of parking a 15 s GPS wait in front of a real trigger queued behind it.
        var fix: GpsPoint? = null
        var decision = evaluateArEnterArm(
            session = session,
            fix = null,
            enterTrueTimeMs = trueEpochMs,
            nowMs = now,
            recentStaleExitRecorded = recentStaleExitRecorded,
        )
        if (decision is ArEnterDecision.NoFix) {
            fix = runCatching { getOneLocation(maxAgeMs = detectionConfig.freshFixMaxAgeMs) }.getOrNull()
            decision = evaluateArEnterArm(
                session = session,
                fix = fix,
                enterTrueTimeMs = trueEpochMs,
                nowMs = now,
                recentStaleExitRecorded = recentStaleExitRecorded,
            )
        }
        val lagMs = now - trueEpochMs
        when (decision) {
            is ArEnterDecision.ArmAtCar -> {
                val detail = "geof=${decision.geofenceId.take(8)} lag=${lagMs}ms dep=${ArmEvidence.BoardingAtCar.persistLabel}"
                PaparcarLogger.d(DIAG, "  → AR ENTER at own fence — arming Coordinator, waiting for ride proof ($detail) [DET-AR-FIRST-001]")
                cancelDetectionJob()
                startParkingDetection(
                    DetectionTrigger.AR_VEHICLE_ENTER,
                    detail,
                    trip = TripContext(session!!.location, session.vehicleId),
                    armEvidence = ArmEvidence.BoardingAtCar,
                )
            }
            is ArEnterDecision.ArmMidTrip -> {
                // Same machinery as a far-delivered EXIT: speed-gated departure re-check +
                // a coordinator armed with whatever evidence the verifier grants NOW.
                val speedKmh = fix?.speed?.times(KMH_PER_MPS)
                val armEvidence = verifyDepartureEvidence(
                    exitTimestampMs = now,
                    currentSpeedKmh = speedKmh,
                    currentAccuracyM = fix?.accuracy,
                    sessionStartMs = session!!.location.timestamp,
                    distanceFromCarMeters = fix?.let {
                        haversineMeters(it.latitude, it.longitude, session.location.latitude, session.location.longitude)
                    },
                    fenceRadiusMeters = detectionConfig.geofenceRadiusFor(
                        session.sizeCategory,
                        session.location.accuracy,
                    ),
                )
                val detail = "geof=${decision.geofenceId.take(8)} lag=${lagMs}ms dep=${armEvidence.persistLabel} (exit∧enter)"
                PaparcarLogger.d(DIAG, "  → AR ENTER + broken-fence record — arming mid-trip ($detail) [DET-AR-FIRST-001]")
                WorkManager.getInstance(this@CoordinatorDetectionService).enqueueUniqueWork(
                    "${DepartureDetectionWorker.TAG}_${decision.geofenceId}",
                    ExistingWorkPolicy.REPLACE,
                    DepartureDetectionWorker.buildRequest(geofenceId = decision.geofenceId, exitTimestampMs = now),
                )
                cancelDetectionJob()
                startParkingDetection(
                    DetectionTrigger.AR_VEHICLE_ENTER,
                    detail,
                    trip = TripContext(session.location, session.vehicleId),
                    armEvidence = armEvidence,
                )
            }
            ArEnterDecision.NoSession,
            ArEnterDecision.StaleEnter,
            ArEnterDecision.NoFix,
            ArEnterDecision.TickOnly -> {
                // The evidence lane already enqueued the evaluator tick for this same ENTER.
                // NO stop here: the intake epilogue decides, and only when this command is
                // still the newest — the 00:38 field EXIT died to a stop issued right here.
                PaparcarLogger.d(DIAG, "  ⊘ AR ENTER not armable ($decision, lag=${lagMs}ms) — evaluator's call [DET-AR-FIRST-001]")
            }
        }
    }

    /**
     * [DET-HONEST-CLOSE-001] After a SILENT coordinator abort, run the honest-close ladder from the
     * live FGS: if the car provably drove away from its last pin (step budget since the pin's seal
     * ≪ the displacement), release the stale pin + leave an approximate mark + nudge — never
     * deferred to the Doze-held worker. Silent aborts only; confirms and every other outcome are
     * untouched. `stepsSinceSeal` reads the baseline sealed at the previous park's confirm.
     */
    private suspend fun maybeRunHonestClose() {
        val outcome = parkingDetectionCoordinator.lastSessionOutcome
        if (outcome != OUTCOME_ABORTED_FALSE_ENTER && outcome != OUTCOME_ABORTED_NO_MOVEMENT) return
        val abortFix = parkingDetectionCoordinator.lastSessionFix ?: return
        val vehicleId = runCatching { vehicleRepository.observeActiveVehicle().firstOrNull()?.id }
            .getOrNull() ?: return
        val stalePin = runCatching { userParkingRepository.getActiveSessionByVehicle(vehicleId) }.getOrNull()
        // No active pin (or no fence to key the step baseline) → nothing to release, nothing to prove.
        val staleGeofence = stalePin?.geofenceId ?: return
        // [DET-STEP-BUDGET-ORIGIN-001] steps + WHERE their baseline was sealed — the ladder only
        // compares the budget against a displacement measured from that same origin.
        val budget = runCatching { detectionStepAnchors.stepsSinceSeal(staleGeofence) }.getOrNull()
        // [DET-FROZEN-COUNTER-001] The aborting session's own testimony: its step-DETECTOR count
        // witnesses the cumulative counter's liveness (a live cumulative delta can never be below
        // it), and measured speed outranks the step inference outright. Field 2026-07-25 22:29,
        // Redmi: without the witness, a frozen MIUI counter read a 150 m walk to a restaurant as
        // a ride and the ladder planted an approximate pin over the correct 6-minute-old one.
        val sessionStepEvents = parkingDetectionCoordinator.lastSessionStepEvents
        val sessionMaxSpeedMps = parkingDetectionCoordinator.lastSessionMaxSpeedMps
        // [DET-TRIP-WITNESS-001] The budget expires: the ladder refuses a delta whose seal is
        // hours old (or undated — legacy seal), the exact shape of the 30-07 EXIT-echo FP.
        val sealAgeMs = budget?.sealedAtMs?.let { System.currentTimeMillis() - it }
        // [DET-UNWITNESSED-DISPLACEMENT-001] The last position an EARLIER wake vouched for (its
        // end fix, or a safety-net check): the ladder refuses an abort fix the body could not
        // have reached since then. Age measured to now — this abort just happened in this intake.
        // A negative elapsed (clock skew) degrades to "no witness", never to a false refutation.
        val witness = readLastWitnessedFix()
        val witnessAgeMs = witness?.let { (System.currentTimeMillis() - it.second).takeIf { age -> age >= 0 } }
        val result = runCatching {
            runHonestClose(
                vehicleId, abortFix, budget?.steps, budget?.sealPoint,
                sealAgeMs = sealAgeMs,
                lastWitnessedFix = witness?.first,
                witnessAgeMs = witnessAgeMs,
                sessionStepEvents = sessionStepEvents,
                sessionMaxSpeedMps = sessionMaxSpeedMps,
            )
        }
            .onFailure { e -> PaparcarLogger.w(DIAG, "  ⚠ honest-close failed (continuing)", e) }
            .getOrNull() ?: return
        val verdict = result.verdict
        if (result.outcomeLabel != null) {
            PaparcarLogger.d(
                DIAG,
                "  ⑊ honest close: $outcome → ${result.outcomeLabel} (${verdict.reason}; pinDist=${verdict.pinDistanceMeters?.toInt()}m steps=${verdict.stepsDelta}/${verdict.requiredSteps}) [DET-HONEST-CLOSE-001]"
            )
        } else {
            PaparcarLogger.d(
                DIAG,
                "  ⑊ honest close: $outcome stayed silent (${verdict.reason}; pinDist=${verdict.pinDistanceMeters?.toInt()}m steps=${verdict.stepsDelta}/${verdict.requiredSteps} sessionSteps=$sessionStepEvents)"
            )
        }
        // [DET-FROZEN-COUNTER-001] Stamp the full reasoning into the aborted session's remote
        // trace — an approximate pin/zone (or a silence) must never again be unexplainable from
        // Firestore alone. Logged under the finished session's own id.
        val sessionId = parkingDetectionCoordinator.lastSessionId
        if (sessionId != null) {
            runCatching {
                detectionEventLogger.log(
                    DetectionEvent.HonestClose(
                        sessionId = sessionId,
                        timestampMs = System.currentTimeMillis(),
                        verdict = result.outcomeLabel ?: "silent",
                        reason = verdict.reason,
                        distanceMeters = verdict.pinDistanceMeters,
                        walkDistanceMeters = verdict.walkDistanceMeters,
                        stepsDelta = verdict.stepsDelta,
                        requiredSteps = verdict.requiredSteps,
                        sessionStepEvents = sessionStepEvents,
                        sessionMaxSpeedKmh = sessionMaxSpeedMps * 3.6f,
                        radiusMeters = result.zoneRadiusMeters,
                        witnessDistanceMeters = verdict.witnessDistanceMeters,
                        witnessAgeMs = verdict.witnessAgeMs,
                        location = abortFix,
                    ),
                )
            }.onFailure { e -> PaparcarLogger.w(DIAG, "  ⚠ honest-close trace log failed: ${e.message}") }
        }
    }

    /**
     * [DET-UNWITNESSED-DISPLACEMENT-001] The last position an earlier wake vouched for, plus WHEN
     * (epoch ms) — written by [stampLastWitnessedFix] and by the safety-net check. Null when no
     * witness was ever stamped (fresh install) or the slot is unparseable.
     */
    private fun readLastWitnessedFix(): Pair<GpsPoint, Long>? {
        val prefs = getSharedPreferences(ParkingSafetyNetWorker.PREFS_NAME, MODE_PRIVATE)
        val pos = prefs.getString(ParkingSafetyNetWorker.KEY_LAST_WITNESSED_POS, null) ?: return null
        val at = prefs.getLong(ParkingSafetyNetWorker.KEY_LAST_WITNESSED_AT, 0L)
        if (at <= 0L) return null
        val parts = pos.split(",")
        val lat = parts.getOrNull(0)?.toDoubleOrNull() ?: return null
        val lon = parts.getOrNull(1)?.toDoubleOrNull() ?: return null
        val acc = prefs.getFloat(ParkingSafetyNetWorker.KEY_LAST_WITNESSED_ACC, 0f)
        return GpsPoint(lat, lon, accuracy = acc, timestamp = at, speed = 0f) to at
    }

    /**
     * [DET-UNWITNESSED-DISPLACEMENT-001] Every finished session's last fix becomes the NEXT
     * wake's independent witness of where the body was. Stamped in the intake epilogue AFTER
     * [maybeRunHonestClose] read the previous slot — order matters: a session must never witness
     * for its own abort (its fixes and the abort fix are the same cluster). Same disk-backed slot
     * the safety-net check refreshes; an OEM kill between wakes cannot blind the coherence gate.
     */
    private fun stampLastWitnessedFix() {
        val fix = parkingDetectionCoordinator.lastSessionFix ?: return
        runCatching {
            getSharedPreferences(ParkingSafetyNetWorker.PREFS_NAME, MODE_PRIVATE).edit {
                putString(ParkingSafetyNetWorker.KEY_LAST_WITNESSED_POS, "${fix.latitude},${fix.longitude}")
                putFloat(ParkingSafetyNetWorker.KEY_LAST_WITNESSED_ACC, fix.accuracy)
                putLong(ParkingSafetyNetWorker.KEY_LAST_WITNESSED_AT, System.currentTimeMillis())
            }
        }.onFailure { e -> PaparcarLogger.w(DIAG, "  ⚠ witness stamp failed: ${e.message}") }
    }

    /**
     * [DET-BACKFILL-TAINT-001] Persist the coordinator's ARRIVAL RESOLUTION when an unattended
     * abort resolved this arrival as NUDGE-ONLY (GAP-ENTERED anchor: the rest was never witnessed,
     * the forward error is unboundable, no place is honest — only the user can mark it). The
     * resolution used to die with the session state, so one minute later the safety net's
     * backfill chain re-decided the SAME arrival with less information and planted the pin the
     * coordinator had just refused (field 2026-07-30 20:42, Redmi/Jerez). Stamped to the safety
     * net's own prefs — it must survive the process death that separates this abort from the
     * worker's wake; [ParkingBackfillWorker] reads it and defers the placement to the nudge.
     */
    private fun maybeStampArrivalResolution() {
        if (parkingDetectionCoordinator.lastSessionOutcome != OUTCOME_ABORTED_UNATTENDED_GAP_ANCHOR) return
        val fix = parkingDetectionCoordinator.lastSessionFix ?: return
        runCatching {
            getSharedPreferences(ParkingSafetyNetWorker.PREFS_NAME, MODE_PRIVATE).edit {
                putLong(ParkingSafetyNetWorker.KEY_ARRIVAL_RESOLUTION_AT, System.currentTimeMillis())
                putString(
                    ParkingSafetyNetWorker.KEY_ARRIVAL_RESOLUTION_POS,
                    "${fix.latitude},${fix.longitude}",
                )
            }
            PaparcarLogger.d(
                DIAG,
                "  ⑊ arrival resolution stamped: nudge-only (gap anchor) at ${fix.latitude},${fix.longitude} — net backfill will defer [DET-BACKFILL-TAINT-001]"
            )
        }.onFailure { e -> PaparcarLogger.w(DIAG, "  ⚠ arrival-resolution stamp failed: ${e.message}") }
    }

    /** Cancels the in-flight detection job (if any) and nulls the slot. Main-thread only. */
    private fun cancelDetectionJob() {
        detectionJob?.cancel()
        detectionJob = null
    }

    /**
     * [DET-INTAKE-001] Stops the service only when (a) no detection job is running AND (b)
     * [startId] is still the newest start command delivered (`stopSelfResult` — the framework
     * vetoes stale stops, so a queued-but-unprocessed intent keeps the service alive).
     *
     * The "just stop, unconditionally-if-idle" epilogue for ERROR / edge paths (command failure,
     * null-intent sticky restart). The normal idle epilogue goes through [resolveIdleEpilogue],
     * which may keep the service resident in SENTRY. On error we never enter SENTRY — a failed
     * command has no business leaving a resident process behind.
     */
    private fun stopIfIdle(reason: String, startId: Int) {
        if (detectionJob?.isActive == true) return
        // [DET-RESIDENT-FGS-001 · F2] Error-path teardown is still a DELIBERATE stop — drop any
        // residency stamp so the kill detector never reads this as an OS kill.
        sentryWatchJob?.cancel()
        sentryWatchJob = null
        SentryResidenceStore.clear(this)
        val stopped = fgs.stopForegroundAndSelf(startId) // [FIX BUG-FGS-100][DET-INTAKE-001]
        PaparcarLogger.d(DIAG, "  stopIfIdle($reason) → stopSelfResult($startId)=$stopped")
    }

    /**
     * [DET-RESIDENT-FGS-001] Normal end-of-command teardown decision: DIE (today's wake-and-kill)
     * or stay resident in SENTRY. Preserves the [DET-INTAKE-001] guards exactly — a running job or a
     * newer start command still keep the service alive — and only when genuinely idle consults
     * [resolvePostDetectionLifecycle]. With the flag OFF (default) this is byte-for-byte [stopIfIdle].
     *
     * Suspend because the SENTRY branch needs to know whether anything is parked; both call sites
     * (the intake consumer's DetectionEnded handler and the processIntent epilogue) already run in
     * the serialized coroutine, so the repo read cannot race another command in.
     */
    private suspend fun resolveIdleEpilogue(reason: String, startId: Int) {
        // (a) A running job owns the service — never tear down under it. Same guard as stopIfIdle.
        if (detectionJob?.isActive == true) return
        val parkedSessions = runCatching {
            userParkingRepository.observeActiveSessions().firstOrNull().orEmpty()
        }.getOrElse { emptyList() }
        // [DET-SENTRY-COOLDOWN-001] Fold the just-ended session into the walking-abort streak and
        // hand the resulting quiet period to the monitor BEFORE enterSentry tries to re-arm. A
        // sentry-wake refuted as a walking abort extends the streak; anything else resets it —
        // the pure policy lives in commonMain (SentryWakeCooldown.kt). Field 2026-08-13: one
        // wake-abort cycle every ~18 s for over an hour while the user simply walked.
        val endedTrigger = lastEndedArmTrigger
        if (endedTrigger != null) {
            lastEndedArmTrigger = null
            sentryWakeAbortStreak = nextSentryWakeAbortStreak(
                previousStreak = sentryWakeAbortStreak,
                armedBySentryWake = endedTrigger == DetectionTrigger.SIGNIFICANT_MOTION,
                sessionOutcome = parkingDetectionCoordinator.lastSessionOutcome,
            )
            val cooldownMs = sentryWakeRearmCooldownMs(sentryWakeAbortStreak, detectionConfig)
            runCatching { significantMotionMonitor.applyRearmCooldown(cooldownMs) }
            if (cooldownMs > 0) {
                PaparcarLogger.d(
                    DIAG,
                    "  ⏸ sentry-wake abort streak=$sentryWakeAbortStreak → re-arm cooldown ${cooldownMs / 1000}s [DET-SENTRY-COOLDOWN-001]",
                )
                logSentry(
                    DetectionEvent.Sentry.WAKE_COOLDOWN,
                    signal = "streak=$sentryWakeAbortStreak cooldown=${cooldownMs / 1000}s",
                    sessionId = parkedSessions.firstNotNullOfOrNull { it.geofenceId } ?: "-",
                )
            }
        }
        // [DET-STRATEGY-GATE-001] Residency is strategy-aware: under BLUETOOTH the ACL broadcast
        // wakes the process by itself (manifest receiver, FGS-from-bg exempt), so the resident
        // watcher would only burn battery + pin a permanent notification. Resolved fresh on every
        // epilogue, so pairing/unpairing BT or toggling the adapter self-corrects one cycle later.
        val strategy = runCatching { strategyResolver.resolve() }.getOrDefault(ParkingStrategy.COORDINATOR)
        when (
            resolvePostDetectionLifecycle(
                autoDetectEnabled = appPreferences.autoDetectParking, // [F3] Settings toggle IS the sentry gate
                hasParkedSession = parkedSessions.isNotEmpty(),
                strategy = strategy,
            )
        ) {
            PostDetectionLifecycle.EnterSentry ->
                enterSentry(reason, parkedSessions.firstNotNullOfOrNull { it.geofenceId })
            PostDetectionLifecycle.Stop -> {
                // [DET-RESIDENT-FGS-001 · F2] Deliberate teardown — a residency (if any) ends HERE,
                // not by a kill: drop the stamp so the kill detector never mistakes this for one.
                sentryWatchJob?.cancel()
                sentryWatchJob = null
                SentryResidenceStore.clear(this)
                // (b) stopSelfResult still lets a newer queued intent veto a stale stop.
                val stopped = fgs.stopForegroundAndSelf(startId) // [FIX BUG-FGS-100][DET-INTAKE-001]
                PaparcarLogger.d(DIAG, "  resolveIdleEpilogue($reason) → stop → stopSelfResult($startId)=$stopped")
            }
        }
    }

    /**
     * [DET-RESIDENT-FGS-001] Degrade to the resident idle watcher instead of dying: keep the FGS
     * notification, leave the GPS off (the tracking job already ended, so no location stream is
     * running), and re-arm the significant-motion trigger + seed a safety-net pass — the same
     * re-arm [onDestroy] does today, except the process now STAYS ALIVE, so a subsequent geofence
     * EXIT / AR ENTER lands on a live foreground service (no dead-process resurrection, no
     * Android 12+ background-FGS-start restriction). The transition back to ACTIVE is the ordinary
     * trigger path (ACTION_GEOFENCE_EXIT / ACTION_AR_TRANSITION → startParkingDetection).
     */
    private fun enterSentry(reason: String, geofenceId: String?) {
        detectionRuntime.setPresence(ServicePresence.Sentry)
        // [F2] Durable residency stamp + telemetry. The stamp is cleared on every deliberate exit
        // (wake to ACTIVE, idle teardown), so one that outlives the process proves the OS killed the
        // resident watcher — the kill detectors (worker tick + service re-arm) read it back.
        val residency = SentryResidenceStore.stamp(
            this, geofenceId,
            enteredAtMs = System.currentTimeMillis(),
            enteredElapsedMs = SystemClock.elapsedRealtime(),
        )
        logSentry(DetectionEvent.Sentry.ENTERED, signal = reason, sessionId = residency.geofenceId)
        PaparcarLogger.d(DIAG, "  ⏾ enterSentry($reason) — resident, GPS off, re-arming departure wake [DET-RESIDENT-FGS-001]")
        if (BuildConfig.DEBUG) notificationPort.showDebug("Modo CENTINELA: coche aparcado y detección terminada → quedo residente con GPS apagado, esperando; cuando el coche se mueva verás 'Detección ARRANCADA'")
        // [F3] Swap the FGS notification for the low-profile sentry one (own MIN-importance silent
        // channel, plain-language copy). startForeground with the same id replaces it in place; the
        // next wake's promote (every onStartCommand) swaps the active-detection one back.
        runCatching {
            fgs.promote(
                notificationId = AppNotificationManager.DETECTION_NOTIFICATION_ID,
                notification = foregroundNotificationProvider.buildSentryNotification(),
                withLocationPermission = hasRequiredPermissions(),
            )
        }.onFailure { e -> PaparcarLogger.w(DIAG, "  ⚠ enterSentry notification swap failed: ${e.message}") }
        runCatching { significantMotionMonitor.sync(shouldBeArmed = true) }
            .onFailure { e -> PaparcarLogger.w(DIAG, "  ⚠ enterSentry sig-motion arm failed: ${e.message}") }
        runCatching {
            ParkingSafetyNetWorker.enqueueCheckNow(
                WorkManager.getInstance(this),
                source = ParkingSafetyNetWorker.SOURCE_DETECTION_END,
            )
        }.onFailure { e -> PaparcarLogger.w(DIAG, "  ⚠ enterSentry safety-net enqueue failed: ${e.message}") }
        watchSentryPreconditions()
    }

    /**
     * [DET-RESIDENT-FGS-001 · F3] While resident, watch the two facts that justify residency — the
     * Settings auto-detect toggle and the existence of a parked session. Either going false ends the
     * watch through the ordinary serialized STOP command (same intake, same epilogue, which re-reads
     * both facts and resolves Stop) — so "turn detection off in Settings" or "free my spot from Home"
     * tears the resident watcher down within the same second, with the residency stamp cleared as the
     * deliberate exit it is. The job is cancelled on every exit from SENTRY (wake or teardown).
     */
    private fun watchSentryPreconditions() {
        sentryWatchJob?.cancel()
        sentryWatchJob = lifecycleScope.launch {
            combine(
                appPreferences.observeAutoDetectParking(),
                userParkingRepository.observeActiveSessions(),
            ) { enabled, sessions -> enabled && sessions.isNotEmpty() }
                .distinctUntilChanged()
                .collect { residencyStillWanted ->
                    if (!residencyStillWanted && detectionRuntime.presence.value == ServicePresence.Sentry) {
                        PaparcarLogger.d(DIAG, "  ⏻ sentry stand-down (detection off or nothing parked) → STOP_TRACKING")
                        startService(
                            Intent(this@CoordinatorDetectionService, CoordinatorDetectionService::class.java)
                                .setAction(ACTION_STOP_TRACKING),
                        )
                    }
                }
        }
    }

    /**
     * [DET-RESIDENT-FGS-001 · F2] Close the residency ledger as a tracking job launches, BEFORE the
     * presence flips to ACTIVE. A live SENTRY handing the process over is a `woke` (the arm trigger
     * is the waking signal — this one point sees ALL wake channels: direct SigMotion, geofence EXIT,
     * AR ENTER, manual). A stamp found on a NON-resident start means the resident watcher died and
     * this trigger revived a dead process — the same [resolveSentryKillVerdict] the safety-net
     * worker runs on its periodic lane, witnessed live instead (no heartbeat gap to measure here).
     */
    private fun closeSentryResidencyLedger(trigger: DetectionTrigger) {
        // [F3] Leaving SENTRY (whatever the destination) ends the precondition watch.
        sentryWatchJob?.cancel()
        sentryWatchJob = null
        val residency = SentryResidenceStore.read(this)
        val now = System.currentTimeMillis()
        if (detectionRuntime.presence.value == ServicePresence.Sentry) {
            SentryResidenceStore.clear(this)
            logSentry(
                DetectionEvent.Sentry.WOKE,
                signal = trigger.name,
                sessionId = residency?.geofenceId ?: SentryResidenceStore.FALLBACK_SESSION,
                residencyMs = residency?.let { now - it.enteredAtMs },
            )
            return
        }
        if (residency == null) return
        when (
            resolveSentryKillVerdict(
                residencyExpected = true,
                presence = detectionRuntime.presence.value,
                rebootedSince = SystemClock.elapsedRealtime() < residency.enteredElapsedMs,
            )
        ) {
            SentryKillVerdict.Killed -> {
                SentryResidenceStore.clear(this)
                PaparcarLogger.w(DIAG, "  ⚠ sentry residency stamp outlived the process — resident watcher was killed; $trigger revived us [DET-RESIDENT-FGS-001]")
                logSentry(
                    DetectionEvent.Sentry.KILLED,
                    signal = trigger.name,
                    sessionId = residency.geofenceId,
                    residencyMs = now - residency.enteredAtMs,
                )
            }
            SentryKillVerdict.ClearStamp -> SentryResidenceStore.clear(this)
            SentryKillVerdict.None -> Unit
        }
    }

    /** [DET-RESIDENT-FGS-001 · F2] Fire-and-forget sentry lifecycle telemetry — same launch pattern
     *  as [logArmTrigger] (`log` is suspend but never blocks). */
    private fun logSentry(event: String, signal: String?, sessionId: String, gapMs: Long? = null, residencyMs: Long? = null) {
        val now = System.currentTimeMillis()
        lifecycleScope.launch {
            runCatching {
                detectionEventLogger.log(
                    DetectionEvent.Sentry(
                        sessionId = sessionId,
                        timestampMs = now,
                        event = event,
                        signal = signal,
                        gapMs = gapMs,
                        residencyMs = residencyMs,
                    ),
                )
            }.onFailure { e -> PaparcarLogger.w(DIAG, "  ⚠ sentry-event log failed: ${e.message}") }
        }
    }

    private suspend fun startParkingDetection(
        trigger: DetectionTrigger,
        detail: String? = null,
        trip: TripContext? = null,
        /** [DET-G-05][DET-SOLID-001] Typed evidence behind this arm. GEOFENCE_EXIT passes the
         *  verifier's result; MANUAL passes [ArmEvidence.Manual] (the default). */
        armEvidence: ArmEvidence = ArmEvidence.Manual,
        /** [DET-ZOMBIE-PROBE-001] Arm born from a FAR-delivered (stale-lane) EXIT — the
         *  coordinator shrinks its no-movement budget to the zombie probe. */
        staleExitDelivery: Boolean = false,
    ) {
        // [DET-STRATEGY-GATE-001] Single strategy choke point: EVERY automatic arm funnels through
        // here (geofence EXIT, AR ENTER, sentry sig-motion), so this is the one place that asks
        // whether the coordinator owns detection at all. Field 2026-08-01: only the EXIT lane
        // checked, and the sentry/AR arms pinned the BT-paired Kamiq's trips on the primary Focus.
        // MANUAL is exempt inside the rule (explicit user intent / safety-net arrival handoff).
        // [DET-STOP-BUTTON-001] The user's own veto outranks every automatic nominator. While the
        // quiet period they opened by tapping "Parar detección" lasts, no AR ENTER / fence EXIT /
        // motion wake may arm — without this the button is a lie: the same walk to the passenger
        // seat re-fires AR seconds later and detection comes back on its own. MANUAL is the same
        // user retracting, so it is never suppressed and CLEARS the stamp. Only ARMING sleeps: a
        // fence EXIT delivered meanwhile still released the spot, upstream of this call.
        val userStoppedAtMs = UserStopStore.read(applicationContext)
        if (userStoppedAtMs != null) {
            val now = System.currentTimeMillis()
            if (isArmSuppressedByUserStop(trigger, userStoppedAtMs, now, detectionConfig)) {
                val remainingMs = userStopQuietPeriodRemainingMs(userStoppedAtMs, now, detectionConfig)
                PaparcarLogger.d(
                    DIAG,
                    "  ⊘ arm refused — the user stopped detection; ${remainingMs / 1000}s of quiet left (trigger=$trigger) [DET-STOP-BUTTON-001]",
                )
                logArmSuppressedByUserStop(trigger, remainingMs)
                return
            }
            // Either MANUAL (explicit retraction) or the period lapsed — the stamp has no more work.
            UserStopStore.clear(applicationContext)
        }
        if (trigger != DetectionTrigger.MANUAL) {
            val strategy = strategyResolver.resolve()
            if (!coordinatorMayArm(strategy, trigger)) {
                PaparcarLogger.d(
                    DIAG,
                    "  ⊘ arm refused — strategy=$strategy owns detection; coordinator stands down (trigger=$trigger) [DET-STRATEGY-GATE-001]",
                )
                return
            }
        }
        logArmTrigger(trigger, detail)
        // [DET-SENTRY-COOLDOWN-001] Remember what armed this session; the teardown epilogue folds
        // its outcome into the sentry-wake abort streak. A supersede simply overwrites — only the
        // surviving job's end reaches the epilogue.
        lastEndedArmTrigger = trigger
        PaparcarLogger.d(DIAG, "  ▶ startParkingDetection — launching coordinator (trigger=$trigger)")
        closeSentryResidencyLedger(trigger)
        // [DET-READY-001c] Mark detection as actively running so the Home banner shows Monitoring.
        // Set synchronously here (not inside the coroutine) so a superseded old job's finally — which
        // only flips the flag when it is still the current job — never races this to false.
        detectionRuntime.setRunning(true)
        // [DET-RESIDENT-FGS-001] A tracking job is launching → ACTIVE (whether armed from Dead or woken
        // from Sentry). Sentry re-arms significant-motion, harmless while a job runs; it self-disarms.
        detectionRuntime.setPresence(ServicePresence.Active)
        // Publish the trip's origin AFTER setRunning(true) so the first Monitoring emission already
        // carries it. setRunning(true) does not touch the trip; setTrip(null) clears any stale origin
        // from a previous trip (manual start). Set after — never before cancelDetectionJob — so a
        // superseded job's finally (which clears on setRunning(false)) can't wipe it. [DEPART-CONSISTENCY-001]
        detectionRuntime.setTrip(trip)
        // [DET-NEVER-SILENT-001] Persist a durable pending at arm so a process death mid-trip is
        // recoverable; the heartbeat below keeps it fresh, the finally clears it on any terminal.
        val armedAt = System.currentTimeMillis()
        val armId = armedAt.toString()
        io.apptolast.paparcar.detection.PendingDetectionStore.arm(applicationContext, armId, armedAt, trigger.name)
        detectionJob = lifecycleScope.launch {
            val thisJob = coroutineContext[Job]

            // [DET-NEVER-SILENT-001] Keep the pending's heartbeat fresh while the session is alive and
            // latch sawDriving once the trip reaches park-evaluation (Candidate). A live long trip
            // (1 h motorway) never looks dead; the watchdog nudges only stale (process-death) pendings,
            // and AR_VEHICLE_ENTER only if it actually drove.
            val heartbeat = launch {
                launch {
                    detectionRuntime.phase.collect { phase ->
                        if (phase == io.apptolast.paparcar.domain.detection.DetectionPhase.Candidate) {
                            io.apptolast.paparcar.detection.PendingDetectionStore.heartbeat(
                                applicationContext, armId, System.currentTimeMillis(), sawDriving = true,
                            )
                        }
                    }
                }
                while (isActive) {
                    kotlinx.coroutines.delay(detectionConfig.pendingHeartbeatMs)
                    io.apptolast.paparcar.detection.PendingDetectionStore.heartbeat(
                        applicationContext, armId, System.currentTimeMillis(), sawDriving = false,
                    )
                }
            }

            // [ROUTE-PASSIVE-FILL-001] Passive piggyback tap: inherits, at zero battery cost, the
            // fixes OTHER apps request (a navigation app running during the drive) and feeds them
            // ONLY into the persisted route — when the OEM throttles OUR request (field 2026-08-14:
            // 7-min MIUI GPS nap → a 4.6 km hole the matcher had to reconstruct), a live nav app
            // keeps the recorded route dense. NEVER merged into the coordinator's stream: the
            // detection decisions stay on their own measured stream. The route store's own
            // accuracy gate + decimation absorb whatever quality arrives.
            val passiveRouteTap = launch {
                locationDataSource.observePassiveLocation()
                    .catch { e -> PaparcarLogger.w(DIAG, "    ⚠ passive route tap failed: ${e.message}") }
                    .collect { drivingRouteStore.append(it) }
            }

            // [FIX BUG-SERVICE-108: pull vehicle name inside the detection job rather than in a
            //  parallel lifecycleScope.launch — same lifetime as the coordinator, no leak across
            //  flapping START_TRACKING events.]
            runCatching {
                val name = vehicleRepository.observeActiveVehicle().firstOrNull()
                    ?.let { it.displayName(fallback = "").takeIf { n -> n.isNotBlank() } }
                if (name != null) {
                    notificationPort.updateDetectionVehicle(
                        name,
                        AppNotificationManager.DETECTION_NOTIFICATION_ID,
                    )
                }
            }.onFailure { e ->
                PaparcarLogger.w(DIAG, "    ⚠ vehicle-name fetch failed: ${e.message}")
            }

            try {
                PaparcarLogger.d(DIAG, "    ▶ detection coroutine entered, invoking coordinator")
                // [DET-G-04] GEOFENCE_EXIT arms MID-trip (the car already crossed its parked-car
                // geofence radius), so on a short hop between two parks this session's GPS stream can
                // warm up after the fast driving is over — the coordinator would never observe driving
                // speed and the false-enter guard would discard a real park. Tell it the drive already
                // happened. MANUAL arms BEFORE the trip, so its stream captures the speed
                // naturally and must keep the guard (a premature "I'm driving" tap can be spurious).
                // [DET-G-05][DET-SOLID-001] …but only when the exit carries VERIFIED evidence: the
                // phone leaving the radius on foot fires the same exit, and an unconditional seed
                // let walking re-confirm a bogus park (BUG-REPARK-WALK-001). Unverified exits run
                // with the legacy guards; DepartureDetectionWorker upgrades the live session on
                // late evidence via DepartureConfirmationListener.
                parkingDetectionCoordinator(
                    // Tap the tracking stream to persist the dense driving route (same fixes the
                    // coordinator consumes → zero extra battery). Survives background / process
                    // death so Home redraws the REAL trip, not a reconstruction. [DET-ROUTE-TRACK-001]
                    observeAdaptiveLocation().onEach { drivingRouteStore.append(it) },
                    armEvidence = armEvidence,
                    // The nominating fence's vehicle (geofence exit identifies the car). Null for
                    // manual / AR-armed trips. [VEH-ACTIVE-FENCE-001]
                    nominatingVehicleId = trip?.departingVehicleId,
                    staleExitDelivery = staleExitDelivery, // [DET-ZOMBIE-PROBE-001]
                    // [DET-SHORT-HOP-PROOF-001] The pin this trip LEFT — reference for the
                    // displacement-based drive proof (a short stop-and-go hop never holds the
                    // speed-window shape the track proof needs). Null on manual/AR arms with no
                    // origin pin, and then only the speed proof applies.
                    departureAnchor = trip?.departurePoint,
                    departureFenceRadiusMeters = trip?.departurePoint?.let { anchor ->
                        // Size unknown at this point (the TripContext carries position + vehicle id,
                        // not the size snapshot) → the default radius, which is the LARGEST of the
                        // common classes' bases; a wider radius only makes the proof stricter.
                        detectionConfig.geofenceRadiusFor(sizeCategory = null, accuracyMeters = anchor.accuracy)
                    } ?: 0f,
                )
                PaparcarLogger.d(DIAG, "    ✓ coordinator returned NORMALLY")
                // [DET-HONEST-CLOSE-001] A silent abort must not lose a real drive-away: if the car
                // provably left its last pin, release it + leave an approximate mark + nudge — NOW,
                // from the live FGS, not deferred to the Doze-held worker. NonCancellable so a
                // supersede mid-release can't leave the stale pin half-cleared.
                withContext(NonCancellable) {
                    maybeRunHonestClose()
                    maybeStampArrivalResolution() // [DET-BACKFILL-TAINT-001]
                    // AFTER the honest close consumed the previous witness — a session never
                    // witnesses for its own abort. [DET-UNWITNESSED-DISPLACEMENT-001]
                    stampLastWitnessedFix()
                }
            } catch (e: CancellationException) {
                PaparcarLogger.d(DIAG, "    ✗ detection cancelled: ${e.message}")
                throw e
            } catch (e: Exception) {
                PaparcarLogger.e(DIAG, "    ✗ detection error", e)
                notificationPort.showDebug("ERROR en la detección (${e.message}): la sesión se aborta SIN pin → la red de seguridad periódica sigue vigilando tu plaza")
            } finally {
                // [DET-NEVER-SILENT-001] This job reached a terminal (confirm / abort / supersede) →
                // stop its heartbeat and clear its pending. ONLY a process death skips this finally,
                // leaving the pending stale for the watchdog. Cleared for superseded jobs too (their
                // armId is distinct from the replacement's), so a supersede never leaves a false pending.
                heartbeat.cancel()
                passiveRouteTap.cancel() // [ROUTE-PASSIVE-FILL-001] the piggyback listener dies with the session
                io.apptolast.paparcar.detection.PendingDetectionStore.clear(applicationContext, armId)
                // Skip teardown when this job has been superseded by a newer detection job
                // (START_TRACKING / IN_VEHICLE_ENTER replacement). Stopping here would
                // destroy the service after the replacement coordinator was just launched,
                // killing it via onDestroy. [DETECT-SERVICE-RACE-001]
                if (detectionJob === thisJob) {
                    // [DET-READY-001c] This job is the current one and is ending → detection idle.
                    detectionRuntime.setRunning(false)
                    // [DET-ROUTE-TRACK-001] The recorded route is NOT cleared here. This finally runs
                    // on any terminal — including a spurious/late job end mid-trip (Doze, an OEM
                    // freeze, a stop misread as an end) that a fresh arm then continues. Clearing here
                    // wiped the in-progress route so a re-entry redrew a straight line from the parked
                    // spot (field 2026-08-09). The route is instead cleared at CONFIRM (route consumed
                    // onto the parking) with a genuine-new-trip gap-reset as the safety net.
                    // [DET-ENDED-VETO-RACE-001] DetectionEnded is NOT sent from here. A send from
                    // inside the still-active job resumes the intake consumer INLINE within the
                    // trySend (Main.immediate on the same thread), so stopIfIdle ran while this
                    // finally was mid-flight, saw detectionJob.isActive == true, and vetoed its own
                    // teardown — with no later command ever retrying, the FGS stayed glued to a
                    // live idle process (field 2026-07-23, both devices). The send lives in the
                    // job's invokeOnCompletion (startParkingDetection), which the runtime invokes
                    // only once the job is COMPLETE (isActive == false guaranteed).
                } else {
                    PaparcarLogger.d(DIAG, "    ■ finally → superseded by newer job; its completion callback skips the stop")
                }
            }
        }
        // [DET-ENDED-VETO-RACE-001] The teardown request is registered on job COMPLETION, never
        // sent from the job's own finally: invokeOnCompletion fires only after the job reaches a
        // terminal state, so when the intake consumer runs stopIfIdle — even resumed inline within
        // this trySend — detectionJob.isActive is already false and the stop proceeds. The identity
        // guard preserves the supersede rule ([DETECT-SERVICE-RACE-001]): a replaced job's callback
        // must not stop the service its replacement is using. A newer intent delivered after the
        // send still vetoes via stopSelfResult (startId mismatch), exactly as before.
        val startedJob = detectionJob!!
        startedJob.invokeOnCompletion {
            if (detectionJob === startedJob) {
                PaparcarLogger.d(DIAG, "    ■ job complete → DetectionEnded(startId=$lastStartId) → intake")
                intake.trySend(Command.DetectionEnded(lastStartId))
            }
        }
    }

    /**
     * [DET-AR-REARM-001] Records WHICH trigger armed this Coordinator session to three sinks:
     *  - **Crashlytics** custom key `det_trigger` (rides along on any subsequent crash report),
     *  - the remote **[DetectionEventLogger]** (Firestore trace, gated by remote config in the real
     *    binding) as a `SessionStarted` whose strategy encodes the arm trigger,
     *  - a **debug notification** (DEBUG builds only) so a field tester sees, on the device, whether
     *    a park was armed by GEOFENCE_EXIT, AR proximity, or the manual button.
     */
    /**
     * [DET-STOP-BUTTON-001] Remote trace of an arm the user's quiet period refused. Without it a
     * field session where "detection never started" looks identical to an OEM-killed trigger — the
     * exact confusion the provenance rule exists to prevent. Same fire-and-forget shape as
     * [logArmTrigger], under its own synthetic id since there is no session to attach to.
     */
    private fun logArmSuppressedByUserStop(trigger: DetectionTrigger, remainingMs: Long) {
        val now = System.currentTimeMillis()
        lifecycleScope.launch {
            runCatching {
                detectionEventLogger.log(
                    DetectionEvent.Decision(
                        sessionId = "arm_$now",
                        timestampMs = now,
                        outcome = "ARM_SUPPRESSED_USER_STOP",
                        pathLabel = "${trigger.name}(quiet=${remainingMs / 1000}s)",
                    ),
                )
            }.onFailure { e -> PaparcarLogger.w(DIAG, "  ⚠ suppressed-arm log failed: ${e.message}") }
        }
        if (BuildConfig.DEBUG) {
            notificationPort.showDebug(
                "Detección NO arrancada: paraste la detección hace poco, así que ignoro los avisos " +
                    "automáticos durante ${remainingMs / 60_000} min más. Pulsa 'Estoy conduciendo' si quieres volver ya",
            )
        }
    }

    private fun logArmTrigger(trigger: DetectionTrigger, detail: String?) {
        runCatching {
            FirebaseCrashlytics.getInstance().setCustomKey("det_trigger", trigger.name)
        }
        val now = System.currentTimeMillis()
        lifecycleScope.launch {
            runCatching {
                detectionEventLogger.log(
                    DetectionEvent.SessionStarted(
                        sessionId = "arm_$now",
                        timestampMs = now,
                        strategy = "ARM:${trigger.name}${detail?.let { " ($it)" } ?: ""}",
                    ),
                )
            }.onFailure { e -> PaparcarLogger.w(DIAG, "  ⚠ detection-event log failed: ${e.message}") }
        }
        if (BuildConfig.DEBUG) {
            val cause = when (trigger) {
                DetectionTrigger.GEOFENCE_EXIT -> "saliste de la valla de tu plaza"
                DetectionTrigger.MANUAL -> "pulsaste 'Estoy conduciendo'"
                DetectionTrigger.AR_VEHICLE_ENTER -> "el móvil te ve subiendo a tu coche"
                DetectionTrigger.SIGNIFICANT_MOTION -> "el sensor de movimiento saltó estando de centinela"
            }
            val msg = "Detección ARRANCADA (${trigger.name}): $cause" +
                (detail?.let { " · $it" } ?: "") +
                " → mido el viaje; solo la conducción medida confirmará una plaza"
            notificationPort.showDebug(msg)
        }
    }

    private fun updateCrashlyticsContext(intentAction: String?, hasLocationPerm: Boolean) {
        // [FIX BUG-SERVICE-110: never swallow throwables silently]
        runCatching {
            FirebaseCrashlytics.getInstance().run {
                setCustomKey("det_action", intentAction ?: "null→START_TRACKING")
                setCustomKey("det_job_active", detectionJob?.isActive == true)
                setCustomKey("det_has_movement", parkingDetectionCoordinator.hasDetectedMovement)
                setCustomKey("det_location_perm", hasLocationPerm)
            }
        }.onFailure { e ->
            PaparcarLogger.w(DIAG, "  ⚠ Crashlytics custom-keys update failed: ${e.message}")
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val fineLoc = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val bgLoc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        } else true
        return fineLoc && bgLoc
    }

    /**
     * Centralised location-permission gate — covers every entry path: explicit START, IN_VEHICLE
     * PendingIntent delivery, and Activity Recognition fallback. Caller should `return`.
     *
     * [DET-INTAKE-001] Detection cannot run without location, so any in-flight job is cancelled;
     * the intake epilogue then tears the service down (the FGS detection notification is removed
     * by the accepted stop — the separate permission-revoked notification has a different ID).
     */
    private fun guardPermissions(actionLabel: String): Boolean {
        if (hasRequiredPermissions()) return true
        PaparcarLogger.w(DIAG, "  ✗ $actionLabel aborted — missing location permission")
        notificationPort.showPermissionRevoked()
        cancelDetectionJob()
        return false
    }

    override fun onDestroy() {
        PaparcarLogger.d(DIAG, "■ Service onDestroy — cancelling detectionJob")
        detectionJob?.cancel()
        detectionRuntime.setRunning(false) // [DET-READY-001c] service gone → detection idle
        detectionRuntime.setPresence(ServicePresence.Dead) // [DET-RESIDENT-FGS-001] process gone
        // [FIX BUG-FGS-113: defensive safety net. Every primary teardown path is supposed
        //  to call fgs.stopForegroundAndSelf(), but if any future code path reaches onDestroy
        //  without first removing the FGS notification, do it now. Idempotent — calling
        //  stopForeground after the notification is already gone is a no-op on every Android
        //  version we ship to.]
        runCatching { fgs.removeForegroundNotification() }
            .onFailure { e -> PaparcarLogger.w(DIAG, "  ⚠ onDestroy stopForeground failed: ${e.message}") }
        // [DET-SAFETY-NET-001] Every detection episode ends here (post-confirm, post-departure,
        // aborts). Run one safety-net pass now so the significant-motion trigger is re-armed and
        // the position anchor seeded seconds after a park — not up to 15 min later.
        runCatching {
            ParkingSafetyNetWorker.enqueueCheckNow(
                WorkManager.getInstance(this),
                source = ParkingSafetyNetWorker.SOURCE_DETECTION_END,
            )
        }
        super.onDestroy()
        PaparcarLogger.d(DIAG, "■ Service onDestroy DONE")
    }

    companion object {
        // [DET-RESIDENT-FGS-001 · F3] The F1/F2 SENTRY_ENABLED experiment const is gone: residency is
        // now governed at runtime by the Settings auto-detect toggle (resolveIdleEpilogue reads
        // AppPreferences.autoDetectParking) — the sentry has no switch of its own.

        /** m/s → km/h factor for the one-shot exit-speed sample. [DET-SOLID-001] */
        private const val KMH_PER_MPS = 3.6f

        const val ACTION_START_TRACKING = "io.apptolast.paparcar.ACTION_START_TRACKING"
        const val ACTION_STOP_TRACKING = "io.apptolast.paparcar.ACTION_STOP_TRACKING"

        /** [DET-STOP-BUTTON-001] The user pressed "Parar detección" on a live session (Home row or
         *  the foreground-service notification). Never sent by the system — [ACTION_STOP_TRACKING]
         *  stays the internal cancel; only THIS one stamps `stopped_by_user` and opens the quiet
         *  period in which automatic nominators may not re-arm. */
        const val ACTION_USER_STOP = "io.apptolast.paparcar.ACTION_USER_STOP"
        // [DET-RESIDENT-FGS-001] Significant-motion wake, delivered by SignificantMotionMonitor ONLY
        // when the service is resident in SENTRY (already foreground → legal re-delivery). Arms a
        // coordinator session with Unverified evidence; the WorkManager path is used from a dead process.
        const val ACTION_SENTRY_WAKE = "io.apptolast.paparcar.ACTION_SENTRY_WAKE"
        // [DET-WATCH-HONEST-001] Rebuild the resident SENTRY watcher an OEM kill dropped while a car
        // stayed parked. The idle epilogue re-enters SENTRY iff a Coordinator car is parked, else stops.
        // [DET-WATCH-REACTIVATE-001] Both senders go through DepartureWatchResumer, from a foreground
        // moment (legal FGS start): the visible Activity noticing the gap, and the user's "Reactivate"
        // tap. EXTRA_RESUME_SOURCE says which one, so a field log never has to guess.
        const val ACTION_RESUME_SENTRY = "io.apptolast.paparcar.ACTION_RESUME_SENTRY"
        const val EXTRA_RESUME_SOURCE = "io.apptolast.paparcar.EXTRA_RESUME_SOURCE"
        // [DET-TIERS-001] Bluetooth arbitration override: the BT receiver decided a paired-car edge
        // must supersede the running coordinator session; the service aborts it. Reason is for the log.
        const val ACTION_BT_OVERRIDE = "io.apptolast.paparcar.ACTION_BT_OVERRIDE"
        const val EXTRA_BT_OVERRIDE_REASON = "io.apptolast.paparcar.EXTRA_BT_OVERRIDE_REASON"

        // [DET-HONEST-CLOSE-001] Terminal outcome labels that trigger the honest-close ladder —
        // the two SILENT aborts. Shared with the sentry-wake cooldown reducer, so they live in
        // commonMain (`DetectionSessionOutcomes`) rather than as per-class literals.
        private const val OUTCOME_ABORTED_FALSE_ENTER =
            io.apptolast.paparcar.domain.detection.DetectionSessionOutcomes.ABORTED_FALSE_ENTER
        private const val OUTCOME_ABORTED_NO_MOVEMENT =
            io.apptolast.paparcar.domain.detection.DetectionSessionOutcomes.ABORTED_NO_MOVEMENT
        // [DET-BACKFILL-TAINT-001] Unattended abort the coordinator resolved as NUDGE-ONLY (gap
        // anchor: no place is honest) — stamped so the safety net's backfill defers to the nudge.
        private const val OUTCOME_ABORTED_UNATTENDED_GAP_ANCHOR = "aborted_unattended_gap_anchor"
        // [DET-G-01] Geofence-exit delivered directly to the service via getForegroundService so
        // Play Services grants the privileged FGS start (the same getForegroundService mechanism the
        // AR IN_VEHICLE path used before AR was moved to a plain broadcast — BUG-FGS-001).
        const val ACTION_GEOFENCE_EXIT = "io.apptolast.paparcar.ACTION_GEOFENCE_EXIT"
        // [DET-AR-FIRST-001] AR IN_VEHICLE ENTER delivered directly to the service via
        // getForegroundService (the DECISION lane) — GMS grants the privileged FGS start, same
        // as the geofence EXIT lane. The evidence receiver lane keeps its own getBroadcast.
        const val ACTION_AR_TRANSITION = "io.apptolast.paparcar.ACTION_AR_TRANSITION"
        /** ns→ms for the AR event's elapsedRealTimeNanos → epoch conversion. */
        private const val NANOS_PER_MS = 1_000_000L
        // Pre-save prompt (state A): user is being asked whether they parked.
        const val ACTION_PARKING_CONFIRMED = "io.apptolast.paparcar.ACTION_PARKING_CONFIRMED"
        const val ACTION_PARKING_DENIED = "io.apptolast.paparcar.ACTION_PARKING_DENIED"
        // [REFACTOR-300] Post-save confirm (state B): the parking has been saved; user is
        // acknowledging or reverting.
        const val ACTION_PARKING_ACK = "io.apptolast.paparcar.ACTION_PARKING_ACK"
        const val ACTION_PARKING_REVERT = "io.apptolast.paparcar.ACTION_PARKING_REVERT"
        const val EXTRA_PARKING_ID = "io.apptolast.paparcar.EXTRA_PARKING_ID"
        // [DET-AR-REARM-001] Watchdog "still parked? → I've left" → release the spot for the geofence.
        const val ACTION_DEPARTURE_CONFIRMED = "io.apptolast.paparcar.ACTION_DEPARTURE_CONFIRMED"
        const val EXTRA_GEOFENCE_ID = "io.apptolast.paparcar.EXTRA_GEOFENCE_ID"
        private const val DIAG = "PARKDIAG/Service"
    }
}
