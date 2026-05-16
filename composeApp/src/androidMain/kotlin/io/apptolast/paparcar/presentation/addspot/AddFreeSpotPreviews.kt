package io.apptolast.paparcar.presentation.addspot

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.LocationInfo
import io.apptolast.paparcar.ui.theme.PaparcarTheme

/**
 * Visual previews for the AddFreeSpotScreen rework.
 *
 * The full [AddFreeSpotContent] embeds [PaparcarMapView], whose Google Maps
 * dependency does not initialise inside Android Studio's Compose preview
 * runtime. So these previews render only the [ReportSheet] — the
 * visually-interesting bottom surface — against a faked map placeholder.
 * The glass back button (a MapCircleFab) renders identically here at runtime.
 */

@Preview(name = "Sheet · GPS pending (light)", showBackground = true)
@Composable
private fun ReportSheetGpsPendingLightPreview() {
    PaparcarTheme(darkTheme = false) {
        SheetOverFakeMap(state = AddFreeSpotState())
    }
}

@Preview(name = "Sheet · Locating address (light)", showBackground = true)
@Composable
private fun ReportSheetLocatingLightPreview() {
    PaparcarTheme(darkTheme = false) {
        SheetOverFakeMap(state = stateLocatingAddress())
    }
}

@Preview(name = "Sheet · Locating address (dark)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReportSheetLocatingDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        SheetOverFakeMap(state = stateLocatingAddress())
    }
}

@Preview(name = "Sheet · Address resolved (light)", showBackground = true)
@Composable
private fun ReportSheetAddressLightPreview() {
    PaparcarTheme(darkTheme = false) {
        SheetOverFakeMap(state = stateAddressResolved())
    }
}

@Preview(name = "Sheet · Address resolved (dark)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReportSheetAddressDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        SheetOverFakeMap(state = stateAddressResolved())
    }
}

@Preview(name = "Sheet · Reporting in progress (light)", showBackground = true)
@Composable
private fun ReportSheetReportingLightPreview() {
    PaparcarTheme(darkTheme = false) {
        SheetOverFakeMap(state = stateAddressResolved().copy(isReporting = true))
    }
}

// ─── Fixtures ────────────────────────────────────────────────────────────────

private fun stateLocatingAddress() = AddFreeSpotState(
    userGpsPoint = madridGps(),
    cameraLat = madridGps().latitude,
    cameraLon = madridGps().longitude,
    pinLocation = null,
)

private fun stateAddressResolved() = AddFreeSpotState(
    userGpsPoint = madridGps(),
    cameraLat = madridGps().latitude,
    cameraLon = madridGps().longitude,
    pinLocation = LocationInfo(
        address = AddressInfo(
            street = "Calle Mayor, 14",
            city = "Madrid",
            region = null,
            country = "España",
        ),
        placeInfo = null,
    ),
)

private fun madridGps() = GpsPoint(
    latitude = 40.416775,
    longitude = -3.703790,
    accuracy = 8f,
    timestamp = 0L,
    speed = 0f,
)

@Composable
private fun SheetOverFakeMap(state: AddFreeSpotState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 240.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        ReportSheet(
            state = state,
            onConfirmReport = {},
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
