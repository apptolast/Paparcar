package io.apptolast.paparcar.presentation.permissions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.isBatteryOptimizationRelevant
import io.apptolast.paparcar.ui.components.PapAlertDialog
import io.apptolast.paparcar.ui.components.PapFooterButton
import io.apptolast.paparcar.ui.components.PaparcarBottomActionScaffold
import io.apptolast.paparcar.ui.illustrations.OnboardingHero
import io.apptolast.paparcar.ui.theme.PaparcarSpacing
import io.apptolast.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.permissions_bg_guide_body
import paparcar.composeapp.generated.resources.permissions_bg_guide_cta
import paparcar.composeapp.generated.resources.permissions_bg_guide_dismiss
import paparcar.composeapp.generated.resources.permissions_bg_guide_title
import paparcar.composeapp.generated.resources.permissions_btn_activate_detection
import paparcar.composeapp.generated.resources.permissions_btn_allow_background
import paparcar.composeapp.generated.resources.permissions_btn_continue
import paparcar.composeapp.generated.resources.permissions_btn_grant
import paparcar.composeapp.generated.resources.permissions_btn_location_settings
import paparcar.composeapp.generated.resources.permissions_btn_settings
import paparcar.composeapp.generated.resources.permissions_continue_with_core
import paparcar.composeapp.generated.resources.permissions_skip_dialog_activate
import paparcar.composeapp.generated.resources.permissions_skip_dialog_body
import paparcar.composeapp.generated.resources.permissions_skip_dialog_later
import paparcar.composeapp.generated.resources.permissions_skip_dialog_title
import paparcar.composeapp.generated.resources.permissions_perm_activity
import paparcar.composeapp.generated.resources.permissions_perm_activity_desc
import paparcar.composeapp.generated.resources.permissions_perm_autostart
import paparcar.composeapp.generated.resources.permissions_perm_autostart_desc
import paparcar.composeapp.generated.resources.permissions_perm_oem_battery
import paparcar.composeapp.generated.resources.permissions_perm_oem_battery_desc
import paparcar.composeapp.generated.resources.permissions_perm_background
import paparcar.composeapp.generated.resources.permissions_perm_background_desc
import paparcar.composeapp.generated.resources.permissions_perm_battery
import paparcar.composeapp.generated.resources.permissions_perm_battery_desc
import paparcar.composeapp.generated.resources.permissions_perm_battery_oem_hint
import paparcar.composeapp.generated.resources.permissions_reliability_reduced_callout
import paparcar.composeapp.generated.resources.permissions_perm_bluetooth
import paparcar.composeapp.generated.resources.permissions_perm_bluetooth_desc
import paparcar.composeapp.generated.resources.permissions_perm_location
import paparcar.composeapp.generated.resources.permissions_perm_location_desc
import paparcar.composeapp.generated.resources.permissions_perm_location_services
import paparcar.composeapp.generated.resources.permissions_perm_location_services_desc
import paparcar.composeapp.generated.resources.permissions_perm_notifications
import paparcar.composeapp.generated.resources.permissions_perm_notifications_desc
import paparcar.composeapp.generated.resources.permissions_rationale
import paparcar.composeapp.generated.resources.permissions_section_detection
import paparcar.composeapp.generated.resources.permissions_section_essential
import paparcar.composeapp.generated.resources.permissions_section_optional
import paparcar.composeapp.generated.resources.permissions_subtitle
import paparcar.composeapp.generated.resources.permissions_tier_detection_benefit
import paparcar.composeapp.generated.resources.permissions_tier_essential_benefit
import paparcar.composeapp.generated.resources.permissions_tier_optional_benefit
import paparcar.composeapp.generated.resources.permissions_title

private val HERO_ILLUSTRATION_W = 140.dp
private val HERO_ILLUSTRATION_H = 120.dp

