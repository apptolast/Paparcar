@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.history.components

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.presentation.history.BodyMedium
import io.apptolast.paparcar.presentation.history.BodySmall
import io.apptolast.paparcar.presentation.history.LabelBold
import io.apptolast.paparcar.presentation.util.locationDisplayText
import io.apptolast.paparcar.presentation.util.relativeTimeText
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.history_view_map
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

@Composable
internal fun DayHeaderRow(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), CircleShape)
        )
        Text(
            text = label.uppercase(),
            style = LabelBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        )
    }
}

@Composable
internal fun EndedSessionTimelineNode(
    session: UserParking,
    isLast: Boolean,
    isActive: Boolean = false,
    onViewOnMap: (Double, Double) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        Column(
            modifier = Modifier
                .width(20.dp)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(if (isActive) 10.dp else 14.dp))
            if (isActive) {
                PulsingDot(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
            } else {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.65f), CircleShape)
                )
            }
            if (!isLast) {
                Box(
                    Modifier
                        .width(1.5.dp)
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f))
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        SessionCardContent(
            session = session,
            isActive = isActive,
            onViewOnMap = onViewOnMap,
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 8.dp),
        )
    }
}

@Composable
private fun SessionCardContent(
    session: UserParking,
    isActive: Boolean,
    onViewOnMap: (Double, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val dateTime = remember(session.location.timestamp) {
        Instant.fromEpochMilliseconds(session.location.timestamp)
            .toLocalDateTime(TimeZone.currentSystemDefault())
    }
    val timeStr = "${dateTime.hour.toString().padStart(2, '0')}:${dateTime.minute.toString().padStart(2, '0')}"
    val activeRelativeTime = relativeTimeText(session.location.timestamp)
    val primaryText = locationDisplayText(
        placeInfo = session.placeInfo,
        address = session.address,
        lat = session.location.latitude,
        lon = session.location.longitude,
    )
    val secondaryText = if (isActive) activeRelativeTime
        else session.address?.city?.let { "$it · $timeStr" } ?: timeStr

    val textPrimary = if (isActive) cs.onPrimaryContainer else cs.onSurface
    val textMuted = textPrimary.copy(alpha = if (isActive) 0.6f else 0.5f)

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) cs.primaryContainer else cs.surface,
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = primaryText,
                    style = BodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = textPrimary,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = secondaryText,
                    style = BodySmall,
                    color = textMuted,
                )
            }
            IconButton(
                onClick = { onViewOnMap(session.location.latitude, session.location.longitude) },
            ) {
                Icon(Icons.Filled.Map, contentDescription = stringResource(Res.string.history_view_map), tint = cs.primary)
            }
        }
    }
}