@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.home.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.presentation.util.distanceMeters
import io.apptolast.paparcar.presentation.util.driveTimeString
import io.apptolast.paparcar.presentation.util.formatDistance
import io.apptolast.paparcar.presentation.util.locationDisplayText
import io.apptolast.paparcar.ui.theme.PapForestDark
import io.apptolast.paparcar.ui.theme.PapGreen
import io.apptolast.paparcar.ui.theme.PaparcarTheme
import kotlin.time.Clock

private const val FRESH_MINUTES = 5L
private const val RECENT_MINUTES = 15L

// ═══════════════════════════════════════════════════════════════════════════════
//  SECCIÓN B — PROPUESTA: Peek con LazyRow de chips horizontales
//
//  El peek reemplaza la lista vertical por tarjetas deslizables (LazyRow). Cada
//  tarjeta muestra icono P, dirección, distancia y chip de frescura en ~160dp.
//  Tocar un chip selecciona el marcador en el mapa sin necesidad de expandir
//  el sheet. El sheet expandido mantiene el layout de la Versión A.
//
//  NUEVOS COMPONENTES (solo en este archivo, no en producción):
//    · SpotChipCardB  — tarjeta compacta horizontal para el LazyRow
//    · PeekContentB   — contenido del peek: location row + LazyRow de chips
// ═══════════════════════════════════════════════════════════════════════════════

// ─── B — SpotChipCardB ────────────────────────────────────────────────────────

/**
 * Tarjeta compacta (~160dp ancho) para el LazyRow horizontal del peek (Propuesta B).
 * Muestra: icono P + chip de frescura en la fila superior, dirección en L2, distancia en L3.
 */
@Composable
private fun SpotChipCardB(
    spot: Spot,
    userLocation: Pair<Double, Double>?,
    isSelected: Boolean = false,
    onClick: () -> Unit = {},
) {
    val distanceM = userLocation?.let { (uLat, uLon) ->
        distanceMeters(uLat, uLon, spot.location.latitude, spot.location.longitude)
    }
    val displayText = locationDisplayText(
        placeInfo = spot.placeInfo,
        address = spot.address,
        lat = spot.location.latitude,
        lon = spot.location.longitude,
    )
    val ageMinutes = remember(spot.location.timestamp) {
        (Clock.System.now().toEpochMilliseconds() - spot.location.timestamp) / 60_000L
    }
    val freshnessContainer = when {
        ageMinutes < FRESH_MINUTES  -> MaterialTheme.colorScheme.primaryContainer
        ageMinutes < RECENT_MINUTES -> MaterialTheme.colorScheme.secondaryContainer
        else                        -> MaterialTheme.colorScheme.surfaceVariant
    }
    val freshnessContent = when {
        ageMinutes < FRESH_MINUTES  -> MaterialTheme.colorScheme.primary
        ageMinutes < RECENT_MINUTES -> MaterialTheme.colorScheme.secondary
        else                        -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    }
    val freshnessLabel = when {
        ageMinutes < 1L  -> "< 1m"
        ageMinutes < 60L -> "hace ${ageMinutes}m"
        else             -> "hace ${ageMinutes / 60L}h"
    }
    val chipBg = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    else
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
    val chipBorder = if (isSelected)
        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    else
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)

    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(160.dp)
            .border(1.dp, chipBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = chipBg,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            // Fila superior: icono P + chip de frescura
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
                            RoundedCornerShape(8.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "P",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.primary,
                    )
                }
                Surface(shape = RoundedCornerShape(6.dp), color = freshnessContainer) {
                    Text(
                        freshnessLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = freshnessContent,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
            }
            // Dirección
            Text(
                displayText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Distancia + tiempo en coche
            if (distanceM != null) {
                Text(
                    "${formatDistance(distanceM)}  ·  ${driveTimeString(distanceM)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ─── B — PeekContentB ────────────────────────────────────────────────────────

/**
 * Contenido del peek para la Propuesta B: pill + fila de location/badge + LazyRow de chips.
 * Sustituye a HomePeekHandle en estado idle (sin spot/parking seleccionado).
 */
@Composable
private fun PeekContentB(
    locationLabel: String,
    spots: List<Spot>,
    userLocation: Pair<Double, Double>?,
    selectedSpotId: String? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Drag pill
        Box(
            modifier = Modifier
                .padding(top = 10.dp, bottom = 6.dp)
                .size(width = 32.dp, height = 4.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), CircleShape)
                .align(Alignment.CenterHorizontally),
        )
        // Location + badge
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Outlined.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                locationLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (spots.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(8.dp)) {
                    Text(
                        "${spots.size} libres",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
        // LazyRow horizontal de chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(spots) { spot ->
                SpotChipCardB(
                    spot = spot,
                    userLocation = userLocation,
                    isSelected = spot.id == selectedSpotId,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
    }
}

// ─── B — Previews de componentes aislados ─────────────────────────────────────

@Preview(name = "B — SpotChipCardB: 3 frescuras en fila (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES, heightDp = 140, widthDp = 530)
@Composable
private fun SpotChipCardBRowDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SpotChipCardB(fakeSpot("c1", 0L), Pair(40.4165, -3.7030))
            SpotChipCardB(fakeSpotWithPoi("c2", 10L), Pair(40.4165, -3.7030))
            SpotChipCardB(fakeSpot("c3", 25L), Pair(40.4165, -3.7030))
        }
    }
}

@Preview(name = "B — SpotChipCardB: 3 frescuras en fila (claro)", showBackground = true,
    heightDp = 140, widthDp = 530)
@Composable
private fun SpotChipCardBRowLightPreview() {
    PaparcarTheme(darkTheme = false) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SpotChipCardB(fakeSpot("c1", 0L), Pair(40.4165, -3.7030))
            SpotChipCardB(fakeSpotWithPoi("c2", 10L), Pair(40.4165, -3.7030))
            SpotChipCardB(fakeSpot("c3", 25L), Pair(40.4165, -3.7030))
        }
    }
}

@Preview(name = "B — SpotChipCardB: seleccionado (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES, heightDp = 130, widthDp = 200)
@Composable
private fun SpotChipCardBSelectedDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        Box(Modifier.padding(12.dp)) {
            SpotChipCardB(fakeSpotWithPoi("sel", 3L), Pair(40.4165, -3.7030), isSelected = true)
        }
    }
}

