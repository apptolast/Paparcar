package com.rndeveloper.paparcar.fuel

import androidx.work.WorkManager
import com.rndeveloper.paparcar.detection.worker.FuelPriceWatchWorker
import com.rndeveloper.paparcar.domain.usecase.fuel.ObserveFollowedFuelStationsUseCase
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Installs the daily fuel watch while there is anything to watch, and removes it when there is not.
 * [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001]
 *
 * ⛔ **A stream, not a read at app start.** Reading the followed set once on launch is exactly the
 * shape [DET-WATCH-REACTIVATE-001] had to undo: on a clean install Room is still empty while the
 * sync lands, so the one read sees "nothing followed" and the watch stays dead until the next
 * process launch. Following a station also has to arm the watch THERE AND THEN, not next time the
 * app is killed and reopened.
 *
 * ⛔ It schedules; it does not gate. Whether the user holds Plus is read live inside the use case at
 * each run, so a lapse stops the notifications on the very next tick whatever this decided — two
 * places deciding entitlement is two places to keep in step.
 */
class FuelWatchScheduler(
    private val workManager: WorkManager,
    private val observeFollowedFuelStations: ObserveFollowedFuelStationsUseCase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start() {
        scope.launch {
            observeFollowedFuelStations()
                .map { it.isNotEmpty() }
                // The COUNT is not the question — whether the set is empty is. Following a fourth
                // station must not re-enqueue the periodic and reset its clock.
                .distinctUntilChanged()
                .collect { hasSomethingToWatch ->
                    PaparcarLogger.d(TAG) {
                        if (hasSomethingToWatch) "▶ daily fuel watch armed" else "■ nothing followed — fuel watch cancelled"
                    }
                    runCatching { FuelPriceWatchWorker.syncSchedule(workManager, hasSomethingToWatch) }
                        .onFailure { PaparcarLogger.w(TAG, "could not sync the fuel watch schedule", it) }
                }
        }
    }

    private companion object {
        const val TAG = "FuelWatch"
    }
}
