package io.apptolast.paparcar.presentation.app

sealed class AppEffect {
    data object ShowConnectionRestored : AppEffect()
    data class ApplyLocale(val tag: String) : AppEffect()
}
