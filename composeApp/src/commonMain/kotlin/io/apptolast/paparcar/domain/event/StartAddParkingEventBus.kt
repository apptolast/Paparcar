package io.apptolast.paparcar.domain.event

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * One-shot signal asking Home to enter manual "add parking" mode, raised by the cold-start nudge
 * notification ("Marcar mi plaza" / tap). [DET-TOGGLE-002]
 *
 * Unlike [MapFocusEventBus] (a `SharedFlow` with `replay = 0`, which drops emissions made while no
 * collector is active), this uses a CONFLATED [Channel]: the nudge almost always opens the app from
 * cold start, so the request is raised in `MainActivity.onCreate` *before* `HomeViewModel` exists.
 * The channel buffers the latest request until Home subscribes and consumes it exactly once — it is
 * not re-delivered to a later subscriber, so re-entering Home will not re-trigger the mode.
 */
class StartAddParkingEventBus {
    private val _requests = Channel<Unit>(Channel.CONFLATED)
    val requests = _requests.receiveAsFlow()

    fun request() {
        _requests.trySend(Unit)
    }
}
