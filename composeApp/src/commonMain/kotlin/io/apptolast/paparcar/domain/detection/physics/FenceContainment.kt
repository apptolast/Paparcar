package io.apptolast.paparcar.domain.detection.physics

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.util.haversineMeters

/**
 * **Could the body plausibly be AT this car?**
 *
 * ```
 * d(fix, fenceCentre)  <=  fenceRadius  +  acc(fix)
 * ```
 *
 * The padding by the fix's own accuracy is the whole point: a fix that could be off by 40 m and
 * lands 30 m outside the ring has not shown the body is away from the car, it has shown the
 * receiver is unsure. Reading that as "definitely outside" would act on GPS noise.
 *
 * **The asymmetry is deliberate and runs one way.** A wrongly-"inside" answer costs a handful of
 * extra wake-ups, or an arm labelled at-the-car when it was mid-trip. A wrongly-"outside" answer
 * costs a parking spot. So containment is generous, and it is generous on purpose.
 *
 * Two callers ask exactly this: the sentry damper's `isInsideAnyOwnedFence`
 * [DET-SENTRY-COOLDOWN-001] and the AR-boarding evaluator deciding `ArmAtCar` vs `ArmMidTrip`
 * [DET-AR-FIRST-001].
 *
 * ---
 *
 * ⚠️ **A third site tests containment WITHOUT the padding, and that is not a discrepancy.**
 * `EvaluateSafetyNetCheckUseCase` uses the bare `d <= radius`, and the reason is structural: it does
 * not ask a binary question. It runs a three-zone ladder —
 *
 * ```
 * d <= radius                        -> cure this fence
 * d - acc(fix) <= farThreshold       -> do nothing (the ambiguous ring: GPS-noise territory)
 * otherwise                          -> far, run the departure proofs
 * ```
 *
 * — and **its generosity already lives on the FAR gate**, where "far" must hold even if the fix
 * erred by its own accuracy (a single 100 m cache jump must not clear it). Padding the inside test
 * as well would push the two paddings into each other and eat the ambiguous ring, converting
 * noise-territory fixes into fence cures. The ring exists precisely so that neither answer is
 * forced when the fix cannot support one.
 *
 * Whether that third site *should* be padded is a product question with its own trade-off (bug #9,
 * `11-bugs-encontrados.md`) and belongs in its own ticket. What was missing until now was this
 * paragraph: **without it, a reasoned asymmetry is indistinguishable from an oversight**, and the
 * next person to notice the difference has no way to tell which one they are looking at.
 */
fun isWithinFence(fix: GpsPoint, fenceCenter: GpsPoint, fenceRadiusMeters: Float): Boolean {
    val d = haversineMeters(
        fix.latitude, fix.longitude,
        fenceCenter.latitude, fenceCenter.longitude,
    )
    return d <= fenceRadiusMeters + fix.accuracy
}
