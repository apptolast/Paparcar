package com.rndeveloper.paparcar.fakes

import com.rndeveloper.paparcar.domain.detection.DetectionPhase
import com.rndeveloper.paparcar.domain.detection.DetectionPhaseSink
import com.rndeveloper.paparcar.domain.usecase.parking.FinalizeDeducedDeparture
import com.rndeveloper.paparcar.domain.usecase.parking.RetractDeducedDeparture

/**
 * [DET-COORDINATOR-NO-OPTIONAL-DEPS-001] Recording doubles for the three coordinator dependencies
 * that used to be injected as `null`. They record and answer, nothing else — the behaviour of the
 * real pair lives in `FinalizeDeducedDepartureUseCaseTest` / `RetractDeducedDepartureUseCaseTest`;
 * here the question is only whether the coordinator CALLS them at the §B / §B.3 moments.
 */
class FakeDetectionPhaseSink : DetectionPhaseSink {
    val phases = mutableListOf<DetectionPhase>()
    override fun setPhase(phase: DetectionPhase) {
        phases += phase
    }
}

class FakeFinalizeDeducedDeparture : FinalizeDeducedDeparture {
    /** One entry per call, carrying the vehicle id the coordinator attributed to the drive. */
    val calls = mutableListOf<String?>()
    var result = false
    override suspend fun invoke(vehicleId: String?): Boolean {
        calls += vehicleId
        return result
    }
}

class FakeRetractDeducedDeparture : RetractDeducedDeparture {
    var calls = 0
        private set
    var result = 0
    override suspend fun invoke(): Int {
        calls++
        return result
    }
}
