package com.rndeveloper.paparcar.data.analytics

import android.content.Context
import com.rndeveloper.paparcar.domain.analytics.AdAttributionTracker
import com.rndeveloper.paparcar.domain.analytics.AdMeasurementConsent
import com.rndeveloper.paparcar.domain.analytics.AdMeasurementLane
import com.rndeveloper.paparcar.domain.analytics.AnalyticsEvent
import com.rndeveloper.paparcar.domain.analytics.AnalyticsUserProperty
import com.rndeveloper.paparcar.domain.analytics.asAdAttributionEvent
import com.rndeveloper.paparcar.domain.preferences.AppPreferences
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import com.tiktok.TikTokBusinessSdk
import com.tiktok.appevents.base.TTBaseEvent

/**
 * TikTok App Events como [AdAttributionTracker] de Android — hermano de [MetaAnalyticsTracker],
 * mismo contrato. [MKT-TIKTOK-APP-EVENTS-SDK-001]
 *
 * Lo que sale de aquí es un NOMBRE de evento y nada más — la MISMA lista blanca que ya usa Meta
 * ([asAdAttributionEvent] en `commonMain`): `parking_confirmed`, `spot_released`, `paywall_shown`,
 * sin parámetros. No hay una segunda lista propia de TikTok: el vocabulario de lo que sale del
 * móvil hacia CUALQUIER red publicitaria es uno, por diseño.
 *
 * **Consentimiento**: igual que Meta, detrás de `AppPreferences.adMeasurementConsent == GRANTED`,
 * leído en CADA llamada — un «no» en Ajustes corta el siguiente evento, no el del próximo arranque.
 *
 * Es también el [AdMeasurementLane] de TikTok en Android.
 */
class TikTokAnalyticsTracker(
    private val context: Context,
    private val appPreferences: AppPreferences,
) : AdAttributionTracker, AdMeasurementLane {

    override val isAvailable: Boolean get() = TikTokAdAttribution.isConfigured

    override fun onConsentChanged(consent: AdMeasurementConsent) {
        when (consent) {
            AdMeasurementConsent.GRANTED -> TikTokAdAttribution.activate(context)
            AdMeasurementConsent.DENIED, AdMeasurementConsent.UNASKED -> TikTokAdAttribution.deactivate()
        }
    }

    override fun track(event: AnalyticsEvent) {
        val wire = event.asAdAttributionEvent()?.wire ?: return
        if (!hasConsent()) return
        if (!TikTokAdAttribution.activate(context)) return
        // Todos los overloads `trackEvent(String, …)` están deprecados en el SDK. `trackTTEvent`
        // con un `TTBaseEvent` propio (no uno de los `EventName` estándar, cerrados y ninguno
        // encaja con nuestro vocabulario) es la vía no deprecada — `newBuilder` deja las
        // propiedades vacías por defecto, que es justo "cero parámetros".
        runCatching { TikTokBusinessSdk.trackTTEvent(TTBaseEvent.newBuilder(wire).build()) }.onFailure { e ->
            PaparcarLogger.w(TAG, "evento de atribución TikTok descartado ($wire): ${e.message}")
        }
    }

    /**
     * TikTok no tiene propiedades de usuario propias — igual que Meta, esto no manda nada, pero
     * SÍ enciende el carril si hay consentimiento (la señal más temprana de la sesión).
     */
    override fun setUserProperty(property: AnalyticsUserProperty) {
        if (hasConsent()) TikTokAdAttribution.activate(context)
    }

    private fun hasConsent(): Boolean =
        runCatching { appPreferences.adMeasurementConsent == AdMeasurementConsent.GRANTED }
            .getOrDefault(false)

    private companion object {
        const val TAG = "TikTokAnalyticsTracker"
    }
}
