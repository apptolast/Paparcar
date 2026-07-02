package io.apptolast.paparcar.presentation.vehicles

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.apptolast.paparcar.presentation.preview.FakeData
import io.apptolast.paparcar.ui.theme.PaparcarTheme

// ─── Production previews — render the real VehiclesContent / VehicleHeroCard ───
//
// These mirror the live card: name + plain status pin (green active / blue BT / grey inactive) +
// size chip, a divided stats row, and — for inactive vehicles — a separate "Establecer como activo"
// row. They render the real composable so they stay in sync automatically. [HOME-VEH-REFINE-001]

// Per-vehicle history so the activity chart + filter bar + timeline render in the preview (the hero
// card alone doesn't exercise the History section). [VEHICLES-REDESIGN-001]
private fun previewHistory(sessions: List<io.apptolast.paparcar.domain.model.UserParking>) =
    FakeData.vehiclesWithStats.associate { vws ->
        vws.vehicle.id to HistoryState(
            sessions = sessions,
            activeFilter = HistoryFilter.All,
            filteredSessions = sessions,
        )
    }

@Preview(name = "Vehicles — lista · Claro", showBackground = true)
@Composable
private fun VehiclesListLightPreview() {
    PaparcarTheme(darkTheme = false) {
        VehiclesContent(
            state = VehiclesState(
                vehicles = FakeData.vehiclesWithStats,
                isLoading = false,
                historyCache = previewHistory(FakeData.allSessions),
            ),
        )
    }
}

@Preview(name = "Vehicles — lista · Oscuro", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VehiclesListDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        VehiclesContent(
            state = VehiclesState(
                vehicles = FakeData.vehiclesWithStats,
                isLoading = false,
                historyCache = previewHistory(FakeData.allSessions),
            ),
        )
    }
}

@Preview(name = "Vehicles — pocos datos · Oscuro", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VehiclesLowDataDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        VehiclesContent(
            state = VehiclesState(
                vehicles = FakeData.vehiclesWithStats,
                isLoading = false,
                historyCache = previewHistory(FakeData.endedSessions.take(1)),
            ),
        )
    }
}

@Preview(name = "Vehicles — ficha Bluetooth · Oscuro", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VehiclesBluetoothDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        VehiclesContent(
            state = VehiclesState(
                vehicles = FakeData.vehiclesWithStats,
                isLoading = false,
                selectedVehicleIndex = 1, // Corolla — BT paired
                historyCache = previewHistory(FakeData.allSessions),
            ),
        )
    }
}

@Preview(name = "Vehicles — ficha inactiva (métricas + activar) · Oscuro", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VehiclesInactiveDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        VehiclesContent(
            state = VehiclesState(
                vehicles = FakeData.vehiclesWithStats,
                isLoading = false,
                selectedVehicleIndex = 2, // Moto — inactive, keeps muted stats + "Establecer como activo"
                historyCache = previewHistory(FakeData.allSessions),
            ),
        )
    }
}

@Preview(name = "Vehicles — vacío · Claro", showBackground = true)
@Composable
private fun VehiclesEmptyLightPreview() {
    PaparcarTheme(darkTheme = false) {
        VehiclesContent(
            state = VehiclesState(vehicles = emptyList(), isLoading = false),
        )
    }
}

@Preview(name = "Vehicles — vacío · Oscuro", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VehiclesEmptyDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        VehiclesContent(
            state = VehiclesState(vehicles = emptyList(), isLoading = false),
        )
    }
}

@Preview(name = "Vehicles — cargando", showBackground = true)
@Composable
private fun VehiclesLoadingPreview() {
    PaparcarTheme(darkTheme = false) {
        VehiclesContent(
            state = VehiclesState(isLoading = true),
        )
    }
}
