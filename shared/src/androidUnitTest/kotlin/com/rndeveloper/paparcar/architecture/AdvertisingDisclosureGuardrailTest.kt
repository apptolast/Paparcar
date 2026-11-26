package com.rndeveloper.paparcar.architecture

import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Si el binario lleva una red publicitaria dentro, lo publicado tiene que decirlo.
 * [MKT-META-APP-EVENTS-COST-THE-NO-ADS-PROMISE-001]
 *
 * ## Por qué existe
 *
 * Hasta la 1.5.0 esta app prometía, en tres superficies publicadas a la vez, que nada de lo tuyo se
 * usaba para publicidad: la política (§3), la web (`trust`) y la ficha de Play. Las tres eran
 * ciertas porque el manifest **borraba** el permiso del identificador de publicidad
 * [ANALYTICS-EVENTS-001]. Meter el SDK de Meta invierte eso de golpe, y el fallo que hay que
 * impedir no es escribir mal una frase: es **que el binario se mueva y las superficies no**. Eso no
 * rompe nada, no sale en ningún log, y deja a la app prometiendo lo contrario de lo que hace —
 * durante meses, en dos idiomas, con una política que es un documento legal.
 *
 * ## Qué comprueba, y qué NO
 *
 * Comprueba **las superficies que describen el PRODUCTO**, que son las que se mueven en la misma
 * tarea que el producto: la política publicada, el copy de la web y su salida construida, y la
 * chuleta de Data Safety. La **ficha de Play no está aquí a propósito**: describe el build
 * PUBLICADO, así que su momento es el corte y no antes — nombrar en la tienda algo que la app
 * todavía no hace es el defecto contrario. De eso se ocupa
 * `check_listing_names_the_ad_network` en `tools/release/cut.py`.
 * [DOCS-THE-STORE-LISTING-IS-A-CONSUMER-TOO-001]
 *
 * Y comprueba dos invariantes del propio binario de los que la política depende palabra por
 * palabra: que el identificador de publicidad **no vuelva a borrarse** del manifest (la política
 * dice que viaja; si no viajara, Meta no atribuiría nada y la frase sobraría), que sigan FUERA
 * las dos APIs de Privacy Sandbox que `facebook-core` fusiona sola y que sirven para segmentar
 * —Topics y Custom Audience—, y que los tres automatismos del SDK sigan apagados en el manifest — porque la promesa *«nada de esto ocurre
 * hasta que aceptas»* la sostiene exactamente ese `AutoInitEnabled=false`: con él a `true`, el
 * ContentProvider del SDK arranca y manda el evento de sesión antes de que corra una línea nuestra.
 *
 * ## Dónde está el testigo
 *
 * La mayor parte de esta regla es POSITIVA — «la superficie tiene que nombrar la red» — y por eso
 * no tiene el punto ciego de las prohibiciones: si el fichero se mueve, se vacía o se renombra, el
 * assert cae en vez de dar por buena una búsqueda sin población. El que sí lo tendría es el único
 * chequeo negativo (la frase retirada de la política), y por eso va acompañado: lo sostiene el
 * `assertTrue` de que la red SÍ se nombra en esa misma mitad del documento. Si algún día la frase
 * se reescribe, el patrón muere sin ruido pero la regla sigue viva por la mitad positiva.
 * [TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001]
 *
 * Konsist no, pero el aviso vale igual: esto no toca bytecode, así que Gradle lo da por UP-TO-DATE.
 * Para falsificarlo de verdad hace falta `--rerun-tasks`.
 */
class AdvertisingDisclosureGuardrailTest {

