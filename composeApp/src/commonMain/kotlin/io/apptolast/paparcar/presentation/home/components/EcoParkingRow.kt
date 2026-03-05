@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.presentation.util.distanceMeters
import io.apptolast.paparcar.presentation.util.formatDistance
import io.apptolast.paparcar.presentation.util.formatRelativeTime
import io.apptolast.paparcar.presentation.util.formatWalkTime
import io.apptolast.paparcar.ui.theme.EcoGreen
import io.apptolast.paparcar.ui.theme.EcoGreenMuted
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_banner_accuracy
import paparcar.composeapp.generated.resources.home_parking_row_label

// ─────────────────────────────────────────────────────────────────────────────
// Parking row — muestra AddressInfo (o coords como fallback).
// Al hacer tap la cámara anima directamente al punto de aparcamiento.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun EcoParkingRow(
    parking: UserParking,
    address: AddressInfo?,
    userLocation: Pair<Double, Double>?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryLabel = address?.displayLine ?: stringResource(Res.string.home_parking_row_label)
    val distanceM = userLocation?.let { (uLat, uLon) ->
        distanceMeters(uLat, uLon, parking.location.latitude, parking.location.longitude)
    }

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = EcoGreenMuted.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Outlined.DirectionsCar,
                contentDescription = null,
                tint = EcoGreen,
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = primaryLabel,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = EcoGreen,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        formatRelativeTime(parking.location.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = EcoGreen.copy(alpha = 0.6f),
                    )
                    if (distanceM != null) {
                        Text(
                            "·",
                            style = MaterialTheme.typography.labelSmall,
                            color = EcoGreen.copy(alpha = 0.3f),
                        )
                        Icon(
                            Icons.AutoMirrored.Outlined.DirectionsWalk,
                            contentDescription = null,
                            modifier = Modifier.size(10.dp),
                            tint = EcoGreen.copy(alpha = 0.5f),
                        )
                        Text(
                            "${formatDistance(distanceM)} · ${formatWalkTime(distanceM)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = EcoGreen.copy(alpha = 0.6f),
                        )
                    }
                }
            }
            Surface(shape = CircleShape, color = EcoGreenMuted) {
                Text(
                    stringResource(
                        Res.string.home_banner_accuracy,
                        parking.location.accuracy.toInt()
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = EcoGreen,
                    fontSize = 10.sp,
                )
            }
        }
    }
}
