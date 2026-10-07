package com.rndeveloper.paparcar.presentation.settings

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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.SensorsOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import com.rndeveloper.paparcar.isBatteryOptimizationRelevant
import com.rndeveloper.paparcar.isBtStrategyRelevant
import com.rndeveloper.paparcar.domain.diagnostics.DiagnosticsReport
import com.rndeveloper.paparcar.domain.entitlement.PaywallReason
import com.rndeveloper.paparcar.domain.error.PaparcarError
import com.rndeveloper.paparcar.domain.permissions.RequiredPermission
import com.rndeveloper.paparcar.domain.preferences.ThemeMode
import com.rndeveloper.paparcar.presentation.permissions.PermissionsFocus
import com.rndeveloper.paparcar.ui.components.PapAlertDialog
import com.rndeveloper.paparcar.ui.components.PapCollapsingTopBarScaffold
import com.rndeveloper.paparcar.ui.components.PapDangerCard
import com.rndeveloper.paparcar.ui.components.PapDialogAccent
import com.rndeveloper.paparcar.ui.components.PapDialogAction
import com.rndeveloper.paparcar.ui.components.PapDivider
import com.rndeveloper.paparcar.ui.components.PapIconTile
import com.rndeveloper.paparcar.ui.components.PapInfoRow
import com.rndeveloper.paparcar.ui.components.PapListItem
import com.rndeveloper.paparcar.ui.components.PapNavChevron
import com.rndeveloper.paparcar.ui.components.PapNavRow
import com.rndeveloper.paparcar.ui.components.PapOutlinedCard
import com.rndeveloper.paparcar.ui.components.PapSegmentedChoice
import com.rndeveloper.paparcar.ui.components.PapPremiumEmphasis
import com.rndeveloper.paparcar.ui.components.PapPremiumSurface
import com.rndeveloper.paparcar.ui.components.PapSectionHeader
import com.rndeveloper.paparcar.ui.components.PapSectionHeaderRow
import com.rndeveloper.paparcar.ui.components.PapScrollToTopButton
import com.rndeveloper.paparcar.ui.components.PapShimmerBox
import com.rndeveloper.paparcar.ui.components.PapSwitchRow
import com.rndeveloper.paparcar.ui.components.PapTextField
import com.rndeveloper.paparcar.ui.theme.PapAlpha
import com.rndeveloper.paparcar.ui.theme.PapBorders
import com.rndeveloper.paparcar.ui.theme.PapCardLight
import com.rndeveloper.paparcar.ui.theme.PapInk
import com.rndeveloper.paparcar.ui.theme.PaparcarSpacing
import com.rndeveloper.paparcar.ui.theme.PapMotion
import com.rndeveloper.paparcar.ui.theme.PapShapes
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import com.rndeveloper.paparcar.ui.theme.outlineSubtle
import com.rndeveloper.paparcar.ui.theme.PapColor
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
import paparcar.composeapp.generated.resources.permissions_perm_precise
import paparcar.composeapp.generated.resources.permissions_perm_location_services
import paparcar.composeapp.generated.resources.permissions_perm_notifications
import paparcar.composeapp.generated.resources.settings_add_widget
import paparcar.composeapp.generated.resources.settings_auto_detect
import paparcar.composeapp.generated.resources.settings_auto_detect_desc
import paparcar.composeapp.generated.resources.settings_contact
import paparcar.composeapp.generated.resources.settings_send_diagnostics
import paparcar.composeapp.generated.resources.settings_send_diagnostics_confirm_action
import paparcar.composeapp.generated.resources.settings_send_diagnostics_confirm_message
import paparcar.composeapp.generated.resources.settings_send_diagnostics_confirm_title
import paparcar.composeapp.generated.resources.settings_send_diagnostics_error
import paparcar.composeapp.generated.resources.settings_send_diagnostics_message_label
import paparcar.composeapp.generated.resources.settings_send_diagnostics_message_placeholder
import paparcar.composeapp.generated.resources.settings_send_diagnostics_no_reply
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
import paparcar.composeapp.generated.resources.settings_replay_first_steps
import paparcar.composeapp.generated.resources.settings_replay_first_steps_done
import paparcar.composeapp.generated.resources.settings_notif_parking
import paparcar.composeapp.generated.resources.settings_notif_parking_desc
import paparcar.composeapp.generated.resources.settings_privacy
import paparcar.composeapp.generated.resources.settings_profile_delete_account
import paparcar.composeapp.generated.resources.settings_profile_email_unverified
import paparcar.composeapp.generated.resources.settings_profile_email_verified
import paparcar.composeapp.generated.resources.settings_profile_email_verify_cta
import paparcar.composeapp.generated.resources.settings_profile_email_verify_error
import paparcar.composeapp.generated.resources.settings_profile_email_verify_sent
import paparcar.composeapp.generated.resources.settings_profile_email_verify_why
import paparcar.composeapp.generated.resources.settings_profile_logout
import paparcar.composeapp.generated.resources.settings_profile_name_placeholder
import paparcar.composeapp.generated.resources.settings_section_about
import paparcar.composeapp.generated.resources.settings_section_appearance
import paparcar.composeapp.generated.resources.settings_section_detection
import paparcar.composeapp.generated.resources.settings_section_map
import paparcar.composeapp.generated.resources.settings_notif_fuel_alerts
import paparcar.composeapp.generated.resources.settings_notif_fuel_alerts_desc
import paparcar.composeapp.generated.resources.settings_notif_month_recap
import paparcar.composeapp.generated.resources.settings_notif_month_recap_desc
import paparcar.composeapp.generated.resources.settings_notif_zone_alerts
import paparcar.composeapp.generated.resources.settings_sync_routes
import paparcar.composeapp.generated.resources.settings_sync_routes_desc
import paparcar.composeapp.generated.resources.settings_notif_zone_alerts_desc
import paparcar.composeapp.generated.resources.settings_plus_badge_active
import paparcar.composeapp.generated.resources.settings_plus_subtitle_active
import paparcar.composeapp.generated.resources.settings_plus_subtitle_free
import paparcar.composeapp.generated.resources.settings_plus_title
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
    onNavigateToLicenses: () -> Unit = {},
    onOpenPaywall: (PaywallReason) -> Unit = {},
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onSetThemeMode: (ThemeMode) -> Unit = {},
    imperialUnits: Boolean = false,
    onToggleImperialUnits: (Boolean) -> Unit = {},
) {
    val viewModel: SettingsViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val snackbarHostState = remember { SnackbarHostState() }
    val msgDetectionStopped = stringResource(Res.string.home_det_stopped_msg)
    val msgTurnOn = stringResource(Res.string.home_det_stopped_action)
    val msgDeleteAccountError = stringResource(Res.string.settings_delete_account_error)
    val msgVerifyEmailError = stringResource(Res.string.settings_profile_email_verify_error)
    val msgErrorUnknown = stringResource(Res.string.error_unknown)
    val msgDiagnosticsSent = stringResource(Res.string.settings_send_diagnostics_sent)
    val msgDiagnosticsError = stringResource(Res.string.settings_send_diagnostics_error)
    val msgFirstStepsRestarted = stringResource(Res.string.settings_replay_first_steps_done)

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
                is SettingsEffect.NavigateToLicenses -> onNavigateToLicenses()
                is SettingsEffect.NavigateToPaywall -> onOpenPaywall(effect.reason)
                is SettingsEffect.OpenUrl -> uriHandler.openUri(effect.url)
                // Cada error con SU mensaje, y lo nuevo cae en el genérico antes que
                // desaparecer. [SETTINGS-AUDIT-REMEDIATION-001]
                // ⛔ El caso del correo va ANTES de la rama `Auth` entera: colapsarla en
                // «no se pudo borrar la cuenta» hacía que un envío fallido se leyera como un
                // borrado fallido. [AUTH-AN-UNCONFIRMED-EMAIL-MUST-SAY-SO-001]
                is SettingsEffect.ShowError -> snackbarHostState.showSnackbar(
                    message = when (effect.error) {
                        is PaparcarError.Auth.VerificationEmailFailed -> msgVerifyEmailError
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
                // The checklist is back, but it lives on Home — say so, or the row reads as inert.
                // [ONBOARDING-FIRST-STEPS-ARE-GUIDED-NOT-TOLD-001]
                is SettingsEffect.FirstStepsRestarted -> snackbarHostState.showSnackbar(
                    message = msgFirstStepsRestarted,
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
fun SettingsContent(
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
            loadingAction = PapDialogAction.Primary.takeIf { state.isSendingDiagnostics },
            // The description rides in the SAME dialog as the consent, never as a second step:
            // one surface asks, one surface sends. Deliberately NOT auto-focused — raising the
            // keyboard would cover the very paragraph the user has to read before consenting.
            // The primary stays enabled while it is empty: evidence without words is still the
            // part that cannot be recovered later.
            // [SUPPORT-A-REPORT-MUST-SAY-WHAT-WENT-WRONG-001]
            content = {
                PapTextField(
                    value = state.diagnosticsMessage,
                    onValueChange = { onIntent(SettingsIntent.UpdateDiagnosticsMessage(it)) },
                    label = stringResource(Res.string.settings_send_diagnostics_message_label),
                    placeholder = stringResource(Res.string.settings_send_diagnostics_message_placeholder),
                    singleLine = false,
                    maxLines = DIAGNOSTICS_MESSAGE_MAX_LINES,
                    maxChars = DiagnosticsReport.MAX_MESSAGE_CHARS,
                    enabled = !state.isSendingDiagnostics,
                    showClearButton = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(Res.string.settings_send_diagnostics_no_reply),
                    style = PaparcarType.current.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            },
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
            // The dialog stays up with its own spinner until the deletion resolves — same contract
            // as the vehicle deletion. It used to be dismissed by the VM in the very update that
            // raised the flag, so this spinner could never run and the one the user saw belonged to
            // the danger-zone button behind it.
            // [UI-A-SPINNER-LIVES-IN-THE-BUTTON-THAT-WAS-PRESSED-001]
            loadingAction = PapDialogAction.Primary.takeIf { state.isDeletingAccount },
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
                        // isNotBlank, not just null: a profile can carry displayName = "" and an
                        // empty name must fall back to the localized placeholder exactly like a
                        // missing one. [PROFILE-AVATAR-INITIAL-NOT-INVENTED-001]
                        displayName = state.userProfile?.displayName?.takeIf { it.isNotBlank() }
                            ?: stringResource(Res.string.settings_profile_name_placeholder),
                        email = state.userProfile?.email,
                        photoUrl = state.userProfile?.photoUrl,
                        emailVerified = state.emailVerified,
                        isSendingVerification = state.isSendingEmailVerification,
                        verificationSent = state.emailVerificationSent,
                        onSendVerification = { onIntent(SettingsIntent.SendEmailVerification) },
                        onLogout = { onIntent(SettingsIntent.Logout) },
                    )
                }

                // ── 1b · Paparcar Plus [MONEY-001] — right under the account it belongs
                // to, one line, no shouting: free users get the door to the paywall, Plus
                // users get the quiet ACTIVE badge (the row then has nothing to sell, so
                // it stops being tappable).
                item {
                    // SUBDUED, the same weight as the history footer — because it is the same
                    // moment: a row inside a list you are scanning, not a screen you came to in
                    // order to decide. A filled slab of brand metal here read as an advert
                    // wedged between the account card and the detection settings.
                    // [UI-PLUS-EMPHASIS-MATCHES-THE-MOMENT-001]
                    PapPremiumSurface(
                        modifier = Modifier.fillMaxWidth(),
                        emphasis = PapPremiumEmphasis.Subdued,
                        // Already Plus → nothing left to sell, so the row stops being a door.
                        onClick = if (state.isPlus) null else {
                            { onIntent(SettingsIntent.OpenPlusPaywall) }
                        },
                    ) {
                        // On a tint the medal wears the brand green: there is no filled bed for
                        // it to disappear against, and the row's text goes back to the page's own
                        // ink like every other Settings row.
                        // [UI-PLUS-WEARS-THE-BRANDS-OWN-MARK-001]
                        PapListItem(
                            leading = {
                                Icon(
                                    imageVector = Icons.Rounded.WorkspacePremium,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(PLUS_MARK_DP.dp),
                                )
                            },
                            title = stringResource(Res.string.settings_plus_title),
                            subtitle = if (state.isPlus) {
                                stringResource(Res.string.settings_plus_subtitle_active)
                            } else {
                                stringResource(Res.string.settings_plus_subtitle_free)
                            },
                            trailing = {
                                if (state.isPlus) {
                                    Text(
                                        text = stringResource(Res.string.settings_plus_badge_active),
                                        style = PaparcarType.current.badge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                } else {
                                    PapNavChevron()
                                }
                            },
                        )
                    }
                }

                // ── 2 · Detection & permissions (heart of the app → sits high) ───
                item { SectionHeaderMuted(stringResource(Res.string.settings_section_detection)) }
                item { DetectionSectionCard(state = state, onIntent = onIntent) }

                // ── 3 · Notifications ────────────────────────────────────────────
                // Two honest switches. The old "spot freed nearby" toggle died in
                // SETTINGS-AUDIT-REMEDIATION-001 for persisting a pref no code read; its
                // successor below exists only because ObserveZoneSpotAlertsUseCase now READS
                // it [MONEY-001]. The parking one gates the informative "parking saved"
                // notification at its single choke point; safety asks are never silenced here.
                item { SectionHeaderMuted(stringResource(Res.string.settings_section_notifications)) }
                item {
                    // PapOutlinedCard hands its content straight to a Surface (a Box, not a
                    // Column) — TWO rows here need an explicit Column or they measure and
                    // draw on top of each other instead of stacking. [UI-SETTINGS-NOTIF-ROWS-OVERLAPPED-001]
                    PapOutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            PapSwitchRow(
                                icon = Icons.Rounded.Notifications,
                                label = stringResource(Res.string.settings_notif_parking),
                                description = stringResource(Res.string.settings_notif_parking_desc),
                                checked = state.notifyParkingDetected,
                                onCheckedChange = { onIntent(SettingsIntent.ToggleParkingDetectedNotif(it)) },
                                subtitleColor = settingsSubtitleColor(),
                                // Produced only by auto-detection — locked (not blanked) while OFF.
                                // Same reading as the master switch it depends on: detection that
                                // cannot run produces no "parking saved" notification either.
                                // [DET-TOGGLE-A-SWITCH-THAT-CANNOT-RUN-IS-NOT-ON-001]
                                enabled = state.autoDetectActive,
                            )
                            PapDivider()
                            // [MONEY-001] Master gate for the Plus zone alerts. Soft-gated in the
                            // VM: a free user flipping it ON meets the paywall; OFF is always allowed.
                            PapSwitchRow(
                                icon = Icons.Rounded.NotificationsActive,
                                label = stringResource(Res.string.settings_notif_zone_alerts),
                                description = stringResource(Res.string.settings_notif_zone_alerts_desc),
                                checked = state.isPlus && state.notifyZoneSpots,
                                onCheckedChange = { onIntent(SettingsIntent.ToggleZoneAlertsNotif(it)) },
                                subtitleColor = settingsSubtitleColor(),
                            )
                            PapDivider()
                            // [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001] Same shape as
                            // the zone row above and for the same reason: the watch is the Plus
                            // half, so a free user flipping it ON meets the paywall rather than a
                            // switch that was never there. Following a station stays free.
                            PapSwitchRow(
                                icon = Icons.Rounded.LocalGasStation,
                                label = stringResource(Res.string.settings_notif_fuel_alerts),
                                description = stringResource(Res.string.settings_notif_fuel_alerts_desc),
                                checked = state.isPlus && state.notifyFuelPrices,
                                onCheckedChange = { onIntent(SettingsIntent.ToggleFuelAlertsNotif(it)) },
                                subtitleColor = settingsSubtitleColor(),
                            )
                            PapDivider()
                            // [MONEY-A-CARD-THAT-NEVER-APPEARS-IS-NEVER-SOLD-001] La fila que
                            // faltaba, y su ausencia eran DOS fallos: un Plus no podía callar el
                            // único aviso de los cuatro que no se podía callar, y para un free la
                            // función no existía en ninguna superficie — sin card en el panel, sin
                            // notificación y sin una sola mención. Una fila que no existe no se
                            // puede gatear en blando, que es la única forma en que este plan vende.
                            //
                            // ⛔ La descripción dice QUÉ es el aviso y nada de free-vs-Plus, igual
                            // que sus dos vecinas (decisión del user, 16-09): quien lo explica es
                            // el paywall, en el momento del toque.
                            PapSwitchRow(
                                icon = Icons.Rounded.EventAvailable,
                                label = stringResource(Res.string.settings_notif_month_recap),
                                description = stringResource(Res.string.settings_notif_month_recap_desc),
                                checked = state.isPlus && state.notifyMonthRecap,
                                onCheckedChange = { onIntent(SettingsIntent.ToggleMonthRecapNotif(it)) },
                                subtitleColor = settingsSubtitleColor(),
                            )
                            PapDivider()
                            // [PRIV-A-TRACE-IS-NOT-A-STAT-001] Local-first: the route GEOMETRY only
                            // leaves the phone under this consent. Same soft gate as the row above.
                            PapSwitchRow(
                                icon = Icons.Rounded.CloudUpload,
                                label = stringResource(Res.string.settings_sync_routes),
                                description = stringResource(Res.string.settings_sync_routes_desc),
                                checked = state.isPlus && state.syncRouteGeometry,
                                onCheckedChange = { onIntent(SettingsIntent.ToggleRouteCloudSync(it)) },
                                subtitleColor = settingsSubtitleColor(),
                            )
                        }
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
                            // System leads: it is the default and what most users keep, so the
                            // option already in force opens the row.
                            options = listOf(
                                ThemeMode.SYSTEM to stringResource(Res.string.settings_theme_mode_system),
                                ThemeMode.LIGHT to stringResource(Res.string.settings_theme_mode_light),
                                ThemeMode.DARK to stringResource(Res.string.settings_theme_mode_dark),
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
                            // [ONBOARDING-FIRST-STEPS-ARE-GUIDED-NOT-TOLD-001] Above the legal rows:
                            // this is the only one in the section a user might actually want, and
                            // burying help under licences is how it never gets found.
                            // [WIDGET-ACTIONS-002] Placing the widget is configuration → it lives
                            // here, not in Home. Only when the launcher supports the pin sheet.
                            if (state.canPinWidget) {
                                PapNavRow(
                                    icon = Icons.Rounded.Widgets,
                                    label = stringResource(Res.string.settings_add_widget),
                                    onClick = { onIntent(SettingsIntent.PinWidget) },
                                )
                                PapDivider()
                            }
                            PapNavRow(
                                icon = Icons.Rounded.School,
                                label = stringResource(Res.string.settings_replay_first_steps),
                                onClick = { onIntent(SettingsIntent.RestartFirstSteps) },
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
            // Master toggle. It reports `autoDetectActive`, NOT the stored preference: a switch
            // that says "on" above its own health row saying "Missing background location" is a
            // false statement. Flipping it on while blocked banks the intent and opens the
            // permissions flow. [DET-TOGGLE-A-SWITCH-THAT-CANNOT-RUN-IS-NOT-ON-001]
            PapSwitchRow(
                icon = if (state.autoDetectActive) Icons.Rounded.Sensors else Icons.Rounded.SensorsOff,
                label = stringResource(Res.string.settings_auto_detect),
                description = stringResource(Res.string.settings_auto_detect_desc),
                checked = state.autoDetectActive,
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
            // Las mejoras son promesas por plataforma: el carril BT no existe en iOS (emparejar no
            // cambia nada y la pantalla de emparejar listaría cero dispositivos) y la exención de
            // batería es un concepto Android. Sin mejoras que ofrecer, la sección entera sobra.
            // [IOS-A-BT-LANE-NEEDS-A-REAL-SIGNAL-001] [SETTINGS-REMODEL-001]
            if (isBtStrategyRelevant || isBatteryOptimizationRelevant) {
                PapDivider()
                // Optional one-time setup rows — improvements, never blockers
                MiniHeader(stringResource(Res.string.settings_detection_improve))
                if (isBtStrategyRelevant) {
                    ImprovementRow(
                        icon = Icons.Rounded.Bluetooth,
                        title = stringResource(Res.string.settings_detection_bt_title),
                        description = stringResource(Res.string.settings_detection_bt_desc),
                        configured = state.btDeviceConfigured,
                        onClick = { onIntent(SettingsIntent.ConfigureBluetooth) },
                    )
                }
                if (isBatteryOptimizationRelevant) {
                    if (isBtStrategyRelevant) PapDivider()
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
                    tint = PapColor.attention,
                )
            }
        },
        title = when {
            !healthy -> stringResource(Res.string.settings_detection_health_missing, firstMissingLabel(state))
            reduced -> stringResource(Res.string.settings_detection_reliability_reduced)
            else -> stringResource(Res.string.settings_detection_health_ok)
        },
        titleColor = if (amber) PapColor.attention else cs.onSurface,
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
                    shape = PapShapes.row,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PapColor.attention),
                    border = BorderStroke(PapBorders.thin, PapColor.attention),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Rounded.Build, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(
                        stringResource(Res.string.settings_detection_fix),
                        style = PaparcarType.current.cta,
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
        RequiredPermission.PRECISE_LOCATION -> stringResource(Res.string.permissions_perm_precise)
        RequiredPermission.BACKGROUND_LOCATION -> stringResource(Res.string.permissions_perm_background)
        RequiredPermission.ACTIVITY_RECOGNITION -> stringResource(Res.string.permissions_perm_activity)
        RequiredPermission.NOTIFICATIONS -> stringResource(Res.string.permissions_perm_notifications)
        null -> ""
    }
}

// Orden de dependencia: la precisión va justo detrás del propio permiso de ubicación, porque una
// ubicación aproximada deja sin valor a todo lo que viene después — el segundo plano incluido.
private val HEALTH_PRIORITY = listOf(
    RequiredPermission.FOREGROUND_LOCATION,
    RequiredPermission.PRECISE_LOCATION,
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

/**
 * El correo sin confirmar, como fila de tarea. [AUTH-AN-UNCONFIRMED-EMAIL-MUST-SAY-SO-001]
 *
 * Calca a propósito [ImprovementRow] / [SetupStatusTrailing], que es como esta pantalla ya dice
 * «esto te falta» / «esto ya está» sin un solo chip de color. Un badge relleno aquí era un cuerpo
 * extraño: gastaba el verde de la marca — que en este sistema significa acción — en algo que no se
 * podía pulsar.
 *
 * No se reutiliza [ImprovementRow] tal cual porque su trailing tiene los textos de detección
 * clavados ("Configurado" / "Configurar"); lo que se comparte es la receta, no el literal.
 */
@Composable
private fun EmailVerificationRow(sent: Boolean, sending: Boolean, onClick: () -> Unit) {
    PapListItem(
        // Ya enviado (o enviándose) deja de ser pulsable: insistir solo consigue que Firebase
        // conteste TooManyRequests.
        modifier = if (sent || sending) Modifier else Modifier.clickable(onClick = onClick),
        leading = { PapIconTile(icon = Icons.Rounded.Email) },
        title = stringResource(Res.string.settings_profile_email_unverified),
        subtitle = stringResource(Res.string.settings_profile_email_verify_why),
        subtitleColor = settingsSubtitleColor(),
        trailing = { EmailVerificationTrailing(sent = sent) },
    )
}

@Composable
private fun EmailVerificationTrailing(sent: Boolean) {
    val cs = MaterialTheme.colorScheme
    if (sent) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(Res.string.settings_profile_email_verify_sent),
                style = PaparcarType.current.label,
                color = PapColor.actionText,
            )
            Icon(Icons.Rounded.Check, contentDescription = null, tint = cs.primary, modifier = Modifier.size(18.dp))
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                stringResource(Res.string.settings_profile_email_verify_cta),
                style = PaparcarType.current.label,
                color = cs.onSurfaceVariant,
            )
            PapNavChevron()
        }
    }
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
                color = PapColor.actionText,
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
    /** `null` mientras la sesión no se ha leído: entonces no se pinta nada, porque un badge
     *  en blanco mentiría sobre un estado que aún no conocemos. [AUTH-AN-UNCONFIRMED-EMAIL-MUST-SAY-SO-001] */
    emailVerified: Boolean?,
    isSendingVerification: Boolean,
    verificationSent: Boolean,
    onSendVerification: () -> Unit,
    onLogout: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PapShapes.card,
        color = cs.surfaceContainerHigh,
        border = outlineSubtle,
    ) {
        // ⛔ El padding horizontal lo ponen los HIJOS, no el Column: la fila del correo es un
        // `PapListItem` y tiene que colgar del mismo raíl que sus vecinas de Ajustes. Metida dentro
        // de un `padding(16.dp)` común quedaba **42 px hacia dentro por los dos lados** — medido en
        // device contra «Bluetooth del coche»: su icono entraba 42 px y su CTA se quedaba 42 px
        // corto del borde. [AUTH-AN-UNCONFIRMED-EMAIL-MUST-SAY-SO-001]
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                ProfileAvatar(displayName = displayName, photoUrl = photoUrl)
                Spacer(Modifier.size(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        style = PaparcarType.current.cardTitle,
                        color = cs.onSurface,
                    )
                    if (email != null) {
                        // El sello va en la MISMA línea que el correo, no en una propia: [AUTH-AN-UNCONFIRMED-EMAIL-MUST-SAY-SO-001]
                        // un chip debajo gastaba un renglón entero y todo el ancho a su derecha
                        // para decir una palabra, y colgaba del avatar en vez de del dato que
                        // califica.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = email,
                                style = PaparcarType.current.caption,
                                color = cs.onSurface.copy(alpha = PapAlpha.subtitle),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (emailVerified == true) {
                                // El check DENTRO de un disco, para que el sello tenga presencia sin
                                // gastar un renglón propio ni el ancho entero.
                                // [AUTH-AN-UNCONFIRMED-EMAIL-MUST-SAY-SO-001]
                                //
                                // ⛔ Disco en el VERDE DE ACCIÓN y tinta OSCURA, nunca `onPrimary` /
                                // `PapColor.onAction` — que son el mismo color y es BLANCO en el tema
                                // claro. Medido: tinta sobre el verde da 12,97:1 en oscuro y 5,52:1 en
                                // claro; blanco sobre ese verde se queda en 1,46:1 y 3,43:1. El error
                                // ya mordió una vez (ver [premiumInk] en `Color.kt`). Es, además, el
                                // MISMO par que la marca usa en la web: disco verde, glifo en tinta.
                                Box(
                                    modifier = Modifier
                                        // 16 dp: la altura de la línea del correo. Más grande,
                                        // el sello se come el renglón en vez de acompañarlo.
                                        .size(16.dp)
                                        .background(cs.primary, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        // ⛔ `Check` y no `CheckCircle`: medido en device, el segundo
                                        // reserva su caja (42x42 en el árbol de accesibilidad) y **no
                                        // pinta un solo píxel**.
                                        imageVector = Icons.Rounded.Check,
                                        // Con texto, no decorativo: un glifo mudo desaparece para un
                                        // lector de pantalla.
                                        contentDescription = stringResource(
                                            Res.string.settings_profile_email_verified,
                                        ),
                                        tint = PapInk,
                                        modifier = Modifier.size(10.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Cuando falta confirmarlo deja de ser un adorno y pasa a ser UNA TAREA, con la
            // forma exacta de sus vecinas ("Bluetooth del coche", "Batería sin restricciones"):
            // `PapListItem` + `PapIconTile` + trailing, y la fila entera pulsable. [AUTH-AN-UNCONFIRMED-EMAIL-MUST-SAY-SO-001]
            //
            // La asimetría con el estado confirmado es deliberada: el estado correcto se ACUSA (el
            // sello de arriba) y el pendiente se ANUNCIA y se resuelve. Peso visual proporcional a
            // la acción que hace falta, no al hecho de existir.
            if (emailVerified == false) {
                Spacer(Modifier.size(12.dp))
                PapDivider()
                EmailVerificationRow(
                    sent = verificationSent,
                    sending = isSendingVerification,
                    onClick = onSendVerification,
                )
            }

            // Logout outlined
            Spacer(Modifier.size(16.dp))
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(48.dp),
                shape = PapShapes.row,
            ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text(
                    stringResource(Res.string.settings_profile_logout),
                    style = PaparcarType.current.cta,
                )
            }
        }
    }
}

/**
 * Profile avatar: loads [photoUrl] when present. Shows [PapShimmerBox] while it resolves, the
 * display name's initial on a brand-green disc on error or when no URL exists, and the photo
 * (crop-to-fill) on success — never an empty circle.
 *
 * [SubcomposeAsyncImage] rather than gating a `rememberAsyncImagePainter` on its own state: the
 * painter's request only starts on its first DRAW, so drawing it only after Success deadlocks the
 * load forever (the eternal green disc). [PROFILE-PHOTO-STUCK-AND-STALE-001]
 */
@Composable
private fun ProfileAvatar(displayName: String, photoUrl: String?) {
    Box(
        modifier = Modifier
            .size(AVATAR_DP.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        val url = photoUrl?.takeIf { it.isNotBlank() }
        if (url == null) {
            AvatarInitial(displayName)
        } else {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { PapShimmerBox(modifier = Modifier.fillMaxSize()) },
                error = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        AvatarInitial(displayName)
                    }
                },
            )
        }
    }
}

/** The initial of the name on the brand disc — and when there is no usable character, a person
 *  glyph, never an invented letter: "U" only spells "User" in English, and hardcoded UI text is
 *  banned anyway. [PROFILE-AVATAR-INITIAL-NOT-INVENTED-001] */
@Composable
private fun AvatarInitial(displayName: String) {
    val initial = displayName.trim().firstOrNull()?.uppercaseChar()
    if (initial != null) {
        Text(
            text = initial.toString(),
            style = PaparcarType.current.sectionTitle,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    } else {
        Icon(
            imageVector = Icons.Rounded.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Appearance — theme + language
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Settings row offering 2–5 mutually exclusive options by NAME: the shared list-item anatomy
 * (icon + title + subtitle) with an M3 segmented control underneath. The selected segment follows
 * the house chip recipe ([com.rndeveloper.paparcar.ui.components.chips.PaparcarFilterChip]):
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
    Column {
        PapListItem(
            leading = { PapIconTile(icon = icon) },
            title = title,
            subtitle = subtitle,
            subtitleColor = settingsSubtitleColor(),
        )
        // El control es de la casa y vive en `ui/components`: la misma elección entre dos existe
        // fuera de Ajustes (qué mide la rejilla de «Cuándo aparcas»), y mientras esta receta fue
        // privada de esta pantalla, allí se dibujó con chips.
        // [UI-STATS-ONE-QUESTION-ONE-COMPONENT-001]
        PapSegmentedChoice(
            options = options,
            selected = selected,
            onSelect = onSelect,
            optionIcon = optionIcon,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
        )
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

/**
 * The entrance to account deletion — it OPENS the confirmation, it does not delete. So it carries
 * no spinner: the deletion runs behind a modal that never leaves the screen while it does, and the
 * progress belongs to that dialog's own button. This one used to animate into a spinner, which was
 * the only one the user could see once the dialog was dismissed too early — a button reporting
 * work it had not been asked to do. [UI-A-SPINNER-LIVES-IN-THE-BUTTON-THAT-WAS-PRESSED-001]
 */
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
                shape = PapShapes.row,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.error),
                border = BorderStroke(PapBorders.medium, cs.error),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    // Button text → cta, the app's button convention. [TYPO-AUDIT-001]
                    Text(label, style = PaparcarType.current.cta)
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

/** The bare Plus mark, matched to the leading tile it replaced so the row keeps its rhythm. */
private const val PLUS_MARK_DP = 32

private const val AVATAR_DP = 56

/** Report description: tall enough for the three facts that make a report actionable (expected,
 *  happened, roughly when), short enough that the dialog still fits above the keyboard.
 *  [SUPPORT-A-REPORT-MUST-SAY-WHAT-WENT-WRONG-001] */
private const val DIAGNOSTICS_MESSAGE_MAX_LINES = 5

/** Theme swatch: a dot, not a badge — three segments share one row and the label needs the width. */
private const val THEME_SWATCH_DP = 14
private const val THEME_SWATCH_RING_DP = 1

