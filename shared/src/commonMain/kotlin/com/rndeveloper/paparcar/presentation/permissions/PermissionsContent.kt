package com.rndeveloper.paparcar.presentation.permissions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.isBatteryOptimizationRelevant
import com.rndeveloper.paparcar.domain.permissions.SystemPermissionDialog
import com.rndeveloper.paparcar.ui.components.PapAlertDialog
import com.rndeveloper.paparcar.ui.components.PapFooterButton
import com.rndeveloper.paparcar.ui.components.PaparcarBottomActionScaffold
import com.rndeveloper.paparcar.ui.illustrations.OnboardingHero
import com.rndeveloper.paparcar.ui.theme.PaparcarSpacing
import com.rndeveloper.paparcar.ui.theme.PapColor
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.permissions_bg_disclosure
import paparcar.composeapp.generated.resources.permissions_bg_guide_cta
import paparcar.composeapp.generated.resources.permissions_bg_guide_dismiss
import paparcar.composeapp.generated.resources.permissions_bg_guide_title
import paparcar.composeapp.generated.resources.permissions_btn_activate_detection
import paparcar.composeapp.generated.resources.permissions_btn_allow_background
import paparcar.composeapp.generated.resources.permissions_btn_continue
import paparcar.composeapp.generated.resources.permissions_btn_grant
import paparcar.composeapp.generated.resources.permissions_btn_location_settings
import paparcar.composeapp.generated.resources.permissions_btn_not_now
import paparcar.composeapp.generated.resources.permissions_btn_settings
import paparcar.composeapp.generated.resources.permissions_core_detection_teaser
import paparcar.composeapp.generated.resources.permissions_core_subtitle
import paparcar.composeapp.generated.resources.permissions_core_title
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
import paparcar.composeapp.generated.resources.permissions_tier_assisted_plus_name
import paparcar.composeapp.generated.resources.permissions_tier_automatic_name
import paparcar.composeapp.generated.resources.permissions_tier_unlocks
import paparcar.composeapp.generated.resources.permissions_perm_location
import paparcar.composeapp.generated.resources.permissions_perm_location_desc
import paparcar.composeapp.generated.resources.permissions_perm_location_services
import paparcar.composeapp.generated.resources.permissions_perm_notifications
import paparcar.composeapp.generated.resources.permissions_perm_precise
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
fun PermissionsContent(
    state: PermissionsState,
    onRequestPermissions: () -> Unit,
    onRequestForegroundLocation: () -> Unit = {},
    onOpenLocationServices: () -> Unit = {},
    onRequestBackgroundLocation: () -> Unit = {},
    onRequestPreciseLocation: () -> Unit = {},
    onRequestActivityRecognition: () -> Unit = {},
    onRequestNotifications: () -> Unit = {},
    onRequestBluetooth: () -> Unit = {},
    onRequestBatteryOptimization: () -> Unit = {},
    onRequestOemAutostart: () -> Unit = {},
    onRequestOemBatterySettings: () -> Unit = {},
    onConfirmBackgroundLocationGuide: () -> Unit = {},
    onDismissBackgroundLocationGuide: () -> Unit = {},
    onNotNow: () -> Unit = {},
    onFinish: () -> Unit = {},
    focus: PermissionsFocus = PermissionsFocus.Core,
) {
    if (state.showBackgroundLocationGuide) {
        PapAlertDialog(
            onDismiss = onDismissBackgroundLocationGuide,
            icon = Icons.Rounded.Explore,
            title = stringResource(Res.string.permissions_bg_guide_title),
            // El camino a «Siempre» es distinto en cada plataforma: narrar el de Android en iOS
            // manda al usuario a una pantalla que no existe.
            body = stringResource(platformPermissionCopy.backgroundGuideBody),
            primaryLabel = stringResource(Res.string.permissions_bg_guide_cta),
            onPrimary = onConfirmBackgroundLocationGuide,
            cancelLabel = stringResource(Res.string.permissions_bg_guide_dismiss),
        )
    }

    // One focus, one tier: first-run and CORE repairs see the essential section only (the detection
    // wall is what made the old first-run aggressive); "activate detection" visits see the producer
    // sections only. [ONB-PERMISSIONS-ASK-AT-THE-MOMENT-OF-VALUE-001]
    val showEssential = focus == PermissionsFocus.Core
    val showProducer = focus == PermissionsFocus.Producer

    // Bottom action button state. CORE = foreground location; GPS = essential toggle; PRODUCER =
    // background + AR + notifications, requested via the "Activate detection" button. [DET-READY-001i]
    val isCorePending = !state.hasFineLocation
    val isGpsPending = !state.isLocationServicesEnabled
    // Everything THIS focus asks for is granted → the footer switches from "grant" actions to the
    // exit ("Continue" / optional reliability toggle). The producer visit still checks the core
    // pair: its permissions are useless without location, and Android won't even grant background
    // without foreground. [PERM-FOOTER-001]
    // ⛔ El tier de detección NO se recalcula aquí: `producerGranted` viene de
    // `AppPermissionState.hasProducerPermissions`, que ya sabe que un permiso inexistente en este
    // dispositivo no puede faltar y que una ubicación aproximada sí.
    // [IOS-PERMISSIONS-A-ROW-MUST-BE-GRANTABLE-HERE-001]
    val requiredComplete = when (focus) {
        PermissionsFocus.Core -> state.hasFineLocation && state.isLocationServicesEnabled
        PermissionsFocus.Producer -> state.hasFineLocation && state.isLocationServicesEnabled &&
            state.producerGranted
    }

    // The footer is ALWAYS present: it either drives the next pending grant or, once everything
    // required is done, offers the optional background toggle + a "Continue" to enter the app. This
    // also guarantees the navigation-bar inset is always reserved (no content under the nav bar).
    PaparcarBottomActionScaffold(
        footer = {
            PermissionsFooter(
                state = state,
                focus = focus,
                isCorePending = isCorePending,
                isGpsPending = isGpsPending,
                requiredComplete = requiredComplete,
                onRequestPermissions = onRequestPermissions,
                onNotNow = onNotNow,
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
            // Each focus narrates ITS ask: the core visit is about seeing the map (consumer side),
            // the producer visit is about automating your parking (producer side). One shared
            // header lied to both. [ONB-PERMISSIONS-ASK-AT-THE-MOMENT-OF-VALUE-001]
            Text(
                text = stringResource(
                    if (showEssential) Res.string.permissions_core_title
                    else Res.string.permissions_title,
                ),
                style = PaparcarType.current.heroTitle,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(PaparcarSpacing.sm))
            Text(
                text = stringResource(
                    if (showEssential) Res.string.permissions_core_subtitle
                    else Res.string.permissions_subtitle,
                ),
                style = PaparcarType.current.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(PaparcarSpacing.xxxl))

        // ── Nivel de detección actual (la promesa honesta) ──────────────────
        // Sólo cuando la sección de detección es visible: en foco Core-only (GPS apagado) la
        // detección no puede correr, así que el nivel no aplica. [DET-TIERS-001]
        if (showProducer) {
            // Prominent disclosure de ubicación en segundo plano (política de Play): vive aquí,
            // ANTES de la petición de background — no en el onboarding, donde sermoneaba a todo
            // usuario nuevo por una detección que aún no había pedido activar.
            // [ONB-PERMISSIONS-ASK-AT-THE-MOMENT-OF-VALUE-001]
            Text(
                text = stringResource(Res.string.permissions_bg_disclosure),
                style = PaparcarType.current.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(PaparcarSpacing.xl))
            DetectionTierStatusCard(tier = state.currentTier)
            Spacer(Modifier.height(PaparcarSpacing.xxl))
        }

        // ── Timeline por tier de beneficio ──────────────────────────────────
        // El nodo se pone verde cuando el tier entero está concedido → progresión de valor.
        if (showEssential) {
            PermissionTier(
                icon = Icons.Rounded.Map,
                title = stringResource(Res.string.permissions_section_essential),
                benefit = stringResource(Res.string.permissions_tier_essential_benefit),
                satisfied = state.hasFineLocation && state.isLocationServicesEnabled,
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
                    // El interruptor global vive en un sitio distinto en cada plataforma, y en iOS
                    // no se puede enlazar: hay que decir el camino con palabras.
                    reason = stringResource(platformPermissionCopy.locationServicesReason),
                    state = requiredState(state.isLocationServicesEnabled),
                    onGrant = onOpenLocationServices,
                )
            }

            // The teaser that replaces the old wall: auto-detection exists, and it is activated
            // later from the map, when there is a parking to watch — not granted here up-front.
            // [ONB-PERMISSIONS-ASK-AT-THE-MOMENT-OF-VALUE-001]
            Spacer(Modifier.height(PaparcarSpacing.xl))
            Text(
                text = stringResource(Res.string.permissions_core_detection_teaser),
                style = PaparcarType.current.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (showProducer) {
            PermissionTier(
                icon = Icons.Rounded.Sensors,
                title = stringResource(Res.string.permissions_section_detection),
                benefit = stringResource(Res.string.permissions_tier_detection_benefit),
                satisfied = state.producerGranted,
            ) {
                // Ubicación concedida pero APROXIMADA: el único estado en que el usuario cree estar
                // servido y no lo está, así que la fila solo aparece entonces — y va la primera,
                // porque sin precisión nada de lo que viene debajo sirve de nada. En Android no
                // aparece nunca. [IOS-PERMISSIONS-A-ROW-MUST-BE-GRANTABLE-HERE-001]
                if (state.hasReducedLocationAccuracy) {
                    PermissionRow(
                        icon = Icons.Rounded.MyLocation,
                        title = stringResource(Res.string.permissions_perm_precise),
                        reason = stringResource(platformPermissionCopy.preciseLocationReason),
                        state = requiredState(granted = false),
                        onGrant = onRequestPreciseLocation,
                    )
                    Spacer(Modifier.height(PaparcarSpacing.md))
                }
                PermissionRow(
                    icon = Icons.Rounded.Explore,
                    title = stringResource(Res.string.permissions_perm_background),
                    reason = stringResource(Res.string.permissions_perm_background_desc),
                    state = requiredState(state.hasBackgroundLocation),
                    onGrant = onRequestBackgroundLocation,
                )
                // Sin coprocesador de movimiento no hay nada que conceder: una fila permanentemente
                // pendiente que el usuario no puede resolver es peor que no tenerla.
                if (state.isActivityRecognitionAvailable) {
                    Spacer(Modifier.height(PaparcarSpacing.md))
                    PermissionRow(
                        icon = Icons.Rounded.Person,
                        title = stringResource(Res.string.permissions_perm_activity),
                        reason = stringResource(Res.string.permissions_perm_activity_desc),
                        state = requiredState(state.hasActivityRecognition),
                        onGrant = onRequestActivityRecognition,
                    )
                }
                Spacer(Modifier.height(PaparcarSpacing.md))
                PermissionRow(
                    icon = Icons.Rounded.Notifications,
                    title = stringResource(Res.string.permissions_perm_notifications),
                    reason = stringResource(Res.string.permissions_perm_notifications_desc),
                    state = requiredState(state.hasNotifications),
                    onGrant = onRequestNotifications,
                )
            }

            // El respiro entre secciones que antes ponía el conector de la timeline.
            Spacer(Modifier.height(PaparcarSpacing.xxl))

            PermissionTier(
                icon = Icons.Rounded.Shield,
                title = stringResource(Res.string.permissions_section_optional),
                benefit = stringResource(Res.string.permissions_tier_optional_benefit),
                // Lo que esta plataforma no puede conceder no puede impedir el check: en iOS la
                // exención de batería no existe, y exigirla dejaba el tier opcional en gris para
                // siempre. [IOS-PERMISSIONS-A-ROW-MUST-BE-GRANTABLE-HERE-001]
                satisfied = state.hasBluetoothConnect &&
                    (!isBatteryOptimizationRelevant || state.isBatteryOptimizationExempt),
            ) {
                PermissionRow(
                    icon = Icons.Rounded.Bluetooth,
                    title = stringResource(Res.string.permissions_perm_bluetooth),
                    reason = stringResource(Res.string.permissions_perm_bluetooth_desc),
                    state = optionalState(state.hasBluetoothConnect),
                    // Pairing car Bluetooth is the single jump to the Automatic tier. [DET-TIERS-001]
                    unlocks = stringResource(
                        Res.string.permissions_tier_unlocks,
                        stringResource(Res.string.permissions_tier_automatic_name),
                    ),
                    onGrant = onRequestBluetooth,
                )
                // La exención de batería es un concepto de Android (Doze + los OEM agresivos). El
                // pie de esta misma pantalla y Ajustes ya consultaban este flag; la lista no, y por
                // eso iOS pintaba una fila que al pulsarla no hacía nada.
                if (isBatteryOptimizationRelevant) {
                    Spacer(Modifier.height(PaparcarSpacing.md))
                    PermissionRow(
                        icon = Icons.Rounded.BatteryFull,
                        title = stringResource(Res.string.permissions_perm_battery),
                        reason = stringResource(Res.string.permissions_perm_battery_desc),
                        state = optionalState(state.isBatteryOptimizationExempt),
                        // The battery exemption lifts the bare Assisted tier to Assisted + (fewer prompts).
                        unlocks = stringResource(
                            Res.string.permissions_tier_unlocks,
                            stringResource(Res.string.permissions_tier_assisted_plus_name),
                        ),
                        onGrant = onRequestBatteryOptimization,
                    )
                }
                if (isBatteryOptimizationRelevant && !state.isBatteryOptimizationExempt) {
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
                            PapColor.attention
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
                color = PapColor.attention,
            )
        }
    }
}

@Composable
private fun ColumnScope.PermissionsFooter(
    state: PermissionsState,
    focus: PermissionsFocus,
    isCorePending: Boolean,
    isGpsPending: Boolean,
    requiredComplete: Boolean,
    onRequestPermissions: () -> Unit,
    onNotNow: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onFinish: () -> Unit,
) {
    // Everything THIS focus asks for is granted. On the producer visit, surface the optional
    // background-reliability toggle the copy promises ("permite la actividad en segundo plano aquí
    // abajo") as the emphasised action — it's the highest-impact opt-in on Doze-aggressive OEMs —
    // with a plain "Continue" underneath. The core visit exits plainly: battery reliability props
    // up a detection the user hasn't activated yet, so it has no business there. [PERM-FOOTER-001]
    if (requiredComplete) {
        val batteryPending = focus == PermissionsFocus.Producer &&
            isBatteryOptimizationRelevant && !state.isBatteryOptimizationExempt
        if (batteryPending) {
            PapFooterButton(
                label = stringResource(Res.string.permissions_btn_allow_background),
                onClick = onRequestBatteryOptimization,
            )
            TextButton(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(Res.string.permissions_btn_continue),
                    style = PaparcarType.current.cta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            PapFooterButton(
                label = stringResource(Res.string.permissions_btn_continue),
                onClick = onFinish,
            )
        }
        return
    }

    // Location permanently denied / revoked → the request dialog would no-op, so jump straight to the
    // amber "open settings" CTA from the first frame. [DET-READY-001m]
    // ⛔ Se pregunta por el DIÁLOGO, no por el flag de Android: en iOS «ya no va a salir» se sabe
    // por el estado de autorización, y leer solo `locationPermanentlyDenied` dejaba el CTA
    // diciendo «Conceder» sobre una petición que el sistema descarta.
    // [IOS-PERMISSIONS-A-ROW-MUST-BE-GRANTABLE-HERE-001] [DET-READY-001m]
    val coreNeedsSettings = isCorePending && state.dialogSpent(SystemPermissionDialog.LOCATION)
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
    PapFooterButton(
        label = label,
        onClick = onRequestPermissions,
        containerColor = if (isAmber) PapColor.attention else null,
        contentColor = if (isAmber) PapColor.onAttention else null,
    )
    // "Not now" — the producer visit is voluntary (the user tapped an "activate detection" CTA in
    // Home/Settings), so declining is one tap back to where they came from, with no guilt dialog:
    // the Home banner keeps owning the ask. The core visit has no producer ask to decline.
    // [ONB-PERMISSIONS-ASK-AT-THE-MOMENT-OF-VALUE-001]
    if (focus == PermissionsFocus.Producer) {
        TextButton(
            onClick = onNotNow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(Res.string.permissions_btn_not_now),
                // TextButton text → cta, the app's button convention. [TYPO-AUDIT-001]
                style = PaparcarType.current.cta,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
