package io.apptolast.paparcar.presentation.vehicles

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.apptolast.paparcar.presentation.preview.FakeData
import io.apptolast.paparcar.ui.theme.PaparcarTheme

@Preview(name = "Vehicles — lista · Claro", showBackground = true)
@Composable
private fun VehiclesListLightPreview() {
    PaparcarTheme(darkTheme = false) {
        VehiclesContent(
            state = VehiclesState(vehicles = FakeData.vehiclesWithStats, isLoading = false),
        )
    }
}

@Preview(name = "Vehicles — lista · Oscuro", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VehiclesListDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        VehiclesContent(
            state = VehiclesState(vehicles = FakeData.vehiclesWithStats, isLoading = false),
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

@Preview(name = "Vehicles — diálogo borrar · Claro", showBackground = true)
@Composable
private fun VehiclesDeleteDialogPreview() {
    PaparcarTheme(darkTheme = false) {
        VehiclesContent(
            state = VehiclesState(
                vehicles = FakeData.vehiclesWithStats,
                isLoading = false,
                pendingDeleteVehicleId = FakeData.vehiclesWithStats.first().vehicle.id,
            ),
        )
    }
}
