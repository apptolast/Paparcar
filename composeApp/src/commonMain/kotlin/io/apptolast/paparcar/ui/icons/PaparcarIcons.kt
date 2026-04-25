package io.apptolast.paparcar.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Custom icon set for Paparcar.
 *
 * Icons are defined as [ImageVector] so they work across all KMP targets
 * without platform drawable resources. When used inside a Compose [Icon]
 * composable the path colours are overridden by the `tint` parameter, so the
 * [SolidColor] values here only need non-zero alpha — they won't appear at runtime.
 */
object PaparcarIcons {

    /**
     * Top-down view of an empty parking bay: a portrait rounded rectangle with
     * two vertical lane-marking lines inside.
     *
     * Used both in the spot list rows and as the map-marker icon for community
     * spots, distinguishing them from the user's own car ([DirectionsCar]).
     *
     * Viewport: 24 × 24 dp
     * Outer rect: 5.5,2 → 18.5,22  (rx = 3)
     * Lane lines: x = 9 and x = 15, from y = 6.5 to y = 17.5
     */
    val ParkingBay: ImageVector by lazy {
        ImageVector.Builder(
            name = "ParkingBay",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {

            // ── Outer bay outline ─────────────────────────────────────────────
            // Portrait rounded rectangle — the boundary of the parking space.
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(7f, 2f)
                lineTo(17f, 2f)
                curveTo(18.66f, 2f, 20f, 3.34f, 20f, 5f)
                lineTo(20f, 19f)
                curveTo(20f, 20.66f, 18.66f, 22f, 17f, 22f)
                lineTo(7f, 22f)
                curveTo(5.34f, 22f, 4f, 20.66f, 4f, 19f)
                lineTo(4f, 5f)
                curveTo(4f, 3.34f, 5.34f, 2f, 7f, 2f)
                close()
            }

            // ── Left lane marking ─────────────────────────────────────────────
            // Vertical line at ~1/3 of the bay width from the left wall.
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(9f, 6.5f)
                lineTo(9f, 17.5f)
            }

            // ── Right lane marking ────────────────────────────────────────────
            // Vertical line at ~2/3 of the bay width from the left wall.
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(15f, 6.5f)
                lineTo(15f, 17.5f)
            }

        }.build()
    }

    /**
     * Brand pin — map-pin teardrop with a "P" cut-out (evenOdd). Mirrors the
     * launcher foreground geometry so the app icon and the in-map markers are
     * visually the same mark. Single fill, tint with the desired status colour
     * via Icon(imageVector = …, tint = …).
     *
     * Viewport : 24 × 24 dp.
     * Pin head : center (12,9), tip (12,22).
     * P bar    : x 10–11, y 5–12.
     * P bowl   : outer arc r=2 peaking at (13,7); inner counter r=1 returns the
     *            bowl hole to filled so the P keeps its shape via XOR winding.
     */
    val SpotPin: ImageVector by lazy {
        ImageVector.Builder(
            name = "SpotPin",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.EvenOdd,
            ) {
                // ── Pin teardrop ──────────────────────────────────────────────
                moveTo(12f, 2f)
                curveTo(8.13f, 2f, 5f, 5.13f, 5f, 9f)
                curveTo(5f, 14.25f, 12f, 22f, 12f, 22f)
                curveTo(12f, 22f, 19f, 14.25f, 19f, 9f)
                curveTo(19f, 5.13f, 15.87f, 2f, 12f, 2f)
                close()

                // ── Outer P (punches a P-shaped hole through the pin) ─────────
                moveTo(10f, 5f)
                lineTo(11f, 5f)
                arcTo(2f, 2f, 0f, false, true, 11f, 9f)
                lineTo(11f, 12f)
                lineTo(10f, 12f)
                close()

                // ── Inner counter (returns the bowl hole to filled) ───────────
                moveTo(11f, 6f)
                arcTo(1f, 1f, 0f, false, true, 11f, 8f)
                close()
            }
        }.build()
    }
}