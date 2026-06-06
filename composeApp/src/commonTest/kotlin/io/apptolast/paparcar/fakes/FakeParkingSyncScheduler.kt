package io.apptolast.paparcar.fakes

import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.PlaceInfo
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.service.ParkingSyncScheduler

class FakeParkingSyncScheduler : ParkingSyncScheduler {

    data class SaveNewParkingSessionCall(val session: UserParking, val previousSessionId: String?)
    data class ClearActiveParkingSessionCall(val sessionId: String)
    data class UpdateParkingSessionAddressAndPlaceCall(val sessionId: String, val address: AddressInfo?, val placeInfo: PlaceInfo?)

    private val _saveNewParkingSessionCalls = mutableListOf<SaveNewParkingSessionCall>()
    val saveNewParkingSessionCalls: List<SaveNewParkingSessionCall> get() = _saveNewParkingSessionCalls.toList()
    val saveNewParkingSessionCallCount: Int get() = _saveNewParkingSessionCalls.size

    private val _clearActiveParkingSessionCalls = mutableListOf<ClearActiveParkingSessionCall>()
    val clearActiveParkingSessionCalls: List<ClearActiveParkingSessionCall> get() = _clearActiveParkingSessionCalls.toList()

    private val _updateParkingSessionAddressAndPlaceCalls = mutableListOf<UpdateParkingSessionAddressAndPlaceCall>()
    val updateParkingSessionAddressAndPlaceCalls: List<UpdateParkingSessionAddressAndPlaceCall> get() = _updateParkingSessionAddressAndPlaceCalls.toList()

    override fun enqueueSaveNewParkingSession(session: UserParking, previousSessionId: String?) {
        _saveNewParkingSessionCalls += SaveNewParkingSessionCall(session, previousSessionId)
    }

    override fun enqueueClearActiveParkingSession(sessionId: String) {
        _clearActiveParkingSessionCalls += ClearActiveParkingSessionCall(sessionId)
    }

    override fun enqueueUpdateParkingSessionAddressAndPlace(sessionId: String, address: AddressInfo?, placeInfo: PlaceInfo?) {
        _updateParkingSessionAddressAndPlaceCalls += UpdateParkingSessionAddressAndPlaceCall(sessionId, address, placeInfo)
    }
}
