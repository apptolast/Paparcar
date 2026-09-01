package com.rndeveloper.paparcar.architecture

import androidx.compose.ui.graphics.Color
import com.rndeveloper.paparcar.ui.theme.PapAmber
import com.rndeveloper.paparcar.ui.theme.PapAmberContainerLight
import com.rndeveloper.paparcar.ui.theme.PapAmberLight
import com.rndeveloper.paparcar.ui.theme.PapAmberMuted
import com.rndeveloper.paparcar.ui.theme.PapBlueContainerLight
import com.rndeveloper.paparcar.ui.theme.PapBlueMuted
import com.rndeveloper.paparcar.ui.theme.PapCarBlueDark
import com.rndeveloper.paparcar.ui.theme.PapCarBlueLight
import com.rndeveloper.paparcar.ui.theme.PapGreen
import com.rndeveloper.paparcar.ui.theme.PapGreenContainerLight
import com.rndeveloper.paparcar.ui.theme.PapGreenLight
import com.rndeveloper.paparcar.ui.theme.PapGreenTextLight
import com.rndeveloper.paparcar.ui.theme.PapGreenMuted
import com.rndeveloper.paparcar.ui.theme.PapGreenOutline
import com.rndeveloper.paparcar.ui.theme.PapGreenOutlineLight
import com.rndeveloper.paparcar.ui.theme.PapLiveMap
import com.rndeveloper.paparcar.ui.theme.PapNeutralOutline
import com.rndeveloper.paparcar.ui.theme.PapNeutralOutlineLight
import com.rndeveloper.paparcar.ui.theme.PapOnAmberContainerLight
import com.rndeveloper.paparcar.ui.theme.PapOnBlue
import com.rndeveloper.paparcar.ui.theme.PapOnGreenContainerLight
import com.rndeveloper.paparcar.ui.theme.PapOnRed
import com.rndeveloper.paparcar.ui.theme.PapOutlineVariantLight
import com.rndeveloper.paparcar.ui.theme.PapRed
import com.rndeveloper.paparcar.ui.theme.PapRedLight
import com.rndeveloper.paparcar.ui.theme.PapRedMuted
import com.rndeveloper.paparcar.ui.theme.PapSpotFresh
import com.rndeveloper.paparcar.ui.theme.PapSpotCoolingLight
import com.rndeveloper.paparcar.ui.theme.PapSpotExpiringLight
import com.rndeveloper.paparcar.ui.theme.PapSpotFreshLight
import com.rndeveloper.paparcar.ui.theme.PapSpotFreshDeep
import com.rndeveloper.paparcar.ui.theme.PapSpotFreshContainerLight
import com.rndeveloper.paparcar.ui.theme.PapOnSpotFreshContainerLight
import com.rndeveloper.paparcar.ui.theme.PapSpotExpiringContainerLight
import com.rndeveloper.paparcar.ui.theme.PapSpotFreshPuck
import com.rndeveloper.paparcar.ui.theme.PapSpotFreshMuted
import com.rndeveloper.paparcar.ui.theme.PapWatchGreen
import com.rndeveloper.paparcar.ui.theme.PapWatchGreenContainerLight
import com.rndeveloper.paparcar.ui.theme.PapWatchGreenLight
import com.rndeveloper.paparcar.ui.theme.PapWatchGreenMuted
import com.rndeveloper.paparcar.ui.theme.PapOnWatchGreenContainerLight
import org.junit.Test
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.test.assertTrue

/**
 * Guardrail for the colour doctrine (`docs/design/COLOR-SYSTEM.md`): the app is brand green; a
 * vehicle's colour is its WATCH METHOD (green = active detection, blue = Bluetooth, grey = off),
 * resolved in exactly one place (`ui/theme/VehicleIdentity.kt`); the state machine is neutral text;
 * spots keep the freshness ramp. The rules below keep the token table from silently growing back
 * into the 8-meanings-of-green zoo. [UI-COLOR-DOCTRINE-001 F6]
 */
class ColorGuardrailTest {

    /**
     * Feature files = `presentation/` + `ui/components/` in commonMain. It now comes from
     * [GuardrailScope], which is both the one definition the type and divider guardrails share and
     * the thing that refuses to return an empty list — a colour rule satisfied by "no feature file
     * matched" is the same green as "no feature file offends".
     * [TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001]
     */
    private fun featureFiles() = GuardrailScope.featureFiles()

