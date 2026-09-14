package com.rndeveloper.paparcar.presentation.util

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow

/**
 * ¿El rótulo de una zona enseña su NOMBRE, o sólo su glifo?
 *
 * Una zona no es un punto: es un ÁMBITO, y su sujeto en el mapa es el anillo de radio
 * [UI-A-ZONE-IS-AN-AREA-NOT-A-PIN-001]. El nombre es el rótulo de ese anillo, así que sólo tiene
 * sentido mientras el anillo se vea: alejado, el círculo cabe en dos píxeles y el nombre queda
 * flotando sobre nada, tapando las plazas que sí ocupan ese punto. Por debajo del umbral la zona se
 * queda en su glifo, que sigue diciendo "aquí hay una zona tuya" sin ocupar una frase.
 *
 * Vive aquí, fuera del composable y sin Compose, porque es la única parte del rótulo que se puede
 * MEDIR: un umbral mal puesto no se ve en una preview, se ve en un mapa alejándose.
 */

/**
 * Radio del anillo de una zona EN PANTALLA, en dp.
 *
 * Mercator esférico: el mundo mide 256 dp de lado a zoom 0, así que un dp son
 * `156543.034 · cos(latitud) / 2^zoom` metros. En Android el zoom de Google Maps ya viene en esa
 * escala independiente de densidad, así que lo que sale son dp y no píxeles físicos — que es
 * justo lo que hace falta para compararlo con el tamaño de un texto.
 *
 * @param radiusMeters radio de la zona; 0 o negativo ⇒ 0 (una zona sin radio no tiene anillo).
 * @param latitude latitud del centro. Se acota a ±[MERCATOR_MAX_LATITUDE], donde Mercator deja de
 *   estar definido, para que un dato corrupto no devuelva un radio infinito.
 * @param zoom zoom actual de la cámara.
 */
fun zoneRingRadiusDp(radiusMeters: Float, latitude: Double, zoom: Float): Float {
    if (radiusMeters <= 0f) return 0f
    val lat = latitude.coerceIn(-MERCATOR_MAX_LATITUDE, MERCATOR_MAX_LATITUDE)
    val metersPerDp = EQUATOR_METERS_PER_DP_AT_ZOOM_0 * cos(lat * PI / 180.0) / 2.0.pow(zoom.toDouble())
    if (metersPerDp <= 0.0) return 0f
    return (radiusMeters / metersPerDp).toFloat()
}

/**
 * `true` cuando el anillo es lo bastante grande como para que su nombre sea el rótulo DE ALGO.
 *
 * El umbral se mide contra el RADIO, no contra el diámetro, porque el nombre sale del centro hacia
 * los lados: con [ZONE_LABEL_MIN_RING_DP] de radio el rótulo cabe dentro del círculo que titula en
 * lugar de desbordarlo sobre la calle de al lado.
 */
fun zoneLabelShowsName(radiusMeters: Float, latitude: Double, zoom: Float): Boolean =
    zoneRingRadiusDp(radiusMeters, latitude, zoom) >= ZONE_LABEL_MIN_RING_DP

/**
 * Radio mínimo del anillo, en dp, para que la zona escriba su nombre.
 *
 * 48 dp ⇒ 96 dp de diámetro, que es el ancho que ocupa un nombre corto con su glifo. Con el radio
 * típico de una zona (≈80 m) eso cae alrededor del zoom 16: a nivel de calle la zona se llama, a
 * nivel de ciudad se calla. ⚠️ Calibrado en la fórmula, **no todavía en mano sobre el mapa**.
 */
const val ZONE_LABEL_MIN_RING_DP = 48f

/** 2·π·R_tierra / 256 dp — metros por dp en el ecuador a zoom 0. */
private const val EQUATOR_METERS_PER_DP_AT_ZOOM_0 = 156_543.03392
private const val MERCATOR_MAX_LATITUDE = 85.05112878
