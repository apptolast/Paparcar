package io.apptolast.paparcar.ui.components

import com.swmansion.kmpmaps.core.MapType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MapSkinTest {

    // ── Resolution: skin → (MapType, styleJson) ───────────────────────────────

    @Test
    fun should_resolveNormalTileType_when_brandOrDrivingSkin() {
        assertEquals(MapType.NORMAL, MapSkin.BRAND.mapType)
        assertEquals(MapType.NORMAL, MapSkin.DRIVING.mapType)
    }

    @Test
    fun should_resolveHybridTileType_when_aerialSkin() {
        assertEquals(MapType.HYBRID, MapSkin.AERIAL.mapType)
    }

    @Test
    fun should_provideBrandStyles_when_brandSkin() {
        assertEquals(LIGHT_MAP_STYLE, MapSkin.BRAND.styleJson(isDark = false))
        assertEquals(DARK_MAP_STYLE, MapSkin.BRAND.styleJson(isDark = true))
    }

    @Test
    fun should_provideDrivingStyles_when_drivingSkin() {
        assertEquals(DRIVING_LIGHT_MAP_STYLE, MapSkin.DRIVING.styleJson(isDark = false))
        assertEquals(DRIVING_DARK_MAP_STYLE, MapSkin.DRIVING.styleJson(isDark = true))
    }

    @Test
    fun should_provideNoStyle_when_aerialSkin() {
        assertNull(MapSkin.AERIAL.styleJson(isDark = false))
        assertNull(MapSkin.AERIAL.styleJson(isDark = true))
    }

    // ── Preference round-trip ─────────────────────────────────────────────────

    @Test
    fun should_roundTripPreferenceString_when_anySkin() {
        MapSkin.entries.forEach { skin ->
            assertEquals(skin, MapSkin.fromPreferenceString(skin.toPreferenceString()))
        }
    }

    // ── Legacy migration (values persisted when the picker offered raw MapTypes) ──

    @Test
    fun should_migrateLegacyTerrainToBrand_when_parsingPreference() {
        assertEquals(MapSkin.BRAND, MapSkin.fromPreferenceString("TERRAIN"))
    }

    @Test
    fun should_migrateLegacySatelliteToAerial_when_parsingPreference() {
        assertEquals(MapSkin.AERIAL, MapSkin.fromPreferenceString("SATELLITE"))
    }

    @Test
    fun should_migrateLegacyHybridToAerial_when_parsingPreference() {
        assertEquals(MapSkin.AERIAL, MapSkin.fromPreferenceString("HYBRID"))
    }

    @Test
    fun should_fallBackToBrand_when_parsingUnknownPreference() {
        assertEquals(MapSkin.BRAND, MapSkin.fromPreferenceString(""))
        assertEquals(MapSkin.BRAND, MapSkin.fromPreferenceString("garbage"))
    }
}