    /** `tertiary` is a retired role: its old meanings (BT, manual report, private zone, info) all
     *  moved to the blue story, the person badge, or a neutral+lock. The scheme slots keep backing
     *  values for framework internals, but feature code must never read them. */
    @Test
    fun `feature code never reads the retired tertiary role`() {
        val violations = featureFiles()
            .filter { TERTIARY_REGEX.containsMatchIn(it.text) }
            .map { it.name }
        assertTrue(
            violations.isEmpty(),
            "[colorScheme.tertiary* is retired — Bluetooth blue is papCarBlue, provenance is " +
                "a glyph, private zones are neutral+lock] ${violations.size} violation(s):\n" +
                violations.joinToString("\n") { "  - $it.kt" },
        )
    }

    /** Colour VALUES live in `ui/theme/` — a literal `Color(0x…)` in feature code is a token that
     *  escaped the table and will drift. (`ui/components/` map markers are allowlisted: their
     *  fixed-over-tiles palettes are documented token objects, not strays.) */
    @Test
    fun `presentation declares no literal colours`() {
        val violations = GuardrailScope.presentationFeatureFiles()
            .filter { COLOR_LITERAL_REGEX.containsMatchIn(it.text) }
            .map { it.name }
        assertTrue(
            violations.isEmpty(),
            "[literal Color(0x…) in presentation — add a token with its story to ui/theme/Color.kt] " +
                "${violations.size} violation(s):\n${violations.joinToString("\n") { "  - $it.kt" }}",
        )
    }

    /**
     * **A leg is not a role.** `PapSpotFreshMuted` is not "the fresh spot colour" — it is the fresh
     * spot colour *in the dark theme*, one half of a story. Feature code that names a leg has
     * decided the theme by hand, and a file that decides the theme by hand can forget to: the age
     * pill named the three dark legs and no light ones, so it rendered a near-black lozenge on a
     * white sheet for months. Nothing caught it, because every token it read was the RIGHT token —
     * a sweep asking WHICH token a site reads cannot see HOW it picks one.
     *
     * Banning the leg does see it, because a theme-blind `when` has to name a leg to exist. Feature
     * code asks for a ROLE — `PapColor`, `stateColors()`, `vehicleIdentityColor()`, `colorScheme` —
     * and the roles live in `ui/theme`, where the luminance probe is written once.
     * [UI-COLOR-THE-RAMP-HAS-ONE-RESOLVER-001]
     */
    @Test
    fun `feature code reads roles, never theme legs`() {
        val violations = featureFiles()
            .filterNot { it.name in LEG_ALLOWLIST }
            .mapNotNull { file ->
                val legs = LEG_REGEX.findAll(file.text).map { it.groupValues[1] }.distinct().toList()
                if (legs.isEmpty()) null else "  - ${file.name}.kt → ${legs.joinToString(", ")}"
            }
        assertTrue(
            violations.isEmpty(),
            "[a *Light/*Muted/*Dark token is one THEME LEG of a story — naming it in feature code " +
                "means this file picks the theme by hand, which is how the age pill went blind. Ask " +
                "for a role: PapColor, stateColors(), vehicleIdentityColor(), colorScheme]\n" +
                violations.joinToString("\n"),
        )
    }

    /**
     * **A vehicle's colour has ONE resolver.** Doctrine rule 2 says it in prose ("el color de un
     * vehículo sale SOLO del resolver único"), but the leg rule above only sees the `Light`, `Muted`
     * and `Dark` suffixes — the theme-aware identity accents (`papCarBlue`, `papWatchGreen`) slipped through
     * it, and `HomeDetectionSurface` used exactly that gap to keep a private copy of the
     * method→colour switch: a Boolean that could not say "unwatched", silently wrong the day the
     * greens split. Feature code asks `vehicleIdentityColor(watch)` (or the container/border
     * resolvers next to it); only `ui/theme` may name the accents themselves.
     * [UI-SEVEN-STRAYS-FROM-THE-CANON-001]
     */
    @Test
    fun `feature code never names the identity accents directly`() {
        // Subject witness for the PARSER itself: a regex that has gone blind reports the same green
        // as a codebase with no offenders, so first prove it can see a known offender shape.
        // [TEST-AN-ORPHANED-FIELD-TRACE-...: every text-parsing guardrail carries its own witness]
        assertTrue(
            IDENTITY_ACCENT_REGEX.containsMatchIn("import com.rndeveloper.paparcar.ui.theme.papCarBlue"),
            "IDENTITY_ACCENT_REGEX no longer matches the offender shape it was written to catch",
        )
        val violations = featureFiles()
            .mapNotNull { file ->
                val hits = IDENTITY_ACCENT_REGEX.findAll(file.text).map { it.groupValues[1] }.distinct().toList()
                if (hits.isEmpty()) null else "  - ${file.name}.kt → ${hits.joinToString(", ")}"
            }
        assertTrue(
            violations.isEmpty(),
            "[an identity accent named outside ui/theme — a vehicle's colour comes ONLY from " +
                "vehicleIdentityColor(watch) / vehicleChassisBorder / vehicleIdentityContainer, " +
                "so every surface reads the same switch and none can drift]\n" +
                violations.joinToString("\n"),
        )
    }

