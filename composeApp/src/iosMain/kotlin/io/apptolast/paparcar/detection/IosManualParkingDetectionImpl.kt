package io.apptolast.paparcar.detection

import io.apptolast.paparcar.domain.detection.ports.ManualParkingDetection

/** iOS has no Coordinator detection service yet — no-op until detection lands there. [DET-G-01b] */
class IosManualParkingDetectionImpl : ManualParkingDetection {
    override fun start() = Unit
    override fun stop() = Unit
    override fun stopByUser() = Unit // [DET-STOP-BUTTON-001]

    @Suppress("UNUSED_PARAMETER")
    override fun answerPrompt(parked: Boolean) = Unit // [DET-ASK-STATE-001]
}
