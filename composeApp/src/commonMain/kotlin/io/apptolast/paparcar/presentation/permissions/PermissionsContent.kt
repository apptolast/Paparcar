package io.apptolast.paparcar.presentation.permissions

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.apptolast.paparcar.ui.components.PapAlertDialog
import io.apptolast.paparcar.ui.components.PapSectionHeader
import io.apptolast.paparcar.ui.theme.PaparcarSpacing
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.ic_shield_3d
import paparcar.composeapp.generated.resources.permissions_bg_guide_body
import paparcar.composeapp.generated.resources.permissions_bg_guide_cta
import paparcar.composeapp.generated.resources.permissions_bg_guide_dismiss
import paparcar.composeapp.generated.resources.permissions_bg_guide_title
import paparcar.composeapp.generated.resources.permissions_btn_activate_detection
import paparcar.composeapp.generated.resources.permissions_btn_grant
import paparcar.composeapp.generated.resources.permissions_btn_location_settings
import paparcar.composeapp.generated.resources.permissions_btn_settings
import paparcar.composeapp.generated.resources.permissions_continue_with_core
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
import paparcar.composeapp.generated.resources.permissions_status_granted
import paparcar.composeapp.generated.resources.permissions_status_optional
import paparcar.composeapp.generated.resources.permissions_status_pending
import paparcar.composeapp.generated.resources.permissions_subtitle
import paparcar.composeapp.generated.resources.permissions_title

private val   BUTTON_HEIGHT           = 52.dp
private val   ROW_VERTICAL_PADDING    = 14.dp
private val   ROW_CONTENT_SPACING     = 14.dp
private val   ICON_BOX_SIZE           = 40.dp
private val   ICON_SIZE               = 22.dp
private val   BADGE_ICON_SIZE         = 14.dp
private const val HERO_ICON_DP        = 72

