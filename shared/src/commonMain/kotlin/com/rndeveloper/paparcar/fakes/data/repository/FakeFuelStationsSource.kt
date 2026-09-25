package com.rndeveloper.paparcar.fakes.data.repository

import com.rndeveloper.paparcar.domain.fuel.FuelStation
import com.rndeveloper.paparcar.domain.fuel.FuelStationsQuery
import com.rndeveloper.paparcar.domain.fuel.FuelStationsResult
import com.rndeveloper.paparcar.domain.fuel.FuelStationsSource
import com.rndeveloper.paparcar.domain.fuel.StationPrice
import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.model.Money
import kotlin.time.Clock

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
                // ⛔ Generic names and LOCAL addresses, both on purpose
                // [PLAY-SHOTS-EVERY-LANGUAGE-SHOWS-ITS-OWN-UI-001]. This fixture used to quote
                // "Repsol" and "Cepsa" at Dublin addresses while the header above the list said
                // Dublin — a third-party trademark inside a store capture (the same
                // policy that sent "Mercadona"/"Repsol" out of the 01-09 raws) sitting on top of an
                // address 600 km from the pin. A fake that contradicts its own header teaches the
                // reader to distrust the screen.
                stations = listOf(
                    station(
                        query = query,
                        id = "mock-1",
                        name = "Bayside Service Station",
                        address = "Amiens Street 24",
                        town = "Dublin",
                        northMeters = 400.0,
                        eastMeters = 250.0,
                        cents = 3,
                    ),
                    station(
                        query = query,
                        id = "mock-2",
                        name = "Northgate Fuel",
                        address = "Custom House Quay 8",
                        town = "Dublin",
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
                        address = "North Wall Quay 3",
                        town = "Dublin",
                        northMeters = 2_600.0,
                        eastMeters = -1_500.0,
                        cents = 6,
                    ),
                    station(
                        query = query,
                        id = "mock-4",
                        name = "Harbour Road Fuel",
                        address = "Gardiner Street 51",
                        town = "Dublin",
                        northMeters = -1_400.0,
                        eastMeters = -800.0,
                        cents = 1,
                    ),
                    station(
                        query = query,
                        id = "mock-5",
                        name = "Marina Service Station",
                        address = "East Wall Road 2",
                        town = "Dublin",
                        northMeters = 1_900.0,
                        eastMeters = 2_400.0,
                        cents = 4,
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
        // Dated like a national feed does, so the mock exercises the age line instead of hiding it:
        // an undated price silently skips the hour the real screens show.
        // [FUEL-FRANCE-A-PRICE-CARRIES-ITS-OWN-HOUR-001]
        val price = { base: Double ->
            StationPrice(Money(base + cents * CENT, EUR), mockReportedAtMs())
        }
        return FuelStation(
            id = id,
            name = name,
            address = address,
            town = town,
            latitude = query.latitude + northMeters / METERS_PER_DEGREE,
            longitude = query.longitude + eastMeters / METERS_PER_DEGREE,
            // ⛔ The three products a forecourt actually sells, and NOT ONE MORE. This fake used to
            // also quote HYBRID and PLUG_IN_HYBRID, which no feed on earth emits — and since the
            // catalogue's default car is a hybrid, the fixture answered the one lookup every real
            // source returns null for. The mock set was hiding the exact bug it exists to surface:
            // a hybrid driver saw a priceless row and a priceless capsule in production and a fully
            // priced list in the gallery. [FUEL-A-HYBRID-FILLS-UP-WITH-PETROL-001]
            prices = mapOf(
                FuelType.GASOLINE to price(BASE_GASOLINE),
                FuelType.DIESEL to price(BASE_DIESEL),
                FuelType.LPG_CNG to price(BASE_LPG),
            ),
        )
    }

    /**
     * A couple of hours ago, relative to whenever the catalogue is opened.
     *
     * Not a fixed instant: pinned to a date in 2026 the age line would read "hace 8 meses" forever
     * and nobody could tell a formatting bug from the fixture growing old.
     */
    private fun mockReportedAtMs(): Long =
        Clock.System.now().toEpochMilliseconds() - MOCK_PRICE_AGE_MS

    private companion object {
        const val EUR = "EUR"
        const val CENT = 0.01
        const val MOCK_PRICE_AGE_MS = 2 * 60 * 60 * 1000L
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