    /**
     * **One hex, one story.** The doctrine's rule 4 ("every new token needs its own row with its own
     * story") was prose, so nothing enforced it and four pairs of tokens drifted back into holding
     * the same value under different names — the exact disorder §1 of COLOR-SYSTEM.md was written to
     * kill, surviving under new names for months.
     *
     * Two tokens may share a value only by DECLARING the same story below. An accidental collision
     * fails here. [UI-COLOR-EVERY-HUE-EARNS-ITS-MEANING-001]
     */
    @Test
    fun `no two colour stories share a hex`() {
        val collisions = PALETTE
            .groupBy({ it.second }, { it.first to it.third })
            .filterValues { entries -> entries.map { it.second }.distinct().size > 1 }

        assertTrue(
            collisions.isEmpty(),
            "[two DIFFERENT stories are painted with the same hex — give one its own value, or " +
                "declare them the same story in PALETTE if they really are the same promise]\n" +
                collisions.entries.joinToString("\n") { (color, entries) ->
                    "  ${color.hex()} shared by:\n" +
                        entries.joinToString("\n") { (name, story) -> "    - $name  (story: $story)" }
                },
        )
    }

    /**
     * **The three greens stay apart.** Hex equality is not the invariant — being INDISTINGUISHABLE
     * is. The bug this ticket fixed measured ΔE00 = 0.00, but a future ΔE00 of 2 would read exactly
     * the same to a user and would sail past `no two colour stories share a hex`.
     *
     * So the floor is perceptual, and it is carried by HUE: v1 was revoked on device because its key
     * distinction rode on the alpha of a border, and a lightness-only split is that same mistake —
     * two greens differing only in L* read as "the same colour, weaker", not as two things.
     *
     * Floors: ≥ 14° of hue and ≥ 18 CIE76 in Lab, in BOTH themes. The shipped values were chosen
     * with CIEDE2000 (min 12.5) and clear these with room; CIE76 is the coarse, cheap floor that
     * keeps a future edit honest. [UI-COLOR-EVERY-HUE-EARNS-ITS-MEANING-001]
     */
    @Test
    fun `the three greens stay perceptually apart in both themes`() {
        val themes = mapOf(
            "dark" to listOf("brand" to PapGreen, "watch" to PapWatchGreen, "fresh" to PapSpotFresh),
            "light" to listOf("brand" to PapGreenLight, "watch" to PapWatchGreenLight, "fresh" to PapSpotFreshLight),
        )
        val failures = buildList {
            themes.forEach { (theme, greens) ->
                for (i in greens.indices) for (j in i + 1 until greens.size) {
                    val (na, a) = greens[i]
                    val (nb, b) = greens[j]
                    val dE = a.labDistanceTo(b)
                    val dHue = a.hueDistanceTo(b)
                    if (dE < MIN_LAB_DISTANCE || dHue < MIN_HUE_DEGREES) {
                        add(
                            "  $theme: $na (${a.hex()}) vs $nb (${b.hex()}) — " +
                                "ΔE76 ${dE.toInt()} (min ${MIN_LAB_DISTANCE.toInt()}), " +
                                "Δhue ${dHue.toInt()}° (min ${MIN_HUE_DEGREES.toInt()}°)"
                        )
                    }
                }
            }
        }
        assertTrue(
            failures.isEmpty(),
            "[two of the three greens have drifted back together — brand, watched vehicle and fresh " +
                "spot must stay separable, and the separation must be in HUE, not lightness]\n" +
                failures.joinToString("\n"),
        )
    }

