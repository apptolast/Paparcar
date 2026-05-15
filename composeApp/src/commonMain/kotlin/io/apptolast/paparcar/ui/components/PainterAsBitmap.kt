package io.apptolast.paparcar.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize

/**
 * Renderiza un [Painter] (típicamente un vector drawable) a [ImageBitmap]
 * para poder usarlo como BitmapDescriptor en Google Maps Compose.
 *
 * Uso:
 * ```
 * val bitmap = rememberPainterAsBitmap(
 *     painter = painterResource(Res.drawable.ic_marker_my_vehicle),
 *     size = DpSize(64.dp, 80.dp),
 * )
 * Marker(
 *     state = markerState,
 *     icon = BitmapDescriptorFactory.fromBitmap(bitmap.asAndroidBitmap()),
 *     anchor = Offset(0.5f, 1f),
 * )
 * ```
 *
 * Nota: `.asAndroidBitmap()` solo está disponible en androidMain. Para iOS
 * usa la API de marker correspondiente. En KMP normalmente este helper
 * vive en `androidMain` y se invoca desde un `expect/actual`.
 */
@Composable
fun rememberPainterAsBitmap(
    painter: Painter,
    size: DpSize,
): ImageBitmap {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    return remember(painter, size, density) {
        val widthPx = with(density) { size.width.toPx() }
        val heightPx = with(density) { size.height.toPx() }
        val bitmap = ImageBitmap(widthPx.toInt(), heightPx.toInt())
        val canvas = Canvas(bitmap)
        val drawScope = CanvasDrawScope()
        drawScope.draw(
            density = density,
            layoutDirection = layoutDirection,
            canvas = canvas,
            size = Size(widthPx, heightPx),
        ) {
            with(painter) { draw(size = Size(widthPx, heightPx)) }
        }
        bitmap
    }
}

/**
 * Versión que acepta dimensiones individuales en Dp.
 */
@Composable
fun rememberPainterAsBitmap(
    painter: Painter,
    width: Dp,
    height: Dp,
): ImageBitmap = rememberPainterAsBitmap(painter, DpSize(width, height))
