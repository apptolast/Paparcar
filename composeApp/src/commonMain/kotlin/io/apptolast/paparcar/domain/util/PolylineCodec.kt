package io.apptolast.paparcar.domain.util

import io.apptolast.paparcar.domain.model.GpsPoint
import kotlin.math.roundToInt

/**
 * Google Encoded Polyline Algorithm (precision 5) for a list of lat/lon points. [DET-ROUTE-TRACK-001]
 *
 * A driven route is stored as ONE compact string on the parking record (local + remote) instead of a
 * structured point array — a ~300-point trip encodes to a few hundred bytes, so per-parking route
 * storage grows the DB linearly and cheaply (the user's "exponential growth" concern). Only lat/lon
 * are kept: a drawn route needs no accuracy/timestamp/speed, and dropping them is most of the saving.
 *
 * Standard, interoperable format (same as Google Directions / OSRM overview geometry) so the stored
 * blob is inspectable and portable. Pure commonMain — no platform dependency.
 */
object PolylineCodec {

    private const val PRECISION = 1e5

    /** Encodes [points] (lat/lon only) to an encoded-polyline string. Empty list → empty string. */
    fun encode(points: List<GpsPoint>): String {
        if (points.isEmpty()) return ""
        val sb = StringBuilder()
        var prevLat = 0
        var prevLon = 0
        for (p in points) {
            val lat = (p.latitude * PRECISION).roundToInt()
            val lon = (p.longitude * PRECISION).roundToInt()
            encodeValue(lat - prevLat, sb)
            encodeValue(lon - prevLon, sb)
            prevLat = lat
            prevLon = lon
        }
        return sb.toString()
    }

    /** Decodes an encoded-polyline string back to points (accuracy/timestamp/speed = 0). Blank → empty. */
    fun decode(encoded: String?): List<GpsPoint> {
        if (encoded.isNullOrEmpty()) return emptyList()
        val out = ArrayList<GpsPoint>()
        var index = 0
        var lat = 0
        var lon = 0
        while (index < encoded.length) {
            lat += decodeValue(encoded, index).also { index = it.nextIndex }.delta
            lon += decodeValue(encoded, index).also { index = it.nextIndex }.delta
            out.add(GpsPoint(lat / PRECISION, lon / PRECISION, 0f, 0L, 0f))
        }
        return out
    }

    private fun encodeValue(value: Int, sb: StringBuilder) {
        var v = if (value < 0) (value shl 1).inv() else value shl 1
        while (v >= 0x20) {
            sb.append(((0x20 or (v and 0x1f)) + 63).toChar())
            v = v shr 5
        }
        sb.append((v + 63).toChar())
    }

    /**
     * Haversine length (meters) of an encoded polyline, or null when there is no route to measure
     * (null/blank or a single point). The repository stamps this next to every route write so
     * consumers (stats) read a persisted number instead of decoding polylines in hot paths.
     * [VEH-STATS-SAY-SOMETHING-USEFUL-001]
     */
    fun lengthMeters(encoded: String?): Float? {
        val points = decode(encoded)
        if (points.size < 2) return null
        var sum = 0.0
        for (i in 1 until points.size) {
            sum += haversineMeters(
                points[i - 1].latitude, points[i - 1].longitude,
                points[i].latitude, points[i].longitude,
            )
        }
        return sum.toFloat()
    }

    private class Decoded(val delta: Int, val nextIndex: Int)

    private fun decodeValue(encoded: String, start: Int): Decoded {
        var index = start
        var shift = 0
        var result = 0
        var b: Int
        do {
            b = encoded[index++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20)
        val delta = if (result and 1 != 0) (result shr 1).inv() else result shr 1
        return Decoded(delta, index)
    }
}
