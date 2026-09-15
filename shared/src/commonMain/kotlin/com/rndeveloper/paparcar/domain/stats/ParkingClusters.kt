package com.rndeveloper.paparcar.domain.stats

import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.Zone
import com.rndeveloper.paparcar.domain.util.haversineMeters
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.PI

/**
 * A place the user parks at REPEATEDLY, and what parking there costs them.
 * [STATS-A-USUAL-PLACE-IS-A-CLUSTER-NOT-A-STREET-001]
 *
 * The unit of truth for "where you park" is a PLACE — a patch of ground — not the string a geocoder
 * returned that afternoon. Comparing street names made one spot into two whenever the corner
 * answered with a different road, and four kilometres of Gran Vía into one.
 */
data class ParkingCluster(
    /** Stable across recomputations: the rounded centroid, so a list can key on it. */
    val id: String,
    /**
     * What to call it: the user's own [Zone] name first, then the street that repeats, then the POI.
     * Null when nothing can name it — and an unnamed cluster is never listed, because a row that
     * cannot say where it is helps nobody.
     */
    val name: String?,
    /** Set when a user zone contains the centroid — that is where the name came from. */
    val zoneId: String? = null,
    /** True when that zone is a private one (your garage): it never publishes a spot on departure. */
    val isPrivateZone: Boolean = false,
    val latitude: Double,
    val longitude: Double,
    /** Times parked here in the period. Always ≥ [MIN_PARKINGS_FOR_PLACE] once filtered. */
    val parkings: Int,
    /** Mean stay of the ENDED sessions here; null when none of them has closed yet. */
    val avgStayMs: Long?,
    /** Time spent hunting a spot here, with its coverage. [STATS-A-MEASURE-CARRIES-ITS-COVERAGE-001] */
    val searchTime: ScopedMeasure<Long>?,
    /** Departures from here that gave the community a spot. */
    val spotsReleased: Int,
    /** Most recent park here — what orders "your places" when two are tied. */
    val lastParkedAtMs: Long,
) {
    /**
     * What ONE hunt here typically costs — the sum over what was measured.
     *
     * ⛔ The mean, and for the same reason the day × hour grid uses one
     * [STATS-THE-EXPENSIVE-HOUR-IS-MEASURED-001]: a per-place SUM grows with how often you go
     * there, so it would rank your places by frequency while appearing to rank them by cost. And
     * the app cannot say "buscando" with two different statistics on two cards of one screen.
     */
    val avgSearchTimeMs: Long? get() = searchTime?.let { it.value / it.fromParkings }
}

/**
 * The places [sessions] repeat at, most-visited first. [STATS-A-USUAL-PLACE-IS-A-CLUSTER-NOT-A-STREET-001]
 *
 * Agglomerative, with a grid index: each session joins the nearest cluster within
 * [CLUSTER_RADIUS_METERS], and only the nine neighbouring cells are searched, so the whole thing
 * stays O(n) — the "All" filter has no cap, and a car with three years of history must not turn
 * the ficha into a stutter.
 *
 * The radius is measured against the field corpus — see [CLUSTER_RADIUS_METERS], which carries the
 * numbers and what they cannot say.
 */
fun clusterParkings(
    sessions: List<UserParking>,
    zones: List<Zone> = emptyList(),
): List<ParkingCluster> {
    if (sessions.isEmpty()) return emptyList()

    // ── 1 · group points into places ────────────────────────────────────────
    val buckets = mutableListOf<MutableList<UserParking>>()
    val grid = mutableMapOf<Long, MutableList<Int>>()
    sessions.forEach { session ->
        val lat = session.location.latitude
        val lon = session.location.longitude
        val nearest = grid.neighbourIndices(lat, lon)
            .mapNotNull { index ->
                val bucket = buckets[index]
                val distance = haversineMeters(lat, lon, bucket.meanLatitude(), bucket.meanLongitude())
                if (distance <= CLUSTER_RADIUS_METERS) index to distance else null
            }
            .minByOrNull { it.second }
            ?.first
        if (nearest != null) {
            buckets[nearest].add(session)
        } else {
            buckets.add(mutableListOf(session))
            grid.getOrPut(cellKey(lat, lon)) { mutableListOf() }.add(buckets.lastIndex)
        }
    }

    // ── 2 · a place is where you REPEAT ─────────────────────────────────────
    return buckets
        .filter { it.size >= MIN_PARKINGS_FOR_PLACE }
        .map { bucket -> bucket.toCluster(zones) }
        .sortedWith(compareByDescending<ParkingCluster> { it.parkings }.thenByDescending { it.lastParkedAtMs })
}

/** The grid cell a point falls in — the index that keeps the search local. */
private fun cellKey(lat: Double, lon: Double): Long =
    packCell(floor(lat / cellSizeLat()).toLong(), floor(lon / cellSizeLon(lat)).toLong())

private fun MutableMap<Long, MutableList<Int>>.neighbourIndices(lat: Double, lon: Double): List<Int> {
    val latCell = floor(lat / cellSizeLat()).toLong()
    val lonCell = floor(lon / cellSizeLon(lat)).toLong()
    val found = mutableListOf<Int>()
    for (dLat in -1..1) {
        for (dLon in -1..1) {
            this[packCell(latCell + dLat, lonCell + dLon)]?.let { found.addAll(it) }
        }
    }
    return found
}