@Composable
internal fun PermissionsContent(
    state: PermissionsState,
    onRequestPermissions: () -> Unit,
    onRequestBluetooth: () -> Unit = {},
    onRequestBatteryOptimization: () -> Unit = {},
    onRequestOemAutostart: () -> Unit = {},
    onRequestOemBatterySettings: () -> Unit = {},
    onConfirmBackgroundLocationGuide: () -> Unit = {},
    onDismissBackgroundLocationGuide: () -> Unit = {},
    onContinueWithCore: () -> Unit = {},
    focus: PermissionsFocus = PermissionsFocus.All,
) {
    if (state.showBackgroundLocationGuide) {
        PapAlertDialog(
            onDismiss = onDismissBackgroundLocationGuide,
            icon = Icons.Outlined.Explore,
            title = stringResource(Res.string.permissions_bg_guide_title),
            body = stringResource(Res.string.permissions_bg_guide_body),
            primaryLabel = stringResource(Res.string.permissions_bg_guide_cta),
            onPrimary = onConfirmBackgroundLocationGuide,
            cancelLabel = stringResource(Res.string.permissions_bg_guide_dismiss),
        )
    }

    // Reserve exactly the floating footer's measured height as bottom scroll padding, so the last
    // row clears the button without leaving a big empty gap (the footer grows when "Maybe later"
    // shows, shrinks when it doesn't). [DET-READY-001i]
    var footerHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = PaparcarSpacing.xxl)
                .padding(
                    top = PaparcarSpacing.xxxl,
                    bottom = with(density) { footerHeightPx.toDp() } + PaparcarSpacing.xxxl,
                )
                .verticalScroll(rememberScrollState()),
        ) {
            val showEssential = focus != PermissionsFocus.Producer
            val showProducer = focus != PermissionsFocus.Core

            // Header — 3D brand shield badge (green gradient + check + drop shadow, designed in Figma)
            // to the LEFT of the title, vertically centred against it; subtitle below full width. More
            // dynamic than a flat top-left stack. [DET-READY-001i]
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PaparcarSpacing.md),
            ) {
                Image(
                    painter = painterResource(Res.drawable.ic_shield_3d),
                    contentDescription = null,
                    modifier = Modifier.size(HERO_ICON_DP.dp),
                )
                Text(
                    text = stringResource(Res.string.permissions_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(PaparcarSpacing.sm))
            Text(
                text = stringResource(Res.string.permissions_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(PaparcarSpacing.xxxl))

            // ── ESSENTIAL (CORE) — the minimum to use the map ───────────────
            if (showEssential) {
                PapSectionHeader(
                    title = stringResource(Res.string.permissions_section_essential),
                    modifier = Modifier.padding(bottom = PaparcarSpacing.md),
                )
                PermissionRow(
                    icon = Icons.Default.LocationOn,
                    title = stringResource(Res.string.permissions_perm_location),
                    desc = stringResource(Res.string.permissions_perm_location_desc),
                    granted = state.hasFineLocation,
                )
                Spacer(Modifier.height(PaparcarSpacing.md))
                PermissionRow(
                    icon = Icons.Default.Settings,
                    title = stringResource(Res.string.permissions_perm_location_services),
                    desc = stringResource(Res.string.permissions_perm_location_services_desc),
                    granted = state.isLocationServicesEnabled,
                )
            }

            // ── AUTO-DETECTION (PRODUCER) — the star feature, optional ───────
            if (showProducer) {
                if (showEssential) Spacer(Modifier.height(PaparcarSpacing.xl))
                PapSectionHeader(
                    title = stringResource(Res.string.permissions_section_detection),
                    modifier = Modifier.padding(bottom = PaparcarSpacing.md),
                )
                PermissionRow(
                    icon = Icons.Outlined.Explore,
                    title = stringResource(Res.string.permissions_perm_background),
                    desc = stringResource(Res.string.permissions_perm_background_desc),
                    granted = state.hasBackgroundLocation,
                )
                Spacer(Modifier.height(PaparcarSpacing.md))
                PermissionRow(
                    icon = Icons.Default.Person,
                    title = stringResource(Res.string.permissions_perm_activity),
                    desc = stringResource(Res.string.permissions_perm_activity_desc),
                    granted = state.hasActivityRecognition,
                )
                Spacer(Modifier.height(PaparcarSpacing.md))
                PermissionRow(
                    icon = Icons.Default.Notifications,
                    title = stringResource(Res.string.permissions_perm_notifications),
                    desc = stringResource(Res.string.permissions_perm_notifications_desc),
                    granted = state.hasNotifications,
                )

                // ── OPTIONAL — reliability on aggressive OEMs ────────────────
                Spacer(Modifier.height(PaparcarSpacing.xl))
                PapSectionHeader(
                    title = stringResource(Res.string.permissions_section_optional),
                    modifier = Modifier.padding(bottom = PaparcarSpacing.md),
                )
                OptionalPermissionRow(
                    icon = Icons.Outlined.Bluetooth,
                    title = stringResource(Res.string.permissions_perm_bluetooth),
                    desc = stringResource(Res.string.permissions_perm_bluetooth_desc),
                    granted = state.hasBluetoothConnect,
                    onGrant = onRequestBluetooth,
                )
                Spacer(Modifier.height(PaparcarSpacing.md))
                OptionalPermissionRow(
                    icon = Icons.Outlined.BatteryFull,
                    title = stringResource(Res.string.permissions_perm_battery),
                    desc = stringResource(Res.string.permissions_perm_battery_desc),
                    granted = state.isBatteryOptimizationExempt,
                    onGrant = onRequestBatteryOptimization,
                )
                if (!state.isBatteryOptimizationExempt) {
                    Spacer(Modifier.height(PaparcarSpacing.sm))
                    Text(
                        text = stringResource(Res.string.permissions_perm_battery_oem_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Optional OEM autostart whitelist (MIUI/ColorOS/EMUI…) — no public API to read the
                // toggle; we track whether the user opened the settings screen this session.
                if (state.showAutostartCard) {
                    Spacer(Modifier.height(PaparcarSpacing.md))
                    OptionalPermissionRow(
                        icon = Icons.Outlined.RocketLaunch,
                        title = stringResource(Res.string.permissions_perm_autostart),
                        desc = stringResource(Res.string.permissions_perm_autostart_desc),
                        granted = state.hasAcknowledgedAutostart,
                        onGrant = onRequestOemAutostart,
                    )
                }

                // Optional OEM battery / Hans freeze (ColorOS/OPPO/Realme) — OplusHansManager SIGSTOPs
                // processes even when autostart is whitelisted. Only shown on ColorOS. [OEM-002]
                if (state.showOemBatteryCard) {
                    Spacer(Modifier.height(PaparcarSpacing.md))
                    OptionalPermissionRow(
                        icon = Icons.Outlined.BatteryAlert,
                        title = stringResource(Res.string.permissions_perm_oem_battery),
                        desc = stringResource(Res.string.permissions_perm_oem_battery_desc),
                        granted = state.hasAcknowledgedOemBattery,
                        onGrant = onRequestOemBatterySettings,
                    )
                }
            }

            if (state.showRationale && !state.showSettingsPrompt) {
                Spacer(Modifier.height(PaparcarSpacing.xl))
                Text(
                    text = stringResource(Res.string.permissions_rationale),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }

        // ── Bottom action button ─────────────────────────────────────────────
        // CORE = foreground location; GPS = essential toggle; PRODUCER = background + AR + notifications.
        // The first ask is CORE-only; PRODUCER is requested via the "Activate detection" button. [DET-READY-001i]
        val isCorePending = !state.hasFineLocation
        val isGpsPending = !state.isLocationServicesEnabled
        val isProducerPending = !state.hasBackgroundLocation ||
            !state.hasActivityRecognition || !state.hasNotifications

        val showButton = state.showSettingsPrompt || isCorePending || isGpsPending || isProducerPending

        if (showButton) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = PaparcarSpacing.xxl)
                    .navigationBarsPadding()
                    .padding(bottom = PaparcarSpacing.xxxl)
                    .onSizeChanged { footerHeightPx = it.height },
            ) {
                // Location permanently denied / revoked → the request dialog would no-op, so jump
                // straight to the amber "open settings" CTA from the first frame. [DET-READY-001m]
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
                Button(
                    onClick = onRequestPermissions,
                    modifier = Modifier.fillMaxWidth().height(BUTTON_HEIGHT),
                    shape = MaterialTheme.shapes.medium,
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
                // "Maybe later" — enter with CORE only, defer PRODUCER. Shown once the minimum
                // (CORE + GPS) is met so the user is never stranded below it. [DET-READY-001e]
                if (state.canContinueWithCore) {
                    TextButton(
                        onClick = onContinueWithCore,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(Res.string.permissions_continue_with_core),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
        shape = MaterialTheme.shapes.medium,
        color = surfaceColor,
        onClick = { if (!granted) onGrant() },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = PaparcarSpacing.lg, vertical = ROW_VERTICAL_PADDING),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ROW_CONTENT_SPACING),
        ) {
            Surface(
                shape = CircleShape,
                color = if (granted) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(ICON_BOX_SIZE),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (granted) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(ICON_SIZE),
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
                shape = MaterialTheme.shapes.small,
                color = if (granted) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = PaparcarSpacing.sm, vertical = PaparcarSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PaparcarSpacing.xs),
                ) {
                    if (granted) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(BADGE_ICON_SIZE),
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
        shape = MaterialTheme.shapes.medium,
        color = surfaceColor,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = PaparcarSpacing.lg, vertical = ROW_VERTICAL_PADDING),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ROW_CONTENT_SPACING),
        ) {
            Surface(
                shape = CircleShape,
                color = if (granted) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.size(ICON_BOX_SIZE),
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
                        modifier = Modifier.size(ICON_SIZE),
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
                shape = MaterialTheme.shapes.small,
                color = if (granted) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = PaparcarSpacing.sm, vertical = PaparcarSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PaparcarSpacing.xs),
                ) {
                    if (granted) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(BADGE_ICON_SIZE),
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
