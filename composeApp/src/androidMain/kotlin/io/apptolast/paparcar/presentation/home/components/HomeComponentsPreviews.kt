@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.home.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.presentation.home.HomeState
import io.apptolast.paparcar.presentation.preview.FakeData
import io.apptolast.paparcar.ui.theme.PaparcarTheme
import kotlin.time.Clock

// ─── Helpers comunes ──────────────────────────────────────────────────────────

/** Spot con timestamp [agoMinutes] minutos en el pasado. */
internal fun fakeSpot(
    id: String,
    agoMinutes: Long,
    lat: Double = 40.418,
    lon: Double = -3.706,
) = io.apptolast.paparcar.domain.model.Spot(
    id = id,
    location = GpsPoint(
        latitude = lat,
        longitude = lon,
        accuracy = 10f,
        timestamp = Clock.System.now().toEpochMilliseconds() - agoMinutes * 60_000L,
        speed = 0f,
    ),
    reportedBy = "user_preview",
    address = FakeData.addrStreet,
    placeInfo = null,
)

internal fun fakeSpotWithPoi(id: String, agoMinutes: Long) = io.apptolast.paparcar.domain.model.Spot(
    id = id,
    location = GpsPoint(
        latitude = 40.419,
        longitude = -3.704,
        accuracy = 8f,
        timestamp = Clock.System.now().toEpochMilliseconds() - agoMinutes * 60_000L,
        speed = 0f,
    ),
    reportedBy = "user_preview",
    address = FakeData.addrFuel,
    placeInfo = FakeData.placeInfoFuel,
)

/** Lista de 3 spots con frescuras verde, ámbar y gris para comparar colores. */
internal fun fakeSpotsVariedFreshness() = listOf(
    fakeSpot("v1", agoMinutes = 0L),
    fakeSpotWithPoi("v2", agoMinutes = 10L),
    fakeSpot("v3", agoMinutes = 25L, lat = 40.417, lon = -3.708),
)

// ═══════════════════════════════════════════════════════════════════════════════
//  SECCIÓN A — DISEÑO ACTUAL
//  Bottom sheet con lista vertical de HomeSpotRow. El peek muestra contexto de
//  cámara + badge de spots libres. Diseño implementado en producción.
//  Componentes: HomeSheetContent · HomeSpotRow · HomeSpotRowGlass ·
//               HomeParkingRow · HomePeekHandle · HomeParkingEmptyCard ·
//               HomeSectionHeader · HomeEmptySpots · HomePermissionsCard ·
//               HomeNavBar · HomeReportSpotFab · HomeFloatingHeader
// ═══════════════════════════════════════════════════════════════════════════════

// ─── A — HomeFloatingHeader ───────────────────────────────────────────────────

@Preview(name = "A — HomeFloatingHeader (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeFloatingHeaderDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        Column(Modifier.padding(16.dp)) {
            HomeFloatingHeader(onHistoryClick = {}, onMyCarClick = {}, onSettingsClick = {})
        }
    }
}

@Preview(name = "A — HomeFloatingHeader (claro)", showBackground = true)
@Composable
private fun HomeFloatingHeaderLightPreview() {
    PaparcarTheme(darkTheme = false) {
        Column(Modifier.padding(16.dp)) {
            HomeFloatingHeader(onHistoryClick = {}, onMyCarClick = {}, onSettingsClick = {})
        }
    }
}

// ─── A — HomePeekHandle ───────────────────────────────────────────────────────

@Preview(name = "A — HomePeekHandle: POI + spots (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomePeekHandleWithPoiDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        HomePeekHandle(
            state = HomeState(
                cameraLocationInfo = FakeData.locationInfoFuel,
                nearbySpots = FakeData.nearbySpots,
            ),
        )
    }
}

@Preview(name = "A — HomePeekHandle: dirección simple (claro)", showBackground = true)
@Composable
private fun HomePeekHandleStreetLightPreview() {
    PaparcarTheme(darkTheme = false) {
        HomePeekHandle(
            state = HomeState(
                cameraLocationInfo = FakeData.locationInfoStreet,
                nearbySpots = FakeData.nearbySpots,
            ),
        )
    }
}

