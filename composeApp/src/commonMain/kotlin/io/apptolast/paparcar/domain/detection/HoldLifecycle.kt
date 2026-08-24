package io.apptolast.paparcar.domain.detection

/**
 * [DET-HOLD-BRANCHES-MUST-SPEAK-001] What became of a post-confirm hold [DET-C-02].
 *
 * The hold is the two minutes between "the egress proof says you parked" and actually planting the
 * pin, kept open to rule out an errand stop. It has **seven ways out and six of them were silent in
 * remote** — a trace showed the confirm that eventually happened and nothing about the hold that
 * produced it. That is not a comfort problem, it is a testability one:
 * `DET-CONFIRM-BRANCH-ORDER-MUST-BE-TESTABLE-001` tried to pin the precedence of three hold branches
 * and could write **none** of the three tests, because neutralising a branch left the observable
 * output byte-identical. A branch that emits nothing cannot be discriminated by any test, only by
 * reading the source and hoping.
 *
 * Two of these exits **plant a pin with no fix to justify it** ([STARVED] and [SESSION_ENDED]).
 * In field forensics those are exactly what "a spot appeared and I don't know why" looks like.
 */
enum class HoldAction {

    /** A tentative confirm opened the hold. Was local-log only, and its absence is what made
     *  "the hold swallowed this fix" indistinguishable from "the fast lane re-fired". */
    OPENED,

    /** The window elapsed (or the user said yes) with a fix in hand, and the pin was planted. */
    SETTLED,

    /** [DET-AUDIT-002 T7] The stream went silent mid-hold and the watchdog planted the pin at the
     *  held position after the window plus margin. **No fix re-validated this park** — deliberately
     *  so (a starved stream is a walk into a building, not a car driving off), but a trace must say
     *  it happened. */
    STARVED,

    /** [DET-AUDIT-002 T7/M2] The session ended (upstream completion or cancellation) with a confirm
     *  still held, and the epilogue finalised it rather than drop a park the egress had earned.
     *  Same "no fix justified this pin" caveat as [STARVED]. */
    SESSION_ENDED,

    /** [DET-CONFIRM-FRESHNESS-001] At settle the position sat farther from the held pin than the
     *  counted steps could walk: a vehicle covered that ground, so this was a pick-up/errand stop.
     *  Discarded, detection continues toward the real park. */
    DISCARDED_STALE,

    /** [DET-C-02] Real driving speed resumed inside the window — the errand ended and the car drove
     *  on. Discarded, detection continues. This was mute, while its sibling [DISCARDED_STALE] did
     *  emit, which is precisely why the two were impossible to tell apart from outside. */
    DISCARDED_DROVE_OFF,

    /** [DET-STOP-BUTTON-001] The user stopped detection while a confirm was held. The hold is
     *  dropped so the button does not plant the very pin the user just refused. */
    DROPPED_BY_USER,
}
