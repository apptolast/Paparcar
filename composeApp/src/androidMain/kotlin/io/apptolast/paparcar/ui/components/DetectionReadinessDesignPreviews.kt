package io.apptolast.paparcar.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.ui.theme.PapAmber
import io.apptolast.paparcar.ui.theme.PapBlue
import io.apptolast.paparcar.ui.theme.PapGreen
import io.apptolast.paparcar.ui.theme.PapRed
import io.apptolast.paparcar.ui.theme.PapShapes
import io.apptolast.paparcar.ui.theme.PaparcarSpacing
import io.apptolast.paparcar.ui.theme.PaparcarTheme

// ═══════════════════════════════════════════════════════════════════════════════
//  DET-READY-001 — Detection readiness surfaces: DESIGN EXPLORATION (v2)
//
//  Updated to the LOCKED logic (closes DET-G-02 surfacing):
//    · Blocked split → CORE (red, severe) vs PRODUCER (amber, the star upsell)
//    · Ready → AwaitingFirstPark (Coordinator-only cold-start) with TWO actions:
//        primary "Mark my spot"  ·  secondary "I'm driving" (gated on manual-arm)
//    · Monitoring → ephemeral pill (driving), never a fixed bar
//    · Parked → reuses the EXISTING parked-car card in the sheet (no new surface)
//    · Disabled·non-parking AND Ready(Bluetooth) → silent (no surface)
//
//  Precedence (one state at a time):
//    Disabled → Blocked.Core → Blocked.Producer → Parked → Monitoring → AwaitingFirstPark
//
//  Surface classes (decided): CARD_ACTION (Treatment B) for the 4 action states,
//  PILL_EPHEMERAL (Treatment A) for Monitoring, nothing new for the rest.
//  This file is preview-only — it does NOT depend on the domain model, which we
//  evolve during implementation (split Blocked, rename Ready).
// ═══════════════════════════════════════════════════════════════════════════════

private enum class Severity { ACTION, STEADY, INFO }

private enum class SurfaceClass { CARD_ACTION, PILL_EPHEMERAL, EXISTING_CARD, SILENT }

private data class RVisual(
    val icon: ImageVector,
    val tint: Color,
    val title: String,
    val subtitle: String,
    val cta: String?,
    val secondaryCta: String?,
    val severity: Severity,
    val surface: SurfaceClass,
)

private enum class DesignState(val label: String) {
    NoVehicle("Disabled · no vehicle"),
    BlockedCore("Blocked · CORE — location off"),
    BlockedProducer("Blocked · PRODUCER — detection off  ★"),
    Parked("Parked"),
    Monitoring("Monitoring — driving"),
    AwaitingFirstPark("Awaiting first park — cold-start"),
    NonParking("Disabled · non-parking vehicle"),
    ReadyBluetooth("Ready · Bluetooth — armed"),
}

@Composable
private fun visualFor(state: DesignState): RVisual = when (state) {
    DesignState.NoVehicle -> RVisual(
        icon = Icons.Rounded.Add,
        tint = PapAmber,
        title = "Add your car",
        subtitle = "Automate parking — find-my-car & history",
        cta = "Add car",
        secondaryCta = null,
        severity = Severity.ACTION,
        surface = SurfaceClass.CARD_ACTION,
    )

    DesignState.BlockedCore -> RVisual(
        icon = Icons.Rounded.Warning,
        tint = PapRed,
        title = "Location is off",
        subtitle = "Paparcar needs location to show spots near you",
        cta = "Turn on location",
        secondaryCta = null,
        severity = Severity.ACTION,
        surface = SurfaceClass.CARD_ACTION,
    )

    DesignState.BlockedProducer -> RVisual(
        icon = Icons.Rounded.Explore,
        tint = PapAmber,
        title = "Turn on auto-detection",
        subtitle = "Save your spot automatically when you drive off",
        cta = "Activate",
        secondaryCta = null,
        severity = Severity.ACTION,
        surface = SurfaceClass.CARD_ACTION,
    )

    DesignState.AwaitingFirstPark -> RVisual(
        icon = Icons.Rounded.LocalParking,
        tint = PapBlue,
        title = "Where's your car?",
        subtitle = "Mark your spot to start automating parking",
        cta = "Mark my spot",
        secondaryCta = "I'm driving",
        severity = Severity.ACTION,
        surface = SurfaceClass.CARD_ACTION,
    )

    DesignState.Monitoring -> RVisual(
        icon = Icons.Rounded.DirectionsCar,
        tint = PapGreen,
        title = "Following your trip",
        subtitle = "We'll catch where you park",
        cta = null,
        secondaryCta = null,
        severity = Severity.STEADY,
        surface = SurfaceClass.PILL_EPHEMERAL,
    )

    DesignState.Parked -> RVisual(
        icon = Icons.Rounded.LocalParking,
        tint = PapGreen,
        title = "Parked & protected",
        subtitle = "Watching for departure — handled by the existing parked-car card",
        cta = null,
        secondaryCta = null,
        severity = Severity.STEADY,
        surface = SurfaceClass.EXISTING_CARD,
    )

    DesignState.NonParking -> RVisual(
        icon = Icons.AutoMirrored.Rounded.DirectionsBike,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        title = "Detection off",
        subtitle = "Active vehicle doesn't take parking spots — nothing to show",
        cta = null,
        secondaryCta = null,
        severity = Severity.INFO,
        surface = SurfaceClass.SILENT,
    )

    DesignState.ReadyBluetooth -> RVisual(
        icon = Icons.Rounded.Bluetooth,
        tint = PapGreen,
        title = "Bluetooth armed",
        subtitle = "Fully automatic — no manual action ever needed",
        cta = null,
        secondaryCta = null,
        severity = Severity.STEADY,
        surface = SurfaceClass.SILENT,
    )
}