@Preview(name = "A — HomePeekHandle: skeleton loading (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomePeekHandleSkeletonDarkPreview() {
    PaparcarTheme(darkTheme = true) { HomePeekHandle(state = HomeState()) }
}

@Preview(name = "A — HomePeekHandle: skeleton loading (claro)", showBackground = true)
@Composable
private fun HomePeekHandleSkeletonLightPreview() {
    PaparcarTheme(darkTheme = false) { HomePeekHandle(state = HomeState()) }
}

@Preview(name = "A — HomePeekHandle: sin dirección, 0 spots (claro)", showBackground = true)
@Composable
private fun HomePeekHandleEmptyLightPreview() {
    PaparcarTheme(darkTheme = false) { HomePeekHandle(state = HomeState()) }
}

@Preview(name = "A — HomePeekHandle: spot seleccionado (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomePeekHandleSpotSelectedDarkPreview() {
    val spot = FakeData.nearbySpots.first()
    PaparcarTheme(darkTheme = true) {
        HomePeekHandle(
            state = HomeState(
                nearbySpots = FakeData.nearbySpots,
                userGpsPoint = GpsPoint(40.4165, -3.7030, 12f, Clock.System.now().toEpochMilliseconds(), 0f),
                selectedItemId = spot.id,
            ),
        )
    }
}

@Preview(name = "A — HomePeekHandle: spot seleccionado (claro)", showBackground = true)
@Composable
private fun HomePeekHandleSpotSelectedLightPreview() {
    val spot = FakeData.nearbySpots[1]
    PaparcarTheme(darkTheme = false) {
        HomePeekHandle(
            state = HomeState(
                nearbySpots = FakeData.nearbySpots,
                userGpsPoint = GpsPoint(40.4165, -3.7030, 12f, Clock.System.now().toEpochMilliseconds(), 0f),
                selectedItemId = spot.id,
            ),
        )
    }
}

@Preview(name = "A — HomePeekHandle: parking seleccionado (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomePeekHandleParkingSelectedDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        HomePeekHandle(
            state = HomeState(
                userParking = FakeData.activeSession,
                userGpsPoint = GpsPoint(40.4165, -3.7030, 12f, Clock.System.now().toEpochMilliseconds(), 0f),
                nearbySpots = FakeData.nearbySpots,
                selectedItemId = HomeState.PARKING_ITEM_ID,
            ),
        )
    }
}

@Preview(name = "A — HomePeekHandle: parking seleccionado (claro)", showBackground = true)
@Composable
private fun HomePeekHandleParkingSelectedLightPreview() {
    PaparcarTheme(darkTheme = false) {
        HomePeekHandle(
            state = HomeState(
                userParking = FakeData.activeSessionSupermarket,
                userGpsPoint = GpsPoint(40.4165, -3.7030, 12f, Clock.System.now().toEpochMilliseconds(), 0f),
                nearbySpots = FakeData.nearbySpots,
                selectedItemId = HomeState.PARKING_ITEM_ID,
            ),
        )
    }
}

// ─── A — HomeParkingRow ───────────────────────────────────────────────────────

@Preview(name = "A — HomeParkingRow: con POI (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeParkingRowPoiDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        Column(Modifier.padding(16.dp)) {
            HomeParkingRow(parking = FakeData.activeSession, userLocation = Pair(40.4165, -3.7030), onSelect = {})
        }
    }
}

@Preview(name = "A — HomeParkingRow: sin dirección (claro)", showBackground = true)
@Composable
private fun HomeParkingRowNoAddressLightPreview() {
    PaparcarTheme(darkTheme = false) {
        Column(Modifier.padding(16.dp)) {
            HomeParkingRow(
                parking = FakeData.activeSession.copy(address = null, placeInfo = null),
                userLocation = null,
                onSelect = {},
            )
        }
    }
}

@Preview(name = "A — HomeParkingRow: seleccionado (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeParkingRowSelectedDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        Column(Modifier.padding(16.dp)) {
            HomeParkingRow(
                parking = FakeData.activeSessionSupermarket,
                userLocation = Pair(40.4165, -3.7030),
                isSelected = true,
                onSelect = {},
            )
        }
    }
}

// ─── A — HomeParkingEmptyCard ─────────────────────────────────────────────────

