package io.apptolast.paparcar.detection

import io.apptolast.paparcar.domain.detection.ArrivalHandoffDetection

/** iOS has no Coordinator detection service yet — no-op until detection lands there.
 *  [DET-HANDOFF-NOT-MANUAL-001] */
class IosArrivalHandoffDetectionImpl : ArrivalHandoffDetection {
    override fun start() = Unit
}
