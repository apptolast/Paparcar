package com.rndeveloper.paparcar.data.analytics

import android.app.Application
import android.content.Context
import com.facebook.FacebookSdk
import com.facebook.appevents.AppEventsLogger
import com.rndeveloper.paparcar.domain.util.PaparcarLogger

/**
 * El interruptor del SDK de Meta, y el ÚNICO sitio del repo que lo enciende.
 * [MKT-META-APP-EVENTS-COST-THE-NO-ADS-PROMISE-001]
 *
 * Dos pasos a propósito, porque son dos momentos distintos:
 *
 * - [configure] corre en `PaparcarApp.onCreate`, antes de Koin, y **no manda nada**: sólo deja los
 *   ids puestos y los tres automatismos del SDK APAGADOS. El manifest ya los apaga
 *   (`AutoInitEnabled`, `AutoLogAppEventsEnabled`, `AdvertiserIDCollectionEnabled` a `false`); esto
 *   lo repite en código porque un `meta-data` perdido en una fusión de manifiestos no avisa, y el
 *   fallo sería silencioso y en la dirección mala.
 * - [activate] enciende el carril, y sólo se llama con el consentimiento legal ya aceptado.
 *   Idempotente: el primer `activateApp` es el que atribuye la instalación, los siguientes no
 *   duplican nada.
 *
 * ⛔ **El identificador de publicidad se recoge aquí y en ningún otro sitio.** El permiso
 * `com.google.android.gms.permission.AD_ID` vuelve al manifest por este carril, y por eso Firebase
 * Analytics lo sigue teniendo prohibido con `google_analytics_adid_collection_enabled=false`: los
 * eventos de producto se quedan anclados al app-instance id, como estaban
 * [ANALYTICS-EVENTS-001]. Un permiso declarado no es un permiso usado por todos.
 */
object MetaAdAttribution {

    @Volatile
    private var configured = false

    @Volatile
    private var activated = false

    /** Hay ids y el SDK los tiene puestos. No implica que se haya mandado nada. */
    val isConfigured: Boolean get() = configured

    /** El carril está encendido: hubo consentimiento y el SDK se inicializó. */
    val isActivated: Boolean get() = activated

    /**
     * Deja el SDK listo y mudo. Con [applicationId] en blanco no configura nada y el carril queda
     * muerto para siempre en ese proceso — es el caso de un clon sin claves y del CI, que no
     * pueden crashear ni mandar eventos de prueba a la cuenta de anuncios.
     */
    fun configure(applicationId: String, clientToken: String) {
        if (applicationId.isBlank()) {
            PaparcarLogger.w(TAG, "FACEBOOK_APP_ID en blanco — carril de atribución apagado")
            return
        }
        runCatching {
            FacebookSdk.setApplicationId(applicationId)
            if (clientToken.isNotBlank()) {
                FacebookSdk.setClientToken(clientToken)
            }
            FacebookSdk.setAutoInitEnabled(false)
            FacebookSdk.setAutoLogAppEventsEnabled(false)
            FacebookSdk.setAdvertiserIDCollectionEnabled(false)
            configured = true
        }.onFailure { e ->
            PaparcarLogger.w(TAG, "no se pudo configurar el SDK de Meta: ${e.message}")
        }
    }

    /**
     * Enciende el carril si está configurado. Devuelve si quedó encendido, para que quien llama
     * pueda rendirse sin preguntar dos veces.
     *
     * ⛔ Quien llama es el dueño del consentimiento: esta función **no** lo consulta, porque
     * entonces habría dos sitios que deciden lo mismo. El único que la llama con la respuesta ya
     * en la mano es [MetaAnalyticsTracker] (y `PaparcarApp` en el arranque).
     */
    fun activate(context: Context): Boolean {
        if (!configured) return false
        if (activated) return true
        val application = context.applicationContext as? Application ?: return false
        return runCatching {
            FacebookSdk.setAutoInitEnabled(true)
            FacebookSdk.fullyInitialize()
            FacebookSdk.setAutoLogAppEventsEnabled(true)
            FacebookSdk.setAdvertiserIDCollectionEnabled(true)
            AppEventsLogger.activateApp(application)
            activated = true
            PaparcarLogger.d(TAG, "carril de atribución encendido")
            true
        }.getOrElse { e ->
            PaparcarLogger.w(TAG, "no se pudo encender el carril de atribución: ${e.message}")
            false
        }
    }

    private const val TAG = "MetaAdAttribution"
}
