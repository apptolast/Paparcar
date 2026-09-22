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
     * España también se reconoce **por coordenadas** cuando nadie ha podido nombrar el país.
     * [FUEL-A-POSTCODE-IS-THE-PROVINCE-ID-001]
     *
     * ⛔ Solo cuando el país viene VACÍO. Si el geocoder dijo «PT», dijo PT: una caja envolvente
     * rebosa por las fronteras y no está aquí para corregir a quien sí contestó, sino para dar una
     * respuesta donde no había ninguna.
     */
    override fun claims(query: FuelStationsQuery): Boolean = when {
        covers(query.countryCode) -> true
        query.countryCode.isNotBlank() -> false
        else -> SpanishProvinceBounds.idFor(query.latitude, query.longitude) != null
    }

    /**
     * TRES claves, en orden, porque la que se exigía al principio es la que un móvil muchas veces no
     * tiene. [HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001]
     *
     * 1. **The town** — the field an address always carries, and the one that names a province when
     *    the geocoder wrote an autonomous community where a province goes.
     *    ⛔ Sólo NOMBRA. Servir sus estaciones y parar ahí escondía el 11 % de lo que había en el
     *    radio. [FUEL-A-RADIUS-DOES-NOT-RESPECT-A-TOWN-BOUNDARY-001]
     * 2. **The province**, from the town's own row when we found one, else from `regionName` as
     *    before — the wider net for a town the ministry spells in a way we did not match. **Es
     *    siempre la que sirve la lista**: el feed se pide por provincia y se filtra por radio aquí.
     * 3. **Las coordenadas** ([SpanishProvinceBounds]), que es la única clave que no depende de que
     *    nadie conteste. Va la última a propósito: acierta el 94,6 % y las otras dos, cuando están,
     *    no fallan. [FUEL-A-POSTCODE-IS-THE-PROVINCE-ID-001]
     *
     * ⛔ Only when the THREE fail does this throw, and only then does the registry fall back to a
     * feed with no prices. Measured on a real phone the old single key produced the opposite:
     * «Andalucía» is not a province, so Spain — a country that publishes prices for that exact
     * street, stamped the same afternoon — was reported as a country that publishes none.
     */
    override suspend fun stationsNear(query: FuelStationsQuery): Result<FuelStationsResult> =
        runCatching {
            // ⛔ El catálogo solo se descarga si hay pueblo que buscar. Escrito al revés
            // —`find(query.cityName, municipalities())`— evaluaba `municipalities()` SIEMPRE, así que
            // una consulta sin ciudad se traía la lista entera de municipios de España para no
            // mirarla. Lo cazaron los tests que ya existían del camino por provincia.
            val town = query.cityName?.takeIf { it.isNotBlank() }
                ?.let { city -> SpanishMunicipalities.find(city, municipalities()) }

            // ⛔ **EL PUEBLO NOMBRA LA PROVINCIA; NO SIRVE LAS ESTACIONES.**
            // [FUEL-A-RADIUS-DOES-NOT-RESPECT-A-TOWN-BOUNDARY-001]
            //
            // Aquí se preguntaba por `FiltroMunicipio` y, si contestaba algo, esa era la respuesta
            // entera. El municipio define un PAYLOAD y se le estaba dejando definir la COBERTURA, y
            // no puede: lo que se pide es un radio de 5 km —un círculo— y un municipio es un
            // polígono administrativo. No se contienen.
            //
            // Medido sobre el feed real de Cádiz (291 estaciones): el **11 %** de lo que hay a 5 km
            // de una estación cualquiera pertenece a otro municipio, y en la bahía —cuatro pueblos
            // pegados— sube al **90 %**. Es el síntoma de la issue #113: estás delante de una
            // gasolinera y no sale.
            //
            // El guard de «municipio vacío ensancha» era lo que lo hacía invisible: mientras el
            // pueblo contestara ALGO, la provincia no se consultaba y nadie veía lo que faltaba.
            //
            // Lo que el pueblo sí aporta sigue abajo intacto, y es para lo que se trajo: su
            // `provinceId` es la clave nº 1 de enrutado, porque es el campo que una dirección
            // siempre trae y ninguna comunidad autónoma es una provincia.
            // [HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001]

            // ⛔ Mismo error que la vía de municipios de arriba, en el mismo fichero y sin cazar:
            // `provinces()` es un ARGUMENTO, así que se evaluaba SIEMPRE — con `regionName` a null,
            // `idFor` devuelve null sin mirar nada y el catálogo de las 52 provincias ya se había
            // descargado para nada. Y `regionName` a null es justo lo que deja un geocode mudo, o
            // sea que la vía que menos puede permitirse un viaje de red era la que lo pagaba.
            // [FUEL-AN-UNKNOWN-COUNTRY-IS-NOT-A-COUNTRY-WITHOUT-PRICES-001]
            val region = query.regionName?.takeIf { it.isNotBlank() }
            val named = town?.provinceId?.takeIf { it.isNotEmpty() }
                // ⛔ El código postal, ANTES que el nombre de región y sin descargar catálogo
                // ninguno: los ids del ministerio son los códigos INE, o sea los dos primeros
                // dígitos del postal. No empareja nombres, así que no le afectan ni los acentos ni
                // los artículos ni que el geocoder escriba la comunidad donde va la provincia.
                // Y es la ÚNICA clave que existe en Ceuta y Melilla, donde `subAdminArea` no llega.
                // [FUEL-A-POSTCODE-IS-THE-PROVINCE-ID-001]
                ?: SpanishProvinces.idFromPostalCode(query.postalCode)
                ?: region?.let { SpanishProvinces.idFor(it, provinces()) }
            // La tercera clave. [FUEL-A-POSTCODE-IS-THE-PROVINCE-ID-001]
            val guessed = named ?: SpanishProvinceBounds.idFor(query.latitude, query.longitude)
            val provinceId = guessed
                ?: throw IllegalStateException(
                    "No Spanish town matches '${query.cityName}', no province matches " +
                        "'${query.regionName}' and (${query.latitude}, ${query.longitude}) is in none",
                )

            val fromProvince = parse(fetch("$BASE/EstacionesTerrestres/FiltroProvincia/$provinceId"), query)

            // ⛔ **Una provincia ADIVINADA no puede dictar un veredicto.** Si la provincia salió de
            // las coordenadas y la respuesta viene vacía, lo más probable no es que no haya
            // gasolineras: es que hemos preguntado a la provincia de al lado — medido, le pasa al
            // 4,6 % de los puntos, todos de frontera. Devolver esa lista vacía diría «no hay ninguna
            // a 5 km», que es exactamente la clase de frase que este código existe para no decir.
            // Fallar manda al registro al feed sin precios, que es lo que había ANTES de poder
            // adivinar nada: no se pierde nada, no se miente.
            //
            // Con provincia NOMBRADA el vacío sí es respuesta, y se respeta: lo dice quien sabe
            // dónde está. [FUEL-A-POSTCODE-IS-THE-PROVINCE-ID-001]
            if (named == null && fromProvince.stations.isEmpty()) {
                throw IllegalStateException(
                    "Province $provinceId was guessed from coordinates and answered empty",
                )
            }
            fromProvince
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
