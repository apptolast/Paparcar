package com.rndeveloper.paparcar.architecture

import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
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
            .filterNot { it.usesPageRhythm() }
            .map { it.name }
        val header = "[a page that sets its own margins — take them from PapPageRhythm] " +
            "${violations.size} violation(s):"
        assertTrue(
            violations.isEmpty(),
            (listOf(header) + violations.map { "  - $it.kt" }).joinToString(System.lineSeparator()),
        )
    }

    /**
     * Y lo que ya se adhirió al rol **no se des-adhiere en silencio**.
     * [UI-A-PAGE-GUARD-MUST-SEE-PAST-THE-LIST-SHAPE-001]
     *
     * Hay superficies que colocan bloques contra el borde de la pantalla y que **ninguna forma
     * selecciona**, porque su scroll lo pone otro: un pager, el `Column` de la pantalla, el mapa de
     * debajo. Citan [com.rndeveloper.paparcar.ui.theme.PapPageRhythm] desde
     * [UI-A-PAGE-KEEPS-ONE-VERTICAL-RHYTHM-001] — pero lo citaban porque alguien las editó, no
     * porque nada se lo exigiera, y un rol CON dueño pero sin guardia se des-adhiere igual que uno
     * sin dueño: basta un `padding(12.dp)` en la siguiente edición.
     *
     * ⛔ **Sí, es una lista, y es deliberado.** Enumerar está mal como única población porque deja
     * fuera lo que nazca mañana, y de eso se encarga el chequeo de arriba. Aquí la pregunta es la
     * contraria — *¿sigue adherido lo que ya migramos?* — y esa sólo se puede contestar nombrando.
     * Por eso falla también cuando el fichero **no aparece**: un rename que se lleve por delante la
     * cita dejaría el guard mirándose a un conjunto vacío, que es la forma de verde que este
     * paquete no admite. [TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001]
     */
    @Test
    fun `every adhered surface keeps asking PapPageRhythm`() {
        val byName = GuardrailScope.presentationFeatureFiles().associateBy { it.name }
        val missing = ADHERED_SURFACES.keys.filterNot { it in byName }
        val dropped = ADHERED_SURFACES.keys
            .mapNotNull { byName[it] }
            .filterNot { it.usesPageRhythm() }
            .map { it.name }

        val violations =
            missing.map {
                "$it.kt: no aparece en presentation/ — ¿renombrado? mientras tanto el guard deja " +
                    "de vigilarlo: actualiza ADHERED_SURFACES con su nombre nuevo"
            } +
            dropped.map { "$it.kt: dejó de citar PapPageRhythm — ${ADHERED_SURFACES[it]}" }

        val header = "[una superficie adherida se soltó del rol] ${violations.size} violation(s):"
        assertTrue(
            violations.isEmpty(),
            (listOf(header) + violations.map { "  - $it" }).joinToString(System.lineSeparator()),
        )
    }

    /**
     * ¿Este fichero **usa** el rol, o sólo lo nombra?
     * [UI-A-PAGE-GUARD-MUST-SEE-PAST-THE-LIST-SHAPE-001]
     *
     * ⛔ Era `text.contains("PapPageRhythm")`, y eso lo satisface un **import huérfano**: descubierto
     * al falsificar el chequeo de superficies adheridas — cambiados los dos usos de
     * `VehiclePageContent` por la escala a pelo, el import se quedó ahí (Kotlin sólo avisa) y el
     * guard siguió verde sobre una pantalla que ya no preguntaba nada. El KDoc que explica el rol
     * lo satisfaría igual, y es justo lo que sobrevive a un refactor descuidado.
     *
     * Por eso se cuenta el USO: `PapPageRhythm.` fuera de un `import` y fuera de comentario.
     */
    private fun KoFileDeclaration.usesPageRhythm(): Boolean =
        text.lineSequence().any { line ->
            val code = line.substringBefore("//").trim()
            code.contains("PapPageRhythm.") &&
                !code.startsWith("import ") &&
                !code.startsWith("*") &&
                !code.startsWith("/*")
        }

    private companion object {
        /** `verticalArrangement = Arrangement.spacedBy(12.dp)` — el rol escrito como número. */
        val VERTICAL_LITERAL_GAP_REGEX =
            Regex("""verticalArrangement\s*=\s*Arrangement\.spacedBy\(\s*\d+(?:\.\d+)?\.dp""")

        /**
         * No son páginas aunque tengan la forma — y cada una con su razón, aquí y no en el doc de
         * un ticket cerrado, porque aquí es donde la lee quien se tope con el guard.
         * [UI-A-PAGE-GUARD-MUST-SEE-PAST-THE-LIST-SHAPE-001]
         *
         *  - `HomeBottomSheet` — una hoja que se **arrastra**: su aire es el de un peek
         *    (`PapGap.block`), no el de una columna de bloques que se scrollea.
         *  - `OnboardingScreen`, `FirstStepExplainerScreen`, `VehicleSizeExplainerScreen` — no son
         *    columnas de hermanos sino **composiciones centradas** (ilustración, título, prosa,
         *    CTA). Sus márgenes `xxl`/`xxxl` son deliberados: una pantalla de bienvenida respira
         *    más que una lista, y estrecharla a 16 sería un rediseño, no una corrección.
         *  - `PaywallScreen` — lo mismo, más su `SCREEN_H_PADDING_DP` 20 de superficie de venta.
         *
         * ⛔ La exención se escribe aquí, nunca retorciendo la forma de la población para que algo
         * no entre: una exención con nombre se lee y se discute; un selector amañado, no.
         */
        val NOT_A_PAGE = setOf(
            "HomeBottomSheet",
            "OnboardingScreen",
            "FirstStepExplainerScreen",
            "VehicleSizeExplainerScreen",
            "PaywallScreen",
        )

        /**
         * Superficies que piden su aire al rol y que **ninguna forma selecciona**, con el motivo
         * por el que son página aunque no lo parezcan.
         *
         * `VehicleRegistrationScreen` NO está aquí a propósito: desde
         * [UI-A-PAGE-GUARD-MUST-SEE-PAST-THE-LIST-SHAPE-001] entra sola por `verticalScroll`, y
         * vigilar lo mismo dos veces reparte la responsabilidad en vez de concentrarla.
         */
        val ADHERED_SURFACES = mapOf(
            "VehiclePageContent" to
                "es la página de un vehículo dentro del pager, y su hueco entre la hero card y la " +
                "sección del grupo es justo el que el user vio al doble (20,2 dp)",
            "HomeHeaderSection" to
                "coloca el buscador y la fila de zonas contra el borde, sobre el mapa: es la " +
                "cabecera de Home, aunque su scroll no exista",
            "SharedCarSection" to
                "apila la card del grupo con su fila de chips dentro de la página de vehículo, y " +
                "es donde el overhang del chip pintaba 15 dp donde se pedían 8",
        )

    }
}
