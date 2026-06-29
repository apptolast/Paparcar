package io.apptolast.paparcar.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.CarbodyType

/**
 * Vector geometry of the vehicle pictograms — the single source of truth for every car render
 * (side-profile + top-down), so a chosen [VehicleColor] can recolour the body at runtime by
 * rebuilding the [ImageVector] with a [CarPalette] instead of shipping one drawable per
 * (shape × colour). Colours are stored as the original opaque ARGB longs so `recolor` can swap
 * only the body-family ones; the default brand-green renders via the identity [defaultCarPalette].
 * The old per-shape `ic_car_*` drawables were retired once everything routed through here.
 * [VEH-COLOR-001] [CAR-WHITE-BORDER-001]
 */
internal class CarPath(
    val data: String,
    val fill: Long? = null,
    val fillAlpha: Float = 1f,
    val stroke: Long? = null,
    val strokeWidth: Float = 0f,
    val strokeAlpha: Float = 1f,
    val round: Boolean = false,
)

internal class CarSpec(
    val viewportWidth: Float,
    val viewportHeight: Float,
    val paths: List<CarPath>,
)

// Source colours baked into the artwork (opaque ARGB). Body-family ones (BODY/BODY2/HI/LO)
// are remapped by `recolor`; the rest are theme-neutral and pass through unchanged.
private const val BODY = 0xFF00794A   // side-profile body
private const val BODY2 = 0xFF009F5E  // top-down body / mirrors / heading wedge
private const val HI = 0xFF23C47D     // roof highlight / window frame
private const val LO = 0xFF005E39     // side crease / cargo divider
private const val GLASS = 0xFFCFE9F6
private const val TIRE = 0xFF15281D
private const val HUB = 0xFF5C7A6B
private const val HEAD = 0xFFEAFBF2
private const val SHADOW = 0xFF0E1A2E
private const val WHITE = 0xFFFFFFFF

private fun p(
    data: String,
    fill: Long? = null,
    fa: Float = 1f,
    stroke: Long? = null,
    sw: Float = 0f,
    sa: Float = 1f,
    round: Boolean = false,
) = CarPath(data, fill, fa, stroke, sw, sa, round)

// ── Side-profile (isometric) specs ─────────────────────────────────────────────

