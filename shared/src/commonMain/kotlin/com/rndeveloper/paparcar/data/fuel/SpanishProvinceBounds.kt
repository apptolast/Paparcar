package com.rndeveloper.paparcar.data.fuel

import kotlin.math.cos

/**
 * Dónde cae un punto, en provincias del ministerio, **sin preguntarle a nadie**.
 * [FUEL-A-POSTCODE-IS-THE-PROVINCE-ID-001]
 *
 * ### Por qué existe
 * El feed español se pide **por id** de provincia o municipio, y esos ids salen hoy de emparejar
 * NOMBRES que da un geocoder ([SpanishMunicipalities], [SpanishProvinces]). Cuando el geocoder no
 * contesta —lo normal en los primeros segundos tras instalar, ver
 * FUEL-AN-UNKNOWN-COUNTRY-IS-NOT-A-COUNTRY-WITHOUT-PRICES-001— no hay ni pueblo ni región, así que
 * no se le puede preguntar NADA al ministerio y el usuario se queda sin precios. Esta tabla es la
 * única clave que no depende de que nadie conteste: las coordenadas ya las tenemos.
 *
 * ### ⛔ Esto NO es geografía administrativa
 * No son los límites de las provincias: son las **cajas envolventes de sus gasolineras**, que es una
 * pregunta distinta y, para lo que hacemos, la correcta — no nos importa dónde acaba la provincia,
 * nos importa dónde hay surtidores. Por eso la tabla **se genera y no se inventa**, y sale de la
 * misma fuente que luego responde.
 *
 * ### Cómo se regenera
 * ```
 * curl -H 'User-Agent: Paparcar/1.0 (+https://paparcar.com)'  *   https://sedeaplicaciones.minetur.gob.es/ServiciosRESTCarburantes/PreciosCarburantes/EstacionesTerrestres/
 * ```
 * agrupar `ListaEESSPrecio` por `IDProvincia` y, por grupo, min/max de `Latitud` y
 * `Longitud (WGS84)` (redondeados hacia fuera) más la media de ambas como centroide. El número entre
 * paréntesis de cada fila es cuántas estaciones la sostienen, que es lo que permite ver de un
 * vistazo si una provincia se ha quedado coja al regenerar.
 *
 * Generada el **14-09-2026** con las **11.511** estaciones del feed de esa fecha.
 *
 * ### Qué tan bien acierta, medido contra su propia fuente
 * Las 11.511 estaciones traen su `IDProvincia`, así que son 11.511 puntos con verdad conocida:
 *
 * | | |
 * |---|---|
 * | provincia correcta | **94,6 %** |
 * | provincia vecina, pero CON gasolineras a ≤5 km del punto | 0,8 % |
 * | provincia vecina SIN nada a ≤5 km | 4,6 % |
 *
 * ⛔ Ese último 4,6 % es la razón de que [MineturStationsSource] trate una respuesta VACÍA como un
 * FALLO cuando la provincia vino de aquí: *una provincia adivinada no puede dictar un veredicto*.
 * Degrada al feed sin precios —lo mismo que hay hoy en ese caso— en vez de afirmar «no hay ninguna
 * gasolinera a 5 km», que sería mentira. Los otros dos casos son mejor que hoy, que es no tener
 * precios nunca.
 *
 * ⛔ Y el empate se rompe por CENTROIDE. Medidas las tres alternativas del mismo coste en datos:
 * centroide 94,6 %, «más dentro de la caja» en relativo 73,3 %, en km 14,2 %. No volver a probarlo.
 */
internal object SpanishProvinceBounds {

    private class P(
        val id: String,
        val latMin: Double,
        val latMax: Double,
        val lonMin: Double,
        val lonMax: Double,
        val centreLat: Double,
        val centreLon: Double,
    )

    /**
     * El id de provincia del ministerio para este punto, o null si no cae en ninguna caja — que es
     * la respuesta normal fuera de España y NO un fallo.
     *
     * Un punto cae de media en **3** cajas (son rectángulos sobre un país que no lo es), así que el
     * desempate es obligatorio: gana el centroide más cercano.
     */
    fun idFor(latitude: Double, longitude: Double): String? = BOXES
        .filter { latitude in it.latMin..it.latMax && longitude in it.lonMin..it.lonMax }
        .minByOrNull { box ->
            val dLat = box.centreLat - latitude
            // Un grado de longitud mide menos que uno de latitud, y aquí la diferencia decide
            // fronteras: sin corregirlo, el desempate se inclina hacia el este-oeste.
            val dLon = (box.centreLon - longitude) * cos(latitude * DEGREES_TO_RADIANS)
            dLat * dLat + dLon * dLon
        }
        ?.id

    private const val DEGREES_TO_RADIANS = 0.017453292519943295

