package io.apptolast.paparcar.fakes

import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.service.ParkingSyncScheduler

class FakeParkingSyncScheduler : ParkingSyncScheduler {

    data class Call(val session: UserParking, val previousSessionId: String?)

    private val _calls = mutableListOf<Call>()
    val calls: List<Call> get() = _calls.toList()
    val scheduleCallCount: Int get() = _calls.size

    override fun schedule(session: UserParking, previousSessionId: String?) {
        _calls += Call(session, previousSessionId)
    }
}
