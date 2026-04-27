package io.apptolast.paparcar.presentation.home

import io.apptolast.paparcar.domain.error.PaparcarError

/**
 * Efectos de un solo uso para la pantalla Home.
 * Estos efectos se consumen una sola vez (navegación, snackbar, etc.).
 */
sealed class HomeEffect {
    data class ShowError(val error: PaparcarError) : HomeEffect()
    data object SpotReported : HomeEffect()
    data object TestSpotSent : HomeEffect()
    data object NavigateToHistory : HomeEffect()
    data object RequestLocationPermission : HomeEffect()
    data object SpotSignalSent : HomeEffect()
    data object OfflineActionBlocked : HomeEffect()
}