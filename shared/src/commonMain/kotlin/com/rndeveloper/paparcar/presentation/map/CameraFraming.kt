package com.rndeveloper.paparcar.presentation.map

import kotlin.math.max

/**
 * What zoom a camera move should LAND on.
 * [MAP-A-CAMERA-MOVE-MUST-NOT-COST-A-PINCH-001]
 *
 * ## El problema que resuelve
 *
 * Cada animación de cámara llevaba su nivel escrito a pelo — navegar 15, editar zona 14, coche
 * aparcado 17 — y lo aplicaba **en absoluto**. Así que mirar de cerca y tocar una zona te alejaba
 * a 15, y añadir una zona a 14, vinieras de donde vinieras: el usuario tenía que volver a acercar
 * a mano justo después de pedir ir a un sitio. Reportado en la issue #31.
 *
 * ## La regla
 *
 * Mover la cámara cambia **dónde** miras. No debería cambiar **cuánto** te acercas, salvo que el
 * destino lo exija:
 *
 * | intención | modo | qué hace |
 * |---|---|---|
 * | ir a un sitio (zona, resultado de búsqueda, coche) | [ZoomIntent.AtLeast] | acerca si estabas lejos; **nunca aleja** |
 * | encuadrar un área (editar zona, dos puntos) | [ZoneIntent.Fit] | manda el encuadre, aunque toque alejar |
 *
 * El nivel escrito deja de ser un destino y pasa a ser un **suelo**: «al menos así de cerca».
 */
enum class ZoomIntent {
    /** Un suelo: acerca si hace falta, jamás aleja lo que el usuario había elegido. */
    AtLeast,

    /** Un encuadre: la cosa tiene tamaño y hay que verla entera, aunque implique alejar. */
    Fit,
}

/** Alias legible para el `when` de arriba; el KDoc de la tabla lo nombra así. */
private typealias ZoneIntent = ZoomIntent

/**
 * El zoom al que animar, dados el actual y el que pide la animación.
 *
 * [current] nulo (la cámara aún no ha posado) ⇒ manda [wanted]: no hay nada que preservar.
 */
fun settleZoom(current: Float?, wanted: Float, intent: ZoomIntent): Float = when {
    current == null -> wanted
    intent == ZoomIntent.Fit -> wanted
    else -> max(current, wanted)
}

/**
 * El zoom que enseña una zona ENTERA, por su radio.
 *
 * Antes se editaba siempre a 14 —unos 4 km de ancho— para «ver el círculo completo». Para un garaje
 * de 40 m eso es mandar al usuario a la otra punta del barrio: el círculo se ve entero, sí, del
 * tamaño de una moneda. La escalera es la misma familia que la de `BOUNDS_DELTA_*` del mapa, leída
 * por radio en vez de por caja.
 */
fun zoomForZoneRadius(radiusMeters: Float): Float = when {
    radiusMeters <= RADIUS_BLOCK   -> ZOOM_STREET_LEVEL
    radiusMeters <= RADIUS_SMALL   -> ZOOM_CLOSE_LEVEL
    radiusMeters <= RADIUS_MEDIUM  -> ZOOM_DEFAULT_LEVEL
    radiusMeters <= RADIUS_LARGE   -> ZOOM_NEIGHBORHOOD_LEVEL
    radiusMeters <= RADIUS_HUGE    -> ZOOM_DISTRICT_LEVEL
    else                           -> ZOOM_WIDE_LEVEL
}

// Radios en metros — los tramos que separan «una manzana» de «un barrio».
private const val RADIUS_BLOCK  = 80f
private const val RADIUS_SMALL  = 200f
private const val RADIUS_MEDIUM = 400f
private const val RADIUS_LARGE  = 800f
private const val RADIUS_HUGE   = 1_600f

// Los mismos peldaños que usa el mapa para encuadrar por caja, nombrados aquí porque los de allí
// son privados de `PaparcarMapView` y esta función vive donde se puede testear.
private const val ZOOM_STREET_LEVEL       = 17f
private const val ZOOM_CLOSE_LEVEL        = 16f
private const val ZOOM_DEFAULT_LEVEL      = 15f
private const val ZOOM_NEIGHBORHOOD_LEVEL = 14f
private const val ZOOM_DISTRICT_LEVEL     = 13f
private const val ZOOM_WIDE_LEVEL         = 12f
