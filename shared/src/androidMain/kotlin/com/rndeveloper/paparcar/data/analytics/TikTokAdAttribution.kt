package com.rndeveloper.paparcar.data.analytics

import android.app.Application
import android.content.Context
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import com.tiktok.TikTokBusinessSdk

/**
 * El interruptor del SDK de TikTok, y el ÚNICO sitio del repo que lo enciende.
 * [MKT-TIKTOK-APP-EVENTS-SDK-001]
 *
 * ⛔ **No es el mismo reparto en dos pasos que [MetaAdAttribution].** El SDK de Meta deja
 * `configure()`/`activate()` separados porque se puede inicializar MUDO y des-mutear después
 * (`setAdvertiserIDCollectionEnabled(true)` en cualquier momento). El de TikTok no: su
 * `disableAdvertiserIDCollection()` sólo se decide en el momento de `initializeSdk`, que además
 * arranca solo el auto-tracking de Install/Launch salvo que se pida lo contrario — así que
 * "inicializar mudo y luego desmutear" dejaría el identificador de publicidad fuera para
 * siempre en ese proceso, o el auto-tracking disparando antes del consentimiento.
 *
 * Por eso aquí [configure] NO toca el SDK — sólo guarda las credenciales y comprueba que existen.
 * [activate] es la ÚNICA llamada a `TikTokBusinessSdk.initializeSdk`, y por diseño nunca ocurre sin
 * que `AppPreferences.adMeasurementConsent == GRANTED` ya esté en la mano de quien llama
 * ([TikTokAnalyticsTracker]). `startTrack()` es el propio método que la documentación del SDK
 * describe para este caso exacto: *"invoked after the user agrees to some terms or conditions, so
 * that events are not pushed to the network before user's consent"*.
 *
 * [deactivate] sí puede apagar del todo (`destroy()` deja `ttSdk` en null), a diferencia de Meta —
 * así que una revocación en Ajustes para TikTok es un apagado real, no sólo silenciar automatismos.
 */
object TikTokAdAttribution {

    @Volatile
    private var configured = false

    @Volatile
    private var activated = false

    private var appId: String = ""
    private var ttAppId: String = ""

    /** Hay ids y están puestos. No implica que se haya inicializado el SDK. */
    val isConfigured: Boolean get() = configured

    /** El carril está encendido: hubo consentimiento y el SDK ya se inicializó de verdad. */
    val isActivated: Boolean get() = activated

    /**
     * Guarda las credenciales. Con [appId] o [ttAppId] en blanco no hay nada que inicializar nunca
     * — es el caso de un clon sin claves y del CI, igual que [MetaAdAttribution.configure].
     */
    fun configure(appId: String, ttAppId: String) {
        if (appId.isBlank() || ttAppId.isBlank()) {
            PaparcarLogger.w(TAG, "TIKTOK_APP_ID o TIKTOK_TT_APP_ID en blanco — carril de TikTok apagado")
            return
        }
        this.appId = appId
        this.ttAppId = ttAppId
        configured = true
    }

    /**
     * Inicializa el SDK de verdad — la ÚNICA vez que esto puede pasar, porque quien llama
     * garantiza que ya hay consentimiento. Idempotente: una segunda llamada no reinicializa.
     */
    fun activate(context: Context): Boolean {
        if (!configured) return false
        if (activated) return true
        val application = context.applicationContext as? Application ?: return false
        return runCatching {
            val config = TikTokBusinessSdk.TTConfig(application, "")
                .setAppId(appId)
                .setTTAppId(ttAppId)
                // Paparcar only uses install, launch and the explicit attribution events.
                .disableRetentionLogging()
                .disableAutoIapTrack()
                .disableAutoEnhancedDataPostbackEvent()
                .disableMonitor()
            TikTokBusinessSdk.initializeSdk(config)
            // El doc del propio SDK: nada se manda a red hasta llamar esto. No hace falta
            // `disableAutoStart()` arriba porque nunca llegamos aquí sin consentimiento.
            TikTokBusinessSdk.startTrack()
            activated = true
            PaparcarLogger.d(TAG, "carril TikTok encendido")
            true
        }.getOrElse { e ->
            PaparcarLogger.w(TAG, "no se pudo encender el carril TikTok: ${e.message}")
            false
        }
    }

    /** Apaga el SDK del todo tras un «no» en Ajustes. Idempotente y nunca lanza. */
    fun deactivate() {
        if (!activated) return
        runCatching { TikTokBusinessSdk.destroy() }.onFailure { e ->
            PaparcarLogger.w(TAG, "no se pudo apagar el carril TikTok: ${e.message}")
        }
        activated = false
        PaparcarLogger.d(TAG, "carril TikTok apagado")
    }

    private const val TAG = "TikTokAdAttribution"
}
