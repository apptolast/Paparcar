package com.rndeveloper.paparcar.domain.detection.physics

/**
 * What a session's ending MEANS to the sentry-wake storm damper. Declared per outcome, never
 * inferred. [DET-SENTRY-COOLDOWN-001][DET-STOP-BUTTON-001]
 *
 * A plain `Boolean` would let a new outcome inherit `false` — i.e. RESETS — by saying nothing,
 * which is the exact accident this type exists to prevent: `stopped_by_user` resets the streak
 * today because it falls into a `when`'s `else`, and that is a real decision (the highest
 * authority in the system speaking, not a refuted nomination) that nobody ever wrote down.
 */
enum class SentryStreakEffect { EXTENDS, RESETS }

/**
 * [09 §1.7][11 bug #3] The terminal label a detection session ends with — typed, with the
 * serialization **byte for byte identical** to the strings it replaces.
 *
 * ## Why this is not cosmetic
 *
 * Three consumers ask three different questions of this label, and every one of them asked it of a
 * **String**:
 *
 * | Consumer | Question | How it asked |
 * |---|---|---|
 * | the unattended zone save | did the session actually save? | `startsWith("confirmed_")` |
 * | the honest-close ladder | was this a silent abort? | equality against two constants |
 * | the sentry-wake damper | was this a walking abort? | equality against the same two |
 *
 * So membership was decided by **how the string was spelled**. Adding an outcome granted or denied
 * it three behaviours at once, silently, depending only on its prefix — and it happened: when
 * `aborted_no_movement_jam` was introduced it left the honest-close set AND the streak set without
 * anyone deciding either, purely because it stopped being equal to `aborted_no_movement`. Both
 * exclusions turn out to be right ([AbortedNoMovementJam] carries the reasoning); the point is that
 * nobody chose them.
 *
 * Here every arm STATES its three memberships. A new outcome does not compile until its author
 * answers all three.
 *
 * ## The contract this type must not break
 *
 * These strings are a **trace contract**: field diagnoses in `docs/backlog/` quote them, and a
 * session recorded in July must still read the same in a September build. `SessionOutcomeTest`
 * pins every [serialized] value against the literal it replaced. Nothing here may rename anything.
 *
 * ## What it deliberately does NOT fix
 *
 * `aborted_unattended_human_powered` has **two producers** — the 15-minute response timeout and
 * [DET-HUMAN-POWERED-EARLY-CLOSE-001]'s early close — and the name encodes a provenance that is
 * only true for one of them. Typing the label does not make that go away and must not pretend to:
 * it is bug #7 and it has its own ticket. Both producers keep emitting the same string on purpose,
 * so field comparisons against every previous bicycle session still line up.
 *
 * There is also no `parse`. Nothing needs one: the coordinator PRODUCES these and hands out
 * [serialized] for telemetry, so no code path ever turns a string back into a type. Adding a parse
 * would mean inventing an `Unknown` arm to stay total, and an `Unknown` arm has to answer the three
 * membership questions with a guess.
 */
sealed interface SessionOutcome {

    /** The exact string this outcome has always been written as. */
    val serialized: String

    /** Did this session end with a park actually saved? Was `startsWith("confirmed_")`. */
    val isConfirmed: Boolean

    /**
     * Should the honest-close ladder run after this ending? True only for the two SILENT aborts —
     * a session armed by a real trigger that ended with nothing measured and a stale pin possibly
     * gone rancid. [DET-HONEST-CLOSE-001]
     */
    val triggersHonestClose: Boolean

    /** @see SentryStreakEffect */
    val sentryStreakEffect: SentryStreakEffect

