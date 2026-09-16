package com.rndeveloper.paparcar.presentation.util

import com.rndeveloper.paparcar.GallonKind

/**
 * El consumo, tal y como se ENSEÑA y como se TECLEA.
 * [I18N-A-RADIUS-WRITTEN-BY-HAND-IGNORES-MILES-001]
 *
 * ⛔ **El dominio no se entera de nada de esto.** `Vehicle.consumptionPer100` sigue siendo l/100 km y
 * `consumptionElectricPer100` sigue siendo kWh/100 km, que es lo que consumen `FuelDefaults`, el
 * cálculo de litros y todo el pipeline de estadísticas. Aquí sólo se traduce en el borde: al pintar
 * y al leer lo que el usuario escribió. Meter mpg en el modelo habría obligado a cada consumidor a
 * preguntarse en qué unidad le llega el número, que es exactamente el fallo que este ticket cierra.
 *
 * Por qué no basta con cambiar la etiqueta —que era la trampa del ticket original—: **el consumo no
 * es una distancia, es una magnitud POR distancia**, y su forma imperial va al revés. l/100 km mide
 * gasto (más es peor) y mpg mide rendimiento (más es mejor), así que no hay factor que multiplicar:
 * hay que INVERTIR. Por eso esto vive en su propio fichero y no como una unidad más de
 * [formatDistance].
 */

/** l/100 km → mpg. La inversión, con el galón que toque. */
fun litresPer100KmToMpg(litresPer100Km: Float, gallon: GallonKind): Float =
    if (litresPer100Km <= 0f) 0f else mpgNumerator(gallon) / litresPer100Km

/** mpg → l/100 km. La misma hipérbola en el otro sentido: aplicarla dos veces devuelve el original. */
fun mpgToLitresPer100Km(mpg: Float, gallon: GallonKind): Float =
    if (mpg <= 0f) 0f else mpgNumerator(gallon) / mpg

/** kWh/100 km → kWh/100 mi. Aquí sí es un factor: el kWh no cambia, la distancia sí. */
fun kwhPer100KmToPer100Miles(kwhPer100Km: Float): Float = kwhPer100Km * METERS_PER_MILE / METERS_PER_KM

/** kWh/100 mi → kWh/100 km. */
fun kwhPer100MilesToPer100Km(kwhPer100Miles: Float): Float = kwhPer100Miles * METERS_PER_KM / METERS_PER_MILE

/**
 * Un consumo del dominio, en la unidad que este usuario lee.
 *
 * [usesLiters] distingue las dos magnitudes que comparten este camino: la que quema combustible
 * (l/100 km ⇄ mpg, invertida) y la eléctrica (kWh/100 km ⇄ kWh/100 mi, escalada).
 */
fun consumptionToDisplay(
    per100Km: Float,
    usesLiters: Boolean,
    unit: DistanceUnit,
    gallon: GallonKind,
): Float = when {
    unit == DistanceUnit.METRIC -> per100Km
    usesLiters -> litresPer100KmToMpg(per100Km, gallon)
    else -> kwhPer100KmToPer100Miles(per100Km)
}

/**
 * Lo que el usuario tecleó, de vuelta a la unidad del dominio.
 *
 * ⛔ Inversa EXACTA de [consumptionToDisplay]. Que lo sea no es un detalle de estilo: el formulario
 * de alta enseña el valor guardado y vuelve a guardar lo que quede en el campo, así que abrir la
 * ficha de un coche y darle a guardar sin tocar nada tiene que dejar el mismo número. Si estas dos
 * funciones divergen, editar el nombre de un coche le cambia el consumo.
 */
fun consumptionFromDisplay(
    shown: Float,
    usesLiters: Boolean,
    unit: DistanceUnit,
    gallon: GallonKind,
): Float = when {
    unit == DistanceUnit.METRIC -> shown
    usesLiters -> mpgToLitresPer100Km(shown, gallon)
    else -> kwhPer100MilesToPer100Km(shown)
}

/**
 * Litros por galón, ya metidos en la constante que invierte l/100 km.
 *
 * `100 km / (l/100km) / litros-por-galón` en millas = `(100 / 1.609344) / l100 * litrosPorGalón`.
 * Los dos números de abajo son ese despeje ya hecho, y son los valores que publica cualquier tabla
 * de conversión: 235,215 para el galón US (3,785411784 l) y 282,481 para el imperial (4,54609 l).
 */
private fun mpgNumerator(gallon: GallonKind): Float = when (gallon) {
    GallonKind.US -> MPG_US_NUMERATOR
    GallonKind.IMPERIAL -> MPG_IMPERIAL_NUMERATOR
}

private const val MPG_US_NUMERATOR = 235.2145f
private const val MPG_IMPERIAL_NUMERATOR = 282.4809f
private const val METERS_PER_MILE = 1609.344f
private const val METERS_PER_KM = 1000f
