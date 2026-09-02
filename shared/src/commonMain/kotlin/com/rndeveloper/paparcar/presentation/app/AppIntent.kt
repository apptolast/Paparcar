package com.rndeveloper.paparcar.presentation.app

import com.rndeveloper.paparcar.domain.preferences.ThemeMode

sealed class AppIntent {
    data object MarkOnboardingCompleted : AppIntent()
    data object DismissGpsAccuracyDisclaimer : AppIntent()
    /** The user ticked "I accept the Privacy Policy" on the auth flow — persist it so the
     *  checkbox is asked once per install. [AUTH-A-SIGN-IN-ASKS-FOR-CONSENT-FIRST-001] */
    data object AcceptLegalConsent : AppIntent()
    data class SetThemeMode(val mode: ThemeMode) : AppIntent()
    data class SetDistanceUnit(val imperial: Boolean) : AppIntent()
}
