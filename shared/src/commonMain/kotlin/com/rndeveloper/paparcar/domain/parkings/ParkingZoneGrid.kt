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

    /**
     * The zone a point belongs to — **identified by two integers, with its centre DERIVED**.
     * [OSM-PRECOOKED-SNAPSHOT-001]
     *
     * ⛔ It used to hold the two centre coordinates, and that was fine while the zone only ever
     * lived in memory. It stops being fine the moment a zone has to be **named** — a pre-cooked
     * snapshot file, a CDN cache key — because a `Double` has **no single spelling**: Kotlin/JVM and
     * Kotlin/Native are not obliged to print `40.4125` the same way, and two spellings of the same
     * zone are two CDN entries, which is half the cache hits and twice the function runs **with no
     * visible error anywhere**.
     *
     * Two integers have one spelling on every platform and in every language, so [id] is the zone's
     * name and the centre is what you compute from it — never the other way round. Equality now
     * compares the indices instead of two floating-point numbers, which is also the comparison the
     * callers always meant.
     */
    data class Zone(
        val bandIndex: Int,
        val columnIndex: Int,
    ) {
        val centreLatitude: Double get() = (bandIndex + HALF) * LAT_STEP

        /** ⛔ Derived from [centreLatitude], never from a caller's own latitude — see [zoneOf]. */
        val centreLongitude: Double get() = (columnIndex + HALF) * lonStepAt(centreLatitude)

        /** The zone's NAME: what a snapshot file is called and what a cache key is keyed on. */
        val id: String get() = "${bandIndex}_$columnIndex"
    }

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
        val bandIndex = floor(latitude / LAT_STEP)
        val centreLat = (bandIndex + HALF) * LAT_STEP
        val columnIndex = floor(longitude / lonStepAt(centreLat))
        return Zone(bandIndex.toInt(), columnIndex.toInt())
    }

    /**
     * A zone back from its [Zone.id], or null when the text is not one.
     *
     * ⛔ Strict on purpose: this reads a **file name and a cache key**, so anything it cannot account
     * for has to be a miss rather than a guess. A lenient parse here serves one cell's car parks
     * under another cell's name, and nothing downstream could tell.
     */
    fun zoneOf(id: String): Zone? {
        val separator = id.lastIndexOf('_')
        if (separator <= 0 || separator == id.lastIndex) return null
        val band = id.substring(0, separator).toIntOrNull() ?: return null
        val column = id.substring(separator + 1).toIntOrNull() ?: return null
        return Zone(band, column).takeIf { it.id == id }
    }

    /** Whether two points fall in the same zone — i.e. whether panning owes the server anything. */
    fun sameZone(
        latitude: Double,
        longitude: Double,
        otherLatitude: Double,
        otherLongitude: Double,
    ): Boolean = zoneOf(latitude, longitude) == zoneOf(otherLatitude, otherLongitude)

    /**
     * **How far from a cell's centre the answer about that cell has to reach**, in metres.
     * [OSM-PRECOOKED-SNAPSHOT-001]
     *
     * Cells **partition** the world: a pre-cooked cell holds what falls INSIDE it, never what its
     * zone reaches ([ZONE_RADIUS_METERS]). The alternative was measured and rejected — a cell
     * holding its whole reach publishes every car park in about twenty-one cells, which turns an
     * 8 MB artefact into 380 MB and says the same thing twenty-one times.
     *
     * So the reach is covered by reading the cells AROUND the one asked about ([blockAround]), and
     * how far that has to go is arithmetic, not a choice: the furthest anybody can stand from the
     * centre is **half a cell** on each axis, and from there they are still promised
     * [PublicParkingsQuery.DEFAULT_RADIUS_METERS].
     */
    val BLOCK_REACH_METERS: Double =
        CELL_METERS * HALF + PublicParkingsQuery.DEFAULT_RADIUS_METERS

    /**
     * The cells whose contents answer about [zone] — the block that covers [BLOCK_REACH_METERS]
     * around its centre. [OSM-PRECOOKED-SNAPSHOT-001]
     *
     * ⛔ **It is NOT a square of rings, and the first version of it was — it leaked.** Writing
     * `ceil(radius / cell)` rings around the centre's own column is the obvious shape and it loses
     * car parks, measured: from the south-west corner of Madrid's cell, a point 3 km away on a
     * bearing of 255° lands in a column the 3 × 3 block does not contain. Two reasons, both
     * structural:
     *
     * - **A column's width is derived from its own band's centre latitude** (see [zoneOf]), so the
     *   grids of two neighbouring bands are OFFSET: `Zone(band + 1, column)` is not the cell due
     *   north of `Zone(band, column)`, and the asked cell's centre can sit anywhere inside a
     *   neighbouring band's column — up to half a cell off its middle. Rings counted from that
     *   column are short on one side by however far off-centre it fell.
     * - **A degree of longitude is worth fewer metres further from the equator**, so the reach in
     *   DEGREES is larger at the far edge of the block than at the centre. Counting in cells of the
     *   centre's own band understates it, and the error grows with latitude (it leaked twelve of
     *   5.760 sample points at Svalbard against four at Madrid).
     *
     * So the block is derived from the **geographic box** the answer must cover, with the longitude
     * delta taken at the box's worst latitude, and the columns resolved **per band**. Measured over
     * nine cities from Kampala to the 89th parallel: **zero** sample points outside the block, at a
     * cost of 13 to 21 cells instead of 9.
     *
     * ⛔ And it is deliberately **generous** at the edges (`floor` on both ends, so a boundary that
     * is touched at a single line still gets its cell): a block that reads one file too many is
     * cheap, and a block with a hole in it looks exactly like *"there are no car parks here"*.
     */
    fun blockAround(zone: Zone): List<Zone> {
        val centreLat = zone.centreLatitude
        val centreLon = zone.centreLongitude
        val latDelta = BLOCK_REACH_METERS / METERS_PER_DEGREE_LAT
        val firstBand = floor((centreLat - latDelta) / LAT_STEP).toInt()
        val lastBand = floor((centreLat + latDelta) / LAT_STEP).toInt()
        // El grado de longitud MAS CARO del bloque: el de la latitud mas alejada del ecuador, que
        // es donde un metro vale mas grados. Tomarlo en el centro es lo que dejaba agujeros.
        val worstLatitude = max(abs(centreLat - latDelta), abs(centreLat + latDelta))
        val lonDelta = BLOCK_REACH_METERS / metersPerDegreeLonAt(worstLatitude)
        return (firstBand..lastBand).flatMap { band ->
            val step = lonStepAt((band + HALF) * LAT_STEP)
            val firstColumn = floor((centreLon - lonDelta) / step).toInt()
            val lastColumn = floor((centreLon + lonDelta) / step).toInt()
            (firstColumn..lastColumn).map { Zone(band, it) }
        }
    }

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

    /** A degree of longitude is worth fewer metres the further from the equator, so the step grows. */
    private fun lonStepAt(latitude: Double): Double = CELL_METERS / metersPerDegreeLonAt(latitude)

    /** A band is the same height everywhere, so its step is a constant and not a function. */
    private val LAT_STEP: Double = CELL_METERS / METERS_PER_DEGREE_LAT

    /** `cos(80°)` — a zone at the eightieth parallel is as narrow as the grid is allowed to get. */
    private const val MIN_METERS_PER_DEGREE_LON = 111_320.0 * 0.17364818
}
