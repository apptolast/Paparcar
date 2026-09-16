package com.rndeveloper.paparcar.i18n

import org.junit.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * El guard que `COPY-APOSTROPHE-IS-NOT-ESCAPED-001` no dejó, y por eso la regla se volvió a romper.
 * [COPY-AN-APOSTROPHE-GUARD-THAT-NEVER-EXISTED-001]
 *
 * `4ae0cabe` barrió 87 apóstrofos escapados y dejó el árbol a cero. Meses después había seis otra
 * vez, todos entrados por el espejo de notificaciones: copiar de `app/src/main/res` —donde el escape
 * es OBLIGATORIO— a `composeResources` —donde está PROHIBIDO— sin desescapar. La regla estuvo escrita
 * todo ese tiempo en `CLAUDE.md`. Una regla sin guard es una regla que se vuelve a romper.
 *
 * Qué se vigila, y por qué estos dos:
 *  - **`\'`** — Compose Resources no lo desescapa y el usuario lee la barra («I\'ve left»). Medido en
 *    su día decodificando el `strings.commonMain.cvr` del APK instalado: `\n` SÍ se desescapa, el
 *    apóstrofo crudo sale limpio, y `\'` es la única secuencia rota.
 *  - **`%%`** — [COPY-PERCENT-IS-NOT-ESCAPED-001]. Misma familia y misma causa: esto no es
 *    `android:strings` ni `String.format`. El formateo de `components-resources` es un solo
 *    `Regex("""%(\d+)\$[ds]""").replace(...)`, así que `%%` sale impreso tal cual («−41%%»). Hoy está
 *    a cero; esto es lo que lo mantiene ahí.
 *
 * ⛔ **`app/src/main/res` NO se mira.** Allí la regla es la INVERSA y el compilador de Android ya la
 * impone: un `'` crudo no compila. Repetirla aquí sería escribir dos veces la regla contraria, y ese
 * árbol tiene hoy 33 `\'` que están bien.
 *
 * ⛔ **`NotificationCopyParityTest` no cubre esto y no debe intentarlo.** Normaliza `\'` en los dos
 * lados antes de comparar, porque para su pregunta —*¿dicen lo mismo?*— la diferencia de escape es
 * legítima. Es correcto para lo suyo y ciego para esto. Dos guards, dos preguntas.
 */
class ComposeResourcesEscapeGuardrailTest {

    @Test
    fun should_carryNoEscapedApostrophe_when_everyComposeResourcesLocaleIsRead() {
        assertNoneOf(ESCAPED_APOSTROPHE, "\\'") {
            "Compose Resources no desescapa \\' — el usuario lee la barra. Apóstrofo CRUDO aquí. " +
                "⛔ Ojo: en app/src/main/res la regla es la contraria y un ' crudo no compila."
        }
    }

    @Test
    fun should_carryNoDoubledPercent_when_everyComposeResourcesLocaleIsRead() {
        assertNoneOf(DOUBLED_PERCENT, "%%") {
            "El formateo de Compose Resources no es String.format: %% sale impreso tal cual. " +
                "Un % literal se escribe con un solo %."
        }
    }

    /**
     * Testigo de población. Un guard que lee un path equivocado —o un árbol vacío— devuelve cero
     * hallazgos y verde, y son dos verdes distintos que no se pueden confundir. Esto no es celo: la
     * medición que abrió este ticket dio «0» durante un rato justamente porque leía revisiones en
     * las que el path no existía todavía, y nadie miraba el código de salida.
     * [TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001]
     */
    @Test
    fun should_haveActuallyReadNineLocales_when_theGuardReportsClean() {
        assertTrue(
            localeFiles().size == LOCALES.size,
            "el guard solo encontró ${localeFiles().size} de ${LOCALES.size} ficheros de locale — " +
                "está mirando otro sitio, no un árbol limpio",
        )
        localeFiles().forEach { (locale, file) ->
            val strings = STRING_TAG.findAll(file.readText()).count()
            assertTrue(strings > 100, "$locale solo declara $strings cadenas — el guard lee un fichero vacío")
        }
    }

    private fun assertNoneOf(needle: String, shown: String, message: () -> String) {
        val hits = localeFiles().flatMap { (locale, file) ->
            file.readLines().withIndex()
                .filter { (_, line) -> needle in line }
                .map { (index, line) -> "  - $locale:${index + 1}  ${line.trim().take(160)}" }
        }
        assertTrue(hits.isEmpty(), "[${shown} en composeResources] ${message()}\n${hits.joinToString("\n")}")
    }

    private fun localeFiles(): List<Pair<String, File>> = LOCALES.mapNotNull { locale ->
        File(COMPOSE_RESOURCES, "$locale/strings.xml").takeIf { it.isFile }?.let { locale to it }
    }

    private companion object {
        val LOCALES = listOf(
            "values", "values-es", "values-it", "values-pt", "values-fr",
            "values-de", "values-nl", "values-pl", "values-ro",
        )

        val STRING_TAG = Regex("""<string name="""")

        /**
         * Construidas con `Char(92)` a propósito. La medición que destapó esto se equivocó por un
         * nivel de escape perdido en tránsito, y una constante que se lee sin ambigüedad no puede
         * repetir ese error.
         */
        val ESCAPED_APOSTROPHE = "${Char(92)}'"
        const val DOUBLED_PERCENT = "%%"

        /** El working dir de los tests es el módulo Gradle por defecto, no por contrato. */
        val COMPOSE_RESOURCES: File = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?.let { File(it, "shared/src/commonMain/composeResources") }
            ?: error("repo root not found from ${File("").absolutePath}")
    }
}
