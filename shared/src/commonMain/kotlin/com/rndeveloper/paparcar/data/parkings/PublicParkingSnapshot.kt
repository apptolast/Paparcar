package com.rndeveloper.paparcar.data.parkings

import com.rndeveloper.paparcar.domain.parkings.FeeInfo
import com.rndeveloper.paparcar.domain.parkings.PublicParking
import com.rndeveloper.paparcar.domain.parkings.PublicParkingKind
import kotlinx.serialization.Serializable
import kotlin.math.roundToLong

/**
 * **El fichero de una celda del snapshot pre-cocinado**, que es el contrato entre el generador y
 * quien lo lea. [OSM-PRECOOKED-SNAPSHOT-001]
 *
 * Vive en `commonMain` y no en el generador a propósito: lo **escribe** el tool
 * (`:shared:parkingSnapshotTool`) y lo **leerá** el backend que fusiona celdas
 * (`PARKINGS-ONE-CALL-MERGED-001`). Dos definiciones del mismo fichero es el día que una se ajusta
 * y la otra no.
 *
 * ⛔ **No es `PublicParking` serializado.** El modelo de dominio es Kotlin puro y no lleva
 * anotaciones de nadie; esto es la capa de datos, y lo que se publica tiene que poder cambiar de
 * forma —añadir `source`, por ejemplo, cuando el backend fusione— sin tocar el dominio.
 */
@Serializable
data class PublicParkingSnapshotCell(
    /** El NOMBRE de la celda: `<band>_<col>` de `ParkingZoneGrid.Zone.id`. */
    val cell: String,
    /** La semana ISO que generó el fichero, repetida aquí para que un fichero suelto se explique. */
    val version: String,
    val parkings: List<PublicParkingSnapshotEntry>,
)

/**
 * Un parking como se publica.
 *
 * ⛔ **Los nulos NO se escriben** (`explicitNulls = false` en el generador), y no es cosmética: el
 * 94 % de los parkings de la región de Madrid no tiene `name`, el 96,8 % no tiene `capacity` y el
 * 99,8 % no tiene `maxheight` [MEDIDO 30-10]. Escribir `"name":null` diez mil veces es pagar bytes
 * por decir que no se sabe nada.
 */
@Serializable
data class PublicParkingSnapshotEntry(
    val id: String,
    val lat: Double,
    val lon: Double,
    /** [PublicParkingKind] por nombre. Texto y no ordinal: un ordinal se rompe al reordenar el enum. */
    val kind: String,
    /** [FeeInfo] por nombre — `UNKNOWN` es una respuesta real y se escribe. */
    val fee: String,
    val name: String? = null,
    val operator: String? = null,
    val capacity: Int? = null,
    val maxHeightM: Double? = null,
    val openingHours: String? = null,
)

/** El manifiesto de un snapshot entero: lo que se publica, contado. */
@Serializable
data class PublicParkingSnapshotManifest(
    val version: String,
    val generatedAtMs: Long,
    val cells: Int,
    val parkings: Int,
    /** `<cellId>` → cuántos parkings lleva. Ordenado para que dos generaciones iguales den el mismo byte. */
    val counts: Map<String, Int>,
    /**
     * ⛔ **El interruptor del carril de la API** [PARKINGS-ONE-CALL-MERGED-001]. Se escribe aquí
     * porque el puntero `latest.json` es lo único de este artefacto que **cambia sin regenerar nada**
     * y que ya va con `must-revalidate`: ponerlo a `false` y desplegar hosting manda a los clientes
     * de vuelta a Overpass sin tocar la app y sin esperar un `fetch` de Remote Config, que la app no
     * tiene. Se escribe siempre `true`; apagarlo es editar el fichero publicado.
     */
    val serve: Boolean = true,
)

/**
 * ⛔ **Las coordenadas se redondean a 7 decimales, y es SIN PÉRDIDA.** OpenStreetMap guarda las
 * suyas con exactamente esa precisión (1e-7 grados ≈ 1,1 cm), así que un decimal más no es dato: es
 * el ruido de haber promediado una caja envolvente en coma flotante. Sin esto, un `Double` como
 * `40.416751234567891` se escribe entero en el fichero y se paga en cada registro.
 */
private const val COORDINATE_SCALE = 10_000_000.0

private fun Double.atOsmPrecision(): Double = (this * COORDINATE_SCALE).roundToLong() / COORDINATE_SCALE

fun PublicParking.toSnapshotEntry(): PublicParkingSnapshotEntry = PublicParkingSnapshotEntry(
    id = id,
    lat = latitude.atOsmPrecision(),
    lon = longitude.atOsmPrecision(),
    kind = kind.name,
    fee = fee.name,
    name = name,
    operator = operator,
    capacity = capacity,
    maxHeightM = maxHeightMeters,
    openingHours = openingHours,
)

/**
 * De vuelta al dominio.
 *
 * ⛔ Un `kind` o un `fee` que no reconocemos cae a `UNKNOWN` en vez de reventar: el fichero lo
 * escribió una versión del generador que puede ser más nueva que esta app, y un parking que no
 * sabemos clasificar sigue siendo un parking que está ahí.
 */
fun PublicParkingSnapshotEntry.toPublicParking(): PublicParking = PublicParking(
    id = id,
    name = name,
    operator = operator,
    kind = PublicParkingKind.entries.firstOrNull { it.name == kind } ?: PublicParkingKind.UNKNOWN,
    latitude = lat,
    longitude = lon,
    capacity = capacity,
    fee = FeeInfo.entries.firstOrNull { it.name == fee } ?: FeeInfo.UNKNOWN,
    maxHeightMeters = maxHeightM,
    openingHours = openingHours,
)
