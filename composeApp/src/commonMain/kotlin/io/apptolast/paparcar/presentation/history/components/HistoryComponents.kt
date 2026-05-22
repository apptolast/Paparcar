package io.apptolast.paparcar.presentation.history.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.presentation.history.BodyMedium
import io.apptolast.paparcar.ui.components.PapSectionHeaderRow
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.history_empty_subtitle
import paparcar.composeapp.generated.resources.history_empty_title

/**
 * Active section header — primary-tinted variant of [PapSectionHeaderRow]
 * with a leading pulsing dot. Padding-top 12dp for clearer separation from
 * the timeline.
 */
@Composable
internal fun ActiveSectionHeader(text: String) {
    PapSectionHeaderRow(
        title = text,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        leading = {
            PulsingDot(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(8.dp),
            )
        },
    )
}

/**
 * Empty history state (v1 redesign) — circular surfaceVariant container with
 * centred icon, then bold title + muted body. 32dp horizontal / 60dp vertical
 * padding keeps copy comfortable on wide phones.
 */
@Composable
internal fun EmptyHistoryState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(ICON_CIRCLE_DP.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = CIRCLE_BG_ALPHA)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.DirectionsCar,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = ICON_ALPHA),
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(Res.string.history_empty_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(Res.string.history_empty_subtitle),
            style = BodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = SUBTITLE_ALPHA),
            textAlign = TextAlign.Center,
        )
    }
}

private const val ICON_CIRCLE_DP = 72
private const val CIRCLE_BG_ALPHA = 0.5f
private const val ICON_ALPHA = 0.55f
private const val SUBTITLE_ALPHA = 0.55f
