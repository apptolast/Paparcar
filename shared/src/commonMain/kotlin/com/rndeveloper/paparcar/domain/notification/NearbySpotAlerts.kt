package com.rndeveloper.paparcar.domain.notification

import com.rndeveloper.paparcar.domain.model.Spot
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * «Acaba de quedar libre una plaza donde estás» — dicho por quien lo sabe, entregado por quien sabe
 * entregarlo. [ZONE-AN-ALERT-NEAR-YOU-IS-NOT-AN-ALERT-IN-YOUR-ZONE-001]
 *
 * Hermano de [MapVisibility] y por la misma razón: **una señal, un dueño**. Quien decide vive en
 * Home, porque es el único sitio donde ya están las dos entradas que hace falta cruzar —el fix del
 * GPS y las plazas de la suscripción viva—, y pedirlas otra vez a nivel de app serían dos
 * suscripciones y dos verdades sobre «dónde estoy». Quien entrega vive en `AppViewModel`, porque el
 * banner flota sobre toda la app.
 *
 * ⛔ **Y sobre todo: una sola RANURA.** El aviso de zona y éste acaban en el mismo sitio de
 * `AppState`, así que tienen que pasar por el mismo punto de entrega o serían dos dueños de un solo
 * hueco, peleándose por él sin que nadie lo vea. [NOTIF-A-FLOW-SPEAKS-THROUGH-ONE-SLOT-001]
 *
 * ⚠️ `DROP_OLDEST` con hueco para uno: si llegan dos plazas seguidas, la nueva sustituye a la que no
 * se ha entregado todavía. Es lo que ya hace el banner cuando se reemplaza en pantalla — nadie
 * quiere leer una cola de avisos sobre un mapa que los está enseñando.
 */
class NearbySpotAlerts {
    private val _alerts = MutableSharedFlow<Spot>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Las plazas que merecen interrumpir, ya decididas: quien colecta sólo entrega. */
    val alerts: SharedFlow<Spot> = _alerts.asSharedFlow()

    fun announce(spot: Spot) {
        _alerts.tryEmit(spot)
    }
}
