package com.rndeveloper.paparcar.domain.usecase.analytics

import com.rndeveloper.paparcar.domain.analytics.AdMeasurementConsent
import com.rndeveloper.paparcar.domain.analytics.AdMeasurementLane
import com.rndeveloper.paparcar.domain.preferences.AppPreferences

/**
 * Records the user's answer about advertising measurement and puts the lane in step with it.
 * [MKT-AD-MEASUREMENT-ASKS-ITS-OWN-CONSENT-001]
 *
 * One door for both places that ask — the one-time prompt and the Settings switch — so a "no" in
 * either turns the lane off in the same way, right now, and not on the next cold start.
 *
 * The preference is written FIRST: the tracker re-reads it on every event, so even if the lane
 * call failed, nothing more would leave the phone after a "no".
 */
class SetAdMeasurementConsentUseCase(
    private val appPreferences: AppPreferences,
    private val lane: AdMeasurementLane,
) {
    operator fun invoke(granted: Boolean): Result<Unit> = runCatching {
        val consent = if (granted) AdMeasurementConsent.GRANTED else AdMeasurementConsent.DENIED
        appPreferences.setAdMeasurementConsent(consent)
        lane.onConsentChanged(consent)
    }
}
