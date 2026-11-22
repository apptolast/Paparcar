package com.rndeveloper.paparcar.ui.illustrations

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlin.math.min
import org.jetbrains.compose.resources.painterResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.illus_detection_3d

/**
 * Dark detection from the **app theme** (not the system). The app's theme is not driven by
 * `isSystemInDarkTheme()`, so the illustrations must read the rendered surface luminance —
 * same convention as `SpotStateColors` — or they paint the light variant over a dark UI.
 */
@Composable
private fun isAppDark(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

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
private const val WHEEL_RIM = 1.4f

/**
 * Las dos ilustraciones que hacen de HÉROE de un diálogo ([FoundersGiftIllustration] y
 * [AutoDetectionIllustration]) se dibujan más grandes que las de estado vacío.
 *
 * No es gusto: en un diálogo la ilustración comparte ancho con un título, un cuerpo de texto y un
 * botón que ocupan TODO el ancho útil, y a 140 dp quedaba flotando en el centro — se leía como una
 * viñeta pegada encima, no como la cabecera del bloque. En un estado vacío no pasa, porque allí la
 * ilustración no compite con nada. ⛔ Se agranda el LIENZO, nunca los márgenes del diálogo.
 *
 * La proporción es la del render de [AutoDetectionIllustration] (880×747). El regalo se dibuja en
 * un viewBox 140×120 y su transform escala preservando aspecto, así que comparte caja sin
 * deformarse.
 */
private val HERO_ILLUSTRATION_W = 184.dp
private val HERO_ILLUSTRATION_H = 156.dp

/**
 * "Activa la ubicación" — pin de marca + badge de alerta + coche. Gate bloqueante (rojo
 * justificado), pero leído como "serio y amable", no error de sistema.
 */
@Composable
fun LocationAlertIllustration(
    modifier: Modifier = Modifier,
    dark: Boolean = isAppDark(),
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
    dark: Boolean = isAppDark(),
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

/**
 * "No hay gasolineras cerca" — surtidor + anillo punteado (el radio buscado) + coche. Empty state
 * de la sección «dónde repostar». [FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001]
 */
@Composable
fun EmptyFuelStationsIllustration(
    modifier: Modifier = Modifier,
    dark: Boolean = isAppDark(),
) {
    Canvas(modifier.size(140.dp, 120.dp)) {
        viewBox {
            drawShadowEllipse(56f, 106f, 34f, 6f, dark)
            // Anillo punteado: el radio en el que se buscó y no había nada
            val ring = if (dark) Color(0xFF2DF58E) else Color(0xFF009F5E)
            drawCircle(
                ring.copy(alpha = if (dark) 0.5f else 0.45f),
                40f,
                Offset(56f, 62f),
                style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.5f, 7f))),
            )
            // Surtidor
            val hardware = if (dark) Color(0xFF2A3A55) else Color(0xFFE7EEF8)
            val hardwareEdge = if (dark) Color(0xFF16243C) else Color.White
            val cap = if (dark) Color(0xFF3A4D6B) else Color(0xFFD9E2F0)
            val screenFill = if (dark) Color(0xFF0E2A1E) else Color(0xFFE7F6EE)
            val bar = if (dark) Color(0xFF3A4D6B) else Color(0xFFC6D3E6)
            val body = Path().apply {
                addRoundRect(RoundRect(38f, 40f, 74f, 100f, 7f, 7f))
            }
            drawPath(body, hardware)
            drawPath(body, hardwareEdge, style = Stroke(3f, join = StrokeJoin.Round))
            // Remate superior
            val top = Path().apply { addRoundRect(RoundRect(42f, 33f, 70f, 42f, 4f, 4f)) }
            drawPath(top, cap)
            // Pantalla con su marco verde
            val screen = Path().apply { addRoundRect(RoundRect(44f, 48f, 68f, 64f, 4f, 4f)) }
            drawPath(screen, screenFill)
            drawPath(screen, ring, style = Stroke(2.4f))
            // Barras de precio dentro del cuerpo
            val barShape = Path().apply { addRoundRect(RoundRect(44f, 72f, 62f, 77f, 2.5f, 2.5f)) }
            drawPath(barShape, bar)
            val barShape2 = Path().apply { addRoundRect(RoundRect(44f, 82f, 55f, 87f, 2.5f, 2.5f)) }
            drawPath(barShape2, bar)
            // Manguera hasta la pistola, en verde de marca
            val hose = Path().apply {
                moveTo(74f, 52f)
                cubicTo(84f, 52f, 88f, 60f, 88f, 70f)
                lineTo(88f, 80f)
            }
            drawPath(hose, ring, style = Stroke(3f, cap = StrokeCap.Round))
            val nozzle = Path().apply { addRoundRect(RoundRect(84f, 79f, 92f, 90f, 2.5f, 2.5f)) }
            drawPath(nozzle, ring)
            drawPath(nozzle, hardwareEdge, style = Stroke(2f, join = StrokeJoin.Round))
            // Coche
            miniCar(translateX = 92f, translateY = 76f, scale = 0.58f, dark = dark)
        }
    }
}

