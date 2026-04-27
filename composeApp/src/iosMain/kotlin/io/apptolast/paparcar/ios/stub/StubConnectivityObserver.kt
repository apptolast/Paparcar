package io.apptolast.paparcar.ios.stub

import io.apptolast.paparcar.domain.connectivity.ConnectivityObserver
import io.apptolast.paparcar.domain.connectivity.ConnectivityStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class StubConnectivityObserver : ConnectivityObserver {
    private val _status = MutableStateFlow(ConnectivityStatus.Online)
    override val status: StateFlow<ConnectivityStatus> = _status.asStateFlow()
    override fun start() = Unit
    override fun stop() = Unit
}
