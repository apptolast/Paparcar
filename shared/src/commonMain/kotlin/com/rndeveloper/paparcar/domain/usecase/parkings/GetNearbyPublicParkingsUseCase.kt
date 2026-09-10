package com.rndeveloper.paparcar.domain.usecase.parkings

import com.rndeveloper.paparcar.domain.geocoder.GeocoderDataSource
import com.rndeveloper.paparcar.domain.parkings.FeeInfo
import com.rndeveloper.paparcar.domain.parkings.PublicParking
import com.rndeveloper.paparcar.domain.parkings.PublicParkingsQuery
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
 * The geocode routes the request exactly as it does for fuel — `AddressInfo` already carries the
 * `countryCode`, so no new lookup is invented here — and a failed geocode is not a failed request:
 * OSM covers the world and needs no country to answer.
 */
class GetNearbyPublicParkingsUseCase(
    private val parkings: PublicParkingsSource,
    private val geocoder: GeocoderDataSource,
) {

    suspend operator fun invoke(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int = PublicParkingsQuery.DEFAULT_RADIUS_METERS,
    ): Result<NearbyPublicParkings> {
        val address = geocoder.getAddress(latitude, longitude).getOrNull()

        val query = PublicParkingsQuery(
            latitude = latitude,
            longitude = longitude,
            countryCode = address?.countryCode.orEmpty(),
            regionName = address?.province ?: address?.region,
            radiusMeters = radiusMeters,
        )

        return parkings.parkingsNear(query).map { found ->
            NearbyPublicParkings(
                parkings = found.parkings
                    .filter { it.isIdentifiable }
                    .map {
                        NearbyPublicParking(
                            parking = it,
                            distanceMeters = haversineMeters(latitude, longitude, it.latitude, it.longitude),
                        )
                    }
                    .sortedBy { it.distanceMeters },
                // Carried through untouched: the filtering above changes WHICH car parks are worth
                // showing, never when the app last managed to ask.
                rememberedAtMs = found.rememberedAtMs,
            )
        }
    }
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
