package com.rndeveloper.paparcar.data.parkings

import com.rndeveloper.paparcar.domain.parkings.FeeInfo
import com.rndeveloper.paparcar.domain.parkings.PublicParking
import com.rndeveloper.paparcar.domain.parkings.PublicParkingKind
import kotlinx.serialization.Serializable

/**
 * Lo que contesta `/api/parkings?cell=…`. [PARKINGS-ONE-CALL-MERGED-001]
 *
 * **Una sola llamada, ya fusionada en el backend** — la decisión del user en §E.3 del expediente:
 * la trampa de la fusión por proximidad se implementa UNA vez y no una por plataforma (decisivo
 * para un KMP que va a iOS), y la lógica de licencias vive en un sitio.
 *
 * ⛔ **El contrato llega completo aunque hoy sólo venga la base.** `availability` y `offer` los
 * rellenarán `PARKINGS-LIVE-OCCUPANCY-001` y `PARCLICK-OFFER-BEHIND-A-FLAG-001`, y el único motivo
 * de declararlos ya es que **este fichero no se vuelva a tocar** cuando lleguen: los campos
 * aparecerán en una respuesta que la app ya sabe leer. Espejo de `functions/src/parkingsCell.ts`.
 */
@Serializable
data class ParkingsApiResponse(
    val cell: String = "",
    val baseVersion: String = "",
    val generatedAt: Long = 0,
    /**
     * ⛔ **El interruptor del carril, y por qué está en la respuesta.** La app no tiene Remote
     * Config, y meterlo entero para un `if` es un SDK, su ciclo de fetch, su registro en Koin y su
     * fake del flavor mock. Así que la bandera viaja en el puntero del snapshot y llega aquí: en
     * `false`, el cliente se va a Overpass **para esta pregunta**, sin release y con efecto en lo
     * que tarde el CDN (acotado a una hora a propósito, ver `CACHE_STATIC_SECONDS`).
     *
     * Por defecto `true`: un backend viejo que no la mande **no apaga el carril**.
     */
    val serve: Boolean = true,
    val licences: List<ParkingsApiLicence> = emptyList(),
    val parkings: List<ParkingsApiEntry> = emptyList(),
)

/** Quién dice qué y bajo qué licencia. Hoy sólo OpenStreetMap (ODbL 1.0). */
@Serializable
data class ParkingsApiLicence(
    val id: String = "",
    val source: String = "",
    val attribution: String = "",
)

@Serializable
data class ParkingsApiEntry(
    val id: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val kind: String = "",
    val fee: String = "",
    val name: String? = null,
    val operator: String? = null,
    val capacity: Int? = null,
    val maxHeightM: Double? = null,
    val openingHours: String? = null,
    /**
     * ⛔ **Sólo viene cuando ha HABIDO una fusión**, y su presencia es el testigo de que dos
     * catálogos se emparejaron. Con una sola fuente sería siempre `[su propio id]`: ~25 B por
     * registro, casi el doble de payload para no decir nada.
     */
    val mergedFrom: List<String>? = null,
    val availability: ParkingsApiAvailability? = null,
    val offer: ParkingsApiOffer? = null,
)

/** Plazas libres AHORA. Llega con el ticket 3; hoy siempre null. */
@Serializable
data class ParkingsApiAvailability(
    val free: Int = 0,
    val total: Int? = null,
    val updatedAt: Long = 0,
    /** ⛔ Procedencia, NO confianza: `madrid`, `malaga`… El tipo de fuente va aparte. */
    val source: String = "",
    val sourceKind: String = "",
    val licence: String = "",
)

/** Reservable con precio de referencia. Llega con el ticket 5; hoy siempre null. */
@Serializable
data class ParkingsApiOffer(
    val fromMinor: Int = 0,
    val currency: String = "",
    val windowMinutes: Int = 0,
    val provider: String = "",
    val firm: Boolean = false,
    val licence: String = "",
)

/**
 * Al dominio.
 *
 * ⛔ Un `kind` o un `fee` que no reconocemos cae a `UNKNOWN` en vez de reventar: el backend puede
 * ser más nuevo que esta app, y un parking que no sabemos clasificar **sigue estando ahí**. Es la
 * misma regla que [PublicParkingSnapshotEntry.toPublicParking].
 */
fun ParkingsApiEntry.toPublicParking(): PublicParking = PublicParking(
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
