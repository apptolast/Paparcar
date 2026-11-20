package com.rndeveloper.paparcar.architecture

import org.junit.Test
import java.io.File
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * `gradle/libs.versions.toml` declara lo que el build usa, y nada más.
 * [BUILD-THE-CATALOG-SAYS-ONLY-WHAT-THE-BUILD-USES-001]
 *
 * ## Por qué existe
 *
 * El catálogo tenía tres clases de basura a la vez, y el barrido de versiones
 * ([BUILD-DEPS-AT-LATEST-STABLE-002], [DEPS-REVENUECAT-3-10-AND-A-FULL-SWEEP-001]) sólo miraba una
 * de ellas — si el número estaba atrasado:
 *
 *  1. **Alias muertos.** Tres librerías de `lifecycle-viewmodel` con un comentario de tres líneas
 *     explicando que «usamos las tres variantes» y **cero** consumidores, más dos plugins
 *     (`androidKmpLibrary`, `kotlinCocoapods`) declarados `apply false` en la raíz que ningún módulo
 *     aplicaba. Nada de eso rompe un build: simplemente miente sobre lo que la app usa.
 *  2. **Claves duplicadas.** Seis claves con el mismo valor para la misma línea de release de
 *     Compose Multiplatform, y dos para `androidx.lifecycle`. La regla de subirlas a la vez tenía
 *     que vivir en CLAUDE.md **porque el catálogo no la podía expresar**, y una regla escrita en
 *     prosa es exactamente lo que se olvida en el siguiente barrido.
 *  3. **Pre-releases.** «Solo estables» era prosa también.
 *
 * ## Lo que comprueba, y por qué estas tres
 *
 *  1. **Ningún alias huérfano** — cada entrada de `[libraries]`/`[plugins]` la nombra algún
 *     `build.gradle.kts`. Es la mitad mecánica de «el catálogo dice lo que el build usa».
 *  2. **Ninguna clave de versión huérfana** — cada entrada de `[versions]` la referencia un
 *     `version.ref` del propio catálogo o un `libs.versions.*` de un script. Es lo que mantiene el
 *     colapso de claves duplicadas: la séptima clave de una línea que ya tiene la suya se queda sin
 *     referencias en el momento en que se deja de usar, y entonces sale aquí.
 *  3. **Ni un pre-release** — ni alpha, ni beta, ni rc, ni dev, ni snapshot, ni EAP.
 *
 * Lo que NO comprueba, y no puede: si un número está **atrasado**. Eso exige hablar con
 * `repo1.maven.org` y `dl.google.com`, y un test que pide red se vuelve rojo el día que la red
 * falla, que es el modo más rápido de que alguien lo borre. El barrido sigue siendo una tarea
 * humana; esto vigila la forma del fichero, no su fecha.
 *
 * ## El testigo
 *
 * Las tres comprobaciones filtran poblaciones, así que las tres comparten el mismo punto ciego:
 * *no hay violaciones* y *no hay dónde mirar* dan el mismo verde — la lección de [GuardrailScope].
 * Un `settings.gradle.kts` que mueva módulos, un renombrado de carpeta o un parser que deje de
 * entender el TOML dejarían este test contento y vacío. Por eso [catalogIsPopulatedAndReachable]
 * mide primero las cuatro poblaciones contra suelos declarados, y los suelos están a ~la mitad de
 * lo medido el 20-11-2026 — **72 alias** (60 librerías + 12 plugins), **44 claves de versión**,
 * **5 scripts** y **75 accesos `libs.*`** — para no convertir cada borrado normal en un build rojo.
 *
 * ## Falsificado, no razonado
 *
 * Las tres prohibiciones se vieron ROJAS antes de darlas por buenas: metiendo en el catálogo un
 * `zz-dead-alias` que ningún script pide, una clave `zz-orphan-key` que nada referencia y un
 * `zz-prerelease = "2.0.0-beta3"`, las tres fallaron a la vez y nombrando al culpable (20-11-2026,
 * `--rerun-tasks`, que aquí NO es opcional: tocar el TOML no cambia el bytecode y Gradle da el test
 * por UP-TO-DATE).
 *
 * [catalogIsPopulatedAndReachable] es la excepción honesta: no se puede falsificar en aislamiento,
 * porque cualquier forma de dejar al parser sin material —mover el catálogo, romper sus cabeceras—
 * tumba primero el propio build de Gradle. Existe para el día en que el fichero siga ahí y este
 * parser se quede obsoleto, que es cuando el verde sería mentira sin que nada más se entere.
 */
class VersionCatalogGuardrailTest {

    @Test
    fun catalogIsPopulatedAndReachable() {
        assertTrue(
            catalog.isFile,
            "el catálogo no está donde este guard lo busca: ${catalog.path} (desde ${repoRoot.path})",
        )
        assertTrue(
            buildScripts.size >= MIN_BUILD_SCRIPTS,
            "sólo ${buildScripts.size} ficheros .gradle.kts encontrados (suelo $MIN_BUILD_SCRIPTS): " +
                "el guard se habría quedado sin dónde mirar",
        )
        assertTrue(
            aliases.size >= MIN_ALIASES,
            "sólo ${aliases.size} alias leídos del catálogo (suelo $MIN_ALIASES): el parser no lo entiende",
        )
        assertTrue(
            versionKeys.size >= MIN_VERSION_KEYS,
            "sólo ${versionKeys.size} claves de versión leídas (suelo $MIN_VERSION_KEYS)",
        )
        assertTrue(
            accessorsUsed.size >= MIN_ACCESSORS,
            "sólo ${accessorsUsed.size} accesos `libs.*` encontrados en los scripts (suelo $MIN_ACCESSORS)",
        )
    }

