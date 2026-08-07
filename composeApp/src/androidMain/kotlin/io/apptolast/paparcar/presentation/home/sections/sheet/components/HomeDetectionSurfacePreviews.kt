package io.apptolast.paparcar.presentation.home.sections.sheet.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.presentation.home.model.DetectionStory
import io.apptolast.paparcar.ui.theme.PaparcarTheme

// One entry per story the surface can tell — action rows + discreet happy lines.
// [UX-DETECTION-STORY-001]
private val stories = listOf(
    "BlockedCore" to DetectionStory.BlockedCore,
    "Inactive" to DetectionStory.Inactive,
    "NoVehicle" to DetectionStory.NoVehicle,
    "AwaitingFirstPark" to DetectionStory.AwaitingFirstPark,
    "Watching · parked" to DetectionStory.Watching("Škoda Kamiq", isParked = true, viaBluetooth = false),
    "Watching · BT armed" to DetectionStory.Watching("Škoda Kamiq", isParked = false, viaBluetooth = true),
    "Driving" to DetectionStory.Driving("Škoda Kamiq", isCandidate = false),
    "Candidate" to DetectionStory.Driving("Škoda Kamiq", isCandidate = true),
)

@Composable
private fun Gallery() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        stories.forEach { (label, story) ->
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HomeDetectionSurface(
                story = story,
                onAddVehicle = {},
                onOpenPermissions = {},
                onMarkSpot = {},
                onStartDrivingDetection = {},
                onActivateDetection = {},
                allowDrivingDetection = true, // preview shows the full two-CTA cold-start layout
            )
        }
        // [DET-NUDGE-PERSIST-001] Pending "where did you leave your car?" row.
        Text("ParkNudge", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HomeDetectionSurface(
            story = DetectionStory.Hidden,
            onAddVehicle = {},
            onOpenPermissions = {},
            onMarkSpot = {},
            onStartDrivingDetection = {},
            onActivateDetection = {},
            showParkNudge = true,
        )
    }
}

@Preview(name = "Detection story · Dark", showBackground = true, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DetectionSurfaceDark() = PaparcarTheme(darkTheme = true) { Gallery() }

@Preview(name = "Detection story · Light", showBackground = true, heightDp = 900)
@Composable
private fun DetectionSurfaceLight() = PaparcarTheme(darkTheme = false) { Gallery() }
