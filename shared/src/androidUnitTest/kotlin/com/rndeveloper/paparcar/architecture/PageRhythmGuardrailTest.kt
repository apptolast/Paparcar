package com.rndeveloper.paparcar.architecture

import org.junit.Test
import kotlin.test.assertTrue

/**
 * Guardrail del ritmo VERTICAL de una página: **cuánto aire separa dos bloques de una pantalla lo
 * dice [com.rndeveloper.paparcar.ui.theme.PapPageRhythm], no la pantalla.**
 * [UI-A-PAGE-KEEPS-ONE-VERTICAL-RHYTHM-001]
 *
 * ## Por qué hacía falta un guard y no bastaba con el token
 *
 * Este proyecto ya había centralizado el alto de los controles, el tamaño del glifo con etiqueta y
 * el padding de los chips, y aun así el margen de una PÁGINA seguía declarado cinco veces en
 * privado —`CONTENT_H_PADDING`/`CONTENT_V_PADDING`, `SCREEN_H_PADDING`/`LIST_V_PADDING` (×3),
 * `H_PAD_DP`/`LIST_V_PAD_DP`— con dos valores distintos para el mismo rol. Ninguno de los seis
 * autores hizo nada raro: cada uno escribió una constante privada bien nombrada, que es justo la
 * forma legal según la regla de magic numbers. El sistema no se rompió por literales sueltos, se
 * rompió porque **nadie era el dueño**, y una constante privada por pantalla es indistinguible de
 * un sistema hasta que se ponen las pantallas una al lado de otra.
 *
 * De ahí la forma de los dos chequeos: uno prohíbe el literal (la vía rápida), y el otro —el que
 * de verdad cierra el agujero— exige que **toda página nueva cite al dueño**. El segundo es el que
 * habría cazado las cinco declaraciones privadas; el primero no las habría visto nunca.
 *
 * ⛔ Falsificar cualquiera de los dos exige `--rerun-tasks`: no cambian bytecode, así que Gradle da
 * la tarea por UP-TO-DATE y no llega a ejecutarla. Misma trampa que el resto del paquete.
 */
class PageRhythmGuardrailTest {

    /**
     * El aire vertical entre hermanos es un ROL, así que no se escribe como número.
     *
     * Dónde mordió: `VehiclePageContent` separaba la hero card de la sección del grupo con
     * `spacedBy(12.dp)` mientras Ajustes usaba 8 para lo mismo — la diferencia que el user señaló
     * (19-10) mirando las dos pantallas seguidas.
     *
     * Acotado a `verticalArrangement` a propósito: el hueco HORIZONTAL de una fila de chips ya
     * tiene dueño ([com.rndeveloper.paparcar.ui.theme.PapGap.chipRow]) y su propia historia, y
     * meterlos en un solo chequeo es lo que convirtió en inusable la regla de "ningún literal dp".
     */
    @Test
    fun `no feature file writes its own vertical arrangement gap`() {
        val violations = GuardrailScope.featureFiles()
            .flatMap { file ->
                file.text.lines()
                    .filter { line -> VERTICAL_LITERAL_GAP_REGEX.containsMatchIn(line) }
                    .map { line -> "${file.name}.kt: ${line.trim()}" }
            }
        val header = "[vertical gap written as a literal — ask PapPageRhythm (page blocks), " +
            "PapGap (inside a block) or name it with its reason] ${violations.size} violation(s):"
        assertTrue(
            violations.isEmpty(),
            (listOf(header) + violations.map { "  - $it" }).joinToString(System.lineSeparator()),
        )
    }

    /**
     * Y toda PÁGINA cita al dueño del ritmo.
     *
     * La población se detecta, no se enumera: una lista con `contentPadding` es una página
     * scrollable —la forma que tienen las seis pantallas de este árbol—, así que **la pantalla que
     * alguien añada mañana entra sola**. Enumerarlas habría dejado fuera exactamente el caso por el
     * que existe este chequeo.
     *
     * Exención, nombrada en vez de escondida en una regex: `HomeBottomSheet` monta una lista con
     * `contentPadding` y NO es una página — es la hoja de Home, cuyo aire responde a la doctrina de
     * peek ([com.rndeveloper.paparcar.ui.theme.PapGap.block]) porque es una superficie densa que se
     * arrastra, no una columna de bloques que se scrollea.
     */
    @Test
    fun `every page list asks PapPageRhythm for its rhythm`() {
        val pages = GuardrailScope.pageFiles()
        val violations = pages
            .filterNot { it.name in NOT_A_PAGE }
            .filterNot { it.text.contains("PapPageRhythm") }
            .map { it.name }
        val header = "[a page that sets its own margins — take them from PapPageRhythm] " +
            "${violations.size} violation(s):"
        assertTrue(
            violations.isEmpty(),
            (listOf(header) + violations.map { "  - $it.kt" }).joinToString(System.lineSeparator()),
        )
    }

    private companion object {
        /** `verticalArrangement = Arrangement.spacedBy(12.dp)` — el rol escrito como número. */
        val VERTICAL_LITERAL_GAP_REGEX =
            Regex("""verticalArrangement\s*=\s*Arrangement\.spacedBy\(\s*\d+(?:\.\d+)?\.dp""")

        /**
         * No son páginas aunque tengan la forma: la hoja de Home se arrastra y su aire es el de un
         * peek, no el de una columna de bloques.
         */
        val NOT_A_PAGE = setOf("HomeBottomSheet")

    }
}
