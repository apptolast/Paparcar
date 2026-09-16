package com.rndeveloper.paparcar.presentation.util

import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.PlaceInfo
import com.rndeveloper.paparcar.domain.util.haversineMeters
import kotlin.math.roundToInt

/** UI-facing `Float` wrapper over the domain [haversineMeters] great-circle distance. */
fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float =
    haversineMeters(lat1, lon1, lat2, lon2).toFloat()

fun formatDistance(meters: Float, unit: DistanceUnit = DistanceUnit.METRIC): String = when (unit) {
    DistanceUnit.METRIC -> when {
        meters < 1000 -> "${meters.roundToInt()} m"
        else -> {
            val tenths = ((meters / 1000f) * 10 + 0.5f).toLong().coerceAtLeast(0)
            "${tenths / 10}.${tenths % 10} km"
        }
    }
    DistanceUnit.IMPERIAL -> {
        val feet = meters * 3.28084f
        val miles = meters / 1609.344f
        when {
            feet < 1000f -> "${feet.roundToInt()} ft"
            else -> {
                val tenths = ((miles * 10) + 0.5f).toLong().coerceAtLeast(0)
                "${tenths / 10}.${tenths % 10} mi"
            }
        }
    }
}

/**
 * A RADIUS as the copy says it — "3 km", "1.9 mi", "820 ft".
 * [I18N-A-RADIUS-WRITTEN-BY-HAND-IGNORES-MILES-001]
 *
 * Deliberately a thin layer OVER [formatDistance] rather than a second formatter: the conversion,
 * the ft/mi threshold and the unit names stay in exactly one place, which is the whole point of the
 * ticket — four radii had been typed into the copy by hand and one of them ([PublicParkingsQuery]'s)
 * had been saying 1.5 km since the day the radius moved to 3 km.
 *
 * The only thing a radius does NOT inherit is the forced tenth. A measured distance earns its
 * decimal because something was measured; a radius is a round number WE chose, and "No car parks
 * within 3.0 km" reads like a precision nobody asked for. A converted radius keeps its tenth
 * (1.9 mi), because there the decimal is the honest part.
 */
fun formatRadius(meters: Float, unit: DistanceUnit = DistanceUnit.METRIC): String =
    formatDistance(meters, unit).replace(WHOLE_TENTH, " ")

/** The `.0` of a round figure, with the space before the unit kept. */
private val WHOLE_TENTH = Regex("""\.0 """)

/**
 * Una ALTURA — el gálibo de un garaje: «2.10 m», «6 ft 11 in».
 * [I18N-A-RADIUS-WRITTEN-BY-HAND-IGNORES-MILES-001]
 *
 * No pasa por [formatDistance] a propósito, y no es una excepción a la regla de un solo formateador:
 * es que una altura no se dice como una distancia. Bajo el metro, [formatDistance] contaría metros
 * con decimales y en imperial daría pies con décimas («6.9 ft»), y nadie mide un gálibo así — se dice
 * en pies y PULGADAS. La unidad de destino tampoco es la misma: una distancia corta va a pies, y a
 * partir de 1.000 pasa a millas; una altura no pasa a millas nunca.
 */
fun formatHeight(meters: Float, unit: DistanceUnit = DistanceUnit.METRIC): String = when (unit) {
    // Dos decimales: el gálibo se señaliza así en el garaje («2,10 m»), y redondear a «2,1» lo
    // aleja de lo que el usuario va a leer en la barra de la entrada.
    DistanceUnit.METRIC -> {
        val centimetres = (meters * CM_PER_METER).roundToInt()
        "${centimetres / CM_PER_METER}.${(centimetres % CM_PER_METER).toString().padStart(2, '0')} m"
    }
    DistanceUnit.IMPERIAL -> {
        val totalInches = (meters * INCHES_PER_METER).roundToInt()
        val feet = totalInches / INCHES_PER_FOOT
        val inches = totalInches % INCHES_PER_FOOT
        if (inches == 0) "$feet ft" else "$feet ft $inches in"
    }
}

private const val CM_PER_METER = 100
private const val INCHES_PER_METER = 39.3701f
private const val INCHES_PER_FOOT = 12

/**
 * Returns the best human-readable label for a location, combining POI name and
 * address when both are available, or `null` when neither exists — the call
 * site supplies its own context-appropriate fallback (a spot vs your parking).
 * Raw coordinates are never a label: the adjacent map already says WHERE, the
 * text's only job is to name it. [UI-LOCATION-FALLBACK-SPEAKS-HUMAN-001]
 * The POI category is shown via a dedicated icon at the render site, never an emoji.
 */
fun locationDisplayText(
    placeInfo: PlaceInfo?,
    address: AddressInfo?,
): String? {
    val place = placeInfo?.name
    val addr = address?.displayLine
    return when {
        place != null && addr != null -> "$place  ·  $addr"
        place != null -> place
        else -> addr
    }
}