/**
 * "No hay parkings cerca" — edificio de parking + barrera de acceso + anillo punteado (el radio
 * buscado) + coche. Empty state del carril «dónde aparcar» público. [PARKINGS-ON-THE-MAP-001]
 */
@Composable
fun EmptyParkingsIllustration(
    modifier: Modifier = Modifier,
    dark: Boolean = isAppDark(),
) {
    Canvas(modifier.size(140.dp, 120.dp)) {
        viewBox {
            drawShadowEllipse(60f, 106f, 38f, 6f, dark)
            // Anillo punteado: el radio en el que se buscó y no había nada
            val ring = if (dark) Color(0xFF2DF58E) else Color(0xFF009F5E)
            drawCircle(
                ring.copy(alpha = if (dark) 0.5f else 0.45f),
                42f,
                Offset(60f, 62f),
                style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.5f, 7f))),
            )
            // Edificio del parking
            val hardware = if (dark) Color(0xFF2A3A55) else Color(0xFFE7EEF8)
            val hardwareEdge = if (dark) Color(0xFF16243C) else Color.White
            val cap = if (dark) Color(0xFF3A4D6B) else Color(0xFFD9E2F0)
            val bar = if (dark) Color(0xFF3A4D6B) else Color(0xFFC6D3E6)
            val doorway = if (dark) Color(0xFF0A1220) else Color(0xFFCBD6E6)
            val body = Path().apply {
                addRoundRect(RoundRect(30f, 42f, 92f, 100f, 8f, 8f))
            }
            drawPath(body, hardware)
            drawPath(body, hardwareEdge, style = Stroke(3f, join = StrokeJoin.Round))
            // Remate superior
            val top = Path().apply { addRoundRect(RoundRect(34f, 34f, 88f, 44f, 4f, 4f)) }
            drawPath(top, cap)
            // Plantas: dos bandas de fachada
            val band1 = Path().apply { addRoundRect(RoundRect(38f, 52f, 84f, 60f, 2.5f, 2.5f)) }
            drawPath(band1, bar)
            val band2 = Path().apply { addRoundRect(RoundRect(38f, 66f, 74f, 74f, 2.5f, 2.5f)) }
            drawPath(band2, bar)
            // Entrada (hueco)
            val entrance = Path().apply { addRoundRect(RoundRect(50f, 80f, 72f, 100f, 4f, 4f)) }
            drawPath(entrance, doorway)
            // Barrera de acceso: poste + brazo levantado, en verde de marca
            drawLine(hardwareEdge, Offset(26f, 68f), Offset(26f, 100f), 3f, StrokeCap.Round)
            drawLine(ring, Offset(26f, 70f), Offset(54f, 54f), 3.4f, StrokeCap.Round)
            drawCircle(ring, 3.4f, Offset(26f, 70f))
            // Coche
            miniCar(translateX = 92f, translateY = 76f, scale = 0.58f, dark = dark)
        }
    }
}

