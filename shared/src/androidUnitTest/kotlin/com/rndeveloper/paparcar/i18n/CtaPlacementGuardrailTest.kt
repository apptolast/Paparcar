package com.rndeveloper.paparcar.i18n

import com.rndeveloper.paparcar.ui.components.CTA_INLINE_MAX_CHARS
import com.rndeveloper.paparcar.ui.components.CTA_SIDE_BY_SIDE_MAX_CHARS
import com.rndeveloper.paparcar.ui.components.ctaFitsInline
import com.rndeveloper.paparcar.ui.components.ctaPairFitsSideBySide
import org.junit.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * El testigo de [UI-A-PILL-IS-FOR-A-LABEL-NOT-A-SENTENCE-002]: **un cambio de copy no puede cambiar
 * una maqueta en silencio.**
 *
 * Este proyecto no tiene tests de Compose UI —lo dejó escrito
 * [UI-A-PILL-IS-FOR-A-LABEL-NOT-A-SENTENCE-001]: *«las tres píldoras pasaron los 3594 tests y
 * ninguno las mira»*— así que ninguna prueba puede ver dónde acaba pintado un botón. Lo que sí es
 * comprobable es la ENTRADA de esa decisión: la longitud del label en los 9 locales. Que es
 * exactamente el dato que nadie volvió a mirar cuando «Parar» (9 chars) se convirtió en «No voy en
 * mi coche» (23 en italiano) y la fila del viaje se quedó pintando inline una frase.
 *
 * ⚠️ Fallar aquí NO significa que la traducción esté mal. Significa que **esa fila ha cambiado de
 * maqueta** y hay que mirarla en pantalla antes de aceptar el cambio: si la nueva colocación es la
 * buena, se actualiza la expectativa de abajo, y queda escrito que se miró.
 */
class CtaPlacementGuardrailTest {

    @Test
    fun `every Home CTA still lands on the layout side it was designed for`() {
        val violations = EXPECTED.flatMap { (key, expected) ->
            LOCALE_DIRS.mapNotNull { locale ->
                val label = labelOf(key, locale)
                    ?: return@mapNotNull "  - $locale · $key is not declared"
                val actual = if (ctaFitsInline(label)) Placement.INLINE else Placement.STACKED
                if (actual == expected) {
                    null
                } else {
                    "  - $locale · $key = \"$label\" (${label.length} chars) now renders " +
                        "$actual, designed as $expected"
                }
            }
        }

        assertTrue(
            violations.isEmpty(),
            "[a copy change moved a button: the label crossed the $CTA_INLINE_MAX_CHARS-char inline " +
                "budget] ${violations.size} label(s):\n${violations.joinToString("\n")}\n" +
                "Look at the row on screen. If the new placement is right, update EXPECTED here — " +
                "that edit IS the record that somebody looked.",
        )
    }

    /**
     * El mismo testigo, para la OTRA pregunta: **¿caben DOS cápsulas en la misma fila?**
     * [UI-A-PILL-PAIR-SPLITS-THE-BUDGET-IN-TWO-001]
     *
     * El test de arriba llevaba escrito desde que nació que `home_det_awaiting_cta_primary` es una
     * frase y baja a ancho completo… y la fila de dos botones la pintaba a MEDIA anchura de todas
     * formas, porque nadie había preguntado por el par. Dicho de otro modo: el dato ya estaba
     * medido y la maqueta que lo rompía no lo consultaba. Aquí se consulta.
     *
     * ⚠️ Igual que arriba: fallar NO significa que la traducción esté mal, significa que **ese par
     * ha cambiado de maqueta**. Se mira en pantalla y se actualiza la expectativa — esa edición ES
     * el registro de que alguien miró.
     */
    @Test
    fun `every Home CTA pair still lands on the layout it was designed for`() {
        val violations = EXPECTED_PAIRS.flatMap { (pair, expectation) ->
            LOCALE_DIRS.mapNotNull { locale ->
                val primary = labelOf(pair.first, locale)
                    ?: return@mapNotNull "  - $locale · ${pair.first} is not declared"
                val secondary = labelOf(pair.second, locale)
                    ?: return@mapNotNull "  - $locale · ${pair.second} is not declared"
                val actual = if (ctaPairFitsSideBySide(primary, secondary)) {
                    PairLayout.SIDE_BY_SIDE
                } else {
                    PairLayout.STACKED
                }
                val expected = expectation.at(locale)
                if (actual == expected) {
                    null
                } else {
                    "  - $locale · ${pair.first} + ${pair.second} = " +
                        "\"$primary\" (${primary.length}) / \"$secondary\" (${secondary.length}) " +
                        "now renders $actual, designed as $expected"
                }
            }
        }

        assertTrue(
            violations.isEmpty(),
            "[a copy change moved a button pair: a label crossed the " +
                "$CTA_SIDE_BY_SIDE_MAX_CHARS-char half-width budget] ${violations.size} pair(s):\n" +
                "${violations.joinToString("\n")}\n" +
                "Look at the row on screen. If the new layout is right, update EXPECTED_PAIRS here — " +
                "that edit IS the record that somebody looked.",
        )
    }

