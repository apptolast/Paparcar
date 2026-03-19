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

// ─── HomeFloatingHeader ────────────────────────────────────────────────────────

@Preview(name = "HomeFloatingHeader (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeFloatingHeaderDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        Column(Modifier.padding(16.dp)) {
            HomeFloatingHeader(
                onHistoryClick = {},
                onSettingsClick = {},
            )
        }
    }
}

@Preview(name = "HomeFloatingHeader (claro)", showBackground = true)
@Composable
private fun HomeFloatingHeaderLightPreview() {
    PaparcarTheme(darkTheme = false) {
        Column(Modifier.padding(16.dp)) {
            HomeFloatingHeader(
                onHistoryClick = {},
                onSettingsClick = {},
            )
        }
    }
}

// ─── HomePeekHandle ────────────────────────────────────────────────────────────

@Preview(name = "HomePeekHandle — con POI, spots libres (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomePeekHandleWithPoiDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        HomePeekHandle(
            state = HomeState(
                userLocationInfo = FakeData.locationInfoFuel,
                nearbySpots = FakeData.nearbySpots,
            ),
        )
    }
}

@Preview(name = "HomePeekHandle — dirección simple (claro)", showBackground = true)
@Composable
private fun HomePeekHandleStreetLightPreview() {
    PaparcarTheme(darkTheme = false) {
        HomePeekHandle(
            state = HomeState(
                userLocationInfo = FakeData.locationInfoStreet,
                nearbySpots = FakeData.nearbySpots,
            ),
        )
    }
}

@Preview(name = "HomePeekHandle — sin dirección, sin spots (claro)", showBackground = true)
@Composable
private fun HomePeekHandleEmptyLightPreview() {
    PaparcarTheme(darkTheme = false) {
        HomePeekHandle(state = HomeState())
    }
}

// ─── HomeParkingRow ────────────────────────────────────────────────────────────

@Preview(name = "HomeParkingRow — con POI (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeParkingRowPoiDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        Column(Modifier.padding(16.dp)) {
            HomeParkingRow(
                parking = FakeData.activeSession,
                userLocation = Pair(40.4165, -3.7030),
                onSelect = {},
            )
        }
    }
}

@Preview(name = "HomeParkingRow — sin dirección (claro)", showBackground = true)
@Composable
private fun HomeParkingRowNoAddressLightPreview() {
    PaparcarTheme(darkTheme = false) {
        Column(Modifier.padding(16.dp)) {
            HomeParkingRow(
                parking = FakeData.activeSession.copy(address = null, placeInfo = null),
                userLocation = null,
                onSelect = {},
            )
        }
    }
}
