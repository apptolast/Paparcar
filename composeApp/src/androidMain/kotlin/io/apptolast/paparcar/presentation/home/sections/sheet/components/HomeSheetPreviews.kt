@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.home.sections.sheet.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.presentation.home.HomeState
import io.apptolast.paparcar.presentation.home.VehicleCard
import io.apptolast.paparcar.presentation.preview.FakeData
import io.apptolast.paparcar.ui.components.PaparcarBottomActionBar
import io.apptolast.paparcar.ui.components.PapSectionHeader
import io.apptolast.paparcar.ui.theme.PaparcarTheme
import kotlin.time.Clock

// ─── Helpers comunes ──────────────────────────────────────────────────────────

/** Spot con timestamp [agoMinutes] minutos en el pasado. */
internal fun fakeSpot(
    id: String,
    agoMinutes: Long,
    lat: Double = 40.418,
    lon: Double = -3.706,
    sizeCategory: io.apptolast.paparcar.domain.model.VehicleSize? = VehicleSize.MEDIUM_SUV,
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
    sizeCategory = sizeCategory,
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
    sizeCategory = VehicleSize.MICRO_SMALL,
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
//  Componentes: HomeSheetContent · HomeSpotRow · HomeVehicleCard ·
//               HomePeekHandle · PapSectionHeader ·
//               HomeEmptySpots · HomePermissionsCard · PaparcarBottomActionBar
// ═══════════════════════════════════════════════════════════════════════════════

// ─── A — HomePeekHandle ───────────────────────────────────────────────────────

@Preview(name = "A — HomePeekHandle: POI + spots (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomePeekHandleWithPoiDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        HomePeekHandle(
            state = HomeState(
                cameraAddressAndPlace = FakeData.addressAndPlaceFuel,
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
                cameraAddressAndPlace = FakeData.addressAndPlaceStreet,
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
                activeSessions = listOf(FakeData.activeSession),
                userGpsPoint = GpsPoint(40.4165, -3.7030, 12f, Clock.System.now().toEpochMilliseconds(), 0f),
                nearbySpots = FakeData.nearbySpots,
                selectedItemId = FakeData.activeSession.id,
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
                activeSessions = listOf(FakeData.activeSessionSupermarket),
                userGpsPoint = GpsPoint(40.4165, -3.7030, 12f, Clock.System.now().toEpochMilliseconds(), 0f),
                nearbySpots = FakeData.nearbySpots,
                selectedItemId = FakeData.activeSessionSupermarket.id,
            ),
        )
    }
}

// ─── A — HomeVehicleCard (single vehicle, full-width) ─────────────────────────

@Preview(name = "A — HomeVehicleCard: aparcado + dirección (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeVehicleCardParkedPoiDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        Column(Modifier.padding(16.dp)) {
            HomeVehicleCard(
                card = VehicleCard(vehicle = FakeData.vehicleSedan, session = FakeData.activeSession),
                userLocation = 40.417 to -3.708,
                onClick = {},
            )
        }
    }
}

@Preview(name = "A — HomeVehicleCard: aparcado sin dirección (claro)", showBackground = true)
@Composable
private fun HomeVehicleCardParkedNoAddressLightPreview() {
    PaparcarTheme(darkTheme = false) {
        Column(Modifier.padding(16.dp)) {
            HomeVehicleCard(
                card = VehicleCard(
                    vehicle = FakeData.vehicleSedan,
                    session = FakeData.activeSession.copy(address = null, placeInfo = null),
                ),
                onClick = {},
            )
        }
    }
}

@Preview(name = "A — HomeVehicleCard: sin marcar (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeVehicleCardUnmarkedDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        Column(Modifier.padding(16.dp)) {
            HomeVehicleCard(
                card = VehicleCard(vehicle = FakeData.vehicleSedan, session = null),
                onClick = {},
            )
        }
    }
}

// Driving state — live "Conduciendo" card with radar halo (animation only runs in interactive
// preview / on device; static render shows the blue border + label). [CHIP-DRIVING-001]
@Preview(name = "A — HomeVehicleCard: driving (claro)", showBackground = true)
@Composable
private fun HomeVehicleCardDrivingLightPreview() {
    PaparcarTheme(darkTheme = false) {
        Column(Modifier.padding(16.dp)) {
            HomeVehicleCard(
                card = VehicleCard(vehicle = FakeData.vehicleSedan, session = null),
                isDriving = true,
                onClick = {},
            )
        }
    }
}

// ─── A — HomeVehicleChip (2+ vehicles, compact strip) ─────────────────────────
// Status icon before the name (green active / blue BT / grey inactive); foot = address (parked)
// or the "not marked" glyph. [HOME-VEH-REFINE-001]

@Preview(name = "A — HomeVehicleChip: estados (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 620)
@Composable
private fun HomeVehicleChipStatesDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            // Active + parked → green icon + address
            HomeVehicleChip(
                card = VehicleCard(vehicle = FakeData.vehicleSedan, session = FakeData.activeSession),
                onClick = {},
            )
            // Bluetooth + not marked → blue icon + "Sin marcar"
            HomeVehicleChip(
                card = VehicleCard(vehicle = FakeData.vehicleCorolla, session = null),
                onClick = {},
            )
            // Inactive + parked → grey icon + address
            HomeVehicleChip(
                card = VehicleCard(vehicle = FakeData.vehicleMoto, session = FakeData.activeSessionSupermarket),
                onClick = {},
            )
        }
    }
}

