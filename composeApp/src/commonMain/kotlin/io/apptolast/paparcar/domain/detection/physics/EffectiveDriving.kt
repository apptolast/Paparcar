package io.apptolast.paparcar.domain.detection.physics

/**
 * [DET-AR-FIRST-001 F3] **Is the car moving, or is the person?** — the discriminator that decides
 * whether this fix clears the park anchor.
 *
 * Getting it wrong is expensive in both directions. Say CAR when a person walked and the true anchor
 * is wiped, so the pin re-anchors wherever the user next stands still (field 2026-07-10, Camelias:
 * three steps at the kerb cleared the anchor and the pin landed inside the house). Say PERSON when
 * the car crept and the anchor freezes at a traffic light.
 *
 * **The ORDER is the content.** Every row below won an argument with a real trip, and the rows are
 * not independent rules — each one exists to beat the ones under it in a case where both apply. This
 * is why the `when` is moved here verbatim and takes nine already-computed signals rather than
 * recomputing anything: flattening it into configuration would be less readable than the commented
 * `when`, and fragmenting it into separate predicates would destroy the only thing it encodes.
 *
 * Reading it top to bottom:
 *
 *  1. **Real driving speed → CAR, always wins.** Nothing outranks a credible fix at trip speed.
 *  2. **Sustained departure → CAR even when no single fix is credible.** The position provably RAN
 *     from the anchor at vehicle pace; this is what unfreezes an anchor when the OEM starves every
 *     individual fix of accuracy [DET-CREDIBLE-DRIVE-001].
 *  3. **Stepless departure → CAR.** The position keeps escaping the anchor's envelopes at
 *     above-walking pace while a counter *proven alive this session* has counted nothing. A person
 *     covering that ground produces steps within a couple of fixes; a live counter's silence is
 *     evidence of the car (field 2026-07-23, Bodegas Osborne: 160 m of 6–16 km/h creep never moved
 *     the frozen anchor, and the egress walk then confirmed AT the traffic light)
 *     [DET-CONFIRM-FRESHNESS-001].
 *  4. **Pinned anchor → PERSON below real driving.** Once egress steps have locked the anchor, or
 *     the end-of-drive stop matured, brisk walking must not clear it [ANCHOR-LOCK-001].
 *  5. **Corroborated mute hop → CAR.** With a mute counter the ambiguous band may never prove CAR by
 *     its DECLARED speed — "outruns zero steps" is how the walk back from a reposition laundered the
 *     odometer and froze the anchor at a house door (field 2026-07-15, Camelias-Oppo). But a hop the
 *     position PROVABLY made is independent evidence; without this row the car's own deceleration to
 *     the kerb reads as a walk-in and taints a correct anchor (field 2026-07-16, Galeote: 23.7 m in
 *     5 s against 9.9 m of joint noise) [DET-CREDIBLE-DRIVE-001].
 *  6. **Mute counter otherwise → PERSON.** The row that makes the Camelias laundering impossible:
 *     with zero steps and no corroborated hop, the declared speed alone decides nothing.
 *  7. **Steps cover the displacement → PERSON.** The anchor holds.
 *  8. **Otherwise, believe the band.**
 *
 * ⚠️ Rows 5 and 6 look contradictory read in isolation and are not: 5 is the *measured* escape
 * hatch of 6. Their order is load-bearing, and swapping them re-opens Galeote. Same for 3 and 4 —
 * swapping those re-opens Bodegas Osborne. `EffectiveDrivingTest` pins every adjacent pair.
 *
 * @param isRealDrive Credible fix at or above trip speed.
 * @param sustainedDeparture The position ran from the anchor at vehicle pace since its stop began.
 * @param steplessDeparture Enough stepless moving fixes past the anchor envelope with a live counter.
 * @param anchorPinned Step lock or end-of-drive freeze holds the anchor.
 * @param corroboratedMuteHop Zero steps, ambiguous band, and a hop the position provably made.
 * @param stepsCounted Steps counted for this stop — `0` is the mute-counter case.
 * @param hasAnchor An anchor exists to argue about.
 * @param displacementOutrunsSteps The distance beat what the counted steps could walk.
 * @param isDriving The ambiguous-band bar: credible fix above [clearBestStopSpeedMps].
 */
@Suppress("LongParameterList")
fun effectiveDriving(
    isRealDrive: Boolean,
    sustainedDeparture: Boolean,
    steplessDeparture: Boolean,
    anchorPinned: Boolean,
    corroboratedMuteHop: Boolean,
    stepsCounted: Int,
    hasAnchor: Boolean,
    displacementOutrunsSteps: Boolean,
    isDriving: Boolean,
): Boolean = when {
    isRealDrive -> true
    sustainedDeparture -> true
    steplessDeparture -> true
    anchorPinned -> false
    corroboratedMuteHop -> true
    stepsCounted == 0 && isDriving -> false
    hasAnchor && isDriving && !displacementOutrunsSteps -> false
    else -> isDriving
}
