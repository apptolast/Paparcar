package io.apptolast.paparcar.presentation.permissions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.ui.components.PapPrimaryButton
import io.apptolast.paparcar.ui.components.PapSectionHeader
import io.apptolast.paparcar.ui.illustrations.OnboardingHero
import io.apptolast.paparcar.ui.theme.PaparcarSpacing
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.perm_rationale_cta
import paparcar.composeapp.generated.resources.perm_rationale_subtitle
import paparcar.composeapp.generated.resources.perm_rationale_title
import paparcar.composeapp.generated.resources.permissions_perm_activity
import paparcar.composeapp.generated.resources.permissions_perm_activity_desc
import paparcar.composeapp.generated.resources.permissions_perm_background
import paparcar.composeapp.generated.resources.permissions_perm_background_desc
import paparcar.composeapp.generated.resources.permissions_perm_battery
import paparcar.composeapp.generated.resources.permissions_perm_battery_desc
import paparcar.composeapp.generated.resources.permissions_perm_bluetooth
import paparcar.composeapp.generated.resources.permissions_perm_bluetooth_desc
import paparcar.composeapp.generated.resources.permissions_perm_location
import paparcar.composeapp.generated.resources.permissions_perm_location_desc
import paparcar.composeapp.generated.resources.permissions_perm_location_services
import paparcar.composeapp.generated.resources.permissions_perm_location_services_desc
import paparcar.composeapp.generated.resources.permissions_perm_notifications
import paparcar.composeapp.generated.resources.permissions_perm_notifications_desc
import paparcar.composeapp.generated.resources.permissions_section_detection
import paparcar.composeapp.generated.resources.permissions_section_essential
import paparcar.composeapp.generated.resources.permissions_section_optional

private val TOP_CONTENT_PADDING    = 56.dp
private val HERO_ILLUSTRATION_W    = 140.dp
private val HERO_ILLUSTRATION_H    = 120.dp

/**
 * Pantalla explicativa previa a la concesión ("Automate your parking"). Lee como "la lista que
 * ahora vas a conceder": mismo hero, mismas secciones y mismo orden de permisos que la pantalla de
 * concesión [PermissionsContent], pero con las filas en estado pre-concesión (requeridas en
 * `Pending`, opcionales en `Optional`) y sin acción — informativas. [ONB-IDENTITY-001 D/E]
 */
@Composable
fun PermissionsRationaleScreen(
    onAccept: () -> Unit,
) {
    // Reserva como padding inferior la altura medida del footer (CTA + nav bar) + 16dp, para que la
    // última fila nunca quede tapada por el botón a sangre. [ONB-IDENTITY-001 F]
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
                    top = TOP_CONTENT_PADDING,
                    bottom = with(density) { footerHeightPx.toDp() } + PaparcarSpacing.lg,
                )
                .verticalScroll(rememberScrollState()),
        ) {
            // Header — hero + título + subtítulo centrados (mismo patrón que grant y onboarding).
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                OnboardingHero(
                    hero = OnboardingHero.AUTOMATION,
                    modifier = Modifier.size(HERO_ILLUSTRATION_W, HERO_ILLUSTRATION_H),
                )
                Spacer(Modifier.height(PaparcarSpacing.md))
                Text(
                    text = stringResource(Res.string.perm_rationale_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(PaparcarSpacing.sm))
                Text(
                    text = stringResource(Res.string.perm_rationale_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(PaparcarSpacing.xxxl))

            // ── ESSENTIAL ───────────────────────────────────────────────────
            PapSectionHeader(
                title = stringResource(Res.string.permissions_section_essential),
                modifier = Modifier.padding(bottom = PaparcarSpacing.md),
            )
            PermissionRow(
                icon = Icons.Default.LocationOn,
                title = stringResource(Res.string.permissions_perm_location),
                reason = stringResource(Res.string.permissions_perm_location_desc),
                state = PermissionUiState.Pending,
            )
            Spacer(Modifier.height(PaparcarSpacing.md))
            PermissionRow(
                icon = Icons.Default.Settings,
                title = stringResource(Res.string.permissions_perm_location_services),
                reason = stringResource(Res.string.permissions_perm_location_services_desc),
                state = PermissionUiState.Pending,
            )

            // ── AUTO-DETECTION ──────────────────────────────────────────────
            Spacer(Modifier.height(PaparcarSpacing.xl))
            PapSectionHeader(
                title = stringResource(Res.string.permissions_section_detection),
                modifier = Modifier.padding(bottom = PaparcarSpacing.md),
            )
            PermissionRow(
                icon = Icons.Outlined.Explore,
                title = stringResource(Res.string.permissions_perm_background),
                reason = stringResource(Res.string.permissions_perm_background_desc),
                state = PermissionUiState.Pending,
            )
            Spacer(Modifier.height(PaparcarSpacing.md))
            PermissionRow(
                icon = Icons.Default.Person,
                title = stringResource(Res.string.permissions_perm_activity),
                reason = stringResource(Res.string.permissions_perm_activity_desc),
                state = PermissionUiState.Pending,
            )
            Spacer(Modifier.height(PaparcarSpacing.md))
            PermissionRow(
                icon = Icons.Default.Notifications,
                title = stringResource(Res.string.permissions_perm_notifications),
                reason = stringResource(Res.string.permissions_perm_notifications_desc),
                state = PermissionUiState.Pending,
            )

            // ── OPTIONAL · reliability ──────────────────────────────────────
            Spacer(Modifier.height(PaparcarSpacing.xl))
            PapSectionHeader(
                title = stringResource(Res.string.permissions_section_optional),
                modifier = Modifier.padding(bottom = PaparcarSpacing.md),
            )
            PermissionRow(
                icon = Icons.Outlined.Bluetooth,
                title = stringResource(Res.string.permissions_perm_bluetooth),
                reason = stringResource(Res.string.permissions_perm_bluetooth_desc),
                state = PermissionUiState.Optional,
            )
            Spacer(Modifier.height(PaparcarSpacing.md))
            PermissionRow(
                icon = Icons.Outlined.BatteryFull,
                title = stringResource(Res.string.permissions_perm_battery),
                reason = stringResource(Res.string.permissions_perm_battery_desc),
                state = PermissionUiState.Optional,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = PaparcarSpacing.xxl)
                .navigationBarsPadding()
                .padding(bottom = PaparcarSpacing.xxxl)
                .onSizeChanged { footerHeightPx = it.height },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PapPrimaryButton(
                label = stringResource(Res.string.perm_rationale_cta),
                onClick = onAccept,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
