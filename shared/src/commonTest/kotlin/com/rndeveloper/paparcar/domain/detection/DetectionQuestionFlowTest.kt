package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.event.MapFocusEventBus
import com.rndeveloper.paparcar.fakes.FakeAppPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNull

class DetectionQuestionFlowTest {
    private val question = StillParkedQuestion("car-fence", 40.4, -3.7, 1_000L)
    @Test fun anAnswerHereSurvivesPersistenceAndSuppressesTheSameEvidence() {
        val restored = StillParkedQuestion.decode(question.copy(confirmedHereAtMs = 2_000L).encode())
        assertEquals(question.copy(confirmedHereAtMs = 2_000L), restored)
        assertFalse(stillParkedQuestionMayRepeat(restored, "car-fence", null))
        assertFalse(stillParkedQuestionMayRepeat(restored, "car-fence", 1_900L))
        assertTrue(stillParkedQuestionMayRepeat(restored, "car-fence", 2_001L))
        assertTrue(stillParkedQuestionMayRepeat(restored, "other-fence", null))
    }
    @Test fun anOldAnswerCannotAnswerTheNextTripOrAnExpiredQuestion() {
        val live = PendingPromptWindow(shownAtMs = 2_000L, vehicleId = "car")
        assertFalse(promptAnswerIsCurrent(live, 1_000L, 2_100L, 10_000L))
        assertFalse(promptAnswerIsCurrent(live, null, 2_100L, 10_000L))
        assertFalse(promptAnswerIsCurrent(null, 2_000L, 2_100L, 10_000L))
        assertFalse(promptAnswerIsCurrent(live, 2_000L, 20_000L, 10_000L))
        assertTrue(promptAnswerIsCurrent(live, 2_000L, 2_100L, 10_000L))
    }
    @Test fun openingANotificationBeforeHomeExistsStillFocusesItsPoint() = runTest {
        val bus = MapFocusEventBus()
        bus.focusAt(40.4, -3.7)
        assertEquals(40.4 to -3.7, bus.events.first())
    }
    @Test fun signOutClearsTheDepartureQuestionAndItsAnswer() {
        val prefs = FakeAppPreferences()
        prefs.setStillParkedQuestion(question)
        prefs.clearUserScopedState()
        assertNull(prefs.stillParkedQuestion())
    }
}
