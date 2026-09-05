@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.rndeveloper.paparcar.detection

import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.CLVisit
import platform.darwin.NSObject

/**
 * The iOS mesh's between-trips wake primitives: Significant Location Changes (~500 m / cell
 * handoff) and `CLVisit` (arriving at / leaving a place). Both RELAUNCH the terminated app —
 * with Always authorization the OS starts the process and delivers the event, which is exactly
 * when the safety net must look at reality. Cheap by design: no live GPS session, the OS decides
 * the cadence. (Plan §2.1 — the "sentry" the OS keeps for us.)
 * [IOS-F2-A-WAKE-MUST-QUERY-THE-PAST-001]
 *
 * BGAppRefreshTask is NOT here on purpose: task registration must happen in the Swift app
 * delegate before launch finishes, and the Info.plist identifiers land with the sync-queues
 * ticket (IOS-SYNC-A-QUEUE-THAT-DIES-WITH-THE-PROCESS-001) — one registration site, not two.
 */
class IosWakeMonitors {

    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var manager: CLLocationManager? = null
    private var delegate: WakeDelegate? = null

    /** Idempotent. [onWake] receives a short source tag (`slc-wake` / `visit-wake`). */
    fun start(onWake: (source: String) -> Unit) {
        if (manager != null) return
        mainScope.launch {
            val mgr = CLLocationManager()
            val del = WakeDelegate(onWake)
            mgr.delegate = del
            mgr.startMonitoringSignificantLocationChanges()
            mgr.startMonitoringVisits()
            manager = mgr
            delegate = del
            PaparcarLogger.d(TAG, "SLC + visit monitoring started")
        }
    }
}

private class WakeDelegate(
    private val onWake: (source: String) -> Unit,
) : NSObject(), CLLocationManagerDelegateProtocol {

    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        onWake("slc-wake")
    }

    override fun locationManager(manager: CLLocationManager, didVisit: CLVisit) {
        onWake("visit-wake")
    }
}

private const val TAG = "IosWakeMonitors"