@Preview(name = "A — HomeParkingEmptyCard (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeParkingEmptyCardDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        Column(Modifier.padding(16.dp)) { HomeParkingEmptyCard(onManualPark = {}) }
    }
}

@Preview(name = "A — HomeParkingEmptyCard (claro)", showBackground = true)
@Composable
private fun HomeParkingEmptyCardLightPreview() {
    PaparcarTheme(darkTheme = false) {
        Column(Modifier.padding(16.dp)) { HomeParkingEmptyCard(onManualPark = {}) }
    }
}

// ─── A — HomeSectionHeader ────────────────────────────────────────────────────

@Preview(name = "A — HomeSectionHeader: con badge (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeSectionHeaderBadgeDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        Column(Modifier.padding(16.dp)) { HomeSectionHeader(title = "CERCA DE TI", badge = "3 libres") }
    }
}

@Preview(name = "A — HomeSectionHeader: con badge (claro)", showBackground = true)
@Composable
private fun HomeSectionHeaderBadgeLightPreview() {
    PaparcarTheme(darkTheme = false) {
        Column(Modifier.padding(16.dp)) { HomeSectionHeader(title = "CERCA DE TI", badge = "3 libres") }
    }
}

@Preview(name = "A — HomeSectionHeader: sin badge (claro)", showBackground = true)
@Composable
private fun HomeSectionHeaderNoBadgeLightPreview() {
    PaparcarTheme(darkTheme = false) {
        Column(Modifier.padding(16.dp)) { HomeSectionHeader(title = "ESTÁS APARCADO") }
    }
}

// ─── A — HomeSpotRow ──────────────────────────────────────────────────────────

@Preview(name = "A — HomeSpotRow: fresco < 1 min, POI (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeSpotRowFreshDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        Column {
            HomeSpotRow(spot = fakeSpotWithPoi("p1", 0L), userLocation = Pair(40.4165, -3.7030), onSelect = {})
        }
    }
}

@Preview(name = "A — HomeSpotRow: verde < 5 min (claro)", showBackground = true)
@Composable
private fun HomeSpotRowFreshLightPreview() {
    PaparcarTheme(darkTheme = false) {
        Column {
            HomeSpotRow(spot = fakeSpot("p1", 3L), userLocation = Pair(40.4165, -3.7030), onSelect = {})
        }
    }
}

@Preview(name = "A — HomeSpotRow: ámbar 10 min (claro)", showBackground = true)
@Composable
private fun HomeSpotRowMediumLightPreview() {
    PaparcarTheme(darkTheme = false) {
        Column {
            HomeSpotRow(spot = fakeSpot("p2", 10L), userLocation = Pair(40.4165, -3.7030), onSelect = {})
        }
    }
}

@Preview(name = "A — HomeSpotRow: gris 25 min (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeSpotRowOldDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        Column {
            HomeSpotRow(spot = fakeSpot("p3", 25L), userLocation = Pair(40.4165, -3.7030), onSelect = {})
        }
    }
}

@Preview(name = "A — HomeSpotRow: seleccionado, sin distancia (claro)", showBackground = true)
@Composable
private fun HomeSpotRowSelectedNoDistanceLightPreview() {
    PaparcarTheme(darkTheme = false) {
        Column {
            HomeSpotRow(
                spot = fakeSpotWithPoi("p4", 7L),
                userLocation = null,
                isSelected = true,
                onSelect = {},
            )
        }
    }
}

// ─── A — HomeSpotRowGlass ─────────────────────────────────────────────────────

@Preview(name = "A — HomeSpotRowGlass: 3 frescuras (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES, heightDp = 340)
@Composable
private fun HomeSpotRowGlassListDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            fakeSpotsVariedFreshness().forEach { spot ->
                HomeSpotRowGlass(
                    spot = spot,
                    userLocation = Pair(40.4165, -3.7030),
                    onSelect = {},
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
    }
}

@Preview(name = "A — HomeSpotRowGlass: 3 frescuras (claro)", showBackground = true, heightDp = 340)
@Composable
private fun HomeSpotRowGlassListLightPreview() {
    PaparcarTheme(darkTheme = false) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            fakeSpotsVariedFreshness().forEach { spot ->
                HomeSpotRowGlass(
                    spot = spot,
                    userLocation = Pair(40.4165, -3.7030),
                    onSelect = {},
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
    }
}

@Preview(name = "A — HomeSpotRowGlass: seleccionado (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeSpotRowGlassSelectedDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        Column(Modifier.padding(12.dp)) {
            HomeSpotRowGlass(
                spot = fakeSpotWithPoi("g_sel", 4L),
                userLocation = Pair(40.4165, -3.7030),
                isSelected = true,
                onSelect = {},
            )
        }
    }
}