/**
 * "Eres de los 2.000 primeros" — el regalo de Fundadores, la promo de Black Friday.
 * [MONEY-FOUNDERS-WELCOME-001] · volumen en [UI-THE-FOUNDERS-GIFT-EARNS-ITS-VOLUME-001]
 *
 * Tres cosas tenían que leerse de un vistazo y ninguna podía escribirse, porque una ilustración
 * que necesita texto no viaja a nueve idiomas:
 *  · **Es un regalo** — la caja con su lazo, la única forma universal de decirlo.
 *  · **Es Black Friday** — la caja es NEGRA. Sin etiqueta de descuento ni porcentaje: un número
 *    dentro del dibujo envejece el día que cambie la promo, y chocaría con el "2.000" del título.
 *  · **Es de Paparcar** — el regalo está DENTRO DE UNA PLAZA, con sus líneas pintadas en el suelo.
 *    El lazo lleva el verde de marca, el mismo que el coche de [miniCar].
 *
 * ### El volumen lo da la PROYECCIÓN, y sólo después la luz
 * Se dibuja en axonometría: **tres caras** —tapa, lateral izquierdo y lateral derecho— construidas
 * sobre dos vectores de eje, [AX_U] y [AX_V]. Eso es lo que hace que ocupe espacio; el degradado
 * por cara y la sombra proyectada sólo lo apoyan. La primera versión estaba dibujada de frente y
 * plana, y ninguna cantidad de sombreado le habría dado cuerpo a un rectángulo.
 *
 * La luz cae **arriba-izquierda → abajo-derecha**, la misma diagonal que el barrido metálico de
 * `PapPremiumSurface`, para que el héroe y el botón del diálogo estén iluminados desde el mismo
 * sitio. De ahí el orden de los tonos: tapa clara, izquierda media, derecha oscura — y la sombra
 * desplazada hacia abajo-derecha, nunca un óvalo centrado bajo la caja.
 *
 * ⛔ **Volumen, no fotorrealismo.** Convive con cuatro ilustraciones planas en este mismo fichero.
 * Un render con reflejos no sería más profesional: sería una pieza de otra familia. Se conservan
 * paleta, grosores y el viewBox 140×120 de sus hermanas.
 *
 * ⛔ No lleva el isotipo. La marca se GENERA, no se dibuja a mano (regla de iconos de CLAUDE.md).
 *
 * ⚠️ Dos fallos que sólo se vieron en el device, los dos de GEOMETRÍA: las líneas de la plaza
 * puestas de pie se leían como las patas de un caballete —por eso la plaza se PINTA sobre asfalto
 * relleno— y un lazo alto y estrecho se leía como un brote de planta saliendo de una maceta —por
 * eso va ancho y bajo.
 */
