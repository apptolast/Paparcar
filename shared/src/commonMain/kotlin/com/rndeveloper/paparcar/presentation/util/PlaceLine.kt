package com.rndeveloper.paparcar.presentation.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import com.rndeveloper.paparcar.domain.model.AddressAndPlace
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.location_approximate_near

/**
 * One resolved "where this is" line, ready to be projected into a slice.
 *
 * Two jobs, and the split between them is the point:
 *
 * - **[line] is already resolved** (POI name > street address), so a slice can carry it and its
 *   structural equality stays COARSE: driving down a long avenue re-geocodes every ~11 m
 *   ([com.rndeveloper.paparcar.presentation.home.HomeGeocodingController]) but the value only
 *   changes when the street does. Carrying the raw [AddressAndPlace] instead would re-emit the
 *   slice — and recompose its whole section — on every fix.
 * - **[approximate] stays a flag, not prose**, because "Near X" is a localized string and slices
 *   are built outside composition. The render site says it with [placeLineText].
 *
 * [UI-A-LIVE-TRIP-NAMES-ITS-PLACE-001] [GEO-CACHE-ANSWERS-NEARBY-001]
 */
@Immutable
data class PlaceLine(val line: String, val approximate: Boolean = false)

/**
 * POI name > street address, or null when the geocode says nothing usable yet. The single resolver
 * behind every surface that names a place from a geocode — the peek titles, the vehicle chip and
 * the vehicle card. Three copies of this fallback chain are three chances for two surfaces to name
 * the same point differently. [UI-A-LIVE-TRIP-NAMES-ITS-PLACE-001]
 */
fun AddressAndPlace?.toPlaceLine(): PlaceLine? {
    val info = this ?: return null
    val line = info.placeInfo?.name?.takeIf { it.isNotBlank() }
        ?: info.address.displayLine?.takeIf { it.isNotBlank() }
        ?: return null
    return PlaceLine(line = line, approximate = info.approximate)
}

/**
 * The line UNDER [toPlaceLine]: the street when the title is a POI name, the city/region otherwise.
 * Never repeats the title. Pure — no localization — so it projects into a slice like the title does.
 */
fun AddressAndPlace?.secondaryPlaceLine(): String? {
    val info = this ?: return null
    val placeName = info.placeInfo?.name
    return if (placeName != null) {
        info.address.displayLine?.takeIf { it.isNotBlank() && it != placeName }
    } else {
        listOfNotNull(info.address.city, info.address.region)
            .joinToString(SECONDARY_SEPARATOR)
            .takeIf { it.isNotEmpty() }
    }
}

private const val SECONDARY_SEPARATOR = ", "

/**
 * [PlaceLine] as the user reads it: a borrowed-neighbour answer declares itself as "Near X" instead
 * of passing an approximation off as exact. [GEO-CACHE-ANSWERS-NEARBY-001]
 */
@Composable
fun placeLineText(place: PlaceLine?): String? {
    val resolved = place ?: return null
    return if (resolved.approximate) {
        stringResource(Res.string.location_approximate_near, resolved.line)
    } else {
        resolved.line
    }
}
