package com.rndeveloper.paparcar.data.parkings

import com.rndeveloper.paparcar.domain.parkings.FeeInfo
import com.rndeveloper.paparcar.domain.parkings.PublicParking
import com.rndeveloper.paparcar.domain.parkings.PublicParkingKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * OpenStreetMap's `amenity=parking`: every country, no occupancy. [PARKINGS-ON-THE-MAP-001]
 *
 * Measured on 09-09-2026 over five cities (1 km radius, 2 km in Cuenca), which is what the shape
 * below is built against rather than assumed:
 * - ⛔ **`amenity=parking` is mostly the STREET.** `parking=street_side` and `lane` are roadside
 *   parking — 256 of the 300 elements around central Málaga, 35 of 70 around Châtelet. That is not a
 *   facility you drive into; it is the kerb where Paparcar already looks for free spots, so
 *   rendering it would duplicate our own product as anonymous polygons.
 * - ⛔ **A third of them are not yours to use**: `access` is `private`, `no`, `permit` or
 *   `customers` on 15 of 39 elements in Madrid and 86 of 223 in Berlin.
 * - ⛔ **Names are the exception, not the rule**: `name` reaches 72% in central Madrid and Paris but
 *   falls to 14% in Berlin-Mitte and **2,6% in Cuenca**. The kind carries the row when the name
 *   cannot — see [PublicParkingKind].
 *
 * Both exclusions are pushed into the Overpass query instead of being applied after download: they
 * are most of the payload, and a phone should not spend a user's data on 256 kerbside polygons to
 * throw them away. In Overpass, `[k!~"…"]` also matches elements where `k` is absent, which is what
 * keeps the untagged majority (59 of 90 in Cuenca) in the answer.
 */
object OsmPublicParkingParser {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Every usable car park within [radiusMeters] of the point.
     *
     * `out center`, like the fuel query: a car park is a polygon and its middle is the point you
     * navigate to.
     *
     * ⛔ **A union of two statements, not one negated regex, and that is the whole point.** Whether
     * Overpass's `[k!~"v"]` also matches elements that do not carry `k` at all decides this feature
     * in villages — **59 of Cuenca's 90 car parks declare no `parking` tag** — and a filter that
     * dropped them would empty the layer exactly where there is no community to fill the map, while
     * looking perfect in a capital (Madrid is 19 underground out of 22, all tagged). So the query
     * asks for both halves explicitly: *no tag at all* (`[!parking]`, which is unambiguous) OR
     * *tagged and not roadside*. Under either semantics the answer is identical — Overpass unions
     * deduplicate by element id — so nothing here rests on a behaviour nobody measured.
     *
     * The access filter stays on the phone (see [parse]): it would need the same doubling, and it
     * removes far less payload than the kerb does — 256 of Málaga's 300 elements are roadside.
     */
    fun buildQuery(lat: Double, lon: Double, radiusMeters: Int, timeoutSeconds: Int): String =
        "[out:json][timeout:$timeoutSeconds];" +
            "(" +
            "nwr(around:$radiusMeters,$lat,$lon)[amenity=parking][!parking];" +
            "nwr(around:$radiusMeters,$lat,$lon)[amenity=parking][\"parking\"!~\"$ROADSIDE_PATTERN\"];" +
            ");" +
            "out center $MAX_RESULTS;"

    fun parse(body: String): List<PublicParking> =
        json.decodeFromString<OsmResponse>(body).elements.mapNotNull { element ->
            val lat = element.lat ?: element.center?.lat ?: return@mapNotNull null
            val lon = element.lon ?: element.center?.lon ?: return@mapNotNull null
            val tags = element.tags
            // Access is filtered HERE and only here — the query cannot express it without doubling
            // itself again (see [buildQuery]), and it is the cheaper of the two to carry down the
            // wire. The roadside check is belt and braces: the query already excluded those, but a
            // cached or hand-made payload must not slip a kerb into the layer.
            if (tags["parking"] in ROADSIDE || tags["access"] in CLOSED_ACCESS) return@mapNotNull null
            if (!tags.customersMayBuyIn()) return@mapNotNull null
            PublicParking(
                id = "osm/${element.type}/${element.id}",
                name = tags["name"],
                operator = tags["operator"],
                kind = tags["parking"].toKind(),
                latitude = lat,
                longitude = lon,
                capacity = tags["capacity"]?.toIntOrNull()?.takeIf { it > 0 },
                fee = tags["fee"].toFee(),
                maxHeightMeters = tags["maxheight"].toMetres(),
                openingHours = tags["opening_hours"],
            )
        }

