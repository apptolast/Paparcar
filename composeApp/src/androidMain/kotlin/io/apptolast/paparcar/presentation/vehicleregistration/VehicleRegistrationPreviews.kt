package io.apptolast.paparcar.presentation.vehicleregistration

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.apptolast.paparcar.domain.model.CarbodyType
import io.apptolast.paparcar.domain.model.VehicleColor
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.model.VehicleType
import io.apptolast.paparcar.ui.theme.PaparcarTheme

@Preview(name = "VehicleRegistration — nuevo · Claro", showBackground = true)
@Composable
private fun VehicleRegistrationNewLightPreview() {
    PaparcarTheme(darkTheme = false) {
        VehicleRegistrationContent(
            state = VehicleRegistrationState(),
        )
    }
}

@Preview(name = "VehicleRegistration — nuevo · Oscuro", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VehicleRegistrationNewDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        VehicleRegistrationContent(
            state = VehicleRegistrationState(),
        )
    }
}

@Preview(name = "VehicleRegistration — edición · Claro", showBackground = true)
@Composable
private fun VehicleRegistrationEditLightPreview() {
    PaparcarTheme(darkTheme = false) {
        VehicleRegistrationContent(
            state = VehicleRegistrationState(
                editingVehicleId = "v-edit",
                brand = "Toyota",
                model = "Corolla",
                sizeCategory = VehicleSize.MEDIUM_SUV,
                showBrandModelOnSpot = true,
            ),
        )
    }
}

@Preview(name = "VehicleRegistration — color · Claro", showBackground = true)
@Composable
private fun VehicleRegistrationColorLightPreview() {
    PaparcarTheme(darkTheme = false) {
        VehicleRegistrationContent(
            state = VehicleRegistrationState(
                editingVehicleId = "v-color",
                brand = "Seat",
                model = "León",
                vehicleType = VehicleType.CAR,
                carbodyType = CarbodyType.HATCHBACK_MEDIUM,
                sizeCategory = VehicleSize.MEDIUM_SUV,
                color = VehicleColor.RED,
            ),
        )
    }
}

@Preview(name = "VehicleRegistration — color · Oscuro", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VehicleRegistrationColorDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        VehicleRegistrationContent(
            state = VehicleRegistrationState(
                editingVehicleId = "v-color",
                brand = "Seat",
                model = "León",
                vehicleType = VehicleType.CAR,
                carbodyType = CarbodyType.HATCHBACK_MEDIUM,
                sizeCategory = VehicleSize.MEDIUM_SUV,
                color = VehicleColor.BLUE,
            ),
        )
    }
}

@Preview(name = "VehicleRegistration — guardando", showBackground = true)
@Composable
private fun VehicleRegistrationSavingPreview() {
    PaparcarTheme(darkTheme = false) {
        VehicleRegistrationContent(
            state = VehicleRegistrationState(
                brand = "Seat",
                model = "Ibiza",
                sizeCategory = VehicleSize.MICRO_SMALL,
                isSaving = true,
            ),
        )
    }
}
