package io.apptolast.paparcar.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.ZoneIcon
import io.apptolast.paparcar.presentation.util.SpotReliabilityUiState
import io.apptolast.paparcar.presentation.util.zoneIconFor
import io.apptolast.paparcar.ui.theme.PaparcarTheme

@Preview(name = "Markers · Claro", showBackground = true, widthDp = 360)
@Composable
private fun MarkersLightPreview() {
    PaparcarTheme(darkTheme = false) { MarkersShowcase() }
}

@Preview(name = "Markers · Oscuro", showBackground = true, widthDp = 360,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MarkersDarkPreview() {
    PaparcarTheme(darkTheme = true) { MarkersShowcase() }
}

@Composable
private fun MarkersShowcase() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        SectionLabel("LicensePlate — no plate · with plate · selected")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            LicensePlateMarker()
            LicensePlateMarker(plateText = "1234ABC")
            LicensePlateMarker(plateText = "1234ABC", selected = true)
        }

        SectionLabel("MyVehicle — default · selected (legacy teardrop)")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MyVehicleMarker()
            MyVehicleMarker(selected = true)
        }

        SectionLabel("FreeSpot — HIGH · MEDIUM · LOW · MANUAL · selected")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            FreeSpotMarker(reliability = SpotReliabilityUiState.HIGH)
            FreeSpotMarker(reliability = SpotReliabilityUiState.MEDIUM)
            FreeSpotMarker(reliability = SpotReliabilityUiState.LOW)
            FreeSpotMarker(reliability = SpotReliabilityUiState.MANUAL)
            FreeSpotMarker(reliability = SpotReliabilityUiState.HIGH, selected = true)
        }

        SectionLabel("FreeSpot · en route — 2 · 5 · 9+")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            FreeSpotMarker(enRouteCount = 2)
            FreeSpotMarker(enRouteCount = 5)
            FreeSpotMarker(enRouteCount = 12)
            FreeSpotMarker(enRouteCount = 5, selected = true)
        }

        SectionLabel("Cluster (3 · 12 · 99+)")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FreeSpotClusterMarker(count = 3)
            FreeSpotClusterMarker(count = 12)
            FreeSpotClusterMarker(count = 250)
        }

        SectionLabel("Zone marker — area label (public · private)")
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ZoneMarker(name = "Casa", icon = zoneIconFor(ZoneIcon.HOME))
            ZoneMarker(name = "Trabajo", icon = zoneIconFor(ZoneIcon.WORK), isPrivate = true)
            ZoneMarker(name = "Gimnasio del barrio", icon = zoneIconFor(ZoneIcon.GYM))
        }

        SectionLabel("Centre pin · Report (rest · lifted)")
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            CenterPinSlot { ReportCenterPin(cameraMoving = false) }
            CenterPinSlot { ReportCenterPin(cameraMoving = true) }
        }

        SectionLabel("Centre pin · Parking (rest · lifted)")
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            CenterPinSlot { ParkingCenterPin(cameraMoving = false) }
            CenterPinSlot { ParkingCenterPin(cameraMoving = true) }
        }

        SectionLabel("Centre pin · Zone (rest · lifted)")
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            CenterPinSlot { ZoneCenterPin(icon = Icons.Rounded.LocalParking, cameraMoving = false) }
            CenterPinSlot { ZoneCenterPin(icon = Icons.Rounded.LocalParking, cameraMoving = true) }
        }
    }
}

@Composable
private fun CenterPinSlot(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.size(width = 64.dp, height = 96.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
