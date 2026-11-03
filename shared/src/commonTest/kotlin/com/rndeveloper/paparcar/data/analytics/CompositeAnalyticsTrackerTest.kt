package com.rndeveloper.paparcar.data.analytics

import com.rndeveloper.paparcar.domain.analytics.AnalyticsEvent
import com.rndeveloper.paparcar.domain.analytics.AnalyticsTracker
import com.rndeveloper.paparcar.domain.analytics.AnalyticsUserProperty
import com.rndeveloper.paparcar.domain.detection.DetectionStateLabel
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.SpotType
import com.rndeveloper.paparcar.fakes.FakeAnalyticsTracker
import kotlin.test.Test
import kotlin.test.assertEquals

class CompositeAnalyticsTrackerTest {

    @Test
    fun should_forwardEventsAndUserPropertiesToAllTrackers_when_tracked() {
        val first = FakeAnalyticsTracker()
        val second = FakeAnalyticsTracker()
        val composite = CompositeAnalyticsTracker(listOf(first, second))

        composite.track(PARKED)

        assertEquals(listOf<AnalyticsEvent>(PARKED), first.events)
        assertEquals(listOf<AnalyticsEvent>(PARKED), second.events)

        val property = AnalyticsUserProperty.DetectionState(label = DetectionStateLabel.ON)
        composite.setUserProperty(property)

        assertEquals(listOf<AnalyticsUserProperty>(property), first.properties)
        assertEquals(listOf<AnalyticsUserProperty>(property), second.properties)
    }

    /**
     * El contrato que de verdad justifica la clase: el orden de la lista no decide quién cuenta.
     * Sin el `runCatching` por sink, un SDK que lanza al cargarse dejaría a Firebase sin el evento
     * sólo por venir detrás en la lista.
     */
    @Test
    fun should_keepFeedingTheRest_when_oneTrackerThrows() {
        val survivor = FakeAnalyticsTracker()
        val composite = CompositeAnalyticsTracker(listOf(ThrowingTracker, survivor))

        composite.track(PARKED)
        composite.setUserProperty(AnalyticsUserProperty.DetectionState(DetectionStateLabel.ON))

        assertEquals(listOf<AnalyticsEvent>(PARKED), survivor.events)
        assertEquals(1, survivor.properties.size)
    }

    private object ThrowingTracker : AnalyticsTracker {
        override fun track(event: AnalyticsEvent): Unit = throw IllegalStateException("SDK roto")
        override fun setUserProperty(property: AnalyticsUserProperty): Unit =
            throw IllegalStateException("SDK roto")
    }

    private companion object {
        val PARKED = AnalyticsEvent.ParkingSessionStarted(
            location = GpsPoint(40.4168, -3.7038, 10f, 1000L, 0f),
            spotType = SpotType.AUTO_DETECTED,
            detectionPath = "bt",
            armEvidence = "BT_DISCONNECT",
        )
    }
}
