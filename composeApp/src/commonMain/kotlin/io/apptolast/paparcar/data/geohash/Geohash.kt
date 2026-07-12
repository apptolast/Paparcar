package io.apptolast.paparcar.data.geohash

private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"

/**
 * Encodes a lat/lon pair as a geohash string.
 *
 * Default precision 9 (~2.4m×4.8m cells) gives enough resolution to be useful
 * as a stored value while still supporting bbox queries at coarser precisions
 * (e.g. prefix[:5] ≈ 5km, prefix[:6] ≈ 1.2km) via Firestore range queries.
 */
internal fun encodeGeohash(lat: Double, lon: Double, precision: Int = 9): String {
    var minLat = -90.0; var maxLat = 90.0
    var minLon = -180.0; var maxLon = 180.0
    val result = StringBuilder(precision)
    var bits = 0
    var hashValue = 0
    var isLon = true

    while (result.length < precision) {
        if (isLon) {
            val mid = (minLon + maxLon) / 2
            if (lon >= mid) { hashValue = (hashValue shl 1) or 1; minLon = mid }
            else { hashValue = hashValue shl 1; maxLon = mid }
        } else {
            val mid = (minLat + maxLat) / 2
            if (lat >= mid) { hashValue = (hashValue shl 1) or 1; minLat = mid }
            else { hashValue = hashValue shl 1; maxLat = mid }
        }
        isLon = !isLon
        if (++bits == 5) {
            result.append(BASE32[hashValue])
            bits = 0
            hashValue = 0
        }
    }
    return result.toString()
}

/**
 * Returns the [prefix, successor) range for a geohash prefix of [queryPrecision] chars.
 *
 * Use this to build a Firestore range query:
 *   `WHERE geohash >= range.first AND geohash < range.second`
 *
 * [queryPrecision] 5 ≈ 5km radius, 6 ≈ 1.2km, 7 ≈ 150m.
 */
internal fun geohashQueryRange(lat: Double, lon: Double, queryPrecision: Int = 6): Pair<String, String> {
    val prefix = encodeGeohash(lat, lon, queryPrecision)
    val successor = geohashSuccessor(prefix)
    return prefix to successor
}

/**
 * [AUDIT-DATA-002 A11] The set of geohash prefix ranges that COVER a circle of [radiusMeters]
 * around ([lat], [lon]) — the center cell plus its 8 neighbours (the 3×3 grid). A single-cell
 * range query misses spots just across a cell boundary (a spot 200 m away in the next cell has a
 * different prefix) and, at a coarse precision, downloads a whole metropolitan area. Querying the
 * 3×3 neighbourhood at a precision whose cell is at least as large as the radius guarantees the
 * whole circle is covered regardless of where the center falls inside its cell.
 *
 * Returns DISTINCT [prefix, successor) ranges (usually 9, fewer near the poles/antimeridian where
 * neighbours collide). The caller issues one Firestore range query per range and merges, then
 * clips to the true radius with the bounding-box filter it already applies.
 */
internal fun geohashQueryBounds(
    lat: Double,
    lon: Double,
    radiusMeters: Double,
): List<Pair<String, String>> {
    val precision = precisionForRadius(radiusMeters)
    val center = encodeGeohash(lat, lon, precision)
    val n = geohashAdjacent(center, 'n')
    val s = geohashAdjacent(center, 's')
    val cells = listOf(
        center,
        n, s,
        geohashAdjacent(center, 'e'), geohashAdjacent(center, 'w'),
        geohashAdjacent(n, 'e'), geohashAdjacent(n, 'w'),
        geohashAdjacent(s, 'e'), geohashAdjacent(s, 'w'),
    ).distinct()
    return cells.map { it to geohashSuccessor(it) }
}

/**
 * Largest precision (smallest cell) whose cell is still at least [radiusMeters] across in its
 * SMALLER dimension, so the 3×3 neighbourhood provably covers the circle. Cell minimum extents:
 * p6 ≈ 0.6 km, p5 ≈ 4.9 km, p4 ≈ 19.5 km.
 */
internal fun precisionForRadius(radiusMeters: Double): Int = when {
    radiusMeters <= 600.0 -> 6
    radiusMeters <= 4_800.0 -> 5
    radiusMeters <= 19_000.0 -> 4
    else -> 3
}

// Canonical geohash neighbour algorithm (Chris Veness / movable-type). `type` = hash length parity
// (0 = even, 1 = odd) selects the row because the last character alternates lon-major / lat-major.
private val NEIGHBOUR = mapOf(
    'n' to arrayOf("p0r21436x8zb9dcf5h7kjnmqesgutwvy", "bc01fg45238967deuvhjyznpkmstqrwx"),
    's' to arrayOf("14365h7k9dcfesgujnmqp0r2twvyx8zb", "238967debc01fg45kmstqrwxuvhjyznp"),
    'e' to arrayOf("bc01fg45238967deuvhjyznpkmstqrwx", "p0r21436x8zb9dcf5h7kjnmqesgutwvy"),
    'w' to arrayOf("238967debc01fg45kmstqrwxuvhjyznp", "14365h7k9dcfesgujnmqp0r2twvyx8zb"),
)
private val BORDER = mapOf(
    'n' to arrayOf("prxz", "bcfguvyz"),
    's' to arrayOf("028b", "0145hjnp"),
    'e' to arrayOf("bcfguvyz", "prxz"),
    'w' to arrayOf("0145hjnp", "028b"),
)

/** The geohash of the cell adjacent to [hash] in [direction] ('n'/'s'/'e'/'w'). */
internal fun geohashAdjacent(hash: String, direction: Char): String {
    if (hash.isEmpty()) return hash
    val lastCh = hash.last()
    var parent = hash.substring(0, hash.length - 1)
    val type = hash.length % 2
    if (BORDER.getValue(direction)[type].contains(lastCh) && parent.isNotEmpty()) {
        parent = geohashAdjacent(parent, direction)
    }
    return parent + BASE32[NEIGHBOUR.getValue(direction)[type].indexOf(lastCh)]
}

private fun geohashSuccessor(hash: String): String {
    val chars = hash.toCharArray()
    var i = chars.lastIndex
    while (i >= 0) {
        val idx = BASE32.indexOf(chars[i])
        if (idx < BASE32.lastIndex) {
            chars[i] = BASE32[idx + 1]
            return chars.concatToString()
        }
        chars[i] = BASE32[0]
        i--
    }
    // Overflow — return a string that sorts after all valid geohashes
    return hash + BASE32.last()
}
