package com.rndeveloper.paparcar.data.fuel

import com.rndeveloper.paparcar.domain.fuel.FuelStationsQuery
import com.rndeveloper.paparcar.domain.fuel.FuelStationsResult
import com.rndeveloper.paparcar.domain.fuel.FuelStationsSource
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

/**
 * Germany, via Tankerkönig — the MTS-K register (Bundeskartellamt) under CC BY 4.0.
 * [FUEL-GERMANY-THE-FEED-ANSWERS-WHOLE-001]
 *
 * The feed answers whole — name, brand, address and prices in one response, native radius, server
 * ordering — so this class asks one question and hands the body to [GermanyStationParser]. No OSM
 * join, no second request, nothing filtered on the phone.
 *
 * The one thing it needs that no other national feed does is a **key**. It is personal, free, and
 * gitignored (`local.properties` — the repo is public), so it arrives as a PROVIDER rather than a
 * value: a build without one exists legitimately (anyone cloning the repo), and that build must
 * behave as "Germany has no feed", not as "Germany's feed is down" — see [claims].
 */
class GermanyFuelStationsSource(
    private val client: HttpClient,
    private val apiKey: () -> String,
) : FuelStationsSource {

    override fun covers(countryCode: String): Boolean = countryCode.uppercase() == COUNTRY

    /**
     * ⛔ No key, no claim. If a keyless build claimed Germany and then failed, the registry would
     * mark the fallback with `didFallBack` and the lane would say «the official prices did not
     * load» forever — a sentence about a feed that was never askable. Unclaimed, Germany degrades
     * to the honest floor: forecourts from OSM, no prices, no false apology.
     */
    override fun claims(query: FuelStationsQuery): Boolean =
        covers(query.countryCode) && apiKey().isNotBlank()

    override suspend fun stationsNear(query: FuelStationsQuery): Result<FuelStationsResult> =
        runCatching {
            val response = client.get(ENDPOINT) {
                url {
                    parameters.append("lat", query.latitude.toString())
                    parameters.append("lng", query.longitude.toString())
                    // In KM here — the same parameter is metres in France and the mismatch is
                    // silent, so the conversion lives in exactly one line. Documented ceiling 25.
                    parameters.append(
                        "rad",
                        (query.radiusMeters / METERS_PER_KM).coerceAtMost(MAX_RADIUS_KM).toString(),
                    )
                    // Nearest first, decided by the server — measured intact at 347 stations for
                    // 25 km, so no output cap truncates the radius we ask for.
                    parameters.append("sort", "dist")
                    parameters.append("type", "all")
                    parameters.append("apikey", apiKey())
                }
            }
            if (!response.status.isSuccess()) {
                throw IllegalStateException("Tankerkönig answered ${response.status.value}")
            }
            FuelStationsResult(
                // The parser also gates `ok:false` — a dead key arrives as HTTP 200 (measured).
                stations = GermanyStationParser.parse(response.bodyAsText()),
                // ⛔ Null and honest: the feed stamps neither the response nor the price. What it
                // DOES know is the next field.
                reportedAt = null,
                source = SOURCE,
                // MTS-K prices are near-real-time by law — the fact this source can state where
                // Spain states a response hour and France an oldest-price bound.
                pricesAreLive = true,
            )
        }

    companion object {
        /** ⛔ Part of the disk cache's key: ids are only unique WITHIN a source. */
        const val SOURCE = "de-tankerkoenig"
        private const val COUNTRY = "DE"
        private const val METERS_PER_KM = 1_000.0
        private const val MAX_RADIUS_KM = 25.0
        const val ENDPOINT = "https://creativecommons.tankerkoenig.de/json/list.php"
    }
}
