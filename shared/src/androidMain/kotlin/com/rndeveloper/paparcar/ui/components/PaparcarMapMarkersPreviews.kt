package com.rndeveloper.paparcar.ui.components

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
import com.rndeveloper.paparcar.domain.model.CarbodyType
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.domain.model.ZoneIcon
import com.rndeveloper.paparcar.domain.model.SpotFreshness
import com.rndeveloper.paparcar.presentation.util.zoneIconFor
import com.rndeveloper.paparcar.ui.theme.PaparcarTheme

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

        // [UI-MAP-PUCK-BELONGS-TO-THE-DRIVE-NOT-TO-ONE-LANE-001] The driving puck had no preview and
        // no gallery entry — the only marker on the map you could not look at without starting a
        // trip, which is exactly why nobody noticed it read small. Headings are the four cardinals
        // so the rotation is checkable at a glance.
        SectionLabel("Driving puck — N · E · S · W (hatchback · SUV · van)")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            LocationActiveMarker(
                carbody = CarbodyType.HATCHBACK_MEDIUM,
                size = VehicleSize.MEDIUM_SUV,
                headingDegrees = 0f,
            )
            LocationActiveMarker(
                carbody = CarbodyType.SUV_MEDIUM,
                size = VehicleSize.MEDIUM_SUV,
                headingDegrees = 90f,
            )
            LocationActiveMarker(
                carbody = CarbodyType.VAN_LIGHT,
                size = VehicleSize.VAN_HIGH,
                headingDegrees = 180f,
            )
            LocationActiveMarker(carbody = null, size = null, headingDegrees = 270f)
        }

        // [UI-A-SAVED-ZONE-WEARS-ITS-DOUBT-TOO-001] The three dresses side by side — this row is the
        // whole point of the ticket, and it did not exist: the ask state shipped with a gallery
        // entry but no preview, so the one comparison that decides the design (are these three
        // telling me three different things?) could only be made on a phone. The order is the
        // user's story: it's here → is it here? → it's somewhere around here.
        SectionLabel("VehicleBadge — exact · asking (open question) · saved AREA")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
            VehicleBadgeMarker(carbodyType = CarbodyType.SUV_MEDIUM)
            VehicleBadgeMarker(carbodyType = CarbodyType.SUV_MEDIUM, doubt = VehicleMarkerDoubt.ASKING)
            VehicleBadgeMarker(carbodyType = CarbodyType.SUV_MEDIUM, doubt = VehicleMarkerDoubt.APPROXIMATE)
        }

        // The doubt badge is knocked out of the identity colour, so it has to hold up in all three
        // tones — grey is the one that can go quiet against a tile. [UI-COLOR-DOCTRINE-001]
        SectionLabel("VehicleBadge · saved AREA — watched · Bluetooth · unwatched · selected")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
            VehicleBadgeMarker(carbodyType = CarbodyType.SEDAN, doubt = VehicleMarkerDoubt.APPROXIMATE)
            VehicleBadgeMarker(
                carbodyType = CarbodyType.SEDAN,
                isBluetoothPaired = true,
                doubt = VehicleMarkerDoubt.APPROXIMATE,
            )
            VehicleBadgeMarker(
                carbodyType = CarbodyType.SEDAN,
                isActive = false,
                doubt = VehicleMarkerDoubt.APPROXIMATE,
            )
            VehicleBadgeMarker(
                carbodyType = CarbodyType.SEDAN,
                selected = true,
                doubt = VehicleMarkerDoubt.APPROXIMATE,
            )
        }

        SectionLabel("FreeSpot — HIGH · MEDIUM · LOW · MANUAL · selected")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            FreeSpotMarker(reliability = SpotFreshness.FRESH)
            FreeSpotMarker(reliability = SpotFreshness.RECENT)
            FreeSpotMarker(reliability = SpotFreshness.STALE)
            FreeSpotMarker(isManual = true)
            FreeSpotMarker(reliability = SpotFreshness.FRESH, selected = true)
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
