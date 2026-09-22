package com.rndeveloper.paparcar.i18n

import com.rndeveloper.paparcar.ui.components.CTA_INLINE_MAX_CHARS
import com.rndeveloper.paparcar.ui.components.ctaFitsInline
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

    private enum class Placement { INLINE, STACKED }

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