private fun MutableList<UserParking>.meanLatitude(): Double = sumOf { it.location.latitude } / size

private fun MutableList<UserParking>.meanLongitude(): Double = sumOf { it.location.longitude } / size

private fun MutableList<UserParking>.toCluster(zones: List<Zone>): ParkingCluster {
    val lat = meanLatitude()
    val lon = meanLongitude()
    // A zone OWNS the name when it contains the place: the user already said what this is called.
    val zone = zones.minByOrNull { haversineMeters(lat, lon, it.lat, it.lon) }
        ?.takeIf { haversineMeters(lat, lon, it.lat, it.lon) <= it.radiusMeters }
    val ended = filter { !it.isActive }
    val stays = ended.mapNotNull { s -> s.endedAtMs?.let { it - s.location.timestamp }?.takeIf { it > 0 } }
    return ParkingCluster(
        id = "${lat.roundedForId()},${lon.roundedForId()}",
        name = zone?.name ?: mostRepeated { it.address?.street } ?: mostRepeated { it.placeInfo?.name },
        zoneId = zone?.id,
        isPrivateZone = zone?.isPrivate == true,
        latitude = lat,
        longitude = lon,
        parkings = size,
        avgStayMs = stays.takeIf { it.isNotEmpty() }?.let { it.sum() / it.size },
        searchTime = sumMeasuredSearchTime(this),
        spotsReleased = count { it.publishedSpot },
        lastParkedAtMs = maxOf { it.location.timestamp },
    )
}

/**
 * The name that repeats most among the sessions of one place. Ties break alphabetically, so the
 * same history always produces the same name.
 *
 * ⛔ **No "must repeat" threshold here, and that is the point of the ticket.** The old figure
 * required a street to appear twice before it counted, because the STRING was the identity: without
 * that rule, one visit to one street was a "usual street". Now the geometry proves the repetition —
 * a cluster only exists at three parkings — so the string has one job left, naming, and demanding
 * it repeat as well would leave a place the user visits weekly nameless the moment the geocoder
 * answered the corner with two different roads. Which is the very failure this replaces.
 */
private fun List<UserParking>.mostRepeated(of: (UserParking) -> String?): String? =
    mapNotNull { of(it)?.takeIf(String::isNotBlank) }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .firstOrNull()
        ?.key

private fun Double.roundedForId(): Long = (this * ID_PRECISION).toLong()

private fun cellSizeLat(): Double = CLUSTER_RADIUS_METERS / METERS_PER_DEGREE_LAT

/** Cells shrink with the cosine of the latitude, so a cell is ~the radius wide everywhere. */
private fun cellSizeLon(lat: Double): Double {
    val shrink = cos(lat * PI / DEGREES_HALF_TURN)
    return CLUSTER_RADIUS_METERS / (METERS_PER_DEGREE_LAT * maxOf(abs(shrink), MIN_COS_LATITUDE))
}

private fun packCell(latCell: Long, lonCell: Long): Long = latCell * CELL_PACK_STRIDE + lonCell

/**
 * How far apart two parkings can be and still be the same place — **measured, not guessed**
 * (15-09-2026, over the 18 field traces in `domain/detection/coordinator/replay`).
 *
 * Three numbers decided it:
 *
 * - **A stop's own GPS scatter**, over anchor-grade fixes (accuracy ≤ 25 m): p50 **5.8 m**, p90
 *   **24 m**, and one stop at **94.7 m** — `HouseMirage001`, whose fixes wander across a whole
 *   house. Two visits to that place could be sampled at opposite ends of that wander, so any radius
 *   at or below ~95 m splits a real place in two.
 * - **The gap between stops.** Sorting every pair of anchors gives
 *   `0 4 6 6 6 9 9 10 11 13 13 13 13 30 30 31 38 42 58 · 205 205 208 · 543 …` — nothing at all
 *   between **58 m and 205 m**. Same-place pairs end at 58; different-place pairs start at 205.
 * - **The plateau.** Re-clustering those anchors at 30…500 m collapses them into 10, 9, then **8
 *   places from 60 m all the way to 200 m**, and 7 at 300 m. Anywhere in [60, 200] gives the same
 *   answer; below it splits, above it merges.
 *
 * 120 m sits in the middle of that plateau, above the worst single-stop scatter and well below the
 * nearest genuinely different place.
 *
 * ⚠️ **What this corpus cannot say**, and the next person to touch this should know it: the traces
 * are Coordinator, one user, one city — so the merge side is under-sampled (in a dense centre the
 * next real place can be 80 m away, and nothing here shows that), and the Bluetooth walk-away bias
 * [DET-BT-PIN-SAMPLED-AFTER-THE-WALK-001] is not in the data at all. Both argue for keeping the
 * headroom rather than tightening toward the 60 m floor.
 */
const val CLUSTER_RADIUS_METERS = 120.0

/** A place is where you REPEAT — the threshold the "usual street" figure already used. */
const val MIN_PARKINGS_FOR_PLACE = 3

private const val METERS_PER_DEGREE_LAT = 111_320.0
private const val DEGREES_HALF_TURN = 180.0
/** Near the poles the cosine collapses; clamp so a cell never becomes infinitely wide. */
private const val MIN_COS_LATITUDE = 0.01
/** ~1 m of precision in the id, enough to be stable and never to collide between two places. */
private const val ID_PRECISION = 100_000.0
private const val CELL_PACK_STRIDE = 1_000_000L
