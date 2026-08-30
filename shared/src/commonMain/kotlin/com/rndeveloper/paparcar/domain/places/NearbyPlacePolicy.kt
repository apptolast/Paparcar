package com.rndeveloper.paparcar.domain.places

import com.rndeveloper.paparcar.domain.model.PlaceCategory
import com.rndeveloper.paparcar.domain.model.PlaceInfo
import com.rndeveloper.paparcar.domain.util.BoundingBox
import com.rndeveloper.paparcar.domain.util.distanceToBoundingBoxMeters
import com.rndeveloper.paparcar.domain.util.haversineMeters

/**
 * A named place found near a coordinate, before anything decides whether it is worth naming.
 *
 * [bounds] is the place's own footprint when OSM knows it (a way or relation: a supermarket, a
 * hospital, a park). For a node it is null and the place is just its point.
 */
data class PlaceCandidate(
    val name: String,
    val category: PlaceCategory,
    val lat: Double,
    val lon: Double,
    val bounds: BoundingBox? = null,
)

/**
 * WHICH nearby place — if any — may be named as "where you parked".
 * [POI-A-PLACE-IS-NAMED-ONLY-IF-YOU-ARE-AT-IT-001]
 *
 * ## The nearest one wins
 *
 * Until this policy existed, candidates were sorted by category priority FIRST and by distance only
 * as a tiebreak, so a petrol station 79 m away beat a café 3 m away — `FUEL` is index 0 of the
 * priority list and `CAFE` index 6. The priority list was written to separate two businesses
 * sharing one doorway, and it ended up separating things a block apart. Here it does only the job
 * it was written for: it breaks ties among candidates within [CATEGORY_TIE_METERS] of the nearest
 * one. Everything else is decided by distance.
 *
 * ## Distance is measured to the EDGE, not to the centre
 *
 * A place's distance is [distanceToBoundingBoxMeters] — zero while you stand inside it. This is the
 * whole reason [NAMING_RADIUS_METERS] can be a single number: measured to the centroid, an honest
 * threshold would be ~40 m for a node and ~100 m for a mall, because a big building contributes its
 * own half-size to every measurement. Measured to the edge, standing at the door of a supermarket is
 * 0 m whether the building is a kiosk or a hypermarket.
 *
 * ## Naming nothing is an answer
 *
 * When no candidate is close enough this returns null and the line falls back to the street address.
 * That is the asymmetric-failure doctrine applied to copy: staying quiet costs a name, naming the
 * wrong place costs the pin its credibility.
 */
object NearbyPlacePolicy {

    /** What is ASKED of Overpass. Wider than [NAMING_RADIUS_METERS] so a large polygon still enters
     *  the list and then gets measured honestly by its edge — it is not a naming radius. */
    const val QUERY_RADIUS_METERS = 80

    /** Ceiling on how many candidates the query returns. */
    const val MAX_RESULTS = 20

    /**
     * How far the EDGE of a place may be and still be called the place you parked at: typical urban
     * GPS error on the pin (10-25 m, measured in the field) plus pavement, carriageway and the bay
     * itself (10-15 m). Roughly half a block — past it there is another doorway in between.
     */
    const val NAMING_RADIUS_METERS = 40.0

    /** Candidates this close to the nearest one are the same doorway; only there does category rank. */
    const val CATEGORY_TIE_METERS = 10.0

    /** Lower index = preferred when two candidates share a doorway. */
    private val CATEGORY_PRIORITY = listOf(
        PlaceCategory.FUEL,
        PlaceCategory.SUPERMARKET,
        PlaceCategory.MALL,
        PlaceCategory.HOSPITAL,
        PlaceCategory.PHARMACY,
        PlaceCategory.RESTAURANT,
        PlaceCategory.CAFE,
        PlaceCategory.HOTEL,
        PlaceCategory.PARKING,
        PlaceCategory.BANK,
        PlaceCategory.SCHOOL,
        PlaceCategory.GYM,
        PlaceCategory.OTHER,
    )

    /** Metres from ([lat], [lon]) to this place: to its footprint when known, to its point otherwise. */
    fun distanceMeters(candidate: PlaceCandidate, lat: Double, lon: Double): Double =
        candidate.bounds
            ?.let { distanceToBoundingBoxMeters(lat, lon, it) }
            ?: haversineMeters(lat, lon, candidate.lat, candidate.lon)

    /** The place worth naming at ([lat], [lon]), or null when none is close enough to claim. */
    fun pick(candidates: List<PlaceCandidate>, lat: Double, lon: Double): PlaceInfo? {
        val reachable = candidates
            .map { it to distanceMeters(it, lat, lon) }
            .filter { (_, meters) -> meters <= NAMING_RADIUS_METERS }
        val nearestMeters = reachable.minOfOrNull { (_, meters) -> meters } ?: return null
        return reachable
            .filter { (_, meters) -> meters <= nearestMeters + CATEGORY_TIE_METERS }
            .minWithOrNull(
                compareBy({ priorityOf(it.first.category) }, { it.second }),
            )
            ?.first
            ?.let { PlaceInfo(name = it.name, category = it.category) }
    }

    private fun priorityOf(category: PlaceCategory): Int =
        CATEGORY_PRIORITY.indexOf(category).let { if (it < 0) Int.MAX_VALUE else it }
}
