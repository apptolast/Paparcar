package com.rndeveloper.paparcar.tools.parkings

import com.rndeveloper.paparcar.data.parkings.OsmPublicParkingParser
import com.rndeveloper.paparcar.data.parkings.PublicParkingSnapshotCell
import com.rndeveloper.paparcar.data.parkings.PublicParkingSnapshotManifest
import com.rndeveloper.paparcar.data.parkings.toSnapshotEntry
import com.rndeveloper.paparcar.domain.parkings.ParkingZoneGrid
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.GZIPOutputStream

/**
 * El generador del snapshot de parkings, **ejecutando el filtro de la app en vez de copiarlo**.
 * [OSM-PRECOOKED-SNAPSHOT-001]
 *
 * ## Por qué vive en un source set de TEST
 *
 * `:shared` tiene `androidTarget()` y dos targets de iOS, y **ningún target `jvm`**, así que no hay
 * dónde colgar un `main()` que vea `commonMain` desde una JVM de escritorio. El runtime de los tests
 * de Android **sí es** esa JVM, y un source set de test tiene justo la propiedad que una herramienta
 * de build necesita: **no se empaqueta en nada**. Añadir un target `jvm` entero para generar un
 * fichero semanal costaría resolver Room/KSP, ktor y GitLive para `jvm` — un cambio de build grande
 * para una tarea de generación.
 *
 * ⛔ **Y la alternativa descartada es la que importa:** portar el filtro a Python. Lo que decide qué
 * es un parking para Paparcar está medido en [OsmPublicParkingParser] —el bordillo
 * (`parking=street_side|lane`, 256 de 300 elementos en el centro de Málaga), los ocho valores de
 * `access` cerrado, la excepción medida de `access=customers`, los dos únicos valores de `fee` que
 * afirman algo— y **una segunda implementación de eso derivaría en silencio**: el snapshot enseñaría
 * un conjunto distinto del que la app enseña hoy y nadie lo vería hasta que un usuario mirase un
 * bordillo dibujado como parking.
 *
 * Se invoca con `./gradlew :shared:parkingSnapshotTool --args="…"`.
 */
object SnapshotToolMain {

    @JvmStatic
    fun main(args: Array<String>) {
        when (args.firstOrNull()) {
            PROBE -> probe()
            COUNT -> count(args.getOrNull(1) ?: error("$COUNT necesita el JSON de osm_extract.py"))
            QUERY -> query(
                args.getOrNull(1)?.toDouble() ?: error("$QUERY necesita lat"),
                args.getOrNull(2)?.toDouble() ?: error("$QUERY necesita lon"),
            )
            COMPARE -> compare(
                overpassPath = args.getOrNull(1) ?: error("$COMPARE necesita la respuesta de Overpass"),
                versionDir = args.getOrNull(2) ?: error("$COMPARE necesita el directorio de la version"),
                lat = args.getOrNull(3)?.toDouble() ?: error("$COMPARE necesita lat"),
                lon = args.getOrNull(4)?.toDouble() ?: error("$COMPARE necesita lon"),
            )
            GRID_FIXTURES -> gridFixtures(
                args.getOrNull(1) ?: error("$GRID_FIXTURES necesita el fichero de salida"),
            )
            SNAPSHOT -> snapshot(
                outRoot = args.getOrNull(1) ?: error("$SNAPSHOT necesita el directorio de salida"),
                version = args.getOrNull(2) ?: error("$SNAPSHOT necesita la version (p. ej. 2026-W44)"),
                extractPaths = args.drop(EXTRACTS_FROM).ifEmpty {
                    error("$SNAPSHOT necesita al menos un JSON de osm_extract.py")
                },
            )
            else -> {
                println("usage: parkingSnapshotTool <$PROBE | $COUNT <json> | $SNAPSHOT <json> <dir> <ver>>")
                println("  $PROBE     comprueba que el filtro de la app corre en esta JVM")
                println("  $COUNT     aplica el filtro de la app a un extracto y mide el rendimiento")
                println("  $SNAPSHOT <dir> <ver> <raw...>  escribe las celdas, el manifiesto y el puntero")
                println("  $GRID_FIXTURES <fichero>  regenera la tabla de fixtures de la rejilla")
                println("  $QUERY <lat> <lon>  imprime la consulta que la app le haria a Overpass")
                println("  $COMPARE <overpass.json> <dir-version> <lat> <lon>  snapshot vs Overpass vivo")
            }
        }
    }

