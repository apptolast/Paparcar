package io.apptolast.paparcar.presentation.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.SensorsOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.apptolast.paparcar.isBatteryOptimizationRelevant
import io.apptolast.paparcar.domain.permissions.RequiredPermission
import io.apptolast.paparcar.domain.preferences.ThemeMode
import io.apptolast.paparcar.presentation.permissions.PermissionsFocus
import io.apptolast.paparcar.presentation.util.collectAsStateLifecycleAware
import io.apptolast.paparcar.ui.components.PapAlertDialog
import io.apptolast.paparcar.ui.components.PapDialogAccent
import io.apptolast.paparcar.ui.components.PapDivider
import io.apptolast.paparcar.ui.components.PapIconTile
import io.apptolast.paparcar.ui.components.PapListItem
import io.apptolast.paparcar.ui.components.PapOutlinedCard
import io.apptolast.paparcar.ui.components.PapSectionHeader
import io.apptolast.paparcar.ui.theme.PapBorders
import io.apptolast.paparcar.ui.theme.PapCardLight
import io.apptolast.paparcar.ui.theme.PapInk
import io.apptolast.paparcar.ui.theme.PapInkHigh
import io.apptolast.paparcar.ui.theme.PapMotion
import io.apptolast.paparcar.ui.theme.PapShapes
import io.apptolast.paparcar.ui.theme.PapSurfaceLight
import io.apptolast.paparcar.ui.theme.PaparcarType
import io.apptolast.paparcar.ui.theme.outlineSubtle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_det_stopped_action
import paparcar.composeapp.generated.resources.home_det_stopped_msg
import paparcar.composeapp.generated.resources.permissions_perm_activity
import paparcar.composeapp.generated.resources.permissions_perm_background
import paparcar.composeapp.generated.resources.permissions_perm_location
import paparcar.composeapp.generated.resources.permissions_perm_location_services
import paparcar.composeapp.generated.resources.permissions_perm_notifications
import paparcar.composeapp.generated.resources.settings_auto_detect
import paparcar.composeapp.generated.resources.settings_auto_detect_desc
import paparcar.composeapp.generated.resources.settings_contact
import paparcar.composeapp.generated.resources.settings_danger_zone
import paparcar.composeapp.generated.resources.settings_danger_zone_subtitle
import paparcar.composeapp.generated.resources.settings_delete_account_cancel
import paparcar.composeapp.generated.resources.settings_delete_account_confirm_action
import paparcar.composeapp.generated.resources.settings_delete_account_confirm_message
import paparcar.composeapp.generated.resources.settings_delete_account_confirm_title
import paparcar.composeapp.generated.resources.settings_detection_battery_desc
import paparcar.composeapp.generated.resources.settings_detection_battery_title
import paparcar.composeapp.generated.resources.settings_detection_bt_desc
import paparcar.composeapp.generated.resources.settings_detection_bt_title
import paparcar.composeapp.generated.resources.settings_detection_configured
import paparcar.composeapp.generated.resources.settings_detection_fix
import paparcar.composeapp.generated.resources.settings_detection_health_missing
import paparcar.composeapp.generated.resources.settings_detection_health_ok
import paparcar.composeapp.generated.resources.settings_detection_health_ok_desc
import paparcar.composeapp.generated.resources.settings_detection_improve
import paparcar.composeapp.generated.resources.settings_detection_setup
import paparcar.composeapp.generated.resources.settings_distance_unit
import paparcar.composeapp.generated.resources.settings_distance_unit_desc
import paparcar.composeapp.generated.resources.settings_language
import paparcar.composeapp.generated.resources.settings_language_auto
import paparcar.composeapp.generated.resources.settings_language_desc
import paparcar.composeapp.generated.resources.settings_licenses
import paparcar.composeapp.generated.resources.settings_notif_parking
import paparcar.composeapp.generated.resources.settings_notif_parking_desc
import paparcar.composeapp.generated.resources.settings_notif_spot
import paparcar.composeapp.generated.resources.settings_notif_spot_desc
import paparcar.composeapp.generated.resources.settings_notifications_subtitle
import paparcar.composeapp.generated.resources.settings_notifications_title
import paparcar.composeapp.generated.resources.settings_privacy
import paparcar.composeapp.generated.resources.settings_profile_delete_account
import paparcar.composeapp.generated.resources.settings_profile_logout
import paparcar.composeapp.generated.resources.settings_profile_name_placeholder
import paparcar.composeapp.generated.resources.settings_section_about
import paparcar.composeapp.generated.resources.settings_section_appearance
import paparcar.composeapp.generated.resources.settings_section_detection
import paparcar.composeapp.generated.resources.settings_section_map
import paparcar.composeapp.generated.resources.settings_section_notifications
import paparcar.composeapp.generated.resources.settings_theme_mode
import paparcar.composeapp.generated.resources.settings_theme_mode_dark
import paparcar.composeapp.generated.resources.settings_theme_mode_desc
import paparcar.composeapp.generated.resources.settings_theme_mode_light
import paparcar.composeapp.generated.resources.settings_theme_mode_system
import paparcar.composeapp.generated.resources.settings_title
import paparcar.composeapp.generated.resources.settings_version

