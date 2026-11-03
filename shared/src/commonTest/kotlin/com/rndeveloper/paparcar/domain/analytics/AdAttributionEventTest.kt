package com.rndeveloper.paparcar.domain.analytics

import com.rndeveloper.paparcar.domain.detection.sentry.TriggerDisposition
import com.rndeveloper.paparcar.domain.entitlement.PaywallReason
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.SpotType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * La lista blanca de lo que sale hacia la red publicitaria, comprobada en LAS DOS direcciones:
 * lo que sale, y lo que NO. [MKT-META-APP-EVENTS-COST-THE-NO-ADS-PROMISE-001]
 *
 * La mitad de los `null` es la que importa: un `when` exhaustivo obliga a decidir, pero nada
 * impide decidir mal, y un evento de mecánica de detección que se escape hacia un anunciante no
 * rompe nada — se manda en silencio y la política publicada pasa a mentir.
 */
class AdAttributionEventTest {

    @Test
    fun should_crossToTheAdNetwork_when_theEventIsAnAchievement() {
        assertEquals(AdAttributionEvent.PARKING_CONFIRMED, PARKED.asAdAttributionEvent())
        assertEquals(AdAttributionEvent.SPOT_RELEASED, RELEASED.asAdAttributionEvent())
        assertEquals(AdAttributionEvent.PAYWALL_SHOWN, PAYWALL.asAdAttributionEvent())
    }

    @Test
    fun should_stayOnTheDevice_when_theEventIsDetectionMechanics() {
        assertNull(TRIGGER_FIRED.asAdAttributionEvent())
        assertNull(SESSION_ENDED.asAdAttributionEvent())
        assertNull(SPOT_REPORTED.asAdAttributionEvent())
    }

    /**
     * El testigo. Si alguien añade un `AnalyticsEvent`, el `when` del mapeo no compila y hay que
     * decidir — pero si lo añade a [AdAttributionEvent], nada obliga a volver aquí. Este suelo es
     * lo que convierte "se añadió un evento que sale del móvil" en un test rojo.
     */
    @Test
    fun should_shipExactlyThreeNamesToTheAdNetwork_when_countingTheVocabulary() {
        assertEquals(
            listOf("parking_confirmed", "spot_released", "paywall_shown"),
            AdAttributionEvent.entries.map { it.wire },
            "El vocabulario que ve un anunciante cambió. Cada nombre nuevo es un hecho más que un " +
                "tercero sabe de nuestros usuarios: tiene que estar defendido en " +
                "hosting/public/privacy-policy.html y en docs/legal/DATA-SAFETY-FORM.md antes de " +
                "entrar aquí.",
        )
    }

    /**
     * Y el otro testigo: que los eventos de arriba sean TODOS los que existen. El `when` de
     * [caseName] es exhaustivo, así que añadir un `AnalyticsEvent` **no compila** hasta que se le
     * da nombre aquí, y entonces este test cae hasta que además se le da un caso. Sin esto, un
     * evento nuevo podría estar saliendo hacia Meta sin que ningún test lo nombre.
     */
    @Test
    fun should_coverEveryAnalyticsEvent_when_listingTheOnesUnderTest() {
        val underTest = listOf(PARKED, RELEASED, PAYWALL, TRIGGER_FIRED, SESSION_ENDED, SPOT_REPORTED)
        assertEquals(
            ALL_CASES.sorted(),
            underTest.map { it.caseName() }.sorted(),
            "Hay un AnalyticsEvent sin fila en este test. Decide si sale del móvil hacia un " +
                "anunciante — y si sale, defiéndelo antes en hosting/public/privacy-policy.html y " +
                "en docs/legal/DATA-SAFETY-FORM.md.",
        )
    }

    /** Exhaustivo a propósito: es el que no deja añadir un evento en silencio. */
    private fun AnalyticsEvent.caseName(): String = when (this) {
        is AnalyticsEvent.ParkingSessionStarted -> "ParkingSessionStarted"
        is AnalyticsEvent.SpotReleased -> "SpotReleased"
        is AnalyticsEvent.PaywallShown -> "PaywallShown"
        is AnalyticsEvent.TriggerFired -> "TriggerFired"
        is AnalyticsEvent.ParkingSessionEnded -> "ParkingSessionEnded"
        is AnalyticsEvent.SpotReported -> "SpotReported"
    }

    private companion object {
        val ALL_CASES = listOf(
            "ParkingSessionStarted",
            "SpotReleased",
            "PaywallShown",
            "TriggerFired",
            "ParkingSessionEnded",
            "SpotReported",
        )

        val POINT = GpsPoint(40.4168, -3.7038, 10f, 1_000L, 0f)

        val PARKED = AnalyticsEvent.ParkingSessionStarted(
            location = POINT,
            spotType = SpotType.AUTO_DETECTED,
            detectionPath = "steps+egress",
            armEvidence = "GEOFENCE_EXIT",
        )
        val RELEASED = AnalyticsEvent.SpotReleased(location = POINT)
        val PAYWALL = AnalyticsEvent.PaywallShown(reason = PaywallReason.HISTORY)
        val TRIGGER_FIRED = AnalyticsEvent.TriggerFired(
            trigger = "GEOFENCE_EXIT",
            disposition = TriggerDisposition.ARMED,
        )
        val SESSION_ENDED = AnalyticsEvent.ParkingSessionEnded(
            location = POINT,
            reason = SessionEndReason.DEPARTURE_CONFIRMED,
        )
        val SPOT_REPORTED = AnalyticsEvent.SpotReported(location = POINT)
    }
}
