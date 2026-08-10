package io.apptolast.paparcar.domain.event

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * One-shot signal asking Home to enter "add parking" pin mode, raised by the nudge
 * notifications ("Marcar mi plaza" / tap). [DET-TOGGLE-002]
 *
 * Unlike [MapFocusEventBus] (a `SharedFlow` with `replay = 0`, which drops emissions made while no
 * collector is active), this uses a CONFLATED [Channel]: the nudge almost always opens the app from
 * cold start, so the request is raised in `MainActivity.onCreate` *before* `HomeViewModel` exists.
 * The channel buffers the latest request until Home subscribes and consumes it exactly once — it is
 * not re-delivered to a later subscriber, so re-entering Home will not re-trigger the mode.
 */
class StartAddParkingEventBus {
    private val _requests = Channel<StartAddParkingRequest>(Channel.CONFLATED)
    val requests = _requests.receiveAsFlow()

    fun request(fromDetectionNudge: Boolean) {
        _requests.trySend(StartAddParkingRequest(fromDetectionNudge))
    }
}

/**
 * @param fromDetectionNudge true when the ask originated from a DETECTION nudge (the coordinator
 *   detected a trip but could not place the car — `showMarkParkingNudge`): the confirmed pin keeps
 *   detection provenance (`AUTO_DETECTED`, path `nudge`). False for user-initiated asks (first-park
 *   onboarding nudge) → plain manual pin. [DET-NUDGE-PIN-PROVENANCE-001]
 */
data class StartAddParkingRequest(val fromDetectionNudge: Boolean)
