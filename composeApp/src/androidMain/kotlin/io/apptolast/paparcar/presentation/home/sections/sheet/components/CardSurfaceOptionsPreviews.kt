@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.home.sections.sheet.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import io.apptolast.paparcar.presentation.home.VehicleCard
import io.apptolast.paparcar.presentation.preview.FakeData
import io.apptolast.paparcar.ui.theme.PaparcarTheme

// ═══════════════════════════════════════════════════════════════════════════════
//  Comparativa de surface card — Sheet de Home (modo oscuro)
//
//  Elementos visibles que usan surfaceContainerHigh:
//    · HomeVehicleCard sin sesión activa (Park CTA)
//    · HomeVehicleCard con sesión activa → usa primaryContainer (no cambia)
//    · HomeSpotRow en cualquier estado (referencia, no usa surfaceContainerHigh)
//
//  Opción A — Plano   #141918
//  Opción B — Sutil   #181D1C
//  Opción C — Elevado #1C2221
// ═══════════════════════════════════════════════════════════════════════════════

private val SURFACE_A = Color(0xFF141918)
private val SURFACE_B = Color(0xFF181D1C)
private val SURFACE_C = Color(0xFF1C2221)

@Composable
private fun DarkOption(cardSurface: Color, content: @Composable () -> Unit) {
    PaparcarTheme(darkTheme = true) {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.copy(
                surfaceContainerHigh = cardSurface,
                surfaceVariant = cardSurface,
            ),
        ) {
            content()
        }
    }
}

@Composable
private fun DarkOptionNoBorder(cardSurface: Color, content: @Composable () -> Unit) {
    PaparcarTheme(darkTheme = true) {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.copy(
                surfaceContainerHigh = cardSurface,
                surfaceVariant = cardSurface,
                outline = Color.Transparent,
                outlineVariant = Color.Transparent,
            ),
        ) {
            content()
        }
    }
}

@Composable
private fun HomeSheetSample() {
    val userLoc = Pair(40.4165, -3.7030)
    Column(Modifier.padding(vertical = 8.dp)) {
        // Sin sesión activa → surfaceContainerHigh (aquí A/B/C difieren visualmente)
        HomeVehicleChip(
            card = VehicleCard(vehicle = FakeData.vehicleSedan, session = null),
            userLocation = userLoc,
            isSelected = false,
            onClick = {},
        )
        // Con sesión activa → primaryContainer (referencia fija, no varía entre opciones)
        HomeVehicleChip(
            card = VehicleCard(vehicle = FakeData.vehicleVan, session = FakeData.activeSession),
            userLocation = userLoc,
            isSelected = false,
            onClick = {},
        )
        // Spots de contexto
        HomeSpotRow(spot = fakeSpot("s1", 2L), userLocation = userLoc, onSelect = {})
        HomeSpotRow(spot = fakeSpotWithPoi("s2", 12L), userLocation = userLoc, onSelect = {})
        HomeSpotRow(spot = fakeSpot("s3", 28L), userLocation = userLoc, onSelect = {})
    }
}

@Preview(
    name = "HomeSheet · Opción A — Plano #141918 (oscuro)",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun HomeSheetOptionADarkPreview() {
    DarkOption(SURFACE_A) { HomeSheetSample() }
}

@Preview(
    name = "HomeSheet · Opción B — Sutil #181D1C (oscuro)",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun HomeSheetOptionBDarkPreview() {
    DarkOption(SURFACE_B) { HomeSheetSample() }
}

@Preview(
    name = "HomeSheet · Opción C — Elevado #1C2221 (oscuro)",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun HomeSheetOptionCDarkPreview() {
    DarkOption(SURFACE_C) { HomeSheetSample() }
}

// ─── Sin borde ────────────────────────────────────────────────────────────────

@Preview(
    name = "HomeSheet · Sin borde · Opción A — Plano #141918 (oscuro)",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun HomeSheetNoBorderOptionADarkPreview() {
    DarkOptionNoBorder(SURFACE_A) { HomeSheetSample() }
}

@Preview(
    name = "HomeSheet · Sin borde · Opción B — Sutil #181D1C (oscuro)",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun HomeSheetNoBorderOptionBDarkPreview() {
    DarkOptionNoBorder(SURFACE_B) { HomeSheetSample() }
}

@Preview(
    name = "HomeSheet · Sin borde · Opción C — Elevado #1C2221 (oscuro)",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun HomeSheetNoBorderOptionCDarkPreview() {
    DarkOptionNoBorder(SURFACE_C) { HomeSheetSample() }
}
