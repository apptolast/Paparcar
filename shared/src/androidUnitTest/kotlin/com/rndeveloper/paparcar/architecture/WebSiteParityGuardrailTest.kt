package com.rndeveloper.paparcar.architecture

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The website is a CONSUMER of the product's truth, and consumers drift.
 *
 * `paparcar.com` states facts that live in this repository: the Play link is built from the
 * `applicationId`, "Android 8.0 or later" is `minSdk`, the privacy URL is the constant the app
 * itself opens, "the last 7 days" is [com.rndeveloper.paparcar.domain.entitlement.FREE_HISTORY_WINDOW_DAYS].
 * None of that is enforced by anything the compiler sees: the site is plain files, so the day one
 * of those numbers changes the site keeps saying the old one, forever, in two languages, on the
 * page the Play listing links to.
 *
 * This is that enforcement. It is deliberately NOT a copy review — it cannot tell good prose from
 * bad. It checks the handful of statements that are mechanically checkable, which happen to be the
 * ones that are both easy to forget and expensive to get wrong.
 *
 * ## What it compares
 *
 *  1. `web/lib/config.mjs` — the file where the site declares every verifiable fact — against the
 *     sources each fact claims to come from.
 *  2. The two content locales against each other: same key skeleton, the way
 *     [LocaleParityGuardrailTest] does it for the app's nine.
 *  3. The BUILT pages in `hosting/public` against the content they were generated from, so "I
 *     edited the copy and never ran the build" cannot deploy silently.
 *
 * ## What it deliberately does not do
 *
 * It does not check that the copy is *good*, that a new feature got a section, or that a
 * translation is faithful. Those need a person. The checklist for them lives in `web/README.md`
 * and the trigger in `CLAUDE.md`; this class only makes sure the numbers and URLs cannot rot.
 *
 * ⛔ Konsist-style guards go UP-TO-DATE when no bytecode changed, and none of this is bytecode.
 * Falsify it with `./gradlew :shared:testDebugUnitTest --tests '*WebSiteParity*' --rerun-tasks`.
 *
 * [WEB-THE-SITE-SAYS-ONLY-WHAT-THE-APP-DOES-001] [TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001]
 */
class WebSiteParityGuardrailTest {

    // ── 1 · the declared facts against their sources ──────────────────────────

