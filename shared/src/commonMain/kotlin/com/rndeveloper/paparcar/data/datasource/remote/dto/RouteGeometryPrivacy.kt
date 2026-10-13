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

/**
 * The outgoing session carrying the geometry the CLOUD already holds, not the one on the device.
 * [PRIV-A-LAPSED-PLUS-STILL-UPLOADS-ROUTES-001]
 *
 * Needed because the push is a **full-document `set()`**: a document sent without the geometry
 * fields does not leave them alone, it DELETES them. That is exactly what we want when the user
 * revokes the consent — the scrub above IS the retention promise — and exactly what we must not do
 * when the thing that lapsed is Plus: those routes were uploaded while paying, with the consent
 * valid, and losing them would be us deleting the user's data because they stopped subscribing.
 *
 * So while Plus is lapsed the geometry is **frozen, not refreshed and not erased**: what the cloud
 * holds stays, and nothing new goes up — a later snap or a re-recorded line stays on the device.
 * Copying `remote`'s fields rather than keeping `this`'s is the whole point; keeping the local ones
 * would be an upload.
 *
 * ⛔ The four field names live **only here**, beside [withoutRouteGeometry], and that is deliberate:
 * a list of fields written out a second time is a contract nobody checks, which is how
 * [SYNC-THE-WORKDATA-FIELD-LIST-IS-A-CONTRACT-NOBODY-CHECKS-001] sent columns to null.
 */
fun ParkingHistoryDto.withRouteGeometryOf(remote: ParkingHistoryDto?): ParkingHistoryDto = copy(
    routePolyline = remote?.routePolyline,
    routeSnapped = remote?.routeSnapped ?: false,
    routeInferredSpans = remote?.routeInferredSpans,
    routeInferredResolution = remote?.routeInferredResolution,
)
