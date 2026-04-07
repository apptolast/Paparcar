package io.apptolast.paparcar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.ui.theme.PapAmber
import io.apptolast.paparcar.ui.theme.PapAmberMuted
import io.apptolast.paparcar.ui.theme.PapBlue
import io.apptolast.paparcar.ui.theme.PapBlueMuted
import io.apptolast.paparcar.ui.theme.PapGreen
import io.apptolast.paparcar.ui.theme.PapGreenMuted
import io.apptolast.paparcar.ui.theme.PapOnDark
import io.apptolast.paparcar.ui.theme.PapRed
import io.apptolast.paparcar.ui.theme.PapRedMuted
import io.apptolast.paparcar.ui.theme.PaparcarSpacing

private val BadgeIconSize       = 12.dp
private val BadgeHorizontalPad  = 8.dp
private val BadgeVerticalPad    = 4.dp

/**
 * Generic pill-shaped status badge.
 *
 * @param label Text displayed inside the badge.
 * @param containerColor Background fill of the badge.
 * @param contentColor Text and icon tint color.
 * @param icon Optional leading icon.
 */
@Composable
fun PapBadge(
    label: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(containerColor)
            .padding(horizontal = BadgeHorizontalPad, vertical = BadgeVerticalPad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PaparcarSpacing.xs),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(BadgeIconSize),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
        )
    }
}

// ── Pre-built semantic variants ───────────────────────────────────────────────

/** Spot reliability HIGH — neon green on dark green muted. */
@Composable
fun PapHighReliabilityBadge(label: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    PapBadge(label = label, containerColor = PapGreenMuted, contentColor = PapGreen, modifier = modifier, icon = icon)
}

/** Spot reliability MEDIUM — amber on amber muted. */
@Composable
fun PapMediumReliabilityBadge(label: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    PapBadge(label = label, containerColor = PapAmberMuted, contentColor = PapAmber, modifier = modifier, icon = icon)
}

/** Spot reliability LOW / urgency — red on red muted. */
@Composable
fun PapLowReliabilityBadge(label: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    PapBadge(label = label, containerColor = PapRedMuted, contentColor = PapRed, modifier = modifier, icon = icon)
}

/** Manual report badge — blue on blue muted. */
@Composable
fun PapManualReportBadge(label: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    PapBadge(label = label, containerColor = PapBlueMuted, contentColor = PapBlue, modifier = modifier, icon = icon)
}

/** Generic "new" / positive badge — uses primary surface variant. */
@Composable
fun PapStatusBadge(label: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    PapBadge(
        label = label,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier,
        icon = icon,
    )
}