@Preview(name = "B — PeekContentB: location + chips (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES, heightDp = 200)
@Composable
private fun PeekContentBDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            PeekContentB(
                locationLabel = "Salamanca, Madrid",
                spots = fakeSpotsVariedFreshness(),
                userLocation = Pair(40.4165, -3.7030),
            )
        }
    }
}

@Preview(name = "B — PeekContentB: chip seleccionado (claro)", showBackground = true, heightDp = 200)
@Composable
private fun PeekContentBSelectedLightPreview() {
    PaparcarTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            PeekContentB(
                locationLabel = "Salamanca, Madrid",
                spots = fakeSpotsVariedFreshness(),
                userLocation = Pair(40.4165, -3.7030),
                selectedSpotId = "v2",
            )
        }
    }
}

// ─── B — Pantallas completas ──────────────────────────────────────────────────

@Preview(name = "B — Pantalla: peek chips sin coche (oscuro)",
    showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES,
    widthDp = 393, heightDp = 851)
@Composable
private fun ProposalBScreenNoParkingDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        Column(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFF1C2030)),
                contentAlignment = Alignment.Center,
            ) {
                Text("[mapa]", style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.15f))
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            ) {
                PeekContentB(
                    locationLabel = "Salamanca, Madrid",
                    spots = fakeSpotsVariedFreshness(),
                    userLocation = Pair(40.4165, -3.7030),
                )
            }
        }
    }
}

@Preview(name = "B — Pantalla: peek chips sin coche (claro)",
    showBackground = true, widthDp = 393, heightDp = 851)
@Composable
private fun ProposalBScreenNoParkingLightPreview() {
    PaparcarTheme(darkTheme = false) {
        Column(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFFE4DDD5)),
                contentAlignment = Alignment.Center,
            ) {
                Text("[mapa]", style = MaterialTheme.typography.labelSmall,
                    color = Color.Black.copy(alpha = 0.15f))
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            ) {
                PeekContentB(
                    locationLabel = "Salamanca, Madrid",
                    spots = fakeSpotsVariedFreshness(),
                    userLocation = Pair(40.4165, -3.7030),
                )
            }
        }
    }
}

