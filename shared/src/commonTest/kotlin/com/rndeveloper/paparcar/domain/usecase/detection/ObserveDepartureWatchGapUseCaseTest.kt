package com.rndeveloper.paparcar.domain.usecase.detection

import com.rndeveloper.paparcar.domain.detection.MutableDetectionRuntimeState
import com.rndeveloper.paparcar.domain.detection.ServicePresence
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.domain.model.VehicleType
import com.rndeveloper.paparcar.domain.permissions.AppPermissionState
import com.rndeveloper.paparcar.fakes.FakeAppPreferences
import com.rndeveloper.paparcar.fakes.FakePermissionManager
import com.rndeveloper.paparcar.fakes.FakeUserParkingRepository
import com.rndeveloper.paparcar.fakes.FakeVehicleRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [DET-WATCH-REACTIVATE-001] */
class ObserveDepartureWatchGapUseCaseTest {

    @Test
    fun should_reportGap_when_coordinatorCarIsParkedAndServiceIsDead() = runTest {
        val gap = buildUseCase(presence = ServicePresence.Dead).invoke().first()
        assertTrue(gap, "a parked Coordinator car with a dead service IS an unwatched departure")
    }

    @Test
    fun should_reportNoGap_when_serviceIsResidentInSentry() = runTest {
        val gap = buildUseCase(presence = ServicePresence.Sentry).invoke().first()
        assertFalse(gap, "Sentry is the watch being alive — nothing to rebuild")
    }

    @Test
    fun should_reportNoGap_when_serviceIsTrackingATrip() = runTest {
        val gap = buildUseCase(presence = ServicePresence.Active).invoke().first()
        assertFalse(gap, "Active is a live tracking job — the watch is more than alive")
    }

    @Test
    fun should_reportNoGap_when_nothingIsParked() = runTest {
        val gap = buildUseCase(parked = false).invoke().first()
        assertFalse(gap, "with no session there is no departure to watch — never flash a purposeless FGS")
    }

    @Test
    fun should_reportNoGap_when_autoDetectionIsOff() = runTest {
        val gap = buildUseCase(autoDetectEnabled = false).invoke().first()
        assertFalse(gap, "the detection toggle governs residency — off means off")
    }

    @Test
    fun should_reportNoGap_when_bluetoothOwnsTheParkedCar() = runTest {
        // The ACL receiver wakes the process by itself, so a BT car needs no resident watcher.
        // [DET-STRATEGY-GATE-001]
        val gap = buildUseCase(btConnected = true).invoke().first()
        assertFalse(gap, "a Bluetooth-covered car is watched without a resident service")
    }

    @Test
    fun should_reportNoGap_when_activeVehicleNeverParks() = runTest {
        val gap = buildUseCase(vehicleType = VehicleType.BIKE).invoke().first()
        assertFalse(gap, "bikes never occupy a spot — the strategy is NONE, so there is no watch to rebuild")
    }

    @Test
    fun should_closeGap_when_theWatcherComesUp() = runTest {
        val runtime = MutableDetectionRuntimeState()
        val useCase = buildUseCase(runtime = runtime)
        assertTrue(useCase().first())

        runtime.setPresence(ServicePresence.Sentry)

        assertFalse(useCase().first(), "the gap must close by itself once the watcher is resident")
    }

    @Test
    fun should_openGap_when_theParkedSessionArrivesLate() = runTest {
        // THE regression: a clean install starts with an empty Room and the session only lands when
        // the Firestore sync completes. The predecessor self-heal read the sessions ONCE at process
        // start, saw nothing parked, and left the watch dead until the next launch (field 2026-08-14).
        val parkingRepo = FakeUserParkingRepository()
        val useCase = buildUseCase(parkingRepository = parkingRepo)
        assertFalse(useCase().first(), "nothing synced yet → nothing to watch")

        parkingRepo.saveNewParkingSession(session())

        assertTrue(useCase().first(), "the gap must open as soon as the synced session appears")
    }

    @Test
    fun should_notEmitDuplicates_when_unrelatedStateChanges() = runTest {
        val runtime = MutableDetectionRuntimeState()
        val useCase = buildUseCase(runtime = runtime)
        // Presence churn that never leaves "dead" collapses into a single emission.
        runtime.setRunning(true)
        assertEquals(true, useCase().first())
    }

    // ── closeableFromForeground(): the stream the foreground lane collects ─────────────────────
    // [DET-A-GRANTED-PERMISSION-MUST-REOPEN-THE-WATCH-001]

    @Test
    fun should_holdTheForegroundLaneClosed_when_locationIsNotGrantedYet() = runTest {
        // A clean install: the synced session opens the RAW gap before the user has granted
        // anything. Dispatching then is a guaranteed OS refusal, and the attempt would stamp the
        // resumer's cooldown — so the foreground lane must simply not fire yet.
        val closeable = buildUseCase().closeableFromForeground().first()
        assertFalse(closeable, "without the location permission a resume attempt is a guaranteed refusal")
    }

