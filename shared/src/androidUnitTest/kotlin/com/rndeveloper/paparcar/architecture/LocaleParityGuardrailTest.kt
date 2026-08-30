package com.rndeveloper.paparcar.architecture

import org.junit.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * Guardrail for the nine-locale rule [I18N-PERMISSIONS-BUTTONS-EXIST-IN-ONE-LOCALE-ONLY-001].
 *
 * `CLAUDE.md` says every new string ships to all 9 locales in the same task. Nothing checked it, and
 * the reason we thought nothing had to: the rule claimed Compose Resources *crashes* on a key missing
 * from the active locale. It does not. Measured in CMP 1.12
 * (`ResourceEnvironment.kt:182-195`, `filterByLocale`):
 *
 *     val withLanguage = filter { item -> item.qualifiers.any { it == language } }
 *     if (withLanguage.isEmpty()) return noLocaleItems   // falls back to `values`, silently
 *
 * So there are two different failures, and only one of them is loud:
 *  1. Key in `values` but missing from a translation → **the screen renders in English**, no error.
 *     `permissions_btn_allow_background` and `permissions_btn_continue` did exactly this in 7 of 9
 *     languages, on the onboarding screen every new user crosses, for 48 days.
 *  2. Key missing from `values` (even if some locale has it) → `noLocaleItems` is empty and
 *     resolution ends in `error("Resource with ID='…' not found")` → **crash** in every locale that
 *     doesn't declare it.
 *
 * A silent bug survives; a loud one gets fixed the same day. That asymmetry is why this test exists:
 * it makes failure 1 as loud as failure 2, at build time.
 *
 * It watches BOTH string surfaces, not just the one that broke: `:shared` Compose Resources (the UI)
 * and `:app` Android resources (notification channels and notification copy, which are just as
 * user-visible and drift the same way).
 */
class LocaleParityGuardrailTest {

    @Test
    fun `every locale declares the same keys as the English base`() {
        val violations = SURFACES.flatMap { surface ->
            val base = surface.keysOf(BASE_LOCALE)
            LOCALE_DIRS
                .filter { it != BASE_LOCALE }
                .mapNotNull { locale ->
                    (base - surface.keysOf(locale)).takeIf { it.isNotEmpty() }
                        ?.let { missing -> "  - ${surface.path(locale)} is missing: ${missing.render()}" }
                }
        }

        assertTrue(
            violations.isEmpty(),
            "[key missing from a locale — the string silently renders in English there] " +
                "${violations.size} file(s):\n${violations.joinToString("\n")}",
        )
    }

    @Test
    fun `no key exists only in a translation, which would crash the other locales`() {
        val violations = SURFACES.flatMap { surface ->
            val base = surface.keysOf(BASE_LOCALE)
            LOCALE_DIRS
                .filter { it != BASE_LOCALE }
                .mapNotNull { locale ->
                    (surface.keysOf(locale) - base).takeIf { it.isNotEmpty() }?.let { orphans ->
                        "  - ${surface.path(locale)} declares, and its `values` does not: ${orphans.render()}"
                    }
                }
        }

        assertTrue(
            violations.isEmpty(),
            "[key absent from the `values` base — resolution finds no item and CRASHES in every " +
                "locale that lacks it] ${violations.size} file(s):\n${violations.joinToString("\n")}",
        )
    }

    // Duplicate keys inside one file are NOT checked here on purpose: the Compose Resources Gradle
    // plugin already fails `convertXmlValueResourcesForCommonMain` with "Duplicated key '…'", before
    // tests even compile. Measured while falsifying this guardrail. A second owner for an invariant
    // that already has one is ceremony, not safety.

