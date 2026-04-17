package io.apptolast.paparcar.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swmansion.kmpmaps.core.MapType
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.settings_auto_detect
import paparcar.composeapp.generated.resources.settings_auto_detect_desc
import paparcar.composeapp.generated.resources.settings_cd_back
import paparcar.composeapp.generated.resources.settings_dark_mode
import paparcar.composeapp.generated.resources.settings_dark_mode_desc
import paparcar.composeapp.generated.resources.settings_nav_my_car
import paparcar.composeapp.generated.resources.settings_nav_my_car_desc
import paparcar.composeapp.generated.resources.settings_contact
import paparcar.composeapp.generated.resources.settings_licenses
import paparcar.composeapp.generated.resources.settings_notif_parking
import paparcar.composeapp.generated.resources.settings_profile_logout
import paparcar.composeapp.generated.resources.settings_profile_name_placeholder
import paparcar.composeapp.generated.resources.settings_section_profile
import paparcar.composeapp.generated.resources.settings_notif_parking_desc
import paparcar.composeapp.generated.resources.settings_notif_spot
import paparcar.composeapp.generated.resources.settings_notif_spot_desc
import paparcar.composeapp.generated.resources.settings_privacy
import paparcar.composeapp.generated.resources.settings_section_about
import paparcar.composeapp.generated.resources.settings_section_appearance
import paparcar.composeapp.generated.resources.settings_section_detection
import paparcar.composeapp.generated.resources.settings_section_notifications
import paparcar.composeapp.generated.resources.settings_distance_unit
import paparcar.composeapp.generated.resources.settings_distance_unit_desc
import paparcar.composeapp.generated.resources.settings_map_type
import paparcar.composeapp.generated.resources.settings_map_type_desc
import paparcar.composeapp.generated.resources.settings_map_type_normal
import paparcar.composeapp.generated.resources.settings_map_type_satellite
import paparcar.composeapp.generated.resources.settings_map_type_terrain
import paparcar.composeapp.generated.resources.settings_section_map
import paparcar.composeapp.generated.resources.settings_title
import paparcar.composeapp.generated.resources.settings_version

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToMyCar: () -> Unit = {},
    darkMode: Boolean = true,
    onToggleDarkMode: (Boolean) -> Unit = {},
    imperialUnits: Boolean = false,
    onToggleImperialUnits: (Boolean) -> Unit = {},
) {
    val viewModel: SettingsViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is SettingsEffect.NavigateBack -> onNavigateBack()
                is SettingsEffect.NavigateToMyCar -> onNavigateToMyCar()
                is SettingsEffect.OpenUrl -> { /* TODO: open browser */ }
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.settings_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.settings_cd_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── Profile ───────────────────────────────────────────────────────
            item {
                SettingsSectionHeader(stringResource(Res.string.settings_section_profile))
            }
            item {
                ProfileCard(
                    displayName = state.userProfile?.displayName
                        ?: stringResource(Res.string.settings_profile_name_placeholder),
                    email = state.userProfile?.email,
                    onLogout = { viewModel.handleIntent(SettingsIntent.Logout) },
                    logoutLabel = stringResource(Res.string.settings_profile_logout),
                )
            }

            // ── Appearance ────────────────────────────────────────────────────
            item {
                SettingsSectionHeader(stringResource(Res.string.settings_section_appearance))
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.Outlined.DarkMode,
                    label = stringResource(Res.string.settings_dark_mode),
                    description = stringResource(Res.string.settings_dark_mode_desc),
                    checked = darkMode,
                    onCheckedChange = onToggleDarkMode,
                )
            }

            // ── Map ───────────────────────────────────────────────────────────
            item {
                SettingsSectionHeader(stringResource(Res.string.settings_section_map))
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.Outlined.Map,
                    label = stringResource(Res.string.settings_distance_unit),
                    description = stringResource(Res.string.settings_distance_unit_desc),
                    checked = imperialUnits,
                    onCheckedChange = onToggleImperialUnits,
                )
            }
            item {
                val mapTypeLabels = mapOf(
                    MapType.NORMAL to stringResource(Res.string.settings_map_type_normal),
                    MapType.SATELLITE to stringResource(Res.string.settings_map_type_satellite),
                    MapType.TERRAIN to stringResource(Res.string.settings_map_type_terrain),
                )
                SettingsSegmentedItem(
                    icon = Icons.Outlined.Map,
                    label = stringResource(Res.string.settings_map_type),
                    description = stringResource(Res.string.settings_map_type_desc),
                    options = mapTypeLabels,
                    selected = state.mapType,
                    onSelect = { viewModel.handleIntent(SettingsIntent.SetMapType(it)) },
                )
            }

            // ── Detection ─────────────────────────────────────────────────────
            item {
                SettingsSectionHeader(stringResource(Res.string.settings_section_detection))
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.Outlined.DirectionsCar,
                    label = stringResource(Res.string.settings_auto_detect),
                    description = stringResource(Res.string.settings_auto_detect_desc),
                    checked = state.autoDetectParking,
                    onCheckedChange = {
                        viewModel.handleIntent(SettingsIntent.ToggleAutoDetect(it))
                    },
                )
            }
            item {
                SettingsNavItem(
                    icon = Icons.Outlined.Bluetooth,
                    label = stringResource(Res.string.settings_nav_my_car),
                    description = stringResource(Res.string.settings_nav_my_car_desc),
                    onClick = { viewModel.handleIntent(SettingsIntent.NavigateToMyCar) },
                )
            }

            // ── Notifications ─────────────────────────────────────────────────
            item {
                SettingsSectionHeader(stringResource(Res.string.settings_section_notifications))
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.Outlined.Notifications,
                    label = stringResource(Res.string.settings_notif_parking),
                    description = stringResource(Res.string.settings_notif_parking_desc),
                    checked = state.notifyParkingDetected,
                    onCheckedChange = {
                        viewModel.handleIntent(SettingsIntent.ToggleParkingDetectedNotif(it))
                    },
                )
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.Outlined.Notifications,
                    label = stringResource(Res.string.settings_notif_spot),
                    description = stringResource(Res.string.settings_notif_spot_desc),
                    checked = state.notifySpotFreed,
                    onCheckedChange = {
                        viewModel.handleIntent(SettingsIntent.ToggleSpotFreedNotif(it))
                    },
                )
            }

            // ── About ─────────────────────────────────────────────────────────
            item {
                SettingsSectionHeader(stringResource(Res.string.settings_section_about))
            }
            item {
                SettingsInfoItem(
                    icon = Icons.Outlined.Info,
                    label = stringResource(Res.string.settings_version),
                    value = state.appVersion,
                )
            }
            item {
                SettingsNavItem(
                    icon = Icons.Outlined.Shield,
                    label = stringResource(Res.string.settings_privacy),
                    onClick = { viewModel.handleIntent(SettingsIntent.OpenPrivacyPolicy) },
                )
            }
            item {
                SettingsNavItem(
                    icon = Icons.Outlined.VerifiedUser,
                    label = stringResource(Res.string.settings_licenses),
                    onClick = { viewModel.handleIntent(SettingsIntent.OpenLicenses) },
                )
            }
            item {
                SettingsNavItem(
                    icon = Icons.Outlined.Email,
                    label = stringResource(Res.string.settings_contact),
                    onClick = { viewModel.handleIntent(SettingsIntent.OpenContact) },
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 4.dp),
    )
}

@Composable
private fun SettingsSwitchItem(
    icon: ImageVector,
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SettingsIconBox(icon = icon)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun SettingsInfoItem(
    icon: ImageVector,
    label: String,
    value: String,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SettingsIconBox(icon = icon)
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun SettingsNavItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    description: String? = null,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SettingsIconBox(icon = icon)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SettingsIconBox(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Profile card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProfileCard(
    displayName: String,
    email: String?,
    logoutLabel: String,
    onLogout: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Avatar circle with initials
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "U",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (email != null) {
                        Text(
                            text = email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            TextButton(
                onClick = onLogout,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = logoutLabel,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun <T> SettingsSegmentedItem(
    icon: ImageVector,
    label: String,
    description: String,
    options: Map<T, String>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SettingsIconBox(icon = icon)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { (option, optionLabel) ->
                        FilterChip(
                            selected = option == selected,
                            onClick = { onSelect(option) },
                            label = {
                                Text(
                                    text = optionLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }
            }
        }
    }
}
