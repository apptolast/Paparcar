package com.rndeveloper.paparcar.domain.model

/**
 * Combined result of geocoding + POI lookup for a GPS coordinate.
 *
 * [address] is always present (may contain nulls if geocoding failed).
 * [placeInfo] is present only when a named place was found nearby.
 *
 * [approximate] marks an answer borrowed from a NEARBY cached cell because the
 * live geocoder failed (offline). Approximate answers are display-only — they
 * render as "Near X" and are NEVER persisted into a session or published spot;
 * every persisting consumer must skip them. [GEO-CACHE-ANSWERS-NEARBY-001]
 *
 * [poiChecked] separates the two things a null [placeInfo] used to mean at once:
 * *"we asked and there is no named place here"* from *"we could not ask"*.
 * [POI-A-FAILED-LOOKUP-IS-NOT-AN-ANSWER-001]
 *
 * ⛔ Without it the two are indistinguishable from outside the repository — same
 * emissions, same null — and every persisting consumer read the second as the
 * first: `EnrichParkingSessionWorker` reported `success` on a dead Overpass and
 * the session kept its street as a final answer, with nothing in the app ever
 * asking again. The flag is the same statement the cache already stores as
 * `poiChecked`; what was missing is that it crossed the repository's edge.
 *
 * [displayLine] prioritises the POI name over the street address. The category
 * is conveyed by a dedicated icon at the render site, never an inline emoji.
 */
data class AddressAndPlace(
    val address: AddressInfo,
    val placeInfo: PlaceInfo?,
    val approximate: Boolean = false,
    val poiChecked: Boolean = false,
) {
    val displayLine: String?
        get() = when {
            placeInfo != null -> placeInfo.name
            else -> address.displayLine
        }
}
