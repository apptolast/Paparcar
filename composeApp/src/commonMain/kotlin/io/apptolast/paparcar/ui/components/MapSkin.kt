package io.apptolast.paparcar.ui.components

import com.swmansion.kmpmaps.core.MapType

/**
 * User-facing map skin. Each entry resolves to a base tile type plus an optional Google Maps
 * JSON style — the pair is what [PaparcarMapView] actually feeds the map.
 *
 * BRAND and DRIVING ride on [MapType.NORMAL] because Google's JSON styling only applies to the
 * vector tile type (satellite / hybrid / terrain are raster and ignore most of it). AERIAL is
 * raw [MapType.HYBRID] — aerial imagery + street labels — deliberately unstyled.
 *
 * iOS note: Apple Maps has no JSON styling, so BRAND and DRIVING degrade to the standard map
 * with the light/dark theme; AERIAL maps to the native hybrid. [MAP-TYPES-001]
 */
enum class MapSkin {
    /** Default. Brand-styled road map (light/dark variants follow the app theme). */
    BRAND,

    /** High-contrast, decluttered road map for heading to a spot: POIs off, road hierarchy up. */
    DRIVING,

    /** Satellite with street labels — for checking a spot's physical surroundings. */
    AERIAL,
    ;

    val mapType: MapType
        get() = when (this) {
            AERIAL -> MapType.HYBRID
            else -> MapType.NORMAL
        }

    /** Google Maps JSON style for this skin, or null to keep the native tiles untouched. */
    fun styleJson(isDark: Boolean): String? = when (this) {
        BRAND -> if (isDark) DARK_MAP_STYLE else LIGHT_MAP_STYLE
        DRIVING -> if (isDark) DRIVING_DARK_MAP_STYLE else DRIVING_LIGHT_MAP_STYLE
        AERIAL -> null
    }

    fun toPreferenceString(): String = name

    companion object {
        /**
         * Parses the persisted preference. Migrates the legacy values stored when the picker
         * offered raw [MapType]s: TERRAIN (old default) → [BRAND], SATELLITE/HYBRID → [AERIAL].
         * Anything unknown falls back to [BRAND].
         */
        fun fromPreferenceString(value: String): MapSkin = when (value) {
            BRAND.name -> BRAND
            DRIVING.name -> DRIVING
            AERIAL.name -> AERIAL
            LEGACY_SATELLITE, LEGACY_HYBRID -> AERIAL
            else -> BRAND
        }

        private const val LEGACY_SATELLITE = "SATELLITE"
        private const val LEGACY_HYBRID = "HYBRID"
    }
}
