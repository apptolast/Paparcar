package com.rndeveloper.paparcar.i18n

import org.junit.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * Ninguna DISTANCIA se escribe dentro del copy.
 * [I18N-A-RADIUS-WRITTEN-BY-HAND-IGNORES-MILES-001]
 *
 * Toda distancia que la app enseña pasa por `formatDistance`/`formatRadius`, que respetan el ajuste
 * `settings_distance_unit`. Lo que este guard vigila son las que se saltaban ese camino porque la
 * cifra —o su unidad— estaba tecleada en `strings.xml`. Dos fallos a la vez, y el segundo es mudo:
 *
 *  - **Miente en millas.** «No fuel stations within 5 km» en una app que da todo lo demás en millas.
 *  - **Se desfasa en silencio.** `home_parkings_empty` decía «1,5 km» en los NUEVE locales mientras
 *    `PublicParkingsQuery.DEFAULT_RADIUS_METERS` ya valía 3.000: el radio se movió en `e8c281ba` y la
 *    frase se quedó donde estaba. Nada falla, nada avisa — sólo hay que leerlo para enterarse.
 *
 * Una cifra que viaja como `%1$s` ya formateado no puede hacer ninguna de las dos cosas.
 *
 * ⛔ Este guard NO mira `app/src/main/res`: las notificaciones se formatean en Kotlin antes de llegar
 * al recurso, así que allí no hay ninguna unidad que interceptar.
 */
class DistanceUnitCopyGuardrailTest {

    @Test
    fun should_writeNoDistanceUnitInCopy_when_everyComposeResourcesLocaleIsRead() {
        val hits = localeFiles().flatMap { (locale, file) ->
            STRING_TAG.findAll(file.readText())
                .filter { it.groupValues[KEY] !in ALLOWED }
                .filter { DISTANCE_WITH_UNIT.containsMatchIn(it.groupValues[VALUE]) }
                .map { "  - $locale  ${it.groupValues[KEY]}: ${it.groupValues[VALUE].take(90)}" }
                .toList()
        }
        assertTrue(
            hits.isEmpty(),
            "[distancia tecleada en composeResources] Una distancia se formatea con " +
                "formatDistance/formatRadius y llega como %1\$s — escrita a mano dice «km» a quien " +
                "eligió millas, y se queda atrás cuando la constante que la acota cambia.\n" +
                hits.joinToString("\n"),
        )
    }

    /**
     * Testigo de población: un guard que lee el path equivocado devuelve cero hallazgos y verde.
     * [TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001]
     */
    @Test
    fun should_haveActuallyReadNineLocales_when_theGuardReportsClean() {
        assertTrue(
            localeFiles().size == LOCALES.size,
            "el guard sólo encontró ${localeFiles().size} de ${LOCALES.size} locales — mira otro sitio",
        )
        localeFiles().forEach { (locale, file) ->
            val strings = STRING_TAG.findAll(file.readText()).count()
            assertTrue(strings > MIN_STRINGS, "$locale declara $strings cadenas — el guard lee un fichero vacío")
        }
    }

    /**
     * Testigo del PATRÓN, que es distinto del de población: el de arriba prueba que se leyó algo, éste
     * prueba que lo que se busca se sabe encontrar. Sin él, un regex que dejara de casar convertiría
     * este guard en un test que siempre pasa — que es exactamente la forma que tenía el agujero antes
     * de existir el guard. Las cuatro frases son las que el ticket barrió, tal y como estaban.
     */
    @Test
    fun should_catchTheFourSentencesTheTicketSwept_when_thePatternIsAskedDirectly() {
        listOf(
            "No fuel stations within 5 km.",
            "No public car parks within 1.5 km.",
            "Car parks are only shown within %1\$d km of you.",
            "Somewhere within %1\$d m — we lost the end of the trip.",
        ).forEach { swept ->
            assertTrue(
                DISTANCE_WITH_UNIT.containsMatchIn(swept),
                "el patrón ya no caza «$swept» — el guard está mirando sin ver",
            )
        }
        assertTrue(
            !DISTANCE_WITH_UNIT.containsMatchIn("No public car parks within %1\$s."),
            "el patrón caza la forma CORRECTA (%1\$s) — así no hay manera de arreglarlo",
        )
        // Y la prosa que deletrea la unidad se queda fuera a propósito: no es una cifra formateada
        // con su símbolo, es una frase, y se traduce como tal. Fijado aquí porque el guard SÍ la
        // cazaba mientras el cierre fue `\b`.
        assertTrue(
            !DISTANCE_WITH_UNIT.containsMatchIn("La précision du GPS varie entre 5 et 30 mètres."),
            "el patrón caza prosa con la unidad escrita en letra — volvió el \\b de Java",
        )
        // Y el NOMBRE de una unidad por-distancia tampoco es una medida: no hay cifra que desfasar,
        // y cuál de las cuatro se usa ya lo decide el ajuste.
        listOf("l/100 km", "kWh/100 mi", "Consumption (l/100 km)").forEach { unitName ->
            assertTrue(
                !DISTANCE_WITH_UNIT.containsMatchIn(unitName),
                "el patrón caza «$unitName», que es el nombre de una unidad, no una distancia escrita",
            )
        }
    }

