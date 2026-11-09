package com.rndeveloper.paparcar.data.analytics

import android.content.Context
import com.facebook.appevents.AppEventsLogger
import com.rndeveloper.paparcar.domain.analytics.AdAttributionTracker
import com.rndeveloper.paparcar.domain.analytics.AdMeasurementConsent
import com.rndeveloper.paparcar.domain.analytics.AdMeasurementLane
import com.rndeveloper.paparcar.domain.analytics.AnalyticsEvent
import com.rndeveloper.paparcar.domain.analytics.AnalyticsUserProperty
import com.rndeveloper.paparcar.domain.analytics.asAdAttributionEvent
import com.rndeveloper.paparcar.domain.preferences.AppPreferences
import com.rndeveloper.paparcar.domain.util.PaparcarLogger

/**
 * Meta App Events como [AdAttributionTracker] de Android — el carril que hace que un anuncio se
 * pueda atribuir. [MKT-META-APP-EVENTS-COST-THE-NO-ADS-PROMISE-001]
 *
 * Lo que sale de aquí es un NOMBRE de evento y nada más: la lista blanca la decide
 * [asAdAttributionEvent] en `commonMain`, donde se puede testear sin SDK ni device, y no hay
 * `Bundle` que mantener en sync con nadie. Ni celda, ni `detectionPath`, ni `armEvidence`.
 *
 * **Consentimiento**: mientras `AppPreferences.adMeasurementConsent` no sea `GRANTED`, el SDK no se
 * inicializa — así que no recoge el identificador de publicidad ni manda el evento de sesión. Es un
 * consentimiento PROPIO, pedido aparte del de la cuenta y revocable en Ajustes
 * [MKT-AD-MEASUREMENT-ASKS-ITS-OWN-CONSENT-001], y se lee en CADA llamada a propósito: un «no» en
 * Ajustes tiene que cortar el siguiente evento, no el del próximo arranque.
 *
 * Es también el [AdMeasurementLane] de Android: el mismo objeto que manda es el que se apaga, así
 * que no hay dos sitios que puedan discrepar sobre si el carril está vivo.
 *
 * Mismo contrato fire-and-forget que el resto: nunca lanza, nunca bloquea.
 */
class MetaAnalyticsTracker(
    private val context: Context,
    private val appPreferences: AppPreferences,
) : AdAttributionTracker, AdMeasurementLane {

    override val isAvailable: Boolean get() = MetaAdAttribution.isConfigured

    override fun onConsentChanged(consent: AdMeasurementConsent) {
        when (consent) {
            // Encender YA, no al primer evento: el `activateApp` es lo que atribuye la instalación,
            // y quien dice que sí y no aparca en esta sesión no debería esperar al arranque siguiente.
            AdMeasurementConsent.GRANTED -> MetaAdAttribution.activate(context)
            AdMeasurementConsent.DENIED, AdMeasurementConsent.UNASKED -> MetaAdAttribution.deactivate()
        }
    }

    override fun track(event: AnalyticsEvent) {
        val wire = event.asAdAttributionEvent()?.wire ?: return
        val logger = consentedLogger() ?: return
        runCatching { logger.logEvent(wire) }.onFailure { e ->
            PaparcarLogger.w(TAG, "evento de atribución descartado ($wire): ${e.message}")
        }
    }

    /**
     * Meta App Events no tiene propiedades de usuario, así que esto no manda nada — pero **sí
     * enciende el carril** si hay consentimiento, y eso no es un efecto colateral: es la señal más
     * temprana y más frecuente de la sesión (`PublishDetectionStateUseCase` la emite todo el rato
     * [DET-A-SWITCH-THAT-IS-OFF-LEAVES-NO-TRACE-001]).
     */
    override fun setUserProperty(property: AnalyticsUserProperty) {
        consentedLogger()
    }

    private fun consentedLogger(): AppEventsLogger? {
        if (!hasConsent()) return null
        if (!MetaAdAttribution.activate(context)) return null
        return runCatching { AppEventsLogger.newLogger(context) }.getOrNull()
    }

    private fun hasConsent(): Boolean =
        runCatching { appPreferences.adMeasurementConsent == AdMeasurementConsent.GRANTED }
            .getOrDefault(false)

    private companion object {
        const val TAG = "MetaAnalyticsTracker"
    }
}
