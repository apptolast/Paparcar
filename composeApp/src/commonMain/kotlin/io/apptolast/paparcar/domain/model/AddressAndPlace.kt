package io.apptolast.paparcar.domain.model

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
 * [displayLine] prioritises the POI name over the street address. The category
 * is conveyed by a dedicated icon at the render site, never an inline emoji.
 */
data class AddressAndPlace(
    val address: AddressInfo,
    val placeInfo: PlaceInfo?,
    val approximate: Boolean = false,
) {
    val displayLine: String?
        get() = when {
            placeInfo != null -> placeInfo.name
            else -> address.displayLine
        }
}
