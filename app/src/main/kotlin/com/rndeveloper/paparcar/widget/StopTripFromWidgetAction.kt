package com.rndeveloper.paparcar.widget

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.rndeveloper.paparcar.detection.service.CoordinatorDetectionService
import com.rndeveloper.paparcar.domain.util.PaparcarLogger

/**
 * «No voy en mi coche» desde el widget: mata la detección de ESTE viaje sin abrir la app.
 * [WIDGET-A-TRIP-CAN-BE-ANSWERED-FROM-THE-LAUNCHER-001]
 *
 * ## Por qué es un comando y no un `actionStartActivity`
 *
 * Los otros tres verbos del widget —navegar, ver ficha, ya he aparcado— **abren la app**, porque
 * los tres terminan en una pantalla donde el usuario sigue decidiendo. Éste no: no hay nada más
 * que decidir. El conductor ya sabe que no va en su coche y lo único que quiere es que la app deje
 * de seguirle. Mandarlo a Home para pulsar otra vez sería cobrarle dos gestos por una sola
 * decisión, y encima con la app estorbando justo cuando va en el coche de otro.
 *
 * ## Es el MISMO verbo que la card y la notificación, no uno nuevo
 *
 * Despacha `ACTION_USER_STOP`, exactamente lo que hacen el botón de la tarjeta del vehículo y la
 * acción de la notificación. Las tres superficies mandan el mismo intent al mismo sitio, así que no
 * pueden divergir en lo que significan: el viaje se cierra **sin guardar plaza** y queda un periodo
 * de silencio en el que los avisos automáticos no vuelven a armar. Un verbo nuevo aquí habría sido
 * una cuarta definición de «parar», que es la clase de cosa que este proyecto paga cara.
 *
 * ## `startService`, y a propósito
 *
 * ⛔ **No `startForegroundService`.** Este botón sólo se dibuja en el estado `Driving`, y ese estado
 * existe porque hay un viaje EN CURSO: el servicio está vivo y en primer plano. Promoverlo otra vez
 * sería pedirle una promesa de `startForeground()` a un servicio al que le estamos pidiendo
 * justamente que pare — y una promesa incumplida ahí es un ANR.
 *
 * Y si el servicio hubiera muerto entre el pintado del widget y el dedo, `startService` lanza desde
 * fondo. Por eso el `runCatching`: **no hay nada que parar**, que es exactamente la misma respuesta
 * que da el servicio a un tap rancio (`handleUserStop` sale por `detectionJob?.isActive != true`).
 * Tragarse esa excepción no oculta un fallo: dice lo mismo por otra vía.
 */
class StopTripFromWidgetAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val intent = Intent(context, CoordinatorDetectionService::class.java).apply {
            action = CoordinatorDetectionService.ACTION_USER_STOP
        }
        runCatching { context.startService(intent) }
            .onSuccess {
                PaparcarLogger.d(TAG, "→ USER_STOP desde el widget — el viaje se cierra sin plaza")
            }
            .onFailure { e ->
                // El servicio ya no estaba: no hay viaje que parar y el widget se redibuja solo.
                PaparcarLogger.d(TAG, "⊘ USER_STOP desde el widget sin servicio vivo — nada que parar (${e::class.simpleName})")
            }
        // Repintar ESTE widget ya, sin esperar al refresco periódico: el estado deja de ser Driving
        // y el botón que se acaba de pulsar tiene que desaparecer con él. Un botón que sigue ahí
        // después de pulsarlo se lee como que no ha pasado nada, y aquí sí ha pasado.
        ParkedWidget().update(context, glanceId)
    }

    private companion object {
        /** PARKDIAG prefix: FileAntilog sólo persiste las líneas con esta etiqueta. */
        const val TAG = "PARKDIAG/Widget"
    }
}
