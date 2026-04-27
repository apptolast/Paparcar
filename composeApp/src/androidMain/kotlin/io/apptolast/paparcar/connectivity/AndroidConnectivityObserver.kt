package io.apptolast.paparcar.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import io.apptolast.paparcar.domain.connectivity.ConnectivityObserver
import io.apptolast.paparcar.domain.connectivity.ConnectivityStatus
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidConnectivityObserver(
    context: Context,
) : ConnectivityObserver {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _status = MutableStateFlow(currentStatus())
    override val status: StateFlow<ConnectivityStatus> = _status.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _status.value = ConnectivityStatus.Online
        }

        override fun onLost(network: Network) {
            _status.value = currentStatus()
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            _status.value = if (capabilities.hasInternetCapability()) {
                ConnectivityStatus.Online
            } else {
                ConnectivityStatus.Offline
            }
        }
    }

    override fun start() {
        runCatching {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, callback)
        }.onFailure { e ->
            PaparcarLogger.w(TAG, "registerNetworkCallback failed", e)
        }
        _status.value = currentStatus()
    }

    override fun stop() {
        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
            .onFailure { e -> PaparcarLogger.w(TAG, "unregisterNetworkCallback failed", e) }
    }

    private fun currentStatus(): ConnectivityStatus {
        val capabilities = connectivityManager.activeNetwork
            ?.let(connectivityManager::getNetworkCapabilities)
        return if (capabilities?.hasInternetCapability() == true) {
            ConnectivityStatus.Online
        } else {
            ConnectivityStatus.Offline
        }
    }

    private fun NetworkCapabilities.hasInternetCapability(): Boolean =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

    private companion object {
        const val TAG = "AndroidConnectivityObserver"
    }
}
