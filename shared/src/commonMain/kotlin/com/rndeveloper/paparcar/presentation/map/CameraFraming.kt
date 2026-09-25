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
 * El zoom que pide un destino mientras la cámara está ENGANCHADA al puck del viaje, o `null` si no
 * hay nada que hacer. [HOME-A-FOLLOWED-TRIP-MUST-STILL-OBEY-ITS-ZOOM-001]
 *
 * ## El problema que resuelve
 *
 * Seguir al conductor y encuadrarlo son dos cosas, y el mapa sólo hacía la primera. Con el
 * seguimiento enganchado, la cámara se pega a la posición del puck y **descarta el destino entero**
 * —posición y zoom— porque un destino tirando de la posición pelearía con el enganche. Correcto
 * para la posición; para el zoom, no: el zoom no lo manda el enganche, no lo manda nadie, y se
 * queda en el que tuviera el mapa. Abrir la app en mitad de un viaje la abre en el zoom de
 * arranque, que sin fix previo es el de «no sé dónde estás» — el mundo entero. La cámara SÍ iba al
 * coche; lo que no hacía era acercarse, y es lo que reabrió la issue #66 después de darla por
 * arreglada en el host: allí se pedía el zoom correcto y aquí se tiraba a la basura.
 *
 * ## La regla
 *
 * *El enganche manda DÓNDE; el zoom sigue siendo del host.* Y sigue siendo un suelo
 * ([ZoomIntent.AtLeast]): a quien ya mira de cerca no se le aleja — por eso devuelve `null` cuando
 * [settleZoom] no mueve nada, y así un viaje que se sigue durante media hora no vuelve a tocar el
 * zoom que el usuario eligió con los dedos.
 */
fun followLockZoom(current: Float, target: CameraTarget?, following: Boolean): Float? {
    if (!following) return null
    val wanted = target?.zoom ?: return null
    return settleZoom(current, wanted, target.zoomIntent).takeIf { it != current }
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
