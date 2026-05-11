@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.apptolast.paparcar.connectivity

import io.apptolast.paparcar.domain.connectivity.ConnectivityObserver
import io.apptolast.paparcar.domain.connectivity.ConnectivityStatus
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_monitor_t
import platform.Network.nw_path_status_satisfied
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.dispatch_get_global_queue

/**
 * iOS implementation of [ConnectivityObserver] backed by Network framework's
 * `nw_path_monitor` (iOS 12+).
 *
 * Mirrors [AndroidConnectivityObserver] semantics:
 * - `start()` registers the path-update handler on a global dispatch queue.
 *   The first callback fires almost immediately with the current path status.
 * - `stop()` cancels the monitor.
 *
 * The handler runs off the main queue (background). [MutableStateFlow] is
 * thread-safe, so we update directly. Consumers observe via `collectAsState`
 * which dispatches recomposition on Main.
 *
 * Lifecycle ownership: the AppDelegate (or `MainViewController`) calls `start()`
 * once at launch and `stop()` on terminate. Never wire from a Composable —
 * a per-screen `DisposableEffect` would churn the monitor on every navigation.
 */
class IosConnectivityObserver : ConnectivityObserver {

    private val _status = MutableStateFlow(ConnectivityStatus.Online)
    override val status: StateFlow<ConnectivityStatus> = _status.asStateFlow()

    private var monitor: nw_path_monitor_t = null

    override fun start() {
        if (monitor != null) return
        runCatching {
            val m = nw_path_monitor_create()
            nw_path_monitor_set_update_handler(m) { path ->
                val online = nw_path_get_status(path) == nw_path_status_satisfied
                _status.value = if (online) ConnectivityStatus.Online else ConnectivityStatus.Offline
            }
            val queue = dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)
            nw_path_monitor_set_queue(m, queue)
            nw_path_monitor_start(m)
            monitor = m
        }.onFailure { e ->
            PaparcarLogger.w(TAG, "nw_path_monitor start failed", e)
        }
    }

    override fun stop() {
        monitor?.let {
            runCatching { nw_path_monitor_cancel(it) }
                .onFailure { e -> PaparcarLogger.w(TAG, "nw_path_monitor cancel failed", e) }
        }
        monitor = null
    }

    private companion object {
        const val TAG = "IosConnectivityObserver"
    }
}