/**
 * Settings v3 — remodelled by importance (SETTINGS-REMODEL-001).
 *
 * Order: Account · **Detection & permissions** · Notifications · Appearance · Map · About · Danger.
 * Rows are grouped one card per section (dividers between rows) instead of a card-island per option.
 * New "Detection & permissions" section: master toggle + a permission-health row ("All set" / amber
 * "Missing X" + Fix) + optional one-time setup rows (car Bluetooth, unrestricted battery).
 * Notifications' "Parking detected" sub-row is disabled while auto-detection is OFF (real dependency).
 */
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToVehicles: () -> Unit = {},
    onNavigateToAuth: () -> Unit = {},
    onNavigateToPermissions: (PermissionsFocus) -> Unit = {},
    onNavigateToBluetoothConfig: (String) -> Unit = {},
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onSetThemeMode: (ThemeMode) -> Unit = {},
    imperialUnits: Boolean = false,
    onToggleImperialUnits: (Boolean) -> Unit = {},
    selectedLanguage: String = "auto",
    onSetLanguage: (String) -> Unit = {},
) {
    val viewModel: SettingsViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateLifecycleAware()
    val uriHandler = LocalUriHandler.current
    val snackbarHostState = remember { SnackbarHostState() }
    val msgDetectionStopped = stringResource(Res.string.home_det_stopped_msg)
    val msgTurnOn = stringResource(Res.string.home_det_stopped_action)

    // Refresh pref-backed fields AND runtime permissions every time the screen re-enters
    // composition, so a pref mutated elsewhere (BT-config flow) or a permission granted in the
    // permissions screen / system settings shows up the moment the user returns to Settings.
    LaunchedEffect(Unit) { viewModel.refreshFromPreferences() }

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is SettingsEffect.NavigateToVehicles -> onNavigateToVehicles()
                is SettingsEffect.NavigateToAuth -> onNavigateToAuth()
                is SettingsEffect.NavigateToPermissions -> onNavigateToPermissions(effect.focus)
                is SettingsEffect.NavigateToBluetoothConfig -> onNavigateToBluetoothConfig(effect.vehicleId)
                is SettingsEffect.OpenUrl -> uriHandler.openUri(effect.url)
                is SettingsEffect.ShowError -> { /* error handled via state */ }
                // Turn-off confirmation with one-tap undo, right where the user flipped it. [DET-TOGGLE-002]
                is SettingsEffect.DetectionTurnedOff -> {
                    val result = snackbarHostState.showSnackbar(
                        message = msgDetectionStopped,
                        actionLabel = msgTurnOn,
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.handleIntent(SettingsIntent.ToggleAutoDetect(true))
                    }
                }
            }
        }
    }

    SettingsContent(
        state = state,
        onIntent = viewModel::handleIntent,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        themeMode = themeMode,
        onSetThemeMode = onSetThemeMode,
        imperialUnits = imperialUnits,
        onToggleImperialUnits = onToggleImperialUnits,
        selectedLanguage = selectedLanguage,
        onSetLanguage = onSetLanguage,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsContent(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateBack: () -> Unit = {},
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onSetThemeMode: (ThemeMode) -> Unit = {},
    imperialUnits: Boolean = false,
    onToggleImperialUnits: (Boolean) -> Unit = {},
    selectedLanguage: String = "auto",
    onSetLanguage: (String) -> Unit = {},
) {
    if (state.showDeleteAccountConfirmation) {
        PapAlertDialog(
            onDismiss = { onIntent(SettingsIntent.DismissDeleteAccount) },
            icon = Icons.Rounded.Delete,
            title = stringResource(Res.string.settings_delete_account_confirm_title),
            body = stringResource(Res.string.settings_delete_account_confirm_message),
            primaryLabel = stringResource(Res.string.settings_delete_account_confirm_action),
            primaryLeadingIcon = Icons.Rounded.Delete,
            onPrimary = { onIntent(SettingsIntent.ConfirmDeleteAccount) },
            cancelLabel = stringResource(Res.string.settings_delete_account_cancel),
            accent = PapDialogAccent.Destructive,
            isLoading = state.isDeletingAccount,
        )
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.settings_title),
                        style = PaparcarType.current.screenTitle,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // Match Home's bottom-sheet tone so the page doesn't feel near-black.
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ── 1 · Account (no section header — the card speaks for itself) ─
            item {
                ProfileCardV2(
                    displayName = state.userProfile?.displayName
                        ?: stringResource(Res.string.settings_profile_name_placeholder),
                    email = state.userProfile?.email,
                    photoUrl = state.userProfile?.photoUrl,
                    onLogout = { onIntent(SettingsIntent.Logout) },
                )
            }

            // ── 2 · Detection & permissions (heart of the app → sits high) ───
            item { SectionHeaderMuted(stringResource(Res.string.settings_section_detection)) }
            item { DetectionSectionCard(state = state, onIntent = onIntent) }

            // ── 3 · Notifications (master + grouped subs) ───────────────────
            item { SectionHeaderMuted(stringResource(Res.string.settings_section_notifications)) }
            item {
                // Master = ON when either sub is ON. derivedStateOf so the card only recomposes
                // when the boolean actually flips (not on every other state field change).
                val masterOn by remember(state.notifyParkingDetected, state.notifySpotFreed) {
                    derivedStateOf { state.notifyParkingDetected || state.notifySpotFreed }
                }
                NotificationsGroupCard(
                    masterOn = masterOn,
                    onMasterChange = { onIntent(SettingsIntent.ToggleMasterNotifications(it)) },
                    parkingOn = state.notifyParkingDetected,
                    onParkingChange = { onIntent(SettingsIntent.ToggleParkingDetectedNotif(it)) },
                    // "Parking detected" only fires from auto-detection — dim + lock it when OFF.
                    parkingEnabled = state.autoDetectParking,
                    spotOn = state.notifySpotFreed,
                    onSpotChange = { onIntent(SettingsIntent.ToggleSpotFreedNotif(it)) },
                )
            }

            // ── 4 · Appearance (theme + language) ────────────────────────────
            item { SectionHeaderMuted(stringResource(Res.string.settings_section_appearance)) }
            item {
                val autoLabel = stringResource(Res.string.settings_language_auto)
                val languageOptions = remember(autoLabel) {
                    linkedMapOf(
                        "auto" to autoLabel,
                        "en" to "English", "es" to "Español", "it" to "Italiano",
                        "pt" to "Português", "fr" to "Français", "de" to "Deutsch",
                        "nl" to "Nederlands", "pl" to "Polski", "ro" to "Română",
                    )
                }
                PapOutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        ThemeBlock(selected = themeMode, onSelect = onSetThemeMode)
                        PapDivider()
                        LanguageDropdownRow(
                            label = stringResource(Res.string.settings_language),
                            description = stringResource(Res.string.settings_language_desc),
                            options = languageOptions,
                            selected = selectedLanguage,
                            onSelect = onSetLanguage,
                        )
                    }
                }
            }

            // ── 5 · Map ──────────────────────────────────────────────────────
            item { SectionHeaderMuted(stringResource(Res.string.settings_section_map)) }
            item {
                PapOutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    SwitchRow(
                        icon = Icons.Rounded.Map,
                        label = stringResource(Res.string.settings_distance_unit),
                        description = stringResource(Res.string.settings_distance_unit_desc),
                        checked = imperialUnits,
                        onCheckedChange = onToggleImperialUnits,
                    )
                }
            }

            // ── 6 · About ────────────────────────────────────────────────────
            item { SectionHeaderMuted(stringResource(Res.string.settings_section_about)) }
            item {
                PapOutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        InfoRow(
                            icon = Icons.Rounded.Info,
                            label = stringResource(Res.string.settings_version),
                            value = state.appVersion,
                        )
                        PapDivider()
                        NavRow(
                            icon = Icons.Rounded.Lock,
                            label = stringResource(Res.string.settings_privacy),
                            onClick = { onIntent(SettingsIntent.OpenPrivacyPolicy) },
                        )
                        PapDivider()
                        NavRow(
                            icon = Icons.Rounded.Description,
                            label = stringResource(Res.string.settings_licenses),
                            onClick = { onIntent(SettingsIntent.OpenLicenses) },
                        )
                        PapDivider()
                        NavRow(
                            icon = Icons.Rounded.Email,
                            label = stringResource(Res.string.settings_contact),
                            onClick = { onIntent(SettingsIntent.OpenContact) },
                        )
                    }
                }
            }

            // ── 7 · Danger zone ──────────────────────────────────────────────
            item { SectionHeaderDanger(stringResource(Res.string.settings_danger_zone)) }
            item {
                DangerZoneCard(
                    deleting = state.isDeletingAccount,
                    subtitle = stringResource(Res.string.settings_danger_zone_subtitle),
                    label = stringResource(Res.string.settings_profile_delete_account),
                    onClick = { onIntent(SettingsIntent.RequestDeleteAccount) },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section headers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeaderMuted(title: String) {
    PapSectionHeader(
        title = title,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp, start = 4.dp),
    )
}

@Composable
private fun SectionHeaderDanger(title: String) {
    PapSectionHeader(
        title = title,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp, start = 4.dp),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Detection & permissions section
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DetectionSectionCard(state: SettingsState, onIntent: (SettingsIntent) -> Unit) {
    PapOutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            // Master toggle
            SwitchRow(
                icon = if (state.autoDetectParking) Icons.Rounded.Sensors else Icons.Rounded.SensorsOff,
                label = stringResource(Res.string.settings_auto_detect),
                description = stringResource(Res.string.settings_auto_detect_desc),
                checked = state.autoDetectParking,
                onCheckedChange = { onIntent(SettingsIntent.ToggleAutoDetect(it)) },
            )
            PapDivider()
            // Health of the mandatory permissions
            DetectionHealthRow(state = state, onFix = { onIntent(SettingsIntent.FixDetectionPermissions) })
            PapDivider()
            // Optional one-time setup rows — improvements, never blockers
            MiniHeader(stringResource(Res.string.settings_detection_improve))
            ImprovementRow(
                icon = Icons.Rounded.Bluetooth,
                title = stringResource(Res.string.settings_detection_bt_title),
                description = stringResource(Res.string.settings_detection_bt_desc),
                configured = state.btDeviceConfigured,
                onClick = { onIntent(SettingsIntent.ConfigureBluetooth) },
            )
            // Battery exemption is Android-only (Doze/OEM killers) — hidden on iOS. [SETTINGS-REMODEL-001]
            if (isBatteryOptimizationRelevant) {
                PapDivider()
                ImprovementRow(
                    icon = Icons.Rounded.BatteryFull,
                    title = stringResource(Res.string.settings_detection_battery_title),
                    description = stringResource(Res.string.settings_detection_battery_desc),
                    configured = state.isBatteryOptimizationExempt,
                    onClick = { onIntent(SettingsIntent.ConfigureBattery) },
                )
            }
        }
    }
}

