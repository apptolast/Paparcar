@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.history.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.presentation.history.BodyMedium
import io.apptolast.paparcar.presentation.history.BodySmall
import io.apptolast.paparcar.presentation.history.LabelBold
import io.apptolast.paparcar.presentation.history.MONTH_RES
import io.apptolast.paparcar.presentation.history.TitleBody
import io.apptolast.paparcar.presentation.util.formatCoords
import io.apptolast.paparcar.presentation.util.formatRelativeTime
import io.apptolast.paparcar.ui.theme.PapGreen
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.history_active_since
import paparcar.composeapp.generated.resources.history_date_pattern
import paparcar.composeapp.generated.resources.history_precision
import paparcar.composeapp.generated.resources.history_status_active
import paparcar.composeapp.generated.resources.history_view_map
import kotlin.time.Instant

private const val PULSE_EXPAND_DURATION = 900
private const val PULSE_COLLAPSE_DURATION = 400

@Composable
internal fun PulsingDot(color: Color = PapGreen, modifier: Modifier = Modifier) {
    val ring = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            ring.animateTo(1f, tween(PULSE_EXPAND_DURATION, easing = FastOutSlowInEasing))
            ring.animateTo(0f, tween(PULSE_COLLAPSE_DURATION))
        }
    }
    Box(modifier = modifier.size(14.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size((8 + 6 * ring.value).dp)
                .background(color.copy(alpha = (1f - ring.value) * 0.35f), CircleShape)
        )
        Box(Modifier.size(8.dp).background(color, CircleShape))
    }
}

@Composable
internal fun ActiveSessionHeroCard(
    session: UserParking,
    onViewOnMap: (Double, Double) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val accentColor = cs.primary
    val textPrimary = cs.onPrimaryContainer
    val textMuted = textPrimary.copy(alpha = 0.55f)

    val relativeTime =
        remember(session.location.timestamp) { formatRelativeTime(session.location.timestamp) }
    val dateTime = remember(session.location.timestamp) {
        Instant.fromEpochMilliseconds(session.location.timestamp)
            .toLocalDateTime(TimeZone.currentSystemDefault())
    }
    val monthRes = MONTH_RES.getOrNull(dateTime.month.number - 1)
    val monthName = monthRes?.let { stringResource(it) }
        ?: dateTime.month.number.toString().padStart(2, '0')
    val dateStr = stringResource(
        Res.string.history_date_pattern,
        dateTime.day, monthName,
        dateTime.hour.toString().padStart(2, '0'),
        dateTime.minute.toString().padStart(2, '0'),
    )
    val precisionStr =
        stringResource(Res.string.history_precision, session.location.accuracy.toInt())
    val activeSinceStr = stringResource(Res.string.history_active_since, relativeTime)
    val place = session.placeInfo?.let { "${it.category.emoji} ${it.name}" }
    val addr = session.address?.displayLine
    val locationLabel = when {
        place != null && addr != null -> "$place  ·  $addr"
        place != null -> place
        addr != null -> addr
        else -> formatCoords(session.location.latitude, session.location.longitude)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cs.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.DirectionsCar,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(30.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = activeSinceStr, style = TitleBody, color = textPrimary)
                    Text(text = dateStr, style = BodySmall, color = textMuted)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PulsingDot(color = accentColor)
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        color = accentColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            stringResource(Res.string.history_status_active),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            style = LabelBold,
                            color = accentColor,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Surface(
                color = accentColor.copy(alpha = 0.08f),
                shape = RoundedCornerShape(14.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = locationLabel,
                        style = BodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = textPrimary.copy(alpha = 0.9f),
                        maxLines = 1,
                        modifier = Modifier.weight(1f).basicMarquee(),
                    )
                    Surface(
                        color = accentColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            text = precisionStr,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = LabelBold,
                            color = accentColor,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { onViewOnMap(session.location.latitude, session.location.longitude) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = cs.onPrimary,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.history_view_map), style = LabelBold)
            }
        }
    }
}
