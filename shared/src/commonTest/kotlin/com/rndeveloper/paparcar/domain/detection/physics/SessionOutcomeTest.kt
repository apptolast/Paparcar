package com.rndeveloper.paparcar.domain.detection.physics

import com.rndeveloper.paparcar.domain.usecase.parking.UnattendedSaveReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [09 §1.7][11 bug #3] The golden file for the outcome vocabulary.
 *
 * Two jobs, and the first one is the important one:
 *
 *  1. **Serialization is a trace contract.** Every expected value below is the LITERAL string the
 *     code emitted before the type existed, typed out by hand rather than derived from the type.
 *     A test that read `SessionOutcome.Ended.serialized` and compared it to itself would pass
 *     through any rename; these do not.
 *  2. **Membership is declared, not spelled.** The three questions the three consumers ask are
 *     pinned per arm through an EXHAUSTIVE `when`, so a new outcome cannot be added without
 *     answering all three here too.
 */
class SessionOutcomeTest {

    /** Serialized string, isConfirmed, triggersHonestClose, sentryStreakEffect. */
    private data class Expected(
        val serialized: String,
        val isConfirmed: Boolean,
        val triggersHonestClose: Boolean,
        val sentryStreakEffect: SentryStreakEffect,
    )

    /**
     * The whole vocabulary, spelled out. The strings are copied from the code as it stood at
     * `44f8ba5d`: `CoordinatorParkingDetector` (`"ended"`, `"aborted_false_enter"`,
     * `"aborted_no_movement"`, `"aborted_no_movement_jam"`, `"aborted_no_vehicle"`,
     * `"aborted_response_timeout"`, `"confirmed_$pathLabel"`, `"confirm_failed_$pathLabel"`),
     * `DetectionSessionOutcomes.STOPPED_BY_USER`, and `UnattendedSaveReason.abortedOutcome`
     * (`"aborted_unattended_$key"`).
     */
    private fun expectationFor(outcome: SessionOutcome): Expected = when (outcome) {
        SessionOutcome.Ended ->
            Expected("ended", false, false, SentryStreakEffect.RESETS)
        SessionOutcome.AbortedFalseEnter ->
            Expected("aborted_false_enter", false, true, SentryStreakEffect.EXTENDS)
        SessionOutcome.AbortedNoMovement ->
            Expected("aborted_no_movement", false, true, SentryStreakEffect.EXTENDS)
        SessionOutcome.AbortedNoMovementJam ->
            Expected("aborted_no_movement_jam", false, false, SentryStreakEffect.RESETS)
        SessionOutcome.AbortedNoVehicle ->
            Expected("aborted_no_vehicle", false, false, SentryStreakEffect.RESETS)
        SessionOutcome.AbortedResponseTimeout ->
            Expected("aborted_response_timeout", false, false, SentryStreakEffect.RESETS)
        SessionOutcome.StoppedByUser ->
            Expected("stopped_by_user", false, false, SentryStreakEffect.RESETS)
        // [DET-SUPERSEDE-CANNOT-DISCARD-A-MEASURED-DRIVE-001] Not a new string: the literal the
        // superseded branch of `CoordinatorParkingDetector`'s finally has always logged. Typing it
        // is what stops a coroutine race from deciding between it and "ended".
        SessionOutcome.Superseded ->
            Expected("superseded", false, false, SentryStreakEffect.RESETS)
        is SessionOutcome.AbortedUnattended ->
            Expected("aborted_unattended_gap_anchor", false, false, SentryStreakEffect.RESETS)
        is SessionOutcome.Confirmed ->
            Expected("confirmed_steps+egress", true, false, SentryStreakEffect.RESETS)
        is SessionOutcome.ConfirmFailed ->
            Expected("confirm_failed_steps+egress", false, false, SentryStreakEffect.RESETS)
    }

    /** One instance per arm; the parameterised three carry the sample values used above. */
    private val everyOutcome: List<SessionOutcome> = listOf(
        SessionOutcome.Ended,
        SessionOutcome.AbortedFalseEnter,
        SessionOutcome.AbortedNoMovement,
        SessionOutcome.AbortedNoMovementJam,
        SessionOutcome.AbortedNoVehicle,
        SessionOutcome.AbortedResponseTimeout,
        SessionOutcome.StoppedByUser,
        SessionOutcome.Superseded,
        SessionOutcome.AbortedUnattended("gap_anchor"),
        SessionOutcome.Confirmed("steps+egress"),
        SessionOutcome.ConfirmFailed("steps+egress"),
    )

    @Test
    fun should_serialize_every_outcome_exactly_as_before() {
        everyOutcome.forEach { outcome ->
            assertEquals(expectationFor(outcome).serialized, outcome.serialized, outcome.toString())
        }
    }

    @Test
    fun should_declare_the_membership_every_consumer_used_to_infer_from_the_string() {
        everyOutcome.forEach { outcome ->
            val expected = expectationFor(outcome)
            assertEquals(expected.isConfirmed, outcome.isConfirmed, "isConfirmed of $outcome")
            assertEquals(
                expected.triggersHonestClose,
                outcome.triggersHonestClose,
                "triggersHonestClose of $outcome",
            )
            assertEquals(
                expected.sentryStreakEffect,
                outcome.sentryStreakEffect,
                "sentryStreakEffect of $outcome",
            )
        }
    }

    /**
     * [DET-A-RESOLVED-ARRIVAL-IS-RESOLVED-FOR-ALL-EIGHT-REASONS-001] The fourth membership, and the
     * census that makes a ninth outcome a decision: **exactly** the unattended verdict's own aborts
     * resolve the arrival, and nothing else does.
     *
     * The point of the list is the FALSE side. `AbortedResponseTimeout` is the near miss and it is
     * `false` on purpose: there the verdict was *save exact here* and a confirm GUARD refused it —
     * a different actor, and the backfill's own placement runs through those same guards. A
     * `Confirmed` has a pin already; the silent aborts never reached a park decision at all.
     */
    @Test
    fun should_let_exactly_the_unattended_verdict_resolve_the_arrival() {
        everyOutcome.forEach { outcome ->
            assertEquals(
                outcome is SessionOutcome.AbortedUnattended,
                outcome.resolvesTheArrival,
                "resolvesTheArrival of $outcome",
            )
        }
    }

    /**
     * …and for **all eight** reasons, which is the defect this closes: the service compared against
     * `AbortedUnattended("gap_anchor")` alone, so seven arrivals out of eight were resolved by the
     * coordinator and then re-decided by the safety net a minute later.
     */
    @Test
    fun should_resolve_the_arrival_for_every_one_of_the_eight_unattended_reasons() {
        assertTrue(
            UnattendedSaveReason.entries.size >= MIN_UNATTENDED_REASONS,
            "[${UnattendedSaveReason.entries.size} reasons] the census below is vacuous below " +
                "$MIN_UNATTENDED_REASONS",
        )
        UnattendedSaveReason.entries.forEach { reason ->
            assertTrue(
                SessionOutcome.AbortedUnattended(reason.key).resolvesTheArrival,
                "the coordinator resolved the arrival as nudge-only for ${reason.key}, and the " +
                    "backfill must not re-decide it",
            )
        }
    }

    /** [SessionOutcome.extendsSentryStreak] and its twin can never disagree: both read one source. */
    @Test
    fun should_never_let_the_two_streak_views_contradict_each_other() {
        everyOutcome.forEach { outcome ->
            assertEquals(!outcome.extendsSentryStreak, outcome.resetsSentryStreak, outcome.toString())
        }
    }

    // ── The three consumers, each pinned against what it used to compute ──────

    /**
     * The zone save asked `startsWith("confirmed_")`. Only [SessionOutcome.Confirmed] ever matched
     * — and the string that comes closest to fooling it is one letter away.
     */
    @Test
    fun should_keep_confirm_failed_out_of_confirmed() {
        assertTrue(SessionOutcome.Confirmed("unattended_zone_gap_anchor").isConfirmed)
        assertFalse(SessionOutcome.ConfirmFailed("unattended_zone_gap_anchor").isConfirmed)
        // The declaration and the prefix it replaces must select the SAME outcomes.
        assertEquals(
            everyOutcome.filter { it.serialized.startsWith("confirmed_") },
            everyOutcome.filter { it.isConfirmed },
        )
    }

    /** The honest-close ladder compared against exactly two constants. Same two, no more. */
    @Test
    fun should_run_the_honest_close_ladder_for_the_two_silent_aborts_only() {
        assertEquals(
            listOf("aborted_false_enter", "aborted_no_movement"),
            everyOutcome.filter { it.triggersHonestClose }.map { it.serialized },
        )
    }

    /**
     * The sentry-wake damper compared against the same two constants — and the jam fold is out of
     * BOTH sets. That exclusion arrived by string accident and is now written down twice, once per
     * consumer. [09 §14.4]
     */
    @Test
    fun should_extend_the_storm_streak_for_the_two_walking_aborts_only() {
        assertEquals(
            listOf("aborted_false_enter", "aborted_no_movement"),
            everyOutcome.filter { it.extendsSentryStreak }.map { it.serialized },
        )
        assertFalse(SessionOutcome.AbortedNoMovementJam.extendsSentryStreak)
        assertFalse(SessionOutcome.AbortedNoMovementJam.triggersHonestClose)
    }

    /** [DET-STOP-BUTTON-001] The behaviour that used to be a `when`'s `else`. */
    @Test
    fun should_reset_the_storm_streak_when_the_user_stopped_it_by_hand() {
        assertTrue(SessionOutcome.StoppedByUser.resetsSentryStreak)
        assertFalse(SessionOutcome.StoppedByUser.triggersHonestClose)
    }

    private companion object {
        /** Eight reasons today; half, per the floors doctrine of `GuardrailScope`. */
        const val MIN_UNATTENDED_REASONS = 4
    }
}