// Translucent tint over the host surface — reads correctly in both themes.
private fun RVisual.softContainer(): Color = tint.copy(alpha = 0.14f)

// ─────────────────────────────────────────────────────────────────────────────
//  Shared pieces
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun IconTile(v: RVisual, size: Int = 40) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape((size / 3).dp))
            .background(v.tint.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = v.icon,
            contentDescription = null,
            tint = v.tint,
            modifier = Modifier.size((size * 0.55f).dp),
        )
    }
}

@Composable
private fun FilledCtaPill(label: String, tint: Color, onClick: () -> Unit) {
    Surface(shape = PapShapes.chip, color = tint, onClick = onClick) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.padding(horizontal = PaparcarSpacing.lg, vertical = PaparcarSpacing.sm),
        )
    }
}

@Composable
private fun TonalCtaPill(label: String, tint: Color, onClick: () -> Unit) {
    Surface(shape = PapShapes.chip, color = tint.copy(alpha = 0.20f), onClick = onClick) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = tint,
            modifier = Modifier.padding(horizontal = PaparcarSpacing.md, vertical = PaparcarSpacing.xs),
        )
    }
}

@Composable
private fun SecondaryTextCta(label: String, onClick: () -> Unit) {
    Surface(shape = PapShapes.chip, color = Color.Transparent, onClick = onClick) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = PaparcarSpacing.md, vertical = PaparcarSpacing.xs),
        )
    }
}

