package com.rndeveloper.paparcar.domain.usecase.fuel

import com.rndeveloper.paparcar.domain.fuel.FuelStation
import com.rndeveloper.paparcar.domain.fuel.FuelStationsQuery
import com.rndeveloper.paparcar.domain.fuel.FuelStationsSource
import com.rndeveloper.paparcar.domain.fuel.fuelStationKey
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
     * `source|id` — the identity that survives leaving this answer.
     * [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001]
     *
     * ⛔ Built HERE and not at the call site: the source is a property of the answering feed, and it
     * died at this boundary — `FuelStationsResult` carried it and `NearbyFuelStations` dropped it,
     * so nothing above could name a station in a way that still meant something tomorrow. A follow
     * keyed by the bare id would break the moment two feeds numbered a station alike.
     */
    val stationKey: String,
    /** The feed that answered — travels beside the key so a follow can be stored whole instead of
     *  being reconstructed by splitting a string back apart. */
    val source: String,
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
    /**
     * The PUMP PRODUCT these stations were priced for, never the raw powertrain — a hybrid's answer
     * says GASOLINE because that is the figure inside it. Null when nobody declared what the car
     * burns: nothing was priced, nothing was hidden. [FUEL-A-HYBRID-FILLS-UP-WITH-PETROL-001]
     *
     * ⛔ It is what labels the figure. Echoing the powertrain here would let a caller print
     * «Híbrido» over a petrol price, which is the one pairing the map capsule exists to keep honest.
     */
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
    /**
     * True when the answering feed's prices are near-real-time by construction (Germany's MTS-K).
     * The third freshness sentence, beside Spain's response hour and France's per-price ages.
     * [FUEL-GERMANY-THE-FEED-ANSWERS-WHOLE-001]
     */
    val pricesAreLive: Boolean = false,
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
            // [FUEL-A-POSTCODE-IS-THE-PROVINCE-ID-001] La tercera mitad de lo mismo: el postal ya
            // venía en la respuesta del geocoder y se tiraba en la frontera del dominio.
            postalCode = address?.postalCode,
            radiusMeters = radiusMeters,
        )

        // What this car buys at the pump, which is what every feed is indexed by. A hybrid asking
        // for «hybrid» matched nothing anywhere and came back unpriced in silence.
        // [FUEL-A-HYBRID-FILLS-UP-WITH-PETROL-001]
        val product = fuel?.pumpProduct

        return stations.stationsNear(query).map { result ->
            NearbyFuelStations(
                stations = result.stations
                    // ⛔ Hidden ONLY when the source states a negative. An untagged OSM station is
                    // UNKNOWN, and hiding those would empty the list in most of the world — 56% of
                    // stations measured carry no fuel tag at all.
                    .filterNot { product != null && it.isKnownNotToSell(product) }
                    .map { station ->
                        NearbyFuelStation(
                            station = station,
                            distanceMeters = haversineMeters(
                                latitude, longitude, station.latitude, station.longitude,
                            ),
                            stationKey = fuelStationKey(result.source, station.id),
                            source = result.source,
                            // ⛔ Priced by the PUMP PRODUCT, never by the powertrain — a hybrid
                            // asking for «hybrid» matched nothing anywhere.
                            // [FUEL-A-HYBRID-FILLS-UP-WITH-PETROL-001]
                            price = product?.let { station.prices[it] },
                        )
                    },
                fuel = product,
                reportedAt = result.reportedAt,
                // Carried through untouched: which stations are worth showing and when the app last
                // managed to ask are unrelated questions.
                rememberedAtMs = result.rememberedAtMs,
                // Also carried through untouched, and it is the fact the lane's sentence turns on:
                // whether the country's own feed answered, or whether these are just the forecourts.
                // [HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001]
                didFallBack = result.didFallBack,
                pricesAreLive = result.pricesAreLive,
            )
        }
    }
}