    /**
     * La comprobación que decide la forma del pipeline: que [OsmPublicParkingParser] —código de
     * `commonMain`— corra **en una JVM de escritorio**, con la misma doctrina que en el device.
     *
     * Se le dan tres elementos con la forma de Overpass: uno utilizable, un bordillo y uno privado.
     * Si el filtro está vivo, sale **uno**.
     */
    private fun probe() {
        val parsed = OsmPublicParkingParser.parse(PROBE_PAYLOAD)
        println("parsed=${parsed.size} ${parsed.map { "${it.id}|${it.kind}|${it.fee}" }}")
        check(parsed.size == 1) { "el filtro no se aplicó: se esperaba 1 y salieron ${parsed.size}" }
        check(parsed.single().id == "osm/way/1") { "id inesperado: ${parsed.single().id}" }
        println("OK · el filtro de commonMain corre en esta JVM")
    }

    /**
     * Cuantos elementos de un extracto **sobreviven al filtro de la app**, y repartidos por que.
     *
     * Es la medicion que decide el tamano del artefacto: el expediente estimo ~18 MB para Espana
     * sobre los **120.906 sin filtrar**, y el filtro se come la mayoria — `parking=street_side|lane`
     * eran 256 de 300 elementos en el centro de Malaga.
     */
    private fun count(path: String) {
        val body = File(path).readText()
        // ⛔ Contado deserializando, no con un `Regex("\"type\":")`: ese contaba 14.716 donde habia
        // 14.646, porque **`type` tambien es un tag de OSM** (`type=multipolygon` en las relations) y
        // la expresion no distingue un campo del elemento de una clave de su diccionario de tags.
        // Un denominador inflado habria rebajado el rendimiento medido. [MEDIDO al abrirlo]
        val total = json.decodeFromString<CountEnvelope>(body).elements.size
        val kept = OsmPublicParkingParser.parse(body)
        val pct = if (total == 0) 0.0 else kept.size * 100.0 / total
        println("entran=$total salen=${kept.size} (${"%.1f".format(pct)}%)")
        println("  por kind: " + kept.groupingBy { it.kind }.eachCount().toSortedMap(compareBy { it.name }))
        println("  por fee:  " + kept.groupingBy { it.fee }.eachCount().toSortedMap(compareBy { it.name }))
        println("  con name=${kept.count { it.name != null }} capacity=${kept.count { it.capacity != null }} " +
            "maxheight=${kept.count { it.maxHeightMeters != null }} hours=${kept.count { it.openingHours != null }}")
    }

