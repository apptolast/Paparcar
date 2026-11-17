package com.rndeveloper.paparcar.presentation.app

import com.rndeveloper.paparcar.domain.preferences.ThemeMode

sealed class AppIntent {
    data object MarkOnboardingCompleted : AppIntent()
    data object DismissGpsAccuracyDisclaimer : AppIntent()

    /** El aviso in-app de plaza nueva se ha leído (o se ha agotado su tiempo).
     *  [ZONE-AN-ALERT-IS-DELIVERED-WHERE-YOU-CAN-SEE-IT-001] */
    data object DismissSpotAlert : AppIntent()
    /** The user answered the one-time ad-measurement question — either answer closes it for good.
     *  [MKT-AD-MEASUREMENT-ASKS-ITS-OWN-CONSENT-001] */
    data class AnswerAdMeasurementConsent(val granted: Boolean) : AppIntent()
    data class SetThemeMode(val mode: ThemeMode) : AppIntent()
    data class SetDistanceUnit(val imperial: Boolean) : AppIntent()

    /** Whether ANY app UI is on screen right now, whatever tab — reported from the app's ROOT
     *  composable, outside any NavHost back-stack entry. [BATT-002] A sentry-resident FGS can keep
     *  this ViewModel alive for hours with no human looking; the zone-alert listener should not
     *  outlive that. */
    data class SetAppForeground(val visible: Boolean) : AppIntent()
}
