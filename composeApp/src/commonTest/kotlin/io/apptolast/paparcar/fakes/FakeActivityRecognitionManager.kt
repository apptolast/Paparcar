package io.apptolast.paparcar.fakes

import io.apptolast.paparcar.domain.ActivityRecognitionManager

class FakeActivityRecognitionManager : ActivityRecognitionManager {

    var registerCount = 0
        private set
    var unregisterCount = 0
        private set
    var shouldThrowOnRegister = false

    var enterArmingRegisterCount = 0
        private set
    var enterArmingUnregisterCount = 0
        private set

    override fun registerTransitions() {
        if (shouldThrowOnRegister) throw RuntimeException("AR unavailable")
        registerCount++
    }

    override fun unregisterTransitions() {
        unregisterCount++
    }

    override fun registerVehicleEnterArming() {
        enterArmingRegisterCount++
    }

    override fun unregisterVehicleEnterArming() {
        enterArmingUnregisterCount++
    }
}