    /**
     * [DET-A-RESOLVED-ARRIVAL-IS-RESOLVED-FOR-ALL-EIGHT-REASONS-001] **Did the coordinator reach a
     * verdict about this arrival and answer "only the user can mark it"?**
     *
     * When it did, the 15-minute safety net's backfill must NOT re-decide the same arrival with
     * less information — it stamps the resolution to disk at the abort and the backfill defers to
     * the nudge. That is `DET-BACKFILL-TAINT-001`, opened by field 2026-07-30 20:42 (Redmi/Jerez),
     * where the backfill planted the pin the coordinator had just refused.
     *
     * ⛔ The service used to ask this question by comparing against **one** outcome —
     * `AbortedUnattended("gap_anchor")` — while the verdict that produces it has **eight** reasons,
     * all of them reaching the same `Ask`, all of them ending the session through the same single
     * producer. Seven arrivals out of eight were resolved and then quietly re-decided. Asked of the
     * type now, so a ninth outcome has to answer.
     */
    val resolvesTheArrival: Boolean

    val extendsSentryStreak: Boolean get() = sentryStreakEffect == SentryStreakEffect.EXTENDS
    val resetsSentryStreak: Boolean get() = sentryStreakEffect == SentryStreakEffect.RESETS

    /** The default a session is born with, and what it still says if no path refined it. */
    data object Ended : SessionOutcome {
        override val serialized = "ended"
        override val isConfirmed = false
        override val triggersHonestClose = false
        override val sentryStreakEffect = SentryStreakEffect.RESETS
        /** `false` - no verdict was reached at all. */
        override val resolvesTheArrival = false
    }

    /**
     * [DET-SUPERSEDE-CANNOT-DISCARD-A-MEASURED-DRIVE-001] A newer trigger took this session's place
     * on the SAME trip — it did not fail, it handed over.
     *
     * The string is not new: the superseded branch of the coordinator's `finally` has always logged
     * `"superseded"` as a literal. What was new is that reaching it depended on a RACE. `cancel()`
     * does not join, so whether the predecessor's `finally` ran before or after its successor
     * claimed the singleton decided which of two labels the trace got, and the field session of
     * 2026-08-25 19:59:05 got the other one: [Ended], the default a session is born with, on a
     * 23-minute trip with 60 driving fixes. Stamping the outcome BEFORE the cancel removes the race;
     * this arm is what there is to stamp.
     *
     * **All three memberships are decisions:**
     *  - `triggersHonestClose = false` — the successor now owns this trip's pin. Running the ladder
     *    here would close the world out from under a session that is still driving through it.
     *  - `RESETS` — the streak damps REFUTED walking arms. A supersede is the opposite: a live
     *    trigger asserting the journey continues.
     */
    data object Superseded : SessionOutcome {
        override val serialized = "superseded"
        override val isConfirmed = false
        override val triggersHonestClose = false
        override val sentryStreakEffect = SentryStreakEffect.RESETS
        /** `false` - a newer park replaced it; there is nothing pending to re-decide. */
        override val resolvesTheArrival = false
    }

    /** Armed by a trigger the session's own stream then refuted — nothing drove. One of the two
     *  silent walking aborts: the honest-close ladder runs, and a sentry-wake arm that ends here
     *  extends the storm streak. */
    data object AbortedFalseEnter : SessionOutcome {
        override val serialized = "aborted_false_enter"
        override val isConfirmed = false
        override val triggersHonestClose = true
        override val sentryStreakEffect = SentryStreakEffect.EXTENDS
        /** `false` - the session never reached a park decision. */
        override val resolvesTheArrival = false
    }

    /** The stop clock ran out with no movement worth a candidate. The other silent walking abort. */
    data object AbortedNoMovement : SessionOutcome {
        override val serialized = "aborted_no_movement"
        override val isConfirmed = false
        override val triggersHonestClose = true
        override val sentryStreakEffect = SentryStreakEffect.EXTENDS
        /** `false` - idem - nothing arrived, so nothing was resolved. */
        override val resolvesTheArrival = false
    }

    /**
     * Same ending, but the no-movement window had been EXTENDED because the session measured
     * traffic-jam creep. [DET-JAM-WINDOW-001]
     *
     * **Both exclusions below are decisions, not spelling** [09 §14.4]:
     *  - `triggersHonestClose = false` — deliberate and provisional, awaiting field data on whether
     *    this cohort ("a jam that never cleared, or a crawl into a re-park?") deserves a nudge. The
     *    21-08 sweep over 1,359 sessions found the cohort **EMPTY**, so the question stays
     *    undecidable with data and the exclusion stands unchanged.
     *  - `RESETS` — semantically right and not merely inherited: the streak damps WALKING aborts,
     *    and here displacement was actually measured. The world did move.
     */
    data object AbortedNoMovementJam : SessionOutcome {
        override val serialized = "aborted_no_movement_jam"
        override val isConfirmed = false
        override val triggersHonestClose = false
        override val sentryStreakEffect = SentryStreakEffect.RESETS
        /** `false` - idem. */
        override val resolvesTheArrival = false
    }