@Preview(name = "B — Pantalla: chip seleccionado (oscuro)",
    showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES,
    widthDp = 393, heightDp = 851)
@Composable
private fun ProposalBScreenChipSelectedDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        Column(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFF1C2030)),
                contentAlignment = Alignment.Center,
            ) {
                Text("[mapa — marcador seleccionado]", style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.15f))
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            ) {
                PeekContentB(
                    locationLabel = "Salamanca, Madrid",
                    spots = fakeSpotsVariedFreshness(),
                    userLocation = Pair(40.4165, -3.7030),
                    selectedSpotId = "v1",
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  SECCIÓN C — PROPUESTA: Map-first con contexto sticky
//
//  El mapa es el protagonista. El peek se reduce a una barra mínima:
//    · Badge de conteo de spots ("3 libres")
//    · Resumen del coche aparcado en 1 línea (cuando existe)
//    · Botón "Ver lista" para acceder al panel de spots
//  Parking activo → tarjeta flotante pequeña sobre el mapa, no en el sheet.
//  La lista de spots aparece en modal/panel al pulsar "Ver lista".
//
//  NUEVOS COMPONENTES (solo en este archivo, no en producción):
//    · MinimalPeekBarC      — barra inferior mínima
//    · FloatingParkingCardC — tarjeta flotante de parking activo
// ═══════════════════════════════════════════════════════════════════════════════

// ─── C — MinimalPeekBarC ──────────────────────────────────────────────────────

/**
 * Barra inferior mínima para la Propuesta C.
 * Muestra el conteo de spots libres, opcionalmente el parking activo en 1 línea,
 * y un botón "Ver lista" para abrir el panel de spots completo.
 */
@Composable
private fun MinimalPeekBarC(
    spotCount: Int,
    locationLabel: String = "",
    parkingLabel: String? = null,
    parkingTime: String? = null,
    onShowList: () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .padding(top = 10.dp, bottom = 6.dp)
                .size(width = 32.dp, height = 4.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), CircleShape)
                .align(Alignment.CenterHorizontally),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Badge de conteo
            Surface(
                color = if (spotCount > 0) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "$spotCount",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (spotCount > 0) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    )
                    Text(
                        "libres",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (spotCount > 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                    )
                }
            }
            // Contexto: parking activo o ubicación cámara
            Column(modifier = Modifier.weight(1f)) {
                if (parkingLabel != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Filled.DirectionsCar,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            parkingLabel,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (parkingTime != null) {
                        Text(
                            parkingTime,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Outlined.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            locationLabel.ifEmpty { "Buscando ubicación..." },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            // Botón "Ver lista"
            Surface(
                onClick = onShowList,
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    "Ver lista",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

// ─── C — FloatingParkingCardC ─────────────────────────────────────────────────

/**
 * Tarjeta flotante sobre el mapa que muestra el parking activo (Propuesta C).
 * Incluye ícono de coche, dirección/POI, tiempo + distancia, y acción "Liberar".
 * Se posiciona en la parte inferior del área de mapa, encima de la barra mínima.
 */
@Composable
private fun FloatingParkingCardC(
    placeLabel: String,
    timeAgo: String,
    distanceLabel: String,
    modifier: Modifier = Modifier,
    onRelease: () -> Unit = {},
) {
    Surface(
        modifier = modifier.border(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
            RoundedCornerShape(16.dp),
        ),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 5.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(PapForestDark, CircleShape)
                    .border(2.5.dp, PapGreen, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.DirectionsCar,
                    contentDescription = null,
                    tint = PapGreen,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    placeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "$timeAgo  ·  $distanceLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                )
            }
            Surface(
                onClick = onRelease,
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Text(
                    "Liberar",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                )
            }
        }
    }
}

// ─── C — Previews de componentes aislados ─────────────────────────────────────

@Preview(name = "C — MinimalPeekBarC: con coche (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES, heightDp = 120)
@Composable
private fun MinimalPeekBarCWithParkingDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            MinimalPeekBarC(
                spotCount = 3,
                parkingLabel = "⛽ Repsol Av. Castellana",
                parkingTime = "hace 30 min  ·  280m",
            )
        }
    }
}

@Preview(name = "C — MinimalPeekBarC: sin coche (claro)", showBackground = true, heightDp = 110)
@Composable
private fun MinimalPeekBarCNoParkingLightPreview() {
    PaparcarTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            MinimalPeekBarC(spotCount = 3, locationLabel = "Salamanca, Madrid")
        }
    }
}

@Preview(name = "C — MinimalPeekBarC: 0 spots (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES, heightDp = 110)
@Composable
private fun MinimalPeekBarCZeroSpotsDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            MinimalPeekBarC(spotCount = 0, locationLabel = "Gran Vía, Madrid")
        }
    }
}

