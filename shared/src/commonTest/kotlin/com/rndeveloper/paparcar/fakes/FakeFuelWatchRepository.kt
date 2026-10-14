package com.rndeveloper.paparcar.fakes

import com.rndeveloper.paparcar.domain.fuel.FollowedFuelStation
import com.rndeveloper.paparcar.domain.fuel.FuelPriceSample
import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.repository.FuelWatchRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory watch store. [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001]
 *
 * ⛔ [recordSamples] collapses by (station, fuel, day) exactly as the Room primary key does. A fake
 * that appended instead would let a test pass over a history the real store can never produce —
 * which is the whole reason the series is keyed by day.
 */
class FakeFuelWatchRepository : FuelWatchRepository {

    private val followed = MutableStateFlow<List<FollowedFuelStation>>(emptyList())
    private val samples = mutableListOf<FuelPriceSample>()

    var pruneCount: Int = 0
        private set

    override fun observeFollowed(): Flow<List<FollowedFuelStation>> = followed

    override suspend fun getFollowed(): List<FollowedFuelStation> = followed.value

    override suspend fun follow(station: FollowedFuelStation) {
        followed.value = followed.value.filterNot { it.stationKey == station.stationKey } + station
    }

    /**
     * ⛔ **Deja de seguir y la serie se QUEDA**, que es lo que hace el real desde
     * [FUEL-A-NEARBY-PRICE-IS-FREE-NEWS-001]: un unfollow ya no borra el historial, le encoge la
     * ventana de 30 días a 3. Aquí antes se borraba entero, y eso modelaba una regla que el almacén
     * ya no tiene — un fake que la conservara dejaría pasar tests sobre un producto que no existe.
     *
     * Y no encoge nada, a propósito: este doble **no modela las ventanas** (su [prune] es un
     * contador y su [historyFor] ignora `days`), así que fingir un recorte con un reloj inventado
     * sería sustituir una mentira por otra. La retención se prueba contra Room, donde vive la
     * consulta: `FuelWatchStoreTest`.
     */
    override suspend fun unfollow(stationKey: String) {
        followed.value = followed.value.filterNot { it.stationKey == stationKey }
    }

    override suspend fun recordSamples(samples: List<FuelPriceSample>) {
        samples.forEach { sample ->
            this.samples.removeAll {
                it.stationKey == sample.stationKey &&
                    it.fuelType == sample.fuelType &&
                    it.observedDay == sample.observedDay
            }
            this.samples += sample
        }
    }

    override suspend fun historyFor(stationKey: String, fuelType: FuelType, days: Int): List<FuelPriceSample> =
        samples.filter { it.stationKey == stationKey && it.fuelType == fuelType }
            .sortedBy { it.observedDay }

    override fun observeHistoryFor(stationKey: String, fuelType: FuelType, days: Int): Flow<List<FuelPriceSample>> =
        followed.map { historyFor(stationKey, fuelType, days) }

    override suspend fun prune() {
        pruneCount++
    }
}
