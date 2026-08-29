package com.rndeveloper.paparcar.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.archivo_variable
import paparcar.composeapp.generated.resources.plus_jakarta_sans_variable

/**
 * Las tres VOCES del sistema, resueltas a familias concretas [UI-TYPE-FAMILY-CANDIDATES-001].
 *
 * `PaparcarType` habla de voces (marca / lectura / cifra); este objeto dice qué fuente pone cada
 * voz. Separarlos es lo que permite probar una familia distinta **en la app entera** sin tocar ni
 * un rol ni un call site.
 *
 * En producción sólo existe [defaultFontSet]. El flavor `mock` provee otro a través de
 * [LocalPapFontSet] para poder comparar candidatas en device; con el override a `null`, el
 * comportamiento es exactamente el de antes.
 */
@Immutable
class PapFontSet(
    val name: String,
    /** MARCA — nombres de cosas reales y títulos. */
    val brand: FontFamily,
    /** LECTURA — prosa, acciones, estructura, meta lines, taxonomía. */
    val text: FontFamily,
    /** CIFRA — una cifra que protagoniza su propio bloque. */
    val figure: FontFamily,
    /**
     * Altura de mayúscula de [figure], en fracción de em, leída de su tabla `OS/2`.
     *
     * Existe porque alinear un icono con una cifra exige saber dónde está la BANDA DE DÍGITOS, y eso
     * es una propiedad de la fuente: Barlow Condensed 0.700, Plus Jakarta Sans 0.745, Archivo 0.686.
     * Vivía como constante privada de la ficha de vehículo calibrada a mano para Barlow, así que al
     * cambiar de familia el icono se despegaba de los números sin que nada lo avisara.
     * [UI-STAT-ICON-CENTERS-ON-DIGITS-001]
     */
    val figureCapHeightEm: Float,
    /**
     * Ascenso y descenso tipográficos de [figure], en fracción de em, leídos de su tabla `hhea`.
     *
     * Una caja de texto mide ascent+descent, pero una CIFRA sólo pinta hasta la altura de mayúscula
     * y no baja de la línea base. Ese hueco muerto — 0.29 em por encima del dígito en Jakarta — es
     * lo que hunde un número contra el borde inferior de su celda cuando se centra la caja en vez
     * del glifo. Con estos tres números el centrado óptico se calcula, en lugar de calibrarse a
     * ojo para una familia concreta. [UI-SHEET-001]
     */
    val figureAscentEm: Float,
    val figureDescentEm: Float,
)

/**
 * Cuánto hay que SUBIR un bloque "cifra sobre unidad" para que lo que se ve quede centrado en su
 * caja, en vez de estarlo la caja de texto.
 *
 * Arriba sobra el ascenso que el dígito no usa; abajo sobra el descenso que las mayúsculas no usan.
 * La corrección es la mitad de la diferencia.
 */
fun PapFontSet.figureOpticalOffsetEm(figureSp: Float, unitSp: Float): Float {
    val deadTop = (figureAscentEm - figureCapHeightEm) * figureSp
    val deadBottom = figureDescentEm * unitSp
    return (deadTop - deadBottom) / 2f
}

/** Override de familias para el laboratorio. `null` = el sistema de producción. */
val LocalPapFontSet = staticCompositionLocalOf<PapFontSet?> { null }

/**
 * Lo que la app usa de verdad: **Plus Jakarta Sans en las tres voces**.
 *
 * Elegida por el user el 29-08 tras verla corriendo en el Redmi frente a Outfit+Inter+Barlow y a
 * Archivo. Una sola familia, un solo fichero, y el carácter redondeado que la marca ya tenía con
 * Outfit — que es lo que Archivo, siendo el sistema más limpio sobre el papel, se llevaba por
 * delante. [UI-TYPE-FAMILY-CANDIDATES-001]
 *
 * Las voces siguen existiendo aunque las tres apunten a la misma fuente: son las que deciden peso,
 * tamaño y cuándo un texto es un nombre, una cifra o prosa. Que hoy compartan familia no las
 * fusiona.
 */
@Composable
fun defaultFontSet() = jakartaFullFontSet()

/** El sistema anterior, conservado para poder comparar en el laboratorio. */
@Composable
fun legacyFontSet() = PapFontSet(
    name = "Outfit + Inter + Barlow",
    brand = rememberOutfitFontFamily(),
    text = rememberInterFontFamily(),
    figure = rememberBarlowCondensedFontFamily(),
    figureCapHeightEm = BARLOW_CAP_HEIGHT_EM,
    figureAscentEm = BARLOW_ASCENT_EM,
    figureDescentEm = BARLOW_DESCENT_EM,
)