    /**
     * **Escribe el snapshot**: una celda por fichero, su manifiesto, y el puntero a esta version.
     * [OSM-PRECOOKED-SNAPSHOT-001]
     *
     * ```
     * <dir>/parkings/v1/<version>/<band>_<col>.json.gz   una celda, inmutable
     * <dir>/parkings/v1/<version>/index.json             el manifiesto de lo publicado
     * <dir>/parkings/v1/latest.json                      quien es la version vigente
     * ```
     *
     * ⛔ **Un snapshot publicado es INMUTABLE y por eso va bajo su version.** Si una regeneracion
     * sobrescribiera la celda, un CDN con dos entradas en vuelo serviria mitad de un snapshot y
     * mitad del otro — y las dos mitades se verian perfectamente validas. Publicar bajo una version
     * nueva y mover el puntero al final hace el cambio atomico para quien lee.
     *
     * ⛔ **Y la salida es DETERMINISTA** —celdas ordenadas, parkings ordenados por id, manifiesto
     * ordenado—: dos generaciones sobre el mismo extracto dan el mismo byte, asi que se puede
     * comprobar que una semana no cambio nada comparando hashes en vez de confiando.
     */
    private fun snapshot(outRoot: String, version: String, extractPaths: List<String>) {
        val started = System.currentTimeMillis()
        // ⛔ **Varios extractos, y DEDUPLICADOS por id.** Un territorio no cabe en un fichero de
        // Geofabrik —las Canarias viven en `africa/`, no en `europe/spain`, y con un solo extracto
        // el snapshot las deja a CERO sin que nada falle— y los recortes de Geofabrik se **solapan**
        // en las fronteras, asi que dos extractos vecinos traen el mismo parking dos veces. El id
        // (`osm/<tipo>/<id>`) es estable, asi que el ultimo gana y el conteo no se infla.
        val parkings = extractPaths
            .flatMap { path ->
                val parsed = OsmPublicParkingParser.parse(File(path).readText())
                println("  $path -> ${parsed.size} parkings")
                parsed
            }
            .associateBy { it.id }
            .values
            .toList()
        val duplicates = extractPaths.sumOf { path ->
            OsmPublicParkingParser.parse(File(path).readText()).size
        } - parkings.size
        if (duplicates > 0) println("  solapamiento entre extractos: $duplicates repetidos, deduplicados")
        val byCell = parkings
            .groupBy { ParkingZoneGrid.zoneOf(it.latitude, it.longitude).id }
            .toSortedMap()

        val versionDir = File(outRoot, "$PUBLISH_PREFIX/$version").apply { mkdirs() }
        var storedBytes = 0L
        var wireBytes = 0L
        byCell.forEach { (cellId, cellParkings) ->
            val payload = PublicParkingSnapshotCell(
                cell = cellId,
                version = version,
                parkings = cellParkings.sortedBy { it.id }.map { it.toSnapshotEntry() },
            )
            val body = json.encodeToString(payload).encodeToByteArray()
            File(versionDir, "$cellId.json").writeBytes(body)
            storedBytes += body.size
            wireBytes += gzippedSize(body)
        }

        // ⛔ **Y la base ENTERA en un solo fichero, porque es lo que ODbL pide OFRECER.** El arbol
        // de celdas es la forma de SERVIR —12.566 ficheros para Espana, medido—, y eso es una mala
        // forma de ofrecer una base de datos a un tercero: tendria que adivinar los nombres de las
        // celdas y bajarse doce mil ficheros. Un `.ndjson.gz` de 3 MB es la misma base, descargable
        // de una vez, y deja el recuento de ficheros como un asunto de ingenieria y no de licencia.
        // Una linea por parking (NDJSON y no un array) para que se pueda leer en streaming sin
        // cargar el pais en memoria.
        val bulk = File(versionDir, "$BULK_NAME")
        GZIPOutputStream(bulk.outputStream().buffered()).use { gzip ->
            byCell.forEach { (cellId, cellParkings) ->
                cellParkings.sortedBy { it.id }.forEach { parking ->
                    gzip.write(json.encodeToString(parking.toSnapshotEntry()).encodeToByteArray())
                    gzip.write(NEWLINE)
                }
                // El id de la celda no se escribe por linea: se deriva del punto con la misma
                // rejilla, y repetirlo 70.000 veces es pagar bytes por un dato calculable.
                require(cellId.isNotEmpty())
            }
        }

        val manifest = PublicParkingSnapshotManifest(
            version = version,
            generatedAtMs = started,
            cells = byCell.size,
            parkings = parkings.size,
            counts = byCell.mapValues { (_, value) -> value.size },
        )
        File(versionDir, "index.json").writeText(json.encodeToString(manifest))
        File(outRoot, "$PUBLISH_PREFIX/latest.json").writeText(
            json.encodeToString(manifest.copy(counts = emptyMap())),
        )

        val seconds = (System.currentTimeMillis() - started) / 1000.0
        println("  base entera para ODbL: $BULK_NAME, ${bulk.length() / 1024} KB")
        println(
            "celdas=${byCell.size} parkings=${parkings.size} " +
                "almacen=${storedBytes / 1024} KB cable=${wireBytes / 1024} KB " +
                "en ${"%.1f".format(seconds)}s",
        )
        println("  mediana de parkings por celda: ${byCell.values.map { it.size }.sorted()[byCell.size / 2]}")
        println("  celda mas cargada: ${byCell.maxByOrNull { it.value.size }?.let { "${it.key} con ${it.value.size}" }}")
        println("  -> ${versionDir.path}")
    }

