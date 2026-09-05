package com.rndeveloper.paparcar.detection

import com.rndeveloper.paparcar.domain.detection.ports.ManualParkingDetection

/**
 * The manual door into the iOS orchestrator. [IOS-F1-A-CONTROLLER-FOR-THE-HAPPY-PATH-001]
 * Each method maps to the same intake command its Android intent-action twin enqueues — one door
 * per meaning, so a worker-born arm can never wear the user's intent. [DET-HANDOFF-NOT-MANUAL-001]
 */
class IosManualParkingDetectionImpl(
    private val controller: IosDetectionController,
) : ManualParkingDetection {
    override fun start() = controller.startTrackingManual()

    // [DET-MANUAL-CANCEL-001] Manual pin owns the trip's ending: cancel without a quiet period.
    override fun stop() = controller.stopTracking()

    // [DET-STOP-BUTTON-001] Own terminal outcome + quiet period.
    override fun stopByUser() = controller.stopByUser()

    // [DET-ASK-STATE-001] Same coordinator hooks the notification's two buttons fire.
    override fun answerPrompt(parked: Boolean) = controller.answerPrompt(parked)
}
