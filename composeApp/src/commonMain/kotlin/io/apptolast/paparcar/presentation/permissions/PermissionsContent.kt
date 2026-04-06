package io.apptolast.paparcar.presentation.permissions

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.permissions_btn_background
import paparcar.composeapp.generated.resources.permissions_btn_grant
import paparcar.composeapp.generated.resources.permissions_btn_location_settings
import paparcar.composeapp.generated.resources.permissions_btn_settings
import paparcar.composeapp.generated.resources.permissions_perm_activity
import paparcar.composeapp.generated.resources.permissions_perm_activity_desc
import paparcar.composeapp.generated.resources.permissions_perm_background
import paparcar.composeapp.generated.resources.permissions_perm_background_desc
import paparcar.composeapp.generated.resources.permissions_perm_bluetooth
import paparcar.composeapp.generated.resources.permissions_perm_bluetooth_desc
import paparcar.composeapp.generated.resources.permissions_perm_location
import paparcar.composeapp.generated.resources.permissions_perm_location_desc
import paparcar.composeapp.generated.resources.permissions_perm_location_services
import paparcar.composeapp.generated.resources.permissions_perm_location_services_desc
import paparcar.composeapp.generated.resources.permissions_perm_notifications
import paparcar.composeapp.generated.resources.permissions_perm_notifications_desc
import paparcar.composeapp.generated.resources.permissions_rationale
import paparcar.composeapp.generated.resources.permissions_status_granted
import paparcar.composeapp.generated.resources.permissions_status_optional
import paparcar.composeapp.generated.resources.permissions_status_pending
import paparcar.composeapp.generated.resources.permissions_subtitle
import paparcar.composeapp.generated.resources.permissions_title

@Composable
internal fun PermissionsContent(
    state: PermissionsState,
    onRequestPermissions: () -> Unit,
    onRequestBluetooth: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 48.dp, bottom = 120.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(text = "🔐", fontSize = 48.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(Res.string.permissions_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.permissions_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))

            // ── Runtime permissions ──────────────────────────────────────────
            PermissionRow(
                icon = Icons.Default.LocationOn,
                title = stringResource(Res.string.permissions_perm_location),
                desc = stringResource(Res.string.permissions_perm_location_desc),
                granted = state.hasFineLocation,
            )
            Spacer(Modifier.height(12.dp))
            PermissionRow(
                icon = Icons.Outlined.Explore,
                title = stringResource(Res.string.permissions_perm_background),
                desc = stringResource(Res.string.permissions_perm_background_desc),
                granted = state.hasBackgroundLocation,
            )
            Spacer(Modifier.height(12.dp))
            PermissionRow(
                icon = Icons.Default.Person,
                title = stringResource(Res.string.permissions_perm_activity),
                desc = stringResource(Res.string.permissions_perm_activity_desc),
                granted = state.hasActivityRecognition,
            )
            Spacer(Modifier.height(12.dp))
            PermissionRow(
                icon = Icons.Default.Notifications,
                title = stringResource(Res.string.permissions_perm_notifications),
                desc = stringResource(Res.string.permissions_perm_notifications_desc),
                granted = state.hasNotifications,
            )

            // ── System setting (GPS toggle) ──────────────────────────────────
            Spacer(Modifier.height(20.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 1.dp,
            )
            Spacer(Modifier.height(20.dp))
            PermissionRow(
                icon = Icons.Default.Settings,
                title = stringResource(Res.string.permissions_perm_location_services),
                desc = stringResource(Res.string.permissions_perm_location_services_desc),
                granted = state.isLocationServicesEnabled,
            )

            // ── Optional Bluetooth permission ────────────────────────────────
            Spacer(Modifier.height(20.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 1.dp,
            )
            Spacer(Modifier.height(20.dp))
            OptionalPermissionRow(
                icon = Icons.Outlined.Bluetooth,
                title = stringResource(Res.string.permissions_perm_bluetooth),
                desc = stringResource(Res.string.permissions_perm_bluetooth_desc),
                granted = state.hasBluetoothConnect,
                onGrant = onRequestBluetooth,
            )

            if (state.showRationale && !state.showSettingsPrompt) {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(Res.string.permissions_rationale),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }

        // ── Bottom action button ─────────────────────────────────────────────
        val isStep1Pending = !state.hasFineLocation
            || !state.hasActivityRecognition
            || !state.hasNotifications
        val isStep2Pending = !state.hasBackgroundLocation
        val isGpsPending = !state.isLocationServicesEnabled

        val showButton = state.showSettingsPrompt || isStep1Pending || isStep2Pending || isGpsPending

        if (showButton) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 32.dp),
            ) {
                val (label, isAmber) = when {
                    state.showSettingsPrompt ->
                        stringResource(Res.string.permissions_btn_settings) to true
                    isStep1Pending ->
                        stringResource(Res.string.permissions_btn_grant) to false
                    isStep2Pending ->
                        stringResource(Res.string.permissions_btn_background) to false
                    else ->
                        stringResource(Res.string.permissions_btn_location_settings) to false
                }
                Button(
                    onClick = onRequestPermissions,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAmber) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        contentColor = if (isAmber) {
                            MaterialTheme.colorScheme.onSecondary
                        } else {
                            MaterialTheme.colorScheme.onPrimary
                        },
                    ),
                ) {
                    Text(text = label, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun OptionalPermissionRow(
    icon: ImageVector,
    title: String,
    desc: String,
    granted: Boolean,
    onGrant: () -> Unit,
) {
    val surfaceColor by animateColorAsState(
        targetValue = if (granted) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        label = "opt_perm_row_bg",
    )
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = surfaceColor,
        onClick = { if (!granted) onGrant() },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = if (granted) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (granted) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (granted) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (granted) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Text(
                        text = if (granted) stringResource(Res.string.permissions_status_granted)
                        else stringResource(Res.string.permissions_status_optional),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (granted) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    desc: String,
    granted: Boolean,
) {
    val surfaceColor by animateColorAsState(
        targetValue = if (granted) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        label = "perm_row_bg",
    )
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = surfaceColor,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = if (granted) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (granted) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Status chip
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (granted) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (granted) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Text(
                        text = if (granted) {
                            stringResource(Res.string.permissions_status_granted)
                        } else {
                            stringResource(Res.string.permissions_status_pending)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (granted) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
