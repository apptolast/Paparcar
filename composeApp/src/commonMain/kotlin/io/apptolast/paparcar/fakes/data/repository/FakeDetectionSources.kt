package io.apptolast.paparcar.fakes.data.repository

import io.apptolast.paparcar.domain.ActivityRecognitionManager
import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.PlaceInfo
import io.apptolast.paparcar.domain.model.SpotType
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.sensor.StepDetectorSource
import io.apptolast.paparcar.domain.service.*
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

class FakeGeofenceManager : GeofenceManager {
    override suspend fun createGeofence(geofenceId: String, latitude: Double, longitude: Double, radiusMeters: Float): Result<Unit> =
        Result.success(Unit)

    override suspend fun removeGeofence(geofenceId: String): Result<Unit> = Result.success(Unit)

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
    override fun enqueueReportSpot(spotId: String, lat: Double, lon: Double, address: AddressInfo?, placeInfo: PlaceInfo?, spotType: SpotType, confidence: Float, sizeCategory: VehicleSize?, reporterName: String?) {}
}
