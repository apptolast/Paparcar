package com.rndeveloper.paparcar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.rndeveloper.paparcar.domain.model.SpotFreshness

/**
 * The freshness ramp resolved for every job that paints it. **This is the ramp's only resolver** —
 * a tier's colour is decided here or it is decided twice, and the second time is where it goes
 * blind. The age pill spent months painting the dark leg on a white sheet precisely because it kept
 * a `when` of its own. [UI-COLOR-THE-RAMP-HAS-ONE-RESOLVER-001]
 *
 * @param bg the tier as a FILL — a meter segment, a puck, a badge background. Floor 3:1.
 * @param text the tier when it spells a WORD on the app surface ("FIABLE", the peek eyebrow, an
 *   accent row). Floor 4.5:1, which a fill-bright colour cannot meet on a white card — in the light
 *   theme this is a deeper leg of the same tier, and in the dark theme it IS [bg], because a vivid
 *   colour on a near-black bed already clears the bar. [UI-COLOR-GREEN-TEXT-EARNS-ITS-CONTRAST-001]
 * @param container the tier as a TONAL bed — a soft lozenge with the tier written on it, which is
 *   what the age pill is. Not [bg] dimmed: a fill and a bed are different jobs with different
 *   floors, the same split as [bg] vs [text].
 * @param onContainer content drawn ON [container]. Deeper than [text] where the bed is tinted,
 *   because a tint eats contrast that white does not. All six legs measure ≥ 4.5:1 on their own bed.
 */
data class SpotStateColors(
    val bg: Color,
    val text: Color,
    val container: Color,
    val onContainer: Color,
)

private val isDark: Boolean
    @Composable get() = MaterialTheme.colorScheme.surface.luminance() < SURFACE_DARK_LUMINANCE

@Composable
fun SpotFreshness.stateColors(): SpotStateColors = when (this) {
    // FRESH was the BRAND green (PapGreen / PapGreenLight) — a stranger's free spot painted in the
    // colour of our own CTAs. The ramp is exclusive to spots, so its head gets its own hue.
    //
    // The light FILL legs are the ramp's OWN tokens now, not the theme's warning-amber and error-red.
    // Borrowing those made the whole set read muddy, because a colour that has to mean "error" is
    // dark by necessity. One ramp, the same three tones here and on the map.
    // [UI-COLOR-EVERY-HUE-EARNS-ITS-MEANING-001]
    //
    // The TONAL legs do borrow the theme's amber and red containers, and that is deliberate: a pale
    // amber bed IS a pale amber bed, and minting a near-identical token to keep the story names
    // apart would be the duplicate-hex disease with better manners. Only the fresh tier needs its
    // own pair — the theme has no green container to reuse. See `Color.kt`.
    SpotFreshness.FRESH  -> if (isDark) {
        SpotStateColors(PapSpotFresh, PapSpotFresh, PapSpotFreshMuted, PapSpotFresh)
    } else {
        SpotStateColors(
            PapSpotFreshLight,
            PapSpotFreshDeep,
            PapSpotFreshContainerLight,
            PapOnSpotFreshContainerLight,
        )
    }

    SpotFreshness.RECENT -> if (isDark) {
        SpotStateColors(PapAmber, PapAmber, PapAmberMuted, PapAmber)
    } else {
        SpotStateColors(
            PapSpotCoolingLight,
            PapAmberLight,
            PapAmberContainerLight,
            PapOnAmberContainerLight,
        )
    }

    SpotFreshness.STALE  -> if (isDark) {
        SpotStateColors(PapRed, PapRed, PapRedMuted, PapRed)
    } else {
        SpotStateColors(
            PapSpotExpiringLight,
            PapRedLight,
            PapSpotExpiringContainerLight,
            PapRedLight,
        )
    }
}