    @Test
    fun `a build that carries an ad network says so where it is published`() {
        val networks = declaredAdNetworks()

        // El testigo de la población: sin SDK declarado esta regla no tiene nada que exigir, y un
        // verde aquí sería el mismo verde que si todo estuviera bien. Si se retira el SDK a
        // propósito, lo que toca es devolver la promesa a las tres superficies y retirar también
        // este test — no dejarlo vigilando el vacío.
        assertTrue(
            networks.isNotEmpty(),
            "[empty population] ningún SDK publicitario de $AD_SDK_KEYS aparece declarado en " +
                "gradle/libs.versions.toml Y pedido desde un build.gradle.kts. O el catálogo " +
                "cambió de forma —y esta regla ya no comprueba nada— o el SDK se quitó, y " +
                "entonces hay que devolver «sin anuncios» a la política, a la web y a la ficha.",
        )

        val listed = networks.joinToString { it.name }

        // 1 · La política publicada, en sus DOS mitades.
        val policy = repoFile("hosting/public/privacy-policy.html").readText()
        listOf("en", "es").forEach { lang ->
            val half = policySection(policy, lang)
            assertTrue(
                networks.all { net -> net.aliases.any { it in half } },
                "La mitad «$lang» de la política publicada debe nombrar a todas las redes " +
                    "del binario ($listed). El Console apunta a este documento.",
            )
        }
        RETIRED_POLICY_CLAIMS.forEach { claim ->
            assertTrue(
                claim !in policy,
                "La política publicada todavía promete «$claim», y el binario lleva $listed " +
                    "dentro. Esa frase era cierta mientras el manifest borraba el identificador " +
                    "de publicidad; ya no lo borra, y tres nombres de evento viajan a esa red.",
            )
        }

        // 2 · El copy de la web y su salida construida. Los dos: editar el copy y no reconstruir
        //     es desplegar lo anterior. [WEB-THE-SITE-SAYS-ONLY-WHAT-THE-APP-DOES-001]
        val webSurfaces = listOf(
            "web/content/en.json",
            "web/content/es.json",
            "hosting/public/index.html",
            "hosting/public/en/index.html",
        )
        webSurfaces.forEach { path ->
            val text = repoFile(path).readText()
            assertTrue(
                networks.all { net -> net.aliases.any { it in text } },
                "$path debe nombrar a todas las redes del binario ($listed). La web se mueve en la MISMA " +
                    "tarea que él; si el fallo es sólo que falta reconstruir, `node web/build.mjs`.",
            )
        }

        // 3 · La chuleta con la que se rellena el formulario de Data Safety.
        val dataSafety = repoFile("docs/legal/DATA-SAFETY-FORM.md").readText()
        assertTrue(
            networks.all { net -> net.aliases.any { it in dataSafety } } &&
                AD_ID_PERMISSION in dataSafety,
            "docs/legal/DATA-SAFETY-FORM.md debe declarar a todas las redes del binario " +
                "($listed) y nombrar $AD_ID_PERMISSION. Play cruza el permiso del manifest con la declaración de " +
                "Advertising ID y con la fila de Data Safety: si las tres no dicen lo mismo, la " +
                "publicación se para.",
        )
    }

    @Test
    fun `the manifest keeps the facts the published policy depends on`() {
        val manifest = repoFile("app/src/main/AndroidManifest.xml").readText()

        assertTrue(
            !manifest.contains(AD_ID_PERMISSION + "\" tools:node=\"remove\""),
            "El manifest vuelve a borrar $AD_ID_PERMISSION. Sin él Meta no puede atribuir una " +
                "instalación al anuncio que la pagó —que es para lo que entra el SDK— y la " +
                "política publicada estaría declarando un dato que ya no sale del móvil.",
        )

        // Lo que el SDK fusiona SOLO y la política no declara: las dos APIs de Privacy Sandbox
        // que sirven para segmentar (intereses inferidos y remarketing), no para medir. Medidas
        // entrando solas en el manifiesto fusionado el 02-11-2026.
        SEGMENTATION_PERMISSIONS.forEach { permission ->
            assertTrue(
                manifest.contains(permission + "\" tools:node=\"remove\""),
                "El manifest ya no retira `$permission`. `facebook-core` la fusiona sola, y la " +
                    "política publicada declara tres nombres de evento y el identificador de " +
                    "publicidad «y nada más»: dejarla entrar hace que el binario pueda más de lo " +
                    "que el documento dice.",
            )
        }

        SDK_AUTOMATICS.forEach { name ->
            val declared = Regex(
                """<meta-data\s+android:name="$name"\s+android:value="(\w+)"\s*/>""",
            ).find(manifest)
                ?: fail(
                    "El manifest ya no declara `$name`. Sin esa línea el SDK de Meta arranca solo " +
                        "desde su ContentProvider y manda el evento de sesión ANTES del " +
                        "consentimiento, que es justo lo contrario de lo que promete la política " +
                        "(§2.10: «nada de esto ocurre hasta que aceptas»).",
                )
            assertTrue(
                declared.groupValues[1] == "false",
                "`$name` está a «${declared.groupValues[1]}» en el manifest. Tiene que arrancar " +
                    "en `false`: lo enciende `MetaAdAttribution.activate`, con el consentimiento " +
                    "legal ya aceptado.",
            )
        }
    }