    /** No vehicle could be attributed, so nothing could be saved to anything. */
    data object AbortedNoVehicle : SessionOutcome {
        override val serialized = "aborted_no_vehicle"
        override val isConfirmed = false
        override val triggersHonestClose = false
        override val sentryStreakEffect = SentryStreakEffect.RESETS
        /** `false` - nothing could be saved to anything; the net may still try. */
        override val resolvesTheArrival = false
    }

    /** The user was asked and never answered within `confirmationResponseTimeoutMs`. A question was
     *  posed, so this is not a silent abort. */
    data object AbortedResponseTimeout : SessionOutcome {
        override val serialized = "aborted_response_timeout"
        override val isConfirmed = false
        override val triggersHonestClose = false
        override val sentryStreakEffect = SentryStreakEffect.RESETS
        /** `false` - the verdict WAS 'save exact here' and a confirm GUARD refused it - a different actor, and the backfill's own placement goes through those same guards. */
        override val resolvesTheArrival = false
    }

    /**
     * [DET-STOP-BUTTON-001] The user tapped "Stop detection" on a live session — a REQUESTED false
     * negative. `RESETS` is the third membership behaviour, and until now it was the implicit
     * `else` of a `when`: this is not a refuted nomination, it is the highest authority in the
     * system speaking, so the storm streak starts over rather than growing.
     */
    data object StoppedByUser : SessionOutcome {
        override val serialized = "stopped_by_user"
        override val isConfirmed = false
        override val triggersHonestClose = false
        override val sentryStreakEffect = SentryStreakEffect.RESETS
        /** `false` - the user turned detection off; the net does not run either. */
        override val resolvesTheArrival = false
    }

    /**
     * The unattended timeout resolved the session as nudge-only. [reasonKey] is
     * `UnattendedSaveReason.key`, kept as a plain string so this file stays at the bottom of the
     * dependency order — `physics/` never imports a use case.
     *
     * ⚠️ `human_powered` reaches here from TWO producers with one name — bug #7, see the type KDoc.
     */
    data class AbortedUnattended(val reasonKey: String) : SessionOutcome {
        override val serialized = "aborted_unattended_$reasonKey"
        override val isConfirmed = false
        override val triggersHonestClose = false
        override val sentryStreakEffect = SentryStreakEffect.RESETS
        /** `true` - the verdict itself said nudge-only, for whichever of the eight reasons. */
        override val resolvesTheArrival = true
    }

    /** A park was saved. [pathLabel] is the `detectionPath` that earned it. */
    data class Confirmed(val pathLabel: String) : SessionOutcome {
        override val serialized = "confirmed_$pathLabel"
        override val isConfirmed = true
        override val triggersHonestClose = false
        override val sentryStreakEffect = SentryStreakEffect.RESETS
        /** `false` - a pin exists; the backfill has nothing to place. */
        override val resolvesTheArrival = false
    }

    /**
     * The confirm was attempted and the save FAILED (a guard refused it, or the write did).
     *
     * ⚠️ `confirm_failed_` is one character away from `confirmed_`, and the old membership test was
     * a prefix match. It never actually collided — `confirm_` is not `confirmed_` — but the margin
     * was a single letter, and [isConfirmed] is now a declaration instead of a spelling accident.
     */
    data class ConfirmFailed(val pathLabel: String) : SessionOutcome {
        override val serialized = "confirm_failed_$pathLabel"
        override val isConfirmed = false
        override val triggersHonestClose = false
        override val sentryStreakEffect = SentryStreakEffect.RESETS
        /** `false` - the save was attempted and failed - a retry is not a re-decision. */
        override val resolvesTheArrival = false
    }
}