@Composable
private fun SilentNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontStyle = FontStyle.Italic,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = PaparcarSpacing.sm, top = PaparcarSpacing.xs, bottom = PaparcarSpacing.xs),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Treatment B — Icon-tile action card (the chosen CARD_ACTION surface)
//  Single-CTA states show an inline trailing pill; AwaitingFirstPark (two CTAs)
//  drops to a bottom actions row: secondary text + primary filled pill.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ActionCard(v: RVisual, onCta: () -> Unit = {}, onSecondary: () -> Unit = {}) {
    Surface(
        shape = PapShapes.cardLarge,
        color = v.softContainer(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(PaparcarSpacing.md)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PaparcarSpacing.md),
            ) {
                IconTile(v, size = 40)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = v.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = v.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Single-CTA states keep the compact inline button.
                if (v.cta != null && v.secondaryCta == null) {
                    FilledCtaPill(v.cta, v.tint, onCta)
                }
            }
            // Two-CTA state (AwaitingFirstPark) → actions row underneath.
            if (v.secondaryCta != null && v.cta != null) {
                Spacer(Modifier.height(PaparcarSpacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(PaparcarSpacing.sm, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SecondaryTextCta(v.secondaryCta, onSecondary)
                    FilledCtaPill(v.cta, v.tint, onCta)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Treatment A — Floating capsule (the chosen PILL_EPHEMERAL surface)
//  Content-sized, elevated, centred. For Monitoring: tight & glanceable, no CTA.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CapsulePill(v: RVisual) {
    Surface(
        shape = PapShapes.chip,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        tonalElevation = 2.dp,
        modifier = Modifier.wrapContentWidth(),
    ) {
        Row(
            modifier = Modifier.padding(
                start = PaparcarSpacing.xs,
                end = PaparcarSpacing.lg,
                top = PaparcarSpacing.xs,
                bottom = PaparcarSpacing.xs,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PaparcarSpacing.sm),
        ) {
            IconTile(v, size = 30)
            Text(
                text = v.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Treatment C — Accent-bar row (ALTERNATIVE card form, in-sheet)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AccentRow(v: RVisual, onCta: () -> Unit = {}, onSecondary: () -> Unit = {}) {
    Surface(
        shape = PapShapes.cardSmall,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // Left accent bar spans the full row height.
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(v.tint),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        start = PaparcarSpacing.md,
                        end = PaparcarSpacing.md,
                        top = PaparcarSpacing.sm,
                        bottom = PaparcarSpacing.sm,
                    ),
                verticalArrangement = Arrangement.spacedBy(PaparcarSpacing.sm),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PaparcarSpacing.sm),
                ) {
                    Icon(
                        imageVector = v.icon,
                        contentDescription = null,
                        tint = v.tint,
                        modifier = Modifier.size(20.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = v.title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = v.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // Single-CTA states keep the compact inline tonal pill.
                    if (v.cta != null && v.secondaryCta == null) {
                        TonalCtaPill(v.cta, v.tint, onCta)
                    }
                }
                // Two-CTA state (AwaitingFirstPark) → actions row underneath.
                if (v.secondaryCta != null && v.cta != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(PaparcarSpacing.sm, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SecondaryTextCta(v.secondaryCta, onSecondary)
                        FilledCtaPill(v.cta, v.tint, onCta)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  HEADLINE PREVIEW — the chosen mapping, every state with its real surface class
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ChosenComposition() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(PaparcarSpacing.md),
        verticalArrangement = Arrangement.spacedBy(PaparcarSpacing.sm),
    ) {
        Text(
            text = "Final mapping · one surface at a time",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(PaparcarSpacing.xs))
        DesignState.entries.forEach { st ->
            val v = visualFor(st)
            Text(
                text = "${st.label}   →   ${v.surface.name}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (v.surface) {
                SurfaceClass.CARD_ACTION -> AccentRow(v) // chosen form: accent-bar row (style C)
                SurfaceClass.PILL_EPHEMERAL -> Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) { CapsulePill(v) }
                SurfaceClass.EXISTING_CARD -> SilentNote("↳ reuses the existing parked-car card in the bottom sheet")
                SurfaceClass.SILENT -> SilentNote("↳ no surface")
            }
            Spacer(Modifier.height(PaparcarSpacing.xs))
        }
    }
}

private const val H = 880

@Preview(name = "★ Chosen mapping · Light", showBackground = true, heightDp = H)
@Composable
private fun ChosenLight() = PaparcarTheme(darkTheme = false) { ChosenComposition() }

@Preview(name = "★ Chosen mapping · Dark", showBackground = true, heightDp = H, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChosenDark() = PaparcarTheme(darkTheme = true) { ChosenComposition() }

// ═══════════════════════════════════════════════════════════════════════════════
//  ACTION-CARD FORM COMPARISON — B (icon-tile card) vs C (accent row)
//  Shown for the action states so the card look can still be chosen.
// ═══════════════════════════════════════════════════════════════════════════════

private val actionStates = listOf(
    DesignState.BlockedCore,
    DesignState.BlockedProducer,
    DesignState.NoVehicle,
    DesignState.AwaitingFirstPark,
)

@Composable
private fun CardFormComparison() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(PaparcarSpacing.md),
        verticalArrangement = Arrangement.spacedBy(PaparcarSpacing.md),
    ) {
        Text("B · Icon-tile card", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        actionStates.forEach { ActionCard(visualFor(it)) }
        Spacer(Modifier.height(PaparcarSpacing.sm))
        Text("C · Accent-bar row", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        actionStates.forEach { AccentRow(visualFor(it)) }
    }
}

@Preview(name = "Action card forms · Dark", showBackground = true, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CardFormsDark() = PaparcarTheme(darkTheme = true) { CardFormComparison() }

@Preview(name = "Action card forms · Light", showBackground = true, heightDp = 900)
@Composable
private fun CardFormsLight() = PaparcarTheme(darkTheme = false) { CardFormComparison() }

// ═══════════════════════════════════════════════════════════════════════════════
//  PLACEMENT MOCKUPS — over a mock Home map
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun MockSearchBar() {
    Surface(
        shape = PapShapes.chip,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            "Search parking",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = PaparcarSpacing.lg, vertical = PaparcarSpacing.md),
        )
    }
}

private enum class Placement { CARD_BELOW_SEARCH, PILL_TOP }

@Composable
private fun MockHome(placement: Placement, state: DesignState) {
    val v = visualFor(state)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant), // "map"
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(PaparcarSpacing.md),
            verticalArrangement = Arrangement.spacedBy(PaparcarSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (placement) {
                Placement.PILL_TOP -> {
                    CapsulePill(v)
                    MockSearchBar()
                }
                Placement.CARD_BELOW_SEARCH -> {
                    MockSearchBar()
                    Box(modifier = Modifier.widthIn(max = 480.dp)) { AccentRow(v) }
                }
            }
        }
    }
}

@Preview(name = "Home · Blocked·CORE card (red) · Dark", showBackground = true, heightDp = 560, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeBlockedCore() = PaparcarTheme(darkTheme = true) { MockHome(Placement.CARD_BELOW_SEARCH, DesignState.BlockedCore) }

@Preview(name = "Home · Blocked·PRODUCER card (amber) · Dark", showBackground = true, heightDp = 560, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeBlockedProducer() = PaparcarTheme(darkTheme = true) { MockHome(Placement.CARD_BELOW_SEARCH, DesignState.BlockedProducer) }

@Preview(name = "Home · AwaitingFirstPark card (2 CTAs) · Dark", showBackground = true, heightDp = 560, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeAwaiting() = PaparcarTheme(darkTheme = true) { MockHome(Placement.CARD_BELOW_SEARCH, DesignState.AwaitingFirstPark) }

@Preview(name = "Home · Monitoring pill · Dark", showBackground = true, heightDp = 560, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeMonitoring() = PaparcarTheme(darkTheme = true) { MockHome(Placement.PILL_TOP, DesignState.Monitoring) }

@Preview(name = "Home · AwaitingFirstPark card (2 CTAs) · Light", showBackground = true, heightDp = 560)
@Composable
private fun HomeAwaitingLight() = PaparcarTheme(darkTheme = false) { MockHome(Placement.CARD_BELOW_SEARCH, DesignState.AwaitingFirstPark) }

// ═══════════════════════════════════════════════════════════════════════════════
//  VEHICLE-CHIP cold-start affordance — replaces the muted "Not parked" status with
//  a tappable "Tap to park" + glyph when the car is unparked AND detection is off.
//  Mirrors the real HomeVehicleChip (≈148–160dp wide, icon + name + status line).
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun MockVehicleChip(name: String, parked: Boolean, status: @Composable () -> Unit) {
    val border = if (parked) PapGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    Surface(
        shape = PapShapes.cardSmall,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, border),
        modifier = Modifier.width(160.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DirectionsCar,
                        contentDescription = null,
                        tint = if (parked) PapGreen else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(4.dp))
            status()
        }
    }
}

@Composable
private fun TapToParkAffordance() {
    Surface(shape = PapShapes.chip, color = PapBlue.copy(alpha = 0.16f), onClick = {}) {
        Row(
            modifier = Modifier.padding(horizontal = PaparcarSpacing.sm, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PaparcarSpacing.xs),
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                tint = PapBlue,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = "Tap to park",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = PapBlue,
            )
        }
    }
}

@Composable
private fun VehicleChipAffordance() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(PaparcarSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(PaparcarSpacing.md),
    ) {
        Text(
            "Vehicle chip · unparked status",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(PaparcarSpacing.md)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(PaparcarSpacing.xs)) {
                Text("before", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MockVehicleChip("Tesla M3", parked = false) {
                    Text(
                        "Not parked",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(PaparcarSpacing.xs)) {
                Text("after  ✦", style = MaterialTheme.typography.labelSmall, color = PapBlue)
                MockVehicleChip("Tesla M3", parked = false) { TapToParkAffordance() }
            }
        }
        Text("for reference · parked", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MockVehicleChip("Tesla M3", parked = true) {
            Text(
                "Parked · 120 m",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = PapGreen,
            )
        }
    }
}

@Preview(name = "Vehicle chip affordance · Dark", showBackground = true, heightDp = 320, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChipAffordanceDark() = PaparcarTheme(darkTheme = true) { VehicleChipAffordance() }

@Preview(name = "Vehicle chip affordance · Light", showBackground = true, heightDp = 320)
@Composable
private fun ChipAffordanceLight() = PaparcarTheme(darkTheme = false) { VehicleChipAffordance() }
