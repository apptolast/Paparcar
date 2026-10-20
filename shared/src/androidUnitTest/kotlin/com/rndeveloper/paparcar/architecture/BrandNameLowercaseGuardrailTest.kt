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

    @Test
    fun `the store says the name the app says`() {
        // ⛔ This is the check that was MISSING, and the gap was not random. The two Play HTML
        // files are not templates — they carry their own copy inside — so they fall outside
        // everything that looks like "copy" when you go looking for copy. The lowercase sweep left
        // 28 occurrences there and the suite stayed green; what found it was rendering the banner
        // and LOOKING at it, which is not a method.
        //
        // It is also the same hiding place twice over: `feature.html` is where the mark's own path
        // data used to live by hand, and the listing is where the app name was duplicated until
        // [PLAY-THE-LISTING-MOVES-TO-THE-PUBLISHED-BUILD-001] gave it one home.
        // [BRAND-THE-LOWERCASE-GUARD-MISSED-THE-STORE-001]
        val offenders = storeCopy().filter { read(it).contains(CAPITALISED) }
        assertTrue(
            offenders.isEmpty(),
            buildString {
                appendLine("the store still spells the brand with a capital in:")
                offenders.forEach { appendLine("  $it") }
                appendLine()
                appendLine("This is what a user reads BEFORE installing, so it is the first thing")
                appendLine("the brand says. The paste files are generated:")
                appendLine("  python docs/release/play-listing/tools/make_paste.py")
                appendLine("and the banner is re-rendered with:")
                appendLine("  bash docs/release/play-listing/tools/render_graphics.sh feature")
            },
        )
    }

    // ── plumbing ──────────────────────────────────────────────────────────────

    /**
     * Everything the store shows: the two graphics' own HTML, the LIVE listing and the paste files.
     *
     * ⛔ Which listing is live is not guessed from the file name — `LISTING.md` and
     * `LISTING-i18n.md` read like the current ones and are the RETIRED ones. `published-listing.txt`
     * names it, and that pointer exists because the name used to live in two places
     * [PLAY-THE-LISTING-MOVES-TO-THE-PUBLISHED-BUILD-001]. The retired listings are the record of
     * what was published on the day; rewriting them to match today would be falsifying them.
     */
    private fun storeCopy(): List<String> {
        val live = repoFile(PUBLISHED_POINTER).readLines()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
            ?: fail("$PUBLISHED_POINTER names no listing")
        val pasted = repoFile("$LISTING_DIR/paste").listFiles()
            ?.filter { it.isFile && it.name.endsWith(".txt") }
            ?.map { "$LISTING_DIR/paste/${it.name}" }
            .orEmpty()
        return STORE_GRAPHICS + "$LISTING_DIR/$live" + pasted
    }

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

        const val LISTING_DIR = "docs/release/play-listing"
        const val PUBLISHED_POINTER = "$LISTING_DIR/published-listing.txt"

        /**
         * The two Play graphics. They are NOT templates — background, gradients and their own copy
         * live inside — which is exactly why their text escaped a sweep that went looking for copy.
         */
        val STORE_GRAPHICS = listOf(
            "$LISTING_DIR/assets/feature.html",
            "$LISTING_DIR/assets/screenshot-frame.html",
        )

        val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("repo root not found from ${File("").absolutePath}")
    }
}
