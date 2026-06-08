package io.apptolast.paparcar.presentation.home.sections.sheet.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_peek_dismiss_cd

/**
 * Unified molde for every "state" surface in Home (peek rows, modal sheets,
 * confirmation dialogs). Single visual rhythm across all states:
 *
 *  ┌──────────────────────────────────────────────────────────────────────┐
 *  │ [ CHIP ]   GREEN UPPERCASE LABEL                                [×]  │
 *  │           Display Bold Title (ellipsis, 1 line)                      │
 *  ├──────────────────────────────────────────────────────────────────────┤
 *  │  content slot (stats / meta / helper rows…)                          │
 *  │  actions slot  (PapFooterButton stack)                               │
 *  └──────────────────────────────────────────────────────────────────────┘
 *
 *  - [leading]:     bespoke chip per state — pulsing dot, circular "P", icon chip…
 *  - [headerLabel]: short uppercase label, primary green (or [accentColor]).
 *  - [accentColor]: overrides the label colour for spot variants (amber/red/blue).
 *  - [title]:       the *only* big text — ellipsis, no marquee.
 */
@Composable
internal fun PeekStateCard(
    headerLabel: String,
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    showDismiss: Boolean = true,
    contentScrollable: Boolean = false,
    leading: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit = {},
    actions: @Composable ColumnScope.() -> Unit = {},
) {
    Column(modifier = modifier.padding(horizontal = HORIZONTAL_DP.dp)) {

        // ── Header (chip + [label/title column] + close ×) ───────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            leading()
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = headerLabel.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = accentColor,
                    letterSpacing = LABEL_TRACKING_SP.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = SUBTITLE_ALPHA),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (showDismiss) {
                PeekStateDismissButton(onDismiss = onDismiss)
            }
        }

        // ── Content slot (optional) ───────────────────────────────────────
        if (contentScrollable) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                content()
            }
        } else {
            content()
        }

        // ── Actions slot (optional) ───────────────────────────────────────
        actions()
        Spacer(Modifier.height(16.dp))
    }
}

/**
 * Standard leading chip — rounded-square 44dp tile filled with [accentColor]
 * and a centered icon in [iconTint]. Used by Report, AddingZone, Detection,
 * and every other state that needs the "icon + label/title" header molde.
 */
@Composable
internal fun PeekHeaderIconChip(
    icon: ImageVector,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    iconTint: Color = MaterialTheme.colorScheme.onPrimary,
) {
    Box(
        modifier = Modifier
            .size(CHIP_DP.dp)
            .clip(CircleShape)
            .background(accentColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(30.dp),
        )
    }
}

/**
 * [PeekHeaderIconChip] overload for callers that supply a [Painter] — typically
 * the carbody drawables. Uses [Image] + `ContentScale.Fit` so side-profile
 * pictograms keep their aspect ratio instead of being stretched into a square.
 */
@Composable
internal fun PeekHeaderIconChip(
    painter: androidx.compose.ui.graphics.painter.Painter,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    iconTint: Color = MaterialTheme.colorScheme.onPrimary,
) {
    Box(
        modifier = Modifier
            .size(CHIP_DP.dp)
            .clip(CircleShape)
            .background(accentColor),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(iconTint),
        )
    }
}

/**
 * Visually de-emphasised dismiss × used by every [PeekStateCard].
 */
@Composable
private fun PeekStateDismissButton(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .size(DISMISS_HIT_DP.dp)
            .clip(CircleShape)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.Close,
            contentDescription = stringResource(Res.string.home_peek_dismiss_cd),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DISMISS_ALPHA),
            modifier = Modifier.size(DISMISS_ICON_DP.dp),
        )
    }
}

private const val HORIZONTAL_DP = 20
private const val CHIP_DP = 44
private const val DISMISS_HIT_DP = 48
private const val DISMISS_ICON_DP = 20
private const val DISMISS_ALPHA = 0.55f
private const val SUBTITLE_ALPHA = 0.55f
private const val LABEL_TRACKING_SP = 0.8
