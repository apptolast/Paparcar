package io.apptolast.paparcar.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp

/**
 * Semantic border-width tokens for Paparcar surfaces.
 *
 * Default reference is the parking-card outline (1dp @ outline.alpha 0.4) —
 * subtle, present, never "neon". Larger / more important cards may use
 * [medium]; rarely surfaces need [strong] (used for explicit selection state).
 *
 * Rule of thumb: card outlines should always use [outlineSubtle]; reserve
 * primary-coloured borders for chips or toggles that need to read as selected.
 */
object PapBorders {
    /** Standard hairline outline (1dp). Use for cards, chips, dividers. */
    val thin = 1.dp

    /** Slightly heavier outline (1.5dp). Use sparingly — hero cards, dialogs. */
    val medium = 1.5.dp

    /** Heaviest outline (2dp). Reserve for explicit selection / focus rings. */
    val strong = 2.dp

    /** Default opacity applied to `outline` for card borders — the "good" reference. */
    const val DEFAULT_OUTLINE_ALPHA = 0.4f

    /** Opacity for hairline dividers INSIDE surfaces (list separators, card foot rules).
     *  One value for every inner divider — replaces the 0.08/0.12/0.14/0.15/0.5 zoo. */
    const val HAIRLINE_DIVIDER_ALPHA = 0.14f
}

/** 1dp outline at the default alpha — the reference border for ordinary cards. */
val outlineSubtle: BorderStroke
    @Composable
    @ReadOnlyComposable
    get() = BorderStroke(
        PapBorders.thin,
        MaterialTheme.colorScheme.outline.copy(alpha = PapBorders.DEFAULT_OUTLINE_ALPHA),
    )
