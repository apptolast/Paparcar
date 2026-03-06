@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.home.components

import androidx.compose.foundation.basicMarquee
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.presentation.util.distanceMeters
import io.apptolast.paparcar.presentation.util.formatDistance
import io.apptolast.paparcar.presentation.util.formatRelativeTime
import io.apptolast.paparcar.presentation.util.formatWalkTime
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_banner_accuracy
import paparcar.composeapp.generated.resources.home_parking_row_label

@Composable
internal fun PapParkingRow(
    parking: UserParking,
    userLocation: Pair<Double, Double>?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val place = parking.placeInfo?.let { "${it.category.emoji} ${it.name}" }
    val addr = parking.address?.displayLine
    val locationLabel = when {
        place != null && addr != null -> "$place  ·  $addr"
        place != null -> place
        addr != null -> addr
        else -> stringResource(Res.string.home_parking_row_label)
    }
    val distanceM = userLocation?.let { (uLat, uLon) ->
        distanceMeters(uLat, uLon, parking.location.latitude, parking.location.longitude)
    }

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Icon box
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.DirectionsCar,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }

            // Text column
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = locationLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(),
                )
                Spacer(Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        formatRelativeTime(parking.location.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    )
                    if (distanceM != null) {
                        Text(
                            "·",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                        )
                        Icon(
                            Icons.AutoMirrored.Outlined.DirectionsWalk,
                            contentDescription = null,
                            modifier = Modifier.size(10.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                        )
                        Text(
                            "${formatDistance(distanceM)} · ${formatWalkTime(distanceM)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        )
                    }
                }
            }

            // Accuracy chip
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            ) {
                Text(
                    stringResource(Res.string.home_banner_accuracy, parking.location.accuracy.toInt()),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
