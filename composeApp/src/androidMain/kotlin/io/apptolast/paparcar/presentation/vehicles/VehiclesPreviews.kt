package io.apptolast.paparcar.presentation.vehicles

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.apptolast.paparcar.presentation.preview.FakeData
import io.apptolast.paparcar.ui.theme.PaparcarTheme

// ─── Production previews — render the real VehiclesContent / VehicleHeroCard ───
//
// These mirror the live card (status pill rides as an eyebrow above the name),
// so they stay in sync automatically. [CHIP-DRIVING-001]

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
