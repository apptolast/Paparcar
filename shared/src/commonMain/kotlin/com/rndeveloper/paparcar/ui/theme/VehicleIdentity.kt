package com.rndeveloper.paparcar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.rndeveloper.paparcar.domain.model.VehicleMonitoringStatus

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
    // Was `colorScheme.primary` — the brand green itself, which made a watched car and a CTA the
    // same pixel. `papWatchGreen` is its own hue now. [UI-COLOR-EVERY-HUE-EARNS-ITS-MEANING-001]
    VehicleWatch.Assisted -> papWatchGreen
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

/**
 * Solid container FILL for a card that IS one vehicle's live session — the "parked right now" card
 * of the history timeline. The green leg is the scheme's own `primaryContainer`; the Bluetooth leg
 * is its exact mirror one hue over, built from the same tokens the scheme uses for its blue slots.
 * [UI-HISTORY-IDENTITY-AND-SOURCE-001]
 *
 * Deliberately NOT a translucent wash of [vehicleIdentityColor]: the live card has to read as
 * FILLED at a glance, and one alpha cannot hold that over both surface ramps (user's call on
 * device, 21-08-2026). Pair it with [onVehicleIdentityContainer], never with a raw `onSurface`.
 */
@Composable
fun vehicleIdentityContainer(watch: VehicleWatch): Color = when (watch) {
    VehicleWatch.Bluetooth -> if (isDarkSurface()) PapBlueMuted else PapBlueContainerLight
    // Was the scheme's `primaryContainer` — the BRAND's green bed. Once the watched-vehicle green
    // stopped being the brand green, a watched car's live card would have been filled with the
    // brand while its own dot and border read teal. Now it has its own bed, exactly like the
    // Bluetooth leg has had all along. [UI-COLOR-EVERY-HUE-EARNS-ITS-MEANING-001]
    VehicleWatch.Assisted -> if (isDarkSurface()) PapWatchGreenMuted else PapWatchGreenContainerLight
    VehicleWatch.Off -> MaterialTheme.colorScheme.surfaceContainerHigh
}

/** Content colour ON [vehicleIdentityContainer], mirroring the scheme's own container/on-container
 *  pairing (dark: the vivid identity over its muted bed; light: the deep ink of that same hue). */
@Composable
fun onVehicleIdentityContainer(watch: VehicleWatch): Color = when (watch) {
    VehicleWatch.Bluetooth -> if (isDarkSurface()) papCarBlue else PapOnBlue
    VehicleWatch.Assisted -> if (isDarkSurface()) PapWatchGreen else PapOnWatchGreenContainerLight
    VehicleWatch.Off -> MaterialTheme.colorScheme.onSurface
}

/** The same luminance probe the theme-aware tokens in `Color.kt` use — `isSystemInDarkTheme()` lies
 *  when the theme is forced. */
@Composable
private fun isDarkSurface(): Boolean =
    MaterialTheme.colorScheme.surface.luminance() < SURFACE_DARK_LUMINANCE

/** Watched-card frame — the identity colour dimmed so it frames without shouting; full strength is
 *  reserved for the name, glyph and accents. */
private const val CHASSIS_WATCHED_ALPHA = 0.55f
