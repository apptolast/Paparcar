package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.usecase.parking.SafetyNetAction
import com.rndeveloper.paparcar.domain.usecase.parking.StillParkedReason
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [DET-EXPLAINED-RIDE-ASKS-NO-OTHER-CAR-001] The cross-session mute of the safety-net tick.
 *
 * Field 2026-08-27 12:29:18 (Oppo, fisio): Focus and Kamiq parked 30 m apart, the user drives the
 * Focus away. The same 2 km displacement got `e1cb2b34` a dispatched departure (trip proof, 4
 * trusted steps) and `a786c135` a real "still parked?" notification, 13 ms apart. The prompt is
 * the instrument asymmetric failure leans on — it must not be spent on a question the same tick
 * already answered.
 */
class ExplainedDepartureTest {

    // The two fences of the field incident: 2008 m vs 2001 m from the same fix — indistinguishable
    // by displacement, distinguishable only by what the tick concluded about each.
    private val dispatchedFence = "e1cb2b34-focus"
    private val promptFence = "a786c135-kamiq"

    private fun prompt(fence: String = promptFence) =
        SafetyNetAction.PromptStillParked(fence, reason = StillParkedReason.UNEXPLAINED_EXIT)

    @Test
    fun should_mute_the_still_parked_prompt_when_a_preconfirmed_departure_shares_the_tick() {
        val muted = stillParkedPromptsExplainedByDeparture(
            listOf(
                SafetyNetAction.DispatchDeparture(
                    geofenceId = dispatchedFence,
                    preconfirmed = true,
                    trustedStepsSinceAnchor = 4L,
                ),
                prompt(),
            )
        )
        assertEquals(setOf(promptFence), muted, "the tick already explained this displacement — the ask is noise")
    }

    @Test
    fun should_mute_regardless_of_evaluation_order_when_the_dispatch_comes_after_the_prompt() {
        // The whole reason the worker HOLDS prompts until the loop ends: the explaining session may
        // be evaluated after the asking one.
        val muted = stillParkedPromptsExplainedByDeparture(
            listOf(
                prompt(),
                SafetyNetAction.DispatchDeparture(geofenceId = dispatchedFence, preconfirmed = true),
            )
        )
        assertEquals(setOf(promptFence), muted)
    }

    @Test
    fun should_keep_the_prompt_when_it_is_the_only_action_of_the_tick() {
        // The bus/taxi case the prompt exists for: nothing explained the displacement, so the
        // human disambiguates. Muting here would silence the safety net's one legitimate question.
        val muted = stillParkedPromptsExplainedByDeparture(listOf(prompt()))
        assertEquals(emptySet(), muted, "an unexplained displacement must still ask")
    }

    @Test
    fun should_keep_the_prompt_when_the_departure_is_live_and_still_unverified() {
        // preconfirmed=false → the departure worker still has to re-verify the exit by speed. An
        // exit that may yet be vetoed cannot silence a question that might be the right one.
        val muted = stillParkedPromptsExplainedByDeparture(
            listOf(
                SafetyNetAction.DispatchDeparture(geofenceId = dispatchedFence, preconfirmed = false),
                prompt(),
            )
        )
        assertEquals(emptySet(), muted, "a live dispatch is not yet an explanation")
    }

    @Test
    fun should_mute_every_prompt_of_the_tick_when_one_departure_explains_the_displacement() {
        // Three cars parked, one driven away: the other two staying parked is the tick's normal
        // conclusion — both asks go silent, not just one.
        val muted = stillParkedPromptsExplainedByDeparture(
            listOf(
                prompt("fence-a"),
                SafetyNetAction.DispatchDeparture(geofenceId = dispatchedFence, preconfirmed = true),
                prompt("fence-b"),
            )
        )
        assertEquals(setOf("fence-a", "fence-b"), muted)
    }

    @Test
    fun should_ignore_unrelated_actions_when_collecting_the_tick() {
        val muted = stillParkedPromptsExplainedByDeparture(
            listOf(
                SafetyNetAction.CureGeofence(geofenceId = "fence-c", radiusMeters = 80f),
                SafetyNetAction.None,
                SafetyNetAction.DispatchDeparture(geofenceId = dispatchedFence, preconfirmed = true),
                prompt(),
            )
        )
        assertEquals(setOf(promptFence), muted)
    }
}
