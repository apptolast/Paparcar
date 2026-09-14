package com.rndeveloper.paparcar.data.repository

import com.rndeveloper.paparcar.data.overpass.OverpassQuota
import com.rndeveloper.paparcar.data.overpass.OverpassWalkPatience
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * La red de seguridad que envuelve a Overpass tiene que cubrir lo que Overpass puede costar HOY.
 * [OVERPASS-THE-QUOTAS-WAIT-IS-PART-OF-THE-WALK-001]
 *
 * ⛔ Este test existe porque el número ya se quedó corto una vez en silencio: valía 20 s cuando la
 * vía hablaba con un solo espejo y cortaba el failover a mitad del segundo, de modo que el arreglo
 * de la caminata era invisible desde aquí. Se subió a 80 s con un cálculo — "3 espejos × 25 s" — que
 * el 14-09-2026 dejó de ser cierto por tres motivos a la vez, y el número siguió siendo suficiente
 * por casualidad.
 *
 * Un `withTimeoutOrNull` que se dispara antes de que la caminata termine no rompe nada visible: la
 * celda simplemente no se sella y el POI no llega. Eso es justo lo que lo hace peligroso — se lee
 * como "ese sitio no tiene nombre", no como un fallo.
 */
class Phase2DeadlineTest {

    @Test
    fun should_outlastTheWorstOverpassAskThereIs() {
        // Esperar turno + la caminata entera: lo máximo que `PlacesDataSource` puede tardar.
        val peorCaso = OverpassQuota.WAIT_CEILING_MS + OverpassWalkPatience.WALK_CEILING_MS

        assertTrue(
            AddressAndPlaceRepositoryImpl.PHASE2_TIMEOUT_MS > peorCaso,
            "la red corta a ${AddressAndPlaceRepositoryImpl.PHASE2_TIMEOUT_MS} ms y una petición " +
                "puede costar $peorCaso ms: el failover moriría a medias y nadie lo vería",
        )
    }

    @Test
    fun should_leaveRoomForOurselves_butNotForAnExtraServer() {
        val peorCaso = OverpassQuota.WAIT_CEILING_MS + OverpassWalkPatience.WALK_CEILING_MS
        val holgura = AddressAndPlaceRepositoryImpl.PHASE2_TIMEOUT_MS - peorCaso

        // ⛔ La holgura es para parsear y volver al hilo, no para que quepa otra caminata entera:
        // una red demasiado ancha deja de acotar nada y un carril colgado tarda minutos en rendirse.
        assertTrue(holgura > 0, "no hay holgura para el parseo")
        assertTrue(
            holgura < OverpassWalkPatience.WALK_CEILING_MS,
            "una holgura de $holgura ms ya no es holgura, es una segunda caminata",
        )
    }
}
