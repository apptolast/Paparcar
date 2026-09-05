package com.rndeveloper.paparcar.detection

import com.rndeveloper.paparcar.domain.detection.ports.ArrivalHandoffDetection

/** [DET-HANDOFF-NOT-MANUAL-001] The worker-deduced door: arms as ARRIVAL_HANDOFF with its own weak
 *  evidence — never as the user's word. Callers appear with F2's safety net; the door exists first
 *  so they cannot reach for the manual one. [IOS-F1-A-CONTROLLER-FOR-THE-HAPPY-PATH-001] */
class IosArrivalHandoffDetectionImpl(
    private val controller: IosDetectionController,
) : ArrivalHandoffDetection {
    override fun start() = controller.startTrackingArrivalHandoff()
}
