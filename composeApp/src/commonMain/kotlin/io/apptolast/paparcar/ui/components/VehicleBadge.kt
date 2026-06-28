package io.apptolast.paparcar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.CarbodyType
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.ui.theme.PapBlue
import io.apptolast.paparcar.ui.theme.PapGreen
import io.apptolast.paparcar.ui.theme.PapInk

/**
 * Semantic state of a vehicle, driving the [VehicleBadge] accent. [DET-READY-001k]
 *
 * Replaces the old per-vehicle hue palette ([io.apptolast.paparcar.ui.theme.VehicleAccentPalette]):
 * the badge now reads the same in every surface (chip, peek, map marker, My Vehicles) because the
 * colour is **status**, not identity. Vehicle identity comes from the carbody silhouette + name/plate.
 */
enum class VehicleBadgeTone { Parked, Bluetooth, Idle, Inactive }

/**
 * Resolves the badge tone from vehicle state. Bluetooth wins (its blue is an *informational* signal —
 * "this car auto-detects, hands-off"); then parked (green); then idle (neutral) vs inactive (dimmed).
 */
fun vehicleBadgeTone(isParked: Boolean, isBluetoothPaired: Boolean, isActive: Boolean = true): VehicleBadgeTone =
    when {
        isBluetoothPaired -> VehicleBadgeTone.Bluetooth
        isParked -> VehicleBadgeTone.Parked
        isActive -> VehicleBadgeTone.Idle
        else -> VehicleBadgeTone.Inactive
    }

@Composable
fun vehicleBadgeAccent(tone: VehicleBadgeTone): Color = when (tone) {
    VehicleBadgeTone.Parked -> MaterialTheme.colorScheme.primary
    VehicleBadgeTone.Bluetooth -> MaterialTheme.colorScheme.tertiary
    VehicleBadgeTone.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
    VehicleBadgeTone.Inactive -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = INACTIVE_ALPHA)
}

/** Content colour to use on top of a filled [vehicleBadgeAccent] surface (e.g. a primary CTA). */
@Composable
fun vehicleBadgeOnAccent(tone: VehicleBadgeTone): Color = when (tone) {
    VehicleBadgeTone.Parked -> MaterialTheme.colorScheme.onPrimary
    VehicleBadgeTone.Bluetooth -> MaterialTheme.colorScheme.onTertiary
    VehicleBadgeTone.Idle, VehicleBadgeTone.Inactive -> MaterialTheme.colorScheme.surface
}

/**
 * The canonical "this is a vehicle" badge — one element shown at several sizes across the app. [DET-READY-001k]
 *
 * Logo molde: dark [PapInk] interior + semantic accent ring + accent-tinted carbody pictogram. The
 * single source of truth for the vehicle marker (map), the vehicles chip (Home sheet), the parked
 * peek header and the My Vehicles hero — so the same car always reads the same way everywhere.
 *
 * @param diameter outer circle Ø. Use 28 (chip) / 44 (peek) / 46 (marker) / 56 (hero).
 * @param selected when true the ring turns white to signal map/list selection.
 */
@Composable
fun VehicleBadge(
    carbody: CarbodyType?,
    size: VehicleSize?,
    tone: VehicleBadgeTone,
    diameter: Dp,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    ringWidth: Dp = 2.dp,
) {
    // Always the dark logo molde — the same badge for active and inactive vehicles. The inactive
    // one just reads muted (dim icon, no accent ring). [VEH-BADGE-NEUTRAL-RING]
    val interior = PapInk
    // The disc is a fixed-dark colour, so its icon/ring must use the bright on-dark brand accents
    // directly — the colourScheme primary/tertiary darken in the light theme (PapGreenLight /
    // PapBlueLight) and the pictogram muddied against the dark disc. Neutral tones can't use the
    // theme's onSurfaceVariant either (dark in the light theme); they get a fixed light tint,
    // dimmed further for inactive. [VEH-BADGE-ONINK]
    val iconTint = when (tone) {
        VehicleBadgeTone.Parked -> PapGreen
        VehicleBadgeTone.Bluetooth -> PapBlue
        VehicleBadgeTone.Idle -> Color.White.copy(alpha = NEUTRAL_ICON_ALPHA)
        VehicleBadgeTone.Inactive -> Color.White.copy(alpha = NEUTRAL_ICON_INACTIVE_ALPHA)
    }
    val ring = when {
        selected -> Color.White
        tone == VehicleBadgeTone.Parked -> PapGreen
        tone == VehicleBadgeTone.Bluetooth -> PapBlue
        // The dark disc contrasts on a light chip, but in the dark theme it sits on an equally dark
        // card — a faint light hairline keeps the circle defined there. [VEH-BADGE-NEUTRAL-RING]
        else -> Color.White.copy(alpha = NEUTRAL_RING_ALPHA)
    }
    Box(
        modifier = modifier
            .size(diameter)
            .clip(CircleShape)
            .background(interior)
            .border(width = ringWidth, color = ring, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        VehicleIcon(
            carbody = carbody,
            size = size,
            tint = iconTint,
            modifier = Modifier.size(diameter * ICON_RATIO),
        )
    }
}

private const val ICON_RATIO = 0.64f
private const val INACTIVE_ALPHA = 0.45f
// Faint light hairline that keeps the dark neutral disc defined against dark-theme card surfaces.
// [VEH-BADGE-NEUTRAL-RING]
private const val NEUTRAL_RING_ALPHA = 0.18f
// Muted light icon tints on the dark PapInk disc — idle near-full, inactive dimmer but still
// clearly readable against the near-black disc (a low alpha greyed it into the dark molde).
private const val NEUTRAL_ICON_ALPHA = 0.90f
private const val NEUTRAL_ICON_INACTIVE_ALPHA = 0.74f