    private fun localeFiles(): List<Pair<String, File>> = LOCALES.mapNotNull { locale ->
        File(COMPOSE_RESOURCES, "$locale/strings.xml").takeIf { it.isFile }?.let { locale to it }
    }

    private companion object {
        val LOCALES = listOf(
            "values", "values-es", "values-it", "values-pt", "values-fr",
            "values-de", "values-nl", "values-pl", "values-ro",
        )

        /**
         * Una cifra —literal o parámetro— con una unidad de distancia ABREVIADA pegada detrás.
         *
         * Dos trampas, las dos medidas contra la suite y las dos cazadas por el testigo del patrón:
         *  - El `$` va como `\${'$'}`. Dentro de un raw string Kotlin no admite `\$`, y sin la barra
         *    el `$` del patrón es el metacarácter de fin de línea: `%1${'$'}d` como regex significa
         *    «%1, fin de línea, d», que no casa nunca. El guard compilaba y no veía nada.
         *  - El cierre es `(?![\p{L}\d])`, no `\b`. El `\b` de Java usa `\w` ASCII, así que «30
         *    mètres» tenía frontera entre la `m` y la `è` y entraba como si fuera «30 m». Y entrar no
         *    era lo correcto: la regla es sobre la unidad abreviada junto a una cifra, no sobre la
         *    prosa que escribe «metros» con todas sus letras (`gps_disclaimer_body`), que se traduce
         *    como cualquier otra frase.
         *
         * Y la apertura, `(?<![/\d.,])`, separa una MEDIDA del NOMBRE DE UNA UNIDAD. «l/100 km» no
         * es una distancia tecleada: es cómo se llama la unidad del consumo, sin cifra delante que
         * pueda desfasarse, y cuál de las cuatro se usa lo decide `consumptionUnitLabel` según el
         * ajuste. La barra es lo que las distingue, así que se pregunta por la barra en vez de
         * allowlistear cinco keys — una allowlist habría tapado también la medida de verdad que se
         * colara en una de ellas.
         */
        val DISTANCE_WITH_UNIT =
            Regex("""(?<![/\d.,])(?:\d+(?:[.,]\d+)?|%1\${'$'}d)\s*(?:km|mi|ft|m)(?![\p{L}\d])""")

        val STRING_TAG = Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        const val KEY = 1
        const val VALUE = 2
        const val MIN_STRINGS = 100

        /**
         * Lo que legítimamente lleva una unidad escrita, y por qué. Se añade con su razón o no se
         * añade: una allowlist sin motivos es la forma que tiene un guard de morirse en silencio.
         *
         * Quedan DOS, y no son una excepción a la regla — es que no son distancias: `%1$dm` ahí son
         * MINUTOS, y coinciden con los metros en la letra y en nada más.
         *
         * ⛔ Aquí estuvieron, y ya no están, el gálibo y las cuatro keys de consumo. El primero
         * porque se arregló en este ticket; las otras porque la diferencia entre «5 km» y «l/100 km»
         * la entiende ahora el PATRÓN, que es donde tenía que vivir: una allowlist les habría dado
         * barra libre también a las medidas de verdad que se colaran en esas mismas frases. Si
         * alguna vuelve a hacer falta en esta lista, es que algo se ha desandado.
         */
        val ALLOWED = setOf(
            "relative_time_compact_min",
            "relative_time_compact_now",
        )

        /** El working dir de los tests es el módulo Gradle por defecto, no por contrato. */
        val COMPOSE_RESOURCES: File = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?.let { File(it, "shared/src/commonMain/composeResources") }
            ?: error("repo root not found from ${File("").absolutePath}")
    }
}
