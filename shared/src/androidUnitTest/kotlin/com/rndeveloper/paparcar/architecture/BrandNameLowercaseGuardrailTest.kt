package com.rndeveloper.paparcar.architecture

import org.junit.Test
import java.io.File
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The brand is written **`paparcar`**, in lowercase, in every text a user reads.
 *
 * Decided by the user on 19-10-2026 together with the imagotype, whose word is drawn in lowercase
 * [BRAND-THE-MARK-GAINS-ITS-WORDMARK-001]. Without this the app would show a lowercase logo and,
 * two centimetres below it, a sentence spelling the same name another way.
 *
 * ⛔ This is exactly the kind of rule that rots on its own: it breaks nothing, it fails no build,
 * and the next person to add a string will type "Paparcar" because that is what looks correct. The
 * project already has that scar — the apostrophe rule was swept to zero, written into CLAUDE.md,
 * and six came back through the notification mirror, because copying from `app/src/main/res` is a
 * reflex [COPY-AN-APOSTROPHE-GUARD-THAT-NEVER-EXISTED-001]. So the mirror is checked here too.
 *
 * ⛔ What this does NOT touch, and the line is *who reads it*: code identifiers, packages,
 * `applicationId`, `PaparcarLogger`, internal comments, `docs/` and CLAUDE.md. The brand is what a
 * user reads; a class name is not brand copy. That is also why it reads the `<string>` VALUES out
 * of the XML rather than the file: the comment above the paywall block legitimately says
 * "Paparcar Plus" in prose about the ticket.
 *
 * ⛔ Konsist-style guards go UP-TO-DATE when no bytecode changed, and none of this is bytecode.
 * Falsify it with
 * `./gradlew :shared:testDebugUnitTest --tests '*BrandNameLowercase*' --rerun-tasks`.
 */
class BrandNameLowercaseGuardrailTest {

    @Test
    fun `no string a user reads spells the brand with a capital`() {
        val offenders = mutableListOf<String>()

        for (dir in LOCALE_DIRS) {
            for (root in STRING_ROOTS) {
                val file = repoFile("$root/$dir/strings.xml")
                if (!file.isFile) continue
                for ((name, value) in stringsOf(file.readText())) {
                    if (value.contains(CAPITALISED)) {
                        offenders += "$root/$dir/strings.xml → $name"
                    }
                }
            }
        }

        assertTrue(
            offenders.isEmpty(),
            buildString {
                appendLine("${offenders.size} string(s) still spell the brand with a capital:")
                offenders.take(20).forEach { appendLine("  $it") }
                if (offenders.size > 20) appendLine("  … and ${offenders.size - 20} more")
                appendLine()
                appendLine("The brand is written `paparcar`. The plan keeps its own capital")
                appendLine("(`paparcar Plus`) — it is a product name, not part of the brand.")
                appendLine("[BRAND-THE-NAME-IS-WRITTEN-IN-LOWERCASE-001]")
            },
        )
    }

    @Test
    fun `the name under the icon is the brand's own spelling`() {
        // What the launcher shows IS the brand, and it is also the name the permission guides send
        // people to look for ("Settings → paparcar → Location"). If these two disagree, the copy
        // points at an entry the system does not show.
        val manifest = read(MANIFEST)
        assertTrue(
            manifest.contains("""android:label="paparcar""""),
            "$MANIFEST does not label the app `paparcar`. That label is the name under the icon " +
                "and the one the permission copy tells people to look for in system Settings.",
        )
    }

    @Test
    fun `the site says the name the app says`() {
        // The SOURCE copy first, because that is where a fix belongs…
        val inSource = WEB_COPY.filter { read(it).contains(CAPITALISED) }
        assertTrue(
            inSource.isEmpty(),
            buildString {
                appendLine("the site copy still spells the brand with a capital in: $inSource")
                appendLine("paparcar.com is a consumer of the product's truth, the brand's own")
                appendLine("spelling included [WEB-THE-SITE-SAYS-ONLY-WHAT-THE-APP-DOES-001].")
                appendLine("Edit web/content/*.json and re-run `node web/build.mjs`.")
            },
        )

        // …and then the PUBLISHED pages, which is the check that actually holds. The site's words
        // do not all come from the copy files: the 404's body, the webmanifest's name, the support
        // mail subject and the whole chrome of the two legal pages are written inside their own
        // modules. Scanning the built HTML catches the text whatever door it came through — and it
        // also catches "edited the copy and never rebuilt", which here is the same defect.
        val built = (htmlUnder("hosting/public") + htmlUnder("hosting/public/en"))
            .filter { it.readText().contains(CAPITALISED) }
            .map { it.name }
        assertTrue(
            built.isEmpty(),
            buildString {
                appendLine("the published pages still spell the brand with a capital: $built")
                appendLine("Something outside web/content writes it — the 404 body, the")
                appendLine("webmanifest, the support subject or restyle-legal's chrome — or the")
                appendLine("copy changed and the site was never rebuilt.")
                appendLine()
                appendLine("  node web/build.mjs && node web/tools/restyle-legal.mjs")
            },
        )
    }

    // ── plumbing ──────────────────────────────────────────────────────────────

    /** `name → value` for every `<string>` in an Android/Compose resources file. */
    private fun stringsOf(xml: String): List<Pair<String, String>> =
        Regex("""<string[^>]*name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml)
            .map { it.groupValues[1] to it.groupValues[2] }
            .toList()

    /** The `.html` files directly inside one published directory, never recursing into assets. */
    private fun htmlUnder(relative: String): List<File> =
        repoFile(relative).listFiles()?.filter { it.isFile && it.name.endsWith(".html") }.orEmpty()

    private fun read(relative: String): String =
        repoFile(relative).takeIf { it.isFile }?.readText()
            ?: fail("not found from ${repoRoot.path}: $relative")

    private fun repoFile(relative: String) = File(repoRoot, relative)

    private companion object {
        /**
         * The brand with a capital, and ONLY when it stands as a word. `\b` on both sides keeps
         * `PaparcarLogger`, `PaparcarType` and every other identifier out — those are code, and
         * code is not brand copy.
         */
        val CAPITALISED = Regex("""\bPaparcar\b(?!\p{L})""")

        val LOCALE_DIRS = listOf(
            "values", "values-es", "values-it", "values-pt", "values-fr",
            "values-de", "values-nl", "values-pl", "values-ro",
        )

        /**
         * Both string trees. `app/src/main/res` is the notification mirror, and it is in here on
         * purpose: it is the exact door the apostrophe rule came back through, because copy gets
         * pasted from one tree to the other.
         */
        val STRING_ROOTS = listOf(
            "shared/src/commonMain/composeResources",
            "app/src/main/res",
        )

        const val MANIFEST = "app/src/main/AndroidManifest.xml"

        /** The site's SOURCE copy. `hosting/public` is build output and follows from these. */
        val WEB_COPY = listOf("web/content/es.json", "web/content/en.json")

        val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("repo root not found from ${File("").absolutePath}")
    }
}