@Composable
fun FoundersGiftIllustration(
    modifier: Modifier = Modifier,
    dark: Boolean = isAppDark(),
) {
    Canvas(modifier.size(HERO_ILLUSTRATION_W, HERO_ILLUSTRATION_H)) {
        viewBox {
            // ── La plaza ────────────────────────────────────────────────────
            // Un rectángulo en el suelo, en la MISMA proyección que la caja, así que se dibuja
            // como rombo. Relleno primero: sin asfalto debajo, sus líneas se leen como palos.
            val asphalt = if (dark) Color(0xFF0E161B) else Color(0xFFDCE3EA)
            val bayPaint = if (dark) Color(0xFF38454E) else Color(0xFFF4F8FB)
            // La bahía sale de los MISMOS dos ejes que la caja, así que no puede descuadrarse
            // respecto a ella por mucho que se retoque una u otra.
            val bayBack = Offset(70f, BAY_BACK_Y)
            val bayLeft = bayBack + AX_U * BAY_HALF
            val bayRight = bayBack + AX_V * BAY_HALF
            val bayFront = bayBack + AX_U * BAY_HALF + AX_V * BAY_HALF
            drawPath(quad(bayBack, bayLeft, bayFront, bayRight), asphalt)

            // ⛔ SÓLO las dos aristas DELANTERAS, y por eso convergen bajo el regalo.
            //
            // Antes se pintaban también la trasera y una de cada pareja, y el resultado eran tres
            // MUÑONES: de esas líneas la caja tapaba el tramo central y sólo asomaban las puntas,
            // que es exactamente el defecto de las "patas de caballete" de la versión plana,
            // reaparecido en pequeño al ensanchar el cuerpo. Una línea que entra bajo el objeto y
            // no vuelve a salir se lee como suelo; un trozo suelto a cada lado, no.
            //
            // El fondo de la plaza no necesita línea: ya lo dibuja el borde del asfalto.
            drawLine(bayPaint, bayLeft, bayFront, 3.2f, StrokeCap.Round)
            drawLine(bayPaint, bayRight, bayFront, 3.2f, StrokeCap.Round)

            // ── Geometría de la caja ─────────────────────────────────────
            // Todo sale de un punto y dos vectores: cualquier punto de la tapa es
            // `origin + a·u + b·v`, con a y b en [0,1]. Las caras laterales caen en vertical.
            val lidBack = Offset(70f, LID_TOP_Y)
            val lidU = AX_U * LID_HALF_W
            val lidV = AX_V * LID_HALF_W
            val lidLeft = lidBack + lidU
            val lidFront = lidBack + lidU + lidV
            val lidRight = lidBack + lidV

            val bodyBack = Offset(70f, LID_TOP_Y + LID_THICK + BODY_INSET_Y)
            val bodyU = AX_U * BODY_HALF_W
            val bodyV = AX_V * BODY_HALF_W
            val bodyLeft = bodyBack + bodyU
            val bodyFront = bodyBack + bodyU + bodyV
            val bodyRight = bodyBack + bodyV

            // ── Sombra proyectada ──────────────────────────────────────
            // Desplazada hacia donde NO da la luz. Centrarla bajo la caja es lo que delata una
            // ilustración plana a la que se le ha añadido una sombra de adorno.
            val shadowInk = if (dark) Color.Black else Color(0xFF0E1A2E)
            val castAlpha = if (dark) SHADOW_CAST_DARK_ALPHA else SHADOW_CAST_LIGHT_ALPHA
            // La proyectada: lejos del objeto y suave.
            val footBack = bodyBack + Offset(SHADOW_DX, BODY_HEIGHT + SHADOW_DY)
            drawPath(
                quad(footBack, footBack + bodyU, footBack + bodyU + bodyV, footBack + bodyV),
                shadowInk.copy(alpha = castAlpha),
            )
            // La de CONTACTO: pegada a la base y más densa. Es la que dice que la caja TOCA el
            // suelo — con sólo la proyectada, el regalo flota un par de milímetros por encima.
            val contactBack = bodyBack + Offset(CONTACT_DX, BODY_HEIGHT + CONTACT_DY)
            drawPath(
                quad(contactBack, contactBack + bodyU, contactBack + bodyU + bodyV, contactBack + bodyV),
                shadowInk.copy(alpha = castAlpha * CONTACT_ALPHA_FACTOR),
            )

            // ── Cuerpo: dos caras ──────────────────────────────────────
            val ribbon = giftRibbon(dark)
            val box = giftBox(dark)
            drawPath(faceDown(bodyLeft, bodyFront, BODY_HEIGHT), box.left)
            drawPath(faceDown(bodyFront, bodyRight, BODY_HEIGHT), box.right)
            drawPath(bandDown(bodyLeft, bodyFront, BODY_HEIGHT), ribbon.left)
            drawPath(bandDown(bodyFront, bodyRight, BODY_HEIGHT), ribbon.right)

            // ── Tapa: dos caras y el techo ────────────────────────────────
            // Vuela sobre el cuerpo (de ahí BODY_HALF_W < LID_HALF_W), y por eso se dibuja
            // DESPUÉS: su canto tapa la parte alta de las caras del cuerpo.
            drawPath(faceDown(lidLeft, lidFront, LID_THICK), box.lidLeft)
            drawPath(faceDown(lidFront, lidRight, LID_THICK), box.lidRight)
            drawPath(bandDown(lidLeft, lidFront, LID_THICK), ribbon.left)
            drawPath(bandDown(lidFront, lidRight, LID_THICK), ribbon.right)
            drawPath(quad(lidBack, lidLeft, lidFront, lidRight), box.top)
            // La cinta cruza el techo en las DOS direcciones: una sola banda es un precinto.
            drawPath(topBand(lidBack, lidU, lidV), ribbon.top)
            drawPath(topBand(lidBack, lidV, lidU), ribbon.top)

            // ── Oclusión bajo el vuelo de la tapa ────────────────────────────
            // Va DESPUÉS de la tapa, porque lo que oscurece es justo lo que la tapa deja ver del
            // cuerpo. Es la diferencia entre una tapa dibujada encima y una tapa que ESTÁ encima:
            // sin esta banda, el canto y el cuerpo son dos rectángulos pegados.
            val occlusion = Brush.verticalGradient(
                0f to (if (dark) Color.Black else Color(0xFF0E1A2E))
                    .copy(alpha = if (dark) OCCLUSION_DARK_ALPHA else OCCLUSION_LIGHT_ALPHA),
                1f to Color.Transparent,
                startY = bodyLeft.y,
                endY = bodyFront.y + OCCLUSION_DROP,
            )
            drawPath(faceDown(bodyLeft, bodyFront, BODY_HEIGHT), occlusion)
            drawPath(faceDown(bodyFront, bodyRight, BODY_HEIGHT), occlusion)

            // ── El lazo ──────────────────────────────────────────────
            // Ancho y bajo, con las dos lazadas y dos colas que caen sobre el techo.
            val knot = lidBack + lidU * 0.5f + lidV * 0.5f - Offset(0f, BOW_LIFT)
            val bow = bowFinish(dark, knot)
            // Las colas primero: salen de DEBAJO del nudo, así que todo lo demás las tapa.
            drawPath(bowTail(knot, toLeft = true), bow.tail)
            drawPath(bowTail(knot, toLeft = false), bow.tail)
            // Cada lazada con su propio degradado, claro en la punta y oscuro al nacer: una cinta
            // curvada tiene dos tonos, y es eso —no el contorno— lo que la saca de ser una mancha.
            drawPath(bowLoop(knot, toLeft = true), bow.loopLeft)
            drawPath(bowLoop(knot, toLeft = false), bow.loopRight)
            // El pliegue donde la lazada nace del nudo. Sin él el lazo lee MARIPOSA.
            drawPath(bowCrease(knot, toLeft = true), bow.crease)
            drawPath(bowCrease(knot, toLeft = false), bow.crease)
            drawCircle(bow.knotShade, BOW_KNOT_R, knot + Offset(0.6f, 1f))
            drawCircle(ribbon.knot, BOW_KNOT_R, knot)

            // ── Destellos ────────────────────────────────────────────
            // La nota de "esto es un momento". Fuera de la caja, para no ensuciar su silueta.
            drawPath(sparkle(118f, 30f, 6.5f), ribbon.knot)
            drawPath(sparkle(20f, 54f, 5f), ribbon.knot)
            drawPath(sparkle(110f, 64f, 3.4f), ribbon.knot.copy(alpha = 0.7f))
        }
    }
}

