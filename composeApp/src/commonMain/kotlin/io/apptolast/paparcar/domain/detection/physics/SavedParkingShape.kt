package io.apptolast.paparcar.domain.detection.physics

import io.apptolast.paparcar.domain.model.GpsPoint

/**
 * [07 §3.4.1] **WHAT a verdict leaves behind** — the four shapes a detection session can end in,
 * and nothing else.
 *
 * Today the same four outcomes are spelled three different ways: `SaveExact` / `ApproximatePin` /
 * `Confirmed`, `SaveZone` / `ApproximateZone`, `Ask` / `Prompt`, `KeepSilent` / (silence by
 * omission). Three vocabularies for one question is how a rule gets fixed in one ladder and
 * forgotten in the other — the exact history of the zone ceiling, which lived in the coordinator's
 * two paths and not in the honest-close (`8bf6f02b`, then P1.6).
 *
 * ## The shape is not the reason
 *
 * Each verdict keeps emitting **its own reason** next to its shape: [UnattendedSaveReason],
 * `HonestCloseVerdict.REASON_*`, [PromptReason]. Those vocabularies are a trace contract — a saved
 * field trace from July must still read the same in a build from September — and unifying them
 * would break every diagnosis quoted in `docs/backlog/`. So no arm here carries a `reason` string.
 * That is a deliberate departure from the sketch in `09 §6`, which gave `AskUser`/`KeepSilent` a
 * `reason: String`: a shared reason field is an open invitation to merge the three vocabularies
 * into it, which `07 §3.4.1` forbids in the same sentence that proposes this type.
 *
 * ## What "shape" excludes
 *
 * A shape is a TERMINAL answer about an artifact. `ParkingDecision.Rejected`,
 * `.Inconclusive` and `.CloseHumanPowered` are not shapes and must never be flattened into
 * [KeepSilent]: silence means *this session is done and nothing is saved*, while `Inconclusive`
 * means *not yet, ask me again on the next fix* and `Rejected` means *not this candidate, the stop
 * stays alive*. Collapsing them would end sessions that are still working. `SavedParkingShapeTest`
 * classifies every arm of the existing sealeds exhaustively so that adding one to any of them stops
 * compiling until somebody says which side of this line it falls on.
 *
 * ## Payload decisions, both load-bearing
 *
 * - **[ExactPin] carries `location` AND `reliability`.** No arm today carries both — `SaveExact`
 *   carries neither (the caller reads the anchor), `ApproximatePin` carries only the point,
 *   `Confirmed` only the reliability. The union is available at every emission site; carrying it is
 *   what stops the caller from supplying a different point than the one the verdict reasoned about.
 * - **[BoundedZone] carries the FINAL radius, not the doubt.** This settles the one real
 *   disagreement between the two zone paths: `EvaluateHonestClose` turns doubt into a clamped
 *   radius inside the verdict, `EvaluateUnattendedParkingSave` emits raw `doubtMeters` and lets the
 *   coordinator clamp it. Both call [honestZoneRadius], but only one of them can forget to. With
 *   the radius in the type, the conversion happens before emission, in one place, always.
 *
 * ⚠️ **Nothing adopts this type yet, on purpose.** Making the verdicts return it changes their
 * signatures and their tests, which belongs to the verdict phase (`10-plan-refactor.md` P1.10).
 * What this step buys now is that the divergence stops growing while that phase waits.
 */
sealed interface SavedParkingShape {

    /** A point the session is willing to stand behind, with the trust it claims. */
    data class ExactPin(val location: GpsPoint, val reliability: Float) : SavedParkingShape

    /**
     * An AREA, never a deceptively precise dot: the car is somewhere within [radiusMeters] of
     * [center]. The park is kept and only its precision degrades — the chain never breaks, because
     * the zone still carries a fence.
     */
    data class BoundedZone(val center: GpsPoint, val radiusMeters: Float) : SavedParkingShape

    /**
     * The doubt is real and UNBOUNDED, so no artifact is honest — ask. Asymmetric failure doctrine:
     * a question costs a tap, a phantom pin costs the user their spot.
     */
    data object AskUser : SavedParkingShape

    /**
     * Nothing is saved and nothing is asked: this session ends without an artifact and whatever pin
     * already existed stays untouched. Terminal — see the exclusion above.
     */
    data object KeepSilent : SavedParkingShape
}
