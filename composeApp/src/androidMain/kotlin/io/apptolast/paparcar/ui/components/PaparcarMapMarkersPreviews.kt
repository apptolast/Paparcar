package io.apptolast.paparcar.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.presentation.util.SpotReliabilityLevel
import io.apptolast.paparcar.ui.theme.PaparcarTheme

// ─────────────────────────────────────────────────────────────────────────────
// Visual reference for the Design System markers added in MARKERS-001.
//
// These render the Composables on a neutral background — what they look like
// off-map. The kmpmaps library captures the Composable as a bitmap when
// registered via `customMarkerContent`, so this preview is the closest we can
// get to "what the user will see on the map" without running the app.
//
// PaparcarMapView still uses the legacy MyCarMarkerContent / SpotMarkerContent.
// The swap will land in a follow-up commit once these visuals are validated.
// ─────────────────────────────────────────────────────────────────────────────

@Preview(name = "Markers · Claro", showBackground = true, widthDp = 360)
@Composable
private fun MarkersLightPreview() {
    PaparcarTheme(darkTheme = false) {
        MarkersShowcase()
    }
}

@Preview(name = "Markers · Oscuro", showBackground = true, widthDp = 360,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MarkersDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        MarkersShowcase()
    }
}

@Preview(name = "MyVehicle — sólo · Claro", showBackground = true, widthDp = 160)
@Composable
private fun MyVehiclePreview() {
    PaparcarTheme(darkTheme = false) {
        Row(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MyVehicleMarker()
            MyVehicleMarker(selected = true)
        }
    }
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
        SectionLabel("MyVehicle (default · selected)")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MyVehicleMarker()
            MyVehicleMarker(selected = true)
        }

        SectionLabel("FreeSpot — reliability variants")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FreeSpotMarker(reliability = SpotReliabilityLevel.HIGH)
            FreeSpotMarker(reliability = SpotReliabilityLevel.MEDIUM)
            FreeSpotMarker(reliability = SpotReliabilityLevel.LOW)
            FreeSpotMarker(reliability = SpotReliabilityLevel.MANUAL)
        }

        SectionLabel("FreeSpot — TTL ring (HIGH · 100% / 60% / 20%)")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FreeSpotMarker(reliability = SpotReliabilityLevel.HIGH, ttlProgress = 1.0f)
            FreeSpotMarker(reliability = SpotReliabilityLevel.HIGH, ttlProgress = 0.6f)
            FreeSpotMarker(reliability = SpotReliabilityLevel.HIGH, ttlProgress = 0.2f)
        }

        SectionLabel("FreeSpot — selected (with halo)")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FreeSpotMarker(reliability = SpotReliabilityLevel.HIGH,   selected = true)
            FreeSpotMarker(reliability = SpotReliabilityLevel.MEDIUM, selected = true)
            FreeSpotMarker(reliability = SpotReliabilityLevel.MANUAL, selected = true, ttlProgress = 0.7f)
        }

        SectionLabel("Cluster (3 · 12 · 99+)")
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FreeSpotClusterMarker(count = 3)
            FreeSpotClusterMarker(count = 12)
            FreeSpotClusterMarker(count = 250)
        }
    }
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
