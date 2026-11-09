package com.rndeveloper.paparcar.fakes

import com.rndeveloper.paparcar.domain.analytics.AdMeasurementConsent
import com.rndeveloper.paparcar.domain.analytics.AdMeasurementLane

/** An ad lane that records what it was told. [MKT-AD-MEASUREMENT-ASKS-ITS-OWN-CONSENT-001] */
class FakeAdMeasurementLane(
    override var isAvailable: Boolean = true,
) : AdMeasurementLane {
    val received = mutableListOf<AdMeasurementConsent>()
    var throwOnChange: Boolean = false

    override fun onConsentChanged(consent: AdMeasurementConsent) {
        if (throwOnChange) error("lane refused")
        received += consent
    }
}
