package com.rndeveloper.paparcar.architecture

import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Every brand surface is GENERATED from the parametric master — including the ones that ship as
 * pixels, where no `grep` can see what they draw.
 *
 * This exists because that is exactly how it failed. `iosApp/` sat entirely outside
 * `docs/branding/master/export_surfaces.py`, so `BRAND-A-NEW-MARK-EARNS-ITS-SURFACES-001` never
 * reached it and the iOS app icon kept drawing the **discarded** disc-and-foot proposal — not an
 * old mark, one that was never the mark — all the way into the release lane. Nothing could tell:
 * the drawing lived as a PNG.
 *
 * So the checks below are the ones a raster surface actually allows:
 *
 *  1. The generated SVG sources still agree with the master drawing, which catches "the master
 *     moved and nobody re-ran the exporter".
 *  2. `AppIcon.appiconset` declares a file for each of the three iOS 18 appearances, and each of
 *     them exists — an empty appearance silently falls back to the light icon.
 *  3. Each shipped PNG is MEASURED: how much of it the mark covers. That is what separates a real
 *     regression from a healthy icon here — the discarded mark covered **24.5%** of the tile where
 *     the current one covers **7.9%**, so this check would have caught it on day one. It also
 *     catches a blank render, which this pipeline has produced before at exactly the right size.
 *
 * ⛔ What it does NOT check: that someone re-rasterised after regenerating the SVG. A PNG cannot
 * say when it was made, and git keeps no timestamps. `render_graphics.sh apple` verifies its own
 * output as it writes; if it is never run, only check 3's coverage band can notice, and only when
 * the drawing changed size. That gap is the same one the Play graphics had, and it is named here
 * rather than papered over.
 *
 * ⛔ Konsist-style guards go UP-TO-DATE when no bytecode changed, and none of this is bytecode.
 * Falsify it with `./gradlew :shared:testDebugUnitTest --tests '*BrandSurface*' --rerun-tasks`.
 *
 * [BRAND-THE-APPLE-SURFACES-ARE-OUTSIDE-THE-MASTER-001] [TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001]
 */
class BrandSurfaceGuardrailTest {

    @Test
    fun `every generated Apple surface still draws the master's mark`() {
        val masterPaths = Regex("""<path d="([^"]+)"/>""")
            .findAll(read(MARK_MASTER)).map { it.groupValues[1] }.toList()
        assertEquals(3, masterPaths.size, "$MARK_MASTER should hold the three paths of the mark")

        for (svg in APPLE_SVGS) {
            val text = read(svg)
            for (path in masterPaths) {
                assertTrue(
                    text.contains(path),
                    buildString {
                        appendLine("$svg does not carry a path that is in $MARK_MASTER.")
                        appendLine("The master drawing moved and the exporter was never re-run, so")
                        appendLine("the Apple surfaces still draw the previous mark.")
                        appendLine()
                        appendLine("  cd docs/branding/master && python export_surfaces.py")
                        appendLine("  bash docs/release/play-listing/tools/render_graphics.sh apple")
                    },
                )
            }
        }
    }

