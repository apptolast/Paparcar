package com.rndeveloper.paparcar.domain.sharedcar

import com.rndeveloper.paparcar.domain.model.SharedCarSession
import com.rndeveloper.paparcar.domain.model.UserParking

/**
 * The group's parkings, in the shape the history and the statistics already speak. [VEH-SHARED-001]
 *
 * ⛔ **No second calculator.** The figures card, the chart, the timeline and the free-window gate
 * all consume `List<UserParking>` through `HistoryTimeline.resolve` + `ComputePeriodStatsUseCase`,
 * and the rule that there is exactly ONE aggregator predates this feature [STATS-CORE-001]. So a
 * shared car does not get its own maths: its mirror sessions are turned into the same rows, and
 * every number downstream is computed by the same code that computes yours.
 *
 * What the conversion CANNOT invent, it leaves null — `armEvidence`, `detectionPath`, the zone
 * radius, the private-zone id never travelled, so the row honestly has none. The consequence is
 * visible and correct: a shared row shows no detection provenance, because this device has no
 * business claiming how somebody else's phone decided.
 */
fun SharedCarSession.asHistoryEntry(vehicleId: String, showRoute: Boolean = true): UserParking = UserParking(
    id = sessionId,
    // The AUTHOR, not the reader: this is what lets the row say "Marta" and what keeps a
    // group list honest about whose trip each line was.
    userId = authorUid,
    vehicleId = vehicleId,
    location = location,
    isActive = isActive,
    address = address,
    placeInfo = placeInfo,
    // ⛔ Aquí se APLICA la visibilidad, y solo aquí: la geometría sigue guardada en el espejo — es
    // lo que permite volver a enseñarla —, pero la fila y el mapa no la reciben mientras su autor
    // la tenga apagada. La DISTANCIA no se toca: es una cifra, y las cifras son del grupo.
    routePolyline = routePolyline.takeIf { showRoute },
    routeSnapped = routeSnapped && showRoute,
    routeInferredSpans = routeInferredSpans.takeIf { showRoute },
    routeDistanceMeters = routeDistanceMeters,
    searchTimeMs = searchTimeMs,
    endedAtMs = endedAtMs,
    publishedSpot = publishedSpot,
)

/** True when this trip HAS a route and its author is not showing it — the case the history spells
 *  out («ruta no compartida»), as opposed to a parking that never had one, which is silence. */
fun SharedCarSession.routeHidden(showRoute: Boolean): Boolean =
    !showRoute && !routePolyline.isNullOrBlank()