    /**
     * These labels share a narrow vehicle card or Android's three-action notification row.
     * Keep a separate compact copy budget; this guards translations, not measured text width.
     */
    @Test
    fun `trip chip and presence notification actions keep their compact labels in every locale`() {
        val compactKeys = listOf(
            "home_trip_park_compact",
            "home_trip_continue_compact",
            "home_trip_wrong_car_compact",
            "notif_action_still_here",
            "notif_action_driving_now",
            "notif_action_parked_elsewhere",
        )
        val maxChars = 14
        val violations = LOCALE_DIRS.flatMap { locale ->
            compactKeys.mapNotNull { key ->
                val label = labelOf(key, locale)
                when {
                    label.isNullOrBlank() -> "$locale · $key is missing"
                    label.length > maxChars -> "$locale · $key = \"$label\" exceeds $maxChars chars"
                    else -> null
                }
            }
        }
        assertTrue(violations.isEmpty(), violations.joinToString("\n"))
    }

    private enum class Placement { INLINE, STACKED }

    private enum class PairLayout { SIDE_BY_SIDE, STACKED }

    /**
     * Qué maqueta se espera de un par. [default] rige los nueve locales; [exceptions] nombra los
     * que se salen — y nombrarlos es el punto: un par cuya maqueta depende del idioma tiene que
     * decirlo por escrito, no descubrirse en una captura.
     */
    private class PairExpectation(
        private val default: PairLayout,
        private val exceptions: Map<String, PairLayout> = emptyMap(),
    ) {
        fun at(locale: String): PairLayout = exceptions[locale] ?: default
    }

    private companion object {

        /**
         * Los CTA cuya colocación decide el PRESUPUESTO — o sea los del hueco *trailing* de
         * `HomeDetectionSurface`, el único sitio donde una fila elige entre al lado y debajo.
         *
         * ⛔ **`home_det_driving_stop_cta` no está aquí, y esa ausencia es la regla**: el stop del
         * viaje se apila SIEMPRE, en las dos superficies y en los 9 locales, porque su vecino es una
         * línea con el nombre de una calle —sin tope— y no hay presupuesto que la proteja. Su
         * invariante no lo sostiene este test: lo sostiene que ya no existe la rama inline.
         * Meterlo aquí fue el primer intento, y este test lo cazó: en inglés mide 13 chars y «cabría»
         * por presupuesto, lo que habría escrito que la maqueta depende del idioma cuando no depende.
         *
         * `home_det_awaiting_cta_primary` sí: 25 chars en francés («Marquer mon stationnement») y se
         * pintaba inline siempre que no hubiera CTA secundario, hasta este ticket.
         */
        val EXPECTED = mapOf(
            // Una frase: baja a ancho completo, en los 9 locales.
            "home_det_awaiting_cta_primary" to Placement.STACKED,
            // Etiquetas de una palabra: caben al lado, y por eso la fila no crece.
            "home_det_novehicle_cta" to Placement.INLINE,
            "home_det_producer_cta" to Placement.INLINE,
            "home_det_core_cta" to Placement.INLINE,
        )

        /**
         * Los tres pares de botones de `HomeDetectionSurface` — las únicas dos cápsulas que
         * comparten fila en toda la app. [UI-A-PILL-PAIR-SPLITS-THE-BUDGET-IN-TWO-001]
         *
         * Dos de los tres se apilan en los nueve idiomas y no hay nada que discutir: su acción
         * principal es «marcar mi aparcamiento», que mide 15 en inglés y 27 en portugués.
         *
         * El sí/no de la pregunta es el que roza el listón, y por eso lleva excepción: en alemán
         * («Ja, geparkt» / «Fahre noch») las dos respuestas caben hombro con hombro, y en los otros
         * ocho no. Que la maqueta dependa del idioma aquí es correcto —es el presupuesto haciendo
         * su trabajo—; lo que no puede es dependerlo en silencio.
         */
        val EXPECTED_PAIRS = mapOf(
            ("home_det_awaiting_cta_primary" to "home_det_awaiting_cta_secondary") to
                PairExpectation(PairLayout.STACKED),
            ("home_nudge_cta" to "home_nudge_dismiss") to
                PairExpectation(PairLayout.STACKED),
            ("home_det_ask_cta_yes" to "home_det_ask_cta_no") to
                PairExpectation(
                    default = PairLayout.STACKED,
                    exceptions = mapOf("values-de" to PairLayout.SIDE_BY_SIDE),
                ),
        )

        val LOCALE_DIRS = listOf(
            "values", "values-es", "values-it", "values-pt", "values-fr",
            "values-de", "values-nl", "values-pl", "values-ro",
        )

        /** Igual que en `LocaleParityGuardrailTest`: el directorio de trabajo de los tests es un
         *  defecto, no un contrato — se sube hasta la raíz del repo. */
        val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("repo root not found from ${File("").absolutePath}")

        val resources = File(repoRoot, "shared/src/commonMain/composeResources")

        fun labelOf(key: String, locale: String): String? =
            Regex("""<string\s+name="$key">([^<]*)</string>""")
                .find(File(resources, "$locale/strings.xml").readText())
                ?.groupValues?.get(1)
    }
}
