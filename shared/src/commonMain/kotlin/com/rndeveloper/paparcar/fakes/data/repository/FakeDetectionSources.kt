package com.rndeveloper.paparcar.fakes.data.repository

import com.rndeveloper.paparcar.domain.ActivityRecognitionManager
import com.rndeveloper.paparcar.domain.detection.ports.ArrivalHandoffDetection
import com.rndeveloper.paparcar.domain.detection.ports.DepartureWatchResumer
import com.rndeveloper.paparcar.domain.detection.ports.ManualParkingDetection
import com.rndeveloper.paparcar.domain.detection.MutableDetectionRuntimeState
import com.rndeveloper.paparcar.domain.detection.ServicePresence
import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.CarbodyType
import com.rndeveloper.paparcar.domain.model.PlaceInfo
import com.rndeveloper.paparcar.domain.model.SpotType
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.domain.sensor.StepDetectorSource
import com.rndeveloper.paparcar.domain.service.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow

class FakeActivityRecognitionManager : ActivityRecognitionManager {
    override fun registerTransitions() {}
    override fun unregisterTransitions() {}
}

class FakeStepDetectorSource : StepDetectorSource {
    override fun steps(): Flow<Unit> = emptyFlow()
}

/**
 * Mock "I'm driving": with no Coordinator service to launch, it flips the shared [runtime] flag to
 * Monitoring so Home reacts as if a trip started (driving puck + chip + follow). Null runtime keeps
 * the old no-op for tests. [DRIVE-SIM-001]
 */
class FakeManualParkingDetection(
    private val runtime: MutableDetectionRuntimeState? = null,
) : ManualParkingDetection {
    var startCallCount = 0
        private set
    var stopCallCount = 0
        private set
    var stopByUserCallCount = 0
        private set

    override fun start() {
        startCallCount++
        runtime?.setRunning(true)
    }

    override fun stop() {
        stopCallCount++
        runtime?.setRunning(false)
    }

    /** [DET-STOP-BUTTON-001] Mock "Parar detección": same visible effect in the gallery (the trip
     *  ends and Home leaves Monitoring), counted apart so tests can tell the two commands apart. */
    override fun stopByUser() {
        stopByUserCallCount++
        runtime?.setRunning(false)
    }

    /** [DET-ASK-STATE-001] Mock answer to the "did you park?" row. In the gallery both answers end
     *  the trip (there is no coordinator to keep following it); the recorded answers let tests
     *  assert WHICH one the row sent. */
    override fun answerPrompt(parked: Boolean) {
        promptAnswers += parked
        runtime?.setRunning(false)
    }

    /** Every answer the row has sent, in order — so a test can prove the two buttons are not wired
     *  to the same command. */
    val promptAnswers = mutableListOf<Boolean>()
}

/**
 * [DET-HANDOFF-NOT-MANUAL-001] Mock arrival handoff. Separate from [FakeManualParkingDetection] for
 * the same reason the real ports are separate: a test that asserts "the user started this" must not
 * pass because the safety net did.
 */
class FakeArrivalHandoffDetection(
    private val runtime: MutableDetectionRuntimeState? = null,
) : ArrivalHandoffDetection {
    var startCallCount = 0
        private set

    override fun start() {
        startCallCount++
        runtime?.setRunning(true)
    }
}

/**
 * Mock "reactivate the departure watch": with no Coordinator service to resurrect, it flips the
 * shared [runtime] presence to [com.rndeveloper.paparcar.domain.detection.ServicePresence.Sentry] so the
 * interrupted-watch row heals in the gallery exactly as it does on device. [DET-WATCH-REACTIVATE-001]
 *
 * [resumeSucceeds] false reproduces the platform refusing the start — the case that must surface an
 * error instead of a mute button.
 */
class FakeDepartureWatchResumer(
    private val runtime: MutableDetectionRuntimeState? = null,
    var resumeSucceeds: Boolean = true,
) : DepartureWatchResumer {
    var resumeCallCount = 0
        private set
    var lastSource: String? = null
        private set
    var lastForce: Boolean? = null
        private set

    override suspend fun resume(source: String, force: Boolean): Boolean {
        resumeCallCount++
        lastSource = source
        lastForce = force
        if (resumeSucceeds) runtime?.setPresence(ServicePresence.Sentry)
        return resumeSucceeds
    }
}

class FakeGeofenceManager : GeofenceManager {
    override suspend fun createGeofence(geofenceId: String, latitude: Double, longitude: Double, radiusMeters: Float): Result<Unit> =
        Result.success(Unit)

    override suspend fun removeGeofence(geofenceId: String): Result<Unit> = Result.success(Unit)

    override suspend fun removeAllGeofences(): Result<Unit> = Result.success(Unit)

    override fun getGeofenceEvents(): Flow<GeofenceEvent> = emptyFlow()
}

class FakeDepartureEventBus : DepartureEventBus {
    override var lastVehicleEnteredAt: Long? = null
    override fun onVehicleEntered(timestampMs: Long) { lastVehicleEnteredAt = timestampMs }
    override fun reset() { lastVehicleEnteredAt = null }
}

class FakeGeofenceEventBus : GeofenceEventBus {
    private val _events = MutableSharedFlow<GeofenceEvent>()
    override val events: Flow<GeofenceEvent> = _events
    override fun emit(event: GeofenceEvent) {}
}

class FakeParkingEnrichmentScheduler : ParkingEnrichmentScheduler {
    override fun enqueueEnrichSession(sessionId: String, lat: Double, lon: Double) {}
}

class FakeParkingSyncScheduler : ParkingSyncScheduler {
    override fun enqueueSaveNewParkingSession(session: UserParking, previousSessionId: String?) {}
    override fun enqueueClearActiveParkingSession(sessionId: String) {}
    override fun enqueueUpdateParkingSessionAddressAndPlace(sessionId: String, address: AddressInfo?, placeInfo: PlaceInfo?) {}
}

class FakeReportSpotScheduler : ReportSpotScheduler {
    override fun enqueueReportSpot(spotId: String, lat: Double, lon: Double, address: AddressInfo?, placeInfo: PlaceInfo?, spotType: SpotType, confidence: Float, sizeCategory: VehicleSize?, carbodyType: CarbodyType?, reportedBy: String?, provisional: Boolean) {}
}
