package com.rndeveloper.paparcar.domain.usecase.parkings

import com.rndeveloper.paparcar.domain.parkings.ParkingZoneGrid
import com.rndeveloper.paparcar.domain.parkings.FeeInfo
import com.rndeveloper.paparcar.domain.parkings.PublicParking
import com.rndeveloper.paparcar.domain.parkings.PublicParkingsQuery
import com.rndeveloper.paparcar.domain.parkings.PublicParkingsResult
import com.rndeveloper.paparcar.domain.parkings.PublicParkingsSource
import com.rndeveloper.paparcar.domain.util.haversineMeters

/** One car park, as a row and a marker need it: which one, and how far. */
data class NearbyPublicParking(
    val parking: PublicParking,
    val distanceMeters: Double,
)

/**
 * The answer for an area: the car parks, and whether the app is **looking or remembering**.
 * [CACHE-A-MAP-MUST-NOT-EMPTY-ON-COLD-START-001]
 *
 * Shaped like [com.rndeveloper.paparcar.domain.usecase.fuel.NearbyFuelStations], which already had
 * to carry a stamp beside its list for the same reason.
 */
data class NearbyPublicParkings(
    val parkings: List<NearbyPublicParking>,
    /** Null when this was just fetched; otherwise when the app last actually asked about this area. */
    val rememberedAtMs: Long? = null,
)

/**
 * Where can I leave the car around here. [PARKINGS-ON-THE-MAP-001]
 *
 * A verdict, not a predicate: its result is what the sheet lists, what the layer draws, and what a
 * diagnosis would quote.
 *
 * ⛔ **No geocode, and its absence is the point.**
 * [PARKINGS-A-REMEMBERED-MAP-ANSWERS-BEFORE-THE-NETWORK-001]
 *
 * This used to mirror the fuel lane and look up the address before asking, "so the request is routed
 * like fuel's". It never was: there is no registry over this port —
 * `OsmPublicParkingsSource.covers()` answers `true` for everywhere — so **nobody read the country**.
 * What it did instead was cost two things:
 * - the platform geocoder's latency, in front of every ask, with no deadline of its own (it leans on
 *   a Play-services component that is regularly mute for the first seconds after a fresh install —
 *   which is exactly when a new user opens the map);
 * - a **duplicate request per zone**, because the country lands in the memory cache key: the first
 *   ask keys on `""`, the geocoder wakes up, the second keys on `"es"`, and the same zone is fetched
 *   from Overpass twice.
 *
 * `PublicParkingsQuery.countryCode` stays on the query because `PARKINGS-LIVE-OCCUPANCY-001` will
 * need it the day a city feed enriches this base. ⚠️ When that day comes, the country must NOT come
 * back as a blocking lookup in front of the ask — the geocoder cache (`geocoder_cache`, 30 days) is
 * where to get it from.
 */
class GetNearbyPublicParkingsUseCase(
    private val parkings: PublicParkingsSource,
) {

    /**
     * ⛔ **Asked about the ZONE, answered about YOU.**
     * [PARKINGS-A-ZONE-IS-ASKED-ONCE-AND-PANNED-FOR-FREE-001]
     *
     * The request goes out for the zone's centre and its whole reach, so that panning within the
     * zone produces a byte-identical query — a cache hit on our disk and, failing that, on Overpass's
     * own. The ANSWER is still about the caller: distances are measured from where they actually are,
     * and anything past the display radius is dropped here rather than drawn kilometres away.
     *
     * Those two are separate on purpose. Sharing one radius would either shrink the zone (and lose
     * the caching) or widen the list (and put a car park 5 km away in it, which is a different trip).
     */
    suspend operator fun invoke(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int = PublicParkingsQuery.DEFAULT_RADIUS_METERS,
    ): Result<NearbyPublicParkings> =
        parkings.parkingsNear(queryFor(latitude, longitude))
            .map { answeredFor(it, latitude, longitude, radiusMeters) }

    /**
     * What the app can already say about this area **without asking anybody**, or null when it has
     * never been here. [PARKINGS-A-REMEMBERED-MAP-ANSWERS-BEFORE-THE-NETWORK-001]
     *
     * ⛔ Not an alternative to [invoke] and never a substitute for it: it is what the lane paints
     * **while** [invoke] is in flight, so a zone that was visited in the last 30 days appears at
     * once instead of behind a skeleton for however long Overpass takes. It always carries
     * `rememberedAtMs`, so no surface can mistake it for a fresh answer.
     *
     * The same zone, the same filtering and the same distances as [invoke] — sharing
     * [queryFor] and [answeredFor] rather than repeating them, because two copies of "which car
     * parks are worth showing" is how the remembered list and the live one end up disagreeing.
     */
    suspend fun remembered(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int = PublicParkingsQuery.DEFAULT_RADIUS_METERS,
    ): NearbyPublicParkings? = parkings
        .remembered(queryFor(latitude, longitude))
        ?.let { answeredFor(it, latitude, longitude, radiusMeters) }

    private fun queryFor(latitude: Double, longitude: Double): PublicParkingsQuery {
        val zone = ParkingZoneGrid.zoneOf(latitude, longitude)
        return PublicParkingsQuery(
            latitude = zone.centreLatitude,
            longitude = zone.centreLongitude,
            countryCode = "",
            radiusMeters = ParkingZoneGrid.ZONE_RADIUS_METERS,
        )
    }

    /**
     * The one place an answer ABOUT AN AREA becomes an answer ABOUT THE CALLER: dropped to the car
     * parks somebody could be sent to, measured from where the user actually is, and cut at the
     * display radius.
     *
     * ⛔ Takes the result as a parameter instead of being an extension on it, because `parkings`
     * names two different things in this file — the source this class holds, and the list inside the
     * result — and a reader who resolves it the other way reads a different program.
     */
    private fun answeredFor(
        found: PublicParkingsResult,
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
    ) = NearbyPublicParkings(
        parkings = found.parkings
            .filter { it.isIdentifiable }
            .map {
                NearbyPublicParking(
                    parking = it,
                    distanceMeters = haversineMeters(latitude, longitude, it.latitude, it.longitude),
                )
            }
            .filter { it.distanceMeters <= radiusMeters }
            .sortedBy { it.distanceMeters },
        // Carried through untouched: the filtering above changes WHICH car parks are worth
        // showing, never when the app last managed to ask.
        rememberedAtMs = found.rememberedAtMs,
    )
}

/**
 * Whether this is a car park somebody could be **sent to**, rather than a polygon somebody drew.
 *
 * ⛔ Dropping the kerb and the private garages is not enough, and that was measured: after both
 * exclusions, Cuenca still answered with **76 elements of which 2 carried a name and none was
 * off-street** — 74 anonymous patches of ground with no kind, no capacity, no price and no name.
 * A list of those is not a list, and a map of those is noise laid over the map.
 *
 * So one of four things must be true, each of which means a human catalogued the place: it is a
 * facility you drive into, or somebody named it, or somebody stated what it charges, or somebody
 * counted its spaces. In central Madrid that keeps essentially everything (19 of 22 are underground
 * to begin with); in a village it keeps the few that were actually surveyed, which is the honest
 * answer there.
 */
private val PublicParking.isIdentifiable: Boolean
    get() = kind.isOffStreet ||
        displayName != null ||
        fee != FeeInfo.UNKNOWN ||
        capacity != null
