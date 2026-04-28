package io.apptolast.paparcar.fakes

import io.apptolast.paparcar.domain.connectivity.ConnectivityObserver
import io.apptolast.paparcar.domain.connectivity.ConnectivityStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeConnectivityObserver(
    initialStatus: ConnectivityStatus = ConnectivityStatus.Online,
) : ConnectivityObserver {

    private val _status = MutableStateFlow(initialStatus)
    override val status: StateFlow<ConnectivityStatus> = _status

    var startCount = 0
        private set
    var stopCount = 0
        private set

    override fun start() { startCount++ }
    override fun stop() { stopCount++ }

    fun emit(status: ConnectivityStatus) { _status.value = status }
}