@Composable
private fun DetectionHealthRow(state: SettingsState, onFix: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val healthy = state.detectionHealthy
    PapListItem(
        leading = {
            if (healthy) {
                PapIconTile(icon = Icons.Rounded.CheckCircle)
            } else {
                // Amber (secondary) — a fixable setup gap, not a hard blocker (PRODUCER perms don't
                // block the app; red is reserved for real blockers/destructive). [SETTINGS-REMODEL-001]
                PapIconTile(
                    icon = Icons.Rounded.Warning,
                    container = cs.secondaryContainer,
                    tint = cs.secondary,
                )
            }
        },
        title = if (healthy) {
            stringResource(Res.string.settings_detection_health_ok)
        } else {
            stringResource(Res.string.settings_detection_health_missing, firstMissingLabel(state))
        },
        titleColor = if (healthy) cs.onSurface else cs.secondary,
        subtitle = if (healthy) stringResource(Res.string.settings_detection_health_ok_desc) else null,
        subtitleColor = settingsSubtitleColor(),
        trailing = {
            if (!healthy) {
                OutlinedButton(
                    onClick = onFix,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.secondary),
                    border = BorderStroke(PapBorders.thin, cs.secondary),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        stringResource(Res.string.settings_detection_fix),
                        style = PaparcarType.current.cta,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
    )
}

/**
 * The single most fundamental missing detection requirement, for the "Missing %s" health line.
 * GPS master toggle first (nothing works without it), then permissions in dependency order.
 */
@Composable
private fun firstMissingLabel(state: SettingsState): String {
    if (!state.isLocationServicesEnabled) {
        return stringResource(Res.string.permissions_perm_location_services)
    }
    val first = HEALTH_PRIORITY.firstOrNull { it in state.missingDetectionPermissions }
    return when (first) {
        RequiredPermission.FOREGROUND_LOCATION -> stringResource(Res.string.permissions_perm_location)
        RequiredPermission.BACKGROUND_LOCATION -> stringResource(Res.string.permissions_perm_background)
        RequiredPermission.ACTIVITY_RECOGNITION -> stringResource(Res.string.permissions_perm_activity)
        RequiredPermission.NOTIFICATIONS -> stringResource(Res.string.permissions_perm_notifications)
        null -> ""
    }
}

private val HEALTH_PRIORITY = listOf(
    RequiredPermission.FOREGROUND_LOCATION,
    RequiredPermission.BACKGROUND_LOCATION,
    RequiredPermission.ACTIVITY_RECOGNITION,
    RequiredPermission.NOTIFICATIONS,
)

/** Optional "improve detection" row — setup-once with a status, NOT a toggle. */
@Composable
private fun ImprovementRow(
    icon: ImageVector,
    title: String,
    description: String,
    configured: Boolean,
    onClick: () -> Unit,
) {
    PapListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leading = { PapIconTile(icon = icon) },
        title = title,
        subtitle = description,
        subtitleColor = settingsSubtitleColor(),
        trailing = { SetupStatusTrailing(configured = configured) },
    )
}

@Composable
private fun SetupStatusTrailing(configured: Boolean) {
    val cs = MaterialTheme.colorScheme
    if (configured) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(Res.string.settings_detection_configured),
                style = PaparcarType.current.label,
                color = cs.primary,
                fontWeight = FontWeight.Bold,
            )
            Icon(Icons.Rounded.Check, contentDescription = null, tint = cs.primary, modifier = Modifier.size(18.dp))
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                stringResource(Res.string.settings_detection_setup),
                style = PaparcarType.current.label,
                color = cs.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = cs.onSurface.copy(alpha = CHEVRON_DIM_ALPHA),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Small in-card uppercase sub-label ("IMPROVE DETECTION"). Not a top-level section header. */
@Composable
private fun MiniHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = PaparcarType.current.label,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Account — Profile card (avatar + logout)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProfileCardV2(
    displayName: String,
    email: String?,
    photoUrl: String?,
    onLogout: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PapShapes.card,
        color = cs.surfaceContainerHigh,
        border = outlineSubtle,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfileAvatar(displayName = displayName, photoUrl = photoUrl)
                Spacer(Modifier.size(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        style = PaparcarType.current.cardTitle,
                        fontWeight = FontWeight.Bold,
                        color = cs.onSurface,
                    )
                    if (email != null) {
                        Text(
                            text = email,
                            style = PaparcarType.current.caption,
                            color = cs.onSurface.copy(alpha = SUBTITLE_ALPHA),
                        )
                    }
                }
            }

            // Logout outlined
            Spacer(Modifier.size(16.dp))
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(10.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text(
                    stringResource(Res.string.settings_profile_logout),
                    style = PaparcarType.current.cta,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/**
 * Profile avatar: loads [photoUrl] when present, falling back to the display
 * name's initial on a brand-green disc while loading, on error, or when no URL
 * exists. The fallback is identical to the no-photo state so there's never an
 * empty circle. [photoUrl] image fills the disc (crop-to-fill).
 */
@Composable
private fun ProfileAvatar(displayName: String, photoUrl: String?) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(AVATAR_DP.dp)
            .clip(CircleShape)
            .background(cs.primary),
        contentAlignment = Alignment.Center,
    ) {
        // Initial sits underneath; the photo (when it resolves) paints over it,
        // so loading/error/no-URL all degrade gracefully to the initial.
        Text(
            text = displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "U",
            style = PaparcarType.current.sectionTitle,
            fontWeight = FontWeight.ExtraBold,
            color = cs.onPrimary,
        )
        if (!photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Appearance — theme block (mini previews) + language
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ThemeBlock(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            stringResource(Res.string.settings_theme_mode),
            style = PaparcarType.current.body,
            fontWeight = FontWeight.SemiBold,
            color = cs.onSurface,
        )
        Text(
            stringResource(Res.string.settings_theme_mode_desc),
            style = PaparcarType.current.caption,
            color = cs.onSurface.copy(alpha = SUBTITLE_ALPHA),
        )
        Spacer(Modifier.size(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ThemePreview(
                mode = ThemeMode.LIGHT,
                selected = selected == ThemeMode.LIGHT,
                label = stringResource(Res.string.settings_theme_mode_light),
                onClick = { onSelect(ThemeMode.LIGHT) },
                modifier = Modifier.weight(1f),
            )
            ThemePreview(
                mode = ThemeMode.DARK,
                selected = selected == ThemeMode.DARK,
                label = stringResource(Res.string.settings_theme_mode_dark),
                onClick = { onSelect(ThemeMode.DARK) },
                modifier = Modifier.weight(1f),
            )
            ThemePreview(
                mode = ThemeMode.SYSTEM,
                selected = selected == ThemeMode.SYSTEM,
                label = stringResource(Res.string.settings_theme_mode_system),
                onClick = { onSelect(ThemeMode.SYSTEM) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ThemePreview(
    mode: ThemeMode,
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val (bg, surfaceColor) = when (mode) {
        ThemeMode.LIGHT  -> THEME_LIGHT_BG to THEME_LIGHT_SURFACE
        ThemeMode.DARK   -> THEME_DARK_BG to THEME_DARK_SURFACE
        ThemeMode.SYSTEM -> THEME_LIGHT_BG to THEME_DARK_SURFACE
    }
    // Smoothly grow/recolour the selection ring instead of a hard swap.
    val borderColor by animateColorAsState(
        targetValue = if (selected) cs.primary else cs.outline.copy(alpha = PapBorders.DEFAULT_OUTLINE_ALPHA),
        animationSpec = PapMotion.fast(),
        label = "theme_preview_border_color",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (selected) PapBorders.strong else PapBorders.thin,
        animationSpec = PapMotion.fast(),
        label = "theme_preview_border_width",
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(THEME_PREVIEW_RATIO),
            shape = RoundedCornerShape(10.dp),
            color = bg,
            border = BorderStroke(borderWidth, borderColor),
        ) {
            if (mode == ThemeMode.SYSTEM) {
                // diagonal split light/dark
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                0.0f to THEME_LIGHT_BG,
                                0.5f to THEME_LIGHT_BG,
                                0.5f to THEME_DARK_BG,
                                1.0f to THEME_DARK_BG,
                            ),
                        ),
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(bg)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                            .align(Alignment.BottomCenter)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(surfaceColor),
                    )
                }
            }
        }
        Spacer(Modifier.size(6.dp))
        Text(
            text = label,
            style = PaparcarType.current.label,
            fontWeight = FontWeight.Bold,
            color = if (selected) cs.primary else cs.onSurface.copy(alpha = SUBTITLE_ALPHA),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageDropdownRow(
    label: String,
    description: String,
    options: Map<String, String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var expanded by rememberSaveable { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        PapListItem(
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
            leading = { PapIconTile(icon = Icons.Rounded.Language) },
            title = label,
            subtitleSlot = {
                Text(description, style = PaparcarType.current.caption, color = settingsSubtitleColor())
                Spacer(Modifier.size(6.dp))
                Text(
                    text = options[selected] ?: selected,
                    style = PaparcarType.current.caption,
                    color = cs.primary,
                    fontWeight = FontWeight.Bold,
                )
            },
            trailing = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (tag, displayName) ->
                DropdownMenuItem(
                    text = { Text(displayName) },
                    onClick = { onSelect(tag); expanded = false },
                    leadingIcon = if (tag == selected) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, tint = cs.primary, modifier = Modifier.size(18.dp)) }
                    } else null,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Notifications group card — master + subs
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NotificationsGroupCard(
    masterOn: Boolean,
    onMasterChange: (Boolean) -> Unit,
    parkingOn: Boolean,
    onParkingChange: (Boolean) -> Unit,
    parkingEnabled: Boolean,
    spotOn: Boolean,
    onSpotChange: (Boolean) -> Unit,
) {
    PapOutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            PapListItem(
                leading = { PapIconTile(icon = Icons.Rounded.Notifications) },
                title = stringResource(Res.string.settings_notifications_title),
                subtitle = stringResource(Res.string.settings_notifications_subtitle),
                subtitleColor = settingsSubtitleColor(),
                trailing = { Switch(checked = masterOn, onCheckedChange = onMasterChange) },
            )
            // Accordion: sub-toggles grow/collapse from the top edge under the master
            // switch instead of snapping in. [MOTION-POLISH-001]
            AnimatedVisibility(
                visible = masterOn,
                enter = expandVertically(PapMotion.medium(), expandFrom = Alignment.Top) + fadeIn(PapMotion.medium()),
                exit = shrinkVertically(PapMotion.medium(), shrinkTowards = Alignment.Top) + fadeOut(PapMotion.medium()),
            ) {
                Column {
                    PapDivider()
                    SubNotifRow(
                        label = stringResource(Res.string.settings_notif_parking),
                        description = stringResource(Res.string.settings_notif_parking_desc),
                        checked = parkingOn,
                        onCheckedChange = onParkingChange,
                        // "Parking detected" is produced by auto-detection — dim + lock while OFF.
                        enabled = parkingEnabled,
                    )
                    PapDivider()
                    SubNotifRow(
                        label = stringResource(Res.string.settings_notif_spot),
                        description = stringResource(Res.string.settings_notif_spot_desc),
                        checked = spotOn,
                        onCheckedChange = onSpotChange,
                    )
                }
            }
        }
    }
}

@Composable
private fun SubNotifRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val cs = MaterialTheme.colorScheme
    val contentAlpha = if (enabled) 1f else DISABLED_ROW_ALPHA
    Row(
        modifier = Modifier.padding(start = SUB_NOTIF_INDENT_DP.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = PaparcarType.current.caption,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface.copy(alpha = contentAlpha),
            )
            Text(
                description,
                style = PaparcarType.current.label,
                color = cs.onSurface.copy(alpha = SUBTITLE_ALPHA_STRONG * contentAlpha),
            )
        }
        Switch(checked = checked && enabled, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Danger zone
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DangerZoneCard(
    deleting: Boolean,
    subtitle: String,
    label: String,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PapShapes.card,
        color = cs.errorContainer.copy(alpha = DANGER_BG_ALPHA),
        border = BorderStroke(1.5.dp, cs.error.copy(alpha = DANGER_BORDER_ALPHA)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                subtitle,
                style = PaparcarType.current.caption,
                color = cs.onSurface.copy(alpha = DANGER_SUBTITLE_ALPHA),
            )
            Spacer(Modifier.size(10.dp))
            OutlinedButton(
                onClick = onClick,
                enabled = !deleting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.error),
                border = BorderStroke(1.5.dp, cs.error),
            ) {
                AnimatedContent(
                    targetState = deleting,
                    transitionSpec = { fadeIn(PapMotion.fast()) togetherWith fadeOut(PapMotion.fast()) },
                    label = "delete_account_button",
                ) { isDeleting ->
                    if (isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = cs.error,
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                            // Button text → cta (Inter), the app's button convention. [TYPO-AUDIT-001]
                            Text(label, style = PaparcarType.current.cta)
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reused row primitives — built on PapListItem (no own container; stack in a card)
// [UI-LIST-ITEM-001] · grouped one card per section [SETTINGS-REMODEL-001]
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SwitchRow(
    icon: ImageVector,
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    PapListItem(
        leading = { PapIconTile(icon = icon) },
        title = label,
        subtitle = description,
        subtitleColor = settingsSubtitleColor(),
        trailing = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
    )
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    PapListItem(
        leading = { PapIconTile(icon = icon) },
        title = label,
        trailing = {
            // The only value shown here is the app version — a data token → Barlow (metadata). [TYPO-AUDIT-001]
            Text(value, style = PaparcarType.current.metadata, color = settingsSubtitleColor())
        },
    )
}

@Composable
private fun NavRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    description: String? = null,
) {
    val cs = MaterialTheme.colorScheme
    PapListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leading = { PapIconTile(icon = icon) },
        title = label,
        subtitle = description,
        subtitleColor = settingsSubtitleColor(),
        trailing = {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = cs.onSurface.copy(alpha = CHEVRON_DIM_ALPHA),
                modifier = Modifier.size(20.dp),
            )
        },
    )
}

/** The muted subtitle tone shared by the Settings rows (onSurface @ 0.5). */
@Composable
private fun settingsSubtitleColor(): Color =
    MaterialTheme.colorScheme.onSurface.copy(alpha = SUBTITLE_ALPHA_STRONG)

// ─────────────────────────────────────────────────────────────────────────────
// Tokens
// ─────────────────────────────────────────────────────────────────────────────

private const val AVATAR_DP = 56

private const val THEME_PREVIEW_RATIO = 0.85f

/** Sub-notification rows align their text with the parent row's text column:
 *  16 (card padding) + 40 (PapIconTile default) + 14 (row gap). */
private const val SUB_NOTIF_INDENT_DP = 16 + 40 + 14

private const val SUBTITLE_ALPHA = 0.55f
private const val SUBTITLE_ALPHA_STRONG = 0.5f
private const val CHEVRON_DIM_ALPHA = 0.3f
private const val DISABLED_ROW_ALPHA = 0.38f
private const val DANGER_BG_ALPHA = 0.15f
private const val DANGER_BORDER_ALPHA = 0.7f
private const val DANGER_SUBTITLE_ALPHA = 0.6f

// Mirror the *real* theme surfaces so the swatches (and the System diagonal)
// preview exactly what the app renders — not a stand-in greenish palette.
private val THEME_LIGHT_BG = PapSurfaceLight   // light page background (#F0F4FB)
private val THEME_LIGHT_SURFACE = PapCardLight // light card surface (#FFFFFF)
private val THEME_DARK_BG = PapInk             // dark app base (#0D1117)
private val THEME_DARK_SURFACE = PapInkHigh    // dark card surface (#1A2232)