    /**
     * **La consulta que la app le hace a Overpass para ese punto**, impresa tal cual.
     *
     * ⛔ Existe para que la comparacion del criterio 1 use la consulta DE LA APP y no una que yo
     * haya vuelto a escribir: si se compara el snapshot contra una pregunta distinta, la diferencia
     * que salga no dice nada. Es la misma composicion que `GetNearbyPublicParkingsUseCase`: se
     * pregunta por el CENTRO de la zona y con el alcance de la zona.
     */
    private fun query(lat: Double, lon: Double) {
        val zone = ParkingZoneGrid.zoneOf(lat, lon)
        println(
            OsmPublicParkingParser.buildQuery(
                lat = zone.centreLatitude,
                lon = zone.centreLongitude,
                radiusMeters = ParkingZoneGrid.ZONE_RADIUS_METERS,
                timeoutSeconds = QUERY_TIMEOUT_SECONDS,
            ),
        )
    }

    /**
     * **El criterio 1: el conjunto del snapshot contra el que Overpass devuelve HOY**, con una
     * herramienta y no a ojo. [OSM-PRECOOKED-SNAPSHOT-001]
     *
     * Las dos respuestas se reducen a la misma pregunta —*que parkings hay a
     * [com.rndeveloper.paparcar.domain.parkings.PublicParkingsQuery.DEFAULT_RADIUS_METERS] de ESTE
     * punto*— y se comparan por id. El snapshot se lee como lo leera el backend: el **bloque** de
     * celdas alrededor de la celda del punto.
     *
     * ⛔ Una diferencia NO es automaticamente un fallo del pipeline: entre el extracto de Geofabrik
     * y el Overpass de ahora mismo hay dias de ediciones de OSM. Lo que este modo sirve es para
     * mirar la diferencia y poder decir de que lado cae, no para exigir un cero.
     */
    private fun compare(overpassPath: String, versionDir: String, lat: Double, lon: Double) {
        val radius = com.rndeveloper.paparcar.domain.parkings.PublicParkingsQuery.DEFAULT_RADIUS_METERS
        val live = OsmPublicParkingParser.parse(File(overpassPath).readText())
            .filter { within(lat, lon, it.latitude, it.longitude, radius) }
            .associateBy { it.id }

        val zone = ParkingZoneGrid.zoneOf(lat, lon)
        val block = ParkingZoneGrid.blockAround(zone)
        val stored = block
            .mapNotNull { cell -> File(versionDir, "${cell.id}.json").takeIf { it.isFile } }
            .flatMap { json.decodeFromString<PublicParkingSnapshotCell>(it.readText()).parkings }
            .filter { within(lat, lon, it.lat, it.lon, radius) }
            .associateBy { it.id }

        val onlyLive = live.keys - stored.keys
        val onlyStored = stored.keys - live.keys
        val both = live.keys intersect stored.keys
        println("punto=$lat,$lon celda=${zone.id} bloque=${block.size} celdas")
        println("  Overpass vivo: ${live.size}   snapshot: ${stored.size}   en los dos: ${both.size}")
        println("  solo en Overpass (${onlyLive.size}): ${onlyLive.take(SAMPLE_LIMIT)}")
        println("  solo en snapshot (${onlyStored.size}): ${onlyStored.take(SAMPLE_LIMIT)}")
        val moved = both.filter { id ->
            val a = live.getValue(id)
            val b = stored.getValue(id)
            metres(a.latitude, a.longitude, b.lat, b.lon) > COORDINATE_DRIFT_METERS
        }
        println("  mismo id pero movido mas de $COORDINATE_DRIFT_METERS m (${moved.size}): ${moved.take(SAMPLE_LIMIT)}")
        val agreement = if (live.isEmpty()) 0.0 else both.size * 100.0 / live.size
        println("  coincidencia sobre el vivo: ${"%.1f".format(agreement)}%")
    }

