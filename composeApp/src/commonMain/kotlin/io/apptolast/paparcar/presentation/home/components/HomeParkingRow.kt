@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.home.components

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.presentation.util.distanceMeters
import io.apptolast.paparcar.presentation.util.formatDistance
import io.apptolast.paparcar.presentation.util.formatWalkTime
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_banner_accuracy
import paparcar.composeapp.generated.resources.home_parking_release

@Composable
internal fun HomeParkingRow(
    parking: UserParking,
    userLocation: Pair<Double, Double>?,
    onClick: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val place = parking.placeInfo?.let { "${it.category.emoji} ${it.name}" }
    val address = parking.address?.displayLine
    val locationLabel: String? = when {
        place != null && address != null -> "$place  ·  $address"
        place != null -> place
        address != null -> address
        else -> null
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
        Column {
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
                    val timeAgo = relativeTimeText(parking.location.timestamp)
                    if (distanceM != null) {
                        Text(
                            formatWalkTime(distanceM),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(3.dp))
                        val secondary = buildString {
                            append(formatDistance(distanceM))
                            if (locationLabel != null) append("  ·  $locationLabel")
                            append("  ·  $timeAgo")
                        }
                        Text(
                            text = secondary,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(),
                        )
                    } else {
                        Text(
                            text = locationLabel ?: timeAgo,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(),
                        )
                        if (locationLabel != null) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                timeAgo,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
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

            // Release action — nested click, does not propagate to card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 68.dp, end = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onRelease) {
                    Text(
                        stringResource(Res.string.home_parking_release),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
