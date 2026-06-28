package io.apptolast.paparcar.domain.model

/**
 * Optional paint colour of a vehicle, used purely as a visual identity cue: it
 * recolours the **body** of the vehicle pictogram (side-profile + top-down) while
 * keeping windows, wheels, headlights and the outline untouched.
 *
 * A `null` [Vehicle.color] means "undefined" and renders the original brand-green
 * artwork — the existing look, unchanged. Only an explicit non-null value swaps the
 * body to a derived palette of the chosen colour (see `VehicleCarPaint` in the UI
 * layer for the light/dark resolution and the roof/crease shade derivation).
 *
 * This axis is independent of [VehicleSize] (length / geofence radius) and
 * [CarbodyType] (body shape / icon) — it never affects detection, sizing, geofence
 * or spot publishing.
 *
 * [bodyLightArgb] / [bodyDarkArgb] are the body fills for the light and dark theme
 * respectively, stored as opaque `0xAARRGGBB` longs so the domain stays free of any
 * Compose/Android type. The dark variant is slightly brighter so the car still lifts
 * off a dark map surface.
 */
enum class VehicleColor(
    val bodyLightArgb: Long,
    val bodyDarkArgb: Long,
) {
    WHITE(0xFFE6E9ED, 0xFFF1F3F5),
    SILVER(0xFFBFC5CC, 0xFFD2D8DF),
    GRAY(0xFF888E97, 0xFF9AA1AA),
    GRAPHITE(0xFF474C54, 0xFF59606A),
    BLACK(0xFF23272B, 0xFF2F343A),
    BLUE(0xFF2563C9, 0xFF3B82F6),
    NAVY(0xFF1E3A6E, 0xFF2E4E8A),
    RED(0xFFD0312D, 0xFFEF4444),
    ORANGE(0xFFE2661E, 0xFFF47A2E),
    YELLOW(0xFFE6B400, 0xFFF5C518),
    GOLD(0xFFC9A227, 0xFFDFB93A),
    BROWN(0xFF6E4B2A, 0xFF865C36),
    ;

    companion object {
        /**
         * Parses a stored enum name back to a [VehicleColor], tolerating unknown /
         * blank values (legacy rows, a colour removed in a future release) by
         * returning `null` — i.e. falling back to the default green. Mirrors the
         * defensive parsing used for [CarbodyType] in the mappers.
         */
        fun fromNameOrNull(value: String?): VehicleColor? =
            value?.takeIf { it.isNotBlank() }?.let { name ->
                runCatching { valueOf(name) }.getOrNull()
            }
    }
}
