package com.rndeveloper.paparcar.dev

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rndeveloper.paparcar.domain.model.SpotFreshness
import com.rndeveloper.paparcar.ui.components.SpotPuckIcon
import com.rndeveloper.paparcar.ui.theme.PapAlpha
import com.rndeveloper.paparcar.ui.theme.PaparcarTheme
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import com.rndeveloper.paparcar.ui.theme.rememberBarlowCondensedFontFamily
import com.rndeveloper.paparcar.ui.theme.stateColors
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material3.Icon

/**
 * Laboratorio tipográfico — **fase 1 de UI-TYPE-TWO-VOICES-ONE-ROW-001**.
 *
 * No decide nada: pinta las mismas cuatro filas de plaza (las de la captura del 29-08) y la card de
 * métricas en cuatro tratamientos tipográficos, apilados, para decidir MIRÁNDOLO en device.
 *
 * Lo que se compara:
 * - **A · Actual** — nombre en Outfit 14, meta-line en Barlow 13. Es lo que hay en master, y el
 *   choque que el user identificó a ojo: las dos caras más distintas del set a 1 renglón y 1 px de
 *   tamaño de distancia.
 * - **B · Meta en Inter** — el nombre sigue en Outfit (es identidad), la meta baja a Inter 12. Dos
 *   caras por fila en vez de tres.
 * - **C · Inter + cifras tabulares** — igual que B con `fontFeatureSettings = "tnum"` en los
 *   números. ⚠️ HIPÓTESIS A VERIFICAR: que Compose Multiplatform 1.12 aplique `tnum` en Android.
 *   Si los dígitos de C no se ven más anchos/alineados que los de B, no funciona y el argumento de
 *   "los datos necesitan cara propia" se sostiene sólo con Barlow.
 * - **D · Recompuesta** — la distancia sube a la derecha del nombre (idea del user): forma columna
 *   a lo largo de las filas, que es la ÚNICA precondición bajo la que Barlow se gana su sitio. La
 *   meta-line se queda con fiabilidad · tiempo · en-camino, en Inter.
 *
 * Al final, el **caso peor de ancho**: la fila con 4 tokens en ES / PL / RO. PL es el peor
 * (`ŚREDNIE · NIEPOTWIERDZONE · 214 m · 1 min samochodem`). Si desborda en Inter, la respuesta no es
 * volver a la condensada: es que la línea dice demasiado y hay que acortar el copy.
 *
 * Textos hardcodeados a propósito: es dev tooling y necesita fijar los tres idiomas a la vez, sin
 * depender del locale del device.
 */
@Composable
fun TypographyLabScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    PaparcarTheme(darkTheme = isSystemInDarkTheme()) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(bottom = 32.dp),
            ) {
                LabHeader()
                TypeVariant.entries.forEach { variant ->
                    VariantBlock(variant)
                }
                WorstCaseBlock()
                StatsBlock()
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Variantes
// ─────────────────────────────────────────────────────────────────────────────

private enum class TypeVariant(val title: String, val note: String) {
    Actual(
        "A · ACTUAL (master)",
        "Nombre Outfit 14 · meta Barlow 13. Tres familias por pantalla.",
    ),
    MetaInter(
        "B · META EN INTER",
        "Nombre Outfit 14 · meta Inter 12. Dos familias por fila.",
    ),
    MetaInterTabular(
        "C · INTER + CIFRAS TABULARES",
        "Como B, con tnum en los números. Si C == B, tnum NO se está aplicando.",
    ),
    Recomposed(
        "D · RECOMPUESTA (distancia junto al nombre)",
        "La distancia sube a la derecha en Barlow y forma columna · meta en Inter.",
    ),
    TwoFamilies(
        "E · DOS FAMILIAS · CERO BARLOW EN LA FILA",
        "Como D pero la distancia también en Inter. La columna la marca la POSICIÓN y el peso, " +
            "no la familia. Si esta cabe, Home baja a dos caras y Barlow se queda sólo en métricas.",
    ),
}

/** Variantes que suben la distancia a la línea del nombre. */
private val TypeVariant.distanceOnTop: Boolean
    get() = this == TypeVariant.Recomposed || this == TypeVariant.TwoFamilies

/** Cifras tabulares — el eje que se está poniendo a prueba en la variante C. */
private fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

// ─────────────────────────────────────────────────────────────────────────────
// Bloques
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun VariantBlock(variant: TypeVariant) {
    BlockHeader(variant.title, variant.note)
    SampleRows.forEach { sample ->
        LabSpotRow(sample = sample, variant = variant)
        HorizontalDivider(
            modifier = Modifier.padding(start = 71.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = PapAlpha.dim),
        )
    }
}

@Composable
private fun WorstCaseBlock() {
    BlockHeader(
        "CASO PEOR DE ANCHO · 4 tokens",
        "Misma fila en ES / PL / RO, en A · B · D. PL es el peor. Se busca desbordamiento: " +
            "B trunca en los tres idiomas; la pregunta es si D (sin la distancia en la meta) cabe.",
    )
    WorstCaseLocales.forEach { (locale, sample) ->
        Text(
            locale,
            style = PaparcarType.current.label,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 10.dp),
        )
        listOf(
            TypeVariant.Actual,
            TypeVariant.MetaInter,
            TypeVariant.Recomposed,
            TypeVariant.TwoFamilies,
        ).forEach { LabSpotRow(sample = sample, variant = it) }
    }
}