/**
 * "Lo mejor de Paparcar" — la detección automática, dicha como ESCENA: tu coche aparcado en su
 * plaza, la zona que la app vigila marcada en el suelo, y el marcador emitiendo sobre el techo.
 *
 * ⛔ **Es la ÚNICA ilustración rasterizada del set, y es deliberado.** Sus cinco hermanas se
 * dibujan en Canvas porque son planas y usan trazos discontinuos que un VectorDrawable no soporta.
 * Ésta es un render 3D con luz propia, reflejos y degradados de superficie: eso no se dibuja con
 * paths, y los dos intentos de hacerlo lo demostraron — extruido como poliedro sobre los ejes de
 * [FoundersGiftIllustration] leyó primero TARTA DE PISOS y después MÁQUINA INDUSTRIAL, porque a
 * este tamaño un coche se reconoce por su PERFIL y eso es curvatura. Una caja de regalo sí es una
 * caja; un coche no.
 *
 * ⛔ **Un solo asset para los dos temas, y no es un olvido.** Sus hermanas llevan variante `_dark`
 * porque son dibujos de línea que se apagan contra el fondo. Ésta trae su propio suelo —la losa de
 * asfalto— y su propia luz, así que es un OBJETO sobre la superficie del diálogo, no un dibujo
 * sobre ella: el mismo render se sostiene en claro y en oscuro. Nivel 3, nunca se tiñe.
 *
 * WebP y no PNG: 56 KB frente a 272 KB del maestro, sin pérdida visible a este tamaño.
 */
@Composable
fun AutoDetectionIllustration(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(Res.drawable.illus_detection_3d),
        contentDescription = null,
        modifier = modifier.size(HERO_ILLUSTRATION_W, HERO_ILLUSTRATION_H),
        contentScale = ContentScale.Fit,
    )
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
        // Tyres with a white rim so they read on dark backgrounds (body is drawn on top,
        // so only the lower arc of the rim shows — like the map marker wheels).
        drawCircle(wheel, 6f, Offset(9f, 49f))
        drawCircle(wheel, 6f, Offset(47f, 49f))
        drawCircle(Color.White, 6f, Offset(9f, 49f), style = Stroke(WHEEL_RIM))
        drawCircle(Color.White, 6f, Offset(47f, 49f), style = Stroke(WHEEL_RIM))
        val bodyPath = Path().apply {
            moveTo(5f, 41f)
            cubicTo(5f, 35f, 9f, 30f, 14f, 30f)
            lineTo(22f, 30f)
            quadraticTo(24f, 18f, 30f, 13f)
            lineTo(40f, 13f)
            quadraticTo(46f, 18f, 49f, 30f)
            lineTo(51f, 30f)
            quadraticTo(56f, 30f, 56f, 36f)
            lineTo(56f, 47f)
            quadraticTo(56f, 47f, 53f, 47f)
            lineTo(8f, 47f)
            quadraticTo(5f, 47f, 5f, 41f)
            close()
        }
        drawPath(bodyPath, body)
        drawPath(bodyPath, Color.White, style = Stroke(3f, join = StrokeJoin.Round))
        val roofPath = Path().apply {
            moveTo(24f, 30f)
            quadraticTo(24f, 18f, 30f, 13f)
            lineTo(40f, 13f)
            quadraticTo(46f, 18f, 49f, 30f)
            close()
        }
        drawPath(roofPath, roof)
        drawCircle(Color.White, 2.4f, Offset(9f, 49f))
        drawCircle(Color.White, 2.4f, Offset(47f, 49f))
    }
}

/** Destello de cuatro puntas: el brillo de "esto es un momento". */
private fun sparkle(cx: Float, cy: Float, r: Float): Path = Path().apply {
    val w = r * 0.26f
    moveTo(cx, cy - r)
    quadraticTo(cx + w, cy - w, cx + r, cy)
    quadraticTo(cx + w, cy + w, cx, cy + r)
    quadraticTo(cx - w, cy + w, cx - r, cy)
    quadraticTo(cx - w, cy - w, cx, cy - r)
    close()
}

