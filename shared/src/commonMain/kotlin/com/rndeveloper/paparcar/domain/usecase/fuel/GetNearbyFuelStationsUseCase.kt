package com.rndeveloper.paparcar.domain.usecase.fuel

import com.rndeveloper.paparcar.domain.fuel.FuelStation
import com.rndeveloper.paparcar.domain.fuel.FuelStationsQuery
import com.rndeveloper.paparcar.domain.fuel.FuelStationsSource
import com.rndeveloper.paparcar.domain.geocoder.GeocoderDataSource
import com.rndeveloper.paparcar.domain.fuel.StationPrice
import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.util.haversineMeters

/**
 * One station, as the list needs it: how far, and what it charges for THIS car's fuel.
 * [FUEL-STATIONS-LIST-001]
 */
data class NearbyFuelStation(
    val station: FuelStation,
    val distanceMeters: Double,
    /**
     * Null where the source has no price for this fuel — never a zero, never a substitute.
     *
     * Carries its own hour, because in France it genuinely has one: prices inside a single response
     * were measured from 0,7 h to thirteen months old.
     * [FUEL-FRANCE-A-PRICE-CARRIES-ITS-OWN-HOUR-001]
     */
    val price: StationPrice?,
)

/** The answer, with the two things that make it readable: what was asked, and when it was said. */
data class NearbyFuelStations(
    val stations: List<NearbyFuelStation>,
    /** Null when nobody declared what the car burns — nothing was priced, nothing was hidden. */
    val fuel: FuelType?,
    /** The feed's own timestamp; null when the source carries no prices at all (OSM). */
    val reportedAt: String?,
    /**
     * Set when this list came off the DISK because the live answer failed — when the app last
     * managed to ask. [CACHE-A-MAP-MUST-NOT-EMPTY-ON-COLD-START-001]
     *
     * ⛔ Not a flavour of [reportedAt]: that one is the feed's stamp on a response, this one is ours
     * on the asking, and a remembered answer has no response to stamp.
     */
    val rememberedAtMs: Long? = null,
    /**
     * True when this country HAS a national feed and it could not answer, so these forecourts came
     * from the worldwide fallback. [HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001]
     */
    val didFallBack: Boolean = false,
) {
    /** True when at least one station could be priced — what tells the UI which sentence to say. */
    val hasPrices: Boolean get() = stations.any { it.price != null }
}

/**
 * Where can I refuel this car, and at what. [FUEL-STATIONS-LIST-001]
 *
 * A verdict, not a predicate: its result is what the screen shows and what a diagnosis would quote.
 *
 * The geocode is what routes the request — [com.rndeveloper.paparcar.domain.model.AddressInfo]
 * already carries the `countryCode` the registry needs and the `region` Spain's per-province feed
 * cannot work without, so no new lookup is invented here.
 */
class GetNearbyFuelStationsUseCase(
    private val stations: FuelStationsSource,
    private val geocoder: GeocoderDataSource,
) {

    /**
     * [fuel] is nullable since the map layer: "where can I refuel" is a fair question for a car
     * whose fuel nobody has declared. With no fuel there is nothing to price and nothing that may
     * be hidden — every station stays visible, unpriced, which is honest either way.
     * [FUEL-STATIONS-MAP-AND-PEEK-001]
     */
    suspend operator fun invoke(
        latitude: Double,
        longitude: Double,
        fuel: FuelType?,
        radiusMeters: Int = FuelStationsQuery.DEFAULT_RADIUS_METERS,
    ): Result<NearbyFuelStations> {
        // A failed geocode is not a failed request: the fallback covers the world and does not need
        // a region, so an unknown country still gets the user a list of places to refuel.
        val address = geocoder.getAddress(latitude, longitude).getOrNull()

        val query = FuelStationsQuery(
            latitude = latitude,
            longitude = longitude,
            countryCode = address?.countryCode.orEmpty(),
            // Province first: in Spain `region` is the autonomous community ("Comunidad de
            // Madrid") and the feed is indexed by PROVINCE, so routing on the region left every
            // multi-province community silently unpriced. The fallback keeps single-province
            // communities working where the geocoder emits no province at all.
            // [FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001]
            regionName = address?.province ?: address?.region,
            // [HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001] The town, because the
            // line above can land on an autonomous community and no Spanish province is called
            // «Andalucía». A source that needs a province can get one from the town; nothing can
            // get one from a region that is not one.
            cityName = address?.city,
            radiusMeters = radiusMeters,
        )

        return stations.stationsNear(query).map { result ->
            NearbyFuelStations(
                stations = result.stations
                    // ⛔ Hidden ONLY when the source states a negative. An untagged OSM station is
                    // UNKNOWN, and hiding those would empty the list in most of the world — 56% of
                    // stations measured carry no fuel tag at all.
                    .filterNot { fuel != null && it.isKnownNotToSell(fuel) }
                    .map { station ->
                        NearbyFuelStation(
                            station = station,
                            distanceMeters = haversineMeters(
                                latitude, longitude, station.latitude, station.longitude,
                            ),
                            price = fuel?.let { station.prices[it] },
                        )
                    },
                fuel = fuel,
                reportedAt = result.reportedAt,
                // Carried through untouched: which stations are worth showing and when the app last
                // managed to ask are unrelated questions.
                rememberedAtMs = result.rememberedAtMs,
                // Also carried through untouched, and it is the fact the lane's sentence turns on:
                // whether the country's own feed answered, or whether these are just the forecourts.
                // [HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001]
                didFallBack = result.didFallBack,
            )
        }
    }
}