@Composable
private fun StatsBlock() {
    BlockHeader(
        "MÉTRICAS DEL COCHE",
        "La cifra grande es el otro sitio donde Barlow se gana el sueldo. Barlow · Inter · Inter tnum.",
    )
    val type = PaparcarType.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        StatCell("Barlow", "1.284", type.statNumber, type.badge)
        StatCell("Inter", "1.284", InterStatStyle(), InterBadgeStyle())
        StatCell("Inter tnum", "1.284", InterStatStyle().tabular(), InterBadgeStyle())
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        StatCell("Barlow", "92%", type.statNumber, type.badge)
        StatCell("Inter", "92%", InterStatStyle(), InterBadgeStyle())
        StatCell("Inter tnum", "92%", InterStatStyle().tabular(), InterBadgeStyle())
    }
}

/** Barlow `statNumber` es 25sp Bold; el equivalente Inter se prueba 2sp menor — Inter tiene mayor
 *  altura de x, así que a igual sp pesa más. Es parte de lo que hay que juzgar en device. */
@Composable
private fun InterStatStyle(): TextStyle =
    PaparcarType.current.subtitle.copy(fontSize = 23.sp, fontWeight = FontWeight.Bold)

@Composable
private fun InterBadgeStyle(): TextStyle =
    PaparcarType.current.label.copy(fontWeight = FontWeight.SemiBold)