// region — el regalo axonométrico [UI-THE-FOUNDERS-GIFT-EARNS-ITS-VOLUME-001]

/**
 * Los dos ejes de la proyección, normalizados a semiancho 1: [AX_U] se va a la izquierda y hacia
 * abajo, [AX_V] a la derecha y hacia abajo. Cualquier punto del techo es `origen + a·U + b·V` con
 * a y b en [0,1], y cualquier cara lateral cuelga en VERTICAL de uno de sus cantos.
 *
 * Que la geometría entera salga de dos vectores es lo que hace que la caja no se pueda descuadrar:
 * no hay una sola coordenada escrita a mano que pueda contradecir a otra.
 */
private val AX_U = Offset(-1f, AX_RISE)
private val AX_V = Offset(1f, AX_RISE)

/** Cuánto baja un eje por cada unidad de ancho. Es la única cifra que decide "lo 3D" que se ve. */
private const val AX_RISE = 0.46f

/** La plaza: su esquina del fondo y su lado, en las mismas unidades de eje que la caja. */
private const val BAY_BACK_Y = 50f
private const val BAY_HALF = 56f

private const val LID_TOP_Y = 27f
private const val LID_HALF_W = 36f
private const val LID_THICK = 10f
/** Más estrecho que la tapa: por eso la tapa VUELA, y por eso se dibuja después del cuerpo. */
private const val BODY_HALF_W = 32.5f
private const val BODY_INSET_Y = 1.5f
private const val BODY_HEIGHT = 30f
/** La sombra se va hacia donde NO da la luz. Centrada bajo la caja delata un dibujo plano. */
private const val SHADOW_DX = 7f
private const val SHADOW_DY = 3f
private const val SHADOW_CAST_DARK_ALPHA = 0.46f
private const val SHADOW_CAST_LIGHT_ALPHA = 0.16f
/** La sombra de CONTACTO: casi sin desplazar y más densa — es la que dice que la caja toca. */
private const val CONTACT_DX = 2f
private const val CONTACT_DY = 1f
private const val CONTACT_ALPHA_FACTOR = 1.45f
/** Lo que oscurece el vuelo de la tapa sobre el cuerpo, y cuánto tarda en desvanecerse. */
private const val OCCLUSION_DARK_ALPHA = 0.5f
private const val OCCLUSION_LIGHT_ALPHA = 0.34f
private const val OCCLUSION_DROP = 13f
/** El lazo se levanta del techo: apoyado en el plano parecería impreso. */
private const val BOW_LIFT = 4f
private const val BOW_KNOT_R = 4.2f
/** Semiancho de la cinta, en fracción del lado que recorre — así mide igual en todas las caras. */
private const val RIBBON_HALF = 0.085f

private fun quad(a: Offset, b: Offset, c: Offset, d: Offset): Path = Path().apply {
    moveTo(a.x, a.y)
    lineTo(b.x, b.y)
    lineTo(c.x, c.y)
    lineTo(d.x, d.y)
    close()
}

private fun mix(a: Offset, b: Offset, t: Float): Offset =
    Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

/** La cara vertical que cuelga del canto [topA]–[topB]. */
private fun faceDown(topA: Offset, topB: Offset, depth: Float): Path {
    val down = Offset(0f, depth)
    return quad(topA, topB, topB + down, topA + down)
}

/** La franja central de esa misma cara: por donde baja la cinta. */
private fun bandDown(topA: Offset, topB: Offset, depth: Float): Path {
    val a = mix(topA, topB, 0.5f - RIBBON_HALF)
    val b = mix(topA, topB, 0.5f + RIBBON_HALF)
    val down = Offset(0f, depth)
    return quad(a, b, b + down, a + down)
}

/** Banda que cruza el techo de lado a lado: se sitúa a lo ancho de [along] y corre a lo largo de [across]. */
private fun topBand(origin: Offset, along: Offset, across: Offset): Path {
    val a = origin + along * (0.5f - RIBBON_HALF)
    val b = origin + along * (0.5f + RIBBON_HALF)
    return quad(a, b, b + across, a + across)
}

/**
 * Los cinco pinceles de la caja. Son CINCO y no tres porque la tapa recibe algo más de luz que el
 * cuerpo: un canto de tapa del mismo tono que la cara de debajo funde las dos piezas en una.
 *
 * El rango de cada degradado es corto a propósito, por la misma razón escrita en
 * `PapPremiumSurface`: la ESTRUCTURA es lo que lee como volumen, la AMPLITUD es lo que lee como
 * exagerado. Lo que separa las caras es el SALTO entre ellas, no la pendiente de cada una.
 */
