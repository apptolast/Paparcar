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
         * ⛔ Still **not** the fuel radius (5 km). You drive to a forecourt that is "on the way",
         * but you park at your DESTINATION: a car park 5 km away is not an option, it is a
         * different trip.
         *
         * It was 1.500 m, which turned out to be too tight to be useful, and the reason it could not
         * simply be raised is the one [PARKINGS-A-CAPPED-ANSWER-MUST-NOT-DROP-THE-NEAREST-001]
         * exists for — see `OsmPublicParkingParser.MAX_RESULTS`. Measured on 14-09-2026:
         *
         * | Zona | r=3 km | r=5 km |
         * |---|---|---|
         * | Madrid Sol | 259 elementos | 641 |
         * | Málaga centro | ~250 | 557 |
         *
         * 3 km is where two things meet. It covers a destination and its surroundings at about
         * 100 KB a request, and — because `ViewportRefetch` re-asks after HALF the radius — it
         * doubles the panning distance that triggers a new request, from 750 m to 1.500 m. Against a
         * public service that allows **2 asks per IP** that is not a side effect, it is half the
         * reason: fewer, larger answers beat many small ones when the budget is requests rather than
         * bytes. [OVERPASS-A-MIRROR-WALK-MUST-OUTLIVE-THE-SERVER-IT-ASKS-001]
         */
        const val DEFAULT_RADIUS_METERS = 3_000
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
/**
 * An answer about an area, and **whether it is today's**.
 * [CACHE-A-MAP-MUST-NOT-EMPTY-ON-COLD-START-001]
 *
 * ⛔ [rememberedAtMs] is not bookkeeping: without it a car park read off the disk arrives at the map
 * indistinguishable from one fetched a second ago, and the layer claims to be live while it is
 * remembering. The same rule the fuel lane learned about a price
 * [FUEL-AN-AVERAGE-MUST-SAY-WHEN-IT-WAS-MEASURED-001], one layer out: what cannot describe itself
 * ends up described by whoever reads it.
 */
data class PublicParkingsResult(
    val parkings: List<PublicParking>,
    /** Null when this was just fetched; otherwise when the app last actually asked about this area. */
    val rememberedAtMs: Long? = null,
)

interface PublicParkingsSource {

    /** True when this source can answer for [countryCode] (ISO 3166-1 alpha-2, any case). */
    fun covers(countryCode: String): Boolean

    /**
     * Car parks matching [query], nearest first.
     *
     * Returns a failure rather than an empty list when the network or the feed failed: "none near
     * you" and "we could not ask" are different answers, and the UI must never say the first when it
     * means the second. A remembered answer is a third: it says something, and says how old it is.
     */
    suspend fun parkingsNear(query: PublicParkingsQuery): Result<PublicParkingsResult>

    /**
     * What this source can already say about [query] **without asking anybody**, or null when
     * nothing was ever recorded about it.
     * [PARKINGS-A-REMEMBERED-MAP-ANSWERS-BEFORE-THE-NETWORK-001]
     *
     * ⛔ This is NOT a second flavour of [parkingsNear] and it must never replace it. It exists so
     * the lane can paint the area it already knows **while** the live answer is in flight, instead
     * of showing a skeleton for the 1,4–8 s Overpass takes (40 s in the worst case, once the quota
     * wait and the mirror walk are counted). The live answer still wins the moment it arrives.
     *
     * Null is the honest default: a source that can only ask remembers nothing. Only the layers
     * that HOLD something — memory, disk — override it.
     */
    suspend fun remembered(query: PublicParkingsQuery): PublicParkingsResult? = null
}
