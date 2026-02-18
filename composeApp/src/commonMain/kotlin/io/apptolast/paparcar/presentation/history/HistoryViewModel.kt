package io.apptolast.paparcar.presentation.history

import io.apptolast.paparcar.presentation.base.BaseViewModel

class HistoryViewModel : BaseViewModel<HistoryState, HistoryIntent, HistoryEffect>() {

    override fun initState(): HistoryState = HistoryState()

    override fun handleIntent(intent: HistoryIntent) {
        when (intent) {
            is HistoryIntent.LoadHistory -> {
                // TODO: Load history from use case
            }
            is HistoryIntent.SpotSelected -> {
                sendEffect(HistoryEffect.NavigateToSpotDetails(intent.spotId))
            }
        }
    }
}
