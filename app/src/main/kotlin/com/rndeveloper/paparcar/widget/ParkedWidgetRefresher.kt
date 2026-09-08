package com.rndeveloper.paparcar.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.rndeveloper.paparcar.domain.detection.DetectionPhase
import com.rndeveloper.paparcar.domain.detection.DetectionRuntimeState
import com.rndeveloper.paparcar.domain.repository.UserParkingRepository
import com.rndeveloper.paparcar.domain.repository.VehicleRepository
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import com.rndeveloper.paparcar.presentation.widget.resolveParkedWidgetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Pushes a [ParkedWidget] re-render whenever the parked state changes. [WIDGET-PARKED-001]
 *
 * The parked state only ever changes from OUR process (UI, detection service, workers all live in
 * it), so an in-process observer covers every transition; the widget provider's
 * `updatePeriodMillis` is the floor for the one case this can't see — a render lost to process
 * death. `updateAll` with no widget placed iterates an empty id list: the cost for the
 * no-widget majority is one Room flow this process already has warm.
 */
class ParkedWidgetRefresher(
    private val context: Context,
    private val userParkingRepository: UserParkingRepository,
    private val vehicleRepository: VehicleRepository,
    /** The live trip, which is NOT in Room. Same process, same singleton the service writes to.
     *  [WIDGET-A-TRIP-IS-NOT-AN-EMPTY-WIDGET-001] */
    private val runtime: DetectionRuntimeState,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start() {
        scope.launch {
            combine(
                userParkingRepository.observeActiveSessions(),
                vehicleRepository.observeVehicles(),
                runtime.isRunning,
                runtime.trip,
                runtime.phase,
            ) { sessions, vehicles, isRunning, trip, phase ->
                resolveParkedWidgetState(
                    sessions = sessions,
                    vehicles = vehicles,
                    isDriving = isRunning,
                    drivingVehicleId = trip?.departingVehicleId,
                    isCandidate = phase == DetectionPhase.Candidate,
                )
            }
                // Still the gate that keeps this cheap: a trip emits at its EDGES (start, candidate,
                // park), not per GPS fix, so a drive costs a handful of re-renders.
                // [WIDGET-A-TRIP-IS-NOT-AN-EMPTY-WIDGET-001]
                .distinctUntilChanged()
                .collect {
                    // Fire-and-forget: a failed widget update must never surface anywhere else.
                    runCatching { ParkedWidget().updateAll(context) }
                        .onFailure { e -> PaparcarLogger.w(TAG, "widget update failed: ${e.message}") }
                }
        }
    }

    private companion object {
        const val TAG = "ParkedWidgetRefresher"
    }
}
