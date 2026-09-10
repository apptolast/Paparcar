package com.rndeveloper.paparcar.domain.parkings

/**
 * Where to look for car parks, as one value. [PARKINGS-ON-THE-MAP-001]
 *
 * Mirrors [com.rndeveloper.paparcar.domain.fuel.FuelStationsQuery] on purpose — same geocode, same
 * shape — so that adding a city feed under F6b is adding a registry entry, not a new pipeline.
 */
data class PublicParkingsQuery(
    val latitude: Double,
    val longitude: Double,
    /** ISO 3166-1 alpha-2. Empty when the lookup failed — which routes straight to the fallback. */
    val countryCode: String,
    /** Region/city as a geocoder wrote it. Null is normal; only city feeds will need it. */
    val regionName: String? = null,
    val radiusMeters: Int = DEFAULT_RADIUS_METERS,
) {
    companion object {
        /**
         * ⛔ Deliberately **not** the fuel radius (5 km). You drive to a forecourt that is "on the
         * way", but you park at your DESTINATION: a car park 5 km away is not an option, it is a
         * different trip. It is also a volume problem — 300 elements came back within 1 km of
         * central Málaga, capped by the query itself.
         */
        const val DEFAULT_RADIUS_METERS = 1_500
    }
}

/**
 * Car parks near a point, from whoever can answer for that country.
 *
 * **One source per country, and that source answers whole** — the rule
 * [com.rndeveloper.paparcar.domain.fuel.FuelStationsSource] established, for the same reason: two
 * catalogues joined by proximity produce a mismatch that reads like data rather than like a bug.
 *
 * The floor is OpenStreetMap, which covers the world and knows only what is static: where a car park
 * is, roughly what kind it is, and sometimes its name. **How full it is right now is not in here** —
 * that is a live feed per city, it arrives with `PARKINGS-LIVE-OCCUPANCY-001`, and it arrives as
 * another implementation of this same port.
 */
interface PublicParkingsSource {

    /** True when this source can answer for [countryCode] (ISO 3166-1 alpha-2, any case). */
    fun covers(countryCode: String): Boolean

    /**
     * Car parks matching [query], nearest first.
     *
     * Returns a failure rather than an empty list when the network or the feed failed: "none near
     * you" and "we could not ask" are different answers, and the UI must never say the first when it
     * means the second.
     */
    suspend fun parkingsNear(query: PublicParkingsQuery): Result<List<PublicParking>>
}
