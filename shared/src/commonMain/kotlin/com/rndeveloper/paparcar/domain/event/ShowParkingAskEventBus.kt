package com.rndeveloper.paparcar.domain.event

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * One-shot signal asking Home to SHOW a parking — camera on the pin, its card (peek) selected —
 * raised by the widget's "more" action. [WIDGET-ACTIONS-002]
 *
 * Same CONFLATED-channel shape as [StartAddParkingEventBus] and for the same reason: the widget
 * almost always opens the app from cold start, so the ask is raised in `MainActivity` before
 * `HomeViewModel` exists; the channel buffers the latest ask until Home subscribes.
 *
 * Deliberately a SHOW, never an action: the card the user lands on carries every option (leave,
 * directions, edit) with its own confirmations — the launcher never gets a one-tap irreversible
 * verb. Home validates the session is STILL active; a stale ask dies silently.
 */
class ShowParkingAskEventBus {
    private val _asks = Channel<String>(Channel.CONFLATED)
    val asks = _asks.receiveAsFlow()

    fun ask(sessionId: String) {
        _asks.trySend(sessionId)
    }
}
