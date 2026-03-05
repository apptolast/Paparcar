package io.apptolast.paparcar.presentation.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory

// ─── Eco palette (mirrored from Color.kt — no Compose dependency here) ────────
private const val COLOR_ECO_GREEN       = 0xFF25F48C.toInt()
private const val COLOR_ECO_FOREST_DARK = 0xFF0D3D2E.toInt()
private const val COLOR_ECO_FOREST      = 0xFF0D1C14.toInt()
private const val COLOR_AMBER_ACCENT    = 0xFFF4A825.toInt()
private const val COLOR_AMBER_MUTED     = 0xFF3D2A10.toInt()

// ─────────────────────────────────────────────────────────────────────────────
// My parked car — dark-green pin with a green car silhouette
// Size: 48 × 56 dp
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Custom map pin for the user's own active parking session.
 *
 * Shape: rounded dark-green circle (EcoForestDark) with a bright-green
 * car silhouette (roof + body) and a pointed tail below.
 * Border: 2.5 dp EcoGreen stroke around the circle.
 */
fun Context.createMyCarMarkerIcon(): BitmapDescriptor {
    val dp = resources.displayMetrics.density
    val r   = 22f * dp          // circle radius
    val pad = 2f  * dp          // padding around circle
    val tailH = 13f * dp        // pin tail height

    val bitmapW = ((r + pad) * 2f).toInt()
    val bitmapH = ((r + pad) * 2f + tailH).toInt()
    val cx = bitmapW / 2f
    val cy = r + pad

    val bmp = Bitmap.createBitmap(bitmapW, bitmapH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 1 ─ Tail (same fill as circle, drawn first so circle overlaps it)
    paint.color = COLOR_ECO_FOREST_DARK
    paint.style = Paint.Style.FILL
    canvas.drawPath(pinTailPath(cx, cy + r, 6f * dp, tailH), paint)

    // 2 ─ Circle fill
    canvas.drawCircle(cx, cy, r, paint)

    // 3 ─ Car roof (narrow, upper rounded rect)
    paint.color = COLOR_ECO_GREEN
    canvas.drawRoundRect(
        RectF(cx - r * 0.52f, cy - r * 0.50f, cx + r * 0.52f, cy - r * 0.02f),
        r * 0.14f, r * 0.14f, paint,
    )

    // 4 ─ Car body (wide, lower rounded rect)
    canvas.drawRoundRect(
        RectF(cx - r * 0.80f, cy - r * 0.04f, cx + r * 0.80f, cy + r * 0.44f),
        r * 0.08f, r * 0.08f, paint,
    )

    // 5 ─ Circle border (drawn last so it appears above car icon)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 2.5f * dp
    canvas.drawCircle(cx, cy, r - 1.25f * dp, paint)

    return BitmapDescriptorFactory.fromBitmap(bmp)
}

// ─────────────────────────────────────────────────────────────────────────────
// Free parking spot — green pin with a bold "P"
// Size: 38 × 46 dp
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Custom map pin for a free spot reported by another user.
 *
 * Shape: bright-green circle (EcoGreen) with a bold dark "P" and
 * a pointed tail below.
 * Border: 1.5 dp EcoForest stroke around the circle.
 */
fun Context.createFreeSpotMarkerIcon(): BitmapDescriptor {
    val dp = resources.displayMetrics.density
    val r   = 17f * dp
    val pad = 2f  * dp
    val tailH = 10f * dp

    val bitmapW = ((r + pad) * 2f).toInt()
    val bitmapH = ((r + pad) * 2f + tailH).toInt()
    val cx = bitmapW / 2f
    val cy = r + pad

    val bmp = Bitmap.createBitmap(bitmapW, bitmapH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 1 ─ Tail
    paint.color = COLOR_ECO_GREEN
    paint.style = Paint.Style.FILL
    canvas.drawPath(pinTailPath(cx, cy + r, 5f * dp, tailH), paint)

    // 2 ─ Circle fill
    canvas.drawCircle(cx, cy, r, paint)

    // 3 ─ "P" label
    paint.color = COLOR_ECO_FOREST
    paint.style = Paint.Style.FILL
    paint.textSize = r * 1.30f
    paint.textAlign = Paint.Align.CENTER
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    // Baseline: cy + half cap-height (~0.43 of text size) centres the glyph
    canvas.drawText("P", cx, cy + r * 0.43f, paint)

    // 4 ─ Circle border
    paint.typeface = Typeface.DEFAULT
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 1.5f * dp
    paint.color = COLOR_ECO_FOREST
    canvas.drawCircle(cx, cy, r - 0.75f * dp, paint)

    return BitmapDescriptorFactory.fromBitmap(bmp)
}

// ─────────────────────────────────────────────────────────────────────────────
// Occupied / taken spot — amber pin with a bold "P"
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Custom map pin for a spot that has been taken (occupied).
 *
 * Shape: amber circle (AmberAccent) with a bold dark "P" and a tail.
 */
fun Context.createOccupiedSpotMarkerIcon(): BitmapDescriptor {
    val dp = resources.displayMetrics.density
    val r   = 17f * dp
    val pad = 2f  * dp
    val tailH = 10f * dp

    val bitmapW = ((r + pad) * 2f).toInt()
    val bitmapH = ((r + pad) * 2f + tailH).toInt()
    val cx = bitmapW / 2f
    val cy = r + pad

    val bmp = Bitmap.createBitmap(bitmapW, bitmapH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 1 ─ Tail
    paint.color = COLOR_AMBER_ACCENT
    paint.style = Paint.Style.FILL
    canvas.drawPath(pinTailPath(cx, cy + r, 5f * dp, tailH), paint)

    // 2 ─ Circle fill
    canvas.drawCircle(cx, cy, r, paint)

    // 3 ─ "P" label
    paint.color = COLOR_AMBER_MUTED
    paint.style = Paint.Style.FILL
    paint.textSize = r * 1.30f
    paint.textAlign = Paint.Align.CENTER
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    canvas.drawText("P", cx, cy + r * 0.43f, paint)

    // 4 ─ Circle border
    paint.typeface = Typeface.DEFAULT
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 1.5f * dp
    paint.color = COLOR_AMBER_MUTED
    canvas.drawCircle(cx, cy, r - 0.75f * dp, paint)

    return BitmapDescriptorFactory.fromBitmap(bmp)
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared helper — builds the triangular tail path for a pin
// ─────────────────────────────────────────────────────────────────────────────

private fun pinTailPath(cx: Float, circleBottomY: Float, halfWidth: Float, tailHeight: Float): Path =
    Path().apply {
        moveTo(cx - halfWidth, circleBottomY - 4f)
        lineTo(cx + halfWidth, circleBottomY - 4f)
        lineTo(cx, circleBottomY + tailHeight)
        close()
    }