    /**
     * `access=customers` is the one access value that cannot be judged on its own, and it was
     * measured rather than assumed: all **three** carrying it within 1 km of Puerta del Sol are
     * `fee=yes`, and two of those are ordinary paid garages anyone can drive into — one of them
     * operated by the *Ayuntamiento de Madrid*. Excluding the tag outright deleted municipal car
     * parks from the middle of the capital.
     *
     * So it excludes only when nothing says you may buy your way in. A shop's free customer lot is
     * dropped; a garage that charges is kept, together with the occasional hostel garage that also
     * charges — a stranger's paid garage on the map is a far smaller error than a missing public one.
     */
    private fun Map<String, String>.customersMayBuyIn(): Boolean =
        this["access"] != "customers" || this["fee"] == "yes"

    private fun String?.toKind(): PublicParkingKind = when (this) {
        "underground" -> PublicParkingKind.UNDERGROUND
        "multi-storey", "multi_storey", "rooftop" -> PublicParkingKind.MULTI_STOREY
        "garage_boxes", "garages", "sheds", "carports" -> PublicParkingKind.GARAGE_BOXES
        "surface" -> PublicParkingKind.SURFACE
        else -> PublicParkingKind.UNKNOWN
    }

    /**
     * ⛔ Only the two words that state a fact. `fee=interval`, `fee=customers` and the untagged 71%
     * all mean "nobody has said what this costs", and saying "free" for any of them would send a
     * driver into a barrier they were told not to expect.
     */
    private fun String?.toFee(): FeeInfo = when (this) {
        "yes" -> FeeInfo.PAID
        "no" -> FeeInfo.FREE
        else -> FeeInfo.UNKNOWN
    }

    /**
     * `maxheight` is metric in the vast majority of the world and the value is bare (`2.1`) or
     * suffixed (`2.1 m`). ⛔ Imperial (`6'6"`) is **left null on purpose**: guessing a clearance is
     * how a van gets wedged under a beam, and null renders as silence rather than as a wrong number.
     */
    private fun String?.toMetres(): Double? {
        val raw = this?.trim()?.removeSuffix("m")?.trim()?.replace(',', '.') ?: return null
        return raw.toDoubleOrNull()?.takeIf { it in MIN_CLEARANCE_M..MAX_CLEARANCE_M }
    }

    /** Roadside parking: the kerb, not a facility. */
    private val ROADSIDE = setOf("street_side", "lane", "layby", "on_kerb", "half_on_kerb", "verge")

    /**
     * Access values that mean "not for you", whatever else the element says. ⛔ `customers` is NOT
     * here — see [customersMayBuyIn], which is a measured exception, not an oversight.
     */
    private val CLOSED_ACCESS = setOf(
        "private", "no", "permit", "employees", "residents", "agricultural", "delivery", "military",
    )

    private val ROADSIDE_PATTERN = ROADSIDE.joinToString("|", prefix = "^(", postfix = ")$")
    private val CLOSED_ACCESS_PATTERN = CLOSED_ACCESS.joinToString("|", prefix = "^(", postfix = ")$")

    /**
     * A ceiling on the payload, not on the map: the nearest ones are what a driver acts on.
     *
     * Visible beyond this file because the disk cache has to know it: a response that came back AT
     * the cap was truncated, so the area it can honestly claim to have seen ends at its farthest
     * element rather than at the radius it asked for.
     * [CACHE-A-MAP-MUST-NOT-EMPTY-ON-COLD-START-001]
     */
    internal const val MAX_RESULTS = 80

    private const val MIN_CLEARANCE_M = 1.4
    private const val MAX_CLEARANCE_M = 6.0

    @Serializable
    private data class OsmResponse(val elements: List<OsmElement> = emptyList())

    @Serializable
    private data class OsmElement(
        val type: String = "node",
        val id: Long = 0,
        val lat: Double? = null,
        val lon: Double? = null,
        val center: OsmCenter? = null,
        @SerialName("tags") val tags: Map<String, String> = emptyMap(),
    )

    @Serializable
    private data class OsmCenter(val lat: Double, val lon: Double)
}
