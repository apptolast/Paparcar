package com.rndeveloper.paparcar.fakes

import com.rndeveloper.paparcar.domain.ActivityRecognitionManager

class FakeActivityRecognitionManager : ActivityRecognitionManager {

    var registerCount = 0
        private set
    var unregisterCount = 0
        private set
    var shouldThrowOnRegister = false

    override fun registerTransitions() {
        if (shouldThrowOnRegister) throw RuntimeException("AR unavailable")
        registerCount++
    }

    override fun unregisterTransitions() {
        unregisterCount++
    }
}
