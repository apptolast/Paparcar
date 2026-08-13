package io.apptolast.paparcar.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import io.apptolast.paparcar.domain.model.CarbodyType
import io.apptolast.paparcar.domain.model.VehicleColor
import io.apptolast.paparcar.domain.model.VehicleSize

// The old VehicleBadgeTone / vehicleBadgeAccent / vehicleBadgeOnAccent lived here. They resolved a
// vehicle's colour from (isParked, isBluetoothPaired) with the METHOD winning over the STATE, which
// is why a parked BT car read blue here and green in the Home chip. Both channels now resolve in
// exactly one place: `ui/theme/VehicleIdentity.kt`. [UI-COLOR-DOCTRINE-001]

/**
 * The bare vehicle pictogram — **no surrounding disc** — for in-sheet/in-list surfaces
 * (Home chip, parked peek header, My Vehicles hero). [BOLT-MARKERS-001]
 *
 * Only the on-map [VehicleBadgeMarker] keeps the light "tag" container; everywhere else the car
 * shows on its own — drawn larger than the old badge ([GLYPH_CAR_SCALE]×) with a brief contact
 * shadow underneath so it still feels grounded. Identity = the full-colour silhouette, kept fully
 * opaque in every state — an inactive (monitoring-stopped) vehicle reads its status through the
 * surrounding accent (`vehicleIdentityColor`), never by fading the car itself (that made a present
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

