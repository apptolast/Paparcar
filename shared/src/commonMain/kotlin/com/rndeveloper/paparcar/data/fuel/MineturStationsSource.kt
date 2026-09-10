package com.rndeveloper.paparcar.data.fuel

import com.rndeveloper.paparcar.domain.fuel.FuelStation
import com.rndeveloper.paparcar.domain.fuel.FuelStationsQuery
import com.rndeveloper.paparcar.domain.fuel.FuelStationsResult
import com.rndeveloper.paparcar.domain.fuel.FuelStationsSource
import com.rndeveloper.paparcar.domain.util.haversineMeters
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Spain, straight from the ministry. [FUEL-STATIONS-LIST-001]
 *
 * Lives in commonMain and speaks Ktor rather than following `OverpassPlacesDataSourceImpl`, which
 * splits its transport into an Android class and an iOS one because it uses `HttpURLConnection`.
 * With eight countries on the map that pattern costs sixteen classes; Ktor is already on the
 * commonMain classpath (it arrives with Coil's network fetcher, and both engines are already
 * declared per platform), so a shared client adds no dependency and keeps each country to one file
 * that a test can drive with `MockEngine`.
 *
 * The feed is asked **per province** — the whole country is 12 MB — so this source needs the region
 * name, and says so by failing when it cannot resolve one.
 */
class MineturStationsSource(
    private val client: HttpClient,
) : FuelStationsSource {

    private val provincesLock = Mutex()
    private var provinces: Map<String, String>? = null

    private val municipalitiesLock = Mutex()
    private var municipalities: Map<String, SpanishMunicipalities.Entry>? = null

    override fun covers(countryCode: String): Boolean = countryCode.uppercase() == COUNTRY

    /**
     * TWO keys, tried in order, because the one we used to insist on is the one a phone often does
     * not have. [HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001]
     *
     * 1. **The town.** Precise and smaller: `FiltroMunicipio` returns one municipality instead of a
     *    whole province. It is also the field an address always carries.
     * 2. **The province**, from the town's own row when we found one, else from `regionName` as
     *    before — the wider net for a town the ministry spells in a way we did not match.
     *
     * ⛔ Only when BOTH fail does this throw, and only then does the registry fall back to a feed
     * with no prices. Measured on a real phone the old single key produced the opposite: «Andalucía»
     * is not a province, so Spain — a country that publishes prices for that exact street, stamped
     * the same afternoon — was reported as a country that publishes none.
     */
    override suspend fun stationsNear(query: FuelStationsQuery): Result<FuelStationsResult> =
        runCatching {
            // ⛔ El catálogo solo se descarga si hay pueblo que buscar. Escrito al revés
            // —`find(query.cityName, municipalities())`— evaluaba `municipalities()` SIEMPRE, así que
            // una consulta sin ciudad se traía la lista entera de municipios de España para no
            // mirarla. Lo cazaron los tests que ya existían del camino por provincia.
            val town = query.cityName?.takeIf { it.isNotBlank() }
                ?.let { city -> SpanishMunicipalities.find(city, municipalities()) }

            // ⛔ An EMPTY town is a reason to widen, not an answer. A municipality with no forecourt
            // at all is common; the province around it usually has several within the radius. This
            // is the one place where "empty" is allowed to hand over, and it hands over WITHIN the
            // national feed — never out of it.
            val fromTown = town?.let { parse(fetch("$BASE/EstacionesTerrestres/FiltroMunicipio/${it.municipalityId}"), query) }
            if (fromTown != null && fromTown.stations.isNotEmpty()) return@runCatching fromTown

            val provinceId = town?.provinceId?.takeIf { it.isNotEmpty() }
                ?: SpanishProvinces.idFor(query.regionName, provinces())
                ?: throw IllegalStateException(
                    "No Spanish town matches '${query.cityName}' and no province matches '${query.regionName}'",
                )

            parse(fetch("$BASE/EstacionesTerrestres/FiltroProvincia/$provinceId"), query)
        }

    private fun parse(body: String, query: FuelStationsQuery) = FuelStationsResult(
        stations = MineturStationParser.parseStations(body)
            .withinRadius(query.latitude, query.longitude, query.radiusMeters),
        reportedAt = MineturStationParser.parseReportedAt(body),
        source = SOURCE,
        // Spain downloads a WHOLE province (or town) and filters it on the phone, so it never stops
        // short of the radius it was asked for. There is nothing to shrink.
        isTruncated = false,
    )

    /**
     * A province is a whole province, so the radius filter is ours to apply — and it doubles as the
     * sort, because "nearest first" is the only order this list can have before the user has said
     * anything about price.
     */
    private fun List<FuelStation>.withinRadius(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
    ): List<FuelStation> = asSequence()
        .map { it to haversineMeters(latitude, longitude, it.latitude, it.longitude) }
        .filter { (_, distance) -> distance <= radiusMeters }
        .sortedBy { (_, distance) -> distance }
        .map { (station, _) -> station }
        .toList()

    /**
     * The province catalogue, fetched once per process.
     *
     * Behind a [Mutex] because two screens opening at the same moment would otherwise both pay for
     * the same list — and it is the ministry's own list, so it is not ours to hardcode: 52 names
     * maintained by hand is 52 chances to be wrong about a rename.
     */
    private suspend fun provinces(): Map<String, String> = provincesLock.withLock {
        provinces ?: SpanishProvinces.parse(fetch("$BASE/Listados/Provincias/"))
            .also { provinces = it }
    }

    /**
     * The municipality catalogue, fetched once per process — same contract as [provinces], and for
     * the same reason: it is the ministry's list, not ours to hardcode.
     */
    private suspend fun municipalities(): Map<String, SpanishMunicipalities.Entry> =
        municipalitiesLock.withLock {
            municipalities ?: SpanishMunicipalities.parse(fetch("$BASE/Listados/Municipios/"))
                .also { municipalities = it }
        }

    private suspend fun fetch(url: String): String {
        val response = client.get(url)
        if (!response.status.isSuccess()) {
            throw IllegalStateException("Ministry feed answered ${response.status.value} for $url")
        }
        return response.bodyAsText()
    }

    companion object {
        /** ⛔ Part of the disk cache's key: ids are only unique WITHIN a source. */
        const val SOURCE = "es-minetur"
        private const val COUNTRY = "ES"

        /**
         * ⛔ `PreciosCarburantes`, **plural**. The singular returns 404, and that one missing letter
         * was read once as "the API was retired" — which nearly bought a paid alternative to replace
         * a service that was answering fine the whole time.
         */
        const val BASE =
            "https://sedeaplicaciones.minetur.gob.es/ServiciosRESTCarburantes/PreciosCarburantes"
    }
}
