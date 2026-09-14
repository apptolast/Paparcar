package com.rndeveloper.paparcar.bluetooth

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.apptolast.baselogin.domain.AuthRepository
import com.rndeveloper.paparcar.detection.service.ForegroundPromotion
import com.rndeveloper.paparcar.detection.service.ForegroundServiceController
import com.rndeveloper.paparcar.detection.worker.ParkingSafetyNetWorker
import com.rndeveloper.paparcar.domain.model.displayName
import com.rndeveloper.paparcar.domain.notification.AppNotificationManager
import com.rndeveloper.paparcar.detection.worker.DepartureDetectionWorker
import com.rndeveloper.paparcar.domain.repository.UserParkingRepository
import com.rndeveloper.paparcar.domain.repository.VehicleRepository
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import com.rndeveloper.paparcar.notification.ForegroundNotificationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * ForegroundService that owns the long-running scope for [BluetoothParkingDetector].
 *
 * **Why a dedicated Service?**
 * BT parking detection can take up to ~5 minutes: 30 s debounce + 60 s GPS sampling +
 * the time the user needs to walk ≥ 30 m. Android will kill a background process in that
 * window. A ForegroundService with `foregroundServiceType="location"` keeps the process
 * alive and grants continuous GPS access.
 *
 * **Lifecycle contract:**
 * - [ACTION_BT_DISCONNECTED] — started via `startForegroundService()` from
 *   [BluetoothConnectionReceiver]. Calls `fgs.promote()`, launches detection in
 *   [lifecycleScope], and tears down via `fgs.stopForegroundAndSelf()` when detection
 *   completes.
 * - [ACTION_BT_CONNECTED] — two shapes since [DET-A-BT-CONNECT-IS-A-DEPARTURE-001], and the
 *   receiver picks which by asking whether that car still holds a plaza:
 *     - **nothing held** → started via `startService()` (NOT foreground; work is instant). Cancels
 *       any active detection job, accelerates the safety net and tears down — the original
 *       behaviour, now the minority case.
 *     - **plaza held** → started via `startForegroundService()`, promotes, and watches whether the
 *       car measurably drives away ([BluetoothDepartureDetector]). A measured drive-away is handed
 *       to the common departure door; measuring nothing stands down in silence.
 *   Either way, if the Service is already running as foreground (detection active), this
 *   `onStartCommand` is called on the same instance — the pending job is cancelled first.
 *
 * **Cancellation and the BT debounce:**
 * [BluetoothParkingDetector.detectParking] starts with a [kotlinx.coroutines.delay]. If BT
 * reconnects before the debounce expires, the [ACTION_BT_CONNECTED] handler cancels
 * [detectionJob] — [delay] is a cancellation point, so the coroutine aborts cooperatively
 * without any explicit "reconnect" flag inside the detector. [BT-REFACTOR-FGS-001]
 *
 * **START_NOT_STICKY:**
 * In-memory state (GPS parking fix, detection job) cannot survive a process kill.
 * A sticky restart with a null intent would find no vehicleId or fix coordinates and would
 * be meaningless — better to miss one detection event than to act on invalid state.
 *
 * **Notification ID:**
 * Uses [AppNotificationManager.BT_DETECTION_NOTIFICATION_ID] (1003) — distinct from
 * [AppNotificationManager.DETECTION_NOTIFICATION_ID] (1001) used by [CoordinatorDetectionService]
 * so both services can run their notifications independently without overwriting each other.
 *
 * **Refactor 2026-06-08 [BT-BUG-100..105 + BT-REFACTOR-200]:**
 *  - Reuses [ForegroundServiceController] so every teardown path goes through
 *    `stopForeground(STOP_FOREGROUND_REMOVE)` (no more leaked FGS notification).
 *  - `thisJob === detectionJob` guard prevents a superseded job from killing a
 *    replacement coordinator (`DETECT-SERVICE-RACE-001` ported from CoordinatorDetectionService).
 *  - Vehicle-name fetch moved INSIDE the detection job (no more side-launch race with
 *    `updateDetectionVehicle.notify` re-posting the notification after teardown).
 *  - Vehicle name resolved by `vehicleId` (the actually disconnected vehicle), not by
 *    `observeActiveVehicle` (which returns the user's *default* in multi-vehicle setups).
 *  - `onDestroy` safety net removes the FGS notification defensively.
 */
class BluetoothDetectionService : LifecycleService() {

    private val foregroundNotificationProvider: ForegroundNotificationProvider by inject()
    private val notificationPort: AppNotificationManager by inject()
    private val detector: BluetoothParkingDetector by inject()
    // [DET-A-BT-CONNECT-IS-A-DEPARTURE-001] The departure twin of [detector].
    private val departureDetector: BluetoothDepartureDetector by inject()
    private val userParkingRepository: UserParkingRepository by inject()
    private val vehicleRepository: VehicleRepository by inject()
    private val authRepository: AuthRepository by inject()

    // [BT-REFACTOR-200] one controller per Service instance, centralises FGS lifecycle.
    private val fgs by lazy { ForegroundServiceController(this) }

    // Main-thread-only — lifecycleScope's default dispatcher is Main.immediate.
    @Volatile private var detectionJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        PaparcarLogger.d(DIAG, "▶ Service onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        PaparcarLogger.d(DIAG, "▶ onStartCommand action=${intent?.action}")

        when (intent?.action) {
            ACTION_BT_DISCONNECTED -> handleDisconnected(intent)
            ACTION_BT_CONNECTED -> handleConnected(intent)
            null -> {
                // START_NOT_STICKY means this should never be delivered, but guard defensively.
                PaparcarLogger.w(DIAG, "  ✗ null intent (unexpected restart) — stopForegroundAndSelf")
                fgs.stopForegroundAndSelf() // [FIX BT-BUG-100]
            }
        }

        return START_NOT_STICKY
    }

    private fun handleDisconnected(intent: Intent) {
        val deviceAddress = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
        val vehicleId = intent.getStringExtra(EXTRA_VEHICLE_ID)
        if (deviceAddress.isNullOrBlank() || vehicleId.isNullOrBlank()) {
            PaparcarLogger.w(DIAG, "  ✗ BT_DISCONNECTED — missing extras (deviceAddress=$deviceAddress, vehicleId=$vehicleId), stop")
            // startForegroundCompat has NOT been called yet — plain stopSelf is fine, but
            // route through fgs for consistency. The internal stopForeground is a no-op
            // because we never promoted. [FIX BT-BUG-100]
            fgs.stopForegroundAndSelf()
            return
        }

        // Must promote before any suspending work — Android 8+ enforces the 5-second
        // window from startForegroundService(). [BUG-FGS-001]
        //
        // [DET-A-LOCATION-FGS-MUST-NOT-KILL-THE-APP-001] This lane used to hard-code
        // `withLocationPermission = true` — it did not check the permission at all, so on Android 14+
        // a BT disconnect with location revoked took the app down exactly like the Coordinator's
        // ACTION_RESUME_SENTRY did. The whole point of this service is a GPS fix; without the
        // permission there is no detection to run, so stand down and let the user be told.
        val promotion = fgs.promote(
            notificationId = AppNotificationManager.BT_DETECTION_NOTIFICATION_ID,
            notification = foregroundNotificationProvider.buildDetectionNotification(),
            hasLocationPermission = hasLocationPermission(),
        )
        if (!promotion.isPromoted) {
            PaparcarLogger.e(DIAG, "  ✗ FGS promote refused (outcome=$promotion) — BT detection stands down")
            if (promotion == ForegroundPromotion.REFUSED_MISSING_LOCATION_PERMISSION) {
                runCatching { notificationPort.showPermissionRevoked() }
                    .onFailure { e -> PaparcarLogger.w(DIAG, "  ⚠ permission-revoked notification failed: ${e.message}") }
            }
            fgs.stopForegroundAndSelf()
            return
        }
        PaparcarLogger.d(DIAG, "  ✓ startForeground done (notif ${AppNotificationManager.BT_DETECTION_NOTIFICATION_ID})")

        PaparcarLogger.d(DIAG, "  → BT_DISCONNECTED device=$deviceAddress vehicle=$vehicleId — launching detector")
        detectionJob?.cancel()
        detectionJob = lifecycleScope.launch {
            val thisJob = coroutineContext[Job]

            // [FIX BT-BUG-103 + BT-BUG-104] Resolve the vehicle name INSIDE the detection job
            // (same lifetime as detectionJob, no side-launch race) AND by the vehicleId that
            // actually disconnected (not by observeActiveVehicle, which would return the
            // user's default vehicle in multi-vehicle setups).
            runCatching {
                val userId = authRepository.getCurrentSession()?.userId
                val name = userId
                    ?.let { vehicleRepository.getVehicleById(it, vehicleId) }
                    ?.let { it.displayName(fallback = "").takeIf { n -> n.isNotBlank() } }
                if (name != null) {
                    notificationPort.updateDetectionVehicle(
                        name,
                        AppNotificationManager.BT_DETECTION_NOTIFICATION_ID,
                    )
                }
            }.onFailure { e ->
                PaparcarLogger.w(DIAG, "    ⚠ vehicle-name fetch failed: ${e.message}")
            }

            try {
                // [DET-BT-DISCONNECT-WITHOUT-RIDE-001] The matching ACL_CONNECTED stamp, so the
                // detector can size the engagement. Read here rather than inside the detector so
                // the decision core stays free of Android Context. Null = no connect on record
                // (process installed mid-engagement, prefs cleared) — the evaluator treats that
                // as doubt, not as permission.
                val connectedAtMs = BtConnectionStore.lastConnectedAt(
                    this@BluetoothDetectionService,
                    vehicleId,
                )
                // [DET-BT-A-RIDE-IS-MEASURED-NOT-TIMED-001] …and WHERE that connect happened, the
                // origin the ride is measured from. Read next to its timestamp because the two are
                // one fact; same reason it stays out of the detector's decision core.
                val connectFix = BtConnectionStore.lastConnectFix(
                    this@BluetoothDetectionService,
                    vehicleId,
                )
                detector.detectParking(deviceAddress, vehicleId, connectedAtMs, connectFix)
                PaparcarLogger.d(DIAG, "  ✓ detectParking returned normally")
                // [DET-RETURN-ANCHOR-001] Seal the anchor for the just-confirmed session right
                // away (the coordinator path gets this via its detection-end check): the user is
                // still ~30 m from the car, well inside the fence, so the check writes the
                // time+steps zero-point the step-budget will need hours later.
                ParkingSafetyNetWorker.enqueueCheckNow(
                    WorkManager.getInstance(this@BluetoothDetectionService),
                    source = ParkingSafetyNetWorker.SOURCE_BT_PARK,
                )
            } catch (e: CancellationException) {
                PaparcarLogger.d(DIAG, "  ✗ detection cancelled (BT reconnect or destroy)")
                throw e
            } catch (e: Exception) {
                PaparcarLogger.e(DIAG, "  ✗ detection error", e)
                notificationPort.showDebug("ERROR en la detección Bluetooth (${e.message}): la sesión se aborta SIN pin → el próximo evento del coche (conectar/desconectar) vuelve a intentarlo")
            } finally {
                // [FIX BT-BUG-101] Race guard: skip teardown when this job has been
                // superseded by a newer detection job (e.g. a new BT_DISCONNECTED arrived
                // while we were finishing). Without this, the older job would tear down
                // the FGS that the replacement just promoted. Mirrors `DETECT-SERVICE-RACE-001`
                // from CoordinatorDetectionService.
                if (detectionJob === thisJob) {
                    PaparcarLogger.d(DIAG, "  ■ detection finished — stopForegroundAndSelf()")
                    fgs.stopForegroundAndSelf() // [FIX BT-BUG-100]
                } else {
                    PaparcarLogger.d(DIAG, "  ■ detection finished — superseded by newer job, skipping stop")
                }
            }
        }
    }

    private fun handleConnected(intent: Intent) {
        val deviceAddress = intent.getStringExtra(EXTRA_DEVICE_ADDRESS) ?: ""
        val vehicleId = intent.getStringExtra(EXTRA_VEHICLE_ID)
        val watchDeparture = intent.getBooleanExtra(EXTRA_WATCH_DEPARTURE, false)
        PaparcarLogger.d(DIAG, "  → BT_CONNECTED device=$deviceAddress vehicle=$vehicleId watch=$watchDeparture")
        detectionJob?.cancel()
        detectionJob = null
        // [DET-RETURN-ANCHOR-001] BT CONNECT is the DETERMINISTIC "back at my car" signal — the
        // receiver already gated it to the vehicle's paired MAC, and it fires the moment the
        // engine turns on, immune to the Doze/latency that delays the twin ENTER fence. The
        // gated check re-seals the position anchor + cures the fence state seconds before the
        // drive-away, so the departure resolves live (or via step-budget) instead of dying to a
        // stale anchor as on 2026-07-07. Accelerator only — the evaluator still decides.
        //
        // [DET-A-BT-CONNECT-IS-A-DEPARTURE-001] It stays, and it is NOT what this ticket added:
        // this check runs while the user is still AT the car, so it can prepare the ground for a
        // departure and can never witness one. Preparation and observation are different jobs.
        ParkingSafetyNetWorker.enqueueCheckNow(
            WorkManager.getInstance(this),
            source = ParkingSafetyNetWorker.SOURCE_BT_CONNECT,
        )

        // [DET-A-BT-CONNECT-IS-A-DEPARTURE-001] Nothing held by this car → the CONNECT is the
        // instant work it always was. The receiver started us with a plain `startService` in this
        // case, so there is no promotion owed and `stopForegroundAndSelf` is a clean stop.
        if (!watchDeparture || vehicleId.isNullOrBlank()) {
            PaparcarLogger.d(DIAG, "  ■ no plaza held by this car — nothing to watch, stopForegroundAndSelf()")
            fgs.stopForegroundAndSelf()
            return
        }
        watchForDeparture(vehicleId)
    }

    /**
     * [DET-A-BT-CONNECT-IS-A-DEPARTURE-001] The user boarded a car that still holds a plaza. Watch
     * whether it actually drives away, and hand a measured departure to the common door.
     *
     * ⚠️ We were started with `startForegroundService`, so **every path out of here must have
     * promoted or stopped** — that start is a 5-second promise to the OS (the BUG-FGS-001 class).
     */
    private fun watchForDeparture(vehicleId: String) {
        val promotion = fgs.promote(
            notificationId = AppNotificationManager.BT_DETECTION_NOTIFICATION_ID,
            notification = foregroundNotificationProvider.buildDetectionNotification(),
            hasLocationPermission = hasLocationPermission(),
        )
        if (!promotion.isPromoted) {
            // Same posture as the disconnect lane: without the permission there is no watch to run.
            // The departure is not lost, it degrades to the geofence EXIT and the safety net —
            // which is exactly where it lived before this lane existed.
            PaparcarLogger.e(DIAG, "  ✗ FGS promote refused (outcome=$promotion) — drive-away watch stands down")
            if (promotion == ForegroundPromotion.REFUSED_MISSING_LOCATION_PERMISSION) {
                runCatching { notificationPort.showPermissionRevoked() }
                    .onFailure { e -> PaparcarLogger.w(DIAG, "  ⚠ permission-revoked notification failed: ${e.message}") }
            }
            fgs.stopForegroundAndSelf()
            return
        }

        detectionJob = lifecycleScope.launch {
            val thisJob = coroutineContext[Job]
            try {
                // Re-read inside the job: the receiver's answer is a routing decision taken a moment
                // ago, and between there and here the departure may already have been processed by
                // the geofence. A vanished session is not an error, it is the race resolving the
                // other way.
                val session = userParkingRepository.getActiveSessionByVehicle(vehicleId)
                if (session == null) {
                    PaparcarLogger.d(DIAG, "  ■ the plaza is gone (another lane closed it first) — nothing to watch")
                    return@launch
                }
                // The measured instant of boarding. Null means the stamp is missing (process
                // installed mid-engagement, prefs cleared): without it the departure has no hour,
                // and an undated departure publishes nothing downstream anyway
                // [DET-AN-EXIT-IS-DATED-BY-THE-FIX-THAT-CROSSED-IT-001]. Don't hold a GPS watch to
                // produce an answer that cannot be used.
                val connectedAtMs = BtConnectionStore.lastConnectedAt(this@BluetoothDetectionService, vehicleId)
                if (connectedAtMs == null) {
                    PaparcarLogger.w(DIAG, "  ⊘ no CONNECT stamp for vehicle=$vehicleId — the departure would have no hour, standing down")
                    return@launch
                }

                when (val outcome = departureDetector.watchForDeparture(session, connectedAtMs)) {
                    is BtDepartureOutcome.Measured -> {
                        val geofenceId = session.geofenceId
                        if (geofenceId == null) {
                            PaparcarLogger.w(DIAG, "  ⊘ session ${session.id.take(8)} has no fence — the common door is keyed by fence, nothing to dispatch")
                            return@launch
                        }
                        // The common door, the same one the geofence EXIT uses — REPLACE keyed by
                        // fence, so if Play Services also emits, the two collapse into one dispatch
                        // instead of publishing twice.
                        //
                        // `preconfirmed = false` deliberately: preconfirmed means "the trip already
                        // ENDED, do not re-decide by live speed", and it carries DepartureProof
                        // .Deduced → a provisional 12-minute plaza with the session retained. Here
                        // the trip is STARTING, so letting the ladder take its own fresh fix makes
                        // it Witnessed — full lifetime, car released. This watch does not replace
                        // the ladder; it gives it the right moment to look.
                        WorkManager.getInstance(this@BluetoothDetectionService).enqueueUniqueWork(
                            "${DepartureDetectionWorker.TAG}_$geofenceId",
                            ExistingWorkPolicy.REPLACE,
                            DepartureDetectionWorker.buildRequest(
                                geofenceId = geofenceId,
                                triggerAtMs = System.currentTimeMillis(),
                                // Dated by the BOARDING, not by the moment the drive became
                                // measurable: the plaza was freed when the car pulled out, and the
                                // CONNECT is the measured instant closest to it.
                                departedAtMs = connectedAtMs,
                                preconfirmed = false,
                            ),
                        )
                        PaparcarLogger.i(DIAG, "  ✓ departure dispatched for fence=${geofenceId.take(8)} (proof at ${outcome.fix.timestamp})")
                    }
                    BtDepartureOutcome.NeverDrove -> Unit // already traced by the detector
                }
            } catch (e: CancellationException) {
                PaparcarLogger.d(DIAG, "  ✗ drive-away watch cancelled (BT disconnect or destroy)")
                throw e
            } catch (e: Exception) {
                PaparcarLogger.e(DIAG, "  ✗ drive-away watch error", e)
            } finally {
                // [FIX BT-BUG-101] Same race guard as the disconnect lane: a newer job (the
                // DISCONNECT that ends this very trip) must not have its FGS torn down by this one.
                if (detectionJob === thisJob) {
                    fgs.stopForegroundAndSelf()
                } else {
                    PaparcarLogger.d(DIAG, "  ■ watch finished — superseded by newer job, skipping stop")
                }
            }
        }
    }

    /**
     * [DET-A-LOCATION-FGS-MUST-NOT-KILL-THE-APP-001] What the SYSTEM checks before allowing a
     * `location`-typed foreground service: either coarse or fine. Deliberately not the Coordinator's
     * stricter fine + background gate — that one says whether detection can usefully RUN, this one
     * says whether the promotion can be granted at all, and conflating them would refuse a promotion
     * the OS would have allowed.
     */
    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        PaparcarLogger.d(DIAG, "■ Service onDestroy — cancelling detectionJob")
        detectionJob?.cancel()
        // [BT-REFACTOR-200] Defensive safety net (ports BUG-FGS-113 fix). Idempotent: a
        // redundant stopForeground after the notification is already gone is a documented
        // no-op on every Android version we ship to.
        runCatching { fgs.removeForegroundNotification() }
            .onFailure { e -> PaparcarLogger.w(DIAG, "  ⚠ onDestroy stopForeground failed: ${e.message}") }
        super.onDestroy()
        PaparcarLogger.d(DIAG, "■ Service onDestroy DONE")
    }

    companion object {
        const val ACTION_BT_DISCONNECTED = "com.rndeveloper.paparcar.ACTION_BT_DISCONNECTED"
        const val ACTION_BT_CONNECTED = "com.rndeveloper.paparcar.ACTION_BT_CONNECTED"
        const val EXTRA_DEVICE_ADDRESS = "extra_device_address"
        const val EXTRA_VEHICLE_ID = "extra_vehicle_id"
        /** [DET-A-BT-CONNECT-IS-A-DEPARTURE-001] Set by the receiver on a CONNECT when the car
         *  still holds a plaza. It is also the record of HOW we were started: true means
         *  , i.e. a promotion is owed. */
        const val EXTRA_WATCH_DEPARTURE = "extra_watch_departure"
        /** PARKDIAG prefix: FileAntilog only persists PARKDIAG-tagged lines (was BTDIAG —
         *  invisible in field captures). */
        private const val DIAG = "PARKDIAG/BTService"
    }
}
