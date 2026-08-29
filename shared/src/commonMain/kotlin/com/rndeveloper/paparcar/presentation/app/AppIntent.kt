package com.rndeveloper.paparcar.presentation.app

import com.rndeveloper.paparcar.domain.preferences.ThemeMode

sealed class AppIntent {
    data object MarkOnboardingCompleted : AppIntent()
    data object DismissGpsAccuracyDisclaimer : AppIntent()
    data class SetThemeMode(val mode: ThemeMode) : AppIntent()
    data class SetDistanceUnit(val imperial: Boolean) : AppIntent()
}