private val ISO: Map<CarbodyType, CarSpec> = mapOf(
    CarbodyType.HATCHBACK_SMALL to CarSpec(74f, 62f, listOf(
        p("M5,57 a30,3.8 0 1,0 60,0 a30,3.8 0 1,0 -60,0 Z", fill = SHADOW, fa = 0.22f),
        p("M15,50 a8,8 0 1,0 16,0 a8,8 0 1,0 -16,0 Z", fill = TIRE),
        p("M42,50 a8,8 0 1,0 16,0 a8,8 0 1,0 -16,0 Z", fill = TIRE),
        p("M8 43 C8 38 11 32 16 32 L24 32 Q25 21.6 31 16 L44 16 Q47 20.8 53 32 L57 32 Q62 32 62 37 L62 49 Q62 49 59 49 L11 49 Q8 49 8 43 Z", fill = BODY, stroke = WHITE, sw = 3f, round = true),
        p("M25 32 Q25 21.6 31 16 L44 16 Q47 20.8 53 32 Z", fill = HI),
        p("M26.5 30.5 Q26 20.5 31.5 18.5 L39.6 18.5 L50 30.5 Z", fill = GLASS),
        p("M37.76 19 L39.26 30.5", stroke = HI, sw = 1.6f, sa = 0.8f),
        p("M14 42 L58 42", stroke = LO, sw = 1.4f, sa = 0.55f),
        p("M9.5 36 Q9 40 13 40 L14 36 Z", fill = HEAD),
        p("M19.8,50 a3.2,3.2 0 1,0 6.4,0 a3.2,3.2 0 1,0 -6.4,0 Z", fill = HUB),
        p("M46.8,50 a3.2,3.2 0 1,0 6.4,0 a3.2,3.2 0 1,0 -6.4,0 Z", fill = HUB),
    )),
    CarbodyType.SUV_SMALL to CarSpec(76f, 62f, listOf(
        p("M5,57 a31,3.8 0 1,0 62,0 a31,3.8 0 1,0 -62,0 Z", fill = SHADOW, fa = 0.22f),
        p("M15,50 a8,8 0 1,0 16,0 a8,8 0 1,0 -16,0 Z", fill = TIRE),
        p("M44,50 a8,8 0 1,0 16,0 a8,8 0 1,0 -16,0 Z", fill = TIRE),
        p("M8 43 C8 37 11 31 16 31 L23 31 Q24 19.95 29 14 L47 14 Q51 19.1 55 31 L59 31 Q64 31 64 36 L64 49 Q64 49 61 49 L11 49 Q8 49 8 43 Z", fill = BODY, stroke = WHITE, sw = 3f, round = true),
        p("M24 31 Q24 19.95 29 14 L47 14 Q51 19.1 55 31 Z", fill = HI),
        p("M25.5 29.5 Q25 18.5 29.5 16.5 L41.1 16.5 L52 29.5 Z", fill = GLASS),
        p("M38.36 17 L39.86 29.5", stroke = HI, sw = 1.6f, sa = 0.8f),
        p("M14 41 L60 41", stroke = LO, sw = 1.4f, sa = 0.55f),
        p("M9.5 35 Q9 39 13 39 L14 35 Z", fill = HEAD),
        p("M19.8,50 a3.2,3.2 0 1,0 6.4,0 a3.2,3.2 0 1,0 -6.4,0 Z", fill = HUB),
        p("M48.8,50 a3.2,3.2 0 1,0 6.4,0 a3.2,3.2 0 1,0 -6.4,0 Z", fill = HUB),
    )),
    CarbodyType.HATCHBACK_MEDIUM to CarSpec(84f, 62f, listOf(
        p("M5,57 a35,3.8 0 1,0 70,0 a35,3.8 0 1,0 -70,0 Z", fill = SHADOW, fa = 0.22f),
        p("M17,50 a8,8 0 1,0 16,0 a8,8 0 1,0 -16,0 Z", fill = TIRE),
        p("M50,50 a8,8 0 1,0 16,0 a8,8 0 1,0 -16,0 Z", fill = TIRE),
        p("M8 43 C8 38 11 32 16 32 L26 32 Q27 21.6 34 16 L52 16 Q56 20.8 62 32 L67 32 Q72 32 72 37 L72 49 Q72 49 69 49 L11 49 Q8 49 8 43 Z", fill = BODY, stroke = WHITE, sw = 3f, round = true),
        p("M27 32 Q27 21.6 34 16 L52 16 Q56 20.8 62 32 Z", fill = HI),
        p("M28.5 30.5 Q28 20.5 34.5 18.5 L46.1 18.5 L59 30.5 Z", fill = GLASS),
        p("M43.36 19 L44.86 30.5", stroke = HI, sw = 1.6f, sa = 0.8f),
        p("M14 42 L68 42", stroke = LO, sw = 1.4f, sa = 0.55f),
        p("M9.5 36 Q9 40 13 40 L14 36 Z", fill = HEAD),
        p("M21.8,50 a3.2,3.2 0 1,0 6.4,0 a3.2,3.2 0 1,0 -6.4,0 Z", fill = HUB),
        p("M54.8,50 a3.2,3.2 0 1,0 6.4,0 a3.2,3.2 0 1,0 -6.4,0 Z", fill = HUB),
    )),
    CarbodyType.SUV_MEDIUM to CarSpec(88f, 62f, listOf(
        p("M5,57 a37,3.8 0 1,0 74,0 a37,3.8 0 1,0 -74,0 Z", fill = SHADOW, fa = 0.22f),
        p("M18,50 a8,8 0 1,0 16,0 a8,8 0 1,0 -16,0 Z", fill = TIRE),
        p("M54,50 a8,8 0 1,0 16,0 a8,8 0 1,0 -16,0 Z", fill = TIRE),
        p("M8 43 C8 37 11 31 16 31 L25 31 Q26 19.3 32 13 L58 13 Q63 18.4 67 31 L71 31 Q76 31 76 36 L76 49 Q76 49 73 49 L11 49 Q8 49 8 43 Z", fill = BODY, stroke = WHITE, sw = 3f, round = true),
        p("M26 31 Q26 19.3 32 13 L58 13 Q63 18.4 67 31 Z", fill = HI),
        p("M27.5 29.5 Q27 17.5 32.5 15.5 L49.7 15.5 L64 29.5 Z", fill = GLASS),
        p("M45.519999999999996 16 L47.019999999999996 29.5", stroke = HI, sw = 1.6f, sa = 0.8f),
        p("M14 41 L72 41", stroke = LO, sw = 1.4f, sa = 0.55f),
        p("M9.5 35 Q9 39 13 39 L14 35 Z", fill = HEAD),
        p("M22.8,50 a3.2,3.2 0 1,0 6.4,0 a3.2,3.2 0 1,0 -6.4,0 Z", fill = HUB),
        p("M58.8,50 a3.2,3.2 0 1,0 6.4,0 a3.2,3.2 0 1,0 -6.4,0 Z", fill = HUB),
    )),
    CarbodyType.SEDAN to CarSpec(92f, 62f, listOf(
        p("M5,57 a39,3.8 0 1,0 78,0 a39,3.8 0 1,0 -78,0 Z", fill = SHADOW, fa = 0.22f),
        p("M19,50 a8,8 0 1,0 16,0 a8,8 0 1,0 -16,0 Z", fill = TIRE),
        p("M57,50 a8,8 0 1,0 16,0 a8,8 0 1,0 -16,0 Z", fill = TIRE),
        p("M8 43 C8 39 11 33 16 33 L30 33 Q31 22.6 38 17 L55 17 Q59 23.4 60 33 L75 33 Q80 33 80 38 L80 49 Q80 49 77 49 L11 49 Q8 49 8 43 Z", fill = BODY, stroke = WHITE, sw = 3f, round = true),
        p("M31 33 Q31 22.6 38 17 L55 17 Q59 23.4 60 33 Z", fill = HI),
        p("M32.5 31.5 Q32 21.5 38.5 19.5 L49.4 19.5 L58 31.5 Z", fill = GLASS),
        p("M46.84 20 L48.34 31.5", stroke = HI, sw = 1.6f, sa = 0.8f),
        p("M14 43 L76 43", stroke = LO, sw = 1.4f, sa = 0.55f),
        p("M9.5 37 Q9 41 13 41 L14 37 Z", fill = HEAD),
        p("M23.8,50 a3.2,3.2 0 1,0 6.4,0 a3.2,3.2 0 1,0 -6.4,0 Z", fill = HUB),
        p("M61.8,50 a3.2,3.2 0 1,0 6.4,0 a3.2,3.2 0 1,0 -6.4,0 Z", fill = HUB),
    )),
    CarbodyType.FAMILY_LONG to CarSpec(94f, 62f, listOf(
        p("M5,57 a40,3.8 0 1,0 80,0 a40,3.8 0 1,0 -80,0 Z", fill = SHADOW, fa = 0.22f),
        p("M19,50 a8,8 0 1,0 16,0 a8,8 0 1,0 -16,0 Z", fill = TIRE),
        p("M59,50 a8,8 0 1,0 16,0 a8,8 0 1,0 -16,0 Z", fill = TIRE),
        p("M8 43 C8 38 11 32 16 32 L27 32 Q28 21.6 35 16 L70 16 Q74 20.8 79 32 L77 32 Q82 32 82 37 L82 49 Q82 49 79 49 L11 49 Q8 49 8 43 Z", fill = BODY, stroke = WHITE, sw = 3f, round = true),
        p("M28 32 Q28 21.6 35 16 L70 16 Q74 20.8 79 32 Z", fill = HI),
        p("M29.5 30.5 Q29 20.5 35.5 18.5 L59 18.5 L76 30.5 Z", fill = GLASS),
        p("M53.2 19 L54.7 30.5", stroke = HI, sw = 1.6f, sa = 0.8f),
        p("M14 42 L78 42", stroke = LO, sw = 1.4f, sa = 0.55f),
        p("M9.5 36 Q9 40 13 40 L14 36 Z", fill = HEAD),
        p("M23.8,50 a3.2,3.2 0 1,0 6.4,0 a3.2,3.2 0 1,0 -6.4,0 Z", fill = HUB),
        p("M63.8,50 a3.2,3.2 0 1,0 6.4,0 a3.2,3.2 0 1,0 -6.4,0 Z", fill = HUB),
    )),
    CarbodyType.SUV_LARGE to CarSpec(96f, 62f, listOf(
        p("M5,57 a41,3.8 0 1,0 82,0 a41,3.8 0 1,0 -82,0 Z", fill = SHADOW, fa = 0.22f),
        p("M20,50 a8,8 0 1,0 16,0 a8,8 0 1,0 -16,0 Z", fill = TIRE),
        p("M62,50 a8,8 0 1,0 16,0 a8,8 0 1,0 -16,0 Z", fill = TIRE),
        p("M8 43 C8 37 11 31 16 31 L26 31 Q27 18.65 33 12 L70 12 Q75 17.7 79 31 L79 31 Q84 31 84 36 L84 49 Q84 49 81 49 L11 49 Q8 49 8 43 Z", fill = BODY, stroke = WHITE, sw = 3f, round = true),
        p("M27 31 Q27 18.65 33 12 L70 12 Q75 17.7 79 31 Z", fill = HI),
        p("M28.5 29.5 Q28 16.5 33.5 14.5 L58.4 14.5 L76 29.5 Z", fill = GLASS),
        p("M52.24 15 L53.74 29.5", stroke = HI, sw = 1.6f, sa = 0.8f),
        p("M14 41 L80 41", stroke = LO, sw = 1.4f, sa = 0.55f),
        p("M9.5 35 Q9 39 13 39 L14 35 Z", fill = HEAD),
        p("M24.8,50 a3.2,3.2 0 1,0 6.4,0 a3.2,3.2 0 1,0 -6.4,0 Z", fill = HUB),
        p("M66.8,50 a3.2,3.2 0 1,0 6.4,0 a3.2,3.2 0 1,0 -6.4,0 Z", fill = HUB),
    )),
    CarbodyType.VAN_LIGHT to CarSpec(92f, 62f, listOf(
        p("M5,57 a39,3.8 0 1,0 78,0 a39,3.8 0 1,0 -78,0 Z", fill = SHADOW, fa = 0.22f),
        p("M17,50 a8,8 0 1,0 16,0 a8,8 0 1,0 -16,0 Z", fill = TIRE),
        p("M59,50 a8,8 0 1,0 16,0 a8,8 0 1,0 -16,0 Z", fill = TIRE),
        p("M8 43 C8 37 11 31 16 31 L24 31 Q25 18 30 11 L72 11 L77 11 Q80 11 80 16 L80 35 L80 49 Q80 49 77 49 L11 49 Q8 49 8 43 Z", fill = BODY, stroke = WHITE, sw = 3f, round = true),
        p("M26 31 L25.5 11.5 Q25 11 31 10.5 L76 10.5 Q79 11 79 15 L79 31 Z", fill = HI),
        p("M26.5 29.5 Q26 15.5 30.5 13.5 L58.9 13.5 L76 29.5 Z", fill = GLASS),
        p("M51.84 14 L53.34 29.5", stroke = HI, sw = 1.6f, sa = 0.8f),
        p("M14 41 L76 41", stroke = LO, sw = 1.4f, sa = 0.55f),
        p("M9.5 35 Q9 39 13 39 L14 35 Z", fill = HEAD),
        p("M21.8,50 a3.2,3.2 0 1,0 6.4,0 a3.2,3.2 0 1,0 -6.4,0 Z", fill = HUB),
        p("M63.8,50 a3.2,3.2 0 1,0 6.4,0 a3.2,3.2 0 1,0 -6.4,0 Z", fill = HUB),
    )),
    CarbodyType.VAN_COMMERCIAL to CarSpec(92f, 62f, listOf(
        p("M5,57 a39,3.8 0 1,0 78,0 a39,3.8 0 1,0 -78,0 Z", fill = SHADOW, fa = 0.22f),
        p("M18,50 a8,8 0 1,0 16,0 a8,8 0 1,0 -16,0 Z", fill = TIRE),
        p("M58,50 a8,8 0 1,0 16,0 a8,8 0 1,0 -16,0 Z", fill = TIRE),
        p("M8 43 C8 38 11 32 16 32 L26 32 Q27 21.6 33 16 L48 16 Q51 22.4 52 32 L54 36 L76 36 Q80 36 80 40 L80 49 Q80 49 77 49 L11 49 Q8 49 8 43 Z", fill = BODY, stroke = WHITE, sw = 3f, round = true),
        p("M27 32 Q27 21.6 33 16 L48 16 Q51 22.4 52 32 Z", fill = HI),
        p("M55 37.5 L75 37.5 L75 35 L55 35 Z", fill = LO, fa = 0.5f),
        p("M28.5 30.5 Q28 20.5 33.5 18.5 L40.75 18.5 L50 30.5 Z", fill = GLASS),
        p("M40.8 19 L42.3 30.5", stroke = HI, sw = 1.6f, sa = 0.8f),
        p("M14 42 L76 42", stroke = LO, sw = 1.4f, sa = 0.55f),
        p("M9.5 36 Q9 40 13 40 L14 36 Z", fill = HEAD),
        p("M22.8,50 a3.2,3.2 0 1,0 6.4,0 a3.2,3.2 0 1,0 -6.4,0 Z", fill = HUB),
        p("M62.8,50 a3.2,3.2 0 1,0 6.4,0 a3.2,3.2 0 1,0 -6.4,0 Z", fill = HUB),
    )),
    CarbodyType.PICKUP to CarSpec(100f, 62f, listOf(
        p("M5,57 a43,3.8 0 1,0 86,0 a43,3.8 0 1,0 -86,0 Z", fill = SHADOW, fa = 0.22f),
        p("M19,50 a8,8 0 1,0 16,0 a8,8 0 1,0 -16,0 Z", fill = TIRE),
        p("M66,50 a8,8 0 1,0 16,0 a8,8 0 1,0 -16,0 Z", fill = TIRE),
        p("M8 43 C8 37 11 31 16 31 L24 31 L25.5 9 L80 9 L85 9 Q88 9 88 14 L88 35 L88 49 Q88 49 85 49 L11 49 Q8 49 8 43 Z", fill = BODY, stroke = WHITE, sw = 3f, round = true),
        p("M26 31 L25.5 9.5 Q25 9 29 8.5 L84 8.5 Q87 9 87 13 L87 31 Z", fill = HI),
        p("M26.5 29.5 Q26 13.5 27 11.5 L61.9 11.5 L84 29.5 Z", fill = GLASS),
        p("M55.04 12 L56.54 29.5", stroke = HI, sw = 1.6f, sa = 0.8f),
        p("M14 41 L84 41", stroke = LO, sw = 1.4f, sa = 0.55f),
        p("M9.5 35 Q9 39 13 39 L14 35 Z", fill = HEAD),
        p("M23.8,50 a3.2,3.2 0 1,0 6.4,0 a3.2,3.2 0 1,0 -6.4,0 Z", fill = HUB),
        p("M70.8,50 a3.2,3.2 0 1,0 6.4,0 a3.2,3.2 0 1,0 -6.4,0 Z", fill = HUB),
    )),
)