// ─── A — HomeEmptySpots ───────────────────────────────────────────────────────

@Preview(name = "A — HomeEmptySpots (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeEmptySpotsDarkPreview() {
    PaparcarTheme(darkTheme = true) { Column(Modifier.padding(16.dp)) { HomeEmptySpots() } }
}

@Preview(name = "A — HomeEmptySpots (claro)", showBackground = true)
@Composable
private fun HomeEmptySpotsLightPreview() {
    PaparcarTheme(darkTheme = false) { Column(Modifier.padding(16.dp)) { HomeEmptySpots() } }
}

// ─── A — HomePermissionsCard ──────────────────────────────────────────────────

@Preview(name = "A — HomePermissionsCard (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomePermissionsCardDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        Column(Modifier.padding(16.dp)) { HomePermissionsCard(onRequestPermissions = {}) }
    }
}

@Preview(name = "A — HomePermissionsCard (claro)", showBackground = true)
@Composable
private fun HomePermissionsCardLightPreview() {
    PaparcarTheme(darkTheme = false) {
        Column(Modifier.padding(16.dp)) { HomePermissionsCard(onRequestPermissions = {}) }
    }
}

// ─── A — HomeNavBar ───────────────────────────────────────────────────────────

@Preview(name = "A — HomeNavBar: spot seleccionado (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeNavBarSpotDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        HomeNavBar(navLabel = "⛽ Repsol Av. Castellana  ·  Av. de la Castellana 110", onNavigate = {})
    }
}

@Preview(name = "A — HomeNavBar: parking seleccionado (claro)", showBackground = true)
@Composable
private fun HomeNavBarParkingLightPreview() {
    PaparcarTheme(darkTheme = false) {
        HomeNavBar(navLabel = "Tu coche  ·  Calle Gran Vía 32", onNavigate = {})
    }
}

// ─── A — HomeReportSpotFab ────────────────────────────────────────────────────

@Preview(name = "A — HomeReportSpotFab (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeReportSpotFabDarkPreview() {
    PaparcarTheme(darkTheme = true) { Column(Modifier.padding(16.dp)) { HomeReportSpotFab(onClick = {}) } }
}

@Preview(name = "A — HomeReportSpotFab (claro)", showBackground = true)
@Composable
private fun HomeReportSpotFabLightPreview() {
    PaparcarTheme(darkTheme = false) { Column(Modifier.padding(16.dp)) { HomeReportSpotFab(onClick = {}) } }
}

// ─── A — HomeSheetContent: pantallas completas ────────────────────────────────

