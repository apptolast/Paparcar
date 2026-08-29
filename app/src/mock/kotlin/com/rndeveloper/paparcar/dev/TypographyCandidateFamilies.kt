package com.rndeveloper.paparcar.dev

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import com.rndeveloper.paparcar.ui.theme.rememberBarlowCondensedFontFamily
import org.jetbrains.compose.resources.Font
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.archivo_variable
import paparcar.composeapp.generated.resources.plus_jakarta_sans_variable

/**
 * Familias candidatas para el laboratorio — **UI-TYPE-FAMILY-CANDIDATES-001**.
 *
 * Sólo dev tooling: estas fuentes NO las usa producción. Están aquí para responder a una pregunta
 * concreta del user — *"¿tenemos demasiadas tipografías? ¿hay una familia que encaje mejor?"* —
 * mirándolas en device en vez de por catálogo.
 *
 * Las dos candidatas representan estrategias OPUESTAS:
 * - **Plus Jakarta Sans** (`wght` 200–800, sin `wdth`): una sola cara geométrica con x-height alta
 *   que aguanta a 12sp, así que puede hacer de display Y de UI. Reduce Home a UNA familia. Mismo
 *   espíritu redondeado que Outfit, que es el carácter que ya se eligió para la marca.
 * - **Archivo** (`wght` 100–900 **+ `wdth` 62–125**): la superfamilia con eje de anchura. De un solo
 *   fichero salen título, UI y una condensada de verdad — que es literalmente lo que el user pedía
 *   y que Outfit/Inter no pueden dar. Coste: es una grotesca, más neutra que Outfit; cambia el tono
 *   de la marca, no sólo la letra.
 *
 * ⚠️ Archivo tiene `wght` **default 600**, así que cada peso se pina explícitamente o sale SemiBold.
 * Mismo motivo por el que Inter pina el suyo en `Typography.kt`.
 */
private const val WDTH_CONDENSED = 75f
private const val WDTH_NORMAL = 100f

@Composable
private fun jakartaFont(weight: FontWeight) = Font(
    Res.font.plus_jakarta_sans_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

@Composable
fun rememberJakartaFamily() = FontFamily(
    jakartaFont(FontWeight.Normal),
    jakartaFont(FontWeight.Medium),
    jakartaFont(FontWeight.SemiBold),
    jakartaFont(FontWeight.Bold),
    jakartaFont(FontWeight.ExtraBold),
)

@Composable
private fun archivoFont(weight: FontWeight, width: Float) = Font(
    Res.font.archivo_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight.weight),
        FontVariation.width(width),
    ),
)

@Composable
fun rememberArchivoFamily(width: Float = WDTH_NORMAL) = FontFamily(
    archivoFont(FontWeight.Normal, width),
    archivoFont(FontWeight.Medium, width),
    archivoFont(FontWeight.SemiBold, width),
    archivoFont(FontWeight.Bold, width),
    archivoFont(FontWeight.ExtraBold, width),
)

/**
 * Los estilos que una fila de plaza y una card de métricas necesitan, para poder pintar la MISMA
 * anatomía (la variante E ya decidida) con distintas familias y comparar sólo la letra.
 */
class CandidateSet(
    val title: String,
    val note: String,
    val name: TextStyle,
    val meta: TextStyle,
    val badge: TextStyle,
    val distance: TextStyle,
    val statNumber: TextStyle,
    val statLabel: TextStyle,
)

/** Lo que hay HOY en master: Outfit (nombre) + Inter (todo lo demás) + Barlow (sólo métricas). */
@Composable
fun currentSet(): CandidateSet {
    val t = PaparcarType.current
    return CandidateSet(
        title = "HOY · Outfit + Inter + Barlow",
        note = "Lo que acabas de mergear. Barlow sólo en la card de métricas.",
        name = t.rowName, meta = t.meta, badge = t.badge, distance = t.rowDistance,
        statNumber = t.statNumber, statLabel = t.statLabel,
    )
}

/** Una sola cara para todo; Barlow se mantiene en la cifra grande. */
@Composable
fun jakartaSet(): CandidateSet {
    val f = rememberJakartaFamily()
    val barlow = rememberBarlowCondensedFontFamily()
    return CandidateSet(
        title = "PLUS JAKARTA SANS · una sola cara",
        note = "Nombre y lectura en la MISMA familia. Barlow sigue en la cifra grande.",
        name = TextStyle(fontFamily = f, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp),
        meta = TextStyle(fontFamily = f, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
        badge = TextStyle(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
        distance = TextStyle(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
        statNumber = TextStyle(fontFamily = barlow, fontWeight = FontWeight.Bold, fontSize = 25.sp, lineHeight = 25.sp),
        statLabel = TextStyle(fontFamily = barlow, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.6.sp),
    )
}

/** UNA familia para todo, incluida la cifra: la condensada sale del eje `wdth`, no de otra fuente. */
@Composable
fun archivoSet(): CandidateSet {
    val f = rememberArchivoFamily()
    val condensed = rememberArchivoFamily(WDTH_CONDENSED)
    return CandidateSet(
        title = "ARCHIVO · superfamilia (wdth 62–125)",
        note = "UNA sola fuente para todo: la cifra usa el eje de anchura a 75, no otra familia.",
        name = TextStyle(fontFamily = f, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp),
        meta = TextStyle(fontFamily = f, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
        badge = TextStyle(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
        distance = TextStyle(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
        statNumber = TextStyle(fontFamily = condensed, fontWeight = FontWeight.Bold, fontSize = 25.sp, lineHeight = 25.sp),
        statLabel = TextStyle(fontFamily = condensed, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.6.sp),
    )
}