// ── Top-down (aerial) specs ─────────────────────────────────────────────────────

private val TOPDOWN: Map<CarbodyType, CarSpec> = mapOf(
    CarbodyType.HATCHBACK_SMALL to CarSpec(56f, 54f, listOf(
        p("M15,52 a13,2.6 0 1,0 26,0 a13,2.6 0 1,0 -26,0 Z", fill = SHADOW, fa = 0.15f),
        p("M28 0 L41 10 L28 4.5 L15 10 Z", fill = BODY2, fa = 0.22f),
        p("M15.6,10.4 h0.8 a2.6,2.6 0 0 1 2.6,2.6 v3.8 a2.6,2.6 0 0 1 -2.6,2.6 h-0.8 a2.6,2.6 0 0 1 -2.6,-2.6 v-3.8 a2.6,2.6 0 0 1 2.6,-2.6 Z", fill = TIRE),
        p("M39.6,10.4 h0.8 a2.6,2.6 0 0 1 2.6,2.6 v3.8 a2.6,2.6 0 0 1 -2.6,2.6 h-0.8 a2.6,2.6 0 0 1 -2.6,-2.6 v-3.8 a2.6,2.6 0 0 1 2.6,-2.6 Z", fill = TIRE),
        p("M15.6,34.6 h0.8 a2.6,2.6 0 0 1 2.6,2.6 v3.8 a2.6,2.6 0 0 1 -2.6,2.6 h-0.8 a2.6,2.6 0 0 1 -2.6,-2.6 v-3.8 a2.6,2.6 0 0 1 2.6,-2.6 Z", fill = TIRE),
        p("M39.6,34.6 h0.8 a2.6,2.6 0 0 1 2.6,2.6 v3.8 a2.6,2.6 0 0 1 -2.6,2.6 h-0.8 a2.6,2.6 0 0 1 -2.6,-2.6 v-3.8 a2.6,2.6 0 0 1 2.6,-2.6 Z", fill = TIRE),
        p("M26,4 h4 a11,11 0 0 1 11,11 v24 a11,11 0 0 1 -11,11 h-4 a11,11 0 0 1 -11,-11 v-24 a11,11 0 0 1 11,-11 Z", fill = BODY2, stroke = WHITE, sw = 3f),
        p("M13.2,17.8 h0.8 a1.6,1.6 0 0 1 1.6,1.6 v1.3 a1.6,1.6 0 0 1 -1.6,1.6 h-0.8 a1.6,1.6 0 0 1 -1.6,-1.6 v-1.3 a1.6,1.6 0 0 1 1.6,-1.6 Z", fill = BODY2, stroke = WHITE, sw = 1.1f),
        p("M42,17.8 h0.8 a1.6,1.6 0 0 1 1.6,1.6 v1.3 a1.6,1.6 0 0 1 -1.6,1.6 h-0.8 a1.6,1.6 0 0 1 -1.6,-1.6 v-1.3 a1.6,1.6 0 0 1 1.6,-1.6 Z", fill = BODY2, stroke = WHITE, sw = 1.1f),
        p("M25.5,17.8 h5 a6,6 0 0 1 6,6 v2.7 a6,6 0 0 1 -6,6 h-5 a6,6 0 0 1 -6,-6 v-2.7 a6,6 0 0 1 6,-6 Z", fill = HI),
        p("M20.5 19.8 Q28 15.8 35.5 19.8 L34.5 24.8 L21.5 24.8 Z", fill = GLASS),
        p("M21.5 25.5 L34.5 25.5 L35.5 30.5 Q28 34.5 20.5 30.5 Z", fill = GLASS, fa = 0.9f),
    )),
    CarbodyType.SUV_SMALL to CarSpec(56f, 54f, listOf(
        p("M13,52 a15,2.6 0 1,0 30,0 a15,2.6 0 1,0 -30,0 Z", fill = SHADOW, fa = 0.15f),
        p("M28 0 L41 10 L28 4.5 L15 10 Z", fill = BODY2, fa = 0.22f),
        p("M13.6,10.4 h0.8 a2.6,2.6 0 0 1 2.6,2.6 v3.8 a2.6,2.6 0 0 1 -2.6,2.6 h-0.8 a2.6,2.6 0 0 1 -2.6,-2.6 v-3.8 a2.6,2.6 0 0 1 2.6,-2.6 Z", fill = TIRE),
        p("M41.6,10.4 h0.8 a2.6,2.6 0 0 1 2.6,2.6 v3.8 a2.6,2.6 0 0 1 -2.6,2.6 h-0.8 a2.6,2.6 0 0 1 -2.6,-2.6 v-3.8 a2.6,2.6 0 0 1 2.6,-2.6 Z", fill = TIRE),
        p("M13.6,34.6 h0.8 a2.6,2.6 0 0 1 2.6,2.6 v3.8 a2.6,2.6 0 0 1 -2.6,2.6 h-0.8 a2.6,2.6 0 0 1 -2.6,-2.6 v-3.8 a2.6,2.6 0 0 1 2.6,-2.6 Z", fill = TIRE),
        p("M41.6,34.6 h0.8 a2.6,2.6 0 0 1 2.6,2.6 v3.8 a2.6,2.6 0 0 1 -2.6,2.6 h-0.8 a2.6,2.6 0 0 1 -2.6,-2.6 v-3.8 a2.6,2.6 0 0 1 2.6,-2.6 Z", fill = TIRE),
        p("M24,4 h8 a11,11 0 0 1 11,11 v24 a11,11 0 0 1 -11,11 h-8 a11,11 0 0 1 -11,-11 v-24 a11,11 0 0 1 11,-11 Z", fill = BODY2, stroke = WHITE, sw = 3f),
        p("M11.2,17.8 h0.8 a1.6,1.6 0 0 1 1.6,1.6 v1.3 a1.6,1.6 0 0 1 -1.6,1.6 h-0.8 a1.6,1.6 0 0 1 -1.6,-1.6 v-1.3 a1.6,1.6 0 0 1 1.6,-1.6 Z", fill = BODY2, stroke = WHITE, sw = 1.1f),
        p("M44,17.8 h0.8 a1.6,1.6 0 0 1 1.6,1.6 v1.3 a1.6,1.6 0 0 1 -1.6,1.6 h-0.8 a1.6,1.6 0 0 1 -1.6,-1.6 v-1.3 a1.6,1.6 0 0 1 1.6,-1.6 Z", fill = BODY2, stroke = WHITE, sw = 1.1f),
        p("M23.5,16 h9 a6,6 0 0 1 6,6 v6.4 a6,6 0 0 1 -6,6 h-9 a6,6 0 0 1 -6,-6 v-6.4 a6,6 0 0 1 6,-6 Z", fill = HI),
        p("M18.5 18.0 Q28 14.0 37.5 18.0 L36.5 23.0 L19.5 23.0 Z", fill = GLASS),
        p("M19.5 27.4 L36.5 27.4 L37.5 32.4 Q28 36.4 18.5 32.4 Z", fill = GLASS, fa = 0.9f),
    )),
    CarbodyType.HATCHBACK_MEDIUM to CarSpec(56f, 62f, listOf(
        p("M14.5,60 a13.5,2.6 0 1,0 27,0 a13.5,2.6 0 1,0 -27,0 Z", fill = SHADOW, fa = 0.15f),
        p("M28 0 L41 10 L28 4.5 L15 10 Z", fill = BODY2, fa = 0.22f),
        p("M15.1,11.6 h0.8 a2.6,2.6 0 0 1 2.6,2.6 v3.8 a2.6,2.6 0 0 1 -2.6,2.6 h-0.8 a2.6,2.6 0 0 1 -2.6,-2.6 v-3.8 a2.6,2.6 0 0 1 2.6,-2.6 Z", fill = TIRE),
        p("M40.1,11.6 h0.8 a2.6,2.6 0 0 1 2.6,2.6 v3.8 a2.6,2.6 0 0 1 -2.6,2.6 h-0.8 a2.6,2.6 0 0 1 -2.6,-2.6 v-3.8 a2.6,2.6 0 0 1 2.6,-2.6 Z", fill = TIRE),
        p("M15.1,41.4 h0.8 a2.6,2.6 0 0 1 2.6,2.6 v3.8 a2.6,2.6 0 0 1 -2.6,2.6 h-0.8 a2.6,2.6 0 0 1 -2.6,-2.6 v-3.8 a2.6,2.6 0 0 1 2.6,-2.6 Z", fill = TIRE),
        p("M40.1,41.4 h0.8 a2.6,2.6 0 0 1 2.6,2.6 v3.8 a2.6,2.6 0 0 1 -2.6,2.6 h-0.8 a2.6,2.6 0 0 1 -2.6,-2.6 v-3.8 a2.6,2.6 0 0 1 2.6,-2.6 Z", fill = TIRE),
        p("M25.5,4 h5 a11,11 0 0 1 11,11 v32 a11,11 0 0 1 -11,11 h-5 a11,11 0 0 1 -11,-11 v-32 a11,11 0 0 1 11,-11 Z", fill = BODY2, stroke = WHITE, sw = 3f),
        p("M12.7,20.2 h0.8 a1.6,1.6 0 0 1 1.6,1.6 v1.3 a1.6,1.6 0 0 1 -1.6,1.6 h-0.8 a1.6,1.6 0 0 1 -1.6,-1.6 v-1.3 a1.6,1.6 0 0 1 1.6,-1.6 Z", fill = BODY2, stroke = WHITE, sw = 1.1f),
        p("M42.5,20.2 h0.8 a1.6,1.6 0 0 1 1.6,1.6 v1.3 a1.6,1.6 0 0 1 -1.6,1.6 h-0.8 a1.6,1.6 0 0 1 -1.6,-1.6 v-1.3 a1.6,1.6 0 0 1 1.6,-1.6 Z", fill = BODY2, stroke = WHITE, sw = 1.1f),
        p("M25,20.2 h6 a6,6 0 0 1 6,6 v6.4 a6,6 0 0 1 -6,6 h-6 a6,6 0 0 1 -6,-6 v-6.4 a6,6 0 0 1 6,-6 Z", fill = HI),
        p("M20 22.2 Q28 18.2 36 22.2 L35 27.2 L21 27.2 Z", fill = GLASS),
        p("M21 31.6 L35 31.6 L36 36.6 Q28 40.6 20 36.6 Z", fill = GLASS, fa = 0.9f),
    )),
    CarbodyType.SUV_MEDIUM to CarSpec(56f, 64f, listOf(
        p("M12.5,62 a15.5,2.6 0 1,0 31,0 a15.5,2.6 0 1,0 -31,0 Z", fill = SHADOW, fa = 0.15f),
        p("M28 0 L41 10 L28 4.5 L15 10 Z", fill = BODY2, fa = 0.22f),
        p("M13.1,11.8 h0.8 a2.6,2.6 0 0 1 2.6,2.6 v3.8 a2.6,2.6 0 0 1 -2.6,2.6 h-0.8 a2.6,2.6 0 0 1 -2.6,-2.6 v-3.8 a2.6,2.6 0 0 1 2.6,-2.6 Z", fill = TIRE),
        p("M42.1,11.8 h0.8 a2.6,2.6 0 0 1 2.6,2.6 v3.8 a2.6,2.6 0 0 1 -2.6,2.6 h-0.8 a2.6,2.6 0 0 1 -2.6,-2.6 v-3.8 a2.6,2.6 0 0 1 2.6,-2.6 Z", fill = TIRE),
        p("M13.1,43.2 h0.8 a2.6,2.6 0 0 1 2.6,2.6 v3.8 a2.6,2.6 0 0 1 -2.6,2.6 h-0.8 a2.6,2.6 0 0 1 -2.6,-2.6 v-3.8 a2.6,2.6 0 0 1 2.6,-2.6 Z", fill = TIRE),
        p("M42.1,43.2 h0.8 a2.6,2.6 0 0 1 2.6,2.6 v3.8 a2.6,2.6 0 0 1 -2.6,2.6 h-0.8 a2.6,2.6 0 0 1 -2.6,-2.6 v-3.8 a2.6,2.6 0 0 1 2.6,-2.6 Z", fill = TIRE),
        p("M23.5,4 h9 a11,11 0 0 1 11,11 v34 a11,11 0 0 1 -11,11 h-9 a11,11 0 0 1 -11,-11 v-34 a11,11 0 0 1 11,-11 Z", fill = BODY2, stroke = WHITE, sw = 3f),
        p("M10.7,20.8 h0.8 a1.6,1.6 0 0 1 1.6,1.6 v1.3 a1.6,1.6 0 0 1 -1.6,1.6 h-0.8 a1.6,1.6 0 0 1 -1.6,-1.6 v-1.3 a1.6,1.6 0 0 1 1.6,-1.6 Z", fill = BODY2, stroke = WHITE, sw = 1.1f),
        p("M44.5,20.8 h0.8 a1.6,1.6 0 0 1 1.6,1.6 v1.3 a1.6,1.6 0 0 1 -1.6,1.6 h-0.8 a1.6,1.6 0 0 1 -1.6,-1.6 v-1.3 a1.6,1.6 0 0 1 1.6,-1.6 Z", fill = BODY2, stroke = WHITE, sw = 1.1f),
        p("M23,18.6 h10 a6,6 0 0 1 6,6 v11.5 a6,6 0 0 1 -6,6 h-10 a6,6 0 0 1 -6,-6 v-11.5 a6,6 0 0 1 6,-6 Z", fill = HI),
        p("M18 20.6 Q28 16.6 38 20.6 L37 25.6 L19 25.6 Z", fill = GLASS),
        p("M19 35.1 L37 35.1 L38 40.1 Q28 44.1 18 40.1 Z", fill = GLASS, fa = 0.9f),
    )),
    CarbodyType.SEDAN to CarSpec(56f, 66f, listOf(
        p("M14.5,64 a13.5,2.6 0 1,0 27,0 a13.5,2.6 0 1,0 -27,0 Z", fill = SHADOW, fa = 0.15f),
        p("M28 0 L41 10 L28 4.5 L15 10 Z", fill = BODY2, fa = 0.22f),
        p("M15.1,12.1 h0.8 a2.6,2.6 0 0 1 2.6,2.6 v4.1 a2.6,2.6 0 0 1 -2.6,2.6 h-0.8 a2.6,2.6 0 0 1 -2.6,-2.6 v-4.1 a2.6,2.6 0 0 1 2.6,-2.6 Z", fill = TIRE),
        p("M40.1,12.1 h0.8 a2.6,2.6 0 0 1 2.6,2.6 v4.1 a2.6,2.6 0 0 1 -2.6,2.6 h-0.8 a2.6,2.6 0 0 1 -2.6,-2.6 v-4.1 a2.6,2.6 0 0 1 2.6,-2.6 Z", fill = TIRE),
        p("M15.1,44.6 h0.8 a2.6,2.6 0 0 1 2.6,2.6 v4.1 a2.6,2.6 0 0 1 -2.6,2.6 h-0.8 a2.6,2.6 0 0 1 -2.6,-2.6 v-4.1 a2.6,2.6 0 0 1 2.6,-2.6 Z", fill = TIRE),
        p("M40.1,44.6 h0.8 a2.6,2.6 0 0 1 2.6,2.6 v4.1 a2.6,2.6 0 0 1 -2.6,2.6 h-0.8 a2.6,2.6 0 0 1 -2.6,-2.6 v-4.1 a2.6,2.6 0 0 1 2.6,-2.6 Z", fill = TIRE),
        p("M25.5,4 h5 a11,11 0 0 1 11,11 v36 a11,11 0 0 1 -11,11 h-5 a11,11 0 0 1 -11,-11 v-36 a11,11 0 0 1 11,-11 Z", fill = BODY2, stroke = WHITE, sw = 3f),
        p("M12.7,21.4 h0.8 a1.6,1.6 0 0 1 1.6,1.6 v1.3 a1.6,1.6 0 0 1 -1.6,1.6 h-0.8 a1.6,1.6 0 0 1 -1.6,-1.6 v-1.3 a1.6,1.6 0 0 1 1.6,-1.6 Z", fill = BODY2, stroke = WHITE, sw = 1.1f),
        p("M42.5,21.4 h0.8 a1.6,1.6 0 0 1 1.6,1.6 v1.3 a1.6,1.6 0 0 1 -1.6,1.6 h-0.8 a1.6,1.6 0 0 1 -1.6,-1.6 v-1.3 a1.6,1.6 0 0 1 1.6,-1.6 Z", fill = BODY2, stroke = WHITE, sw = 1.1f),
        p("M25,26 h6 a6,6 0 0 1 6,6 v3.1 a6,6 0 0 1 -6,6 h-6 a6,6 0 0 1 -6,-6 v-3.1 a6,6 0 0 1 6,-6 Z", fill = HI),
        p("M20 28.0 Q28 24.0 36 28.0 L35 33.0 L21 33.0 Z", fill = GLASS),
        p("M21 34.1 L35 34.1 L36 39.1 Q28 43.1 20 39.1 Z", fill = GLASS, fa = 0.9f),
    )),
    CarbodyType.FAMILY_LONG to CarSpec(56f, 72f, listOf(
        p("M14.5,70 a13.5,2.6 0 1,0 27,0 a13.5,2.6 0 1,0 -27,0 Z", fill = SHADOW, fa = 0.15f),
        p("M28 0 L41 10 L28 4.5 L15 10 Z", fill = BODY2, fa = 0.22f),
        p("M15.1,13 h0.8 a2.6,2.6 0 0 1 2.6,2.6 v5 a2.6,2.6 0 0 1 -2.6,2.6 h-0.8 a2.6,2.6 0 0 1 -2.6,-2.6 v-5 a2.6,2.6 0 0 1 2.6,-2.6 Z", fill = TIRE),
        p("M40.1,13 h0.8 a2.6,2.6 0 0 1 2.6,2.6 v5 a2.6,2.6 0 0 1 -2.6,2.6 h-0.8 a2.6,2.6 0 0 1 -2.6,-2.6 v-5 a2.6,2.6 0 0 1 2.6,-2.6 Z", fill = TIRE),
        p("M15.1,48.8 h0.8 a2.6,2.6 0 0 1 2.6,2.6 v5 a2.6,2.6 0 0 1 -2.6,2.6 h-0.8 a2.6,2.6 0 0 1 -2.6,-2.6 v-5 a2.6,2.6 0 0 1 2.6,-2.6 Z", fill = TIRE),
        p("M40.1,48.8 h0.8 a2.6,2.6 0 0 1 2.6,2.6 v5 a2.6,2.6 0 0 1 -2.6,2.6 h-0.8 a2.6,2.6 0 0 1 -2.6,-2.6 v-5 a2.6,2.6 0 0 1 2.6,-2.6 Z", fill = TIRE),
        p("M25.5,4 h5 a11,11 0 0 1 11,11 v42 a11,11 0 0 1 -11,11 h-5 a11,11 0 0 1 -11,-11 v-42 a11,11 0 0 1 11,-11 Z", fill = BODY2, stroke = WHITE, sw = 3f),
        p("M12.7,23.2 h0.8 a1.6,1.6 0 0 1 1.6,1.6 v1.3 a1.6,1.6 0 0 1 -1.6,1.6 h-0.8 a1.6,1.6 0 0 1 -1.6,-1.6 v-1.3 a1.6,1.6 0 0 1 1.6,-1.6 Z", fill = BODY2, stroke = WHITE, sw = 1.1f),
        p("M42.5,23.2 h0.8 a1.6,1.6 0 0 1 1.6,1.6 v1.3 a1.6,1.6 0 0 1 -1.6,1.6 h-0.8 a1.6,1.6 0 0 1 -1.6,-1.6 v-1.3 a1.6,1.6 0 0 1 1.6,-1.6 Z", fill = BODY2, stroke = WHITE, sw = 1.1f),
        p("M25,23.2 h6 a6,6 0 0 1 6,6 v21.3 a6,6 0 0 1 -6,6 h-6 a6,6 0 0 1 -6,-6 v-21.3 a6,6 0 0 1 6,-6 Z", fill = HI),
        p("M20 25.2 Q28 21.2 36 25.2 L35 30.2 L21 30.2 Z", fill = GLASS),
        p("M21 49.5 L35 49.5 L36 54.5 Q28 58.5 20 54.5 Z", fill = GLASS, fa = 0.9f),
    )),
    CarbodyType.SUV_LARGE to CarSpec(56f, 74f, listOf(
        p("M12,72 a16,2.6 0 1,0 32,0 a16,2.6 0 1,0 -32,0 Z", fill = SHADOW, fa = 0.15f),
        p("M28 0 L41 10 L28 4.5 L15 10 Z", fill = BODY2, fa = 0.22f),
        p("M12.6,13.2 h0.8 a2.6,2.6 0 0 1 2.6,2.6 v5.4 a2.6,2.6 0 0 1 -2.6,2.6 h-0.8 a2.6,2.6 0 0 1 -2.6,-2.6 v-5.4 a2.6,2.6 0 0 1 2.6,-2.6 Z", fill = TIRE),
        p("M42.6,13.2 h0.8 a2.6,2.6 0 0 1 2.6,2.6 v5.4 a2.6,2.6 0 0 1 -2.6,2.6 h-0.8 a2.6,2.6 0 0 1 -2.6,-2.6 v-5.4 a2.6,2.6 0 0 1 2.6,-2.6 Z", fill = TIRE),
        p("M12.6,50.2 h0.8 a2.6,2.6 0 0 1 2.6,2.6 v5.4 a2.6,2.6 0 0 1 -2.6,2.6 h-0.8 a2.6,2.6 0 0 1 -2.6,-2.6 v-5.4 a2.6,2.6 0 0 1 2.6,-2.6 Z", fill = TIRE),
        p("M42.6,50.2 h0.8 a2.6,2.6 0 0 1 2.6,2.6 v5.4 a2.6,2.6 0 0 1 -2.6,2.6 h-0.8 a2.6,2.6 0 0 1 -2.6,-2.6 v-5.4 a2.6,2.6 0 0 1 2.6,-2.6 Z", fill = TIRE),
        p("M23,4 h10 a11,11 0 0 1 11,11 v44 a11,11 0 0 1 -11,11 h-10 a11,11 0 0 1 -11,-11 v-44 a11,11 0 0 1 11,-11 Z", fill = BODY2, stroke = WHITE, sw = 3f),
        p("M10.2,23.8 h0.8 a1.6,1.6 0 0 1 1.6,1.6 v1.3 a1.6,1.6 0 0 1 -1.6,1.6 h-0.8 a1.6,1.6 0 0 1 -1.6,-1.6 v-1.3 a1.6,1.6 0 0 1 1.6,-1.6 Z", fill = BODY2, stroke = WHITE, sw = 1.1f),
        p("M45,23.8 h0.8 a1.6,1.6 0 0 1 1.6,1.6 v1.3 a1.6,1.6 0 0 1 -1.6,1.6 h-0.8 a1.6,1.6 0 0 1 -1.6,-1.6 v-1.3 a1.6,1.6 0 0 1 1.6,-1.6 Z", fill = BODY2, stroke = WHITE, sw = 1.1f),
        p("M22.5,21.2 h11 a6,6 0 0 1 6,6 v26.3 a6,6 0 0 1 -6,6 h-11 a6,6 0 0 1 -6,-6 v-26.3 a6,6 0 0 1 6,-6 Z", fill = HI),
        p("M17.5 23.2 Q28 19.2 38.5 23.2 L37.5 28.2 L18.5 28.2 Z", fill = GLASS),
        p("M18.5 52.4 L37.5 52.4 L38.5 57.4 Q28 61.4 17.5 57.4 Z", fill = GLASS, fa = 0.9f),
    )),
    CarbodyType.VAN_LIGHT to CarSpec(56f, 74f, listOf(
        p("M13,72 a15,2.6 0 1,0 30,0 a15,2.6 0 1,0 -30,0 Z", fill = SHADOW, fa = 0.15f),
        p("M28 0 L41 10 L28 4.5 L15 10 Z", fill = BODY2, fa = 0.22f),
        p("M13.6,13.2 h0.8 a2.6,2.6 0 0 1 2.6,2.6 v5.4 a2.6,2.6 0 0 1 -2.6,2.6 h-0.8 a2.6,2.6 0 0 1 -2.6,-2.6 v-5.4 a2.6,2.6 0 0 1 2.6,-2.6 Z", fill = TIRE),
        p("M41.6,13.2 h0.8 a2.6,2.6 0 0 1 2.6,2.6 v5.4 a2.6,2.6 0 0 1 -2.6,2.6 h-0.8 a2.6,2.6 0 0 1 -2.6,-2.6 v-5.4 a2.6,2.6 0 0 1 2.6,-2.6 Z", fill = TIRE),
        p("M13.6,50.2 h0.8 a2.6,2.6 0 0 1 2.6,2.6 v5.4 a2.6,2.6 0 0 1 -2.6,2.6 h-0.8 a2.6,2.6 0 0 1 -2.6,-2.6 v-5.4 a2.6,2.6 0 0 1 2.6,-2.6 Z", fill = TIRE),
        p("M41.6,50.2 h0.8 a2.6,2.6 0 0 1 2.6,2.6 v5.4 a2.6,2.6 0 0 1 -2.6,2.6 h-0.8 a2.6,2.6 0 0 1 -2.6,-2.6 v-5.4 a2.6,2.6 0 0 1 2.6,-2.6 Z", fill = TIRE),
        p("M24,4 h8 a11,11 0 0 1 11,11 v44 a11,11 0 0 1 -11,11 h-8 a11,11 0 0 1 -11,-11 v-44 a11,11 0 0 1 11,-11 Z", fill = BODY2, stroke = WHITE, sw = 3f),
        p("M11.2,23.8 h0.8 a1.6,1.6 0 0 1 1.6,1.6 v1.3 a1.6,1.6 0 0 1 -1.6,1.6 h-0.8 a1.6,1.6 0 0 1 -1.6,-1.6 v-1.3 a1.6,1.6 0 0 1 1.6,-1.6 Z", fill = BODY2, stroke = WHITE, sw = 1.1f),
        p("M44,23.8 h0.8 a1.6,1.6 0 0 1 1.6,1.6 v1.3 a1.6,1.6 0 0 1 -1.6,1.6 h-0.8 a1.6,1.6 0 0 1 -1.6,-1.6 v-1.3 a1.6,1.6 0 0 1 1.6,-1.6 Z", fill = BODY2, stroke = WHITE, sw = 1.1f),
        p("M23.5,17.2 h9 a6,6 0 0 1 6,6 v3.8 a6,6 0 0 1 -6,6 h-9 a6,6 0 0 1 -6,-6 v-3.8 a6,6 0 0 1 6,-6 Z", fill = HI),
        p("M18.5 19.2 Q28 15.2 37.5 19.2 L36.5 25.2 L19.5 25.2 Z", fill = GLASS),
    )),
    CarbodyType.VAN_COMMERCIAL to CarSpec(56f, 82f, listOf(
        p("M12,80 a16,2.6 0 1,0 32,0 a16,2.6 0 1,0 -32,0 Z", fill = SHADOW, fa = 0.15f),
        p("M28 0 L41 10 L28 4.5 L15 10 Z", fill = BODY2, fa = 0.22f),
        p("M12.6,14.4 h0.8 a2.6,2.6 0 0 1 2.6,2.6 v6.6 a2.6,2.6 0 0 1 -2.6,2.6 h-0.8 a2.6,2.6 0 0 1 -2.6,-2.6 v-6.6 a2.6,2.6 0 0 1 2.6,-2.6 Z", fill = TIRE),
        p("M42.6,14.4 h0.8 a2.6,2.6 0 0 1 2.6,2.6 v6.6 a2.6,2.6 0 0 1 -2.6,2.6 h-0.8 a2.6,2.6 0 0 1 -2.6,-2.6 v-6.6 a2.6,2.6 0 0 1 2.6,-2.6 Z", fill = TIRE),
        p("M12.6,55.8 h0.8 a2.6,2.6 0 0 1 2.6,2.6 v6.6 a2.6,2.6 0 0 1 -2.6,2.6 h-0.8 a2.6,2.6 0 0 1 -2.6,-2.6 v-6.6 a2.6,2.6 0 0 1 2.6,-2.6 Z", fill = TIRE),
        p("M42.6,55.8 h0.8 a2.6,2.6 0 0 1 2.6,2.6 v6.6 a2.6,2.6 0 0 1 -2.6,2.6 h-0.8 a2.6,2.6 0 0 1 -2.6,-2.6 v-6.6 a2.6,2.6 0 0 1 2.6,-2.6 Z", fill = TIRE),
        p("M23,4 h10 a11,11 0 0 1 11,11 v52 a11,11 0 0 1 -11,11 h-10 a11,11 0 0 1 -11,-11 v-52 a11,11 0 0 1 11,-11 Z", fill = BODY2, stroke = WHITE, sw = 3f),
        p("M10.2,26.2 h0.8 a1.6,1.6 0 0 1 1.6,1.6 v1.3 a1.6,1.6 0 0 1 -1.6,1.6 h-0.8 a1.6,1.6 0 0 1 -1.6,-1.6 v-1.3 a1.6,1.6 0 0 1 1.6,-1.6 Z", fill = BODY2, stroke = WHITE, sw = 1.1f),
        p("M45,26.2 h0.8 a1.6,1.6 0 0 1 1.6,1.6 v1.3 a1.6,1.6 0 0 1 -1.6,1.6 h-0.8 a1.6,1.6 0 0 1 -1.6,-1.6 v-1.3 a1.6,1.6 0 0 1 1.6,-1.6 Z", fill = BODY2, stroke = WHITE, sw = 1.1f),
        p("M22.5,17.3 h11 a6,6 0 0 1 6,6 v4.3 a6,6 0 0 1 -6,6 h-11 a6,6 0 0 1 -6,-6 v-4.3 a6,6 0 0 1 6,-6 Z", fill = HI),
        p("M17.5 19.3 Q28 15.3 38.5 19.3 L37.5 25.3 L18.5 25.3 Z", fill = GLASS),
    )),
    CarbodyType.PICKUP to CarSpec(56f, 70f, listOf(
        p("M14,68 a14,2.6 0 1,0 28,0 a14,2.6 0 1,0 -28,0 Z", fill = SHADOW, fa = 0.15f),
        p("M28 0 L41 10 L28 4.5 L15 10 Z", fill = BODY2, fa = 0.22f),
        p("M14.6,12.7 h0.8 a2.6,2.6 0 0 1 2.6,2.6 v4.7 a2.6,2.6 0 0 1 -2.6,2.6 h-0.8 a2.6,2.6 0 0 1 -2.6,-2.6 v-4.7 a2.6,2.6 0 0 1 2.6,-2.6 Z", fill = TIRE),
        p("M40.6,12.7 h0.8 a2.6,2.6 0 0 1 2.6,2.6 v4.7 a2.6,2.6 0 0 1 -2.6,2.6 h-0.8 a2.6,2.6 0 0 1 -2.6,-2.6 v-4.7 a2.6,2.6 0 0 1 2.6,-2.6 Z", fill = TIRE),
        p("M14.6,47.4 h0.8 a2.6,2.6 0 0 1 2.6,2.6 v4.7 a2.6,2.6 0 0 1 -2.6,2.6 h-0.8 a2.6,2.6 0 0 1 -2.6,-2.6 v-4.7 a2.6,2.6 0 0 1 2.6,-2.6 Z", fill = TIRE),
        p("M40.6,47.4 h0.8 a2.6,2.6 0 0 1 2.6,2.6 v4.7 a2.6,2.6 0 0 1 -2.6,2.6 h-0.8 a2.6,2.6 0 0 1 -2.6,-2.6 v-4.7 a2.6,2.6 0 0 1 2.6,-2.6 Z", fill = TIRE),
        p("M25,4 h6 a11,11 0 0 1 11,11 v40 a11,11 0 0 1 -11,11 h-6 a11,11 0 0 1 -11,-11 v-40 a11,11 0 0 1 11,-11 Z", fill = BODY2, stroke = WHITE, sw = 3f),
        p("M12.2,22.6 h0.8 a1.6,1.6 0 0 1 1.6,1.6 v1.3 a1.6,1.6 0 0 1 -1.6,1.6 h-0.8 a1.6,1.6 0 0 1 -1.6,-1.6 v-1.3 a1.6,1.6 0 0 1 1.6,-1.6 Z", fill = BODY2, stroke = WHITE, sw = 1.1f),
        p("M43,22.6 h0.8 a1.6,1.6 0 0 1 1.6,1.6 v1.3 a1.6,1.6 0 0 1 -1.6,1.6 h-0.8 a1.6,1.6 0 0 1 -1.6,-1.6 v-1.3 a1.6,1.6 0 0 1 1.6,-1.6 Z", fill = BODY2, stroke = WHITE, sw = 1.1f),
        p("M23.5,18.9 h9 a5,5 0 0 1 5,5 v7.4 a5,5 0 0 1 -5,5 h-9 a5,5 0 0 1 -5,-5 v-7.4 a5,5 0 0 1 5,-5 Z", fill = HI),
        p("M19.5 20.9 Q28 16.9 36.5 20.9 L35.5 25.9 L20.5 25.9 Z", fill = GLASS),
        p("M21,39.2 h14 a4,4 0 0 1 4,4 v15.8 a4,4 0 0 1 -4,4 h-14 a4,4 0 0 1 -4,-4 v-15.8 a4,4 0 0 1 4,-4 Z", fill = BODY),
    )),
)

