package com.rndeveloper.paparcar.domain.parkings

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max

/**
 * Which **zone** a point falls in, so that the same zone is asked once and panned for free.
 * [PARKINGS-A-ZONE-IS-ASKED-ONCE-AND-PANNED-FOR-FREE-001]
 *
 * The lane used to ask about a disc centred on the camera, which meant that every pan past half the
 * radius produced coordinates the server had **never seen before**. Measured on 14-09-2026 against a
 * healthy mirror, three short pans in central Madrid:
 *
 * | Forma de preguntar | Primer byte |
 * |---|---|
 * | Zona fija, la MISMA consulta 3 veces | 1,33 · 1,59 · 1,32 s |
 * | Disco móvil, 3 centros distintos | 1,67 · 2,65 · **5,73 s** |
 *
 * The spread is not the network, it is Overpass's own cache: a query it has answered before comes
 * back from memory, a new centre is computed from scratch. Snapping the centre to a grid turns
 * almost every ask into the first kind. And the part that does not even appear in that table matters
 * more — **a pan inside the zone costs no request at all**, because the answer is already on disk
 * under the very same key. [CACHE-A-MAP-MUST-NOT-EMPTY-ON-COLD-START-001]
 *
 * ⛔ **Not administrative areas, and that was measured too.** Asking by city (`admin_level=8`) failed
 * on BOTH mirrors, each in its own way: `overpass.openstreetmap.fr` — our only fallback — has no area
 * index and answers `200` with an empty list and an internal `remark`, which our health check would
 * wave through as "there are no car parks in Madrid"; `overpass-api.de` answers `504` after 6,6 s,
 * because resolving an area and sweeping it costs far more than a disc. Beyond that, a municipality
 * has no bounded size (Madrid is 600 km², a village 5), it needs a geocode first just to know what
 * name to ask for, and **between two villages there is no city to ask about** — which is most of a
 * drive.
 *
 * ⛔ **The centre is snapped; the QUERY is still a disc.** Keeping `around:` rather than moving to a
 * bounding box is what lets the whole coverage cache stay as it is — `CoverageDisc` already speaks
 * discs, and two snapped discs either coincide exactly or do not overlap at all.
 */
object ParkingZoneGrid {

    /** The zone a point belongs to: its centre, and the radius that covers it whole. */
    data class Zone(
        val centreLatitude: Double,
        val centreLongitude: Double,
    )

    /**
     * The zone containing this point.
     *
     * ⛔ Longitude is snapped with a step derived from the **snapped latitude**, never from the
     * point's own: a metre is a different number of degrees at Málaga than at Amsterdam, and a step
     * that varied continuously would give two neighbours in the same band different grids — the
     * cache misses would be invisible and the whole point would be lost. Deriving it from the band
     * makes every point in that band agree on the grid by construction.
     */
    fun zoneOf(latitude: Double, longitude: Double): Zone {
        val latStep = CELL_METERS / METERS_PER_DEGREE_LAT
        val bandIndex = floor(latitude / latStep)
        val centreLat = (bandIndex + HALF) * latStep

        val lonStep = CELL_METERS / metersPerDegreeLonAt(centreLat)
        val columnIndex = floor(longitude / lonStep)
        val centreLon = (columnIndex + HALF) * lonStep

        return Zone(centreLat, centreLon)
    }

    /** Whether two points fall in the same zone — i.e. whether panning owes the server anything. */
    fun sameZone(
        latitude: Double,
        longitude: Double,
        otherLatitude: Double,
        otherLongitude: Double,
    ): Boolean = zoneOf(latitude, longitude) == zoneOf(otherLatitude, otherLongitude)

    /**
     * How far a zone's answer has to reach.
     *
     * The user can stand anywhere in the cell, including a corner, and still expects to see
     * [PublicParkingsQuery.DEFAULT_RADIUS_METERS] around **themselves** — so the disc drawn from the
     * cell CENTRE must cover the display radius plus the worst-case offset, which is half the cell
     * diagonal.
     *
     * At a 3 km cell that is 3.000 + 2.121 ≈ 5.200 m, and the measured cost of that reach on
     * 14-09-2026 was: Madrid **681** elements / 238 KB, Málaga 571 / 269 KB, Cuenca 108 / 31 KB.
     * Heavy for one request — and cheap for what it buys, because it is fetched **once per zone**
     * and then every pan inside it is free for thirty days.
     */
    val ZONE_RADIUS_METERS: Int =
        (PublicParkingsQuery.DEFAULT_RADIUS_METERS + CELL_METERS * HALF_DIAGONAL).toInt()

    /**
     * 3 km on a side.
     *
     * Bigger cells are counter-intuitively **cheaper in total**, because they are fetched so much
     * less often: covering a 10 × 10 km city costs about 11 zones at 238 KB (≈ 2,6 MB, once a month)
     * against about 35 zones at 166 KB (≈ 5,8 MB) with a 1,7 km cell. Fewer requests AND fewer bytes.
     * The ceiling on cell size is the payload of a single ask, and past 3 km a dense capital starts
     * costing a third of a megabyte in one go.
     */
    private const val CELL_METERS = 3_000.0

    /** Half the diagonal of a square cell, as a multiple of its side: `√2 / 2`. */
    private const val HALF_DIAGONAL = 0.70710678

    private const val HALF = 0.5

    /** A degree of latitude is the same length everywhere; a degree of longitude is not. */
    private const val METERS_PER_DEGREE_LAT = 111_320.0

    /**
     * ⛔ Clamped, because `cos` reaches zero at the pole and the step would explode into a cell wider
     * than a continent. Paparcar ships nine European locales, so the clamp is far outside anywhere it
     * is asked about — it exists so that a coordinate from a broken fix cannot produce a nonsensical
     * zone, not because anybody parks there.
     */
    private fun metersPerDegreeLonAt(latitude: Double): Double =
        max(MIN_METERS_PER_DEGREE_LON, METERS_PER_DEGREE_LAT * cos(abs(latitude) * PI / 180.0))

    /** `cos(80°)` — a zone at the eightieth parallel is as narrow as the grid is allowed to get. */
    private const val MIN_METERS_PER_DEGREE_LON = 111_320.0 * 0.17364818
}
