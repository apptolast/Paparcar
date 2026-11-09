package com.rndeveloper.paparcar.domain.usecase.analytics

import com.rndeveloper.paparcar.domain.analytics.AdMeasurementConsent
import com.rndeveloper.paparcar.domain.analytics.NoAdMeasurementLane
import com.rndeveloper.paparcar.domain.analytics.shouldAskAdMeasurementConsent
import com.rndeveloper.paparcar.fakes.FakeAdMeasurementLane
import com.rndeveloper.paparcar.fakes.FakeAppPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [MKT-AD-MEASUREMENT-ASKS-ITS-OWN-CONSENT-001] */
class SetAdMeasurementConsentUseCaseTest {

    private val prefs = FakeAppPreferences()
    private val lane = FakeAdMeasurementLane()
    private val setConsent = SetAdMeasurementConsentUseCase(prefs, lane)

    @Test
    fun should_storeGrantedAndSwitchTheLaneOn_when_userAllows() {
        assertTrue(setConsent(granted = true).isSuccess)

        assertEquals(AdMeasurementConsent.GRANTED, prefs.adMeasurementConsent)
        assertEquals(listOf(AdMeasurementConsent.GRANTED), lane.received)
    }

    @Test
    fun should_storeDeniedAndSwitchTheLaneOff_when_userWithdraws() {
        setConsent(granted = true)

        setConsent(granted = false)

        assertEquals(AdMeasurementConsent.DENIED, prefs.adMeasurementConsent)
        assertEquals(AdMeasurementConsent.DENIED, lane.received.last())
    }

    @Test
    fun should_keepTheNoStored_when_theLaneFailsToSwitchOff() {
        // The preference is written FIRST: the tracker re-reads it on every event, so even a lane
        // that could not be told still sends nothing after a "no".
        lane.throwOnChange = true

        val result = setConsent(granted = false)

        assertTrue(result.isFailure)
        assertEquals(AdMeasurementConsent.DENIED, prefs.adMeasurementConsent)
    }

    @Test
    fun should_askOnlyOnce_when_thereIsALane() {
        assertTrue(shouldAskAdMeasurementConsent(lane, AdMeasurementConsent.UNASKED))
        assertFalse(shouldAskAdMeasurementConsent(lane, AdMeasurementConsent.GRANTED))
        assertFalse(shouldAskAdMeasurementConsent(lane, AdMeasurementConsent.DENIED))
    }

    @Test
    fun should_neverAsk_when_theBuildHasNoAdLane() {
        assertFalse(shouldAskAdMeasurementConsent(NoAdMeasurementLane, AdMeasurementConsent.UNASKED))
    }
}
