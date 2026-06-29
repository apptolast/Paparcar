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
