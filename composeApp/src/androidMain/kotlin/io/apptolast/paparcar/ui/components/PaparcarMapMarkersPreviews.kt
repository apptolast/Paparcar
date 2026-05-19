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
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.ZoneIcon
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
        SectionLabel("VehicleBadge — default · selected (amber, DirectionsCar)")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            VehicleBadgeMarker()
            VehicleBadgeMarker(selected = true)
        }

        SectionLabel("MyVehicle — default · selected (legacy teardrop)")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MyVehicleMarker()
            MyVehicleMarker(selected = true)
        }

        SectionLabel("FreeSpot — default · selected (green, LocalParking)")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            FreeSpotMarker()
            FreeSpotMarker(selected = true)
        }

        SectionLabel("Cluster (3 · 12 · 99+)")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FreeSpotClusterMarker(count = 3)
            FreeSpotClusterMarker(count = 12)
            FreeSpotClusterMarker(count = 250)
        }

        SectionLabel("Zone marker — 8 presets (surfaceContainer + primary icon)")
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ZoneIcon.PRESETS.take(4).forEach { key -> ZoneMarker(icon = zoneIconFor(key)) }
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ZoneIcon.PRESETS.drop(4).forEach { key -> ZoneMarker(icon = zoneIconFor(key)) }
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

        SectionLabel("Centre pin · Zone — 8 presets")
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ZoneIcon.PRESETS.take(4).forEach { key ->
                CenterPinSlot { ZoneCenterPin(icon = zoneIconFor(key), cameraMoving = false) }
            }
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ZoneIcon.PRESETS.drop(4).forEach { key ->
                CenterPinSlot { ZoneCenterPin(icon = zoneIconFor(key), cameraMoving = false) }
            }
        }

        SectionLabel("Centre pin · Zone (Home · rest vs lifted)")
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            CenterPinSlot { ZoneCenterPin(icon = zoneIconFor(ZoneIcon.HOME), cameraMoving = false) }
            CenterPinSlot { ZoneCenterPin(icon = zoneIconFor(ZoneIcon.HOME), cameraMoving = true) }
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
