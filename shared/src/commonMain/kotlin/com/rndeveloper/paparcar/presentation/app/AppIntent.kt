package com.rndeveloper.paparcar.presentation.app

import com.rndeveloper.paparcar.domain.preferences.ThemeMode

sealed class AppIntent {
    data object MarkOnboardingCompleted : AppIntent()
    data object DismissGpsAccuracyDisclaimer : AppIntent()

    /** El aviso in-app de plaza nueva se ha leído (o se ha agotado su tiempo).
     *  [ZONE-AN-ALERT-IS-DELIVERED-WHERE-YOU-CAN-SEE-IT-001] */
    data object DismissSpotAlert : AppIntent()
    /** The user checked the privacy row during email registration. */
    data object AcceptLegalConsent : AppIntent()
    data class SetThemeMode(val mode: ThemeMode) : AppIntent()
    data class SetDistanceUnit(val imperial: Boolean) : AppIntent()
}
