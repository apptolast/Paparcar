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

    // ── plumbing ──────────────────────────────────────────────────────────────

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
        const val APPICON = "iosApp/iosApp/Assets.xcassets/AppIcon.appiconset"
        const val TOUCH_ICON = "web/assets/img/apple-touch-icon.png"
        const val PREPARE_ASSETS = "web/tools/prepare-assets.py"

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
