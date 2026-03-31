package io.apptolast.paparcar.domain.model

/**
 * Combined result of geocoding + POI lookup for a GPS coordinate.
 *
 * [address] is always present (may contain nulls if geocoding failed).
 * [placeInfo] is present only when a named place was found nearby.
 *
 * [displayLine] prioritises the POI name (with emoji) over the street address.
 */
data class LocationInfo(
    val address: AddressInfo,
    val placeInfo: PlaceInfo?,
) {
    val displayLine: String?
        get() = when {
            placeInfo != null -> "${placeInfo.category.emoji} ${placeInfo.name}"
            else -> address.displayLine
        }
}