    private companion object {
        /** Perceptual floors for the three greens. See the test above for why hue is the axis. */
        const val MIN_LAB_DISTANCE = 18.0
        const val MIN_HUE_DEGREES = 14.0

        /**
         * Every accent token in `ui/theme/Color.kt`, with the STORY it tells. Tokens that share a
         * story string are declared aliases and are allowed to share a value; anything else is a
         * collision. Surface-ramp tokens are out of scope — their story is their position in the
         * ramp, and two ramp steps holding one value would be a visible bug long before a test.
         */
        val PALETTE: List<Triple<String, Color, String>> = listOf(
            Triple("PapGreen", PapGreen, "brand-green/dark"),
            Triple("PapGreenMuted", PapGreenMuted, "brand-green-container/dark"),
            Triple("PapGreenOutline", PapGreenOutline, "brand-green-border/dark"),
            Triple("PapGreenLight", PapGreenLight, "brand-green/light"),
            // Same story, split by JOB: the fill may be vivid, the text must be read.
            // [UI-COLOR-GREEN-TEXT-EARNS-ITS-CONTRAST-001]
            Triple("PapGreenTextLight", PapGreenTextLight, "brand-green/light-text"),
            // declared alias: "the brand green read as a border" is the same promise, not a new one
            Triple("PapGreenOutlineLight", PapGreenOutlineLight, "brand-green/light"),
            Triple("PapGreenContainerLight", PapGreenContainerLight, "brand-green-container/light"),
            // The two greens that used to BE the brand green. Their whole reason to exist is that
            // they are not it. [UI-COLOR-EVERY-HUE-EARNS-ITS-MEANING-001]
            Triple("PapWatchGreen", PapWatchGreen, "watch-identity/dark"),
            Triple("PapWatchGreenLight", PapWatchGreenLight, "watch-identity/light+map"),
            Triple("PapWatchGreenMuted", PapWatchGreenMuted, "watch-container/dark"),
            Triple("PapWatchGreenContainerLight", PapWatchGreenContainerLight, "watch-container/light"),
            Triple("PapOnWatchGreenContainerLight", PapOnWatchGreenContainerLight, "on-watch-container/light"),
            Triple("PapSpotFresh", PapSpotFresh, "spot-fresh/dark"),
            Triple("PapSpotFreshMuted", PapSpotFreshMuted, "spot-fresh-container/dark"),
            Triple("PapSpotFreshLight", PapSpotFreshLight, "spot-fresh/light"),
            Triple("PapSpotFreshDeep", PapSpotFreshDeep, "spot-fresh/light-text"),
            Triple("PapSpotFreshPuck", PapSpotFreshPuck, "spot-fresh/puck"),
            Triple("PapSpotFreshContainerLight", PapSpotFreshContainerLight, "spot-fresh-container/light"),
            Triple("PapOnSpotFreshContainerLight", PapOnSpotFreshContainerLight, "on-spot-fresh-container/light"),
            Triple("PapSpotExpiringContainerLight", PapSpotExpiringContainerLight, "spot-expiring-container/light"),
            Triple("PapSpotCoolingLight", PapSpotCoolingLight, "spot-cooling/light"),
            Triple("PapSpotExpiringLight", PapSpotExpiringLight, "spot-expiring/light"),
            Triple("PapOnGreenContainerLight", PapOnGreenContainerLight, "on-brand-green-container/light"),
            Triple("PapAmber", PapAmber, "attention/dark"),
            Triple("PapAmberMuted", PapAmberMuted, "attention-container/dark"),
            Triple("PapAmberLight", PapAmberLight, "attention/light"),
            Triple("PapAmberContainerLight", PapAmberContainerLight, "attention-container/light"),
            Triple("PapOnAmberContainerLight", PapOnAmberContainerLight, "on-attention-container/light"),
            Triple("PapRed", PapRed, "danger/dark"),
            Triple("PapRedMuted", PapRedMuted, "danger-container/dark"),
            Triple("PapOnRed", PapOnRed, "on-danger/dark"),
            Triple("PapRedLight", PapRedLight, "danger/light"),
            Triple("PapCarBlueDark", PapCarBlueDark, "bluetooth-identity/dark"),
            Triple("PapCarBlueLight", PapCarBlueLight, "bluetooth-identity/light"),
            Triple("PapBlueMuted", PapBlueMuted, "bluetooth-container/dark"),
            Triple("PapBlueContainerLight", PapBlueContainerLight, "bluetooth-container/light"),
            Triple("PapOnBlue", PapOnBlue, "on-bluetooth/light"),
            Triple("PapLiveMap", PapLiveMap, "movement-on-map/fixed"),
            Triple("PapNeutralOutline", PapNeutralOutline, "neutral-outline/dark"),
            Triple("PapNeutralOutlineLight", PapNeutralOutlineLight, "neutral-outline/light"),
            Triple("PapOutlineVariantLight", PapOutlineVariantLight, "divider/light"),
        )

        // Any read of the retired tertiary family off a colour scheme reference.
        val TERTIARY_REGEX = Regex("""\b(colorScheme|cs)\s*\.\s*(tertiary|onTertiary|tertiaryContainer|onTertiaryContainer)\b""")

        // A literal ARGB colour constructor. ui/theme is excluded by scope; Color.Transparent etc. don't match.
        val COLOR_LITERAL_REGEX = Regex("""\bColor\s*\(\s*0[xX][0-9a-fA-F]{6,8}\s*\)""")

        /** A theme leg reached from outside `ui/theme` — qualified, so a token merely NAMED in a
         *  comment or KDoc is not a violation. */
        val LEG_REGEX = Regex("""ui\.theme\.(Pap\w*(?:Muted|Light|Dark))\b""")

        /** The identity accents and their unsuffixed relatives — everything a stray resolver would
         *  need to import that the leg regex cannot see. `\b` keeps the longer container names to
         *  the leg rule (their suffixes match there). */
        val IDENTITY_ACCENT_REGEX = Regex("""ui\.theme\.(papCarBlue|papWatchGreen|PapWatchGreen|PapOnBlue)\b""")

        /**
         * The two jobs that legitimately name a leg, both because they are NOT painting on our
         * surface — the luminance probe would answer the wrong question for them:
         * - map markers and map chrome carry fixed palettes over street tiles, which are the same
         *   photograph in both themes;
         * - the theme picker's swatches show the OTHER theme on purpose — a light card next to a
         *   dark one IS the control. [UI-THEME-OPTION-SHOWS-ITS-THEME-001]
         */
        val LEG_ALLOWLIST = setOf("PaparcarMapMarkers", "PaparcarMapView", "SettingsScreen")

        /** `Color.value` packs the channels in the HIGH bits, so printing it raw yields #000000 and
         *  a useless failure message. Render from the channels instead. */
        fun Color.hex(): String {
            fun channel(f: Float) = ((f * 255).toInt().coerceIn(0, 255)).toString(16).padStart(2, '0')
            return "#${channel(red)}${channel(green)}${channel(blue)}".uppercase()
        }

        /** sRGB → CIELAB (D65). The only space where "do these look different?" is a distance. */
        fun Color.toLab(): Triple<Double, Double, Double> {
            fun linear(c: Float): Double {
                val d = c.toDouble()
                return if (d <= 0.04045) d / 12.92 else ((d + 0.055) / 1.055).pow(2.4)
            }

            val r = linear(red)
            val g = linear(green)
            val b = linear(blue)
            val x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047
            val y = 0.2126 * r + 0.7152 * g + 0.0722 * b
            val z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883
            fun f(t: Double) = if (t > 216.0 / 24389.0) cbrt(t) else (841.0 / 108.0) * t + 4.0 / 29.0
            val fx = f(x)
            val fy = f(y)
            val fz = f(z)
            return Triple(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
        }

        /** CIE76 distance — the coarse floor. Design values were picked with CIEDE2000. */
        fun Color.labDistanceTo(other: Color): Double {
            val (l1, a1, b1) = toLab()
            val (l2, a2, b2) = other.toLab()
            return sqrt((l1 - l2).pow(2) + (a1 - a2).pow(2) + (b1 - b2).pow(2))
        }

        /** Perceptual hue angle difference in degrees, the axis the separation must live on. */
        fun Color.hueDistanceTo(other: Color): Double {
            fun angle(c: Color): Double {
                val (_, a, b) = c.toLab()
                return (Math.toDegrees(atan2(b, a)) + 360.0) % 360.0
            }

            val d = abs(angle(this) - angle(other))
            return if (d > 180) 360 - d else d
        }
    }
}