internal fun isoCarSpec(carbody: CarbodyType): CarSpec = ISO.getValue(carbody)

internal fun topdownCarSpec(carbody: CarbodyType): CarSpec = TOPDOWN.getValue(carbody)

/**
 * Rebuilds [spec] as an [ImageVector], swapping the body-family colours for [palette]. Theme-neutral
 * colours follow [isDark] (dark glass, accent hubs, lighter shadow). The body keeps its baked white
 * outline and the wheels always gain a white ring — in BOTH themes — so every car lifts off any
 * surface with the same white border. [wheelStroke] is the ring width for this orientation
 * (side-profile vs the smaller top-down wheels). [VEH-COLOR-001] [CAR-WHITE-BORDER-001]
 */
internal fun buildCarImageVector(
    spec: CarSpec,
    palette: CarPalette,
    isDark: Boolean,
    wheelStroke: Float,
): ImageVector {
    val builder = ImageVector.Builder(
        defaultWidth = spec.viewportWidth.dp,
        defaultHeight = spec.viewportHeight.dp,
        viewportWidth = spec.viewportWidth,
        viewportHeight = spec.viewportHeight,
    )
    spec.paths.forEach { path ->
        // A bare tyre (filled, no stroke) always gets a white ring so the wheels carry the same white
        // border as the body, on every surface in light and dark. [CAR-WHITE-BORDER-001]
        val addWheelRing = path.fill == TIRE && path.stroke == null
        builder.addPath(
            pathData = PathParser().parsePathString(path.data).toNodes(),
            fill = path.fill?.let { SolidColor(recolor(it, palette, isDark)) },
            fillAlpha = path.fillAlpha,
            stroke = when {
                path.stroke != null -> SolidColor(recolor(path.stroke, palette, isDark))
                addWheelRing -> SolidColor(Color.White)
                else -> null
            },
            strokeAlpha = path.strokeAlpha,
            strokeLineWidth = if (addWheelRing) wheelStroke else path.strokeWidth,
            strokeLineJoin = if (path.round) StrokeJoin.Round else StrokeJoin.Miter,
        )
    }
    return builder.build()
}

// White wheel-ring widths (side-profile wheels r≈8, top-down r≈2.6). Applied in both themes.
internal const val ISO_WHEEL_STROKE = 2f
internal const val TOPDOWN_WHEEL_STROKE = 1.4f