    @Test
    fun `each surface holds exactly the nine locales the project ships`() {
        val violations = SURFACES.mapNotNull { surface ->
            // Only folders that actually carry strings: `values-night`, `values-v31` and friends are
            // Android qualifiers with no copy in them.
            val onDisk = surface.root.listFiles()
                .orEmpty()
                .filter { it.isDirectory && File(it, STRINGS_FILE).isFile }
                .map { it.name }
                .toSortedSet()

            "  - ${surface.name}: found ${onDisk.toList()}"
                .takeIf { onDisk != LOCALE_DIRS.toSortedSet() }
        }

        assertTrue(
            violations.isEmpty(),
            "[locale folders drifted from the 9 the project maintains] expected " +
                "${LOCALE_DIRS.sorted()}\n${violations.joinToString("\n")}\n" +
                "Adding a language means adding it to LOCALE_DIRS too, so parity is enforced for it " +
                "from its first day.",
        )
    }

    @Test
    fun `every plural declares the categories its own language can reach`() {
        val violations = SURFACES.flatMap { surface ->
            LOCALE_DIRS.flatMap { locale ->
                val required = REQUIRED_PLURAL_CATEGORIES.getValue(locale)
                surface.pluralsOf(locale).mapNotNull { (name, declared) ->
                    (required - declared).takeIf { it.isNotEmpty() }?.let { missing ->
                        "  - ${surface.path(locale)} · $name declares ${declared.sorted()}, " +
                            "missing ${missing.sorted()}"
                    }
                }
            }
        }

        assertTrue(
            violations.isEmpty(),
            "[plural declares English's categories instead of the language's — the missing ones " +
                "fall back to `other` and read as wrong grammar] ${violations.size} violation(s):\n" +
                violations.joinToString("\n"),
        )
    }

    @Test
    fun `every declared string is read by something`() {
        val violations = SURFACES.flatMap { surface ->
            (surface.keysOf(BASE_LOCALE) - USAGE_ALLOWLIST)
                .filterNot { key -> CALL_SITES.any { it.contains(key) } }
                .sorted()
                .map { key -> "  - ${surface.path(BASE_LOCALE)} · $key" }
        }

        assertTrue(
            violations.isEmpty(),
            "[string declared and never read — it passes every parity check, because a dead key is " +
                "dead in all nine locales, and it taxes every new language] " +
                "${violations.size} violation(s):\n${violations.joinToString("\n")}\n" +
                "Delete it from the 9 files, or wire it up. If it is genuinely referenced in a way " +
                "this sweep cannot see, add it to USAGE_ALLOWLIST **with the reason** — an " +
                "unexplained entry there is the hole this test exists to close.",
        )
    }

    /** One place strings live. Both are user-visible; both drift the same way. */
    private class StringSurface(val name: String, val root: File) {
        fun path(locale: String) = "$name/$locale/$STRINGS_FILE"

        private fun text(locale: String) = File(root, "$locale/$STRINGS_FILE").readText()

        fun keysOf(locale: String): Set<String> =
            KEY_REGEX.findAll(text(locale)).map { it.groupValues[1] }.toSet()

        /** name → declared `quantity` values, for every `<plurals>` block in this locale. */
        fun pluralsOf(locale: String): List<Pair<String, Set<String>>> =
            PLURALS_REGEX.findAll(text(locale)).map { block ->
                block.groupValues[1] to
                    QUANTITY_REGEX.findAll(block.groupValues[2]).map { it.groupValues[1] }.toSet()
            }.toList()
    }