@Preview(name = "A — Sheet: coche + spots (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES, heightDp = 600)
@Composable
private fun HomeSheetContentWithParkingAndSpotsDarkPreview() {
    val scrollState = rememberScrollState()
    val scrollPositions = remember { mutableMapOf<String, Int>() }
    PaparcarTheme(darkTheme = true) {
        HomeSheetContent(
            state = HomeState(
                allPermissionsGranted = true,
                userParking = FakeData.activeSession,
                userGpsPoint = GpsPoint(40.4165, -3.7030, 12f, Clock.System.now().toEpochMilliseconds(), 0f),
                nearbySpots = FakeData.nearbySpots,
            ),
            onIntent = {}, onCameraMove = { _, _ -> }, onParkingClick = {},
            onManualPark = {}, onSpotSelect = { _, _, _ -> }, onNavigate = { _, _ -> },
            scrollState = scrollState,
            spotScrollPositions = scrollPositions,
        )
    }
}

@Preview(name = "A — Sheet: coche + spots (claro)", showBackground = true, heightDp = 600)
@Composable
private fun HomeSheetContentWithParkingAndSpotsLightPreview() {
    val scrollState = rememberScrollState()
    val scrollPositions = remember { mutableMapOf<String, Int>() }
    PaparcarTheme(darkTheme = false) {
        HomeSheetContent(
            state = HomeState(
                allPermissionsGranted = true,
                userParking = FakeData.activeSessionSupermarket,
                userGpsPoint = GpsPoint(40.4165, -3.7030, 12f, Clock.System.now().toEpochMilliseconds(), 0f),
                nearbySpots = FakeData.nearbySpots,
            ),
            onIntent = {}, onCameraMove = { _, _ -> }, onParkingClick = {},
            onManualPark = {}, onSpotSelect = { _, _, _ -> }, onNavigate = { _, _ -> },
            scrollState = scrollState, spotScrollPositions = scrollPositions,
        )
    }
}

@Preview(name = "A — Sheet: sin coche, spots primero (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES, heightDp = 600)
@Composable
private fun HomeSheetContentSpotsFirstDarkPreview() {
    val scrollState = rememberScrollState()
    val scrollPositions = remember { mutableMapOf<String, Int>() }
    PaparcarTheme(darkTheme = true) {
        HomeSheetContent(
            state = HomeState(
                allPermissionsGranted = true,
                userParking = null,
                userGpsPoint = GpsPoint(40.4165, -3.7030, 12f, Clock.System.now().toEpochMilliseconds(), 0f),
                nearbySpots = FakeData.nearbySpots,
            ),
            onIntent = {}, onCameraMove = { _, _ -> }, onParkingClick = {},
            onManualPark = {}, onSpotSelect = { _, _, _ -> }, onNavigate = { _, _ -> },
            scrollState = scrollState, spotScrollPositions = scrollPositions,
        )
    }
}

@Preview(name = "A — Sheet: sin coche, spots primero (claro)", showBackground = true, heightDp = 600)
@Composable
private fun HomeSheetContentSpotsFirstLightPreview() {
    val scrollState = rememberScrollState()
    val scrollPositions = remember { mutableMapOf<String, Int>() }
    PaparcarTheme(darkTheme = false) {
        HomeSheetContent(
            state = HomeState(
                allPermissionsGranted = true,
                userParking = null,
                userGpsPoint = GpsPoint(40.4165, -3.7030, 12f, Clock.System.now().toEpochMilliseconds(), 0f),
                nearbySpots = FakeData.nearbySpots,
            ),
            onIntent = {}, onCameraMove = { _, _ -> }, onParkingClick = {},
            onManualPark = {}, onSpotSelect = { _, _, _ -> }, onNavigate = { _, _ -> },
            scrollState = scrollState, spotScrollPositions = scrollPositions,
        )
    }
}

@Preview(name = "A — Sheet: sin coche, 0 spots, empty state (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES, heightDp = 500)
@Composable
private fun HomeSheetContentEmptyDarkPreview() {
    val scrollState = rememberScrollState()
    val scrollPositions = remember { mutableMapOf<String, Int>() }
    PaparcarTheme(darkTheme = true) {
        HomeSheetContent(
            state = HomeState(allPermissionsGranted = true, userParking = null, nearbySpots = emptyList()),
            onIntent = {}, onCameraMove = { _, _ -> }, onParkingClick = {},
            onManualPark = {}, onSpotSelect = { _, _, _ -> }, onNavigate = { _, _ -> },
            scrollState = scrollState, spotScrollPositions = scrollPositions,
        )
    }
}

@Preview(name = "A — Sheet: sin permisos (claro)", showBackground = true, heightDp = 400)
@Composable
private fun HomeSheetContentNoPermissionsLightPreview() {
    val scrollState = rememberScrollState()
    val scrollPositions = remember { mutableMapOf<String, Int>() }
    PaparcarTheme(darkTheme = false) {
        HomeSheetContent(
            state = HomeState(allPermissionsGranted = false, userParking = null, nearbySpots = emptyList()),
            onIntent = {}, onCameraMove = { _, _ -> }, onParkingClick = {},
            onManualPark = {}, onSpotSelect = { _, _, _ -> }, onNavigate = { _, _ -> },
            scrollState = scrollState, spotScrollPositions = scrollPositions,
        )
    }
}