    @Test
    fun `the iOS icon set declares a file for each appearance`() {
        val contents = read("$APPICON/Contents.json")
        val declared = Regex(""""filename"\s*:\s*"([^"]+)"""")
            .findAll(contents).map { it.groupValues[1] }.toList()

        assertEquals(
            IOS_ICONS.keys.toList(), declared,
            buildString {
                appendLine("$APPICON/Contents.json does not declare the three appearances.")
                appendLine("iOS 18 lets the home screen go dark or tinted; an appearance with no")
                appendLine("file silently falls back to the light icon, so a dark home screen shows")
                appendLine("a bright tile among icons that did adapt.")
            },
        )
        for (name in IOS_ICONS.keys) {
            assertTrue(repoFile("$APPICON/$name").isFile, "$APPICON/$name is declared but missing")
        }
    }

    @Test
    fun `every shipped Apple raster carries the mark, measured`() {
        for ((relative, expected) in IOS_ICONS + mapOf(TOUCH_ICON to TOUCH_COVERAGE)) {
            val file = if (relative == TOUCH_ICON) repoFile(relative) else repoFile("$APPICON/$relative")
            assertTrue(file.isFile, "missing raster: ${file.path}")

            val coverage = markCoverage(file)
            assertTrue(
                kotlin.math.abs(coverage - expected) <= TOLERANCE,
                buildString {
                    appendLine("${file.name}: the mark covers %.1f%% of it, expected ~%.1f%%."
                        .format(coverage, expected))
                    appendLine()
                    appendLine("Either it is not the mark this repo draws, or it was never")
                    appendLine("re-rasterised. For scale: the DISCARDED disc-and-foot proposal that")
                    appendLine("shipped here until 30-09-2026 measured 24.5%, and a blank render")
                    appendLine("measures 0% at exactly the right file size.")
                    appendLine()
                    appendLine("  cd docs/branding/master && python export_surfaces.py")
                    appendLine("  bash docs/release/play-listing/tools/render_graphics.sh apple")
                },
            )
        }
    }

    @Test
    fun `the web no longer borrows the Play icon for its Apple surface`() {
        val assets = read(PREPARE_ASSETS)

        assertEquals(
            0, Regex("""Image\.open\([^)]*play-icon-512\.png""").findAll(assets).count(),
            buildString {
                appendLine("$PREPARE_ASSETS opens play-icon-512.png again.")
                appendLine("That icon belongs to the Play listing. The apple-touch-icon is a BRAND")
                appendLine("surface and comes from the master now, so borrowing the Play tile here")
                appendLine("would reintroduce the drift that shrank it on every unrelated run.")
            },
        )
        assertTrue(
            !assets.contains("def prepare_touch_icon"),
            "$PREPARE_ASSETS builds the touch icon again; it belongs to export_surfaces.py.",
        )
    }

    @Test
    fun `every imagotype surface draws BOTH halves of the master`() {
        val markPaths = Regex("""<path d="([^"]+)"/>""")
            .findAll(read(MARK_MASTER)).map { it.groupValues[1] }.toList()
        val wordPaths = Regex("""<path d="([^"]+)"/>""")
            .findAll(read(WORD_MASTER)).map { it.groupValues[1] }.toList()
        assertEquals(3, markPaths.size, "$MARK_MASTER should hold the three paths of the mark")
        assertEquals(8, wordPaths.size, "$WORD_MASTER should hold the eight glyphs of the word")

        for (surface in IMAGOTIPO_SURFACES) {
            val text = read(surface)
            for ((half, paths) in listOf(MARK_MASTER to markPaths, WORD_MASTER to wordPaths)) {
                val missing = paths.count { !text.contains(it) }
                assertEquals(
                    0, missing,
                    buildString {
                        appendLine("$surface is missing $missing path(s) that are in $half.")
                        appendLine("The imagotype is COMPOSED from two masters — the parametric")
                        appendLine("symbol and the outlined word — and every surface receives both.")
                        appendLine("A surface that carries one and not the other is drawing a lockup")
                        appendLine("someone assembled by hand, which is the thing this exists to stop.")
                        appendLine()
                        appendLine("  cd docs/branding/master && python wordmark.py")
                        appendLine("  cd docs/branding/master && python export_surfaces.py")
                        appendLine("  node web/build.mjs")
                    },
                )
            }
        }
    }

    @Test
    fun `the imagotype raster carries both halves, in the right order`() {
        val file = repoFile(IMAGOTIPO_RASTER).takeIf { it.isFile }
            ?: fail(
                "$IMAGOTIPO_RASTER is missing — the social card pastes it.\n" +
                    "  bash docs/release/play-listing/tools/render_graphics.sh lockup",
            )
        val (symbol, word) = lockupCoverage(file)

        // Measured on the generated raster, 19-10-2026. The symbol is a fifth of the drawing's
        // width and the word fills the rest, so these two numbers are far enough apart that a
        // swap, a missing half or a blank render cannot land inside either band.
        assertTrue(
            kotlin.math.abs(symbol.share - SYMBOL_COVERAGE) < TOLERANCE,
            "$IMAGOTIPO_RASTER: symbol covers ${"%.1f".format(symbol.share)}%, " +
                "expected $SYMBOL_COVERAGE% ± $TOLERANCE. Re-run render_graphics.sh lockup.",
        )
        assertTrue(
            kotlin.math.abs(word.share - WORD_COVERAGE) < WORD_TOLERANCE,
            "$IMAGOTIPO_RASTER: the word covers ${"%.1f".format(word.share)}%, " +
                "expected $WORD_COVERAGE% ± $WORD_TOLERANCE. Re-run render_graphics.sh lockup.",
        )
        // The ORDER, which is what a coverage figure alone would miss: both halves could be
        // present and sitting on top of each other — exactly what happens if the two translates
        // come out equal — and the shares would barely move.
        assertTrue(
            symbol.centreX < 0.20 && word.centreX > 0.45,
            buildString {
                appendLine("$IMAGOTIPO_RASTER: the halves are not where the lockup puts them.")
                appendLine("symbol centred at ${"%.0f".format(100 * symbol.centreX)}% of the width " +
                    "(expected < 20%), word at ${"%.0f".format(100 * word.centreX)}% (expected > 45%).")
                appendLine("Either they overlap or they are swapped.")
            },
        )
    }

    @Test
    fun `no surface composes the brand name beside the mark any more`() {
        // The defect class this ticket closed: six surfaces each pairing the symbol with the name
        // set as TEXT, in three different typographic treatments. The imagotype replaced all six,
        // and these are the shapes each of them used to have.
        val handmade = listOf(
            Triple("web/lib/layout.mjs", "brand__name", "the site header and footer"),
            Triple("web/tools/restyle-legal.mjs", "brand__name", "the two legal pages"),
            Triple("web/assets/css/site.css", ".brand__name {", "the stylesheet"),
            // ⛔ El cebo NO es el nombre: es la FIRMA de la línea que lo dibujaba,
            // `font=_font(46, 800)` — el único tamaño de esa tarjeta que no es un titular.
            // Buscar la palabra dejó de valer en cuanto la marca pasó a minúscula
            // [BRAND-THE-NAME-IS-WRITTEN-IN-LOWERCASE-001]: un guard que se cae cuando cambia
            // la ortografía de lo que vigila no vigila nada.
            Triple(PREPARE_ASSETS, "font=_font(46", "the social card"),
            Triple(FEATURE_GRAPHIC, "class=\"word\"", "the Play banner"),
            Triple(AUTH_SLOTS, "PaparcarLogo(", "the auth header"),
        )
        for ((file, needle, where) in handmade) {
            assertTrue(
                !read(file).contains(needle),
                buildString {
                    appendLine("$file composes the brand name beside the mark again ($needle).")
                    appendLine("That is the hand-made lockup the imagotype replaced in $where.")
                    appendLine("Use the imagotype: logoLockup() on the web, PaparcarImagotipo in")
                    appendLine("the app, the generated <svg class=\"lockup\"> in the banner.")
                    appendLine("[BRAND-THE-MARK-GAINS-ITS-WORDMARK-001]")
                },
            )
        }
    }

    @Test
    fun `the disc is gone from the repo, not just from its two exceptions`() {
        // Both disc exceptions were the user's, and both rested on one measured fact: the mark at
        // F_EMBLEM floated next to an 82 px wordmark. The imagotype removed the reason, so the
        // code went too — leaving a disc helper with no call sites would be an exception standing
        // over something that no longer renders, which is a hole, not an exception.
        val exporter = read(EXPORTER)
        for (dead in listOf("def emblem_svg", "def disc_circles", "EMBLEM_SITE", "EMBLEM_BANNER")) {
            assertTrue(
                !exporter.contains(dead),
                "$EXPORTER still defines $dead. The disc has no consumers left; delete it.",
            )
        }
        assertTrue(
            !repoFile("docs/assets/paparcar-emblem.svg").exists() &&
                !repoFile("docs/assets/paparcar-emblem-512.png").exists(),
            "The emblem files are back. Their only consumer was the social card, which pastes " +
                "the imagotype now.",
        )
    }

    // ── plumbing ──────────────────────────────────────────────────────────────

    /** Where one half of the lockup sits: how much it covers, and its centre as a share of width. */
    private data class Half(val share: Double, val centreX: Double)

    /**
     * The two halves of the imagotype, measured apart.
     *
     * [markCoverage] lumps brand green and light grey together, which is right for a one-colour
     * tile and useless here: the imagotype's whole point is that those two colours are two
     * different things — the symbol and the word.
     */
    private fun lockupCoverage(file: File): Pair<Half, Half> {
        val image = ImageIO.read(file) ?: fail("not a readable image: ${file.path}")
        var symbol = 0
        var word = 0
        var symbolX = 0L
        var wordX = 0L
        var total = 0
        for (y in 0 until image.height step 2) {
            for (x in 0 until image.width step 2) {
                val argb = image.getRGB(x, y)
                val a = (argb ushr 24) and 0xFF
                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF
                total++
                if (a <= 128) continue
                if (g > 180 && r < 120) {
                    symbol++
                    symbolX += x
                } else if (minOf(r, g, b) > 190) {
                    word++
                    wordX += x
                }
            }
        }
        if (symbol == 0) fail("${file.path}: not one brand-green pixel — the symbol is missing")
        if (word == 0) fail("${file.path}: not one near-white pixel — the WORD is missing")
        return Half(100.0 * symbol / total, symbolX.toDouble() / symbol / image.width) to
            Half(100.0 * word / total, wordX.toDouble() / word / image.width)
    }


    /** Share of sampled pixels the mark covers: brand green, or the light grey of the tinted icon. */
    private fun markCoverage(file: File): Double {
        val image = ImageIO.read(file) ?: fail("not a readable image: ${file.path}")
        val step = maxOf(1, image.width / 128)
        var mark = 0
        var total = 0
        var y = 0
        while (y < image.height) {
            var x = 0
            while (x < image.width) {
                val argb = image.getRGB(x, y)
                val a = (argb ushr 24) and 0xFF
                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF
                total++
                val green = g > 150 && r < 140 && g - b > 40
                val lightGrey = r > 180 && g > 180 && b > 180
                if (a > 128 && (green || lightGrey)) mark++
                x += step
            }
            y += step
        }
        return 100.0 * mark / total
    }

    private fun read(relative: String): String =
        repoFile(relative).takeIf { it.isFile }?.readText()
            ?: fail("not found from ${repoRoot.path}: $relative")

    private fun repoFile(relative: String) = File(repoRoot, relative)

    private companion object {
        const val MARK_MASTER = "docs/branding/mark/paparcar-mark-compas-b.svg"

        /**
         * The OTHER master. The symbol is drawn from parameters; the word cannot be — it is an
         * outlined deliverable — so what it gets instead is a single place to live, which is what
         * the repo's rule actually asks for. Two masters, one lockup.
         */
        const val WORD_MASTER = "docs/branding/mark/paparcar-wordmark.svg"

        const val APPICON = "iosApp/iosApp/Assets.xcassets/AppIcon.appiconset"
        const val TOUCH_ICON = "web/assets/img/apple-touch-icon.png"
        const val PREPARE_ASSETS = "web/tools/prepare-assets.py"
        const val FEATURE_GRAPHIC = "docs/release/play-listing/assets/feature.html"
        const val EXPORTER = "docs/branding/master/export_surfaces.py"
        const val AUTH_SLOTS =
            "shared/src/commonMain/kotlin/com/rndeveloper/paparcar/ui/auth/PaparcarAuthSlots.kt"

        /** Every surface that draws the brand WITH its name. The isotype ones are not here. */
        val IMAGOTIPO_SURFACES = listOf(
            "docs/assets/paparcar-imagotipo.svg",
            "docs/assets/paparcar-imagotipo-dark.svg",
            "docs/assets/paparcar-imagotipo-light.svg",
            "shared/src/commonMain/composeResources/drawable/paparcar_imagotipo_dark.xml",
            "shared/src/commonMain/composeResources/drawable/paparcar_imagotipo_light.xml",
            "web/lib/mark.generated.mjs",
            FEATURE_GRAPHIC,
        )

        /** What the social card pastes. Generated by `render_graphics.sh lockup`. */
        const val IMAGOTIPO_RASTER = "docs/assets/paparcar-imagotipo-1024.png"

        /**
         * Measured on the generated raster, 19-10-2026. The symbol is a fifth of the lockup's
         * width and the word fills the rest, so the two shares sit far apart on purpose: a swap
         * or a missing half lands nowhere near its band.
         */
        const val SYMBOL_COVERAGE = 4.5
        const val WORD_COVERAGE = 23.6
        const val WORD_TOLERANCE = 4.0

        val APPLE_SVGS = listOf(
            "docs/branding/apple/ios-app-icon.svg",
            "docs/branding/apple/ios-app-icon-dark.svg",
            "docs/branding/apple/ios-app-icon-tinted.svg",
            "docs/branding/apple/web-touch-icon.svg",
        )

        /**
         * Measured on the generated rasters, 30-09-2026. The three iOS icons sit at the store
         * fraction (`F_STORE_ICON`), which is the same one the published Play icon uses — it
         * measures 7.9% too, and that is the confirmation that both stores show one drawing.
         */
        val IOS_ICONS = linkedMapOf(
            "app-icon-1024.png" to 7.9,
            "app-icon-1024-dark.png" to 8.0,
            "app-icon-1024-tinted.png" to 8.0,
        )

        /** The touch icon is deliberately bigger (`F_TOUCH`): it never competes in a store grid. */
        const val TOUCH_COVERAGE = 17.0

        /**
         * Wide enough that antialiasing and PNG re-encoding never trip it, narrow enough that the
         * defect this class exists for — a 24.5% drawing where 7.9% belongs — cannot hide.
         */
        const val TOLERANCE = 3.0

        val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("repo root not found from ${File("").absolutePath}")
    }
}
