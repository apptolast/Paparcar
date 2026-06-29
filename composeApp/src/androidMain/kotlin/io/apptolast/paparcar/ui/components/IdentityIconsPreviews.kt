package io.apptolast.paparcar.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.presentation.util.SpotReliabilityUiState
import io.apptolast.paparcar.ui.illustrations.EmptySpotsIllustration
import io.apptolast.paparcar.ui.illustrations.LocationAlertIllustration
import io.apptolast.paparcar.ui.illustrations.OnboardingHero
import io.apptolast.paparcar.ui.theme.PaparcarTheme

@Composable
private fun IdentityIconsShowcase() {
    Surface {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("ReliabilityMeter", style = MaterialTheme.typography.titleSmall)
            SpotReliabilityUiState.entries.forEach { level ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ReliabilityMeter(level)
                    Text(level.name, style = MaterialTheme.typography.labelMedium)
                }
            }
            ReliabilityMeter(SpotReliabilityUiState.HIGH, pct = 0.6f)

            Text("Ilustraciones Nivel 3 (Canvas)", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LocationAlertIllustration(Modifier.size(96.dp, 82.dp))
                EmptySpotsIllustration(Modifier.size(96.dp, 82.dp))
            }

            Text("Onboarding heroes (VectorDrawable)", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OnboardingHero(OnboardingHero.WELCOME, Modifier.size(80.dp, 70.dp))
                OnboardingHero(OnboardingHero.HOW, Modifier.size(80.dp, 70.dp))
                OnboardingHero(OnboardingHero.PRIVACY, Modifier.size(80.dp, 70.dp))
                OnboardingHero(OnboardingHero.AUTOMATION, Modifier.size(80.dp, 70.dp))
            }
        }
    }
}

@Preview(name = "Identity · Claro", showBackground = true, widthDp = 360)
@Composable
private fun IdentityIconsLightPreview() {
    PaparcarTheme(darkTheme = false) { IdentityIconsShowcase() }
}

@Preview(name = "Identity · Oscuro", showBackground = true, widthDp = 360, backgroundColor = 0xFF0D1117)
@Composable
private fun IdentityIconsDarkPreview() {
    PaparcarTheme(darkTheme = true) { IdentityIconsShowcase() }
}
