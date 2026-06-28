package io.apptolast.paparcar.ui.illustrations

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * Nivel 3 — ilustraciones de marca dibujadas en Compose Canvas (no VectorDrawable).
 *
 * Se dibujan a mano en lugar de importarse como VectorDrawable porque usan `stroke-dasharray`
 * (anillo "radar" en [LocationAlertIllustration], recuadro punteado en [EmptySpotsIllustration])
 * y VectorDrawable de Android NO soporta trazos discontinuos. Hacerlo en Canvas además da
 * variante oscura por parámetro y funciona en iOS (commonMain). Ver regla de iconos en CLAUDE.md.
 *
 * Todas comparten el viewBox de diseño 140x120; se escalan preservando aspecto y se centran.
 */
private const val VIEW_W = 140f
private const val VIEW_H = 120f
private const val SHADOW_LIGHT_ALPHA = 0.06f
private const val SHADOW_DARK_ALPHA = 0.24f

/** "Automatiza tu aparcamiento" — escudo-check con destellos. Onboarding. */
@Composable
fun AutomationIllustration(
    modifier: Modifier = Modifier,
    dark: Boolean = isSystemInDarkTheme(),
) {
    Canvas(modifier.size(140.dp, 120.dp)) {
        viewBox {
            // Sombra base
            drawShadowEllipse(70f, 106f, 38f, 6f, dark)
            // Escudo (cara izquierda + cara derecha más oscura)
            val shieldLeft = if (dark) Color(0xFF1BB873) else Color(0xFF009F5E)
            val shieldRight = if (dark) Color(0xFF0E8C57) else Color(0xFF00824D)
            drawPath(shieldFront(), shieldLeft)
            drawPath(shieldBack(), shieldRight)
            // Check
            val check = Path().apply { moveTo(56f, 56f); lineTo(66f, 66f); lineTo(86f, 44f) }
            drawPath(check, Color.White, style = Stroke(7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            // Destellos
            val spark = if (dark) Color(0xFF2DF58E) else Color(0xFF23C47D)
            drawPath(sparkle(), spark)
            drawCircle(spark, 4.5f, Offset(112f, 74f))
        }
    }
}

/**
 * "Activa la ubicación" — pin de marca + badge de alerta + coche. Gate bloqueante (rojo
 * justificado), pero leído como "serio y amable", no error de sistema.
 */
@Composable
fun LocationAlertIllustration(
    modifier: Modifier = Modifier,
    dark: Boolean = isSystemInDarkTheme(),
) {
    Canvas(modifier.size(140.dp, 120.dp)) {
        viewBox {
            drawShadowEllipse(70f, 107f, 42f, 6f, dark)
            // Disco suave de fondo
            drawCircle(if (dark) Color(0xFF3A2226) else Color(0xFFFCEBEA), 48f, Offset(70f, 54f))
            // Anillo "radar" punteado
            val ringColor = if (dark) Color(0xFFFF5A57) else Color(0xFFE0322F)
            drawCircle(
                ringColor.copy(alpha = if (dark) 0.5f else 0.45f),
                30f,
                Offset(64f, 46f),
                style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.5f, 7f))),
            )
            // Pin (relleno + contorno)
            val pinFill = if (dark) Color(0xFFFF5A57) else Color(0xFFE0322F)
            val pinStroke = if (dark) Color(0xFF16243C) else Color.White
            val pin = locationPin()
            drawPath(pin, pinFill)
            drawPath(pin, pinStroke, style = Stroke(3f, join = StrokeJoin.Round))
            drawCircle(if (dark) Color(0xFF16243C) else Color.White, 7.5f, Offset(64f, 39.5f))
            // Badge de alerta (!)
            withTransform({ translate(82f, 22f) }) {
                val badgeRing = if (dark) Color(0xFF16243C) else Color.White
                val badgeFill = if (dark) Color(0xFFFF5A57) else Color(0xFFBA1A1A)
                val mark = if (dark) Color(0xFF16243C) else Color.White
                drawCircle(badgeRing, 11f, Offset.Zero)
                drawCircle(badgeFill, 9f, Offset.Zero)
                drawLine(mark, Offset(0f, -4.2f), Offset(0f, 1.2f), 2.4f, StrokeCap.Round)
                drawCircle(mark, 1.5f, Offset(0f, 4.6f))
            }
            // Coche
            miniCar(translateX = 86f, translateY = 76f, scale = 0.62f, dark = dark)
        }
    }
}

/** "No hay plazas cerca" — plaza vacía punteada + pin fantasma + coche. Empty state. */
@Composable
fun EmptySpotsIllustration(
    modifier: Modifier = Modifier,
    dark: Boolean = isSystemInDarkTheme(),
) {
    Canvas(modifier.size(140.dp, 120.dp)) {
        viewBox {
            drawShadowEllipse(70f, 106f, 40f, 6f, dark)
            // Plaza vacía (recuadro redondeado punteado)
            val bayStroke = if (dark) Color(0xFF3A4D6B) else Color(0xFFB9C6DA)
            val bay = Path().apply {
                addRoundRect(RoundRect(38f, 58f, 38f + 64f, 58f + 40f, 9f, 9f))
            }
            drawPath(
                bay,
                bayStroke,
                style = Stroke(2.6f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 7f))),
            )
            // Pin fantasma
            withTransform({ translate(54f, 16f) }) {
                val ghost = Path().apply {
                    moveTo(16f, 2f)
                    cubicTo(24f, 2f, 30f, 8f, 30f, 16f)
                    cubicTo(30f, 26f, 16f, 40f, 16f, 40f)
                    cubicTo(16f, 40f, 2f, 26f, 2f, 16f)
                    cubicTo(2f, 8f, 8f, 2f, 16f, 2f)
                    close()
                }
                drawPath(ghost, if (dark) Color(0xFF2A3A55) else Color(0xFFE0E6F2))
                drawCircle(if (dark) Color(0xFF16243C) else Color.White, 6f, Offset(16f, 15.5f))
            }
            // Coche
            miniCar(translateX = 92f, translateY = 74f, scale = 0.6f, dark = dark)
        }
    }
}