// ─── Candidatas · SOLO para el laboratorio ───────────────────────────────────
// Sus .ttf viajan en composeResources porque Compose Resources no distingue por flavor. Si alguna
// de estas dos se adoptara, las que se van (Outfit / Inter) salen del repo en la misma tarea.

// Alturas de mayuscula leidas de la tabla OS/2 de cada fichero (sCapHeight / unitsPerEm).
const val BARLOW_CAP_HEIGHT_EM = 0.700f
const val BARLOW_ASCENT_EM = 1.000f
const val BARLOW_DESCENT_EM = 0.200f
const val JAKARTA_CAP_HEIGHT_EM = 0.745f
const val JAKARTA_ASCENT_EM = 1.038f
const val JAKARTA_DESCENT_EM = 0.222f
const val ARCHIVO_CAP_HEIGHT_EM = 0.686f
const val ARCHIVO_ASCENT_EM = 0.878f
const val ARCHIVO_DESCENT_EM = 0.210f

private const val ARCHIVO_WDTH_CONDENSED = 75f
private const val ARCHIVO_WDTH_NORMAL = 100f

@Composable
private fun jakartaFamily() = FontFamily(
    listOf(
        FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold,
        FontWeight.Bold, FontWeight.ExtraBold,
    ).map { w ->
        Font(
            Res.font.plus_jakarta_sans_variable,
            weight = w,
            variationSettings = FontVariation.Settings(FontVariation.weight(w.weight)),
        )
    },
)

/**
 * Archivo pinando también el eje `wdth`.
 *
 * ⚠️ Su `wght` viene con **default 600**: sin pinarlo, todos los pesos salen SemiBold. Es la misma
 * trampa que ya documentó `Typography.kt` para Inter.
 */
@Composable
private fun archivoFamily(width: Float) = FontFamily(
    listOf(
        FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold,
        FontWeight.Bold, FontWeight.ExtraBold,
    ).map { w ->
        Font(
            Res.font.archivo_variable,
            weight = w,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(w.weight),
                FontVariation.width(width),
            ),
        )
    },
)

/** Una sola cara para marca y lectura; la cifra se queda en Barlow. */
@Composable
fun jakartaFontSet(): PapFontSet {
    val jakarta = jakartaFamily()
    return PapFontSet(
        name = "Plus Jakarta Sans (+ Barlow)",
        brand = jakarta,
        text = jakarta,
        figure = rememberBarlowCondensedFontFamily(),
        figureCapHeightEm = BARLOW_CAP_HEIGHT_EM,
        figureAscentEm = BARLOW_ASCENT_EM,
        figureDescentEm = BARLOW_DESCENT_EM,
    )
}

/**
 * Plus Jakarta Sans para TODO, incluida la cifra: cero Barlow en la app.
 *
 * ⚠️ Jakarta no es condensada, asi que las cifras ocupan mas. Los tres sitios donde muerde son la
 * card de metricas (tres celdas compartiendo el ancho), el contador del sheet en Home (una caja de
 * 46dp) y los ejes del grafico. Hay que MIRARLOS, no suponerlos.
 *
 * ⚠️ Ademas descalibra `BARLOW_CAP_HEIGHT_EM` (0.70) de `VehiclePageContent`, que alinea el icono
 * con la banda de digitos asumiendo la metrica de Barlow. [UI-STAT-ICON-CENTERS-ON-DIGITS-001]
 */
@Composable
fun jakartaFullFontSet(): PapFontSet {
    val jakarta = jakartaFamily()
    return PapFontSet(
        name = "Plus Jakarta Sans (completa)",
        brand = jakarta,
        text = jakarta,
        figure = jakarta,
        figureCapHeightEm = JAKARTA_CAP_HEIGHT_EM,
        figureAscentEm = JAKARTA_ASCENT_EM,
        figureDescentEm = JAKARTA_DESCENT_EM,
    )
}

/** UNA familia para todo: la cifra sale del eje de anchura, no de otra fuente. */
@Composable
fun archivoFontSet(): PapFontSet {
    val archivo = archivoFamily(ARCHIVO_WDTH_NORMAL)
    return PapFontSet(
        name = "Archivo (una sola familia)",
        brand = archivo,
        text = archivo,
        figure = archivoFamily(ARCHIVO_WDTH_CONDENSED),
        figureCapHeightEm = ARCHIVO_CAP_HEIGHT_EM,
        figureAscentEm = ARCHIVO_ASCENT_EM,
        figureDescentEm = ARCHIVO_DESCENT_EM,
    )
}
