package com.rndeveloper.paparcar.fakes.data.repository

import com.rndeveloper.paparcar.domain.fuel.FuelStation
import com.rndeveloper.paparcar.domain.fuel.FuelStationsQuery
import com.rndeveloper.paparcar.domain.fuel.FuelStationsResult
import com.rndeveloper.paparcar.domain.fuel.FuelStationsSource
import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.model.Money

/**
 * Forecourts for the mock stack — no network, wherever the fake GPS says you are.
 * [FUEL-STATIONS-LIST-001]
 *
 * ⛔ It exists because the mock graph has **no `dataModule`**, so every port `domainModule` resolves
 * has to be declared again in `MockModule` or the app dies where it first needs it. This one died on
 * entering Home: `NoDefinitionFoundException: FuelStationsSource`, measured on the emulator — and it
 * survived a full round of on-device checking because the state gallery calls the composables
 * directly and never builds a `HomeViewModel`. **A gallery pass is not an app pass.**
 *
 * The stations are placed as offsets from the query point rather than at fixed coordinates, so the
 * distances read sensibly wherever the Dev Catalog drops the puck. Prices cover every litre-burning
 * fuel because the list is filtered by the active vehicle's, and a mock that only priced petrol
 * would show an empty section for half the garage and look like a bug.
 */
class FakeFuelStationsSource : FuelStationsSource {

    override fun covers(countryCode: String): Boolean = true

    override suspend fun stationsNear(query: FuelStationsQuery): Result<FuelStationsResult> =
        Result.success(
            FuelStationsResult(
                stations = listOf(
                    station(
                        query = query,
                        id = "mock-1",
                        name = "Repsol",
                        address = "Avenida de la Constitución 121",
                        town = "Madrid",
                        northMeters = 400.0,
                        eastMeters = 250.0,
                        cents = 3,
                    ),
                    station(
                        query = query,
                        id = "mock-2",
                        name = "Cepsa",
                        address = "Calle de Alcalá 480",
                        town = "Madrid",
                        northMeters = -900.0,
                        eastMeters = 600.0,
                        cents = 0,
                    ),
                    // Nameless on purpose: France's official feed carries no station name at all, and
                    // this is the row that has to keep reading like a place rather than a blank.
                    station(
                        query = query,
                        id = "mock-3",
                        name = null,
                        address = "Carretera de Burgos km 14",
                        town = "Alcobendas",
                        northMeters = 2_600.0,
                        eastMeters = -1_500.0,
                        cents = 6,
                    ),
                ),
                reportedAt = "09/09/2026 08:30:00",
            ),
        )

    private fun station(
        query: FuelStationsQuery,
        id: String,
        name: String?,
        address: String,
        town: String,
        northMeters: Double,
        eastMeters: Double,
        cents: Int,
    ): FuelStation {
        val price = { base: Double -> Money(base + cents * CENT, EUR) }
        return FuelStation(
            id = id,
            name = name,
            address = address,
            town = town,
            latitude = query.latitude + northMeters / METERS_PER_DEGREE,
            longitude = query.longitude + eastMeters / METERS_PER_DEGREE,
            prices = mapOf(
                FuelType.GASOLINE to price(BASE_GASOLINE),
                FuelType.DIESEL to price(BASE_DIESEL),
                FuelType.HYBRID to price(BASE_GASOLINE),
                FuelType.PLUG_IN_HYBRID to price(BASE_GASOLINE),
                FuelType.LPG_CNG to price(BASE_LPG),
            ),
        )
    }

    private companion object {
        const val EUR = "EUR"
        const val CENT = 0.01
        const val BASE_GASOLINE = 1.759
        const val BASE_DIESEL = 1.679
        const val BASE_LPG = 0.949

        /**
         * Good enough to spread three pins over a few km at any latitude the Dev Catalog uses; the
         * longitude squeeze is deliberately ignored because nothing here is measured against it.
         */
        const val METERS_PER_DEGREE = 111_320.0
    }
}