@Preview(name = "C — FloatingParkingCardC (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES, heightDp = 90)
@Composable
private fun FloatingParkingCardCDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        Box(Modifier.padding(12.dp)) {
            FloatingParkingCardC(
                placeLabel = "⛽ Repsol Av. de la Castellana",
                timeAgo = "hace 30 min",
                distanceLabel = "280 m",
            )
        }
    }
}

@Preview(name = "C — FloatingParkingCardC (claro)", showBackground = true, heightDp = 90)
@Composable
private fun FloatingParkingCardCLightPreview() {
    PaparcarTheme(darkTheme = false) {
        Box(Modifier.padding(12.dp)) {
            FloatingParkingCardC(
                placeLabel = "🛒 Mercadona Fuencarral",
                timeAgo = "hace 1 h",
                distanceLabel = "420 m",
            )
        }
    }
}

// ─── C — Pantallas completas ──────────────────────────────────────────────────

@Preview(name = "C — Pantalla: con coche aparcado (oscuro)",
    showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES,
    widthDp = 393, heightDp = 851)
@Composable
private fun ProposalCScreenWithParkingDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        Column(Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFF1C2030))) {
                Text(
                    "[mapa — protagonista]",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.12f),
                    modifier = Modifier.align(Alignment.Center),
                )
                FloatingParkingCardC(
                    placeLabel = "⛽ Repsol Av. de la Castellana",
                    timeAgo = "hace 30 min",
                    distanceLabel = "280 m",
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                        .fillMaxWidth(),
                )
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            ) {
                MinimalPeekBarC(
                    spotCount = 3,
                    parkingLabel = "Repsol Av. Castellana",
                    parkingTime = "hace 30 min  ·  280m",
                )
            }
        }
    }
}

@Preview(name = "C — Pantalla: sin coche, 3 spots (claro)",
    showBackground = true, widthDp = 393, heightDp = 851)
@Composable
private fun ProposalCScreenNoParkingLightPreview() {
    PaparcarTheme(darkTheme = false) {
        Column(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFFE4DDD5)),
                contentAlignment = Alignment.Center,
            ) {
                Text("[mapa — protagonista]", style = MaterialTheme.typography.labelSmall,
                    color = Color.Black.copy(alpha = 0.12f))
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            ) {
                MinimalPeekBarC(spotCount = 3, locationLabel = "Salamanca, Madrid")
            }
        }
    }
}

@Preview(name = "C — Pantalla: con coche aparcado (claro)",
    showBackground = true, widthDp = 393, heightDp = 851)
@Composable
private fun ProposalCScreenWithParkingLightPreview() {
    PaparcarTheme(darkTheme = false) {
        Column(Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFFE4DDD5))) {
                Text(
                    "[mapa — protagonista]",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Black.copy(alpha = 0.12f),
                    modifier = Modifier.align(Alignment.Center),
                )
                FloatingParkingCardC(
                    placeLabel = "🛒 Mercadona Fuencarral",
                    timeAgo = "hace 1 h",
                    distanceLabel = "420 m",
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                        .fillMaxWidth(),
                )
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            ) {
                MinimalPeekBarC(
                    spotCount = 0,
                    parkingLabel = "Mercadona Fuencarral",
                    parkingTime = "hace 1 h  ·  420m",
                )
            }
        }
    }
}