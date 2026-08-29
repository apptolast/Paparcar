package com.rndeveloper.paparcar.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.plus_jakarta_sans_variable

/**
 * Qué fuente pone cada una de las tres VOCES del sistema [UI-TYPE-FAMILY-CANDIDATES-001].
 *
 * `PaparcarType` habla de voces — marca / lectura / cifra — y de roles; este objeto dice con qué
 * letra se pintan. Separarlo es lo que permitió probar tres sistemas tipográficos **en la app
 * entera** sin tocar un solo rol ni un solo call site, y lo que hace que cambiar de familia siga
 * siendo un cambio de un sitio.
 *
 * Hoy las tres voces apuntan a la misma familia. **Eso no las fusiona**: siguen decidiendo peso,
 * tamaño y si un texto es un nombre, una cifra o prosa.
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
     * es una propiedad de la FUENTE. Vivía como constante privada de la ficha de vehículo, calibrada
     * a mano para la familia de entonces (0.70, que era exactamente el cap de Barlow Condensed), así
     * que al cambiar de letra el icono se despegó de los números sin que nada lo avisara.
     * [UI-STAT-ICON-CENTERS-ON-DIGITS-001]
     */
    val figureCapHeightEm: Float,
    /**
     * Ascenso y descenso de [figure], en fracción de em, de su tabla `hhea`.
     *
     * Una caja de texto mide ascent+descent, pero una cifra sólo pinta hasta la altura de mayúscula
     * y no baja de la línea base. Ese hueco muerto es lo que hunde un número contra el borde
     * inferior de su celda cuando se centra la caja en vez del glifo — y lo que `LineHeightStyle`
     * NO arregla, porque recorta el sobrante de `lineHeight`, no el ascenso de la fuente.
     * [UI-SHEET-001]
     */
    val figureAscentEm: Float,
    val figureDescentEm: Float,
)

/**
 * Cuánto hay que SUBIR un bloque «cifra sobre unidad» para que lo que se ve quede centrado en su
 * caja, en vez de estarlo la caja de texto.
 *
 * Arriba sobra el ascenso que el dígito no usa; abajo, el descenso que las mayúsculas no usan. La
 * corrección es la mitad de la diferencia. Medido en device: el contador del sheet pasó de 14 px
 * descentrado a 2 px. [UI-SHEET-001]
 */
fun PapFontSet.figureOpticalLiftSp(figureSp: Float, unitSp: Float): Float {
    val deadTop = (figureAscentEm - figureCapHeightEm) * figureSp
    val deadBottom = figureDescentEm * unitSp
    return (deadTop - deadBottom) / 2f
}

/** El font set activo, para lo poco que necesita las MÉTRICAS de la fuente y no un rol. */
object PapFonts {
    val current: PapFontSet
        @Composable get() = defaultFontSet()
}

// Métricas de Plus Jakarta Sans, leídas de sus tablas `OS/2` y `hhea`.
private const val JAKARTA_CAP_HEIGHT_EM = 0.745f
private const val JAKARTA_ASCENT_EM = 1.038f
private const val JAKARTA_DESCENT_EM = 0.222f

@Composable
private fun jakartaFamily() = FontFamily(
    listOf(
        FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold,
        FontWeight.Bold, FontWeight.ExtraBold,
    ).map { weight ->
        // Jakarta es variable: cada peso pina el eje `wght` o Compose resuelve todo a la instancia
        // por defecto y aplica bold sintético de forma inconsistente. Misma trampa que documentó
        // Inter en su día.
        Font(
            Res.font.plus_jakarta_sans_variable,
            weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
        )
    },
)

/**
 * **Plus Jakarta Sans en las tres voces** — lo que la app usa.
 *
 * Elegida por el user el 29-08 tras verla corriendo en el Redmi contra Outfit+Inter+Barlow y contra
 * Archivo. Una familia, un fichero, y el carácter redondeado que la marca ya tenía con Outfit — que
 * es lo que Archivo, el sistema más limpio sobre el papel, se llevaba por delante.
 * [UI-TYPE-FAMILY-CANDIDATES-001]
 */
@Composable
fun defaultFontSet() = PapFontSet(
    name = "Plus Jakarta Sans",
    brand = jakartaFamily(),
    text = jakartaFamily(),
    figure = jakartaFamily(),
    figureCapHeightEm = JAKARTA_CAP_HEIGHT_EM,
    figureAscentEm = JAKARTA_ASCENT_EM,
    figureDescentEm = JAKARTA_DESCENT_EM,
)
