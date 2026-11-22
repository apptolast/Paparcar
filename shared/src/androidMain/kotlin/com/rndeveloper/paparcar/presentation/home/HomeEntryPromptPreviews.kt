package com.rndeveloper.paparcar.presentation.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.rndeveloper.paparcar.domain.entitlement.PlusMoment
import com.rndeveloper.paparcar.ui.theme.PaparcarTheme
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.HomePeekHandle
import com.rndeveloper.paparcar.presentation.preview.FakeData

/** Mirrors the entry surfaces in the Dev Catalog; real map/editor routing uses its presets. */
@Composable
private fun EntryPreview(gift: Boolean = false, returning: Boolean = false) {
    PaparcarTheme(darkTheme = true) {
        HomeEntryPromptHost(
            accountId = "preview",
            state = HomeState(
                firstHomeParkingFlowReady = true,
                firstHomeParkingFlowCompleted = returning,
                firstHomeParkingFlowCompletedOnArrival = returning,
                showDetectionPitch = !returning,
            ),
            isHomeActive = true,
            plusMoment = if (gift) PlusMoment.FoundersWelcome(null) else null,
            askAdMeasurementConsent = returning,
            onIntent = {},
            onDismissPlusMoment = {},
            onSeePlusPlans = {},
            onAnswerAdMeasurementConsent = {},
        )
    }
}

@Preview(name = "Entry · confirmed gift before detection")
@Composable
private fun GiftEntry() = EntryPreview(gift = true)

@Preview(name = "Entry · detection without gift")
@Composable
private fun DetectionEntry() = EntryPreview()

@Preview(name = "Entry · Meta on a later visit")
@Composable
private fun ReturningEntry() = EntryPreview(returning = true)

@Preview(name = "Entry · existing parking editor", widthDp = 390)
@Composable
private fun FirstParkingEntry() {
    PaparcarTheme(darkTheme = true) {
        val gps = FakeData.activeSession.location
        HomePeekHandle(
            slice = HomeState(
                mode = HomeMode.AddingParking,
                initialHomeParkingEditorActive = true,
                vehicles = listOf(FakeData.vehicleSedan),
                addingParkingVehicleId = FakeData.vehicleSedan.id,
                userGpsPoint = gps,
                pinCameraLat = gps.latitude,
                pinCameraLon = gps.longitude,
            ).toPeekSlice(),
        )
    }
}
