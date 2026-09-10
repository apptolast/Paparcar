package com.rndeveloper.paparcar.data.fuel

import com.rndeveloper.paparcar.domain.fuel.FuelStation
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import com.rndeveloper.paparcar.domain.util.haversineMeters
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Gives France's nameless forecourts their names — **joined on the official id, never on distance**.
 * [FUEL-FRANCE-A-PRICE-CARRIES-ITS-OWN-HOUR-001]
 *
 * France's feed carries no name or brand at all, and this is the one country the survey said would
 * need two sources. The obvious way to do it is the forbidden one: `FUEL-STATIONS-LIST-001` rules
 * out matching catalogues by proximity because two forecourts across a dual carriageway sit ~30 m
 * apart and a mismatch there **looks like a price, not like a bug**.
 *
 * Measured on 161 French stations against 217 OSM `amenity=fuel` nodes in Toulouse, Paris and a
 * rural area (09-09-2026) — proximity fails in *both* directions at once:
 * - nearest-OSM distance p50 = **44 m**, so a tight ≤50 m rule names only **51-62%**
 * - a loose ≤150 m rule names 87% but leaves **6-11% ambiguous** (a second candidate within 100 m)
 *
 * ⛔ Those 44 m are not a matching error — they are **the feed's own coordinate error**. OSM stores
 * the official station id in `ref:FR:prix-carburants` (173 of the 217 nodes), and joining on it
 * gives **156 of 161 = 97%**, of which **100% carry a name**; measured against those id-confirmed
 * twins the feed's coordinate is p50 = 43 m out, p90 = 134 m. So proximity was never measuring
 * sameness, and an id is not "proximity done carefully" — it is a different operation, with the
 * ambiguity that made proximity dangerous simply absent.
 *
 * ⛔ **Names only.** The coordinate, the price and the hour stay the feed's: it is the authority for
 * "this station exists and charges X", and an object stitched from two sources is one nobody can
 * debug afterwards.
 */
internal class OsmStationNames(
    private val overpass: OverpassClient,
) {

    /**
     * [stations] with the names OSM knows, best-effort.
     *
     * ⛔ **Never fails the request.** Overpass has no SLA — it went down twice in one afternoon —
     * and a name is a nicety while the price is the product. When the lookup fails, every station
     * comes back untouched and the UI shows it by address, exactly as it already does for OSM's own
     * nameless nodes.
     */
    suspend fun named(
        stations: List<FuelStation>,
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
    ): List<FuelStation> {
        if (stations.isEmpty()) return stations

        val body = overpass.fetch(buildQuery(latitude, longitude, radiusMeters), TAG)
            .onFailure { PaparcarLogger.w(TAG, "no names this time: ${it.message}") }
            .getOrNull() ?: return stations

        val names = runCatching { parseNames(body) }
            .onFailure { PaparcarLogger.w(TAG, "unreadable name payload: ${it.message}") }
            .getOrNull() ?: return stations

        var matched = 0
        val named = stations.map { station ->
            val candidate = names[station.id] ?: return@map station
            // Sanity radius: the id is the join, but one pair of 156 measured 2.359 m apart — one
            // of the two records is wrong there and we cannot tell which, so we decline to guess.
            val apart = haversineMeters(
                station.latitude, station.longitude, candidate.latitude, candidate.longitude,
            )
            if (apart > MAX_TWIN_DISTANCE_M) {
                PaparcarLogger.w(TAG, "id ${station.id} twin is ${apart.toInt()} m away, skipped")
                return@map station
            }
            matched++
            station.copy(name = candidate.name)
        }
        PaparcarLogger.d(TAG, "named $matched/${stations.size} french stations by official id")
        return named
    }

    /**
     * Only nodes that carry the official id — anything else is unusable here by construction, and
     * asking for less keeps the payload down on a server with no SLA.
     */
    private fun buildQuery(lat: Double, lon: Double, radiusMeters: Int): String =
        "[out:json][timeout:$TIMEOUT_S];" +
            "nwr(around:$radiusMeters,$lat,$lon)[amenity=fuel][\"$REF_TAG\"];" +
            "out center $MAX_RESULTS;"

    /**
     * `official id → the station OSM knows`.
     *
     * ⛔ A duplicated ref that disagrees on the name yields NO name (measured: 3 refs appear twice
     * in Paris). Asymmetric failure, the project's own doctrine — nameless beats the neighbour's
     * name, because a wrong brand on a right price is a sentence the user cannot check.
     */
    private fun parseNames(body: String): Map<String, OsmNamedStation> {
        val candidates = mutableMapOf<String, OsmNamedStation?>()
        json.decodeFromString<OsmNameResponse>(body).elements.forEach { element ->
            val ref = element.tags[REF_TAG]?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
            val name = (element.tags["brand"] ?: element.tags["name"] ?: element.tags["operator"])
                ?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
            val lat = element.lat ?: element.center?.lat ?: return@forEach
            val lon = element.lon ?: element.center?.lon ?: return@forEach

            if (candidates.containsKey(ref)) {
                // Second sighting: keep it only if both agree on the name.
                val held = candidates[ref]
                if (held == null || held.name != name) candidates[ref] = null
            } else {
                candidates[ref] = OsmNamedStation(name, lat, lon)
            }
        }
        return candidates.mapNotNull { (ref, value) -> value?.let { ref to it } }.toMap()
    }

    private data class OsmNamedStation(val name: String, val latitude: Double, val longitude: Double)

    private companion object {
        val json = Json { ignoreUnknownKeys = true }

        /** OSM's tag for the id of France's own fuel-price dataset. This is the whole join. */
        const val REF_TAG = "ref:FR:prix-carburants"
        const val TAG = "OsmStationNames"
        const val TIMEOUT_S = 20
        const val MAX_RESULTS = 100

        /**
         * An id match this far apart is not a match anyone should act on. Generous on purpose: the
         * feed's own coordinate is p90 = 134 m out, so a tight bound here would throw away good
         * names for the feed's imprecision — the very mistake proximity matching makes.
         */
        const val MAX_TWIN_DISTANCE_M = 1_000.0
    }

    @Serializable
    private data class OsmNameResponse(val elements: List<OsmNameElement> = emptyList())

    @Serializable
    private data class OsmNameElement(
        val lat: Double? = null,
        val lon: Double? = null,
        val center: OsmNameCenter? = null,
        @SerialName("tags") val tags: Map<String, String> = emptyMap(),
    )

    @Serializable
    private data class OsmNameCenter(val lat: Double, val lon: Double)
}