// region — helpers de dibujo

/** Aplica el transform viewBox→canvas (escala con aspecto + centrado) y ejecuta [block]. */
private inline fun DrawScope.viewBox(block: DrawScope.() -> Unit) {
    val s = min(size.width / VIEW_W, size.height / VIEW_H)
    val dx = (size.width - VIEW_W * s) / 2f
    val dy = (size.height - VIEW_H * s) / 2f
    withTransform({
        translate(dx, dy)
        scale(s, s, Offset.Zero)
    }) { block() }
}

private fun DrawScope.drawShadowEllipse(cx: Float, cy: Float, rx: Float, ry: Float, dark: Boolean) {
    val color = if (dark) Color.Black else Color(0xFF0E1A2E)
    val alpha = if (dark) SHADOW_DARK_ALPHA else SHADOW_LIGHT_ALPHA
    drawOval(color.copy(alpha = alpha), topLeft = Offset(cx - rx, cy - ry), size = Size(rx * 2, ry * 2))
}

/** Coche de marca compacto, reutilizado en location-alert y empty-spots. */
private fun DrawScope.miniCar(translateX: Float, translateY: Float, scale: Float, dark: Boolean) {
    val wheel = if (dark) Color(0xFF0A130D) else Color(0xFF15281D)
    val body = if (dark) Color(0xFF2DF58E) else Color(0xFF009F5E)
    val roof = if (dark) Color(0xFF0E8C57) else Color(0xFF23C47D)
    withTransform({
        translate(translateX, translateY)
        scale(scale, scale, Offset.Zero)
    }) {
        drawOval(Color.Black.copy(alpha = if (dark) 0.25f else 0.12f), Offset(12f, 59f), Size(32f, 6f))
        drawCircle(wheel, 6f, Offset(9f, 49f))
        drawCircle(wheel, 6f, Offset(47f, 49f))
        val bodyPath = Path().apply {
            moveTo(5f, 41f)
            cubicTo(5f, 35f, 9f, 30f, 14f, 30f)
            lineTo(22f, 30f)
            quadraticBezierTo(24f, 18f, 30f, 13f)
            lineTo(40f, 13f)
            quadraticBezierTo(46f, 18f, 49f, 30f)
            lineTo(51f, 30f)
            quadraticBezierTo(56f, 30f, 56f, 36f)
            lineTo(56f, 47f)
            quadraticBezierTo(56f, 47f, 53f, 47f)
            lineTo(8f, 47f)
            quadraticBezierTo(5f, 47f, 5f, 41f)
            close()
        }
        drawPath(bodyPath, body)
        drawPath(bodyPath, Color.White, style = Stroke(3f, join = StrokeJoin.Round))
        val roofPath = Path().apply {
            moveTo(24f, 30f)
            quadraticBezierTo(24f, 18f, 30f, 13f)
            lineTo(40f, 13f)
            quadraticBezierTo(46f, 18f, 49f, 30f)
            close()
        }
        drawPath(roofPath, roof)
        drawCircle(Color.White, 2.4f, Offset(9f, 49f))
        drawCircle(Color.White, 2.4f, Offset(47f, 49f))
    }
}

private fun shieldFront(): Path = Path().apply {
    moveTo(70f, 20f)
    lineTo(102f, 32f)
    lineTo(102f, 54f)
    cubicTo(102f, 73f, 89f, 87f, 70f, 93f)
    cubicTo(51f, 87f, 38f, 73f, 38f, 54f)
    lineTo(38f, 32f)
    close()
}

private fun shieldBack(): Path = Path().apply {
    moveTo(70f, 20f)
    lineTo(102f, 32f)
    lineTo(102f, 54f)
    cubicTo(102f, 73f, 89f, 87f, 70f, 93f)
    close()
}

private fun sparkle(): Path = Path().apply {
    moveTo(26f, 30f)
    lineTo(28.5f, 35f)
    lineTo(33.5f, 35.7f)
    lineTo(29.9f, 39.2f)
    lineTo(30.8f, 44.2f)
    lineTo(26f, 47f)
    lineTo(21.2f, 49.2f)
    lineTo(22.1f, 44.2f)
    lineTo(18.5f, 41f)
    lineTo(23.5f, 40.3f)
    close()
}

private fun locationPin(): Path = Path().apply {
    moveTo(64f, 23f)
    cubicTo(73.5f, 23f, 81f, 30.5f, 81f, 40f)
    cubicTo(81f, 52f, 64f, 73f, 64f, 73f)
    cubicTo(64f, 73f, 47f, 52f, 47f, 40f)
    cubicTo(47f, 30.5f, 54.5f, 23f, 64f, 23f)
    close()
}

// endregion
