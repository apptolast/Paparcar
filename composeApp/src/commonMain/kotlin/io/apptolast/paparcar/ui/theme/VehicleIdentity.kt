package io.apptolast.paparcar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.apptolast.paparcar.domain.model.VehicleMonitoringStatus

/**
 * THE single source of colour for a vehicle across every surface — Home chip, Home card, Vehicles
 * ficha, sheet peek, map marker. [UI-COLOR-DOCTRINE-001]
 *
 * Replaces four rival resolvers that painted the same car with different ontologies (accent by
 * method, tone with method-over-state priority, an inline marker `when`, a selector dot).
 *
 * Colour model (v3): **the vehicle's colour is its WATCH METHOD, never its state.**
 *
 *  - active detection (Coordinator/assisted) → brand green — the app watching for you.
 *  - Bluetooth → blue — the deterministic, hands-off tier.
 *  - unwatched → grey.
 *
 * The state machine (parked / driving / not marked) is TEXT in `onSurface`, never a colour; a trip
 * in motion is told by ANIMATION (radar halo, pulsing state label), not by a different hue. Green
 * elsewhere stays the brand/action colour; red/amber mean something needs the user; WHICH car it is
 * comes from the full-colour glyph.
 */

/** How a vehicle is monitored — the one axis that owns the vehicle's colour. */
enum class VehicleWatch {
    /** Paired + connected BT device: the deterministic strategy. The "automatic" tier. Blue. */
    Bluetooth,

    /** Coordinator-owned: geofence + activity recognition. The "assisted" tier. Brand green. */
    Assisted,

    /** Not monitored. Grey. */
    Off,
}

fun VehicleMonitoringStatus.watch(): VehicleWatch = when (this) {
    is VehicleMonitoringStatus.Bluetooth -> VehicleWatch.Bluetooth
    VehicleMonitoringStatus.Active -> VehicleWatch.Assisted
    VehicleMonitoringStatus.Inactive -> VehicleWatch.Off
}

/**
 * The vehicle's identity colour — name text, watch glyph, badges, accents. One method, one colour,
 * on every surface: green = active detection, blue = Bluetooth, grey = unwatched.
 */
@Composable
fun vehicleIdentityColor(watch: VehicleWatch): Color = when (watch) {
    VehicleWatch.Bluetooth -> papCarBlue
    VehicleWatch.Assisted -> MaterialTheme.colorScheme.primary
    VehicleWatch.Off -> MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * Card border colour: the identity colour dimmed to a quiet frame for watched cars, neutral for an
 * unwatched one. While a trip is in motion callers thicken the border and use the full-strength
 * identity colour ([vehicleIdentityColor]) — energy comes from weight + animation, not a new hue.
 */
@Composable
fun vehicleChassisBorder(
    watch: VehicleWatch,
    neutral: Color = MaterialTheme.colorScheme.outline.copy(alpha = PapBorders.DEFAULT_OUTLINE_ALPHA),
): Color = when (watch) {
    VehicleWatch.Off -> neutral
    else -> vehicleIdentityColor(watch).copy(alpha = CHASSIS_WATCHED_ALPHA)
}

/** Watched-card frame — the identity colour dimmed so it frames without shouting; full strength is
 *  reserved for the name, glyph and accents. */
private const val CHASSIS_WATCHED_ALPHA = 0.55f