    private companion object {
        const val BASE_LOCALE = "values"
        const val STRINGS_FILE = "strings.xml"

        // The 9 locales of the i18n contract: EN base + ES (P0) + IT/PT/FR (P1) + DE/NL/PL/RO (P2).
        val LOCALE_DIRS = listOf(
            BASE_LOCALE,
            "values-es",
            "values-it",
            "values-pt",
            "values-fr",
            "values-de",
            "values-nl",
            "values-pl",
            "values-ro",
        )

        // Every element type that generates an accessor. `string-array` has no instance today;
        // leaving it out would let a key slip back in through an unwatched door.
        val KEY_REGEX = Regex("""<(?:string|plurals|string-array)\s+name="([^"]+)"""")
        val PLURALS_REGEX = Regex("""<plurals\s+name="([^"]+)">(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL)
        val QUANTITY_REGEX = Regex("""quantity="([^"]+)"""")

        /**
         * CLDR plural categories each language can actually reach, read off the rule lists CMP 1.12
         * ships (`plural/CLDRPluralRuleLists.kt`) — not off intuition, and not off English.
         *
         * A category the language reaches but the file omits is NOT a crash: `loadPluralString`
         * falls back to `PluralCategory.OTHER` (`PluralStringResources.kt`), so the number simply
         * reads with the wrong grammar. Only a plural with no `other` at all throws.
         *
         * `es`/`it`/`pt`/`fr` also declare MANY, deliberately left out here: its rule is
         * `i % 1000000 = 0 … or e != 0..5`, i.e. millions and compact notation. Nothing in this app
         * counts to a million vehicles or spots, so requiring it would only buy duplicated items.
         * The categories below are the ones reachable by real counts.
         */
        val REQUIRED_PLURAL_CATEGORIES = mapOf(
            BASE_LOCALE to setOf("one", "other"),   // en: one = i = 1 and v = 0
            "values-es" to setOf("one", "other"),
            "values-it" to setOf("one", "other"),
            "values-pt" to setOf("one", "other"),   // pt/fr: one also covers 0
            "values-fr" to setOf("one", "other"),
            "values-de" to setOf("one", "other"),
            "values-nl" to setOf("one", "other"),
            // pl: few = i % 10 = 2..4 and i % 100 != 12..14 · many = 0, 5..9, 12..14 …
            "values-pl" to setOf("one", "few", "many", "other"),
            // ro: few = n = 0 or n % 100 = 1..19 — `other` (20+) is the form that needs "de".
            "values-ro" to setOf("one", "few", "other"),
        )

        /**
         * Unit tests run with the working dir at the Gradle module, but that is a default, not a
         * contract — walk up to the repo root instead of hardcoding depth.
         */
        val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("repo root not found from ${File("").absolutePath}")

        val SURFACES = listOf(
            StringSurface(
                name = "shared/src/commonMain/composeResources",
                root = File(repoRoot, "shared/src/commonMain/composeResources"),
            ),
            StringSurface(
                name = "app/src/main/res",
                root = File(repoRoot, "app/src/main/res"),
            ),
        ).onEach {
            check(it.root.isDirectory) { "string surface not found: ${it.root}" }
        }

        /**
         * Everything that can name a string: Kotlin (`Res.string.key` / `R.string.key`) and the XML
         * that is not itself a resource file — `AndroidManifest.xml` references
         * `@string/attribution_detection_label`, so a Kotlin-only sweep would call it dead.
         *
         * Measured before trusting this: the repo has no dynamic resource lookup at all
         * (`allStringResources`, `getIdentifier` — zero hits), so a key can only be named literally.
         * The day that stops being true, this test starts lying and the allowlist starts growing.
         *
         * Test sources are excluded, and that is not tidiness. This very file names
         * `attribution_detection_label` in a comment; while it was swept, deleting the manifest
         * reference left the test GREEN — the guardrail's own prose was keeping the key alive.
         * Caught by falsification. A product string has to be read by product code; being mentioned
         * in a comment is not being used.
         */
        val CALL_SITES: List<String> = listOf("shared/src", "app/src")
            .map { File(repoRoot, it) }
            .flatMap { it.walkTopDown().asIterable() }
            .filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }
            .filterNot { file ->
                val parts = file.invariantSeparatorsPath.split("/")
                file.name == STRINGS_FILE ||
                    "build" in parts ||
                    parts.any { it.endsWith("Test", ignoreCase = true) }
            }
            .map { it.readText() }

        /**
         * Keys that are read in a way the sweep above cannot see. **Empty on purpose** — the 22 keys
         * this test was written for were all genuinely dead and got deleted, and the one that looked
         * dead (`attribution_detection_label`) turned out to be live in the manifest, which is why
         * the sweep reads XML instead of carrying an excuse here.
         * [I18N-A-DEAD-KEY-PASSES-EVERY-PARITY-CHECK-001]
         */
        val USAGE_ALLOWLIST = emptySet<String>()

        fun Set<String>.render() = sorted().joinToString(", ")
    }
}
