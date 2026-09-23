package com.rndeveloper.paparcar.domain.model

/**
 * [SPOT-A-BOARDING-IS-NOT-A-DEPARTURE-001] **What the UI paints a community spot with** — the one
 * closed set every surface (map puck, list row, peek, meter) resolves its colour and words from.
 *
 * ## Why this is not a fourth [SpotFreshness]
 *
 * [SpotFreshness] is a function of ONE input, the spot's age, and its three tiers are a ramp down
 * that axis. A [SpotStatus.RELEASING] spot has no age worth reading: it was published seconds ago
 * and it is still **not free** — the car is in it. Put it in the ramp and it comes out FRESH, i.e.
 * bright green, i.e. the exact lie `SpotFreshnessPolicy`'s KDoc already worried about ("a
 * proportional ramp would paint a possible phantom green for its first nine minutes"), except this
 * one would be green *correctly* and still mislead.
 *
 * So the ramp keeps its single input and its own tests, and this type is what the pixels read.
 *
 * ## Why an enum and not a sealed type
 *
 * `PaparcarMapView` registers each marker's DRAWING by iterating `entries` and building one content
 * id per variant. With the tone as an enum the fourth variant registers its own drawing, and the
 * failure mode of [UI-A-MARKER-ID-MUST-BRING-ITS-DRAWING-001] — a live marker whose `contentId` has
 * no entry in the drawing map renders **nothing at all**, silently — is closed by construction
 * rather than by remembering. That bug has already happened twice in this codebase.
 */
enum class SpotTone {
    /** Free, freed within the last few minutes. */
    FRESH,

    /** Free, cooling off. */
    RECENT,

    /** Free, probably gone — still shown, because an honest stale spot beats an empty map. */
    STALE,

    /**
     * NOT free yet: somebody just got into the car parked here and is about to pull out.
     * Deliberately last, so `entries.last()` reads as what it is — the one tone that is not a
     * point on the freshness ramp.
     */
    RELEASING,
    ;

    /** Whether this tone belongs to the age ramp at all. */
    val isFree: Boolean get() = this != RELEASING
}

/** The ramp tier as the tone it paints. The three-to-three half of the mapping. */
fun SpotFreshness.asTone(): SpotTone = when (this) {
    SpotFreshness.FRESH -> SpotTone.FRESH
    SpotFreshness.RECENT -> SpotTone.RECENT
    SpotFreshness.STALE -> SpotTone.STALE
}

/**
 * **The only resolver.** A spot that claims to be releasing is painted as releasing whatever its
 * timestamp says; everything else is read off the age ramp.
 *
 * Kept here rather than in presentation for the same reason [SpotFreshnessPolicy] is in the domain:
 * the decision is testable without a composition, and a second `when` somewhere in the UI is how
 * two surfaces end up disagreeing about the same spot. [UI-COLOR-THE-RAMP-HAS-ONE-RESOLVER-001]
 */
fun Spot.tone(nowMs: Long): SpotTone =
    if (status == SpotStatus.RELEASING) {
        SpotTone.RELEASING
    } else {
        SpotFreshnessPolicy.of(reportedAtMs = location.timestamp, nowMs = nowMs).asTone()
    }
