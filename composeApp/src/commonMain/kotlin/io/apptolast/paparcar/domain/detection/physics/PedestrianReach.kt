package io.apptolast.paparcar.domain.detection.physics

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.util.haversineMeters

/**
 * The person/car discriminator, as geometry: **has the position outrun what a walker could have
 * covered?**
 *
 * ```
 * d(base, fix)  >  steps × stride  +  acc(base)  +  acc(fix)  +  floor
 * ```
 *
 * This one formula was written out four times in the coordinator with different `(base, steps,
 * floor)` — the only difference between the copies **is** the parameter set (06 §3-b). Each copy
 * carries a field incident in its KDoc and each one is still needed; what is not needed is four
 * chances to fix the arithmetic in one place and leave it wrong in three.
 *
 * **Why every term is there:**
 *  - `steps × stride` — what the counted steps could plausibly have walked. The stride is
 *    deliberately generous ([ParkingDetectionConfig.anchorStrideMeters]): the bias is pro-person,
 *    because calling a walker a car costs a phantom spot and the reverse only costs a question.
 *  - **both** accuracy envelopes — a degraded fix inflates the reach through its own uncertainty,
 *    so the answer fails conservative rather than accusing a car on GPS noise. A Doppler blip while
 *    standing still can never qualify: its distance never escapes its own accuracy.
 *  - `floor` — the noise/under-count allowance, and the term that actually separates the four uses.
 *    A genuine egress under-logs steps and loses GPS (field Calle Gavia: 68 m walked on 8 logged
 *    steps), so the copies that must not strand a real park use a much more generous floor than the
 *    ones policing a single fix.
 *
 * ⚠️ **Related but NOT the same** as `ParkingDetectionConfig.isBeyondPedestrianReach`, which builds
 * its envelope from **time** (`maxPedestrianSpeed × elapsed`) instead of from counted steps.
 * Unifying the two would need its own proof and is not claimed here (06 §3-b, boundary note).
 *
 * @param base the position the reach is measured FROM — the park anchor, or a held pin.
 * @param fix the current position.
 * @param steps steps counted since [base] was captured. Pass **0** for the pure-envelope question
 *        ("has the position measurably left the anchor at all?"), which is that copy exactly.
 * @param strideMeters metres credited per counted step.
 * @param floorMeters the noise floor added on top of both accuracy envelopes.
 */
fun outrunsPedestrianReach(
    base: GpsPoint,
    fix: GpsPoint,
    steps: Int,
    strideMeters: Float,
    floorMeters: Float,
): Boolean {
    val d = haversineMeters(base.latitude, base.longitude, fix.latitude, fix.longitude)
    val walkReach = steps * strideMeters + base.accuracy + fix.accuracy + floorMeters
    return d > walkReach
}
