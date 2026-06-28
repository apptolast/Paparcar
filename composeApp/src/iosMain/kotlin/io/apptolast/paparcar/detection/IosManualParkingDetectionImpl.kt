package io.apptolast.paparcar.detection

import io.apptolast.paparcar.domain.detection.ManualParkingDetection

/** iOS has no Coordinator detection service yet — no-op until detection lands there. [DET-G-01b] */
class IosManualParkingDetectionImpl : ManualParkingDetection {
    override fun start() = Unit
}
