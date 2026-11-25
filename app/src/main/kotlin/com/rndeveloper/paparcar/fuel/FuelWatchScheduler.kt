package com.rndeveloper.paparcar.fuel

import androidx.work.WorkManager
import com.rndeveloper.paparcar.detection.worker.FuelPriceWatchWorker
import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.onboarding.NearbyComplement
import com.rndeveloper.paparcar.domain.preferences.AppPreferences
import com.rndeveloper.paparcar.domain.repository.VehicleRepository
import com.rndeveloper.paparcar.domain.usecase.fuel.ObserveFollowedFuelStationsUseCase
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Installs the daily fuel watch while there is anything to watch, and removes it when there is not.
 * [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001]
 *
 * ⛔ **«Algo que vigilar» ya no es «algo seguido».** [FUEL-A-NEARBY-PRICE-IS-FREE-NEWS-001] Desde que
 * el aviso de lo que pasa a tu ALREDEDOR es free, un usuario que no sigue nada también tiene algo
 * que mirar. Lo que lo arma es **tener un coche que compra en un surtidor** (`fuelType` del
 * vehículo activo, distinto de `ELECTRIC`) — el dato ya existe del alta del coche, antes de llegar
 * a Home. [FUEL-A-NEARBY-ALERT-ARMS-WITH-THE-CAR-NOT-THE-TAB-001] Hasta este ticket el gatillo era
 * **haber preguntado por gasolineras alguna vez** (`NearbyComplement.FUEL` introducido); se
 * mantiene como alternativa — sigue armando el reloj, por si alguna vez hay algo que decir sin
 * tener coche de combustión delante — pero ya no es la ÚNICA puerta. Decisión del user (25-11):
 * el coste de bajar un feed de provincia al día sube a toda cuenta con coche de combustión, y es
 * el tradeoff que compra avisar sin pasar por la pestaña.
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
    private val appPreferences: AppPreferences,
    private val vehicleRepository: VehicleRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start() {
        scope.launch {
            combine(
                observeFollowedFuelStations(),
                appPreferences.observeIntroducedComplements(),
                vehicleRepository.observeActiveVehicle(),
            ) { followed, introduced, activeVehicle ->
                val carBurnsAtAPump = activeVehicle?.fuelType?.let { it != FuelType.ELECTRIC } ?: false
                followed.isNotEmpty() || NearbyComplement.FUEL in introduced || carBurnsAtAPump
            }
                // The COUNT is not the question — whether there is anything to watch is. Following a
                // fourth station must not re-enqueue the periodic and reset its clock.
                .distinctUntilChanged()
                .collect { hasSomethingToWatch ->
                    PaparcarLogger.d(TAG) {
                        if (hasSomethingToWatch) "▶ daily fuel watch armed" else "■ nothing to watch — fuel watch cancelled"
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