    // ── GENERADO · no editar a mano ──────────────────────────────────────────
    // id, latMin, latMax, lonMin, lonMax, centroideLat, centroideLon   // provincia (nº estaciones)
    private val BOXES = listOf(
        P("01", 42.4906, 43.1487, -3.1226, -2.312, 42.852, -2.6952), // Araba/Álava (75)
        P("02", 38.1511, 39.3674, -2.7515, -0.9429, 38.944, -1.8673), // Albacete (154)
        P("03", 37.8598, 38.8585, -1.0469, 0.1864, 38.3827, -0.5292), // Alicante (484)
        P("04", 36.7096, 37.8597, -3.004, -1.6642, 37.0024, -2.3984), // Almería (226)
        P("05", 40.1395, 41.1324, -5.5166, -4.333, 40.6349, -4.7967), // Ávila (72)
        P("06", 38.0286, 39.3637, -7.1681, -5.047, 38.7399, -6.341), // Badajoz (280)
        P("07", 38.7084, 40.0079, 1.2978, 4.2717, 39.5612, 2.8382), // Balears (Illes) (223)
        P("08", -0.0001, 42.2452, -0.0001, 2.7555, 41.4798, 2.0877), // Barcelona (801)
        P("09", 41.5736, 43.167, -4.2452, -2.6961, 42.3147, -3.5991), // Burgos (139)
        P("10", 39.1327, 40.3849, -7.2983, -5.1984, 39.7384, -6.0981), // Cáceres (159)
        P("11", 36.0187, 36.9342, -6.4328, -5.1638, 36.4964, -5.9552), // Cádiz (291)
        P("12", 39.7403, 40.6636, -0.7036, 0.4752, 40.0524, -0.0564), // Castellón / Castelló (198)
        P("13", 38.4311, 39.4604, -4.9729, -2.6916, 38.9779, -3.5523), // Ciudad Real (218)
        P("14", 37.2269, 38.5729, -5.4434, -4.1003, 37.7688, -4.7261), // Córdoba (210)
        P("15", 42.5542, 43.7319, -9.2652, -7.8451, 43.1577, -8.4706), // Coruña (A) (289)
        P("16", 39.3254, 40.4508, -3.0831, -1.2728, 39.7376, -2.336), // Cuenca (120)
        P("17", 41.6757, 42.4629, 1.9219, 3.2762, 42.0685, 2.8399), // Girona (275)
        P("18", 36.699, 37.9833, -4.2848, -2.3333, 37.1915, -3.5017), // Granada (286)
        P("19", 40.298, 41.2144, -3.4548, -1.6833, 40.7019, -3.0035), // Guadalajara (93)
        P("20", 42.9493, 43.3565, -2.5353, -1.7526, 43.2252, -2.0915), // Gipuzkoa (147)
        P("21", 37.02, 38.1382, -7.4099, -6.2208, 37.3968, -6.8809), // Huelva (142)
        P("22", 41.4134, 42.774, -0.7866, 0.7319, 42.043, -0.0337), // Huesca (124)
        P("23", 37.4116, 38.4147, -4.2613, -2.557, 37.9671, -3.6047), // Jaén (214)
        P("24", 42.0764, 43.0145, -6.9217, -4.9786, 42.5487, -5.8815), // León (167)
        P("25", 41.365, 42.8322, 0.393, 1.8456, 41.8003, 0.8958), // Lleida (186)
        P("26", 41.9827, 42.5753, -3.0133, -1.7259, 42.3797, -2.3675), // Rioja (La) (83)
        P("27", 42.4742, 43.6875, -7.93, -7.0176, 43.0833, -7.5016), // Lugo (130)
        P("28", -0.0001, 41.135, -4.4762, 0.0001, 40.3706, -3.6923), // Madrid (895)
        P("29", 36.3326, 37.268, -5.3364, -3.8761, 36.7062, -4.617), // Málaga (310)
        P("30", 37.3888, 38.6202, -1.9733, -0.7079, 37.8941, -1.2324), // Murcia (465)
        P("31", 41.9265, 43.3094, -2.3741, -0.9393, 42.6199, -1.716), // Navarra (254)
        P("32", 41.8252, 42.5131, -8.2461, -6.9788, 42.243, -7.7352), // Ourense (88)
        P("33", 43.0381, 43.6082, -7.1486, -4.5574, 43.4299, -5.8745), // Asturias (241)
        P("34", 41.7863, 42.8947, -4.9664, -4.0759, 42.2269, -4.48), // Palencia (74)
        P("35", 27.7533, 29.1489, -15.7958, -13.4648, 28.2252, -14.9806), // Palmas (Las) (254)
        P("36", -8.6595, 42.7934, -8.8836, 42.0375, 42.135, -8.3913), // Pontevedra (227)
        P("37", 40.3408, 41.2363, -6.8248, -5.2007, 40.8598, -5.8666), // Salamanca (112)
        P("38", 27.7053, 28.8316, -18.012, -16.2086, 28.3394, -16.6508), // Santa Cruz De Tenerife (243)
        P("39", -0.0001, 43.4854, -4.6062, 0.0001, 43.1071, -3.8451), // Cantabria (168)
        P("40", 40.7134, 41.4399, -4.6994, -3.3838, 41.0867, -4.1216), // Segovia (77)
        P("41", 36.8531, 38.0904, -6.3238, -4.6775, 37.3597, -5.7939), // Sevilla (443)
        P("42", 41.1689, 42.0278, -3.1926, -1.9264, 41.6301, -2.5261), // Soria (43)
        P("43", 40.5332, 41.5387, 0.2843, 1.6245, 41.0556, 1.0174), // Tarragona (235)
        P("44", 40.0634, 41.2199, -1.652, 0.2148, 40.6845, -0.7536), // Teruel (70)
        P("45", 39.3543, 40.2447, -5.3328, -2.9898, 39.8992, -4.0428), // Toledo (245)
        P("46", 38.736, 40.1271, -1.4372, -0.0726, 39.358, -0.4769), // Valencia / València (649)
        P("47", 41.1804, 42.1612, -5.3308, -4.1139, 41.5857, -4.7881), // Valladolid (151)
        P("48", 42.9866, 43.4199, -3.3557, -2.4957, 43.267, -2.8791), // Bizkaia (131)
        P("49", 41.1713, 42.0893, -6.8385, -5.3178, 41.7211, -5.7787), // Zamora (88)
        P("50", 41.1171, 42.491, -2.1229, 0.3325, 41.6315, -1.0288), // Zaragoza (240)
        P("51", 35.8794, 35.8948, -5.3368, -5.3008, 35.8907, -5.3217), // Ceuta (10)
        P("52", 35.2695, 35.297, -2.9484, -2.9346, 35.284, -2.9421), // Melilla (12)
    )
}
