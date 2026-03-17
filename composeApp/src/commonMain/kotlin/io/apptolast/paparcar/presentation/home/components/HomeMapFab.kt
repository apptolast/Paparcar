package io.apptolast.paparcar.presentation.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.presentation.util.MapCircleFab

// ─────────────────────────────────────────────────────────────────────────────
// FAB column — location + parked car + midpoint controls
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun HomeMapFabColumn(
    userParking: UserParking?,
    userGpsPoint: GpsPoint?,
    onMyLocation: () -> Unit,
    onParkedCar: () -> Unit,
    onMidpoint: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AnimatedVisibility(
            visible = userParking != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
        ) {
            HomeMapFab(
                icon = Icons.Outlined.DirectionsCar,
                tint = MaterialTheme.colorScheme.primary,
                onClick = onParkedCar,
            )
        }
        AnimatedVisibility(
            visible = userParking != null && userGpsPoint != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
        ) {
            HomeMapFab(icon = Icons.Outlined.Route, onClick = onMidpoint)
        }
        HomeMapFab(icon = Icons.Outlined.MyLocation, onClick = onMyLocation)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reusable circular map FAB
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun HomeMapFab(
    icon: ImageVector,
    onClick: () -> Unit,
    tint: Color = Color.Unspecified,
    containerColor: Color = Color.Unspecified,
) = MapCircleFab(
    icon = icon,
    onClick = onClick,
    iconTint = tint,
    containerColor = containerColor,
)
