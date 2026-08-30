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

    /** One place strings live. Both are user-visible; both drift the same way. */
    private class StringSurface(val name: String, val root: File) {
        fun path(locale: String) = "$name/$locale/$STRINGS_FILE"

        fun keysOf(locale: String): Set<String> =
            File(root, "$locale/$STRINGS_FILE")
                .readText()
                .let { text -> KEY_REGEX.findAll(text).map { it.groupValues[1] }.toSet() }
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

        fun Set<String>.render() = sorted().joinToString(", ")
    }
}
