package com.rndeveloper.paparcar.presentation.home.sections.sheet.components

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
import com.rndeveloper.paparcar.presentation.home.model.DetectionStory
import com.rndeveloper.paparcar.domain.detection.PendingPromptWindow
import com.rndeveloper.paparcar.presentation.home.model.ParkedWatchBadge
import com.rndeveloper.paparcar.ui.theme.PaparcarTheme

// One entry per story the surface can tell — action rows + discreet happy lines.
// [UX-DETECTION-STORY-001]
private val stories = listOf(
    "BlockedCore" to DetectionStory.BlockedCore,
    // [DET-ASK-STATE-001] The open question, named and generic — both wordings ship.
    "AwaitingAnswer · vehicle" to DetectionStory.AwaitingAnswer(PendingPromptWindow(shownAtMs = 0L, vehicleName = "Škoda Kamiq")),
    "AwaitingAnswer · generic" to DetectionStory.AwaitingAnswer(PendingPromptWindow(shownAtMs = 0L)),
    // [DET-NUDGE-PERSIST-001] Pending "where did you leave your car?" row.
    "PendingAsk" to DetectionStory.PendingAsk,
    "Inactive" to DetectionStory.Inactive,
    "NoVehicle" to DetectionStory.NoVehicle,
    "AwaitingFirstPark" to DetectionStory.AwaitingFirstPark,
    "Watching · parked" to DetectionStory.Watching("Škoda Kamiq", isParked = true, viaBluetooth = false),
    // [DET-WATCH-HONEST-001] Honest watch health: fragile (warns) + interrupted (OS killed the FGS).
    "Watching · fragile" to DetectionStory.Watching(
        "Škoda Kamiq", isParked = true, viaBluetooth = false, watchBadge = ParkedWatchBadge.WATCHING_FRAGILE,
    ),
    "Watching · interrupted" to DetectionStory.Watching(
        "Škoda Kamiq", isParked = true, viaBluetooth = false, watchBadge = ParkedWatchBadge.WATCH_INTERRUPTED,
    ),
    "Watching · BT armed" to DetectionStory.Watching("Škoda Kamiq", isParked = false, viaBluetooth = true),
    "Driving" to DetectionStory.Driving("Škoda Kamiq", isCandidate = false),
    "Driving · BT" to DetectionStory.Driving("Škoda Kamiq", isCandidate = false, viaBluetooth = true),
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
    }
}

@Preview(name = "Detection story · Dark", showBackground = true, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DetectionSurfaceDark() = PaparcarTheme(darkTheme = true) { Gallery() }

@Preview(name = "Detection story · Light", showBackground = true, heightDp = 900)
@Composable
private fun DetectionSurfaceLight() = PaparcarTheme(darkTheme = false) { Gallery() }
