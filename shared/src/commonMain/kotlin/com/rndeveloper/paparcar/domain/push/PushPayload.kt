package com.rndeveloper.paparcar.domain.push

/**
 * Lo que un mensaje de push DICE, leído una sola vez para las dos plataformas.
 * [IOS-THE-GROUP-CANNOT-REACH-AN-IPHONE-001]
 *
 * ⛔ **Espejo de `functions/src/index.ts` y `functions/src/sharedCarFanout.ts`.** El contrato de
 * cable son cadenas sueltas en un mapa, y hasta ahora se desenrollaba dentro de un
 * `FirebaseMessagingService` de Android: un sitio donde ningún test puede entrar y que iOS, por
 * construcción, no podía compartir. Aquí es una función pura, así que el mismo veredicto vale para
 * los dos móviles y se puede comprobar sin ninguno.
 *
 * ⛔ Los mensajes son **de datos** a propósito: el texto lo pone el cliente, que es el único lado
 * con los nueve idiomas, y el dedup de lo ya anunciado vive en `zone_alerted_spots`. Un mensaje de
 * notificación lo pintaría el sistema en el idioma que adivinara el servidor y sin preguntar a
 * nadie qué se había dicho ya.
 */
sealed interface PushPayload {

    /**
     * Ha quedado libre una plaza dentro de una zona vigilada.
     * [ZONE-A-SPOT-TELLS-THE-SERVER-AND-THE-SERVER-TELLS-YOU-001]
     */
    data class ZoneSpot(
        val spotId: String,
        val zoneId: String,
        val zoneName: String,
        val latitude: Double,
        val longitude: Double,
        val isProvisional: Boolean,
        /** Cuándo muere la plaza. Es por lo que poda el dedup: lo que impide repetir es que el hueco
         *  siga existiendo, no la edad del aviso. */
        val expiresAt: Long,
    ) : PushPayload

    /**
     * Noticias del coche que conducís varios. **Siempre mueven la valla**, tengan o no con qué
     * pintar una tarjeta. [DET-A-SHARED-FENCE-FOLLOWS-THE-CAR-001]
     */
    sealed interface SharedCarNews : PushPayload

    /**
     * Alguien del grupo lo ha aparcado. [SHARED-A-PARKING-TELLS-THE-GROUP-001]
     *
     * ⛔ Casi todo es nullable y no por comodidad: **un campo que falte puede costar la tarjeta,
     * nunca la vigilancia**. Sin [latitude]/[longitude] no hay mapa al que mandar a nadie y la
     * tarjeta se calla; sin [carId]/[sessionId]/[parkedAtMs] no se puede comprobar si el viaje era
     * el mío y la tarjeta se muestra —el fallo cae del lado de contarlo—. La valla se mueve en los
     * dos casos.
     */
    data class SharedCarParked(
        val carId: String?,
        val sessionId: String?,
        val parkedAtMs: Long?,
        val authorName: String?,
        val addressLine: String?,
        val latitude: Double?,
        val longitude: Double?,
    ) : SharedCarNews

    /** Se lo han llevado. [SHARED-A-CAR-THAT-LEAVES-IS-NEWS-TOO-001] */
    data class SharedCarLeft(val authorName: String?) : SharedCarNews

    /** No hay nada que hacer con esto, y [reason] dice qué se miró. */
    data class Ignored(val reason: String) : PushPayload
}

/** ⛔ Espejados de `functions/`. Cambiar uno aquí sin cambiarlo allí deja el aviso mudo. */
internal const val PUSH_TYPE_ZONE_SPOT = "zone_spot"
internal const val PUSH_TYPE_SHARED_CAR_PARKED = "shared_car_parked"
internal const val PUSH_TYPE_SHARED_CAR_LEFT = "shared_car_left"

/**
 * Qué trae este mensaje, sin tocar nada ni preguntar a nadie.
 *
 * [nowMs] entra porque **una plaza que ya ha muerto en el camino no es noticia**: un aviso sobre un
 * hueco que ya no existe es peor que no avisar, así que la caducidad se mira antes que nada y el
 * mensaje se descarta aquí, no tres capas más abajo.
 */
fun pushPayloadFor(data: Map<String, String>, nowMs: Long): PushPayload = when (data[KEY_TYPE]) {
    PUSH_TYPE_SHARED_CAR_PARKED -> PushPayload.SharedCarParked(
        carId = data[KEY_CAR_ID],
        sessionId = data[KEY_SESSION_ID],
        parkedAtMs = data[KEY_PARKED_AT_MS]?.toLongOrNull(),
        authorName = data[KEY_AUTHOR_NAME]?.takeIf { it.isNotBlank() },
        addressLine = data[KEY_ADDRESS_LINE]?.takeIf { it.isNotBlank() },
        latitude = data[KEY_LATITUDE]?.toDoubleOrNull(),
        longitude = data[KEY_LONGITUDE]?.toDoubleOrNull(),
    )

    PUSH_TYPE_SHARED_CAR_LEFT -> PushPayload.SharedCarLeft(
        authorName = data[KEY_AUTHOR_NAME]?.takeIf { it.isNotBlank() },
    )

    PUSH_TYPE_ZONE_SPOT -> zoneSpotPayload(data, nowMs)

    else -> PushPayload.Ignored("unknown type=${data[KEY_TYPE]}")
}

private fun zoneSpotPayload(data: Map<String, String>, nowMs: Long): PushPayload {
    val spotId = data[KEY_SPOT_ID] ?: return PushPayload.Ignored("zone spot without spotId")
    val zoneId = data[KEY_ZONE_ID] ?: return PushPayload.Ignored("zone spot without zoneId")
    val latitude = data[KEY_LATITUDE]?.toDoubleOrNull()
        ?: return PushPayload.Ignored("zone spot without latitude")
    val longitude = data[KEY_LONGITUDE]?.toDoubleOrNull()
        ?: return PushPayload.Ignored("zone spot without longitude")
    val expiresAt = data[KEY_EXPIRES_AT]?.toLongOrNull() ?: 0L
    if (expiresAt != 0L && expiresAt <= nowMs) {
        return PushPayload.Ignored("spot $spotId expired before delivery")
    }
    return PushPayload.ZoneSpot(
        spotId = spotId,
        zoneId = zoneId,
        zoneName = data[KEY_ZONE_NAME].orEmpty(),
        latitude = latitude,
        longitude = longitude,
        isProvisional = data[KEY_PROVISIONAL]?.toBooleanStrictOrNull() == true,
        expiresAt = expiresAt,
    )
}

private const val KEY_TYPE = "type"
private const val KEY_SPOT_ID = "spotId"
private const val KEY_ZONE_ID = "zoneId"
private const val KEY_ZONE_NAME = "zoneName"
private const val KEY_LATITUDE = "latitude"
private const val KEY_LONGITUDE = "longitude"
private const val KEY_PROVISIONAL = "provisional"
private const val KEY_EXPIRES_AT = "expiresAt"
private const val KEY_CAR_ID = "carId"
private const val KEY_SESSION_ID = "sessionId"
private const val KEY_PARKED_AT_MS = "parkedAtMs"
private const val KEY_AUTHOR_NAME = "authorName"
private const val KEY_ADDRESS_LINE = "addressLine"
