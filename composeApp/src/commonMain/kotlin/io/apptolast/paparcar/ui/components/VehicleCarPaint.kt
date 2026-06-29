package io.apptolast.paparcar.ui.components

import androidx.compose.ui.graphics.Color
import io.apptolast.paparcar.domain.model.VehicleColor

/**
 * Resolves the per-role body palette used to recolour the vehicle pictograms
 * (side-profile + top-down) for a chosen [VehicleColor].
 *
 * The source artwork is brand-green and built from a tiny, *constant* set of
 * "body-family" colours regardless of the car shape:
 *  - `#00794A` / `#009F5E` → the main body fill          → [body]
 *  - `#23C47D`             → roof / window-frame highlight → [bodyHi]
 *  - `#005E39`             → side crease / cargo divider   → [bodyLo]
 *
 * Everything else (windows `#CFE9F6`, wheels `#15281D`, hubs `#5C7A6B`,
 * headlight `#EAFBF2`, ground shadow, white outline) is theme-neutral and is
 * left exactly as authored — so a recoloured car keeps the same windows, wheels
 * and shading, only the body changes. [VEH-COLOR-001]
 */
internal data class CarPalette(
    val body: Color,
    val bodyHi: Color,
    val bodyLo: Color,
)

// Source "body-family" colours baked into the (light) drawables, as opaque ARGB longs.
private const val SRC_BODY_A = 0xFF00794A
private const val SRC_BODY_B = 0xFF009F5E
private const val SRC_BODY_HI = 0xFF23C47D
private const val SRC_BODY_LO = 0xFF005E39

// Theme-neutral source colours that the *_dark drawables swap for darker equivalents. The recolour
// mirrors that so a recoloured car in dark theme matches the default-green dark look (windows go
// dark, hubs pick up the body accent, shadow lightens) instead of keeping the light palette.
private const val SRC_GLASS = 0xFFCFE9F6
private const val SRC_TIRE = 0xFF15281D
private const val SRC_HUB = 0xFF5C7A6B
private const val SRC_SHADOW = 0xFF0E1A2E
private const val DARK_GLASS = 0xFF16243C
private const val DARK_TIRE = 0xFF0A130D
private const val DARK_SHADOW = 0xFF7C8BA1

// Lightness deltas used to derive the roof highlight and the crease shade from the
// base body colour, matching the relationship in the original green artwork.
private const val HIGHLIGHT_LIGHTNESS_DELTA = 0.15f
private const val SHADE_LIGHTNESS_DELTA = -0.09f

/**
 * Identity palette reproducing the original brand-green artwork (no recolour). Used for the
 * default (no chosen [VehicleColor]) path so every car renders through the same geometry builder
 * and therefore carries the white body + wheel border. The side-profile body green (`SRC_BODY_A`)
 * and the top-down body green (`SRC_BODY_B`) differ, so [topdown] picks the right one — recolor()
 * collapses both body-family sources onto [CarPalette.body], so each orientation maps to itself.
 * [CAR-WHITE-BORDER-001]
 */
internal fun defaultCarPalette(topdown: Boolean): CarPalette = CarPalette(
    body = Color(if (topdown) SRC_BODY_B else SRC_BODY_A),
    bodyHi = Color(SRC_BODY_HI),
    bodyLo = Color(SRC_BODY_LO),
)

/** Builds the [CarPalette] for [color] in the active theme. */
internal fun carPaletteOf(color: VehicleColor, isDark: Boolean): CarPalette {
    val base = Color(if (isDark) color.bodyDarkArgb else color.bodyLightArgb)
    return CarPalette(
        body = base,
        bodyHi = base.adjustLightness(HIGHLIGHT_LIGHTNESS_DELTA),
        bodyLo = base.adjustLightness(SHADE_LIGHTNESS_DELTA),
    )
}

/**
 * Maps a source colour from the (light) artwork to its recoloured equivalent. Body-family
 * colours are swapped for the [palette]; theme-neutral colours are swapped for their dark
 * equivalents when [isDark] so the result matches the default-green dark drawable; everything
 * else passes through. [src] is an opaque `0xAARRGGBB` long taken verbatim from the geometry.
 */
internal fun recolor(src: Long, palette: CarPalette, isDark: Boolean): Color = when (src) {
    SRC_BODY_A, SRC_BODY_B -> palette.body
    SRC_BODY_HI -> palette.bodyHi
    SRC_BODY_LO -> palette.bodyLo
    // In dark theme the default art tints the wheel hubs with the body accent.
    SRC_HUB -> if (isDark) palette.bodyHi else Color(src)
    SRC_GLASS -> Color(if (isDark) DARK_GLASS else src)
    SRC_TIRE -> Color(if (isDark) DARK_TIRE else src)
    SRC_SHADOW -> Color(if (isDark) DARK_SHADOW else src)
    else -> Color(src)
}

/**
 * Returns a copy of this colour with its HSL lightness shifted by [delta]
 * (clamped to `0f..1f`), preserving hue and saturation. Used to derive the roof
 * highlight (+) and crease shade (−) from the body colour.
 */
private fun Color.adjustLightness(delta: Float): Color {
    val r = red
    val g = green
    val b = blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val l = (max + min) / 2f
    val d = max - min

    val s = if (d == 0f) 0f else d / (1f - kotlin.math.abs(2f * l - 1f))
    val h = when {
        d == 0f -> 0f
        max == r -> 60f * (((g - b) / d) % 6f)
        max == g -> 60f * (((b - r) / d) + 2f)
        else -> 60f * (((r - g) / d) + 4f)
    }.let { if (it < 0f) it + 360f else it }

    val newL = (l + delta).coerceIn(0f, 1f)
    val c = (1f - kotlin.math.abs(2f * newL - 1f)) * s
    val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
    val m = newL - c / 2f
    val (r1, g1, b1) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(
        red = (r1 + m).coerceIn(0f, 1f),
        green = (g1 + m).coerceIn(0f, 1f),
        blue = (b1 + m).coerceIn(0f, 1f),
        alpha = alpha,
    )
}
