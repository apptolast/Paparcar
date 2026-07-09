package io.apptolast.paparcar.detection.service

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.google.android.gms.location.GeofencingEvent
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.apptolast.paparcar.BuildConfig
import io.apptolast.paparcar.detection.worker.DepartureDetectionWorker
import io.apptolast.paparcar.detection.worker.ParkingSafetyNetWorker
import io.apptolast.paparcar.domain.coordinator.CoordinatorParkingDetector
import io.apptolast.paparcar.domain.detection.ArmEvidence
import io.apptolast.paparcar.domain.detection.DetectionTrigger
import io.apptolast.paparcar.domain.detection.MutableDetectionRuntimeState
import io.apptolast.paparcar.domain.detection.ParkingStrategy
import io.apptolast.paparcar.domain.detection.ParkingStrategyResolver
import io.apptolast.paparcar.domain.diagnostics.DetectionEvent
import io.apptolast.paparcar.domain.detection.TripContext
import io.apptolast.paparcar.domain.diagnostics.DetectionEventLogger
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.displayName
import io.apptolast.paparcar.domain.notification.AppNotificationManager
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.repository.VehicleRepository
import io.apptolast.paparcar.domain.service.DepartureEventBus
import io.apptolast.paparcar.domain.service.GeofenceEvent
import io.apptolast.paparcar.domain.service.GeofenceEventBus
import io.apptolast.paparcar.domain.service.GeofenceManager
import io.apptolast.paparcar.domain.usecase.location.GetOneLocationUseCase
import io.apptolast.paparcar.domain.usecase.location.ObserveAdaptiveLocationUseCase
import io.apptolast.paparcar.domain.usecase.parking.ProcessConfirmedDepartureUseCase
import io.apptolast.paparcar.domain.usecase.parking.RevertParkingUseCase
import io.apptolast.paparcar.domain.usecase.parking.VerifyDepartureEvidenceUseCase
import io.apptolast.paparcar.domain.util.PaparcarLogger
import io.apptolast.paparcar.domain.util.haversineMeters
import io.apptolast.paparcar.notification.ForegroundNotificationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
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
    private val detectionConfig: ParkingDetectionConfig by inject()

    // [REFACTOR: extract FGS lifecycle into ForegroundServiceController]
    private val fgs by lazy { ForegroundServiceController(this) }

    // Main-thread-only — lifecycleScope's default dispatcher is Main.immediate. @Volatile is
    // belt-and-braces against potential cross-thread reads from diagnostic code. [audit C-2]
    @Volatile private var detectionJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        PaparcarLogger.d(DIAG, "▶ Service onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        PaparcarLogger.d(DIAG, "▶ onStartCommand action=${intent?.action} flags=$flags startId=$startId")

        // [DET-B-02] A null intent is a START_STICKY auto-restart after a process kill. The
        // coordinator's detection state lives only in memory and is gone, so there is no session to
        // resume — promoting the FGS here would glue a detection notification on with no work behind
        // it (the orphan-FGS bug). Stop WITHOUT promoting; a genuine drive re-arms detection via a
        // fresh trigger (AR ENTER today, GEOFENCE_EXIT after DET-G-01). Missing one park is a
        // zero-cost false negative; a hung notification is not.
        if (intent == null) {
            PaparcarLogger.d(DIAG, "  ⊘ null intent (sticky restart) — no recoverable session; stop without promoting FGS [DET-B-02]")
            fgs.stopForegroundAndSelf() // never promoted → internal stopForeground is a no-op
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
            stopSelf()
            return START_NOT_STICKY
        }
        PaparcarLogger.d(DIAG, "  ✓ startForeground done (locationPermission=$hasPerms)")
        updateCrashlyticsContext(intent.action, hasPerms)

        when (val action = intent.action) {
            ACTION_START_TRACKING -> handleStartTracking()
            ACTION_GEOFENCE_EXIT -> handleGeofenceExit(intent)
            ACTION_PARKING_CONFIRMED -> handleUserConfirmed()
            ACTION_PARKING_DENIED -> handleUserDenied()
            ACTION_PARKING_ACK -> handlePostSaveAck() // [REFACTOR-300]
            ACTION_PARKING_REVERT -> handlePostSaveRevert(intent.getStringExtra(EXTRA_PARKING_ID)) // [REFACTOR-300]
            ACTION_DEPARTURE_CONFIRMED -> handleWatchdogDeparture(intent.getStringExtra(EXTRA_GEOFENCE_ID)) // [DET-AR-REARM-001]
            ACTION_STOP_TRACKING -> {
                PaparcarLogger.d(DIAG, "  → STOP_TRACKING — stopForegroundAndSelf()")
                fgs.stopForegroundAndSelf() // [FIX BUG-FGS-100: cleanup FGS notif before stop]
            }
            // [DET-B-01] Unknown / null action on a non-null intent: we already promoted to satisfy
            // the 5 s window, so tear down if idle instead of leaving the FGS notification hanging.
            else -> {
                PaparcarLogger.d(DIAG, "  ⊘ unhandled action=$action — stopIfIdle [DET-B-01]")
                stopIfIdle("unhandled-action")
            }
        }

        return START_STICKY
    }

    private fun handleStartTracking() {
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
        // [FIX BUG-FGS-103: a confirm action that arrives with no active job is a stale
        //  tap (auto-confirm already wrote the spot). The notification is dismissed by
        //  the coordinator's onUserConfirmedParking; we must also tear down the FGS so
        //  the service does not stay orphaned with its detection notification.]
        if (detectionJob?.isActive != true) {
            PaparcarLogger.d(DIAG, "    ↳ no active job — stopForegroundAndSelf()")
            fgs.stopForegroundAndSelf()
        }
    }

    private fun handleUserDenied() {
        PaparcarLogger.d(DIAG, "  → PARKING_DENIED delivered to coordinator")
        parkingDetectionCoordinator.onUserDeniedParking()
        // [FIX BUG-FGS-103: same stale-tap handling as confirm]
        if (detectionJob?.isActive != true) {
            PaparcarLogger.d(DIAG, "    ↳ no active job — stopForegroundAndSelf()")
            fgs.stopForegroundAndSelf()
        }
    }

    /**
     * [REFACTOR-300] "Sí, confirmar" on the post-save notification.
     * The save already happened; nothing to do except dismiss the notif and tear down.
     */
    private fun handlePostSaveAck() {
        PaparcarLogger.d(DIAG, "  → PARKING_ACK — user acknowledged auto-confirm")
        notificationPort.dismiss(AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID)
        // Detection job is almost certainly idle (auto-confirm ends the session), but guard
        // anyway: if it's somehow active, leave it running.
        if (detectionJob?.isActive != true) {
            fgs.stopForegroundAndSelf()
        }
    }

    /**
     * [REFACTOR-300] "No, cancelar" on the post-save notification.
     * Runs the [RevertParkingUseCase] for the parkingId carried in the intent extras.
     * The use case dismisses the notification and removes the geofence + clears active session;
     * we tear down the FGS after it returns.
     */
    private fun handlePostSaveRevert(parkingId: String?) {
        if (parkingId.isNullOrBlank()) {
            PaparcarLogger.w(DIAG, "  ✗ PARKING_REVERT received without parkingId — dismissing notif and stopping")
            notificationPort.dismiss(AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID)
            fgs.stopForegroundAndSelf()
            return
        }
        PaparcarLogger.d(DIAG, "  → PARKING_REVERT — running RevertParkingUseCase(parkingId=$parkingId)")
        lifecycleScope.launch {
            runCatching { revertParking(parkingId) }
                .onFailure { e -> PaparcarLogger.e(DIAG, "    ✗ revert failed", e) }
            // Whether revert succeeded or failed (best-effort), tear down so the FGS notif
            // does not stay glued on. The user can retry from the history screen if needed.
            fgs.stopForegroundAndSelf()
        }
    }

    /**
     * [DET-AR-REARM-001] Watchdog "I've left" tap: the user confirmed a departure the geofence EXIT
     * missed. Release the spot for the given geofence via [ProcessConfirmedDepartureUseCase] (report
     * freed + clear session + remove geofence + unregister AR arming), dismiss the prompt, tear down.
     */
    private fun handleWatchdogDeparture(geofenceId: String?) {
        if (geofenceId.isNullOrBlank()) {
            PaparcarLogger.w(DIAG, "  ✗ DEPARTURE_CONFIRMED without geofenceId — dismiss + stop")
            notificationPort.dismiss(AppNotificationManager.STILL_PARKED_NOTIFICATION_ID)
            fgs.stopForegroundAndSelf()
            return
        }
        PaparcarLogger.d(DIAG, "  → DEPARTURE_CONFIRMED (watchdog) geofenceId=$geofenceId")
        lifecycleScope.launch {
            runCatching { processConfirmedDeparture(geofenceId) }
                .onFailure { e -> PaparcarLogger.e(DIAG, "    ✗ watchdog departure failed", e) }
            notificationPort.dismiss(AppNotificationManager.STILL_PARKED_NOTIFICATION_ID)
            fgs.stopForegroundAndSelf()
        }
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
    private fun handleGeofenceExit(intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent)
        if (event == null || event.hasError()) {
            PaparcarLogger.w(DIAG, "  ✗ GEOFENCE_EXIT — null or error event (code=${event?.errorCode})")
            event?.let {
                geofenceEventBus.emit(GeofenceEvent.Error("GeofencingEvent error code: ${it.errorCode}", System.currentTimeMillis()))
            }
            stopIfIdle("geofence-error")
            return
        }
        val triggering = event.triggeringGeofences
        if (triggering.isNullOrEmpty()) {
            PaparcarLogger.w(DIAG, "  ✗ GEOFENCE_EXIT — no triggering geofences")
            stopIfIdle("geofence-empty")
            return
        }
        val triggerLoc = event.triggeringLocation
        val now = System.currentTimeMillis()
        lifecycleScope.launch {
            // 1. Resolve every triggering geofence to its active session; self-clean ORPHANS (a
            //    geofence with no active session — a re-park without a confirmed departure left it
            //    registered NEVER_EXPIRE; it fires spurious exits that would arm with nothing to
            //    release). Root cause is also fixed in ConfirmParkingUseCase. [orphan-geofence]
            val realExits = mutableListOf<Pair<String, UserParking>>()
            for (geofence in triggering) {
                val id = geofence.requestId
                val session = runCatching { userParkingRepository.getActiveSessionByGeofence(id) }.getOrNull()
                if (session == null) {
                    PaparcarLogger.w(DIAG, "  ✗ GEOFENCE_EXIT orphan geof=$id — removing")
                    if (BuildConfig.DEBUG) {
                        notificationPort.showDebug("GEOFENCE_EXIT HUÉRFANA geof=${id.take(8)} → limpio, no armo")
                    }
                    runCatching { geofenceService.removeGeofence(id) }
                    // [DET-SOLID-001] Observability: orphan geofences are invariant violations.
                    runCatching { detectionEventLogger.log(DetectionEvent.OrphanCleaned(sessionId = id, timestampMs = now)) }
                    continue
                }
                realExits += id to session
            }

            // All triggering geofences were orphans → nothing real happened.
            if (realExits.isEmpty()) {
                stopIfIdle("geofence-all-orphan")
                return@launch
            }

            // 2. Decide which vehicle(s) actually departed. With OVERLAPPING geofences the ACTIVE
            //    vehicle's and an INACTIVE still-parked car's geofences can both fire (their radii
            //    overlap) — but the inactive car is still parked, so releasing its spot would be a
            //    false positive. Prefer the active vehicle when its geofence is among those that
            //    fired; only when it is NOT among them does the user appear to have left with an
            //    inactive vehicle → release that one. [multi-vehicle overlap]
            //    FUTURE: richer attribution when only an inactive vehicle's geofence fires.
            val activeVehicleId = runCatching { vehicleRepository.observeActiveVehicle().firstOrNull()?.id }.getOrNull()
            val departing = realExits.filter { it.second.vehicleId == activeVehicleId }
                .ifEmpty { realExits }

            // 3. Delivery-distance split — but BOTH sides run the same machinery. A real
            //    drive-away is delivered far BY CONSTRUCTION (the car is moving + OEM lag: field
            //    2026-07-09, Redmi ride home d=657 m) while a walking exit is delivered at the
            //    boundary — so distance must never decide WHETHER to look, only what the delivery
            //    is worth on its own: a boundary delivery additionally emits the in-process
            //    Exited event; a far one is also recorded for the reconcile's conjunction (the
            //    backstop when the trip ended inside the delivery lag, e.g. ColorOS holding an
            //    EXIT overnight and delivering it 4 km away — field 2026-07-08 04:16; releasing
            //    on that alone confirmed the departure of a car that had not moved, which is why
            //    every release now demands measured movement, not a trusted delivery).
            //    [DET-EXIT-TRUST-001][DET-RIDE-PROOF-001]
            val (boundaryExits, staleExits) = departing.partition { (_, session) ->
                val deliveredAtMeters = triggerLoc?.let {
                    haversineMeters(it.latitude, it.longitude, session.location.latitude, session.location.longitude)
                }
                deliveredAtMeters == null || deliveredAtMeters <= detectionConfig.watchdogFarThresholdMeters
            }

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
                    notificationPort.showDebug("EXIT lejano ($detail) → re-check en vivo + conjunción")
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
                    if (!guardPermissions("GEOFENCE_EXIT")) return@launch
                    // Loop guard: if the coordinator is already running, a fresh exit (e.g. one its
                    // own active GPS stream provoked) must NOT cancel + restart it — that would reset
                    // the no-movement abort timer and, fed by more bad fixes, spin a restart loop.
                    // Departure was already dispatched above; just don't re-arm. [DET-AR-REARM-001]
                    if (detectionJob?.isActive == true) {
                        PaparcarLogger.d(DIAG, "  ↻ GEOFENCE_EXIT — coordinator already running; not re-arming")
                        return@launch
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
                    )
                }
                ParkingStrategy.BLUETOOTH, ParkingStrategy.NONE -> {
                    PaparcarLogger.d(DIAG, "  → GEOFENCE_EXIT — strategy not COORDINATOR; not arming")
                    stopIfIdle("geofence-non-coordinator")
                }
            }
        }
    }

    // [DET-SOLID-001 C1b] handleArVehicleEnter + the AR-proximity re-arm were purged: AR is an
    // INDICATOR only (the ENTER now rides the passive broadcast receiver and only stamps
    // DepartureEventBus). Arming is exclusive to GEOFENCE_EXIT + MANUAL. If AR-proximity ever
    // returns, redesign it on top of ArmEvidence — do not resurrect the FGS-start path.

    /** Cancels the in-flight detection job (if any) and nulls the slot. Main-thread only. */
    private fun cancelDetectionJob() {
        detectionJob?.cancel()
        detectionJob = null
    }

    /** Stops the service only when no detection job is currently running. */
    private fun stopIfIdle(reason: String) {
        if (detectionJob?.isActive == true) return
        PaparcarLogger.d(DIAG, "  stopIfIdle($reason) → stopForegroundAndSelf()")
        fgs.stopForegroundAndSelf() // [FIX BUG-FGS-100]
    }

    private fun startParkingDetection(
        trigger: DetectionTrigger,
        detail: String? = null,
        trip: TripContext? = null,
        /** [DET-G-05][DET-SOLID-001] Typed evidence behind this arm. GEOFENCE_EXIT passes the
         *  verifier's result; MANUAL passes [ArmEvidence.Manual] (the default). */
        armEvidence: ArmEvidence = ArmEvidence.Manual,
    ) {
        logArmTrigger(trigger, detail)
        PaparcarLogger.d(DIAG, "  ▶ startParkingDetection — launching coordinator (trigger=$trigger)")
        // [DET-READY-001c] Mark detection as actively running so the Home banner shows Monitoring.
        // Set synchronously here (not inside the coroutine) so a superseded old job's finally — which
        // only flips the flag when it is still the current job — never races this to false.
        detectionRuntime.setRunning(true)
        // Publish the trip's origin AFTER setRunning(true) so the first Monitoring emission already
        // carries it. setRunning(true) does not touch the trip; setTrip(null) clears any stale origin
        // from a previous trip (manual start). Set after — never before cancelDetectionJob — so a
        // superseded job's finally (which clears on setRunning(false)) can't wipe it. [DEPART-CONSISTENCY-001]
        detectionRuntime.setTrip(trip)
        detectionJob = lifecycleScope.launch {
            val thisJob = coroutineContext[Job]

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
                    observeAdaptiveLocation(),
                    armEvidence = armEvidence,
                )
                PaparcarLogger.d(DIAG, "    ✓ coordinator returned NORMALLY")
            } catch (e: CancellationException) {
                PaparcarLogger.d(DIAG, "    ✗ detection cancelled: ${e.message}")
                throw e
            } catch (e: Exception) {
                PaparcarLogger.e(DIAG, "    ✗ detection error", e)
                notificationPort.showDebug("Detection error: ${e.message}")
            } finally {
                // Skip stopSelf when this job has been superseded by a newer detection job
                // (START_TRACKING / IN_VEHICLE_ENTER replacement). Calling stopSelf here would
                // destroy the service after the replacement coordinator was just launched,
                // killing it via onDestroy. [DETECT-SERVICE-RACE-001]
                if (detectionJob === thisJob) {
                    // [DET-READY-001c] This job is the current one and is ending → detection idle.
                    detectionRuntime.setRunning(false)
                    PaparcarLogger.d(DIAG, "    ■ finally → stopForegroundAndSelf()")
                    fgs.stopForegroundAndSelf() // [FIX BUG-FGS-100]
                } else {
                    PaparcarLogger.d(DIAG, "    ■ finally → superseded by newer job, skipping stop")
                }
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
            val msg = "Coordinator armado por: ${trigger.name}" + (detail?.let { " · $it" } ?: "")
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
     * Centralised location-permission gate. Returns `false` (and stops the service cleanly)
     * when the user has revoked location access since the service was first scheduled — covers
     * every entry path: explicit START, IN_VEHICLE PendingIntent delivery, and Activity
     * Recognition fallback. Caller should `return` so the framework does not restart us.
     *
     * [FIX BUG-FGS-104: route through stopForegroundAndSelf so the FGS detection notification
     *  is removed before the service is destroyed; the separate permission-revoked notification
     *  (different ID) is unaffected.]
     */
    private fun guardPermissions(actionLabel: String): Boolean {
        if (hasRequiredPermissions()) return true
        PaparcarLogger.w(DIAG, "  ✗ $actionLabel aborted — missing location permission")
        notificationPort.showPermissionRevoked()
        fgs.stopForegroundAndSelf()
        return false
    }

    override fun onDestroy() {
        PaparcarLogger.d(DIAG, "■ Service onDestroy — cancelling detectionJob")
        detectionJob?.cancel()
        detectionRuntime.setRunning(false) // [DET-READY-001c] service gone → detection idle
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
        /** m/s → km/h factor for the one-shot exit-speed sample. [DET-SOLID-001] */
        private const val KMH_PER_MPS = 3.6f

        const val ACTION_START_TRACKING = "io.apptolast.paparcar.ACTION_START_TRACKING"
        const val ACTION_STOP_TRACKING = "io.apptolast.paparcar.ACTION_STOP_TRACKING"
        // [DET-G-01] Geofence-exit delivered directly to the service via getForegroundService so
        // Play Services grants the privileged FGS start (the same getForegroundService mechanism the
        // AR IN_VEHICLE path used before AR was moved to a plain broadcast — BUG-FGS-001).
        const val ACTION_GEOFENCE_EXIT = "io.apptolast.paparcar.ACTION_GEOFENCE_EXIT"
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