    @Test
    fun `every fact the site declares still matches the repository`() {
        val declared = declaredFacts()

        // The witness: if the file is reorganised and the selector stops matching, this test would
        // otherwise compare an empty map against nothing at all and pass.
        assertTrue(
            declared.size >= FACT_FLOOR,
            "[empty population] parsed ${declared.size} facts out of $SITE_CONFIG, expected at " +
                "least $FACT_FLOOR. The `export const NAME = '…'` shape changed and this guard is " +
                "no longer comparing anything.",
        )

        val applicationId = read("app/build.gradle.kts")
            .firstGroup("""applicationId\s*=\s*"([^"]+)"""")
        val versionName = read("app/build.gradle.kts")
            .firstGroup("""versionName\s*=\s*"([^"]+)"""")
        // ⛔ Not `app/build.gradle.kts`: that file reads `libs.versions.android.minSdk`, so the
        // number itself lives in the version catalogue. (This guard's first run said so.)
        val minSdk = read("gradle/libs.versions.toml")
            .firstGroup("""android-minSdk\s*=\s*"(\d+)"""")
        val privacyUrl = read(SETTINGS_VIEW_MODEL)
            .firstGroup("""PRIVACY_POLICY_URL\s*=\s*"([^"]+)"""")

        val mismatches = buildList {
            check(
                "PLAY_URL", declared["PLAY_URL"], "app/build.gradle.kts · applicationId",
                expected = "…?id=$applicationId",
            ) { it.endsWith("?id=$applicationId") }

            check(
                "APP_VERSION", declared["APP_VERSION"], "app/build.gradle.kts · versionName",
                expected = versionName,
            ) { it == versionName }

            val expectedAndroid = ANDROID_RELEASE_OF_API[minSdk.toInt()]
                ?: fail("minSdk $minSdk has no entry in ANDROID_RELEASE_OF_API — add it here so the site can name it.")
            check(
                "MIN_ANDROID", declared["MIN_ANDROID"], "app/build.gradle.kts · minSdk=$minSdk",
                expected = expectedAndroid,
            ) { it == expectedAndroid }

            val siteprivacy = declared["ORIGIN"].orEmpty() + declared["PRIVACY_PATH"].orEmpty()
            check(
                "ORIGIN + PRIVACY_PATH", siteprivacy, "SettingsViewModel.PRIVACY_POLICY_URL",
                expected = privacyUrl,
            ) { it == privacyUrl }

            val baseStrings = read(BASE_STRINGS)
            check(
                "SUPPORT_EMAIL", declared["SUPPORT_EMAIL"], "$BASE_STRINGS",
                expected = "an address the app itself shows",
            ) { baseStrings.contains(it) }
        }

        assertTrue(
            mismatches.isEmpty(),
            buildString {
                appendLine("The site states something the repository no longer says:")
                mismatches.forEach { appendLine("  $it") }
                appendLine()
                appendLine("Fix `web/lib/config.mjs`, then rebuild and redeploy:")
                appendLine("  node web/build.mjs --check && firebase deploy --only hosting")
            },
        )
    }

    // ── 2 · the two content locales against each other ────────────────────────

    @Test
    fun `both site locales carry the same keys`() {
        val skeletons = LOCALES.associateWith { keyPaths(contentOf(it)) }

        assertTrue(
            skeletons.values.all { it.size >= KEY_FLOOR },
            "[empty population] key counts ${skeletons.mapValues { it.value.size }}, expected at " +
                "least $KEY_FLOOR each. The content files moved or changed shape.",
        )

        val reference = LOCALES.first()
        for (locale in LOCALES.drop(1)) {
            val missing = skeletons.getValue(reference) - skeletons.getValue(locale)
            val extra = skeletons.getValue(locale) - skeletons.getValue(reference)
            assertTrue(
                missing.isEmpty() && extra.isEmpty(),
                buildString {
                    appendLine("web/content/$locale.json is out of step with $reference.json.")
                    if (missing.isNotEmpty()) appendLine("  missing here: ${missing.sorted()}")
                    if (extra.isNotEmpty()) appendLine("  only here:    ${extra.sorted()}")
                    appendLine()
                    appendLine("A key present in one locale and absent in the other is a section")
                    appendLine("that renders in one language and crashes or blanks in the other.")
                },
            )
        }
    }

    /**
     * The free plan the site sells is the free plan the app enforces.
     *
     * The first version of this test looked for a bare `7` somewhere in each content file, and it
     * was worthless: any stray 7 satisfied it, and a copywriter typing "8 days" while leaving a 7
     * elsewhere would have passed. The numbers are now not written in the copy at all — the copy
     * carries `{{historyDays}}`, `{{freeVehicles}}`, `{{freeZones}}` and the build substitutes them
     * — so there is exactly one place to compare, and this is that comparison.
     */
    @Test
    fun `the free plan the site sells is the one the gates enforce`() {
        val declared = declaredLimits()

        assertTrue(
            declared.size >= FREE_LIMITS.size,
            "[empty population] parsed ${declared.keys} out of $SITE_CONFIG's FREE_LIMITS, expected " +
                "${FREE_LIMITS.keys}. The block was renamed and this guard stopped comparing.",
        )

        for ((token, source) in FREE_LIMITS) {
            val (file, constant) = source
            val enforced = read(file).firstGroup("""$constant\s*=\s*(\d+)""")
            assertEquals(
                enforced,
                declared[token],
                "The site's free plan and the app's disagree on '$token': $constant is $enforced in " +
                    "$file, and web/lib/config.mjs says ${declared[token]}. Whichever moved, the " +
                    "other has to move with it — then rebuild and redeploy.",
            )
        }
    }

    /**
     * And the copy has to actually USE the tokens, or the substitution protects nothing: a limit
     * typed back in as a numeral is exactly the drift this whole arrangement exists to prevent.
     */
    @Test
    fun `every locale states the plan limits through the tokens, never as numerals`() {
        for (locale in LOCALES) {
            val text = contentOf(locale)
            val unused = FREE_LIMITS.keys.filterNot { text.contains("{{$it}}") }
            assertTrue(
                unused.isEmpty(),
                "web/content/$locale.json no longer uses $unused. Either that language stopped " +
                    "stating the free plan — check the plan table and the FAQ — or somebody typed " +
                    "the number in by hand, which is the drift the token exists to stop.",
            )
        }
    }

    // ── 3 · the built pages against the content ───────────────────────────────

    @Test
    fun `the deployed pages were built from the copy that is in the repository`() {
        for ((locale, base) in LOCALE_BASES) {
            val page = repoFile("hosting/public$base/index.html")
            assertTrue(page.isFile, "missing built page for '$locale': ${page.path}. Run `node web/build.mjs`.")

            val description = (Json.parseToJsonElement(contentOf(locale)) as JsonObject)
                .stringAt("site", "description")

            assertTrue(
                page.readText().contains(description),
                buildString {
                    appendLine("${page.path} does not carry the description from web/content/$locale.json.")
                    appendLine("The copy was edited and the site was never rebuilt, so what is deployed")
                    appendLine("is the previous wording.")
                    appendLine()
                    appendLine("  node web/build.mjs --check && firebase deploy --only hosting")
                },
            )
        }
    }

    // ── plumbing ──────────────────────────────────────────────────────────────

    /** `export const NAME = 'value'` → map. Only the string constants: those are the facts. */
    private fun declaredFacts(): Map<String, String> =
        Regex("""export const (\w+) = '([^']*)';""")
            .findAll(read(SITE_CONFIG))
            .associate { it.groupValues[1] to it.groupValues[2] }

    /** The numbers inside `export const FREE_LIMITS = { … }`. */
    private fun declaredLimits(): Map<String, String> {
        val block = read(SITE_CONFIG).firstGroup("""export const FREE_LIMITS = \{([^}]*)}""")
        return Regex("""(\w+):\s*(\d+)""").findAll(block)
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    private fun MutableList<String>.check(
        name: String,
        actual: String?,
        source: String,
        expected: String,
        ok: (String) -> Boolean,
    ) {
        if (actual == null) {
            add("$name — not declared in web/lib/config.mjs (source of truth: $source)")
        } else if (!ok(actual)) {
            add("$name — site says '$actual', $source says '$expected'")
        }
    }

    /** Every leaf path in the document, so a renamed or dropped key shows up as a difference. */
    private fun keyPaths(json: String): Set<String> {
        val out = mutableSetOf<String>()
        fun walk(element: JsonElement, path: String) {
            when (element) {
                is JsonObject -> element.forEach { (key, value) -> walk(value, if (path.isEmpty()) key else "$path.$key") }
                // An array contributes its LENGTH to the skeleton and then folds its entries onto
                // one path: a FAQ list may be reordered without that meaning anything, but it may
                // not lose an entry — or an entry's field — in one language only.
                is JsonArray -> {
                    out += "$path[${element.size}]"
                    element.forEach { walk(it, "$path[]") }
                }
                else -> out += path
            }
        }
        walk(Json.parseToJsonElement(json), "")
        return out
    }

    private fun JsonObject.stringAt(vararg path: String): String {
        var node: JsonElement = this
        for (key in path) node = (node as JsonObject)[key] ?: fail("missing key '${path.joinToString(".")}'")
        return node.toString().trim('"')
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
    }

    private fun contentOf(locale: String): String = read("web/content/$locale.json")

    private fun read(relative: String): String =
        repoFile(relative).takeIf { it.isFile }?.readText()
            ?: fail("not found from ${repoRoot.path}: $relative")

    private fun String.firstGroup(pattern: String): String =
        Regex(pattern).find(this)?.groupValues?.get(1)
            ?: fail("pattern /$pattern/ matched nothing — the file it reads was reorganised")

    private fun repoFile(relative: String) = File(repoRoot, relative)

    private companion object {
        val LOCALES = listOf("es", "en")

        /** Locale → the path its home page is built to, mirroring `LOCALES` in `web/lib/config.mjs`. */
        val LOCALE_BASES = listOf("es" to "", "en" to "/en")

        const val SETTINGS_VIEW_MODEL =
            "shared/src/commonMain/kotlin/com/rndeveloper/paparcar/presentation/settings/SettingsViewModel.kt"

        const val ENTITLEMENT = "shared/src/commonMain/kotlin/com/rndeveloper/paparcar/domain/entitlement"

        /** The site's copy token → the gate that decides the same thing in the app. */
        val FREE_LIMITS = mapOf(
            "historyDays" to ("$ENTITLEMENT/HistoryEntitlementGate.kt" to "FREE_HISTORY_WINDOW_DAYS"),
            "freeVehicles" to ("$ENTITLEMENT/VehicleEntitlementGate.kt" to "FREE_OWN_VEHICLE_LIMIT"),
            "freeZones" to ("$ENTITLEMENT/ZoneEntitlementGate.kt" to "FREE_ZONE_LIMIT"),
        )
        const val BASE_STRINGS = "shared/src/commonMain/composeResources/values/strings.xml"
        const val SITE_CONFIG = "web/lib/config.mjs"

        /**
         * `minSdk` is an API level; the site has to name the Android release a person recognises.
         * Only the levels this project could plausibly sit at — an unmapped one fails loudly rather
         * than letting the site print a number nobody uses.
         */
        val ANDROID_RELEASE_OF_API = mapOf(24 to "7.0", 25 to "7.1", 26 to "8.0", 27 to "8.1", 28 to "9", 29 to "10", 30 to "11")

        /** Measured at 7 declared string facts on 17-09-2026; the floor sits at half, as elsewhere. */
        const val FACT_FLOOR = 4

        /** Measured at ~300 leaf keys per locale; half of that is far under normal copy churn. */
        const val KEY_FLOOR = 120

        val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("repo root not found from ${File("").absolutePath}")
    }
}
