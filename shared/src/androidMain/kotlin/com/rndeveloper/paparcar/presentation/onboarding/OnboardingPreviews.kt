package com.rndeveloper.paparcar.presentation.onboarding

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.rndeveloper.paparcar.ui.theme.PaparcarTheme

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

@Preview(name = "Onboarding · What it does for you", showBackground = true, showSystemUi = true)
@Composable
private fun OnboardingValuePagePreview() {
    PaparcarTheme(darkTheme = false) {
        OnboardingScreen(onComplete = {}, initialPage = 1)
    }
}

@Preview(name = "Onboarding · What it does for you · small phone", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun OnboardingValuePageSmallPreview() {
    PaparcarTheme(darkTheme = false) {
        OnboardingScreen(onComplete = {}, initialPage = 1)
    }
}
