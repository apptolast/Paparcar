package com.rndeveloper.paparcar.data.analytics

import android.content.Context
import com.facebook.appevents.AppEventsLogger
import com.rndeveloper.paparcar.domain.analytics.AdAttributionTracker
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
 * **Consentimiento**: mientras `AppPreferences.hasAcceptedLegalConsent` sea `false`, el SDK no se
 * inicializa — así que no recoge el identificador de publicidad ni manda el evento de sesión. La
 * puerta es la que ya bloquea el sign-in [AUTH-A-SIGN-IN-ASKS-FOR-CONSENT-FIRST-001], y se lee en
 * CADA llamada a propósito: la aceptación llega a mitad de sesión (en la pantalla de auth), y un
 * flag cacheado al arrancar dejaría el carril apagado hasta el siguiente arranque.
 *
 * Mismo contrato fire-and-forget que el resto: nunca lanza, nunca bloquea.
 */
class MetaAnalyticsTracker(
    private val context: Context,
    private val appPreferences: AppPreferences,
) : AdAttributionTracker {

    override fun track(event: AnalyticsEvent) {
        val wire = event.asAdAttributionEvent()?.wire ?: return
        val logger = consentedLogger() ?: return
        runCatching { logger.logEvent(wire) }.onFailure { e ->
            PaparcarLogger.w(TAG, "evento de atribución descartado ($wire): ${e.message}")
        }
    }

    /**
     * Meta App Events no tiene propiedades de usuario, así que esto no manda nada — pero **sí
     * enciende el carril**, y eso no es un efecto colateral: es la señal más temprana y más
     * frecuente que llega tras el consentimiento (`PublishDetectionStateUseCase` la emite durante
     * toda la sesión [DET-A-SWITCH-THAT-IS-OFF-LEAVES-NO-TRACE-001]). Sin esto, quien acepta y no
     * aparca en esa sesión no atribuye su instalación hasta el arranque siguiente.
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
        runCatching { appPreferences.hasAcceptedLegalConsent }.getOrDefault(false)

    private companion object {
        const val TAG = "MetaAnalyticsTracker"
    }
}