private class GiftBoxFinish(
    val top: Brush,
    val left: Brush,
    val right: Brush,
    val lidLeft: Brush,
    val lidRight: Brush,
)

private fun giftBox(dark: Boolean): GiftBoxFinish {
    val topStart = Offset(36f, LID_TOP_Y)
    val topEnd = Offset(104f, LID_TOP_Y + LID_HALF_W * AX_RISE * 2f)
    val sideStart = Offset(0f, LID_TOP_Y + LID_HALF_W * AX_RISE)
    val sideEnd = Offset(0f, LID_TOP_Y + LID_THICK + BODY_INSET_Y + BODY_HEIGHT + 14f)
    return if (dark) {
        GiftBoxFinish(
            top = Brush.linearGradient(listOf(Color(0xFF3A4751), Color(0xFF2A343D)), topStart, topEnd),
            left = Brush.linearGradient(listOf(Color(0xFF1E2831), Color(0xFF151C23)), sideStart, sideEnd),
            right = Brush.linearGradient(listOf(Color(0xFF121920), Color(0xFF0B1015)), sideStart, sideEnd),
            lidLeft = Brush.linearGradient(listOf(Color(0xFF26323B), Color(0xFF1C252C)), sideStart, sideEnd),
            lidRight = Brush.linearGradient(listOf(Color(0xFF172027), Color(0xFF10161B)), sideStart, sideEnd),
        )
    } else {
        GiftBoxFinish(
            top = Brush.linearGradient(listOf(Color(0xFF42505B), Color(0xFF313C46)), topStart, topEnd),
            left = Brush.linearGradient(listOf(Color(0xFF27323B), Color(0xFF1C252C)), sideStart, sideEnd),
            right = Brush.linearGradient(listOf(Color(0xFF161E24), Color(0xFF0F151A)), sideStart, sideEnd),
            lidLeft = Brush.linearGradient(listOf(Color(0xFF2F3B45), Color(0xFF232D35)), sideStart, sideEnd),
            lidRight = Brush.linearGradient(listOf(Color(0xFF1B242B), Color(0xFF141B21)), sideStart, sideEnd),
        )
    }
}

/**
 * La cinta se oscurece cara a cara igual que la caja. Si llevara un solo verde plano, delataría que
 * las tres caras son el mismo dibujo girado — la luz tiene que tratar a la cinta y a la caja igual.
 */
private class GiftRibbon(val top: Brush, val left: Brush, val right: Brush, val knot: Color)

private fun giftRibbon(dark: Boolean): GiftRibbon {
    val topStart = Offset(36f, LID_TOP_Y)
    val topEnd = Offset(104f, LID_TOP_Y + LID_HALF_W * AX_RISE * 2f)
    val sideStart = Offset(0f, LID_TOP_Y + LID_HALF_W * AX_RISE)
    val sideEnd = Offset(0f, LID_TOP_Y + LID_THICK + BODY_INSET_Y + BODY_HEIGHT + 14f)
    return if (dark) {
        GiftRibbon(
            top = Brush.linearGradient(listOf(Color(0xFF4DFFAB), Color(0xFF25F48C)), topStart, topEnd),
            left = Brush.linearGradient(listOf(Color(0xFF25D97F), Color(0xFF1CB76B)), sideStart, sideEnd),
            right = Brush.linearGradient(listOf(Color(0xFF159A58), Color(0xFF0E7A46)), sideStart, sideEnd),
            knot = Color(0xFF2DF58E),
        )
    } else {
        GiftRibbon(
            top = Brush.linearGradient(listOf(Color(0xFF19C47A), Color(0xFF009F5E)), topStart, topEnd),
            left = Brush.linearGradient(listOf(Color(0xFF089059), Color(0xFF06794A)), sideStart, sideEnd),
            right = Brush.linearGradient(listOf(Color(0xFF05653E), Color(0xFF044F31)), sideStart, sideEnd),
            knot = Color(0xFF009F5E),
        )
    }
}

/**
 * Una lazada: ANCHA Y BAJA. Alta y estrecha no es un lazo, es un brote de planta en una maceta.
 *
 * La punta NO es redonda. Una lazada de cinta se dobla sobre sí misma, así que su extremo tiene
 * una esquina; redondeada entera, el lazo lee como el ala de una mariposa — que es exactamente lo
 * que pasó en la primera versión volumétrica.
 */
