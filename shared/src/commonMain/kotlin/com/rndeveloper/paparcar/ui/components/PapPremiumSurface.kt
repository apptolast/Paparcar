package com.rndeveloper.paparcar.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.ui.theme.PapBorders
import com.rndeveloper.paparcar.ui.theme.PapShapes
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import com.rndeveloper.paparcar.ui.theme.premiumEdge
import com.rndeveloper.paparcar.ui.theme.premiumSweep

/**
 * The ONE surface that says "Paparcar Plus". [UI-COLOR-PLUS-WEARS-THE-BRAND-WITH-A-FINISH-001]
 *
 * Every Plus touchpoint — the Settings row, the history footer, the paywall hero — renders on
 * this, so the premium look is a single recipe instead of three call sites each inventing a
 * gradient. The metallic read comes from a DIAGONAL sweep (shadow → mid → brand highlight): a
 * flat tone cannot look like metal, and the doctrine has no hue left for a fourth flat green.
 *
 * The sweep runs corner to corner rather than straight down, because a highlight that tracks
 * the diagonal is what the eye reads as a polished surface; a vertical fade reads as a plain
 * tonal card.
 *
 * ⚠️ This is a FILLED brand surface — vivid green carrying dark ink, the same contract as
 * `PapPrimaryButton` — so it PROVIDES `onPrimary` as its content colour and children inherit
 * ink that reads on green. The first version dimmed the BED instead, to keep light text on it:
 * that kept the brand's hue and lost the brand's look, reading as dark forest on device.
 * Dim the text, not the brand.
 */
@Composable
fun PapPremiumSurface(
    modifier: Modifier = Modifier,
    shape: Shape = PapShapes.cardLarge,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    // deep → lit → deep: metal is a highlight PASSING ACROSS a surface, not a one-way fade.
    // Corner-to-corner: `Brush.linearGradient` with no explicit start/end runs top-left →
    // bottom-right by default, which is exactly the diagonal a metallic sheen needs.
    val sweep = Brush.linearGradient(premiumSweep)
    val edge = BorderStroke(
        width = PapBorders.thin,
        brush = Brush.linearGradient(
            listOf(premiumEdge.copy(alpha = EDGE_BRIGHT_ALPHA), Color.Transparent),
        ),
    )
    val body: @Composable () -> Unit = {
        Box(
            modifier = Modifier.background(sweep, shape),
            content = content,
        )
    }
    // contentColor is the point: everything inside inherits ink that reads on vivid green, so a
    // call site cannot pair the filled brand bed with the surface's own onSurface by accident.
    val ink = MaterialTheme.colorScheme.onPrimary
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = Color.Transparent,
            contentColor = ink,
            border = edge,
            content = body,
        )
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = Color.Transparent,
            contentColor = ink,
            border = edge,
            content = body,
        )
    }
}

/**
 * The CTA that lives ON a [PapPremiumSurface] — inverted, because the canonical
 * [PapPrimaryButton] fills itself with the brand green and would vanish on a brand-green bed.
 *
 * Deliberately NOT a third `tone` on the canonical button: a tone is a MEANING (brand,
 * destructive), and "the same brand CTA, inverted because of what it sits on" is a property of
 * the SURFACE, not a new meaning. Keeping it here is what stops `PapButtonTone` from growing an
 * entry per background. [UI-BUTTON-ONE-CANONICAL-CTA-001][MONEY-001]
 */
@Composable
fun PapPremiumCta(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = PapShapes.chip,
        color = MaterialTheme.colorScheme.onPrimary,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        Text(
            text = label,
            style = PaparcarType.current.cta,
            modifier = Modifier.padding(horizontal = CTA_H_PAD_DP.dp, vertical = CTA_V_PAD_DP.dp),
        )
    }
}

private const val CTA_H_PAD_DP = 18
private const val CTA_V_PAD_DP = 10

/** How present the lit edge is at the highlight corner. Below this the metal reads flat; above
 *  it the border competes with the CTA inside the card. */
private const val EDGE_BRIGHT_ALPHA = 0.55f
