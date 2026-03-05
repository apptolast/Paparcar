@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.history.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.presentation.history.BodyMedium
import io.apptolast.paparcar.presentation.history.BodySmall
import io.apptolast.paparcar.presentation.history.LabelBold
import io.apptolast.paparcar.presentation.history.MONTH_RES
import io.apptolast.paparcar.presentation.util.formatCoords
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.history_precision
import paparcar.composeapp.generated.resources.history_view_map
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
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .size(8.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.65f), CircleShape)
            )
            if (!isLast) {
                Box(
                    Modifier
                        .width(1.5.dp)
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        EndedSessionCardContent(
            session = session,
            onViewOnMap = onViewOnMap,
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 8.dp),
        )
    }
}

@Composable
private fun EndedSessionCardContent(
    session: UserParking,
    onViewOnMap: (Double, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateTime = remember(session.location.timestamp) {
        Instant.fromEpochMilliseconds(session.location.timestamp)
            .toLocalDateTime(TimeZone.currentSystemDefault())
    }
    val monthRes = MONTH_RES.getOrNull(dateTime.month.number - 1)
    val monthName = monthRes?.let { stringResource(it) }
        ?: dateTime.month.number.toString().padStart(2, '0')
    val timeStr = "${dateTime.hour.toString().padStart(2, '0')}:${dateTime.minute.toString().padStart(2, '0')}"
    val precisionStr = stringResource(Res.string.history_precision, session.location.accuracy.toInt())
    val primaryText = session.placeInfo?.let { "${it.category.emoji} ${it.name}" }
        ?: session.address?.displayLine
        ?: formatCoords(session.location.latitude, session.location.longitude)
    val secondaryText = session.address?.let { "${it.city ?: monthName} · $timeStr" } ?: timeStr

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = primaryText,
                    style = BodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = secondaryText,
                    style = BodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        precisionStr,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        style = LabelBold,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    )
                }
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = { onViewOnMap(session.location.latitude, session.location.longitude) },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Icon(Icons.Filled.Map, null, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(stringResource(Res.string.history_view_map), style = BodySmall)
                }
            }
        }
    }
}
