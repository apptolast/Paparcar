package com.rndeveloper.paparcar.architecture

import org.junit.Test
import kotlin.test.assertTrue

/**
 * El mínimo táctil, que es **una sola ley con dos caras**.
 * [UI-THE-SPACING-SWEEP-IS-SCREEN-BY-SCREEN-001]
 *
 * Material3 reserva **48 dp** para cualquier control pulsable, y eso produce dos efectos opuestos
 * según cómo se dibuje el control. Las dos caras se pagaron a mano en este ticket, **siete veces**,
 * y por eso existe este fichero:
 *
 *  1. **Un componente de Material3** (`Surface(onClick)`, `Button`, `Chip`…) lo aplica solo, así que
 *     un control más bajo de 48 se infla en LAYOUT y pinta centrado — dejando `(48 − altura)` de
 *     aire **invisible** que ninguna constante de hueco conoce. Medido: dos cápsulas de 40 dp con
 *     `spacedBy(12)` se veían a **20,2 dp**. Lo arregla `PapGap.betweenControls`.
 *  2. **`Modifier.clickable` NO lo aplica.** Un control dibujado a mano se queda EXACTAMENTE con el
 *     área que pinta, y aquí había siete por debajo del mínimo — el peor, la × de los campos de
 *     texto, a **22 dp**. Lo arregla `Modifier.minimumInteractiveComponentSize()`.
 *
 * ## Lo que estos chequeos NO ven
 *
 * Que el padre no lo aplaste. El lápiz del chip de zona lleva ya el modificador, pero su alto lo capa
 * la altura declarada del chip (34 dp), así que ahí el mínimo sólo gana a lo ancho — y este test sale
 * verde igual. Subir el chip es una decisión de diseño, no algo que un regex pueda exigir; queda
 * escrito en el doc del ticket en vez de fingido aquí.
 *
 * ⛔ Falsificarlos exige `--rerun-tasks`: no tocan bytecode y Gradle los da UP-TO-DATE.
 */
class TouchTargetGuardrailTest {

    /**
     * Cara 2: un `clickable` con tamaño propio declara también su área de toque, así que tiene que
     * pedir el mínimo.
     *
     * El par `.size(…)` → `.clickable(` es la firma de un control dibujado a mano; los componentes
     * de Material3 no la tienen porque el tamaño lo pone el componente.
     */
    @Test
    fun `a hand-drawn clickable declares its touch target`() {
        val violations = GuardrailScope.featureFiles()
            .filter { SIZED_CLICKABLE_REGEX.containsMatchIn(it.text) }
            .filterNot { it.text.contains(MINIMUM_TOUCH_TARGET) }
            .map { it.name }
        assertTrue(
            violations.isEmpty(),
            "[`.size(...)` + `.clickable(` sin `minimumInteractiveComponentSize()` — el área de " +
                "toque es la que pinta, y el mínimo son 48 dp] ${violations.size} violación(es):\n" +
                violations.joinToString("\n") { "  - $it.kt" },
        )
    }

    /**
     * Cara 1: un hueco junto a un control se pide en lo que se VE.
     *
     * Acotado por **dos** filtros, y los dos se ganaron falsificando:
     *
     *  · a los ficheros que YA consumen la escalera de alturas — ahí hay un control pulsable por
     *    construcción, mientras que fuera un literal puede separar dos textos, que no inflan nada;
     *  · y a `verticalArrangement`. ⛔ Al escribirlo sin este segundo filtro cazó dos
     *    `horizontalArrangement = spacedBy(8.dp)` (`HistoryContent`, `VehiclesScreen`) y **las dos
     *    eran correctas**: el mínimo infla en las dos dimensiones, pero la escalera de este proyecto
     *    (34 · 40 · 48) sólo es más pequeña que 48 en el ALTO — una fila de chips es más ancha, así
     *    que horizontalmente la constante dice la verdad y descontar habría juntado los chips.
     *
     * Lo que por tanto NO ve: una fila horizontal de controles ESTRECHOS (los dos redondos del peek,
     * 38 dp). Su anchura no se puede leer de un regex, así que ese caso se paga mirando — y es el
     * que se pagó a mano aquí.
     */
    @Test
    fun `a gap next to a control asks for what is seen`() {
        val violations = GuardrailScope.featureFiles()
            .filter { it.text.contains(CONTROL_HEIGHT_TOKEN) }
            .flatMap { file ->
                file.text.lines()
                    .filter { LITERAL_SPACED_BY_REGEX.containsMatchIn(it) }
                    .map { "${file.name}.kt: ${it.trim()}" }
            }
        assertTrue(
            violations.isEmpty(),
            "[`spacedBy(<literal>.dp)` en un fichero que dibuja controles — usa " +
                "`PapGap.betweenControls(visible = …, controlHeight = …)`, porque el mínimo táctil " +
                "añade (48 − altura) que la constante no sabe] ${violations.size} violación(es):\n" +
                violations.joinToString("\n") { "  - $it" },
        )
    }

    private companion object {
        /** `.size(lo que sea)` y, en la misma cadena, `.clickable(`. */
        val SIZED_CLICKABLE_REGEX = Regex("""\.size\([^)]*\)[^;{}]{0,400}?\.clickable\(""", RegexOption.DOT_MATCHES_ALL)

        /**
         * La LLAMADA al modificador, no su nombre a secas.
         *
         * ⛔ Escrito primero como `"minimumInteractiveComponentSize"`, y la falsificación lo cazó:
         * al quitar el modificador de `PapClearIconButton` el test siguió VERDE, porque **el import
         * sigue teniendo esa palabra**. Un import huérfano satisfacía el chequeo — el agujero
         * clásico de buscar un símbolo en vez de su uso.
         */
        val MINIMUM_TOUCH_TARGET = ".minimumInteractiveComponentSize()"

        /** El fichero consume la escalera de alturas ⇒ dibuja controles pulsables. */
        val CONTROL_HEIGHT_TOKEN = "PapControlHeight."

        /** `verticalArrangement = …spacedBy(8.dp)` — el literal crudo, y sólo en vertical.
         *  `spacedBy(PapGap.…)` y `spacedBy(X_DP.dp)` pasan: el segundo es una constante con
         *  nombre, que es la forma legal del proyecto. */
        val LITERAL_SPACED_BY_REGEX =
            Regex("""verticalArrangement\s*=\s*Arrangement\.spacedBy\(\s*\d+(?:\.\d+)?\.dp""")
    }
}