    private fun within(lat: Double, lon: Double, otherLat: Double, otherLon: Double, radius: Int): Boolean =
        metres(lat, lon, otherLat, otherLon) <= radius

    private fun metres(lat: Double, lon: Double, otherLat: Double, otherLon: Double): Double =
        com.rndeveloper.paparcar.domain.util.haversineMeters(lat, lon, otherLat, otherLon)

    /**
     * Lo que ese cuerpo pesara EN EL CABLE, sin escribir un `.gz` que nadie va a servir.
     *
     * ⛔ **Las celdas se publican en `.json` PLANO, no pre-comprimidas**, y es por dos trampas que
     * este repo ya ha pagado y tiene escritas en `firebase.json`: una regla de cabecera puede
     * **desplegarse limpia y no casar nada** [WEB-A-HOSTING-HEADER-RULE-MUST-ACTUALLY-MATCH-001], y
     * un `.json.gz` **exige** acertar con `Content-Encoding` o cada consumidor tiene que
     * descomprimirlo a mano. Hosting ya comprime lo que sirve segun el `Accept-Encoding` de quien
     * pregunta, asi que el fichero plano da el mismo cable sin una sola regla nueva — y ademas lo
     * deja legible para el backend del ticket 2 y para cualquiera que lo abra.
     *
     * ⚠️ [SUPUESTO] que la compresion automatica de Hosting cubre `application/json`. **Como
     * comprobarlo:** desplegar una celda y leer `content-encoding` de la URL **viva**, que es la
     * leccion del ticket de arriba — nunca de la documentacion.
     */
    private fun gzippedSize(body: ByteArray): Long {
        val counter = object : java.io.OutputStream() {
            var count = 0L
            override fun write(b: Int) { count++ }
            override fun write(b: ByteArray, off: Int, len: Int) { count += len }
        }
        GZIPOutputStream(counter).use { it.write(body) }
        return counter.count
    }

    /**
     * **Regenera `tools/parkings/grid-fixtures.json`**: la rejilla, escrita como tabla.
     * [OSM-PRECOOKED-SNAPSHOT-001]
     *
     * No la lee la app. La lee [com.rndeveloper.paparcar.architecture.ParkingGridFixtureGuardrailTest],
     * que se pone rojo si la rejilla cambia y nadie regenera el fichero — y la leera el backend en
     * **TypeScript** del ticket 2, que necesita `blockAround` y que, sin esto, tendria que
     * comprobarse contra nada.
     *
     * Los puntos no son decorativos: los mismos nueve del test de cobertura, elegidos para que la
     * tabla cubra los dos hemisferios, el meridiano cero, el ecuador y el paralelo 89 — donde el
     * paso de longitud se dispara y el `clamp` de la rejilla entra en juego.
     */
    private fun gridFixtures(path: String) {
        val samples = SAMPLE_POINTS.map { (lat, lon) ->
            val zone = ParkingZoneGrid.zoneOf(lat, lon)
            GridFixtureSample(
                lat = lat,
                lon = lon,
                cell = zone.id,
                centreLat = zone.centreLatitude,
                centreLon = zone.centreLongitude,
                block = ParkingZoneGrid.blockAround(zone).map { it.id },
            )
        }
        val pretty = Json { prettyPrint = true; explicitNulls = false }
        File(path).writeText(pretty.encodeToString(GridFixtures(samples = samples)) + "\n")
        println("${samples.size} puntos -> $path")
    }