    /**
     * TikTok se suma al binario con un gate DISTINTO al de Meta, y por eso necesita su PROPIO
     * testigo. [MKT-TIKTOK-APP-EVENTS-SDK-001]
     *
     * Meta puede inicializarse MUDO (`AutoInitEnabled=false` en el manifest) y des-mutear después
     * — por eso el test de arriba vigila una bandera. El SDK de TikTok no tiene esa bandera: su
     * `disableAdvertiserIDCollection()` sólo se decide en el momento de `initializeSdk`, sin
     * `enable` posterior, así que la única forma de no mandar nada antes del consentimiento es que
     * esa llamada viva en UN sitio, guardado por quien ya tiene el consentimiento en la mano
     * (`TikTokAdAttribution.activate`). Esto NO depende de [declaredAdNetworks] ni exige que la
     * política/web/Data Safety ya nombren a TikTok — es un invariante del BINARIO, independiente
     * de si las superficies publicadas ya están al día (ese reparto es deliberado, ver
     * `docs/backlog/mkt-tiktok-app-events-sdk-001.md`).
     */
    @Test
    fun `TikTokBusinessSdk initializeSdk has exactly one call site, and it is the gate`() {
        // SOLO código de producción: androidUnitTest se excluye a propósito, porque ESTE mismo
        // fichero nombra el literal que busca y se casaría a sí mismo.
        val hits = mutableListOf<String>()
        listOf("shared/src/androidMain", "shared/src/commonMain").forEach { sourceSet ->
            repoRoot.resolve(sourceSet).walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    if ("TikTokBusinessSdk.initializeSdk(" in file.readText()) {
                        hits += file.relativeTo(repoRoot).path.replace("\\", "/")
                    }
                }
        }
        assertTrue(
            hits.isNotEmpty(),
            "[empty population] `TikTokBusinessSdk.initializeSdk(` no aparece en ningún fichero " +
                "de producción bajo shared/src/{androidMain,commonMain}. O el SDK se quitó —y hay " +
                "que retirar este test con él— o la llamada se renombró y esta regla ya no mira " +
                "nada.",
        )
        assertEquals(
            listOf("shared/src/androidMain/kotlin/com/rndeveloper/paparcar/data/analytics/TikTokAdAttribution.kt"),
            hits,
            "`TikTokBusinessSdk.initializeSdk(` aparece fuera de TikTokAdAttribution.kt " +
                "($hits). Esa es la ÚNICA llamada que puede inicializar el SDK de verdad — un " +
                "segundo sitio es un segundo lugar que puede mandar el identificador de " +
                "publicidad y el evento de instalación antes de que haya consentimiento.",
        )
    }

    private data class AdNetwork(val key: String, val name: String, val aliases: List<String>)

    /** Las redes que este árbol mete DE VERDAD en el binario: declaradas y además pedidas. */
    private fun declaredAdNetworks(): List<AdNetwork> {
        val catalog = repoFile("gradle/libs.versions.toml").readText()
        val builds = repoRoot.listFiles().orEmpty()
            .mapNotNull { File(it, "build.gradle.kts").takeIf { f -> f.isFile } }
            .joinToString("\n") { it.readText() }
        return AD_SDKS.filter { net ->
            "${net.key} = {" in catalog && "libs.${net.key.replace('-', '.')}" in builds
        }
    }

    private fun policySection(html: String, id: String): String =
        Regex("""<section id="$id"[^>]*>(.*?)</section>""", RegexOption.DOT_MATCHES_ALL)
            .find(html)
            ?.groupValues
            ?.get(1)
            ?: fail("No se encontró <section id=\"$id\"> en la política publicada.")

    private val repoRoot: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("no se encontró la raíz del repo desde ${File("").absolutePath}")
    }

    private fun repoFile(path: String): File =
        File(repoRoot, path).takeIf { it.isFile } ?: fail("falta $path en el repo")

    private companion object {
        const val AD_ID_PERMISSION = "com.google.android.gms.permission.AD_ID"

        /** Las claves del catálogo que meten una red publicitaria en el binario. */
        val AD_SDKS = listOf(
            AdNetwork(
                key = "facebook-core",
                name = "Meta",
                aliases = listOf("Meta", "Facebook", "Instagram"),
            ),
            AdNetwork(
                key = "tiktok-business-sdk",
                name = "TikTok",
                aliases = listOf("TikTok"),
            ),
        )
        val AD_SDK_KEYS = AD_SDKS.joinToString { it.key }

        /**
         * La promesa ABSOLUTA de la política, retirada el 02-11-2026 — las dos mitades, palabra por
         * palabra como se publicaron.
         *
         * ⛔ Lo retirado es *«no usamos tus datos para publicidad»*, **no** *«no hay anuncios»*:
         * esa segunda sigue siendo cierta y sigue publicada. Paparcar no muestra ni un anuncio
         * dentro; lo que hay es medición de los anuncios que ponemos fuera. Prohibir la frase
         * equivocada habría obligado a borrar algo verdadero de la web y de la ficha.
         */
        val RETIRED_POLICY_CLAIMS = listOf(
            "We do not use your data for\n  advertising or share it with third parties for commercial purposes.",
            "No usamos tus datos para\n  publicidad ni los cedemos a terceros con fines comerciales.",
        )

        /** Las que `facebook-core` fusiona sola y Paparcar no usa: segmentar, no medir. */
        val SEGMENTATION_PERMISSIONS = listOf(
            "android.permission.ACCESS_ADSERVICES_TOPICS",
            "android.permission.ACCESS_ADSERVICES_CUSTOM_AUDIENCE",
        )

        /** Los tres automatismos del SDK que el manifest tiene que dejar apagados. */
        val SDK_AUTOMATICS = listOf(
            "com.facebook.sdk.AutoInitEnabled",
            "com.facebook.sdk.AutoLogAppEventsEnabled",
            "com.facebook.sdk.AdvertiserIDCollectionEnabled",
        )
    }
}
