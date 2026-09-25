package com.rndeveloper.paparcar.i18n

import org.junit.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * `${'$'}` fuera de un *raw string* imprime el código en pantalla.
 * [UI-AN-ESCAPED-DOLLAR-PRINTS-THE-CODE-001]
 *
 * `${'$'}` es el idioma de Kotlin para emitir un **dólar literal** — el de las plantillas de Gradle
 * y el código que genera código, donde hay que impedir la interpolación. En una cadena normal es
 * innecesario, porque `\$` ya hace eso; así que ahí es siempre una de dos cosas: una interpolación
 * que alguien rompió sin querer, o una forma retorcida de escribir `\$`.
 *
 * Roto de verdad, y por eso este guard existe: el alta de vehículo enseñaba
 * `${'$'}{title.uppercase()} · …` literalmente en tres cabeceras, y la pista de altura enseñaba su
 * propia expresión en vez de `1,4 – 1,6`. Entró en `a0f57bf1a`. Ocho meses de distancia y otro
 * commit sin relación (`b67968dc3`) cayó en lo mismo en los mensajes de assert de
 * `MineturStationsSourceTest`, donde el daño es que un test que falla no dice qué se pidió.
 * **Dos commits que no se parecen en nada: eso es un guard, no una revisión.**
 *
 * ⛔ **Dentro de `"""…"""` es LEGÍTIMO y no se toca.** Kotlin no admite `\$` en un raw string, así
 * que `${'$'}` es la única salida — `DistanceUnitCopyGuardrailTest` lo usa para meter un `$` en una
 * regex y su KDoc ya lo explica. La exención es **estructural**, no una lista de ficheros: sobrevive
 * a que mañana alguien añada otro raw string legítimo, y no hay que mantenerla.
 *
 * Es la tercera de la misma familia — [COPY-APOSTROPHE-IS-NOT-ESCAPED-001] y
 * [COPY-PERCENT-IS-NOT-ESCAPED-001] son escapes que producen texto literal donde se esperaba otra
 * cosa. Aquellas viven en los XML y las vigila [ComposeResourcesEscapeGuardrailTest]; ésta vive en
 * Kotlin, así que aquel guard es ciego a ella por construcción y no por descuido.
 */
class EscapedDollarGuardrailTest {

    @Test
    fun should_carryNoEscapedDollarOutsideRawStrings_when_theKotlinTreeIsRead() {
        val offenders = kotlinSources()
            .flatMap { file ->
                var inRaw = false
                file.readLines().mapIndexedNotNull { idx, line ->
                    val opensOrCloses = TRIPLE_QUOTE.findAll(line).count()
                    val wasRaw = inRaw
                    if (opensOrCloses % 2 == 1) inRaw = !inRaw
                    // Una línea que abre o cierra un raw string se considera raw: el `${'$'}` que
                    // lleve está, casi siempre, dentro de esas comillas.
                    val raw = wasRaw || opensOrCloses > 0
                    val comment = line.trimStart().let { it.startsWith("*") || it.startsWith("//") }
                    if (!raw && !comment && ESCAPED_DOLLAR in line) {
                        "${file.path.replace('\\', '/').substringAfter("src/")}:${idx + 1}\n" +
                            "        ${line.trim().take(110)}"
                    } else {
                        null
                    }
                }
            }

        assertTrue(
            offenders.isEmpty(),
            "\n`\${'\$'}` fuera de un raw string imprime el código tal cual en pantalla.\n" +
                "En una cadena normal usa interpolación de verdad (\"\${x}\") o, si querías un dólar\n" +
                "literal, escríbelo \"\\\$\". Dentro de \"\"\"…\"\"\" sí es legítimo y este guard lo exime.\n" +
                "[UI-AN-ESCAPED-DOLLAR-PRINTS-THE-CODE-001]\n\n" +
                offenders.joinToString("\n"),
        )
    }

    private companion object {
        const val ESCAPED_DOLLAR = "\${'\$'}"
        val TRIPLE_QUOTE = Regex("\"\"\"")

        /** Todo el Kotlin del repo: los dos módulos y todos sus source sets. */
        fun kotlinSources(): List<File> =
            listOf(File("src"), File("../app/src"))
                .filter { it.isDirectory }
                .flatMap { it.walkTopDown().filter { f -> f.isFile && f.extension == "kt" } }
                .toList()
    }
}
