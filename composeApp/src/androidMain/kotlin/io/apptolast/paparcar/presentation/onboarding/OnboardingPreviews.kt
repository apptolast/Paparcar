package io.apptolast.paparcar.presentation.onboarding

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.apptolast.paparcar.ui.theme.PaparcarTheme

@Preview(name = "Onboarding · Light", showBackground = true, showSystemUi = true)
@Composable
private fun OnboardingLightPreview() {
    PaparcarTheme(darkTheme = false) {
        OnboardingScreen(onComplete = {})
    }
}

@Preview(
    name = "Onboarding · Dark",
    showBackground = true,
    showSystemUi = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun OnboardingDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        OnboardingScreen(onComplete = {})
    }
}
