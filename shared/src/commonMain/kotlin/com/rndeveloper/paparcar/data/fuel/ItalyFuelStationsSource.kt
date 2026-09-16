package com.rndeveloper.paparcar.data.fuel

import com.rndeveloper.paparcar.domain.fuel.FuelStation
import com.rndeveloper.paparcar.domain.fuel.FuelStationsQuery
import com.rndeveloper.paparcar.domain.fuel.FuelStationsResult
import com.rndeveloper.paparcar.domain.fuel.FuelStationsSource
import com.rndeveloper.paparcar.domain.util.haversineMeters
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.math.ceil

/**
 * Italy, from the ministry's own Osservaprezzi — the register every forecourt must report to.
 * [FUEL-ITALY-A-PRICE-DEPENDS-ON-WHO-PUMPS-IT-001]
 *
 * ⛔ **Not the published CSV, and that is the decision this class exists to hold.** MIMIT's open
 * data channel is two files, 7,5 MB, ungzipped, with no query of any kind — but size is not why it
 * lost. Its own page says it publishes *«le informazioni in vigore alle ore 8 del giorno precedente
 * a quello di pubblicazione»*, so by construction it is a day and a half old, and measured against
 * the live register on the same 820 station+fuel+mode keys in Rome **71,6% of its prices
 * disagreed** (median 0,02 €/L, max 0,17). FUEL-STATIONS-LIST-001 disqualified a commercial proxy
 * of these same feeds at **30%** and one or two cents, writing that for a feature whose promise is
 * comparing prices that is not staleness but the function broken. Wiring the CSV would have kept
 * the file and dropped the reasoning.
 *
 * What answers instead is the backend of the ministry's own public search: one point and a radius,
 * 229 stations for 5 km in Rome in **110 KB and under a second**, no key and no quota observed.
 *
 * The CSV remains the documented relay, exactly like FuelWide: if this endpoint dies, taking a
 * day-old price over no price at all is a decision with a real cost, and it gets made then.
 */
class ItalyFuelStationsSource(
    private val client: HttpClient,
) : FuelStationsSource {

    override fun covers(countryCode: String): Boolean = countryCode.uppercase() == COUNTRY

    override suspend fun stationsNear(query: FuelStationsQuery): Result<FuelStationsResult> =
        runCatching {
            val response = client.post(ENDPOINT) {
                contentType(ContentType.Application.Json)
                setBody(requestBody(query))
            }
            if (!response.status.isSuccess()) {
                throw IllegalStateException("Osservaprezzi answered ${response.status.value}")
            }
            FuelStationsResult(
                // The parser also gates `success:false` — a malformed ask arrives as HTTP 200.
                stations = ItalyStationParser.parse(response.bodyAsText()).nearestFirst(query),
                // ⛔ Null: this feed stamps each STATION, and that stamp travels inside the price
                // where the reader already turns it into an age. A single hour for the response
                // would be a claim no station made — the same reason France leaves it null.
                reportedAt = null,
                source = SOURCE,
                // What gets capped here is the RADIUS, not the number of rows, so the answer is
                // complete out to the ring it was allowed to ask about and short only beyond it.
                isTruncated = askedKm(query) > MAX_RADIUS_KM,
            )
        }

    /**
     * ⛔ **The feed cannot sort by distance.** `priceOrder` does what it says — measured, `asc`
     * comes back sorted by price and never by distance — and there is no other ordering parameter.
     * So the port's "nearest first" is honoured here, with the same haversine Spain uses for the
     * same reason: before the user has said anything about price, distance is the only order this
     * list can have.
     *
     * The radius is trimmed here too: the ask is rounded UP to whole kilometres, so a 4,5 km
     * question would otherwise come back answering five.
     */
    private fun List<FuelStation>.nearestFirst(query: FuelStationsQuery): List<FuelStation> = this
        .map { it to haversineMeters(query.latitude, query.longitude, it.latitude, it.longitude) }
        .filter { (_, distance) -> distance <= query.radiusMeters }
        .sortedBy { (_, distance) -> distance }
        .map { (station, _) -> station }

    /**
     * ⛔ **One point, not a polygon.** Four or five vertices answer `success:false` with an empty
     * list, which reads exactly like "no forecourts here". Five different `fuelType` values were
     * tried before the shape of `points` turned out to be the thing that was wrong.
     *
     * ⛔ **`fuelType` filters WHICH STATIONS come back, never which prices.** Every station returns
     * all of its quotes in both modes regardless, so asking for petrol would drop 62 of the 229
     * Roman forecourts while changing not one price on the rest. [ANY_FUEL] asks for all of them
     * and the answer arrives whole, which is what the port promises.
     */
    private fun requestBody(query: FuelStationsQuery): String =
        """{"points":[{"lat":${query.latitude},"lng":${query.longitude}}],""" +
            """"radius":${radiusKm(query)},"fuelType":"$ANY_FUEL","priceOrder":"$ANY_ORDER"}"""

    /**
     * ⛔ In KILOMETRES, and **silently capped at ten**. Measured: 1→7 stations, 3→67, 5→229, 10→642,
     * and then 25→642 and 50→642 with the farthest still at 9,99 km. Asking for more is not an
     * error and not an empty list — it is ten kilometres pretending to be fifty.
     *
     * Third country, third convention: Germany takes kilometres and stops at 25, France takes
     * metres and refuses them without a unit. The conversion lives in one line per country for
     * exactly that reason.
     */
    private fun radiusKm(query: FuelStationsQuery): Int =
        askedKm(query).coerceAtMost(MAX_RADIUS_KM)

    /** Rounded UP: under-asking would hide forecourts the query did cover. Trimmed back exactly in
     *  [nearestFirst], which works in metres. */
    private fun askedKm(query: FuelStationsQuery): Int =
        ceil(query.radiusMeters / METERS_PER_KM).toInt().coerceAtLeast(1)

    companion object {
        /** ⛔ Part of the disk cache's key: ids are only unique WITHIN a source. */
        const val SOURCE = "it-osservaprezzi"
        private const val COUNTRY = "IT"
        private const val METERS_PER_KM = 1_000.0
        private const val MAX_RADIUS_KM = 10

        /** `"<fuelId>-<self>"`; `x-x` is every product in both modes — see [requestBody]. */
        private const val ANY_FUEL = "x-x"

        /**
         * Required by the endpoint and deliberately not load-bearing: whatever it sorts by, the
         * list is re-ordered by distance in [nearestFirst] before it leaves this class.
         */
        private const val ANY_ORDER = "asc"

        const val ENDPOINT = "https://carburanti.mise.gov.it/ospzApi/search/zone"
    }
}
