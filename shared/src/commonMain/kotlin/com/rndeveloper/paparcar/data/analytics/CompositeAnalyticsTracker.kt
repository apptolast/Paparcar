package com.rndeveloper.paparcar.data.analytics

import com.rndeveloper.paparcar.domain.analytics.AnalyticsEvent
import com.rndeveloper.paparcar.domain.analytics.AnalyticsTracker
import com.rndeveloper.paparcar.domain.analytics.AnalyticsUserProperty

/**
 * Un hecho, varios destinos: Firebase Analytics y —en Android, y sólo con consentimiento— el
 * carril de atribución publicitaria. [MKT-META-APP-EVENTS-COST-THE-NO-ADS-PROMISE-001]
 *
 * Existe para que el punto de estrangulamiento siga siendo UNO. La alternativa era que cada use
 * case que ya llama a `analytics.track(...)` llamara también al segundo sink, y entonces "qué se
 * cuenta" dependería de cuántas llamadas recordó quien tocó el fichero — que es exactamente la
 * deriva que `AnalyticsTracker` existe para evitar [ANALYTICS-EVENTS-001].
 *
 * El `runCatching` por sink no es redundante con el que ya tiene cada implementación: lo que
 * protege aquí es **al resto de la lista**. Un SDK que lanza al cargarse (una clase que falta tras
 * un R8 mal configurado) se lleva por delante a los trackers que vinieran detrás, y el orden de
 * una lista no debería decidir si Firebase recibe un evento.
 */
class CompositeAnalyticsTracker(
    private val trackers: List<AnalyticsTracker>,
) : AnalyticsTracker {

    override fun track(event: AnalyticsEvent) {
        trackers.forEach { tracker ->
            runCatching { tracker.track(event) }
        }
    }

    override fun setUserProperty(property: AnalyticsUserProperty) {
        trackers.forEach { tracker ->
            runCatching { tracker.setUserProperty(property) }
        }
    }
}
