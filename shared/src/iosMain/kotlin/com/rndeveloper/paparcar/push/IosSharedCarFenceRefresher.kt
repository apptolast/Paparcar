package com.rndeveloper.paparcar.push

import com.rndeveloper.paparcar.detection.IosDetectionController
import com.rndeveloper.paparcar.domain.push.SharedCarFenceRefresher

/**
 * En iOS, «pasa ya» es un `Command.Reconcile` más en el intake del controlador.
 * [DET-A-SHARED-FENCE-FOLLOWS-THE-CAR-001][IOS-THE-GROUP-CANNOT-REACH-AN-IPHONE-001]
 *
 * El gemelo del janitor de Android, y con la misma prohibición: no registra regiones. Serializado
 * por el mismo intake que todo lo demás, así que un push que llega mientras se está adjudicando una
 * salida no se cruza con ella.
 */
class IosSharedCarFenceRefresher(
    private val controller: IosDetectionController,
) : SharedCarFenceRefresher {

    override fun refreshNow() {
        controller.reconcileFencesNow(SOURCE)
    }

    private companion object {
        const val SOURCE = "shared-car-push"
    }
}
