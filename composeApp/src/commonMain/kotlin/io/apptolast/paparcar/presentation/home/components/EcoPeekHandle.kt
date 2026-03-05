package io.apptolast.paparcar.presentation.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
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
import io.apptolast.paparcar.presentation.home.HomeState
import io.apptolast.paparcar.ui.theme.EcoGreen
import io.apptolast.paparcar.ui.theme.EcoGreenMuted
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_address_loading
import paparcar.composeapp.generated.resources.home_stats_free_spots_badge

@Composable
internal fun EcoPeekHandle(
    state: HomeState,
    onParkingClick: () -> Unit,
) {
    val freeCount = state.nearbySpots.count { it.isActive }

    Column(modifier = Modifier.fillMaxWidth()) {

        // Drag pill
        Box(
            modifier = Modifier
                .padding(top = 10.dp, bottom = 8.dp)
                .size(width = 32.dp, height = 4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                .align(Alignment.CenterHorizontally),
        )

        // Fila única: dirección + badge
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Outlined.LocationOn,
                contentDescription = null,
                tint = EcoGreen,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.userAddress?.displayLine
                        ?: stringResource(Res.string.home_address_loading),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (state.userAddress?.city != null) {
                    Text(
                        text = listOfNotNull(
                            state.userAddress.city,
                            state.userAddress.region,
                        ).joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        maxLines = 1,
                    )
                }
            }
            // Badge de spots libres
            Surface(
                color = if (freeCount > 0) EcoGreenMuted
                else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (freeCount > 0) EcoGreen
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            ),
                    )
                    Text(
                        stringResource(Res.string.home_stats_free_spots_badge, freeCount),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (freeCount > 0) EcoGreen
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}
