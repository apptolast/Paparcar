package io.apptolast.paparcar.fakes

import io.apptolast.paparcar.domain.ActivityRecognitionManager
import io.apptolast.paparcar.domain.ActivityTransitionEvent

class FakeActivityRecognitionManager : ActivityRecognitionManager {

    var registerCount = 0
        private set
    var unregisterCount = 0
        private set
    var shouldThrowOnRegister = false

    /** [IOS-F0-05] Transitions the fake "platform" recorded — what a pull platform would
     *  return. Leave empty to behave like a push platform (Android). */
    var recordedTransitions: List<ActivityTransitionEvent> = emptyList()
    var queryCount = 0
        private set

    override fun registerTransitions() {
        if (shouldThrowOnRegister) throw RuntimeException("AR unavailable")
        registerCount++
    }

    override fun unregisterTransitions() {
        unregisterCount++
    }

    override suspend fun queryTransitions(fromMs: Long, toMs: Long): List<ActivityTransitionEvent> {
        queryCount++
        return recordedTransitions.filter { it.tMs in fromMs..toMs }.sortedBy { it.tMs }
    }
}
