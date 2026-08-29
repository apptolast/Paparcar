package com.rndeveloper.paparcar.detection

import com.rndeveloper.paparcar.domain.detection.ports.ArrivalHandoffDetection

/** iOS has no Coordinator detection service yet — no-op until detection lands there.
 *  [DET-HANDOFF-NOT-MANUAL-001] */
class IosArrivalHandoffDetectionImpl : ArrivalHandoffDetection {
    override fun start() = Unit
}
