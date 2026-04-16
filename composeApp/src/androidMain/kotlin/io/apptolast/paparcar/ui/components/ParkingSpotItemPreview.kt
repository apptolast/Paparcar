@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.presentation.util.SpotReliabilityUiState
import io.apptolast.paparcar.ui.theme.PaparcarTheme
import kotlin.time.Clock

// ── Sample data ───────────────────────────────────────────────────────────────

private fun sampleSpotData(
    id: String,
    location: String,
    distanceM: Float?,
    agoMinutes: Long,
    reliability: SpotReliabilityUiState = SpotReliabilityUiState.HIGH,
    enRouteCount: Int = 0,
    expiresAt: Long = 0L,
) = SpotCardData(
    id = id,
    displayLocation = location,
    distanceMeters = distanceM,
    reportedAtMs = Clock.System.now().toEpochMilliseconds() - agoMinutes * 60_000L,
    reliability = reliability,
    enRouteCount = enRouteCount,
    expiresAt = expiresAt,
)

// ── Previews ──────────────────────────────────────────────────────────────────

@Preview(name = "ParkingSpotItem — list (dark)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ParkingSpotItemListDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            // Fresh auto-detected, with distance
            ParkingSpotItem(
                data = sampleSpotData("1", "⛽ Repsol  ·  Av. Castellana 110", 203f, 0L),
                onClick = {},
            )
            HorizontalDivider(modifier = Modifier.padding(start = 70.dp, end = 16.dp))
            // 10-min amber, selected
            ParkingSpotItem(
                data = sampleSpotData("2", "Charleston Road 1500", 840f, 10L, SpotReliabilityUiState.MEDIUM),
                onClick = {},
                isSelected = true,
            )
            HorizontalDivider(modifier = Modifier.padding(start = 70.dp, end = 16.dp))
            // Old spot, manual report, no distance
            ParkingSpotItem(
                data = sampleSpotData("3", "Calle Gran Vía 32", null, 25L, SpotReliabilityUiState.MANUAL),
                onClick = {},
            )
            HorizontalDivider(modifier = Modifier.padding(start = 70.dp, end = 16.dp))
            // With TTL
            ParkingSpotItem(
                data = sampleSpotData(
                    "4", "Parking Prim  ·  C/ de Prim 14", 450f, 2L,
                    expiresAt = Clock.System.now().toEpochMilliseconds() + 7 * 60_000L,
                ),
                onClick = {},
            )
        }
    }
}

@Preview(name = "ParkingSpotItem — list (light)", showBackground = true)
@Composable
private fun ParkingSpotItemListLightPreview() {
    PaparcarTheme(darkTheme = false) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            ParkingSpotItem(
                data = sampleSpotData("1", "⛽ Repsol  ·  Av. Castellana 110", 203f, 0L),
                onClick = {},
            )
            HorizontalDivider(modifier = Modifier.padding(start = 70.dp, end = 16.dp))
            ParkingSpotItem(
                data = sampleSpotData("2", "Charleston Road 1500", 840f, 10L, SpotReliabilityUiState.MEDIUM),
                onClick = {},
                isSelected = true,
            )
            HorizontalDivider(modifier = Modifier.padding(start = 70.dp, end = 16.dp))
            ParkingSpotItem(
                data = sampleSpotData("3", "Calle Gran Vía 32", null, 25L, SpotReliabilityUiState.MANUAL),
                onClick = {},
            )
        }
    }
}
