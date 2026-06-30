package io.apptolast.paparcar.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import io.apptolast.paparcar.domain.model.CarbodyType
import io.apptolast.paparcar.domain.model.VehicleColor
import io.apptolast.paparcar.domain.model.VehicleSize

/**
 * Semantic state of a vehicle, driving the [VehicleBadge] accent. [DET-READY-001k]
 *
 * Replaces the old per-vehicle hue palette: the badge now reads the same in every
 * surface (chip, peek, map marker, My Vehicles) because the
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
 * The bare vehicle pictogram — **no surrounding disc** — for in-sheet/in-list surfaces
 * (Home chip, parked peek header, My Vehicles hero). [BOLT-MARKERS-001]
 *
 * Only the on-map [VehicleBadgeMarker] keeps the light "tag" container; everywhere else the car
 * shows on its own — drawn larger than the old badge ([GLYPH_CAR_SCALE]×) with a brief contact
 * shadow underneath so it still feels grounded. Identity = the full-colour silhouette, kept fully
 * opaque in every state — an inactive (monitoring-stopped) vehicle reads its status through the
 * surrounding accent ([vehicleBadgeAccent]), never by fading the car itself (that made a present
 * car look like it was disappearing).
 *
 * @param glyphSize the nominal box the old badge `diameter` used; the side-profile car is scaled
 *   relative to this. The composable lays out at [GLYPH_CAR_SCALE]× width and a shorter height
 *   (car aspect + shadow), so a row's vertical rhythm is unchanged while the car reads bigger.
 */
@Composable
fun VehicleGlyph(
    carbody: CarbodyType?,
    size: VehicleSize?,
    glyphSize: Dp,
    modifier: Modifier = Modifier,
    color: VehicleColor? = null,
) {
    // The new isometric pictogram already bakes in its own contact shadow, so the glyph just lays it
    // out larger than a square box (it's a wide side-on shape) and lets ContentScale.Fit centre it —
    // no extra drawn shadow (that doubled up). [BOLT-MARKERS-001]
    // The car stays fully opaque in every state: status is carried by the accent around it, not by
    // dimming the silhouette (an inactive car is still parked there, not fading away).
    Box(modifier.size(width = glyphSize * GLYPH_CAR_SCALE, height = glyphSize)) {
        VehicleIcon(
            carbody = carbody,
            size = size,
            tint = Color.Unspecified, // native multi-colour artwork (or recoloured body via [color])
            color = color,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

// VehicleGlyph sizing — the bare car is drawn wider than the nominal box so the side-on pictogram
// reads big; its contact shadow comes baked into the drawable. [BOLT-MARKERS-001]
private const val GLYPH_CAR_SCALE = 1.5f        // box width = glyphSize × this (wide side-on shape)

// Alpha for an inactive (monitoring-stopped) vehicle's accent — the car silhouette itself is never
// dimmed (status reads through the accent, not by fading the pictogram). [INACTIVE-OPAQUE-001]
private const val INACTIVE_ALPHA = 0.45f