    @Test
    fun should_openTheForegroundLane_when_locationIsGrantedWhileTheGapIsOpen() = runTest {
        // THE regression: the user grants location from inside the app (map ask / checklist), none
        // of the raw gap's inputs change, and the watch stayed dead for the whole app session
        // (measured 2026-09-09, clean-install repro). The grant itself must be an emission.
        val permissions = FakePermissionManager()
        val useCase = buildUseCase(permissionManager = permissions)
        assertFalse(useCase.closeableFromForeground().first(), "nothing granted yet")

        permissions.emit(AppPermissionState(hasLocationPermission = true))

        assertTrue(
            useCase.closeableFromForeground().first(),
            "granting location while the gap is open must be the emission that resumes the watch",
        )
    }

    @Test
    fun should_holdTheForegroundLaneClosed_when_permissionIsGrantedButNothingIsParked() = runTest {
        val permissions = FakePermissionManager().apply { emit(AppPermissionState(hasLocationPermission = true)) }
        val closeable = buildUseCase(parked = false, permissionManager = permissions)
            .closeableFromForeground().first()
        assertFalse(closeable, "a permission without a gap is not a reason to start anything")
    }

    @Test
    fun should_keepTheRawGapPermissionBlind_when_locationIsNotGranted() = runTest {
        // The resumer's gate and the home-cta force path read the RAW gap on purpose: a gap without
        // permissions is still a real gap (Readiness reports it as Blocked), and a forced dispatch
        // must reach the service so it can answer with its honest refusal.
        val useCase = buildUseCase()
        assertTrue(useCase().first(), "the raw stream must not hide the gap behind permissions")
        assertTrue(useCase.current(), "the one-shot read must agree with the raw stream")
    }

    // ── current(): the one-shot read the resumer uses [DET-WATCH-RESUME-RACE-001] ──────────────

    @Test
    fun should_answerTheSameAsTheStream_when_readOnce() = runTest {
        // The whole point of current(): the resumer must not be able to disagree with the stream that
        // woke it up. On device it did — it re-derived the verdict from Room and answered "nothing
        // parked" 100 ms after the stream had reported a gap (field 2026-08-16, Oppo).
        val watching = buildUseCase()
        assertEquals(watching().first(), watching.current())

        val idle = buildUseCase(parked = false)
        assertEquals(idle().first(), idle.current())
    }

    @Test
    fun should_reportTheGap_when_readOnceWithACoordinatorCarParked() = runTest {
        assertTrue(buildUseCase().current(), "a parked Coordinator car with a dead service IS a gap")
    }

    @Test
    fun should_reportNoGap_when_readOnceWithNothingParked() = runTest {
        assertFalse(buildUseCase(parked = false).current(), "no session, no watcher to rebuild")
    }

    @Test
    fun should_reportNoGap_when_readOnceWithTheWatcherAlreadyResident() = runTest {
        assertFalse(
            buildUseCase(presence = ServicePresence.Sentry).current(),
            "the watcher is alive — resuming it again would be a purposeless service start",
        )
    }

    @Test
    fun should_seeTheSession_when_readAfterTheSyncLands() = runTest {
        val parkingRepo = FakeUserParkingRepository()
        val useCase = buildUseCase(parkingRepository = parkingRepo)
        assertFalse(useCase.current(), "nothing synced yet → nothing to watch")

        parkingRepo.saveNewParkingSession(session())

        assertTrue(useCase.current(), "the very next read must see the session the sync brought")
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private fun buildUseCase(
        parked: Boolean = true,
        autoDetectEnabled: Boolean = true,
        btConnected: Boolean = false,
        vehicleType: VehicleType = VehicleType.CAR,
        presence: ServicePresence = ServicePresence.Dead,
        runtime: MutableDetectionRuntimeState = MutableDetectionRuntimeState().apply { setPresence(presence) },
        parkingRepository: FakeUserParkingRepository = FakeUserParkingRepository(
            initialSession = if (parked) session() else null,
        ),
        permissionManager: FakePermissionManager = FakePermissionManager(),
    ): ObserveDepartureWatchGapUseCase {
        val vehicle = Vehicle(
            id = VEHICLE_ID,
            userId = "u-1",
            sizeCategory = VehicleSize.MEDIUM_SUV,
            vehicleType = vehicleType,
            isActive = true,
            bluetoothDeviceId = if (btConnected) "AA:BB:CC:DD:EE:FF" else null,
        )
        val vehicleRepository = FakeVehicleRepository(defaultVehicle = vehicle)
        return ObserveDepartureWatchGapUseCase(
            userParkingRepository = parkingRepository,
            vehicleRepository = vehicleRepository,
            appPreferences = FakeAppPreferences(initialAutoDetect = autoDetectEnabled),
            detectionRuntime = runtime,
            permissionManager = permissionManager,
        )
    }

    private fun session() = UserParking(
        id = "s-1",
        userId = "u-1",
        vehicleId = VEHICLE_ID,
        location = GpsPoint(latitude = 40.0, longitude = -3.7, accuracy = 0f, timestamp = 0L, speed = 0f),
        geofenceId = "geof-1",
        isActive = true,
    )

    private companion object {
        const val VEHICLE_ID = "v-1"
    }
}
