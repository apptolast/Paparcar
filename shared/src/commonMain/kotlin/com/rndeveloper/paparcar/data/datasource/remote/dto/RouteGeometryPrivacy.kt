package com.rndeveloper.paparcar.data.datasource.remote.dto

/**
 * The local-first strip: the outgoing session WITHOUT its route geometry.
 * [PRIV-A-TRACE-IS-NOT-A-STAT-001]
 *
 * A trace is not a stat. `routeDistanceMeters` and `searchTimeMs` are FIGURES — they say how much,
 * not where — and always travel. The polyline and its inferred-stretch provenance say WHERE the
 * user drove: they leave the device only with the explicit cloud-routes consent, read at the one
 * door every session push converges on.
 *
 * `routeSnapped = false` beside a null polyline is the established "no route" shape (legacy/BT
 * rows) — never the "recalculating" state, which requires a polyline to recalculate.
 */
fun ParkingHistoryDto.withoutRouteGeometry(): ParkingHistoryDto = copy(
    routePolyline = null,
    routeSnapped = false,
    routeInferredSpans = null,
    routeInferredResolution = null,
)
