package com.rndeveloper.paparcar.architecture

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The website is a CONSUMER of the product's truth, and consumers drift.
 *
 * `paparcar.com` states facts that live in this repository: the Play link is built from the
 * `applicationId` — including the `/descargar` shortcut in `firebase.json`'s `hosting.redirects`,
 * a second, independent copy of the same URL — "Android 8.0 or later" is `minSdk`, the privacy
 * URL is the constant the app itself opens, "the last 7 days" is
 * [com.rndeveloper.paparcar.domain.entitlement.FREE_HISTORY_WINDOW_DAYS].
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
 *  4. The MARK the site ships against the parametric master it is generated from — including
 *     that the social card does not borrow another surface's icon, which is how the 2022 logo
 *     stayed baked inside `og-card.jpg`.
 *  5. That every file the pages load is content-hashed, so no redeploy can be hidden by a cache —
 *     the favicon was the one asset outside that rule, and tabs kept the 2022 mark because of it.
 *  6. That each published asset is named after the hash of its OWN bytes, which stopped being true
 *     whenever the build ran from a checkout with different line endings.
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
 * [WEB-A-DOWNLOAD-LINK-NEEDS-A-ROUTE-001]
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

            val downloadDestination = downloadShortcuts()[DOWNLOAD_PATH]
            // ⛔ Not `endsWith("?id=…")` any more: the destination now carries `&referrer=` behind
            // the id [MKT-AN-INSTALL-MUST-SAY-WHERE-IT-CAME-FROM-001]. Pinning the PREFIX against
            // `PLAY_URL` is stricter than the old tail check, not looser — it ties the two copies of
            // the URL together instead of checking each one against the id separately. What the
            // referrer itself must say is the next test's job.
            check(
                "firebase.json · $DOWNLOAD_PATH redirect", downloadDestination,
                "$SITE_CONFIG · PLAY_URL (which this test already pins to the applicationId)",
                expected = "${declared["PLAY_URL"]}&referrer=…",
            ) { it.startsWith(declared["PLAY_URL"].orEmpty() + "&referrer=") }
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

    // ── 1b · the download shortcuts carry where the click came from ───────────

    /**
     * `/descargar` and its siblings are the only bridge between a post and an install, and the
     * `referrer` parameter is the only thing that survives the hop into the store: a UTM-tagged bio
     * link loses its query there, and a 301 to a fixed URL drops the incoming one too. Without it
     * Play files the install as organic and GA4 as `(direct)`, so nobody can tell whether the
     * account brought one install or three hundred.
     *
     * ⛔ **And every way of getting it wrong is SILENT.** A missing `referrer`, a `utm_campaign` that
     * names another territory, or a value written with literal `&` instead of `%26` — which Play
     * splits into separate parameters of its own — all deploy clean, open the store, and lose the
     * attribution without one error anywhere. That is exactly the shape of defect a guard is for;
     * the alternative is finding out a month and a budget later.
     *
     * [MKT-AN-INSTALL-MUST-SAY-WHERE-IT-CAME-FROM-001]
     */
    @Test
    fun `every download shortcut carries an encoded install referrer naming its own campaign`() {
        val shortcuts = downloadShortcuts()

        // The witness: if the redirect block is reorganised and the selector stops matching, this
        // test would otherwise iterate an empty map and pass while the attribution is gone.
        assertTrue(
            shortcuts.containsKey(DOWNLOAD_PATH),
            "[empty population] firebase.json has no redirect whose source is exactly " +
                "'$DOWNLOAD_PATH' — found ${shortcuts.keys}. Either the shortcut was removed (and " +
                "the bio of @paparcar_oficial now points at nothing) or `hosting.redirects` moved " +
                "and this guard is no longer looking at anything.",
        )

        val problems = buildList {
            shortcuts.forEach { (source, destination) ->
                val expectedCampaign =
                    if (source == DOWNLOAD_PATH) BIO_CAMPAIGN else source.removePrefix("$DOWNLOAD_PATH/")

                val encoded = destination.substringAfter("&referrer=", missingDelimiterValue = "")
                if (encoded.isEmpty()) {
                    add("$source · no `&referrer=` at all → this install will be filed as organic")
                    return@forEach
                }
                // Percent-encoded or nothing: a raw `&` or `=` here means Play reads the pieces as
                // its own parameters and the referrer arrives truncated.
                if (encoded.any { it == '&' || it == '=' }) {
                    add(
                        "$source · referrer is NOT percent-encoded ('$encoded'). Write %3D for '=' " +
                            "and %26 for '&', or Play splits it into parameters of its own.",
                    )
                    return@forEach
                }

                val utm = encoded.replace("%3D", "=").replace("%26", "&")
                    .split("&")
                    .mapNotNull { pair ->
                        pair.split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }
                    }
                    .toMap()

                REQUIRED_UTM.filterNot(utm::containsKey).forEach {
                    add("$source · referrer has no `$it`")
                }
                utm["utm_source"]?.takeIf { it != UTM_SOURCE }?.let {
                    add("$source · utm_source is '$it', expected '$UTM_SOURCE'")
                }
                utm["utm_campaign"]?.takeIf { it != expectedCampaign }?.let {
                    add(
                        "$source · utm_campaign is '$it' but the path says '$expectedCampaign'. A " +
                            "campaign that names another territory makes the acquisition report lie.",
                    )
                }
                // Earned and bought must stay separable, or the spend cannot be divided by anything.
                val medium = utm["utm_medium"]
                val looksPaid = expectedCampaign.startsWith(PAID_SLUG_PREFIX)
                if (medium != null && medium !in UTM_MEDIUMS) {
                    add("$source · utm_medium is '$medium', expected one of $UTM_MEDIUMS")
                } else if (medium != null && (medium == PAID_MEDIUM) != looksPaid) {
                    add(
                        "$source · utm_medium='$medium' and the slug '$expectedCampaign' disagree: a " +
                            "bought zone is named '$PAID_SLUG_PREFIX…' and carries '$PAID_MEDIUM'. " +
                            "Mixing them merges bought installs into the earned ones.",
                    )
                }
            }
        }

        assertTrue(
            problems.isEmpty(),
            buildString {
                appendLine("A download shortcut cannot say where its install came from:")
                problems.forEach { appendLine("  $it") }
                appendLine()
                appendLine("Each `$DOWNLOAD_PATH*` redirect in firebase.json needs a destination like:")
                appendLine(
                    "  …details?id=<applicationId>&referrer=utm_source%3D$UTM_SOURCE" +
                        "%26utm_medium%3Dsocial%26utm_campaign%3D<the path's own slug>",
                )
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


    // ── 6 · the mark the site ships is the generated one ─────────────────────

    /**
     * The logo is GENERATED, not copied — and the site is where that rule has broken twice.
     *
     * Once in `icons.mjs`, which carried the three path strings inline under a note asking the
     * next reader to remember to regenerate them elsewhere. And once, far more quietly, inside
     * `og-card.jpg`: `prepare_social()` pasted `play-icon-512.png` — the icon of ANOTHER surface,
     * an opaque tile — so the 2022 mark kept living inside a JPEG while every other surface had
     * moved on. No `grep` finds a logo inside an image, and nothing here can look inside one.
     *
     * So this checks the MECHANISM rather than the pixels, which is the part that is mechanically
     * knowable: the drawing comes from the master, the built pages carry what the generator wrote,
     * and the social card does not borrow another surface's icon.
     *
     * ⛔ Since the imagotype there are TWO masters to agree with, and the word is the one to watch:
     * the symbol can always be re-derived from parameters, the word cannot — it is an outlined
     * deliverable — which makes it exactly the kind of thing that gets pasted into a template "just
     * this once". The site is also where the pair used to be assembled by hand, as `logoMark()`
     * plus a `<span class="brand__name">`.
     *
     * [WEB-THE-MARK-WEARS-ITS-DISC-ON-THE-SITE-001] [BRAND-THE-MARK-GAINS-ITS-WORDMARK-001]
     */
    @Test
    fun `the mark the site ships comes from the master, not from a copy`() {
        // a · the generated module still agrees with the master drawing
        val masterPaths = Regex("""<path d="([^"]+)"/>""")
            .findAll(read(MARK_MASTER)).map { it.groupValues[1] }.toList()
        assertEquals(3, masterPaths.size, "$MARK_MASTER should hold the three paths of the mark")

        val generated = read(MARK_GENERATED)
        for (path in masterPaths) {
            assertTrue(
                generated.contains(path),
                buildString {
                    appendLine("$MARK_GENERATED does not carry a path that is in $MARK_MASTER.")
                    appendLine("The master drawing moved and the exporter was never re-run, so the")
                    appendLine("site draws the previous mark.")
                    appendLine()
                    appendLine("  cd docs/branding/master && python export_surfaces.py")
                    appendLine("  node web/build.mjs")
                },
            )
        }

        // b · nobody pasted the BRAND drawing back into the hand-written module. Its other
        //     icons legitimately carry path data — only the mark's own three are forbidden here.
        val icons = read(SITE_ICONS)
        for (path in masterPaths) {
            assertTrue(
                !icons.contains(path),
                "$SITE_ICONS writes the mark's geometry inline. It arrives from $MARK_GENERATED; " +
                    "pasting it here is the copy the parametric master exists to kill.",
            )
        }

        // b-bis · and neither did anyone paste the WORD back in. It is the half that arrived
        //         last and the one with no parametric source to fall back on, so it is the
        //         likeliest to get copied into a template "just this once".
        val wordPaths = Regex("""<path d="([^"]+)"/>""")
            .findAll(read(WORD_MASTER)).map { it.groupValues[1] }.toList()
        assertEquals(8, wordPaths.size, "$WORD_MASTER should hold the eight glyphs of the word")
        for (path in wordPaths) {
            assertTrue(
                generated.contains(path),
                "$MARK_GENERATED is missing a glyph that is in $WORD_MASTER — re-run " +
                    "`python wordmark.py` and then the exporter.",
            )
            assertTrue(
                !icons.contains(path),
                "$SITE_ICONS writes the word's geometry inline. It arrives from $MARK_GENERATED.",
            )
        }

        // c · the built pages carry what the generator wrote — BOTH halves of the imagotype
        val markTransform = generated.firstGroup("""LOCKUP_MARK_TRANSFORM = '([^']+)'""")
        val wordTransform = generated.firstGroup("""LOCKUP_WORD_TRANSFORM = '([^']+)'""")
        for ((locale, base) in LOCALE_BASES) {
            val page = read("hosting/public$base/index.html")
            assertTrue(
                page.contains(markTransform) && page.contains(wordTransform),
                buildString {
                    appendLine("the built page for '$locale' does not carry the generated imagotype.")
                    appendLine("Either the drawing changed and the site was never rebuilt, or the")
                    appendLine("header went back to a mark plus a <span> with the name beside it.")
                    appendLine("The site wears the imagotype on its header, its footer and both")
                    appendLine("legal pages — it is one drawing, not two pieces.")
                    appendLine()
                    appendLine("  node web/build.mjs --check")
                },
            )
        }

        // d · the social card draws the generated imagotype, never another surface's icon.
        //     ⛔ The count of who may OPEN `play-icon-512.png` moved to BrandSurfaceGuardrailTest
        //     when the touch icon stopped being built here
        //     [BRAND-THE-APPLE-SURFACES-ARE-OUTSIDE-THE-MASTER-001]: that is a question about brand
        //     surfaces, not about the website. What belongs here is the card itself.
        val assets = read(PREPARE_ASSETS)
        assertTrue(
            assets.contains(LOCKUP_RASTER),
            buildString {
                appendLine("$PREPARE_ASSETS no longer builds the social card from the generated")
                appendLine("imagotype ($LOCKUP_RASTER). Borrowing another surface's icon is how the")
                appendLine("2022 mark stayed baked inside og-card.jpg for weeks after every other")
                appendLine("surface had moved on, where no grep could see it.")
            },
        )
    }


    // ── 7 · nothing the pages load can go stale ───────────────────────────────

    /**
     * Every file a page loads is content-hashed, so a changed file is a different URL and a cached
     * copy can never be the wrong one. That is the promise `firebase.json` makes when it marks
     * everything under `assets` as `immutable`, and the favicon was quietly outside it.
     *
     * (That path is written without its glob on purpose: a `slash-star-star` inside a block comment
     * opens a NESTED comment in Kotlin, and the file never closes again.)
     *
     * It sat at a fixed `/favicon.svg` — the ONE visual asset whose URL did not move when its
     * drawing did. So when the 2026 mark landed, tabs kept painting the 2022 one, and no redeploy
     * could fix it [WEB-THE-FAVICON-IS-THE-ONE-ASSET-THAT-CAN-GO-STALE-001].
     *
     * Three doorways cannot be hashed, because the browser and the manifest spec ask for them by a
     * fixed path: `/favicon.ico`, `/favicon.svg` and `/site.webmanifest`. They are allowed here by
     * name, they must exist, and `firebase.json` has to make them revalidate — otherwise they
     * inherit Hosting's default hour and the same bug comes back through the door that is left open.
     */
    @Test
    fun `every file the pages load is content-hashed, and the doorways revalidate`() {
        val pages = LOCALE_BASES.map { (_, base) -> "hosting/public$base/index.html" } +
            listOf("hosting/public/privacy-policy.html", "hosting/public/delete-account.html")

        for (page in pages) {
            // ⛔ Dos trampas de literal, las dos pisadas al escribir esto:
            //    · un raw string NO vale: termina en `"` y pegado a `"""` son cuatro comillas.
            //    · `\.` es un escape ILEGAL en un string normal de Kotlin. Se usa `[.]`, que
            //      dice lo mismo sin un solo backslash.
            val refs = Regex("(?:href|src)=\"(/[^\"]+[.][a-z0-9]+)\"")
                .findAll(read(page)).map { it.groupValues[1] }.distinct()
            for (ref in refs) {
                if (ref in LINKABLE_UNHASHED) continue
                assertTrue(
                    HASHED.containsMatchIn(ref),
                    buildString {
                        appendLine("$page loads $ref, which carries no content hash.")
                        appendLine("A file served at a fixed URL keeps showing its previous content")
                        appendLine("to anyone who has it cached — that is how the tab icon kept")
                        appendLine("drawing the 2022 mark after the 2026 one shipped.")
                        appendLine()
                        appendLine("Publish it from web/assets/ so publishAssets() hashes it, and")
                        appendLine("reference it through asset().")
                    },
                )
            }
        }

        for (doorway in DOORWAYS) {
            assertTrue(
                repoFile("hosting/public$doorway").isFile,
                "missing $doorway — the browser asks for it by that exact path.",
            )
        }

        // ⛔ Cada puerta necesita su PROPIA regla, y se comprueba PARSEANDO el fichero.
        //    La primera version de esto miraba solo que `firebase.json` contuviera los tres
        //    nombres y la cadena `must-revalidate` — y una sola regla con
        //    `"source": "@(/favicon.ico|/favicon.svg|/site.webmanifest)"` cumple las dos cosas
        //    SIN CASAR CON NADA. Se desplego limpia, las tres rutas siguieron con el
        //    max-age=3600 por defecto, y lo unico que lo enseño fue leer la cabecera de la URL
        //    en vivo [WEB-A-HOSTING-HEADER-RULE-MUST-ACTUALLY-MATCH-001].
        //    Un testigo que comprueba que un texto ESTA ESCRITO no comprueba que HAGA algo.
        val headerRules = (Json.parseToJsonElement(read("firebase.json")) as JsonObject)
            .let { it["hosting"] as JsonObject }
            .let { it["headers"] as JsonArray }
            .map { it as JsonObject }

        for (doorway in DOORWAYS) {
            val rule = assertNotNull(
                headerRules.firstOrNull { (it["source"] as? JsonPrimitive)?.content == doorway },
                buildString {
                    appendLine("firebase.json has no rule whose source is exactly $doorway.")
                    appendLine("A path that matches no source inherits Hosting's default")
                    appendLine("max-age=3600, which hides a redeploy for an hour.")
                    appendLine()
                    appendLine("One rule per path: an @(a|b|c) alternation of full paths deploys")
                    appendLine("clean and matches nothing — measured in production, 30-09-2026.")
                },
            )
            val cacheControl = (rule["headers"] as JsonArray)
                .map { it as JsonObject }
                .firstOrNull { (it["key"] as? JsonPrimitive)?.content == "Cache-Control" }
                ?.get("value") as? JsonPrimitive
            assertTrue(
                cacheControl?.content?.contains("must-revalidate") == true,
                "the rule for $doorway does not make it revalidate: ${cacheControl?.content}",
            )
        }
    }


    // ── 8 · a published asset is named after its own bytes ─────────────────

    /**
     * `publishAssets()` names every asset `name.<sha256[0:8]>.ext`. That name is the ONLY reason a
     * long cache is safe: a changed file is a different URL. So the name has to actually BE the
     * hash of the bytes next to it — and for a while it was not.
     *
     * The build hashed whatever was on disk, so a text file's name depended on the line endings of
     * whoever ran it. Measured with one `legal.css`: 4895 bytes with CRLF in the main worktree,
     * 4755 with LF in the index and in any fresh worktree — same CSS, two names. Building from the
     * main worktree renamed an asset nobody had touched, twice, and the second time it reached a
     * commit [WEB-A-BUILD-MUST-NOT-DEPEND-ON-THE-CHECKOUT-001].
     *
     * Reading the hash back off the file is the cheapest check there is, and it catches both that
     * and any rename by hand. `manifest.json` is the one published file that is deliberately NOT
     * hashed — `firebase.json` carries its own note saying it must never be pinned.
     */
    @Test
    fun `every published asset is named after the hash of its own bytes`() {
        val root = repoFile(PUBLISHED_ASSETS)
        assertTrue(root.isDirectory, "missing $PUBLISHED_ASSETS — run `node web/build.mjs`.")

        val files = root.walkTopDown().filter { it.isFile && it.name != "manifest.json" }.toList()
        assertTrue(
            files.size >= ASSET_FLOOR,
            "only ${files.size} published assets; expected at least $ASSET_FLOOR. Did the build run?",
        )

        for (file in files) {
            val named = assertNotNull(
                HASHED_NAME.find(file.name),
                "${file.name} carries no content hash. Everything under $PUBLISHED_ASSETS is " +
                    "published by publishAssets(), which names files after their bytes.",
            )
            val actual = MessageDigest.getInstance("SHA-256")
                .digest(file.readBytes())
                .joinToString("") { "%02x".format(it) }
                .take(8)
            assertEquals(
                named.groupValues[1], actual,
                buildString {
                    appendLine("${file.name} is not named after its own bytes (they hash to $actual).")
                    appendLine()
                    appendLine("Either it was renamed by hand, or it was built from a checkout whose")
                    appendLine("line endings differ — the build normalises text to LF before hashing")
                    appendLine("precisely so that cannot happen.")
                    appendLine()
                    appendLine("  node web/build.mjs --check")
                },
            )
        }
    }

    /**
     * **Un concepto del producto se dibuja igual dentro y fuera de la app.**
     * [ICON-A-CONCEPT-KEEPS-ONE-GLYPH-ON-EVERY-SURFACE-001]
     *
     * `web/lib/icons.mjs` llegó a declarar a mano su propio `spot` (un pin hueco) y su propio
     * `parking` (una P sobre un cuadrado) y los sirvió durante meses, en dos idiomas — los dos
     * RETIRADOS de la app hacía tiempo [SPOT-A-FREE-SPOT-IS-A-GAP-NOT-A-GIFT-001]
     * [UI-THE-CAR-PARK-WEARS-OUR-OWN-P-001]. Ningún `grep` podía verlo: eran dos dibujos distintos
     * del mismo concepto, cada uno correcto en su fichero.
     *
     * Tres preguntas, porque son tres formas distintas de romperlo:
     *  1. que la web vuelva a DECLARAR un glifo de concepto en vez de consumirlo;
     *  2. que alguien edite el módulo generado a mano, o cambie el dibujo en la app y no lo
     *     regenere — se compara contra las constantes de `PaparcarIcons.kt`, que es la fuente;
     *  3. que se regenere y no se reconstruya, que deja el sitio publicado con lo de antes.
     */
    @Test
    fun `the site draws a product concept with the app's own glyph`() {
        val icons = read(SITE_ICONS)
        val generated = read(CONCEPT_GLYPHS_GENERATED)

        // 1 · la web consume, no redibuja.
        val redeclared = CONCEPT_KEYS.filter { key ->
            Regex("""^\s{2}$key:""", RegexOption.MULTILINE).containsMatchIn(icons)
        }
        assertTrue(
            redeclared.isEmpty(),
            "$SITE_ICONS vuelve a declarar un glifo de concepto: ${redeclared.joinToString()}. " +
                "Un concepto lo dibuja la app y la web lo importa de $CONCEPT_GLYPHS_GENERATED — " +
                "declararlo aquí es como el sitio acabó sirviendo una plaza y un parking que la " +
                "app había retirado. Regenera con `python docs/branding/icons/export_icons.py`.",
        )

        // 2 · lo generado es lo que dibuja la app, y nuestras dos marcas se pueden comparar
        //     contra su fuente: viven como constantes en PaparcarIcons.kt.
        val source = read(PAPARCAR_ICONS)
        for ((key, konstant) in OWN_MARK_SOURCES) {
            val expected = source.kotlinStringConst(konstant)
            assertTrue(
                generated.contains("d: '$expected'"),
                buildString {
                    appendLine("el glifo `$key` de la web no es el `$konstant` de la app.")
                    appendLine()
                    appendLine("O se editó el módulo generado a mano, o cambió el dibujo en")
                    appendLine("$PAPARCAR_ICONS y nadie volvió a correr el exportador:")
                    appendLine()
                    appendLine("  python docs/branding/icons/export_icons.py")
                    appendLine("  node web/build.mjs")
                },
            )
        }

        // 3 · y el sitio PUBLICADO lo lleva: regenerar sin reconstruir despliega lo de antes.
        val shipped = Regex("""^\s{4}\{ d: '([^']+)'""", RegexOption.MULTILINE)
            .findAll(generated).map { it.groupValues[1] }.toList()
        assertTrue(shipped.size >= CONCEPT_KEYS.size, "$CONCEPT_GLYPHS_GENERATED quedó vacío")

        // Sólo los conceptos que el copy nombra hoy: el módulo lleva los seis para que la web no
        // pueda inventarse uno, pero exigir en la página los que no pinta sería decoración.
        val es = contentOf("es")
        val used = CONCEPT_KEYS.filter { key -> "\"icon\": \"$key\"" in es }
        assertTrue(used.isNotEmpty(), "ningún concepto sale ya en web/content/es.json — ¿se renombró la clave?")

        for ((_, base) in LOCALE_BASES) {
            val page = read("hosting/public$base/index.html")
            for (key in used) {
                val first = Regex("""^  $key: \[\n\s{4}\{ d: '([^']+)'""", RegexOption.MULTILINE)
                    .find(generated)?.groupValues?.get(1)
                    ?: fail("$key no está en $CONCEPT_GLYPHS_GENERATED")
                assertTrue(
                    first in page,
                    "hosting/public$base/index.html no sirve el glifo `$key` que dice el módulo " +
                        "generado. Se regeneró sin reconstruir: `node web/build.mjs`.",
                )
            }
        }
    }

    // ── plumbing ──────────────────────────────────────────────────────────────

    /** `X = "a" + "b"` → `ab`. Las constantes de path se parten en varias líneas. */
    private fun String.kotlinStringConst(name: String): String {
        val decl = firstGroup("""\b$name\b\s*(?::\s*String\s*)?=\s*((?:\s*"[^"]*"\s*\+?)+)""")
        return Regex("\"([^\"]*)\"").findAll(decl).joinToString("") { it.groupValues[1] }
    }

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

    /**
     * `source → destination` for every `/descargar…` redirect declared in `firebase.json`.
     * One reader for the two tests that need them, so a reorganisation of that block can only
     * break the selector in one place. [MKT-AN-INSTALL-MUST-SAY-WHERE-IT-CAME-FROM-001]
     */
    private fun downloadShortcuts(): Map<String, String> =
        ((Json.parseToJsonElement(read("firebase.json")) as JsonObject)["hosting"] as JsonObject)
            .let { it["redirects"] as? JsonArray }
            ?.map { it as JsonObject }
            ?.mapNotNull { rule ->
                (rule["source"] as? JsonPrimitive)?.content
                    ?.takeIf { it.startsWith(DOWNLOAD_PATH) }
                    ?.let { it to (rule["destination"] as? JsonPrimitive)?.content.orEmpty() }
            }
            ?.toMap()
            .orEmpty()

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

        // ── the download shortcuts [MKT-AN-INSTALL-MUST-SAY-WHERE-IT-CAME-FROM-001] ──

        /** The marketing shortcut, and the prefix of all its siblings. */
        const val DOWNLOAD_PATH = "/descargar"

        /** What the bare shortcut stands for: the bio link of the account, and the QR codes. */
        const val BIO_CAMPAIGN = "bio"

        /** Every shortcut is reached from one place today; a second source gets its own constant. */
        const val UTM_SOURCE = "instagram"

        const val PAID_MEDIUM = "paid"

        /** Earned vs bought. Nothing else is a medium: the ledger only has those two columns. */
        val UTM_MEDIUMS = listOf("social", PAID_MEDIUM)

        val REQUIRED_UTM = listOf("utm_source", "utm_medium", "utm_campaign")

        /** A bought radius is named `z-<place>`; every other slug is earned traffic. */
        const val PAID_SLUG_PREFIX = "z-"

        /** The parametric master's output, and the consumers that must agree with it. */
        const val MARK_MASTER = "docs/branding/mark/paparcar-mark-compas-b.svg"

        /**
         * The word's master. The imagotype has TWO, because only one half can be derived: the
         * symbol is drawn from parameters, the word is an outlined deliverable whose single
         * source of truth is `wordmark.py`. [BRAND-THE-MARK-GAINS-ITS-WORDMARK-001]
         */
        const val WORD_MASTER = "docs/branding/mark/paparcar-wordmark.svg"

        const val MARK_GENERATED = "web/lib/mark.generated.mjs"
        const val SITE_ICONS = "web/lib/icons.mjs"

        // ── los glifos de concepto [ICON-A-CONCEPT-KEEPS-ONE-GLYPH-ON-EVERY-SURFACE-001] ──

        const val CONCEPT_GLYPHS_GENERATED = "web/lib/glyphs.generated.mjs"

        const val PAPARCAR_ICONS =
            "shared/src/commonMain/kotlin/com/rndeveloper/paparcar/ui/icons/PaparcarIcons.kt"

        /** Las claves de `CONCEPT_GLYPHS`, que son las que el copy del sitio nombra. */
        val CONCEPT_KEYS = listOf("spot", "parking", "fuel", "sharedCar", "history", "figures")

        /**
         * Los dos que son dibujo NUESTRO y por eso se pueden comparar contra su fuente: viven como
         * constantes en `PaparcarIcons.kt`. Los demás son Material y su path sale del jar que
         * compila la app, así que de ellos sólo se comprueba que lleguen generados y publicados.
         */
        val OWN_MARK_SOURCES = mapOf(
            "spot" to "SPOT_BAY_PATH",
            "parking" to "GLYPH_P_PATH",
        )
        const val PREPARE_ASSETS = "web/tools/prepare-assets.py"
        const val LOCKUP_RASTER = "paparcar-imagotipo-1024.png"

        const val PUBLISHED_ASSETS = "hosting/public/assets"

        /** The hash out of a published name. */
        val HASHED_NAME = Regex("""\.([0-9a-f]{8})\.[a-z0-9]+$""")

        /** Measured at 65 published assets on 30-09-2026; the floor sits well under it. */
        const val ASSET_FLOOR = 30

        /** `name.<8 hex>.ext` — what `publishAssets()` writes. */
        val HASHED = Regex("""\.[0-9a-f]{8}\.[a-z0-9]+$""")

/**
         * The paths a browser (or the manifest spec) asks for by name, so they cannot move. They
         * must exist at the root and `firebase.json` has to make them revalidate.
         */
        val DOORWAYS = listOf("/favicon.ico", "/favicon.svg", "/site.webmanifest")

        /**
         * Which of those a PAGE may still link by that fixed path — and `/favicon.svg` is not one
         * of them, deliberately. It exists at the root for whoever asks for it directly, but a page
         * has to point at the hashed copy: linking the fixed path is EXACTLY the defect this check
         * was written for, and an allowlist that forgave it would have been decoration. Measured:
         * with `/favicon.svg` allowed here, reverting the link went green.
         */
        val LINKABLE_UNHASHED = listOf("/favicon.ico", "/site.webmanifest")

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