    @Test
    fun everyDeclaredAliasHasAConsumer() {
        val orphans = aliases.filterNot { (_, accessor) -> accessor in accessorsUsed }.map { it.first }
        if (orphans.isNotEmpty()) {
            fail(
                "alias del catálogo que ningún build.gradle.kts nombra — bórralos, no los comentes:\n" +
                    orphans.joinToString("\n") { "  · $it" },
            )
        }
    }

    @Test
    fun everyVersionKeyIsReferenced() {
        val referenced = versionRefsInCatalog + versionAccessorsUsed
        val orphans = versionKeys.filterNot { it in referenced }
        if (orphans.isNotEmpty()) {
            fail(
                "claves de [versions] que nada referencia (ni un `version.ref`, ni un `libs.versions.*`):\n" +
                    orphans.joinToString("\n") { "  · $it" } +
                    "\nDos claves con el mismo valor para la misma línea de release son dos sitios " +
                    "donde olvidarse de una: colapsa, no dupliques.",
            )
        }
    }

    @Test
    fun noPreReleaseVersions() {
        val offenders = versionValues.filter { (_, value) -> PRE_RELEASE.containsMatchIn(value) }
        if (offenders.isNotEmpty()) {
            fail(
                "el catálogo sólo lleva estables [BUILD-DEPS-AT-LATEST-STABLE-002]:\n" +
                    offenders.joinToString("\n") { (key, value) -> "  · $key = \"$value\"" },
            )
        }
    }

    private companion object {
        const val MIN_BUILD_SCRIPTS = 3
        const val MIN_ALIASES = 30
        const val MIN_VERSION_KEYS = 20
        const val MIN_ACCESSORS = 40

        val PRE_RELEASE = Regex("""(?i)(alpha|beta|-rc\d|\.rc\d|-dev|-eap|snapshot|-m\d+\b)""")

        /** `libs.foo.bar`, `libs.plugins.foo`, `libs.versions.foo` — un acceso por token. */
        val ACCESSOR = Regex("""\blibs(?:\.[A-Za-z0-9]+)+""")
        val VERSION_REF = Regex("""version\.ref\s*=\s*"([^"]+)"""")

        val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("repo root not found from ${File("").absolutePath}")

        val catalog: File = File(repoRoot, "gradle/libs.versions.toml")

        private val catalogText: String = catalog.takeIf { it.isFile }?.readText().orEmpty()

        /** Las entradas de una sección del TOML, en orden y sin comentarios. */
        private fun section(name: String): List<Pair<String, String>> {
            var inside = false
            val out = mutableListOf<Pair<String, String>>()
            for (raw in catalogText.lineSequence()) {
                val line = raw.trim()
                if (line.startsWith("[")) {
                    inside = line == "[$name]"
                    continue
                }
                if (!inside || line.isEmpty() || line.startsWith("#")) continue
                val key = line.substringBefore('=').trim()
                if (key.isNotEmpty()) out += key to line.substringAfter('=').trim()
            }
            return out
        }

        /** alias → accesor generado por Gradle (`-` y `_` se vuelven `.`). */
        val aliases: List<Pair<String, String>> =
            section("libraries").map { (key, _) -> key to "libs." + key.replace('-', '.').replace('_', '.') } +
                section("plugins").map { (key, _) ->
                    key to "libs.plugins." + key.replace('-', '.').replace('_', '.')
                }

        // El valor es lo que va ENTRE COMILLAS, no el resto de la línea: media docena de claves
        // llevan un comentario de cola (`ksp = "2.3.14"   # Esquema propio…`) y leerlo como parte
        // del número haría que este guard juzgase prosa.
        private val QUOTED = Regex(""""([^"]*)"""")

        val versionValues: List<Pair<String, String>> = section("versions").mapNotNull { (key, value) ->
            QUOTED.find(value)?.groupValues?.get(1)?.let { key to it }
        }

        val versionKeys: List<String> = versionValues.map { it.first }

        val versionRefsInCatalog: Set<String> =
            VERSION_REF.findAll(catalogText).map { it.groupValues[1] }.toSet()

        val buildScripts: List<File> = buildList {
            File(repoRoot, "settings.gradle.kts").takeIf { it.isFile }?.let { add(it) }
            File(repoRoot, "build.gradle.kts").takeIf { it.isFile }?.let { add(it) }
            repoRoot.listFiles()?.sorted()?.forEach { dir ->
                if (dir.isDirectory) File(dir, "build.gradle.kts").takeIf { it.isFile }?.let { add(it) }
            }
        }

        private val tokens: Set<String> = buildScripts
            .flatMap { ACCESSOR.findAll(it.readText()).map { m -> m.value } }
            .toSet()

        /** `libs.versions.android.compileSdk.get` → se le quita la llamada de cola. */
        val accessorsUsed: Set<String> = tokens.map { it.removeSuffix(".get") }.toSet()

        val versionAccessorsUsed: Set<String> = accessorsUsed
            .filter { it.startsWith("libs.versions.") }
            .map { it.removePrefix("libs.versions.") }
            .flatMap { dotted ->
                // `android.compileSdk` puede venir de la clave `android-compileSdk`.
                listOf(dotted, dotted.replace('.', '-'))
            }
            .toSet()
    }
}
