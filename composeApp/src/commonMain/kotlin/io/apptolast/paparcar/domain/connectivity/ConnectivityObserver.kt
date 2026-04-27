package io.apptolast.paparcar.domain.connectivity

import kotlinx.coroutines.flow.StateFlow

/**
 * Reactive global view of network reachability.
 *
 * Exposed as a hot [StateFlow] so multiple consumers (root scaffold banner,
 * snackbar host, ViewModels reacting to reconnects) share a single underlying
 * platform callback registration.
 *
 * Lifecycle is owned by [MainActivity] / equivalent platform host — never by a
 * Composable, since a `DisposableEffect` per screen would re-register the
 * platform callback on every navigation.
 */
interface ConnectivityObserver {
    val status: StateFlow<ConnectivityStatus>
    fun start()
    fun stop()
}