    /** Una sola instancia: el target de Android compila con `allWarningsAsErrors`, y crear un
     *  `Json` por uso es warning. [BUILD-ZERO-WARNINGS-IS-ENFORCED-001] */
    private val json = Json {
        ignoreUnknownKeys = true
        // ⛔ Los nulos NO se escriben: el 94 % de los parkings no tiene `name`. Ver el KDoc de
        // `PublicParkingSnapshotEntry`.
        explicitNulls = false
        // ⛔ **Pero los valores POR DEFECTO sí**, y esto no es cosmetica: kotlinx los omite, asi que
        // `serve = true` del manifiesto no se escribia y el puntero publicado **no contenia la
        // bandera**. El lado que la lee la interpreta ausente como `true`, asi que todo funcionaba
        // — y el interruptor de [PARKINGS-ONE-CALL-MERGED-001] era inservible, porque una bandera
        // que no esta escrita es una bandera que nadie puede encontrar para cambiarla.
        // No afecta a las celdas: sus campos obligatorios no tienen defecto y los opcionales son
        // nulos, que `explicitNulls = false` sigue omitiendo. [MEDIDO: mismo tamano]
        encodeDefaults = true
    }

    /** La tabla de fixtures de la rejilla, tal y como se publica y se vigila. */
    @Serializable
    data class GridFixtures(val samples: List<GridFixtureSample>)

    @Serializable
    data class GridFixtureSample(
        val lat: Double,
        val lon: Double,
        val cell: String,
        val centreLat: Double,
        val centreLon: Double,
        val block: List<String>,
    )

    /** Solo para CONTAR cuantos elementos entran: el modelo de verdad es el del parser. */
    @Serializable
    private data class CountEnvelope(val elements: List<CountElement> = emptyList())

    @Serializable
    private data class CountElement(val id: Long = 0)

    private const val PROBE = "--probe"
    private const val COUNT = "--count"
    private const val SNAPSHOT = "--snapshot"

    /** El primer argumento que es un extracto: `--snapshot <dir> <ver> <raw...>`. */
    private const val EXTRACTS_FROM = 3
    private const val GRID_FIXTURES = "--grid-fixtures"
    private const val QUERY = "--query"
    private const val COMPARE = "--compare"

    /** Lo que la app le pide a Overpass de plazo. */
    private const val QUERY_TIMEOUT_SECONDS = 20

    /** Cuantos ids se enseñan de cada lista: es un diagnostico, no un volcado. */
    private const val SAMPLE_LIMIT = 8

    /** Mas de esto entre las dos coordenadas del MISMO id no es redondeo: es una edicion de OSM. */
    private const val COORDINATE_DRIFT_METERS = 10.0

    /** Los nueve puntos del testigo de cobertura, y por las mismas razones. */
    private val SAMPLE_POINTS = listOf(
        40.4168 to -3.7038,    // Madrid, Puerta del Sol
        36.7213 to -4.4214,    // Malaga
        41.3874 to 2.1686,     // Barcelona
        -34.60 to -58.38,      // Buenos Aires: hemisferio sur y oeste
        60.17 to 24.94,        // Helsinki: paso de longitud ancho
        0.3 to 32.58,          // Kampala: casi el ecuador
        78.22 to 15.63,        // Svalbard: donde el cuadrado de anillos fallaba mas
        41.0 to 0.0,           // el meridiano cero
        -0.1 to -0.1,          // las dos coordenadas negativas y pequenas
        89.5 to 100.0,         // el clamp polar
    )

    /** Donde se publica dentro del directorio de Hosting. `v1` es el esquema, no la version. */
    private const val PUBLISH_PREFIX = "parkings/v1"

    /** La base entera, descargable de una vez: lo que ODbL pide ofrecer. */
    private const val BULK_NAME = "parkings.ndjson.gz"

    private val NEWLINE = byteArrayOf(0x0A)

    /** Un parking subterráneo de pago · un bordillo · uno privado. Sólo el primero es utilizable. */
    private val PROBE_PAYLOAD = """
        {"elements":[
          {"type":"way","id":1,"center":{"lat":40.4168,"lon":-3.7038},
           "tags":{"amenity":"parking","parking":"underground","fee":"yes","name":"Probe"}},
          {"type":"way","id":2,"center":{"lat":40.4169,"lon":-3.7039},
           "tags":{"amenity":"parking","parking":"street_side"}},
          {"type":"node","id":3,"lat":40.4170,"lon":-3.7040,
           "tags":{"amenity":"parking","access":"private"}}
        ]}
    """.trimIndent()
}
