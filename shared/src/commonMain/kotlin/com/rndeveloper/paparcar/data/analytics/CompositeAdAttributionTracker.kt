package com.rndeveloper.paparcar.data.analytics

import com.rndeveloper.paparcar.domain.analytics.AdAttributionTracker
import com.rndeveloper.paparcar.domain.analytics.AdMeasurementConsent
import com.rndeveloper.paparcar.domain.analytics.AdMeasurementLane
import com.rndeveloper.paparcar.domain.analytics.AnalyticsEvent
import com.rndeveloper.paparcar.domain.analytics.AnalyticsUserProperty

/**
 * Un hecho, varias redes publicitarias — hoy Meta y TikTok, cada una detrás del MISMO
 * `AdMeasurementConsent` del usuario. [MKT-TIKTOK-APP-EVENTS-SDK-001]
 *
 * Mismo patrón que [CompositeAnalyticsTracker], un nivel más abajo: el punto de estrangulamiento
 * sigue siendo UNO ([AdAttributionTracker]), así que añadir una red nueva es añadir un elemento a
 * esta lista, no un segundo sitio que decida qué sale del móvil — eso sigue siendo
 * [com.rndeveloper.paparcar.domain.analytics.asAdAttributionEvent], compartido por todas las redes.
 *
 * También es el [AdMeasurementLane] compuesto: un solo «sí»/«no» del usuario enciende o apaga las
 * dos redes a la vez, delegando en la de cada una ([MetaAnalyticsTracker], [TikTokAnalyticsTracker]
 * — androidMain). [isAvailable] es cierto si AL MENOS una red tiene sus claves puestas, para que la
 * pregunta de consentimiento no dependa de que las dos estén configuradas.
 */
class CompositeAdAttributionTracker(
    private val trackers: List<AdAttributionTracker>,
) : AdAttributionTracker, AdMeasurementLane {

    private val lanes: List<AdMeasurementLane> = trackers.filterIsInstance<AdMeasurementLane>()

    override val isAvailable: Boolean get() = lanes.any { it.isAvailable }

    override fun onConsentChanged(consent: AdMeasurementConsent) {
        lanes.forEach { lane -> runCatching { lane.onConsentChanged(consent) } }
    }

    override fun track(event: AnalyticsEvent) {
        trackers.forEach { tracker -> runCatching { tracker.track(event) } }
    }

    override fun setUserProperty(property: AnalyticsUserProperty) {
        trackers.forEach { tracker -> runCatching { tracker.setUserProperty(property) } }
    }
}
