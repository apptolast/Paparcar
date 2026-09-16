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
 *
 * ⛔ **"The one door" was true when it was written and stopped being true with F8.** The mirror of a
 * shared car is a SECOND door for the same geometry, and it had the opposite rule: it uploaded
 * always and hid on reading, so a user with this consent off shipped their whole route to the group
 * anyway. Since [PRIV-A-ROUTE-LEAVES-THE-PHONE-BY-ONE-DOOR-001] each door asks its own permission —
 * this one for YOUR cloud history, `sharedCarMirrorPayload(shareRoutes = …)` for the GROUP's copy —
 * and neither uploads without it. If a third reader of `routePolyline` ever appears, it needs a
 * permission of its own before it ships, not afterwards.
 */
fun ParkingHistoryDto.withoutRouteGeometry(): ParkingHistoryDto = copy(
    routePolyline = null,
    routeSnapped = false,
    routeInferredSpans = null,
    routeInferredResolution = null,
)
