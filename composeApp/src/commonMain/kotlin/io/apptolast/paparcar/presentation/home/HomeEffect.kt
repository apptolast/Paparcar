package io.apptolast.paparcar.presentation.home

/**
 * Efectos de un solo uso para la pantalla Home.
 * Estos efectos se consumen una sola vez (navegación, snackbar, etc.).
 */
sealed class HomeEffect {
    data class ShowError(val message: String) : HomeEffect()
    data class ShowSuccess(val message: String) : HomeEffect()
    data object NavigateToMap : HomeEffect()
    data object NavigateToHistory : HomeEffect()
    data object RequestLocationPermission : HomeEffect()
}