@Preview(name = "A — HomeVehicleChip: estados (claro)", showBackground = true, widthDp = 620)
@Composable
private fun HomeVehicleChipStatesLightPreview() {
    PaparcarTheme(darkTheme = false) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            HomeVehicleChip(
                card = VehicleCard(vehicle = FakeData.vehicleCorolla, session = FakeData.activeSession),
                onClick = {},
            )
            HomeVehicleChip(
                card = VehicleCard(vehicle = FakeData.vehicleSedan, session = null),
                isDriving = true,
                onClick = {},
            )
        }
    }
}

// ─── A — PapSectionHeader ─────────────────────────────────────────────────────

@Preview(name = "A — PapSectionHeader (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PapSectionHeaderDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        Column(Modifier.padding(16.dp)) { PapSectionHeader(title = "Cerca de ti") }
    }
}

@Preview(name = "A — PapSectionHeader (claro)", showBackground = true)
@Composable
private fun PapSectionHeaderLightPreview() {
    PaparcarTheme(darkTheme = false) {
        Column(Modifier.padding(16.dp)) { PapSectionHeader(title = "Estás aparcado") }
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

// ─── A — PaparcarBottomActionBar ──────────────────────────────────────────────

@Preview(name = "A — PaparcarBottomActionBar: spot seleccionado (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PaparcarBottomActionBarSpotDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        PaparcarBottomActionBar(label = "⛽ Repsol Av. Castellana  ·  Av. de la Castellana 110", onClick = {})
    }
}

@Preview(name = "A — PaparcarBottomActionBar: parking seleccionado (claro)", showBackground = true)
@Composable
private fun PaparcarBottomActionBarParkingLightPreview() {
    PaparcarTheme(darkTheme = false) {
        PaparcarBottomActionBar(label = "Tu coche  ·  Calle Gran Vía 32", onClick = {})
    }
}

// ─── A — HomeSheetContent: pantallas completas ────────────────────────────────

@Composable
private fun PreviewSheet(state: HomeState) {
    val lazyListState = rememberLazyListState()
    LazyColumn(state = lazyListState, modifier = Modifier.fillMaxSize()) {
        homeSheetItems(
            state = state,
            onIntent = {},
            onCameraMove = { _, _ -> },
            onParkingClick = {},
            onParkVehicle = {},
            onSpotSelect = { _, _, _ -> },
            onEnterReportMode = {},
        )
    }
}

@Preview(name = "A — Sheet: coche + spots (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES, heightDp = 600)
@Composable
private fun HomeSheetContentWithParkingAndSpotsDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        PreviewSheet(
            state = HomeState(
                hasCorePermissions = true,
                activeSessions = listOf(FakeData.activeSession),
                userGpsPoint = GpsPoint(40.4165, -3.7030, 12f, Clock.System.now().toEpochMilliseconds(), 0f),
                nearbySpots = FakeData.nearbySpots,
            ),
        )
    }
}

@Preview(name = "A — Sheet: coche + spots (claro)", showBackground = true, heightDp = 600)
@Composable
private fun HomeSheetContentWithParkingAndSpotsLightPreview() {
    PaparcarTheme(darkTheme = false) {
        PreviewSheet(
            state = HomeState(
                hasCorePermissions = true,
                activeSessions = listOf(FakeData.activeSessionSupermarket),
                userGpsPoint = GpsPoint(40.4165, -3.7030, 12f, Clock.System.now().toEpochMilliseconds(), 0f),
                nearbySpots = FakeData.nearbySpots,
            ),
        )
    }
}

@Preview(name = "A — Sheet: sin coche, spots primero (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES, heightDp = 600)
@Composable
private fun HomeSheetContentSpotsFirstDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        PreviewSheet(
            state = HomeState(
                hasCorePermissions = true,
                userGpsPoint = GpsPoint(40.4165, -3.7030, 12f, Clock.System.now().toEpochMilliseconds(), 0f),
                nearbySpots = FakeData.nearbySpots,
            ),
        )
    }
}

@Preview(name = "A — Sheet: sin coche, spots primero (claro)", showBackground = true, heightDp = 600)
@Composable
private fun HomeSheetContentSpotsFirstLightPreview() {
    PaparcarTheme(darkTheme = false) {
        PreviewSheet(
            state = HomeState(
                hasCorePermissions = true,
                userGpsPoint = GpsPoint(40.4165, -3.7030, 12f, Clock.System.now().toEpochMilliseconds(), 0f),
                nearbySpots = FakeData.nearbySpots,
            ),
        )
    }
}

@Preview(name = "A — Sheet: sin coche, 0 spots, empty state (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES, heightDp = 500)
@Composable
private fun HomeSheetContentEmptyDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        PreviewSheet(
            state = HomeState(hasCorePermissions = true, nearbySpots = emptyList()),
        )
    }
}

@Preview(name = "A — Sheet: sin permisos (claro)", showBackground = true, heightDp = 400)
@Composable
private fun HomeSheetContentNoPermissionsLightPreview() {
    PaparcarTheme(darkTheme = false) {
        PreviewSheet(
            state = HomeState(hasCorePermissions = false, nearbySpots = emptyList()),
        )
    }
}