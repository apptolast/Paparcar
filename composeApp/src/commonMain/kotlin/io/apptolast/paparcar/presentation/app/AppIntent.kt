package io.apptolast.paparcar.presentation.app

sealed class AppIntent {
    data object MarkOnboardingCompleted : AppIntent()
}
