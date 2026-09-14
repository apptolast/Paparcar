package com.rndeveloper.paparcar.ui.components

import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Witnesses for the two Google Maps JSON styles. They exist because this file has no compiler
 * behind it: the styles are strings handed to a native SDK that reports nothing when a rule is
 * malformed — it just drops it and paints Google's default. The app already shipped for months
 * with EVERY rule in `MapStyles.kt` discarded (the map type was terrain, and Google styles the
 * normal type only) and nothing anywhere said so. [MAP-A-STYLE-THAT-NEVER-RAN-001]
 */
class MapStylesTest {

    private val styles = mapOf("LIGHT" to LIGHT_MAP_STYLE, "DARK" to DARK_MAP_STYLE)

    private val colorStyler = Regex("""\{"color":"([^"]*)"}""")
    private val validHex = Regex("""^#[0-9a-fA-F]{6}$""")

    /**
     * A typo inside a hex value costs that rule silently — the SDK keeps the rest of the style and
     * paints that one element with Google's colour, which is exactly the kind of half-applied
     * result that reads as "the style is wrong" instead of "the string is wrong".
     */
    @Test
    fun should_declare_every_colour_as_a_six_digit_hex_when_styling_the_map() {
        styles.forEach { (theme, style) ->
            val colors = colorStyler.findAll(style).map { it.groupValues[1] }.toList()
            assertTrue(colors.isNotEmpty(), "$theme: no colour stylers found — did the style change shape?")
            colors.forEach { hex ->
                assertTrue(validHex.matches(hex), "$theme: \"$hex\" is not a valid #rrggbb colour")
            }
        }
    }

    /**
     * The POI decision, in the only place it is actually expressed: icons off, transit off, names
     * kept. A pill with an icon is our marker language (zones, fuel capsules); a grey name is
     * orientation and competes with nothing.
     */
    @Test
    fun should_hide_poi_icons_and_transit_but_keep_their_names_when_styling_the_map() {
        styles.forEach { (theme, style) ->
            val compact = style.replace(" ", "").replace("\n", "")
            assertTrue(
                compact.contains("""{"elementType":"labels.icon","stylers":[{"visibility":"off"}]}"""),
                "$theme: POI icons are no longer switched off",
            )
            assertTrue(
                compact.contains("""{"featureType":"transit","stylers":[{"visibility":"off"}]}"""),
                "$theme: transit is no longer switched off",
            )
            assertTrue(
                compact.contains(""""featureType":"poi","elementType":"labels.text.fill""""),
                "$theme: POI names are no longer styled — they are kept on purpose, not by accident",
            )
        }
    }

    /**
     * The light map is a surface the peek sheet sits on, and the sheet is NOT white — it is
     * `PapAzure` #ECF0F9 (`surfaceContainer`), which is also the nav bar. Measuring the ground
     * against white instead of against its actual neighbour is how a repaint can look like a win
     * on paper and change almost nothing on screen: #e2e9f3 scores 1.22:1 against white and
     * 1.07:1 against the sheet. This test measures the neighbour.
     *
     * Both halves matter. The floor is the separation; the direction is which one is the figure —
     * the ground must stay DARKER than the sheet, so the sheet reads as a card lifted off the map
     * rather than a hole punched in it.
     */
    @Test
    fun should_keep_the_light_ground_darker_than_the_sheet_it_carries_when_styling_the_map() {
        val ground = groundColorOf(LIGHT_MAP_STYLE)
        assertEquals("#cdd8ea", ground, "the light ground moved — check it still reads against the sheet")
        val contrast = contrastWith(ground, SHEET_SURFACE)
        assertTrue(
            relativeLuminance(ground) < relativeLuminance(SHEET_SURFACE),
            "light ground $ground is lighter than the sheet $SHEET_SURFACE — the sheet stops " +
                "reading as a card on top of the map",
        )
        assertTrue(
            contrast >= MIN_GROUND_CONTRAST,
            "light ground $ground contrasts $contrast:1 with the sheet $SHEET_SURFACE, below the " +
                "$MIN_GROUND_CONTRAST:1 floor that keeps the sheet's edge visible without its shadow",
        )
    }

    /** The root `{"elementType":"geometry", …}` rule — the base colour every other rule sits on. */
    private fun groundColorOf(style: String): String =
        Regex("""\{"elementType":"geometry","stylers":\[\{"color":"([^"]*)"}]}""")
            .find(style.replace(" ", "").replace("\n", ""))
            ?.groupValues?.get(1)
            ?: error("no root geometry rule found — the style changed shape")

    private fun contrastWith(a: String, b: String): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun relativeLuminance(hex: String): Double {
        fun channel(start: Int): Double {
            val raw = hex.substring(start, start + 2).toInt(16) / 255.0
            return if (raw <= 0.03928) raw / 12.92 else ((raw + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(1) + 0.7152 * channel(3) + 0.0722 * channel(5)
    }

    private companion object {
        /**
         * `PapAzure` — `surfaceContainer` in the light scheme, the peek sheet and the nav bar.
         * Duplicated here as a literal on purpose: `Color` is a UI type and this is the value the
         * map has to hold its own against, so the test states it rather than importing a token
         * whose meaning could drift without this failing.
         */
        const val SHEET_SURFACE = "#ecf0f9"

        /**
         * Measured on device against [SHEET_SURFACE]: #f5f5f5 sat at 1.05:1 and read as the same
         * surface as the sheet; #e2e9f3 only reached 1.07:1; #cdd8ea reaches 1.26:1.
         */
        const val MIN_GROUND_CONTRAST = 1.20
    }
}
