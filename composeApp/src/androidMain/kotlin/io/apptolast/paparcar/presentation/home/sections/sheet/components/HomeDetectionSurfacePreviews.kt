package io.apptolast.paparcar.presentation.home.sections.sheet.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import io.apptolast.paparcar.domain.detection.DetectionPhase
import io.apptolast.paparcar.presentation.home.model.DetectionUiState
import io.apptolast.paparcar.presentation.home.sections.map.components.MonitoringPillContent
import io.apptolast.paparcar.ui.theme.PaparcarTheme

private val actionStates = listOf(
    "BlockedCore" to DetectionUiState.BlockedCore,
    "Inactive" to DetectionUiState.Inactive,
    "NoVehicle" to DetectionUiState.NoVehicle,
    "AwaitingFirstPark" to DetectionUiState.AwaitingFirstPark,
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
        actionStates.forEach { (label, state) ->
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HomeDetectionSurface(
                state = state,
                onAddVehicle = {},
                onOpenPermissions = {},
                onMarkSpot = {},
                onStartDrivingDetection = {},
                onActivateDetection = {},
                allowDrivingDetection = true, // preview shows the full two-CTA cold-start layout
            )
        }
        Text("Monitoring pill · driving", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            MonitoringPillContent(elapsedLabel = "4 min")
        }
        Text("Monitoring pill · parking", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            MonitoringPillContent(elapsedLabel = "4 min", phase = DetectionPhase.Candidate)
        }
    }
}

@Preview(name = "Detection surface · Dark", showBackground = true, heightDp = 560, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DetectionSurfaceDark() = PaparcarTheme(darkTheme = true) { Gallery() }

@Preview(name = "Detection surface · Light", showBackground = true, heightDp = 560)
@Composable
private fun DetectionSurfaceLight() = PaparcarTheme(darkTheme = false) { Gallery() }