private fun bowLoop(knot: Offset, toLeft: Boolean): Path {
    val s = if (toLeft) -1f else 1f
    return Path().apply {
        moveTo(knot.x + s * 1.5f, knot.y - 1f)
        // Canto de arriba, hasta la punta.
        cubicTo(
            knot.x + s * 7f, knot.y - 10f,
            knot.x + s * 17f, knot.y - 11f,
            knot.x + s * 19.5f, knot.y - 5f,
        )
        // La esquina del doblez.
        lineTo(knot.x + s * 20f, knot.y + 1.5f)
        // Canto de abajo, de vuelta al nudo.
        cubicTo(
            knot.x + s * 16f, knot.y + 6.5f,
            knot.x + s * 8f, knot.y + 6f,
            knot.x + s * 3.5f, knot.y + 3.5f,
        )
        close()
    }
}

/**
 * El PLIEGUE donde la lazada nace del nudo: una cuña oscura, cóncava hacia fuera.
 *
 * Es la pieza que convierte dos manchas en un lazo. Una cinta que sale de un nudo se estrangula
 * ahí y se mete en sombra; sin esa cuña, las lazadas parecen pegadas al nudo por fuera.
 */
private fun bowCrease(knot: Offset, toLeft: Boolean): Path {
    val s = if (toLeft) -1f else 1f
    return Path().apply {
        moveTo(knot.x + s * 1f, knot.y - 2.5f)
        cubicTo(
            knot.x + s * 5f, knot.y - 2f,
            knot.x + s * 7.5f, knot.y + 0.5f,
            knot.x + s * 8f, knot.y + 3.5f,
        )
        cubicTo(
            knot.x + s * 5.5f, knot.y + 2.5f,
            knot.x + s * 3f, knot.y + 1.5f,
            knot.x + s * 1f, knot.y + 2.5f,
        )
        close()
    }
}

/**
 * La cola que cae del nudo sobre el techo, con su corte en V. Sin colas el lazo es una pegatina;
 * sin la V, las colas son dos churros.
 */
private fun bowTail(knot: Offset, toLeft: Boolean): Path {
    val s = if (toLeft) -1f else 1f
    return Path().apply {
        moveTo(knot.x, knot.y + 1f)
        cubicTo(
            knot.x + s * 6f, knot.y + 8f,
            knot.x + s * 11f, knot.y + 13f,
            knot.x + s * 15f, knot.y + 16.5f,
        )
        lineTo(knot.x + s * 11.5f, knot.y + 18.5f)
        lineTo(knot.x + s * 10f, knot.y + 15f)
        lineTo(knot.x + s * 7.5f, knot.y + 18f)
        cubicTo(
            knot.x + s * 6f, knot.y + 13f,
            knot.x + s * 3f, knot.y + 8f,
            knot.x, knot.y + 4f,
        )
        close()
    }
}

/**
 * Los tonos del lazo, anclados al NUDO: cada lazada se aclara hacia su punta y se oscurece al
 * nacer. Un verde plano delata que las dos lazadas son el mismo dibujo espejado; dos tonos que
 * se alejan del nudo dicen que la cinta se curva.
 */
private class BowFinish(
    val loopLeft: Brush,
    val loopRight: Brush,
    val crease: Color,
    val tail: Brush,
    val knotShade: Color,
)

private fun bowFinish(dark: Boolean, knot: Offset): BowFinish {
    val tip = 21f
    val bright = if (dark) Color(0xFF6BFFBE) else Color(0xFF34D893)
    val mid = if (dark) Color(0xFF25F48C) else Color(0xFF009F5E)
    val deep = if (dark) Color(0xFF12B46A) else Color(0xFF06794A)
    return BowFinish(
        loopLeft = Brush.linearGradient(
            listOf(bright, deep),
            start = Offset(knot.x - tip, knot.y - 6f),
            end = Offset(knot.x, knot.y + 4f),
        ),
        loopRight = Brush.linearGradient(
            listOf(bright, deep),
            start = Offset(knot.x + tip, knot.y - 6f),
            end = Offset(knot.x, knot.y + 4f),
        ),
        crease = if (dark) Color(0xFF0C8A52) else Color(0xFF04603C),
        tail = Brush.linearGradient(
            listOf(mid, deep),
            start = Offset(knot.x, knot.y),
            end = Offset(knot.x, knot.y + 20f),
        ),
        knotShade = if (dark) Color(0xFF0C8A52) else Color(0xFF04603C),
    )
}

// endregion

private fun locationPin(): Path = Path().apply {
    moveTo(64f, 23f)
    cubicTo(73.5f, 23f, 81f, 30.5f, 81f, 40f)
    cubicTo(81f, 52f, 64f, 73f, 64f, 73f)
    cubicTo(64f, 73f, 47f, 52f, 47f, 40f)
    cubicTo(47f, 30.5f, 54.5f, 23f, 64f, 23f)
    close()
}

// endregion
