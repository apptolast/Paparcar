package com.rndeveloper.paparcar.domain.usecase.parking

import com.rndeveloper.paparcar.domain.analytics.AnalyticsEvent
import com.rndeveloper.paparcar.domain.analytics.AnalyticsTracker
import com.rndeveloper.paparcar.domain.model.RefuelStopAnswer
import com.rndeveloper.paparcar.domain.repository.UserParkingRepository

/**
 * «¿Repostaste aquí?» — the user's answer, from wherever they gave it.
 * [DET-A-STOP-AT-A-PUMP-IS-NOT-A-PARKING-001]
 *
 * A VERDICT and not a predicate: the answer is what the refuel figure counts and what makes the
 * question stop being asked, so it is quotable in a diagnosis of either.
 * [DET-VERDICT-NOT-PREDICATE-001]
 *
 * ⛔ **Both surfaces land here**, which is the whole reason it exists as a use case rather than a
 * repository call each screen makes for itself: Home asks on the way back to the car and the
 * vehicle's history asks days later, and the ticket's requirement is that answering in either
 * resolves the SAME record. One door, one write, one place to look when they disagree.
 *
 * ⛔ **It does not touch the publication.** Whether a plaza went out was decided at the departure,
 * from what was measured then; a «No» arriving afterwards does not hand a forecourt to the
 * community, and a «Sí» does not take back a plaza that was never published.
 */
class AnswerRefuelStopUseCase(
    private val userParkingRepository: UserParkingRepository,
    private val analyticsTracker: AnalyticsTracker? = null,
) {

    /**
     * @param refuelled the user's own words: true = «Sí», false = «No». Mapped here rather than at
     *   the call sites so no screen has to know the enum, and so neither can ever send
     *   [RefuelStopAnswer.PENDING] — a state only the detection may write.
     */
    suspend operator fun invoke(sessionId: String, refuelled: Boolean): Result<Unit> =
        userParkingRepository.answerRefuelStop(
            sessionId = sessionId,
            answer = if (refuelled) RefuelStopAnswer.CONFIRMED else RefuelStopAnswer.DISMISSED,
        ).onSuccess {
            analyticsTracker?.track(AnalyticsEvent.RefuelStopAnswered(refuelled))
        }
}
