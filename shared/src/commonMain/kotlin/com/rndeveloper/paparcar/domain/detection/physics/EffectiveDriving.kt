package com.rndeveloper.paparcar.domain.detection.physics

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
 *  1. **Real driving speed → CAR** — outright when there is no pinned anchor to overturn (1a), and
 *     only once CORROBORATED when there is (1b). [DET-LONE-SAMPLE-CANNOT-UNFREEZE-AN-ANCHOR-001]
 *     This row used to read "nothing outranks a credible fix at trip speed", and because it sits
 *     above row 4 that made the whole ANCHOR-LOCK skippable by one sample. Field 2026-08-27, Calle
 *     del Vivero: the car really stopped — four consecutive fixes at 0,0 m/s with 2,2 m accuracy —
 *     the lock correctly ignored brisk walking at 2,65 and 4,25 m/s, and then a SINGLE fix at
 *     6,45 m/s (37 m in 5 s, a person walking fast down a narrow street) cleared the anchor. The
 *     next fix, five seconds later, read 0,0 m/s twelve metres away; the park re-anchored 56 m
 *     down the street and the pin landed 35 m from the car. The same physics `shortHopProofFixes`
 *     already encodes — *a single fix can be a cache teleport* — applied to the one place that
 *     overturns a rest the session actually WITNESSED. Cost when the car truly pulls away: one
 *     fix, about five seconds.
 *  2. **Sustained departure → CAR even when no single fix is credible** — outright when there is no
 *     pinned anchor to overturn (2a), and only once CORROBORATED when there is (2b). The position
 *     provably RAN from the anchor at vehicle pace; this is what unfreezes an anchor when the OEM
 *     starves every individual fix of accuracy [DET-CREDIBLE-DRIVE-001].
 *     [DET-DISPLACEMENT-DRIVE-MUST-SURVIVE-ITS-NEXT-FIX-001] The 2a/2b split is row 1's own split
 *     applied to the other lane, for the same field reason one lane over: on 2026-08-28 01:11:04
 *     (Redmi) a SINGLE fix — already rejected as driving by the accuracy gate (acc 81.8 > 50) —
 *     resolved CAR by displacement in the same beat and cleared a FROZEN anchor, a witnessed rest.
 *     Its moving gate is the declared Doppler of that very fix, so the verdict inherits the field's
 *     lies; what a mirage cannot do is KEEP satisfying the departure geometry on the next fix
 *     (2026-08-29 23:47: 71,6 m out, 64,8 m back in 3,5 s), while a real departure re-measures on
 *     every fix (Enamorados' recovery fixes arrive in pairs ~1 s apart). Cost when the car truly
 *     pulls away from a pinned rest: one fix.
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
 * ⚠️ 1b must stay ABOVE row 4 and 1a must stay separate from it: merging them back into a bare
 * `isRealDrive` re-opens Calle del Vivero, and demoting the pair below row 4 would mean a car
 * pulling out of its own space could never clear the anchor it is parked on.
 *
 * @param isRealDrive Credible fix at or above trip speed.
 * @param realDriveCorroborated This is not the FIRST such fix in a row — the run has reached
 *   `pinnedAnchorRealDriveFixes`. Only consulted when an anchor is pinned; a lone sample never
 *   overturns a witnessed rest. [DET-LONE-SAMPLE-CANNOT-UNFREEZE-AN-ANCHOR-001]
 * @param sustainedDeparture The position ran from the anchor at vehicle pace since its stop began.
 * @param sustainedDepartureCorroborated This is not the FIRST such verdict in a row — the run has
 *   reached `pinnedAnchorRealDriveFixes` (same bar as the Doppler lane: both overturn the same
 *   witnessed rest). Only consulted when an anchor is pinned.
 *   [DET-DISPLACEMENT-DRIVE-MUST-SURVIVE-ITS-NEXT-FIX-001]
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
    realDriveCorroborated: Boolean,
    sustainedDeparture: Boolean,
    sustainedDepartureCorroborated: Boolean,
    steplessDeparture: Boolean,
    anchorPinned: Boolean,
    corroboratedMuteHop: Boolean,
    stepsCounted: Int,
    hasAnchor: Boolean,
    displacementOutrunsSteps: Boolean,
    isDriving: Boolean,
): Boolean = when {
    isRealDrive && !anchorPinned -> true
    isRealDrive && realDriveCorroborated -> true
    sustainedDeparture && !anchorPinned -> true
    sustainedDeparture && sustainedDepartureCorroborated -> true
    steplessDeparture -> true
    anchorPinned -> false
    corroboratedMuteHop -> true
    stepsCounted == 0 && isDriving -> false
    hasAnchor && isDriving && !displacementOutrunsSteps -> false
    else -> isDriving
}