@Composable
internal fun PermissionsContent(
    state: PermissionsState,
    onRequestPermissions: () -> Unit,
    onRequestForegroundLocation: () -> Unit = {},
    onOpenLocationServices: () -> Unit = {},
    onRequestBackgroundLocation: () -> Unit = {},
    onRequestActivityRecognition: () -> Unit = {},
    onRequestNotifications: () -> Unit = {},
    onRequestBluetooth: () -> Unit = {},
    onRequestBatteryOptimization: () -> Unit = {},
    onRequestOemAutostart: () -> Unit = {},
    onRequestOemBatterySettings: () -> Unit = {},
    onConfirmBackgroundLocationGuide: () -> Unit = {},
    onDismissBackgroundLocationGuide: () -> Unit = {},
    onContinueWithCore: () -> Unit = {},
    onRequestSkipDetection: () -> Unit = {},
    onDismissSkipDetectionDialog: () -> Unit = {},
    onFinish: () -> Unit = {},
    focus: PermissionsFocus = PermissionsFocus.All,
) {
    if (state.showBackgroundLocationGuide) {
        PapAlertDialog(
            onDismiss = onDismissBackgroundLocationGuide,
            icon = Icons.Rounded.Explore,
            title = stringResource(Res.string.permissions_bg_guide_title),
            body = stringResource(Res.string.permissions_bg_guide_body),
            primaryLabel = stringResource(Res.string.permissions_bg_guide_cta),
            onPrimary = onConfirmBackgroundLocationGuide,
            primaryLeadingIcon = Icons.Rounded.Settings,
            cancelLabel = stringResource(Res.string.permissions_bg_guide_dismiss),
        )
    }

    // "Maybe later" confirmation — nudge the user to activate before deferring auto-detection. The
    // emphasised primary keeps them here; the secondary lets them skip. Outside-tap is safe (stays).
    // [DET-TOGGLE-002]
    if (state.showSkipDetectionDialog) {
        PapAlertDialog(
            onDismiss = onDismissSkipDetectionDialog,
            icon = Icons.Rounded.Sensors,
            title = stringResource(Res.string.permissions_skip_dialog_title),
            body = stringResource(Res.string.permissions_skip_dialog_body),
            primaryLabel = stringResource(Res.string.permissions_skip_dialog_activate),
            onPrimary = onDismissSkipDetectionDialog,
            primaryLeadingIcon = Icons.Rounded.Sensors,
            secondaryLabel = stringResource(Res.string.permissions_skip_dialog_later),
            onSecondary = onContinueWithCore,
            secondaryLeadingIcon = Icons.Rounded.Schedule,
        )
    }

    val showEssential = focus != PermissionsFocus.Producer
    val showProducer = focus != PermissionsFocus.Core

    // Bottom action button state. CORE = foreground location; GPS = essential toggle; PRODUCER =
    // background + AR + notifications. The first ask is CORE-only; PRODUCER is requested via the
    // "Activate detection" button. [DET-READY-001i]
    val isCorePending = !state.hasFineLocation
    val isGpsPending = !state.isLocationServicesEnabled
    // Every required tier granted → the screen no longer *blocks* entry; the footer switches from
    // "grant" actions to the optional reliability toggle + an explicit "Continue". [PERM-FOOTER-001]
    val requiredComplete = state.hasFineLocation && state.isLocationServicesEnabled &&
        state.hasBackgroundLocation && state.hasActivityRecognition && state.hasNotifications

    // The footer is ALWAYS present: it either drives the next pending grant or, once everything
    // required is done, offers the optional background toggle + a "Continue" to enter the app. This
    // also guarantees the navigation-bar inset is always reserved (no content under the nav bar).
    PaparcarBottomActionScaffold(
        footer = {
            PermissionsFooter(
                state = state,
                isCorePending = isCorePending,
                isGpsPending = isGpsPending,
                requiredComplete = requiredComplete,
                onRequestPermissions = onRequestPermissions,
                onRequestSkipDetection = onRequestSkipDetection,
                onRequestBatteryOptimization = onRequestBatteryOptimization,
                onFinish = onFinish,
            )
        },
    ) {
        // Header — hero ilustrado de marca (escudo-check) + título + subtítulo, mismo patrón que los
        // slides de onboarding. [ONB-IDENTITY-001 B/H]
        Spacer(Modifier.height(PaparcarSpacing.xxxl))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OnboardingHero(
                hero = OnboardingHero.AUTOMATION,
                modifier = Modifier.size(HERO_ILLUSTRATION_W, HERO_ILLUSTRATION_H),
            )
            Spacer(Modifier.height(PaparcarSpacing.sm))
            Text(
                text = stringResource(Res.string.permissions_title),
                style = PaparcarType.current.heroTitle,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(PaparcarSpacing.sm))
            Text(
                text = stringResource(Res.string.permissions_subtitle),
                style = PaparcarType.current.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(PaparcarSpacing.xxxl))

        // ── Timeline por tier de beneficio ──────────────────────────────────
        // El nodo se pone verde cuando el tier entero está concedido → progresión de valor.
        if (showEssential) {
            PermissionTier(
                icon = Icons.Rounded.Map,
                title = stringResource(Res.string.permissions_section_essential),
                benefit = stringResource(Res.string.permissions_tier_essential_benefit),
                satisfied = state.hasFineLocation && state.isLocationServicesEnabled,
                isLast = !showProducer,
            ) {
                PermissionRow(
                    icon = Icons.Rounded.LocationOn,
                    title = stringResource(Res.string.permissions_perm_location),
                    reason = stringResource(Res.string.permissions_perm_location_desc),
                    state = requiredState(state.hasFineLocation),
                    onGrant = onRequestForegroundLocation,
                )
                Spacer(Modifier.height(PaparcarSpacing.md))
                PermissionRow(
                    icon = Icons.Rounded.Settings,
                    title = stringResource(Res.string.permissions_perm_location_services),
                    reason = stringResource(Res.string.permissions_perm_location_services_desc),
                    state = requiredState(state.isLocationServicesEnabled),
                    onGrant = onOpenLocationServices,
                )
            }
        }

        if (showProducer) {
            PermissionTier(
                icon = Icons.Rounded.Sensors,
                title = stringResource(Res.string.permissions_section_detection),
                benefit = stringResource(Res.string.permissions_tier_detection_benefit),
                satisfied = state.hasBackgroundLocation &&
                    state.hasActivityRecognition && state.hasNotifications,
                isLast = false,
            ) {
                PermissionRow(
                    icon = Icons.Rounded.Explore,
                    title = stringResource(Res.string.permissions_perm_background),
                    reason = stringResource(Res.string.permissions_perm_background_desc),
                    state = requiredState(state.hasBackgroundLocation),
                    onGrant = onRequestBackgroundLocation,
                )
                Spacer(Modifier.height(PaparcarSpacing.md))
                PermissionRow(
                    icon = Icons.Rounded.Person,
                    title = stringResource(Res.string.permissions_perm_activity),
                    reason = stringResource(Res.string.permissions_perm_activity_desc),
                    state = requiredState(state.hasActivityRecognition),
                    onGrant = onRequestActivityRecognition,
                )
                Spacer(Modifier.height(PaparcarSpacing.md))
                PermissionRow(
                    icon = Icons.Rounded.Notifications,
                    title = stringResource(Res.string.permissions_perm_notifications),
                    reason = stringResource(Res.string.permissions_perm_notifications_desc),
                    state = requiredState(state.hasNotifications),
                    onGrant = onRequestNotifications,
                )
            }

            PermissionTier(
                icon = Icons.Rounded.Shield,
                title = stringResource(Res.string.permissions_section_optional),
                benefit = stringResource(Res.string.permissions_tier_optional_benefit),
                satisfied = state.hasBluetoothConnect && state.isBatteryOptimizationExempt,
                isLast = true,
            ) {
                PermissionRow(
                    icon = Icons.Rounded.Bluetooth,
                    title = stringResource(Res.string.permissions_perm_bluetooth),
                    reason = stringResource(Res.string.permissions_perm_bluetooth_desc),
                    state = optionalState(state.hasBluetoothConnect),
                    onGrant = onRequestBluetooth,
                )
                Spacer(Modifier.height(PaparcarSpacing.md))
                PermissionRow(
                    icon = Icons.Rounded.BatteryFull,
                    title = stringResource(Res.string.permissions_perm_battery),
                    reason = stringResource(Res.string.permissions_perm_battery_desc),
                    state = optionalState(state.isBatteryOptimizationExempt),
                    onGrant = onRequestBatteryOptimization,
                )
                if (!state.isBatteryOptimizationExempt) {
                    Spacer(Modifier.height(PaparcarSpacing.sm))
                    // REDUCED reliability → the honest manufacturer-policy callout (user-level
                    // copy: cause → consequence → remedies) replaces the generic hint; amber to
                    // match the health convention. [DET-RELIABILITY-001]
                    Text(
                        text = stringResource(
                            if (state.isReliabilityReduced) {
                                Res.string.permissions_reliability_reduced_callout
                            } else {
                                Res.string.permissions_perm_battery_oem_hint
                            },
                        ),
                        style = PaparcarType.current.caption,
                        color = if (state.isReliabilityReduced) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }

                // Optional OEM autostart whitelist (MIUI/ColorOS/EMUI…) — no public API to read the
                // toggle; we track whether the user opened the settings screen this session.
                if (state.showAutostartCard) {
                    Spacer(Modifier.height(PaparcarSpacing.md))
                    PermissionRow(
                        icon = Icons.Rounded.RocketLaunch,
                        title = stringResource(Res.string.permissions_perm_autostart),
                        reason = stringResource(Res.string.permissions_perm_autostart_desc),
                        state = optionalState(state.hasAcknowledgedAutostart),
                        onGrant = onRequestOemAutostart,
                    )
                }

                // Optional OEM battery / Hans freeze (ColorOS/OPPO/Realme) — OplusHansManager SIGSTOPs
                // processes even when autostart is whitelisted. Only shown on ColorOS. [OEM-002]
                if (state.showOemBatteryCard) {
                    Spacer(Modifier.height(PaparcarSpacing.md))
                    PermissionRow(
                        icon = Icons.Rounded.BatteryAlert,
                        title = stringResource(Res.string.permissions_perm_oem_battery),
                        reason = stringResource(Res.string.permissions_perm_oem_battery_desc),
                        state = optionalState(state.hasAcknowledgedOemBattery),
                        onGrant = onRequestOemBatterySettings,
                    )
                }
            }
        }

        if (state.showRationale && !state.showSettingsPrompt) {
            Spacer(Modifier.height(PaparcarSpacing.xl))
            Text(
                text = stringResource(Res.string.permissions_rationale),
                style = PaparcarType.current.caption,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun ColumnScope.PermissionsFooter(
    state: PermissionsState,
    isCorePending: Boolean,
    isGpsPending: Boolean,
    requiredComplete: Boolean,
    onRequestPermissions: () -> Unit,
    onRequestSkipDetection: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onFinish: () -> Unit,
) {
    // Everything required is granted. Surface the optional background-reliability toggle the copy
    // promises ("permite la actividad en segundo plano aquí abajo") as the emphasised action —
    // it's the highest-impact opt-in on Doze-aggressive OEMs — with a plain "Continue" underneath
    // to enter the app. Battery exemption is Android-only, so hide that button on iOS. [PERM-FOOTER-001]
    if (requiredComplete) {
        val batteryPending = isBatteryOptimizationRelevant && !state.isBatteryOptimizationExempt
        if (batteryPending) {
            PapFooterButton(
                label = stringResource(Res.string.permissions_btn_allow_background),
                leadingIcon = Icons.Rounded.BatteryFull,
                onClick = onRequestBatteryOptimization,
            )
            TextButton(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(Res.string.permissions_btn_continue),
                    style = PaparcarType.current.cta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            PapFooterButton(
                label = stringResource(Res.string.permissions_btn_continue),
                leadingIcon = Icons.Rounded.ArrowForward,
                onClick = onFinish,
            )
        }
        return
    }

    // Location permanently denied / revoked → the request dialog would no-op, so jump straight to the
    // amber "open settings" CTA from the first frame. [DET-READY-001m]
    val coreNeedsSettings = isCorePending && state.locationPermanentlyDenied
    val (label, isAmber) = when {
        state.showSettingsPrompt || coreNeedsSettings ->
            stringResource(Res.string.permissions_btn_settings) to true
        isCorePending ->
            stringResource(Res.string.permissions_btn_grant) to false
        isGpsPending ->
            stringResource(Res.string.permissions_btn_location_settings) to false
        else -> // PRODUCER pending → the deliberate "activate auto-detection" step
            stringResource(Res.string.permissions_btn_activate_detection) to false
    }
    val icon = when {
        state.showSettingsPrompt || coreNeedsSettings -> Icons.Rounded.Settings
        isCorePending -> Icons.Rounded.LocationOn
        isGpsPending -> Icons.Rounded.GpsFixed
        else -> Icons.Rounded.Sensors
    }
    PapFooterButton(
        label = label,
        leadingIcon = icon,
        onClick = onRequestPermissions,
        containerColor = if (isAmber) MaterialTheme.colorScheme.secondary else null,
        contentColor = if (isAmber) MaterialTheme.colorScheme.onSecondary else null,
    )
    // "Maybe later" — enter with CORE only, defer PRODUCER. Shown once the minimum (CORE + GPS) is
    // met so the user is never stranded below it. [DET-READY-001e]
    if (state.canContinueWithCore) {
        TextButton(
            onClick = onRequestSkipDetection,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                Icons.Rounded.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(Res.string.permissions_continue_with_core),
                // TextButton text → cta (Inter), the app's button convention. [TYPO-AUDIT-001]
                style = PaparcarType.current.cta,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
