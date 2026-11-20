package com.rndeveloper.paparcar.domain.usecase.parking

import com.rndeveloper.paparcar.domain.detection.ParkingAskBudget
import com.rndeveloper.paparcar.domain.detection.ParkingAskKind
import com.rndeveloper.paparcar.domain.detection.parkingAskIsDue
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What the door did, in words a diagnostic can quote. */
sealed interface ParkingAskOutcome {

    /** The question reached the user. [budgeted] is false only when the caller had no vehicle to
     *  key the budget on: the ask still goes out — losing a car is worse than asking twice — but
     *  nothing was spent, and the trace says so instead of implying a stamp that does not exist. */
    data class Raised(val kind: ParkingAskKind, val budgeted: Boolean) : ParkingAskOutcome

    /** The budget for this car is still spent; [askedAgoMs] is how long ago it went. */
    data class Suppressed(val kind: ParkingAskKind, val askedAgoMs: Long) : ParkingAskOutcome

    val kindAsked: ParkingAskKind
        get() = when (this) {
            is Raised -> kind
            is Suppressed -> kind
        }
}

/**
 * **The only door through which Paparcar may interrupt a human about a parked car.**
 * [DET-AN-HONEST-CLOSE-THAT-GOT-IT-RIGHT-MUST-NOT-APOLOGISE-001]
 *
 * [DET-A-STILL-PARKED-ASK-MUST-EARN-ITS-INTERRUPTION-001] built this door for the parked-session
 * net and swept that worker's two questions through it. What it could not see from inside one file
 * is that the same notification has FIVE callers, and the busiest of them — the honest close — had
 * no door at all: it saved an artifact and notified, unconditionally, by design ("Never silent").
 * Measured over the live corpus on 20-11-2026: 33 guaranteed interruptions across the only three
 * accounts with detection activity, ~1 a day on the field device, and three of every four landed
 * on a mark that was already correct.
 *
 * A use case rather than another pure predicate because its result IS citable in a diagnostic
 * (`honest_close_ask` vs the silence of a spent budget) and because it owns the write — stamp
 * before show, so a crash between the two costs one unasked question and never an open door.
 * [DET-VERDICT-NOT-PREDICATE-001]
 *
 * ⛔ What this door does NOT decide is whether the question is WORTH asking. That belongs to
 * whoever holds the evidence — for the honest close, [HonestCloseVerdict.asksTheUser], which
 * compares the doubt the saved artifact carries against the car's own fence. The door only rations
 * interruptions; rationing a question that should never have been asked would just spend the budget
 * on noise and silence the next real one.
 */
class RaiseParkingAskUseCase(
    private val budget: ParkingAskBudget,
    private val config: ParkingDetectionConfig,
) {

    private val mutex = Mutex()

    /**
     * @param vehicleId the car the question is about — the budget's key. Null only where the caller
     *   genuinely has no vehicle (a ghost pending whose owner died with it).
     * @param show raises the actual notification. Invoked at most once, after the stamp.
     */
    suspend operator fun invoke(
        vehicleId: String?,
        kind: ParkingAskKind,
        nowMs: Long,
        show: () -> Unit,
    ): ParkingAskOutcome = mutex.withLock {
        if (vehicleId.isNullOrBlank()) {
            show()
            return@withLock ParkingAskOutcome.Raised(kind, budgeted = false)
        }
        val lastAskAt = budget.lastAskAtMs(vehicleId)
        if (!parkingAskIsDue(lastAskAt, nowMs, config.safetyNetAskThrottleMs)) {
            return@withLock ParkingAskOutcome.Suppressed(kind, askedAgoMs = nowMs - (lastAskAt ?: nowMs))
        }
        // Stamped BEFORE the notification: a crash between the two must cost one unasked question,
        // never an unthrottled door. Same order, and the same reason, as the departure adjudication.
        budget.stampAsk(vehicleId, nowMs)
        show()
        return@withLock ParkingAskOutcome.Raised(kind, budgeted = true)
    }
}
