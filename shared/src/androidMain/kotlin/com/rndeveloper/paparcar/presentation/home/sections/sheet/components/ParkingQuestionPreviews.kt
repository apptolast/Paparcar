package com.rndeveloper.paparcar.presentation.home.sections.sheet.components

import android.content.res.Configuration
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.rndeveloper.paparcar.domain.detection.PendingPromptWindow
import com.rndeveloper.paparcar.domain.detection.StillParkedQuestion
import com.rndeveloper.paparcar.domain.model.SpotType
import com.rndeveloper.paparcar.presentation.home.HomeState
import com.rndeveloper.paparcar.presentation.home.toBrowseListSlice
import com.rndeveloper.paparcar.presentation.preview.FakeData
import com.rndeveloper.paparcar.ui.theme.PaparcarTheme

@Preview(name = "Card · ¿has aparcado aquí?", showBackground = true, heightDp = 760)
@Composable
private fun ArrivalQuestionCardPreview() {
    QuestionCardPreviewContent(arrival = true)
}

@Preview(name = "Card · ¿sigue aparcado aquí? · oscuro", showBackground = true,
    heightDp = 760, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Card · ¿sigue aparcado aquí? · claro", showBackground = true, heightDp = 760)
@Composable
private fun ParkingQuestionCardPreview() {
    QuestionCardPreviewContent()
}

@Preview(name = "Card · permanencia del segundo coche", showBackground = true,
    heightDp = 760, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SecondCarParkingQuestionPreview() {
    QuestionCardPreviewContent(secondCar = true)
}

@Composable
private fun QuestionCardPreviewContent(arrival: Boolean = false, secondCar: Boolean = false) {
    val vehicle = if (secondCar) FakeData.vehicleVan else FakeData.vehicleSedan
    val parking = FakeData.activeSession.copy(
        vehicleId = vehicle.id, geofenceId = "preview-parking-fence", spotType = SpotType.AUTO_DETECTED,
    )
    val state = HomeState(
        vehicles = if (secondCar) listOf(FakeData.vehicleSedan, vehicle) else listOf(vehicle),
        activeSessions = if (arrival) emptyList() else listOf(parking),
        hasCorePermissions = true,
        promptWindow = if (arrival) PendingPromptWindow(
            shownAtMs = 1_000L, vehicleId = vehicle.id, vehicleName = vehicle.brand,
            candidate = parking.location,
        ) else null,
        stillParkedQuestion = if (!arrival) StillParkedQuestion(
            requireNotNull(parking.geofenceId), parking.location.latitude, parking.location.longitude, 1_000L,
        ) else null,
    )
    PaparcarTheme {
        LazyColumn {
            homeSheetItems(state.toBrowseListSlice(), onIntent = {}, onAction = {})
        }
    }
}
