package com.rndeveloper.paparcar.architecture

import org.junit.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * Guardrail de [IOS-A-FEED-WITHOUT-FORWARD-SECRECY-IS-REFUSED-001].
 *
 * App Transport Security no se puede ejecutar en un test JVM: el fallo que este ticket arregla
 * ocurre dentro de `NSURLSession`, en un iPhone, y el repo entero puede compilar, pasar sus 3.591
 * tests y seguir sin poder hablar con un servidor. Eso es justo lo que pasó — España sirviendo
 * precios y la app enseñando surtidores sin precio, sin un solo error por ningún lado.
 *
 * Lo que SÍ es comprobable en frío es la relación entre **los hosts que el código llama** y **las
 * excepciones que el plist declara**, que es donde esto se volverá a romper de dos maneras
 * concretas: alguien añade un país cuyo feed tampoco cumple ATS y no se entera hasta el campo, o
 * alguien ensancha la excepción para salir del paso y apaga ATS para todo.
 */
class AtsExceptionsGuardrailTest {

    @Test
    fun `the plist never turns ATS off wholesale`() {
        assertTrue(
            !plist.declares(ARBITRARY_LOADS),
            "[$ARBITRARY_LOADS apaga App Transport Security para TODOS los hosts] " +
                "Seis de los siete a los que habla la app ofrecen ECDHE o TLS 1.3 y no necesitan " +
                "nada. Si un feed nuevo no cumple, va en NSExceptionDomains con el atributo mínimo " +
                "que le falte — y con su fila en ATS_EXCEPTIONS_REQUIRED, aquí abajo.",
        )
    }

    @Test
    fun `every host known to fail ATS carries its exception`() {
        val violations = ATS_EXCEPTIONS_REQUIRED.filterNot { (host, _) -> plist.declares(host) }
            .map { (host, exception) -> "  - $host — ${exception.why}" }

        assertTrue(
            violations.isEmpty(),
            "[host que ATS rechaza y el plist no exceptúa — en iOS la petición no sale, y la capa " +
                "de arriba lo vive como «ese país no publica datos»] ${violations.size}:\n" +
                violations.joinToString("\n"),
        )
    }

    @Test
    fun `no exception outlives the host that needed it`() {
        val sources = SOURCE_ROOTS.flatMap { root ->
            root.walkTopDown().filter { it.isFile && it.extension == "kt" }.map { it.readText() }
        }
        val violations = EXCEPTION_DOMAIN.findAll(plist)
            .map { it.groupValues[1] }
            .filterNot { host -> sources.any { it.contains(host) } }
            .map { "  - $it" }
            .toList()

        assertTrue(
            violations.isEmpty(),
            "[excepción de ATS para un host que el código ya no llama] ${violations.size}:\n" +
                violations.joinToString("\n") +
                "\nUna excepción huérfana es un permiso que nadie vuelve a revisar: bórrala del " +
                "plist cuando se vaya su fuente.",
        )
    }

    @Test
    fun `an exception relaxes only what its row documents`() {
        val declared = RELAXABLE_KEYS.filter { plist.declares(it) }.toSet()
        val allowed = ATS_EXCEPTIONS_REQUIRED.values.flatMap { it.relaxes }.toSet()

        assertTrue(
            declared == allowed,
            "[el plist releva algo que ninguna fila documenta, o documenta algo que no releva] " +
                "plist: $declared · documentado: $allowed\n" +
                "Cada atributo relajado es una garantía de transporte menos. Si hace falta otro, " +
                "se mide primero contra el servidor y se escribe aquí POR QUÉ.",
        )
    }

    private data class Exception(val why: String, val relaxes: List<String>)

    private companion object {

        const val ARBITRARY_LOADS = "NSAllowsArbitraryLoads"
        const val REQUIRES_FORWARD_SECRECY = "NSExceptionRequiresForwardSecrecy"

        /** Todo atributo de `NSExceptionDomains` que rebaja una garantía del transporte. */
        val RELAXABLE_KEYS = listOf(
            REQUIRES_FORWARD_SECRECY,
            "NSExceptionAllowsInsecureHTTPLoads",
            "NSExceptionMinimumTLSVersion",
        )

        /**
         * Los hosts que ATS rechaza, y exactamente qué se les releva.
         *
         * ⛔ Una fila aquí se gana **midiendo el handshake**, no suponiéndolo. La del ministerio se
         * midió el 22-09-2026: `-cipher ECDHE` y `-cipher DHE` devuelven handshake rechazado, y el
         * único cifrado que negocia es `AES256-GCM-SHA384` — RSA plano, sin forward secrecy.
         */
        val ATS_EXCEPTIONS_REQUIRED = mapOf(
            "sedeaplicaciones.minetur.gob.es" to Exception(
                why = "el feed de precios español sólo negocia AES256-GCM-SHA384 (RSA plano): " +
                    "cero suites con forward secrecy, que es lo que ATS exige por defecto",
                relaxes = listOf(REQUIRES_FORWARD_SECRECY),
            ),
        )

        val EXCEPTION_DOMAIN = Regex("""<key>([a-z0-9.-]+\.[a-z]{2,})</key>""")

        val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("repo root not found from ${File("").absolutePath}")

        /**
         * El plist **sin sus comentarios**, que es lo único que Apple lee.
         *
         * ⛔ Sin esto el guard miraba la prosa. Y no es un detalle de limpieza: la primera versión
         * falló nada más escribirse porque el comentario que explica el arreglo *nombra*
         * `NSAllowsArbitraryLoads` para decir que NO se usa. El espejo de ese falso positivo es el
         * falso NEGATIVO que sí habría dolido — un host citado sólo en un comentario contaba como
         * declarado, o sea que este guard habría dado por puesta una excepción inexistente.
         */
        val plist: String = File(repoRoot, "iosApp/iosApp/Info.plist")
            .also { check(it.isFile) { "Info.plist not found: $it" } }
            .readText()
            .replace(Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL), "")

        /** Declarado = está como `<key>` de verdad, no mencionado de pasada. */
        fun String.declares(key: String): Boolean = contains("<key>$key</key>")

        /** Donde viven las URLs: si un host se cita, es porque algo lo llama. */
        val SOURCE_ROOTS = listOf(File(repoRoot, "shared/src"))
            .onEach { check(it.isDirectory) { "source root not found: $it" } }
    }
}
