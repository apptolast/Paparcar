@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.home.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.presentation.home.HomeState
import io.apptolast.paparcar.presentation.preview.FakeData
import io.apptolast.paparcar.ui.theme.PaparcarTheme

// ─── PapFloatingHeader ────────────────────────────────────────────────────────

@Preview(name = "PapFloatingHeader (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PapFloatingHeaderDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        Column(Modifier.padding(16.dp)) {
            PapFloatingHeader(
                onHistoryClick = {},
                onSettingsClick = {},
            )
        }
    }
}

@Preview(name = "PapFloatingHeader (claro)", showBackground = true)
@Composable
private fun PapFloatingHeaderLightPreview() {
    PaparcarTheme(darkTheme = false) {
        Column(Modifier.padding(16.dp)) {
            PapFloatingHeader(
                onHistoryClick = {},
                onSettingsClick = {},
            )
        }
    }
}

// ─── PapPeekHandle ────────────────────────────────────────────────────────────

@Preview(name = "PapPeekHandle — con POI, spots libres (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PapPeekHandleWithPoiDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        PapPeekHandle(
            state = HomeState(
                userLocationInfo = FakeData.locationInfoFuel,
                nearbySpots = FakeData.nearbySpots,
            ),
            onParkingClick = {},
        )
    }
}

@Preview(name = "PapPeekHandle — dirección simple (claro)", showBackground = true)
@Composable
private fun PapPeekHandleStreetLightPreview() {
    PaparcarTheme(darkTheme = false) {
        PapPeekHandle(
            state = HomeState(
                userLocationInfo = FakeData.locationInfoStreet,
                nearbySpots = FakeData.nearbySpots,
            ),
            onParkingClick = {},
        )
    }
}

@Preview(name = "PapPeekHandle — sin dirección, sin spots (claro)", showBackground = true)
@Composable
private fun PapPeekHandleEmptyLightPreview() {
    PaparcarTheme(darkTheme = false) {
        PapPeekHandle(
            state = HomeState(),
            onParkingClick = {},
        )
    }
}

// ─── PapParkingRow ────────────────────────────────────────────────────────────

@Preview(name = "PapParkingRow — con POI (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PapParkingRowPoiDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        Column(Modifier.padding(16.dp)) {
            PapParkingRow(
                parking = FakeData.activeSession,
                userLocation = Pair(40.4165, -3.7030),
                onClick = {},
            )
        }
    }
}

@Preview(name = "PapParkingRow — sin dirección (claro)", showBackground = true)
@Composable
private fun PapParkingRowNoAddressLightPreview() {
    PaparcarTheme(darkTheme = false) {
        Column(Modifier.padding(16.dp)) {
            PapParkingRow(
                parking = FakeData.activeSession.copy(address = null, placeInfo = null),
                userLocation = null,
                onClick = {},
            )
        }
    }
}
