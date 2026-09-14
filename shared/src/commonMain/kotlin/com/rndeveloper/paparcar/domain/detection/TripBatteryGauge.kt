package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.detection.ports.BatteryMeter

/**
 * Measures what a GPS-holding job actually COST, in the only currency the house accepts: a
 * measured figure or none. [BATT-A-COST-IS-MEASURED-NOT-DECLARED-001]
 *
 * Two samples of the platform's cumulative charge counter bracket the job; [settle] turns them
 * into a [Cost] whose [Cost.clean] is the honesty verdict — only a clean figure may ever feed a
 * user-facing number. A dirty figure is still recorded (it is data, and the fleet can filter),
 * but it never claims to be a measurement of the job alone:
 *
 *  - **charging at either end** — the counter was moving the other way for part of the span;
 *  - **negative delta** — the device was plugged in somewhere inside the span without us seeing it;
 *  - **missing counter** — the device's fuel gauge answers nothing: no figure at all, never a guess;
 *  - **too short** — under [MIN_CLEAN_DURATION_MS] the fuel gauge's own noise (±1–2 %) outweighs
 *    the signal.
 */
object TripBatteryGauge {

    /** One reading of the instrument, stamped when it was taken. */
    data class Sample(
        val chargeMicroAh: Long?,
        val charging: Boolean,
        val atMs: Long,
    )

    /**
     * @property deltaMicroAh µAh consumed over the span (positive = discharge), or null when the
     *   counter was unavailable at either end — absent, never invented.
     * @property clean true only when the figure measures THE JOB and nothing else.
     */
    data class Cost(
        val deltaMicroAh: Long?,
        val clean: Boolean,
        val durationMs: Long,
    )

    fun sample(meter: BatteryMeter, nowMs: Long): Sample =
        Sample(chargeMicroAh = meter.chargeCounterMicroAh(), charging = meter.isCharging(), atMs = nowMs)

    fun settle(start: Sample, end: Sample): Cost {
        val durationMs = end.atMs - start.atMs
        // Discharge DECREASES the cumulative counter → cost = start − end, positive when draining.
        val deltaMicroAh = if (start.chargeMicroAh != null && end.chargeMicroAh != null) {
            start.chargeMicroAh - end.chargeMicroAh
        } else {
            null
        }
        val clean = deltaMicroAh != null &&
            !start.charging && !end.charging &&
            deltaMicroAh >= 0 &&
            durationMs >= MIN_CLEAN_DURATION_MS
        return Cost(deltaMicroAh = deltaMicroAh, clean = clean, durationMs = durationMs)
    }

    /** Below this span the fuel gauge's own noise outweighs the signal — measured figures only. */
    const val MIN_CLEAN_DURATION_MS = 60_000L
}
