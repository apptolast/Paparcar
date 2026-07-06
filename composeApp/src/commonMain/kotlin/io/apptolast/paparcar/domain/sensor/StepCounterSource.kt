package io.apptolast.paparcar.domain.sensor

/**
 * Cumulative pedestrian step counter maintained by the device's sensor hub. [DET-RECONCILE-001]
 *
 * Unlike [StepDetectorSource] (a live stream that only counts while a listener is registered
 * and the process is alive), the cumulative counter keeps counting in hardware while the app
 * process — and even the main CPU — sleeps. Reading it at two points in time yields the TOTAL
 * steps walked in between, regardless of what the OEM did to the process meanwhile.
 *
 * That delta is the step budget the parked-state reconcile uses to distinguish "walked away
 * from the car" (steps ≈ distance/stride) from "was driven away" (steps ≪ distance/stride)
 * long after the fact — the discriminator that does not depend on catching the drive live.
 *
 * Semantics of the value: monotonically increasing steps since device boot. A reboot resets
 * it, so a later reading SMALLER than an earlier one means "reboot in between — budget unknown".
 */
interface StepCounterSource {

    /**
     * One-shot read of the cumulative counter, or null when the device lacks the sensor or
     * no reading arrives within the implementation's timeout.
     */
    suspend fun currentCumulativeSteps(): Long?
}
