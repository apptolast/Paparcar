package com.rndeveloper.paparcar.presentation.home.sections.sheet.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.rndeveloper.paparcar.domain.parkings.FeeInfo
import com.rndeveloper.paparcar.domain.parkings.ParkingAvailability
import com.rndeveloper.paparcar.domain.parkings.PublicParking
import com.rndeveloper.paparcar.domain.parkings.PublicParkingKind
import com.rndeveloper.paparcar.domain.usecase.parkings.NearbyPublicParking
import com.rndeveloper.paparcar.presentation.home.PeekStep
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.peek.PublicParkingPeek
import com.rndeveloper.paparcar.ui.components.PublicParkingMarker
import com.rndeveloper.paparcar.ui.theme.PaparcarTheme
import kotlin.time.Clock

@Preview(name = "Parking availability · list and marker", widthDp = 360, locale = "es")
@Composable
private fun PublicParkingListAndMarkerPreview() {
    PaparcarTheme(darkTheme = false) {
        Surface {
            Column {
                PublicParkingMarker(selected = false, feeLabel = "De pago", occupancyLabel = "42 plazas libres")
                HomePublicParkingRows(listOf(samplePublicParking(42), samplePublicParking(0)))
            }
        }
    }
}

@Preview(name = "Parking availability · detail", widthDp = 360, locale = "es")
@Preview(name = "Parking availability · large text", widthDp = 360, locale = "es", fontScale = 1.5f)
@Composable
private fun PublicParkingDetailPreview() {
    PaparcarTheme(darkTheme = false) {
        PublicParkingPeek(
            nearby = samplePublicParking(42),
            userLocation = null,
            step = PeekStep.None,
            onIntent = {},
            onAction = {},
        )
    }
}

private fun samplePublicParking(free: Int) = NearbyPublicParking(
    parking = PublicParking(
        id = "preview-parking-$free",
        name = if (free == 0) "Parking Benavente" else "Aparcamiento Recoletos",
        operator = null,
        latitude = 40.42,
        longitude = -3.69,
        kind = PublicParkingKind.UNDERGROUND,
        capacity = 300,
        fee = FeeInfo.PAID,
        availability = ParkingAvailability(
            free = free,
            updatedAtMs = Clock.System.now().toEpochMilliseconds(),
            source = "preview",
        ),
    ),
    distanceMeters = 260.0,
)
