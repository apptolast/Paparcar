package io.apptolast.paparcar.presentation.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.SensorsOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.apptolast.paparcar.isBatteryOptimizationRelevant
import io.apptolast.paparcar.domain.error.PaparcarError
import io.apptolast.paparcar.domain.permissions.RequiredPermission
import io.apptolast.paparcar.domain.preferences.ThemeMode
import io.apptolast.paparcar.presentation.permissions.PermissionsFocus
import io.apptolast.paparcar.presentation.util.collectAsStateLifecycleAware
import io.apptolast.paparcar.ui.components.PapAlertDialog
import io.apptolast.paparcar.ui.components.PapCollapsingTopBarScaffold
import io.apptolast.paparcar.ui.components.PapDangerCard
import io.apptolast.paparcar.ui.components.PapDialogAccent
import io.apptolast.paparcar.ui.components.PapDivider
import io.apptolast.paparcar.ui.components.PapIconTile
import io.apptolast.paparcar.ui.components.PapInfoRow
import io.apptolast.paparcar.ui.components.PapListItem
import io.apptolast.paparcar.ui.components.PapNavChevron
import io.apptolast.paparcar.ui.components.PapNavRow
import io.apptolast.paparcar.ui.components.PapOutlinedCard
import io.apptolast.paparcar.ui.components.PapSectionHeader
import io.apptolast.paparcar.ui.components.PapSectionHeaderRow
import io.apptolast.paparcar.ui.components.PapScrollToTopButton
import io.apptolast.paparcar.ui.components.PapSwitchRow
import io.apptolast.paparcar.ui.theme.PapAlpha
import io.apptolast.paparcar.ui.theme.PapBorders
import io.apptolast.paparcar.ui.theme.PapCardLight
import io.apptolast.paparcar.ui.theme.PapInk
import io.apptolast.paparcar.ui.theme.PaparcarSpacing
import io.apptolast.paparcar.ui.theme.PapMotion
import io.apptolast.paparcar.ui.theme.PapShapes
import io.apptolast.paparcar.ui.theme.PaparcarType
import io.apptolast.paparcar.ui.theme.outlineSubtle
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.error_unknown
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
import paparcar.composeapp.generated.resources.settings_send_diagnostics
import paparcar.composeapp.generated.resources.settings_send_diagnostics_confirm_action
import paparcar.composeapp.generated.resources.settings_send_diagnostics_confirm_message
import paparcar.composeapp.generated.resources.settings_send_diagnostics_confirm_title
import paparcar.composeapp.generated.resources.settings_send_diagnostics_error
import paparcar.composeapp.generated.resources.settings_send_diagnostics_sent
import paparcar.composeapp.generated.resources.settings_danger_zone
import paparcar.composeapp.generated.resources.settings_danger_zone_subtitle
import paparcar.composeapp.generated.resources.settings_delete_account_cancel
import paparcar.composeapp.generated.resources.settings_delete_account_confirm_action
import paparcar.composeapp.generated.resources.settings_delete_account_confirm_message
import paparcar.composeapp.generated.resources.settings_delete_account_confirm_title
import paparcar.composeapp.generated.resources.settings_delete_account_error
import paparcar.composeapp.generated.resources.settings_detection_battery_desc
import paparcar.composeapp.generated.resources.settings_detection_battery_title
import paparcar.composeapp.generated.resources.settings_detection_bt_desc
import paparcar.composeapp.generated.resources.settings_detection_bt_title
import paparcar.composeapp.generated.resources.settings_detection_configured
import paparcar.composeapp.generated.resources.settings_detection_fix
import paparcar.composeapp.generated.resources.settings_detection_health_missing
import paparcar.composeapp.generated.resources.settings_detection_health_ok
import paparcar.composeapp.generated.resources.settings_detection_health_ok_desc
import paparcar.composeapp.generated.resources.settings_detection_reliability_reduced
import paparcar.composeapp.generated.resources.settings_detection_reliability_reduced_desc
import paparcar.composeapp.generated.resources.settings_detection_improve
import paparcar.composeapp.generated.resources.settings_detection_setup
import paparcar.composeapp.generated.resources.settings_distance_unit
import paparcar.composeapp.generated.resources.settings_distance_unit_desc
import paparcar.composeapp.generated.resources.settings_distance_unit_imperial
import paparcar.composeapp.generated.resources.settings_distance_unit_metric
import paparcar.composeapp.generated.resources.settings_licenses
import paparcar.composeapp.generated.resources.settings_notif_parking
import paparcar.composeapp.generated.resources.settings_notif_parking_desc
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
    onNavigateToVehicles: () -> Unit = {},
    onNavigateToAuth: () -> Unit = {},
    onNavigateToPermissions: (PermissionsFocus) -> Unit = {},
    onNavigateToBluetoothConfig: (String) -> Unit = {},
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onSetThemeMode: (ThemeMode) -> Unit = {},
    imperialUnits: Boolean = false,
    onToggleImperialUnits: (Boolean) -> Unit = {},
) {
    val viewModel: SettingsViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateLifecycleAware()
    val uriHandler = LocalUriHandler.current
    val snackbarHostState = remember { SnackbarHostState() }
    val msgDetectionStopped = stringResource(Res.string.home_det_stopped_msg)
    val msgTurnOn = stringResource(Res.string.home_det_stopped_action)
    val msgDeleteAccountError = stringResource(Res.string.settings_delete_account_error)
    val msgErrorUnknown = stringResource(Res.string.error_unknown)
    val msgDiagnosticsSent = stringResource(Res.string.settings_send_diagnostics_sent)
    val msgDiagnosticsError = stringResource(Res.string.settings_send_diagnostics_error)

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
                // The only error this screen emits today is the account-deletion failure —
                // surface it; anything new falls back to the generic message rather than
                // vanishing. [SETTINGS-AUDIT-REMEDIATION-001]
                is SettingsEffect.ShowError -> snackbarHostState.showSnackbar(
                    message = when (effect.error) {
                        is PaparcarError.Auth -> msgDeleteAccountError
                        // The diagnostics upload is this screen's only Network emitter today.
                        is PaparcarError.Network -> msgDiagnosticsError
                        else -> msgErrorUnknown
                    },
                    duration = SnackbarDuration.Long,
                )
                // Problem report landed — tell the user it's on us now. [SUPPORT-REPORT-SHIPS-THE-LOCAL-LOG-001]
                is SettingsEffect.DiagnosticsSent -> snackbarHostState.showSnackbar(
                    message = msgDiagnosticsSent,
                    duration = SnackbarDuration.Short,
                )
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
        themeMode = themeMode,
        onSetThemeMode = onSetThemeMode,
        imperialUnits = imperialUnits,
        onToggleImperialUnits = onToggleImperialUnits,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsContent(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onSetThemeMode: (ThemeMode) -> Unit = {},
    imperialUnits: Boolean = false,
    onToggleImperialUnits: (Boolean) -> Unit = {},
) {
    if (state.showSendDiagnosticsConfirmation) {
        // Consent before anything leaves the device: the body says WHAT is shipped (recent
        // technical log, approximate locations included). [SUPPORT-REPORT-SHIPS-THE-LOCAL-LOG-001]
        PapAlertDialog(
            onDismiss = { onIntent(SettingsIntent.DismissSendDiagnostics) },
            icon = Icons.Rounded.BugReport,
            title = stringResource(Res.string.settings_send_diagnostics_confirm_title),
            body = stringResource(Res.string.settings_send_diagnostics_confirm_message),
            primaryLabel = stringResource(Res.string.settings_send_diagnostics_confirm_action),
            onPrimary = { onIntent(SettingsIntent.ConfirmSendDiagnostics) },
            cancelLabel = stringResource(Res.string.settings_delete_account_cancel),
            isLoading = state.isSendingDiagnostics,
        )
    }

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

    val layoutDirection = LocalLayoutDirection.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Un salto programático al principio no pasa por el nested scroll, así que la cabecera no se
    // enteraría y quedaría retirada sobre una lista que ya está arriba. [UI-SCROLL-TO-TOP-001]
    var expandHeader by remember { mutableIntStateOf(0) }

    PapCollapsingTopBarScaffold(
        title = stringResource(Res.string.settings_title),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // Match Home's bottom-sheet tone so the page doesn't feel near-black.
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        expandKey = expandHeader,
    ) { headerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                // Padding de CONTENIDO (no del layout): la primera tarjeta arranca bajo el título y el
                // resto pasa por debajo de la status bar al scrollear. [UI-TOPBAR-COLLAPSE-001]
                contentPadding = PaddingValues(
                    top = headerPadding.calculateTopPadding() + CONTENT_V_PADDING,
                    bottom = headerPadding.calculateBottomPadding() + CONTENT_V_PADDING,
                    start = headerPadding.calculateStartPadding(layoutDirection) + CONTENT_H_PADDING,
                    end = headerPadding.calculateEndPadding(layoutDirection) + CONTENT_H_PADDING,
                ),
                verticalArrangement = Arrangement.spacedBy(PaparcarSpacing.sm),
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

                // ── 3 · Notifications ────────────────────────────────────────────
                // One honest switch. The "spot freed nearby" toggle and its master died in
                // SETTINGS-AUDIT-REMEDIATION-001: they persisted prefs no code ever read, and
                // the spot notification does not exist yet. This one gates the informative
                // "parking saved" notification at its single choke point (the notification
                // adapter); safety asks are never silenced from here.
                item { SectionHeaderMuted(stringResource(Res.string.settings_section_notifications)) }
                item {
                    PapOutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        PapSwitchRow(
                            icon = Icons.Rounded.Notifications,
                            label = stringResource(Res.string.settings_notif_parking),
                            description = stringResource(Res.string.settings_notif_parking_desc),
                            checked = state.notifyParkingDetected,
                            onCheckedChange = { onIntent(SettingsIntent.ToggleParkingDetectedNotif(it)) },
                            subtitleColor = settingsSubtitleColor(),
                            // Produced only by auto-detection — locked (not blanked) while OFF.
                            enabled = state.autoDetectParking,
                        )
                    }
                }

                // ── 4 · Appearance (theme) ───────────────────────────────────────
                // Language is NOT offered here: the picker never applied anything, and an in-app
                // one is a trap — pick the wrong language and you must find this row while unable
                // to read it. The OS owns it, and res/xml/locales_config.xml is what puts Paparcar
                // in Android's own per-app language list. [SETTINGS-LANGUAGE-LIVES-IN-THE-SYSTEM-001]
                item { SectionHeaderMuted(stringResource(Res.string.settings_section_appearance)) }
                item {
                    PapOutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        // Same quiet row anatomy as every other setting — the big theme
                        // swatches read as a foreign widget on this screen.
                        SettingsSegmentedRow(
                            icon = Icons.Rounded.DarkMode,
                            title = stringResource(Res.string.settings_theme_mode),
                            subtitle = stringResource(Res.string.settings_theme_mode_desc),
                            options = listOf(
                                ThemeMode.LIGHT to stringResource(Res.string.settings_theme_mode_light),
                                ThemeMode.DARK to stringResource(Res.string.settings_theme_mode_dark),
                                ThemeMode.SYSTEM to stringResource(Res.string.settings_theme_mode_system),
                            ),
                            selected = themeMode,
                            onSelect = onSetThemeMode,
                            // Each option wears the colour it will paint the app with.
                            optionIcon = { mode -> ThemeModeSwatch(mode) },
                        )
                    }
                }

                // ── 5 · Map ──────────────────────────────────────────────────────
                item { SectionHeaderMuted(stringResource(Res.string.settings_section_map)) }
                item {
                    PapOutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        // Exactly two unit systems, so both are offered by NAME, always visible,
                        // one tap — a segmented control, not an on/off "imperial" switch the user
                        // must already know how to read, nor a dropdown hiding the alternative.
                        SettingsSegmentedRow(
                            icon = Icons.Rounded.Map,
                            title = stringResource(Res.string.settings_distance_unit),
                            subtitle = stringResource(Res.string.settings_distance_unit_desc),
                            options = listOf(
                                false to stringResource(Res.string.settings_distance_unit_metric),
                                true to stringResource(Res.string.settings_distance_unit_imperial),
                            ),
                            selected = imperialUnits,
                            onSelect = onToggleImperialUnits,
                        )
                    }
                }

                // ── 6 · About ────────────────────────────────────────────────────
                item { SectionHeaderMuted(stringResource(Res.string.settings_section_about)) }
                item {
                    PapOutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            PapInfoRow(
                                icon = Icons.Rounded.Info,
                                label = stringResource(Res.string.settings_version),
                                value = state.appVersion,
                                valueColor = settingsSubtitleColor(),
                            )
                            PapDivider()
                            PapNavRow(
                                icon = Icons.Rounded.Lock,
                                label = stringResource(Res.string.settings_privacy),
                                onClick = { onIntent(SettingsIntent.OpenPrivacyPolicy) },
                            )
                            PapDivider()
                            PapNavRow(
                                icon = Icons.Rounded.Description,
                                label = stringResource(Res.string.settings_licenses),
                                onClick = { onIntent(SettingsIntent.OpenLicenses) },
                            )
                            PapDivider()
                            PapNavRow(
                                icon = Icons.Rounded.Email,
                                label = stringResource(Res.string.settings_contact),
                                onClick = { onIntent(SettingsIntent.OpenContact) },
                            )
                            PapDivider()
                            PapNavRow(
                                icon = Icons.Rounded.BugReport,
                                label = stringResource(Res.string.settings_send_diagnostics),
                                onClick = { onIntent(SettingsIntent.RequestSendDiagnostics) },
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

            PapScrollToTopButton(
                listState = listState,
                bottomPadding = headerPadding.calculateBottomPadding(),
                onClick = {
                    scope.launch { listState.animateScrollToItem(0) }
                    expandHeader++
                },
            )
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
            PapSwitchRow(
                icon = if (state.autoDetectParking) Icons.Rounded.Sensors else Icons.Rounded.SensorsOff,
                label = stringResource(Res.string.settings_auto_detect),
                description = stringResource(Res.string.settings_auto_detect_desc),
                checked = state.autoDetectParking,
                onCheckedChange = { onIntent(SettingsIntent.ToggleAutoDetect(it)) },
                subtitleColor = settingsSubtitleColor(),
            )
            PapDivider()
            // Health of the mandatory permissions + reliability of the device environment
            DetectionHealthRow(
                state = state,
                onFix = { onIntent(SettingsIntent.FixDetectionPermissions) },
                onFixReliability = { onIntent(SettingsIntent.FixDetectionReliability) },
            )
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
private fun DetectionHealthRow(state: SettingsState, onFix: () -> Unit, onFixReliability: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val healthy = state.detectionHealthy
    // Precedence: broken permissions (detection CANNOT run) → REDUCED reliability (detection runs
    // but the manufacturer is expected to freeze it) → green. [DET-RELIABILITY-001]
    val reduced = state.detectionReliabilityReduced
    val amber = !healthy || reduced
    PapListItem(
        leading = {
            if (!amber) {
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
        title = when {
            !healthy -> stringResource(Res.string.settings_detection_health_missing, firstMissingLabel(state))
            reduced -> stringResource(Res.string.settings_detection_reliability_reduced)
            else -> stringResource(Res.string.settings_detection_health_ok)
        },
        titleColor = if (amber) cs.secondary else cs.onSurface,
        subtitle = when {
            !healthy -> null
            reduced -> stringResource(Res.string.settings_detection_reliability_reduced_desc)
            else -> stringResource(Res.string.settings_detection_health_ok_desc)
        },
        subtitleColor = settingsSubtitleColor(),
        trailing = {
            if (amber) {
                OutlinedButton(
                    onClick = if (healthy) onFixReliability else onFix,
                    shape = PapShapes.button,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.secondary),
                    border = BorderStroke(PapBorders.thin, cs.secondary),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Rounded.Build, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.size(6.dp))
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
            PapNavChevron()
        }
    }
}

/** In-card group separator ("IMPROVE DETECTION") — the subsection level of the canonical header.
 *  Was a hand-rolled fork of the recipe; now the `dense` tier of [PapSectionHeaderRow], so the
 *  header roles keep living in one file. [SETTINGS-AUDIT-REMEDIATION-001] */
@Composable
private fun MiniHeader(text: String) {
    PapSectionHeaderRow(
        title = text,
        dense = true,
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
                            color = cs.onSurface.copy(alpha = PapAlpha.subtitle),
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
                    .height(48.dp),
                shape = PapShapes.button,
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
// Appearance — theme + language
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Settings row offering 2–5 mutually exclusive options by NAME: the shared list-item anatomy
 * (icon + title + subtitle) with an M3 segmented control underneath. The selected segment follows
 * the house chip recipe ([io.apptolast.paparcar.ui.components.chips.PaparcarFilterChip]):
 * primaryContainer fill + primary border/text — never the M3 default secondaryContainer.
 * [UI-COLOR-DOCTRINE-001]
 */
@Composable
private fun <K> SettingsSegmentedRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    options: List<Pair<K, String>>,
    selected: K,
    onSelect: (K) -> Unit,
    optionIcon: @Composable ((K) -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    Column {
        PapListItem(
            leading = { PapIconTile(icon = icon) },
            title = title,
            subtitle = subtitle,
            subtitleColor = settingsSubtitleColor(),
        )
        val segmentColors = SegmentedButtonDefaults.colors(
            activeContainerColor = cs.primaryContainer,
            activeContentColor = cs.primary,
            activeBorderColor = cs.primary,
            inactiveContainerColor = Color.Transparent,
            inactiveContentColor = cs.onSurface,
            inactiveBorderColor = cs.outline.copy(alpha = PapBorders.DEFAULT_OUTLINE_ALPHA),
        )
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
        ) {
            options.forEachIndexed { index, (key, label) ->
                SegmentedButton(
                    selected = key == selected,
                    onClick = { onSelect(key) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    colors = segmentColors,
                    icon = {
                        if (optionIcon != null) optionIcon(key)
                        else SegmentedButtonDefaults.Icon(active = key == selected)
                    },
                ) {
                    Text(label, style = PaparcarType.current.label)
                }
            }
        }
    }
}

/**
 * The colour a theme option paints the app with: light is the light card's white, dark is the app's
 * base ink, system is both halves. It stands where M3 puts the selection check — the chosen segment
 * already says it is chosen (primaryContainer fill + primary border + primary text), so the icon
 * slot is free to say what each option LOOKS like, on all three at once, before you tap.
 *
 * The ring is what keeps the white circle from vanishing into the light card, and the ink one from
 * vanishing into the dark surface. Decorative: the segment label names the option.
 * [UI-THEME-OPTION-SHOWS-ITS-THEME-001]
 */
@Composable
private fun ThemeModeSwatch(mode: ThemeMode) {
    val halves = when (mode) {
        ThemeMode.LIGHT -> listOf(PapCardLight, PapCardLight)
        ThemeMode.DARK -> listOf(PapInk, PapInk)
        ThemeMode.SYSTEM -> listOf(PapCardLight, PapInk)
    }
    Row(
        modifier = Modifier
            .size(THEME_SWATCH_DP.dp)
            .clip(CircleShape)
            .border(
                width = THEME_SWATCH_RING_DP.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = PapBorders.DEFAULT_OUTLINE_ALPHA),
                shape = CircleShape,
            ),
    ) {
        halves.forEach { half ->
            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(half))
        }
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
    PapDangerCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                subtitle,
                style = PaparcarType.current.caption,
                color = cs.onSurface.copy(alpha = PapAlpha.body),
            )
            Spacer(Modifier.size(10.dp))
            OutlinedButton(
                onClick = onClick,
                enabled = !deleting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = PapShapes.button,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.error),
                border = BorderStroke(PapBorders.medium, cs.error),
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

// Row primitives now live in ui/components/PapSettingRows.kt (PapSwitchRow / PapNavRow /
// PapInfoRow) — promoted so other screens stop re-implementing them. [UI-LIST-ITEM-001]
// [SETTINGS-AUDIT-REMEDIATION-001]

/** The muted subtitle tone shared by the Settings rows (onSurface @ [PapAlpha.muted]). */
@Composable
private fun settingsSubtitleColor(): Color =
    MaterialTheme.colorScheme.onSurface.copy(alpha = PapAlpha.muted)

// ─────────────────────────────────────────────────────────────────────────────
// Tokens
// ─────────────────────────────────────────────────────────────────────────────

/** Holgura del contenido con el borde de pantalla y con la cabecera/borde inferior. */
private val CONTENT_H_PADDING = PaparcarSpacing.lg
private val CONTENT_V_PADDING = PaparcarSpacing.sm

private const val AVATAR_DP = 56

/** Theme swatch: a dot, not a badge — three segments share one row and the label needs the width. */
private const val THEME_SWATCH_DP = 14
private const val THEME_SWATCH_RING_DP = 1

