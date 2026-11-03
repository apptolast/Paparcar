package com.rndeveloper.paparcar.data.parkings

import com.rndeveloper.paparcar.domain.parkings.ParkingZoneGrid
import com.rndeveloper.paparcar.domain.parkings.PublicParkingsQuery
import com.rndeveloper.paparcar.domain.parkings.PublicParkingsResult
import com.rndeveloper.paparcar.domain.parkings.PublicParkingsSource
import com.rndeveloper.paparcar.domain.util.haversineMeters
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * **La base de parkings, en UNA llamada y ya fusionada por el backend.**
 * [PARKINGS-ONE-CALL-MERGED-001]
 *
 * Sustituye a Overpass como camino PRINCIPAL y lo conserva **detrás**, como [fallback]. Medido el
 * 02-11-2026 contra `overpass-api.de` con la consulta que compone la propia app:
 *
 * | | Overpass | esto |
 * |---|---|---|
 * | Madrid (Sol) | **44,8 s** · 227 KB | **0,05-0,20 s** por celda |
 * | Cuenca | **45,9 s** · 33 KB | ídem |
 * | la segunda pregunta seguida | **502 en 0,22 s** | no hay cola: son ficheros en un CDN |
 *
 * ⛔ Y los segundos de Overpass **no eran lo que esperaba un usuario**: el paseo de espejos corta a
 * `WALK_CEILING_MS = 30 s`, así que cuatro de esas cinco respuestas habrían llegado tarde y la app
 * ya habría abandonado la pregunta. Lo que veía el conductor era la lista **recordada** de hasta 30
 * días, o **nada** en una zona nueva — que en pantalla se lee como *«aquí no hay parkings»*.
 *
 * ## ⛔ Por qué es un DECORADOR y no un reemplazo
 *
 * La 1.5.0 está publicada y lee Overpass: **todo corte es aditivo** mientras haya versiones
 * publicadas que lo usen. Así que esto se mete **delante** en la cadena y Overpass no se toca:
 *
 * ```
 * CachingPublicParkingsSource(          memoria, 30 min
 *   DiskBackedPublicParkingsSource(     Room, 30 días
 *     delegate = ApiPublicParkingsSource(          ← esto
 *       fallback = OsmPublicParkingsSource(…)      ← intacto
 * ```
 *
 * ## Cuándo cae al fallback — y es una lista, no un `catch` genérico
 *
 * Cualquiera de estas manda la pregunta a Overpass, y **ninguna requiere decirle nada al cliente**:
 * red caída, 5xx, 4xx, cuerpo ilegible, o un `serve: false` explícito en la respuesta. Un fallback
 * que sólo funciona cuando alguien se acuerda de activarlo no es un fallback.
 */
class ApiPublicParkingsSource(
    private val client: HttpClient,
    private val fallback: PublicParkingsSource,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : PublicParkingsSource {

    /**
     * Lo mismo que cubra el fallback.
     *
     * ⛔ No se inventa un registro por país: la base publicada hoy es España, pero **el que decide
     * si hay respuesta es el backend**, celda a celda — una celda sin fichero contesta `[]`, que es
     * la verdad. Un `covers()` con una lista de países aquí sería una segunda fuente de verdad sobre
     * la cobertura, y derivaría del snapshot en cuanto se publique un país más.
     */
    override fun covers(countryCode: String): Boolean = fallback.covers(countryCode)

    override suspend fun parkingsNear(query: PublicParkingsQuery): Result<PublicParkingsResult> {
        val response = fetch(query)
        // ⛔ `serve == false` es una orden del backend, no un error, y por eso NO se registra como
        // tal: es el interruptor de §D.3 y su efecto es exactamente el del fallo, sin el ruido.
        if (response == null || !response.serve) return fallback.parkingsNear(query)
        return runCatching { response.toResult(query) }
            // Si la respuesta vino pero no se puede convertir, sigue habiendo un camino: el viejo.
            .recoverCatching { return fallback.parkingsNear(query) }
    }

    /** ⛔ La red no recuerda nada. Lo que sostiene una respuesta son las capas de encima. */
    override suspend fun remembered(query: PublicParkingsQuery): PublicParkingsResult? =
        fallback.remembered(query)

    private suspend fun fetch(query: PublicParkingsQuery): ParkingsApiResponse? = runCatching {
        // La celda se deriva del punto POR EL QUE SE PREGUNTA, que el caso de uso ya ha ajustado al
        // centro de la zona — así que `zoneOf` devuelve esa misma zona y la URL es idéntica para
        // toda ella. Eso es lo que hace que el CDN acierte. [OSM-PRECOOKED-SNAPSHOT-001]
        val cell = ParkingZoneGrid.zoneOf(query.latitude, query.longitude).id
        val http = client.get("$baseUrl$PATH") {
            parameter("cell", cell)
            parameter("v", SCHEMA_VERSION)
        }
        if (!http.status.isSuccess()) return null
        withContext(Dispatchers.Default) { json.decodeFromString<ParkingsApiResponse>(http.bodyAsText()) }
    }.getOrNull()

    /**
     * ⛔ **Fuera del hilo del llamante, y no es pulcritud.** Este carril se lanza desde
     * `viewModelScope`, cuyo dispatcher es el PRINCIPAL. Lo que corre aquí son **927 parkings y
     * 95 KB de JSON** en la celda de Sol [MEDIDO en el emulador], decodificados, más una haversine
     * por elemento y una ordenación. Es el presupuesto de varios frames, justo mientras se
     * panoramiza el mapa. Misma razón y mismo `Default` que `OsmPublicParkingsSource`.
     */
    private suspend fun ParkingsApiResponse.toResult(query: PublicParkingsQuery) =
        withContext(Dispatchers.Default) {
            PublicParkingsResult(
                parkings = parkings.map { it.toPublicParking() }.sortedBy {
                    haversineMeters(query.latitude, query.longitude, it.latitude, it.longitude)
                },
                // Esto acaba de llegar de la red, y lo dice no diciendo nada.
                rememberedAtMs = null,
            )
        }

    private companion object {
        /**
         * ⛔ El MISMO origen que sirve la web y el snapshot. No es casualidad ni comodidad: el
         * `rewrite` de `firebase.json` vive ahí, y el artefacto que la función lee es el que
         * `OSM-PRECOOKED-SNAPSHOT-001` publica bajo ODbL en ese dominio.
         */
        const val DEFAULT_BASE_URL = "https://paparcar.com"
        /**
         * ⛔ **El `.json` del path es obligatorio, y se mide, no se opina.** La regla de cabeceras
         * de rutas SIN EXTENSIÓN de `firebase.json` (el glob `!(*.*)`) casa **cualquier** ruta cuyo
         * último segmento no lleve punto
         * — está ahí para que las páginas sin extensión de la web revaliden — y con `/api/parkings`
         * pisaba el `Cache-Control` de la función con `max-age=0, must-revalidate`. Medido en la URL
         * viva: `X-Cache: MISS`. O sea que el CDN **no cacheaba nada** y la función habría corrido
         * **una vez por usuario**, que es exactamente el coste que esta arquitectura existe para
         * evitar. Con extensión, la única regla que casa es `**` (que no pone caché) y sobrevive el
         * TTL por RESPUESTA. [PARKINGS-ONE-CALL-MERGED-001]
         */
        const val PATH = "/api/parkings.json"

        /** El esquema de la respuesta. Viaja en la URL para que un cambio no sirva lo viejo a lo nuevo. */
        const val SCHEMA_VERSION = "1"

        val json = Json { ignoreUnknownKeys = true }
    }
}
