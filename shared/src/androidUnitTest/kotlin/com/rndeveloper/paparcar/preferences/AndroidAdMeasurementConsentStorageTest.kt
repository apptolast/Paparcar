package com.rndeveloper.paparcar.preferences

import com.rndeveloper.paparcar.domain.analytics.AdMeasurementConsent
import org.junit.Test
import kotlin.test.assertEquals

class AndroidAdMeasurementConsentStorageTest {

    @Test
    fun should_askAgain_when_existingGrantOnlyCoveredMeta() {
        assertEquals(AdMeasurementConsent.UNASKED, AndroidAdMeasurementConsentStorage.decode("GRANTED"))
    }

    @Test
    fun should_preserveDenial_when_userPreviouslyDeclined() {
        assertEquals(AdMeasurementConsent.DENIED, AndroidAdMeasurementConsentStorage.decode("DENIED"))
    }

    @Test
    fun should_restoreCurrentChoice_when_consentIsSaved() {
        AdMeasurementConsent.entries.forEach { consent ->
            assertEquals(consent, AndroidAdMeasurementConsentStorage.decode(AndroidAdMeasurementConsentStorage.encode(consent)))
        }
    }
}
