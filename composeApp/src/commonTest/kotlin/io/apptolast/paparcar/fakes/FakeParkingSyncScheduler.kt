package io.apptolast.paparcar.fakes

import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.PlaceInfo
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.service.ParkingSyncScheduler

class FakeParkingSyncScheduler : ParkingSyncScheduler {

    data class ScheduleCall(val session: UserParking, val previousSessionId: String?)
    data class ClearActiveCall(val sessionId: String)
    data class LocationUpdateCall(val sessionId: String, val address: AddressInfo?, val placeInfo: PlaceInfo?)

    private val _scheduleCalls = mutableListOf<ScheduleCall>()
    val scheduleCalls: List<ScheduleCall> get() = _scheduleCalls.toList()
    val scheduleCallCount: Int get() = _scheduleCalls.size

    private val _clearActiveCalls = mutableListOf<ClearActiveCall>()
    val clearActiveCalls: List<ClearActiveCall> get() = _clearActiveCalls.toList()

    private val _locationUpdateCalls = mutableListOf<LocationUpdateCall>()
    val locationUpdateCalls: List<LocationUpdateCall> get() = _locationUpdateCalls.toList()

    override fun schedule(session: UserParking, previousSessionId: String?) {
        _scheduleCalls += ScheduleCall(session, previousSessionId)
    }

    override fun scheduleClearActive(sessionId: String) {
        _clearActiveCalls += ClearActiveCall(sessionId)
    }

    override fun scheduleLocationUpdate(sessionId: String, address: AddressInfo?, placeInfo: PlaceInfo?) {
        _locationUpdateCalls += LocationUpdateCall(sessionId, address, placeInfo)
    }
}
