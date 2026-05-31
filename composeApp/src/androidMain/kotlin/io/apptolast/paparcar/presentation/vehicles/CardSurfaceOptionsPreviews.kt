package io.apptolast.paparcar.presentation.vehicles

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import io.apptolast.paparcar.presentation.preview.FakeData
import io.apptolast.paparcar.ui.theme.PaparcarTheme

// ═══════════════════════════════════════════════════════════════════════════════
//  Comparativa de surface card — Pantalla Vehículos (modo oscuro)
//
//  Las tres opciones difieren SOLO en el valor de surfaceContainerHigh /
//  surfaceVariant, que es el token que usan:
//    · Tarjeta hero del vehículo inactivo
//    · Tarjetas de stats (sesiones / última sesión / fiabilidad)
//    · Gráfico de actividad semanal
//    · Pill de tab no seleccionado
//    · Chips de filtro de historial no seleccionados
//
//  Opción A — Plano   #141918  mismo tono que surfaceContainer (chips, buscador)
//  Opción B — Sutil   #181D1C  lift mínimo (~punto medio entre A y C)
//  Opción C — Elevado #1C2221  valor original antes del cambio DS
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

private val previewState = VehiclesState(
    vehicles = FakeData.vehiclesWithStats,
    isLoading = false,
    selectedVehicleIndex = if (FakeData.vehiclesWithStats.size > 1) 1 else 0,
)

@Preview(
    name = "Vehicles · Opción A — Plano #141918 (oscuro)",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun VehiclesOptionADarkPreview() {
    DarkOption(SURFACE_A) { VehiclesContent(state = previewState) }
}

@Preview(
    name = "Vehicles · Opción B — Sutil #181D1C (oscuro)",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun VehiclesOptionBDarkPreview() {
    DarkOption(SURFACE_B) { VehiclesContent(state = previewState) }
}

@Preview(
    name = "Vehicles · Opción C — Elevado #1C2221 (oscuro)",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun VehiclesOptionCDarkPreview() {
    DarkOption(SURFACE_C) { VehiclesContent(state = previewState) }
}

// ─── Sin borde ────────────────────────────────────────────────────────────────

@Preview(
    name = "Vehicles · Sin borde · Opción A — Plano #141918 (oscuro)",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun VehiclesNoBorderOptionADarkPreview() {
    DarkOptionNoBorder(SURFACE_A) { VehiclesContent(state = previewState) }
}

@Preview(
    name = "Vehicles · Sin borde · Opción B — Sutil #181D1C (oscuro)",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun VehiclesNoBorderOptionBDarkPreview() {
    DarkOptionNoBorder(SURFACE_B) { VehiclesContent(state = previewState) }
}

@Preview(
    name = "Vehicles · Sin borde · Opción C — Elevado #1C2221 (oscuro)",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun VehiclesNoBorderOptionCDarkPreview() {
    DarkOptionNoBorder(SURFACE_C) { VehiclesContent(state = previewState) }
}