@Composable
private fun StatCell(caption: String, value: String, valueStyle: TextStyle, labelStyle: TextStyle) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            caption,
            style = PaparcarType.current.caption,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        Text(value, style = valueStyle, color = MaterialTheme.colorScheme.onSurface)
        Text(
            "PLAZAS CEDIDAS",
            style = labelStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BlockHeader(title: String, note: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            title,
            style = PaparcarType.current.sectionHeader,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            note,
            style = PaparcarType.current.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// La fila, replicada con la anatomía exacta de HomeSpotRows.SpotRowContent
// (puck 42 · gap 12 · padding 13/16/12/12 · POI 15+5 · spacer 2)
// ─────────────────────────────────────────────────────────────────────────────

private class SpotSample(
    val name: String,
    val reliabilityLabel: String,
    val reliability: SpotFreshness,
    val distance: String,
    val driveTime: String,
    val enRoute: String? = null,
    val unconfirmed: String? = null,
    val poiIcon: ImageVector? = null,
)

private val SampleRows = listOf(
    SpotSample("Plaza del Arenal 1", "BAJA", SpotFreshness.STALE, "426 m", "1 min en coche"),
    SpotSample("Calle Corredera 8", "FIABLE", SpotFreshness.FRESH, "518 m", "1 min en coche"),
    SpotSample(
        "Repsol Consistorio · Calle Cielos", "FIABLE", SpotFreshness.FRESH,
        "419 m", "1 min en coche", enRoute = "3 en camino",
        poiIcon = Icons.Rounded.LocalGasStation,
    ),
    SpotSample(
        "Calle Porvera 30", "MEDIA", SpotFreshness.RECENT, "214 m", "1 min en coche",
        unconfirmed = "SIN CONFIRMAR",
    ),
)

private val WorstCaseLocales = listOf(
    "ES" to SampleRows.last(),
    "PL" to SpotSample(
        "Calle Porvera 30", "ŚREDNIE", SpotFreshness.RECENT,
        "214 m", "1 min samochodem", unconfirmed = "NIEPOTWIERDZONE",
    ),
    "RO" to SpotSample(
        "Calle Porvera 30", "MEDIE", SpotFreshness.RECENT,
        "214 m", "1 min cu mașina", unconfirmed = "NECONFIRMAT",
    ),
)

private const val META_SEPARATOR = "  ·  "

@Composable
private fun LabSpotRow(sample: SpotSample, variant: TypeVariant) {
    val cs = MaterialTheme.colorScheme
    val type = PaparcarType.current
    val badgeColor = sample.reliability.stateColors().bg

    // El nombre es identidad en las cuatro variantes — lo que se compara es la META.
    val nameStyle = type.rowName
    val nameWeight = FontWeight.Bold

    // La variante A reproduce el estado ANTERIOR al ticket, así que construye Barlow a mano: los
    // roles `metadata`/`badge` ya no son condensados en el sistema nuevo. Este fichero es dev
    // tooling fuera del guardrail — es el único sitio donde resolver una familia está permitido,
    // porque su trabajo es precisamente comparar familias.
    val barlow = rememberBarlowCondensedFontFamily()
    val barlowMeta = TextStyle(
        fontFamily = barlow, fontWeight = FontWeight.Medium,
        fontSize = 13.sp, lineHeight = 15.sp, letterSpacing = 0.sp,
    )
    val metaBase = when (variant) {
        TypeVariant.Actual -> barlowMeta
        TypeVariant.MetaInterTabular -> type.caption.tabular()
        else -> type.caption
    }
    val metaBadge = when (variant) {
        TypeVariant.Actual -> barlowMeta.copy(fontWeight = FontWeight.Bold)
        else -> type.badge
    }
    // El token de distancia, alineado al final y formando columna entre filas.
    // D lo pinta en Barlow (voz Cifra); E en Inter — la que se llevó la decisión.
    val distanceTokenStyle = when (variant) {
        TypeVariant.TwoFamilies -> type.rowDistance
        else -> barlowMeta.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 13.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SpotPuckIcon(
            reliability = sample.reliability,
            enRouteCount = if (sample.enRoute != null) 3 else 0,
            modifier = Modifier.size(42.dp),
        )

        Column(modifier = Modifier.weight(1f)) {
            // ── Línea 1 · nombre (+ distancia en D) ──────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                sample.poiIcon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = cs.onSurfaceVariant,
                        modifier = Modifier.size(15.dp),
                    )
                }
                Text(
                    text = sample.name,
                    style = nameStyle,
                    fontWeight = nameWeight,
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (variant.distanceOnTop) {
                    Text(
                        text = sample.distance,
                        style = distanceTokenStyle,
                        color = cs.onSurface,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))

            // ── Línea 2 · meta ───────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(sample.reliabilityLabel, style = metaBadge, color = badgeColor, maxLines = 1)
                MetaSep(metaBase)
                sample.unconfirmed?.let {
                    Text(
                        it,
                        style = metaBadge.copy(fontWeight = FontWeight.Normal),
                        color = cs.onSurface.copy(alpha = PapAlpha.subtitle),
                        maxLines = 1,
                    )
                    MetaSep(metaBase)
                }
                // En A/B/C la distancia vive aquí; en D/E ya subió arriba.
                if (!variant.distanceOnTop) {
                    Text(
                        sample.distance,
                        style = metaBase.copy(fontWeight = FontWeight.SemiBold),
                        color = cs.onSurface.copy(alpha = 0.7f),
                        maxLines = 1,
                    )
                    MetaSep(metaBase)
                }
                Text(
                    sample.driveTime,
                    style = metaBase,
                    color = cs.onSurface.copy(alpha = PapAlpha.subtitle),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // En D/E el "en camino" baja aquí, liberando el trailing para la distancia.
                if (variant.distanceOnTop && sample.enRoute != null) {
                    MetaSep(metaBase)
                    Text(
                        sample.enRoute,
                        style = metaBase,
                        color = cs.onSurface.copy(alpha = PapAlpha.subtitle),
                        maxLines = 1,
                    )
                }
            }
        }

        // Trailing "3 en camino" — en A/B/C ocupa el sitio que D/E le dan a la distancia.
        if (!variant.distanceOnTop && sample.enRoute != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Group,
                    contentDescription = null,
                    tint = cs.onSurface.copy(alpha = PapAlpha.subtitle),
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    sample.enRoute,
                    style = if (variant == TypeVariant.Actual) type.badge else type.label,
                    color = cs.onSurface.copy(alpha = PapAlpha.subtitle),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun MetaSep(style: TextStyle) {
    Text(
        META_SEPARATOR,
        style = style,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = PapAlpha.dim),
    )
}

@Composable
private fun LabHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            // El cluster DEV/☀ de DevRoot flota sobre el top-end: dejarle su hueco.
            .padding(start = 16.dp, end = 110.dp, top = 16.dp, bottom = 16.dp),
    ) {
        Text(
            "LAB TIPOGRÁFICO",
            style = PaparcarType.current.screenTitle,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "UI-TYPE-TWO-VOICES-ONE-ROW-001 · fase 1. Cuatro tratamientos de la misma fila. " +
                "Compara la línea de abajo con el nombre de arriba: ¿se leen como una cosa o como dos?",
            style = PaparcarType.current.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .width(120.dp)
                .height(2.dp)
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "El toggle ☀/🌙 de arriba vale aquí también.",
            style = PaparcarType.current.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
