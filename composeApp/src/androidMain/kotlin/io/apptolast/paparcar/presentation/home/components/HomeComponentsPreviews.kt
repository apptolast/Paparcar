@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.home.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.presentation.home.HomeState
import io.apptolast.paparcar.presentation.home.PARKING_ITEM_ID
import io.apptolast.paparcar.presentation.preview.FakeData
import io.apptolast.paparcar.presentation.util.distanceMeters
import io.apptolast.paparcar.presentation.util.driveTimeString
import io.apptolast.paparcar.presentation.util.formatDistance
import io.apptolast.paparcar.presentation.util.locationDisplayText
import io.apptolast.paparcar.ui.theme.PapForestDark
import io.apptolast.paparcar.ui.theme.PapGreen
import io.apptolast.paparcar.ui.theme.PaparcarTheme
import kotlin.time.Clock

// ─── Helpers comunes ──────────────────────────────────────────────────────────

/** Spot con timestamp [agoMinutes] minutos en el pasado. */
private fun fakeSpot(
    id: String,
    agoMinutes: Long,
    lat: Double = 40.418,
    lon: Double = -3.706,
) = Spot(
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

private fun fakeSpotWithPoi(id: String, agoMinutes: Long) = Spot(
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
private fun fakeSpotsVariedFreshness() = listOf(
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
            HomeFloatingHeader(onHistoryClick = {}, onSettingsClick = {})
        }
    }
}

@Preview(name = "A — HomeFloatingHeader (claro)", showBackground = true)
@Composable
private fun HomeFloatingHeaderLightPreview() {
    PaparcarTheme(darkTheme = false) {
        Column(Modifier.padding(16.dp)) {
            HomeFloatingHeader(onHistoryClick = {}, onSettingsClick = {})
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
                userLocationInfo = FakeData.locationInfoFuel,
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
                userLocationInfo = FakeData.locationInfoStreet,
                nearbySpots = FakeData.nearbySpots,
            ),
        )
    }
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
                selectedItemId = PARKING_ITEM_ID,
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
                selectedItemId = PARKING_ITEM_ID,
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            fakeSpotsVariedFreshness().forEachIndexed { i, spot ->
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            fakeSpotsVariedFreshness().forEachIndexed { i, spot ->
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
            onManualPark = {}, onSpotSelect = { _, _, _ -> },
            scrollState = scrollState, spotScrollPositions = scrollPositions,
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
            onManualPark = {}, onSpotSelect = { _, _, _ -> },
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
            onManualPark = {}, onSpotSelect = { _, _, _ -> },
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
            onManualPark = {}, onSpotSelect = { _, _, _ -> },
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
            onManualPark = {}, onSpotSelect = { _, _, _ -> },
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
            onManualPark = {}, onSpotSelect = { _, _, _ -> },
            scrollState = scrollState, spotScrollPositions = scrollPositions,
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  SECCIÓN B — PROPUESTA: Peek con LazyRow de chips horizontales
//
//  El peek reemplaza la lista vertical por tarjetas deslizables (LazyRow). Cada
//  tarjeta muestra icono P, dirección, distancia y chip de frescura en ~160dp.
//  Tocar un chip selecciona el marcador en el mapa sin necesidad de expandir
//  el sheet. El sheet expandido mantiene el layout de la Versión A.
//
//  NUEVOS COMPONENTES (solo en este archivo, no en producción):
//    · SpotChipCardB  — tarjeta compacta horizontal para el LazyRow
//    · PeekContentB   — contenido del peek: location row + LazyRow de chips
// ═══════════════════════════════════════════════════════════════════════════════

// ─── B — SpotChipCardB ────────────────────────────────────────────────────────

/**
 * Tarjeta compacta (~160dp ancho) para el LazyRow horizontal del peek (Propuesta B).
 * Muestra: icono P + chip de frescura en la fila superior, dirección en L2, distancia en L3.
 */
@Composable
private fun SpotChipCardB(
    spot: Spot,
    userLocation: Pair<Double, Double>?,
    isSelected: Boolean = false,
    onClick: () -> Unit = {},
) {
    val distanceM = userLocation?.let { (uLat, uLon) ->
        distanceMeters(uLat, uLon, spot.location.latitude, spot.location.longitude)
    }
    val displayText = locationDisplayText(
        placeInfo = spot.placeInfo,
        address = spot.address,
        lat = spot.location.latitude,
        lon = spot.location.longitude,
    )
    val ageMinutes = remember(spot.location.timestamp) {
        (Clock.System.now().toEpochMilliseconds() - spot.location.timestamp) / 60_000L
    }
    val freshnessContainer = when {
        ageMinutes < 5L  -> MaterialTheme.colorScheme.primaryContainer
        ageMinutes < 15L -> MaterialTheme.colorScheme.secondaryContainer
        else             -> MaterialTheme.colorScheme.surfaceVariant
    }
    val freshnessContent = when {
        ageMinutes < 5L  -> MaterialTheme.colorScheme.primary
        ageMinutes < 15L -> MaterialTheme.colorScheme.secondary
        else             -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    }
    val freshnessLabel = when {
        ageMinutes < 1L  -> "< 1m"
        ageMinutes < 60L -> "hace ${ageMinutes}m"
        else             -> "hace ${ageMinutes / 60L}h"
    }
    val chipBg = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    else
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
    val chipBorder = if (isSelected)
        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    else
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)

    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(160.dp)
            .border(1.dp, chipBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = chipBg,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            // Fila superior: icono P + chip de frescura
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
                            RoundedCornerShape(8.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "P",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.primary,
                    )
                }
                Surface(shape = RoundedCornerShape(6.dp), color = freshnessContainer) {
                    Text(
                        freshnessLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = freshnessContent,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
            }
            // Dirección
            Text(
                displayText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Distancia + tiempo en coche
            if (distanceM != null) {
                Text(
                    "${formatDistance(distanceM)}  ·  ${driveTimeString(distanceM)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ─── B — PeekContentB ────────────────────────────────────────────────────────

/**
 * Contenido del peek para la Propuesta B: pill + fila de location/badge + LazyRow de chips.
 * Sustituye a HomePeekHandle en estado idle (sin spot/parking seleccionado).
 */
@Composable
private fun PeekContentB(
    locationLabel: String,
    spots: List<Spot>,
    userLocation: Pair<Double, Double>?,
    selectedSpotId: String? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Drag pill
        Box(
            modifier = Modifier
                .padding(top = 10.dp, bottom = 6.dp)
                .size(width = 32.dp, height = 4.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), CircleShape)
                .align(Alignment.CenterHorizontally),
        )
        // Location + badge
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Outlined.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                locationLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (spots.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(8.dp)) {
                    Text(
                        "${spots.size} libres",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
        // LazyRow horizontal de chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(spots) { spot ->
                SpotChipCardB(
                    spot = spot,
                    userLocation = userLocation,
                    isSelected = spot.id == selectedSpotId,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
    }
}

// ─── B — Previews de componentes aislados ─────────────────────────────────────

@Preview(name = "B — SpotChipCardB: 3 frescuras en fila (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES, heightDp = 140, widthDp = 530)
@Composable
private fun SpotChipCardBRowDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SpotChipCardB(fakeSpot("c1", 0L), Pair(40.4165, -3.7030))
            SpotChipCardB(fakeSpotWithPoi("c2", 10L), Pair(40.4165, -3.7030))
            SpotChipCardB(fakeSpot("c3", 25L), Pair(40.4165, -3.7030))
        }
    }
}

@Preview(name = "B — SpotChipCardB: 3 frescuras en fila (claro)", showBackground = true,
    heightDp = 140, widthDp = 530)
@Composable
private fun SpotChipCardBRowLightPreview() {
    PaparcarTheme(darkTheme = false) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SpotChipCardB(fakeSpot("c1", 0L), Pair(40.4165, -3.7030))
            SpotChipCardB(fakeSpotWithPoi("c2", 10L), Pair(40.4165, -3.7030))
            SpotChipCardB(fakeSpot("c3", 25L), Pair(40.4165, -3.7030))
        }
    }
}

@Preview(name = "B — SpotChipCardB: seleccionado (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES, heightDp = 130, widthDp = 200)
@Composable
private fun SpotChipCardBSelectedDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        Box(Modifier.padding(12.dp)) {
            SpotChipCardB(fakeSpotWithPoi("sel", 3L), Pair(40.4165, -3.7030), isSelected = true)
        }
    }
}

@Preview(name = "B — PeekContentB: location + chips (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES, heightDp = 200)
@Composable
private fun PeekContentBDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            PeekContentB(
                locationLabel = "Salamanca, Madrid",
                spots = fakeSpotsVariedFreshness(),
                userLocation = Pair(40.4165, -3.7030),
            )
        }
    }
}

@Preview(name = "B — PeekContentB: chip seleccionado (claro)", showBackground = true, heightDp = 200)
@Composable
private fun PeekContentBSelectedLightPreview() {
    PaparcarTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            PeekContentB(
                locationLabel = "Salamanca, Madrid",
                spots = fakeSpotsVariedFreshness(),
                userLocation = Pair(40.4165, -3.7030),
                selectedSpotId = "v2",
            )
        }
    }
}

// ─── B — Pantallas completas ──────────────────────────────────────────────────

@Preview(name = "B — Pantalla: peek chips sin coche (oscuro)",
    showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES,
    widthDp = 393, heightDp = 851)
@Composable
private fun ProposalBScreenNoParkingDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        Column(Modifier.fillMaxSize()) {
            // Mapa placeholder
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFF1C2030)),
                contentAlignment = Alignment.Center,
            ) {
                Text("[mapa]", style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.15f))
            }
            // Peek con LazyRow de chips
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            ) {
                PeekContentB(
                    locationLabel = "Salamanca, Madrid",
                    spots = fakeSpotsVariedFreshness(),
                    userLocation = Pair(40.4165, -3.7030),
                )
            }
        }
    }
}

@Preview(name = "B — Pantalla: peek chips sin coche (claro)",
    showBackground = true, widthDp = 393, heightDp = 851)
@Composable
private fun ProposalBScreenNoParkingLightPreview() {
    PaparcarTheme(darkTheme = false) {
        Column(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFFE4DDD5)),
                contentAlignment = Alignment.Center,
            ) {
                Text("[mapa]", style = MaterialTheme.typography.labelSmall,
                    color = Color.Black.copy(alpha = 0.15f))
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            ) {
                PeekContentB(
                    locationLabel = "Salamanca, Madrid",
                    spots = fakeSpotsVariedFreshness(),
                    userLocation = Pair(40.4165, -3.7030),
                )
            }
        }
    }
}

@Preview(name = "B — Pantalla: chip seleccionado (oscuro)",
    showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES,
    widthDp = 393, heightDp = 851)
@Composable
private fun ProposalBScreenChipSelectedDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        Column(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFF1C2030)),
                contentAlignment = Alignment.Center,
            ) {
                Text("[mapa — marcador seleccionado]", style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.15f))
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            ) {
                PeekContentB(
                    locationLabel = "Salamanca, Madrid",
                    spots = fakeSpotsVariedFreshness(),
                    userLocation = Pair(40.4165, -3.7030),
                    selectedSpotId = "v1",
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  SECCIÓN C — PROPUESTA: Map-first con contexto sticky
//
//  El mapa es el protagonista. El peek se reduce a una barra mínima:
//    · Badge de conteo de spots ("3 libres")
//    · Resumen del coche aparcado en 1 línea (cuando existe)
//    · Botón "Ver lista" para acceder al panel de spots
//  Parking activo → tarjeta flotante pequeña sobre el mapa, no en el sheet.
//  La lista de spots aparece en modal/panel al pulsar "Ver lista".
//
//  NUEVOS COMPONENTES (solo en este archivo, no en producción):
//    · MinimalPeekBarC      — barra inferior mínima
//    · FloatingParkingCardC — tarjeta flotante de parking activo
// ═══════════════════════════════════════════════════════════════════════════════

// ─── C — MinimalPeekBarC ──────────────────────────────────────────────────────

/**
 * Barra inferior mínima para la Propuesta C.
 * Muestra el conteo de spots libres, opcionalmente el parking activo en 1 línea,
 * y un botón "Ver lista" para abrir el panel de spots completo.
 */
@Composable
private fun MinimalPeekBarC(
    spotCount: Int,
    locationLabel: String = "",
    parkingLabel: String? = null,
    parkingTime: String? = null,
    onShowList: () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Drag pill
        Box(
            modifier = Modifier
                .padding(top = 10.dp, bottom = 6.dp)
                .size(width = 32.dp, height = 4.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), CircleShape)
                .align(Alignment.CenterHorizontally),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Badge de conteo
            Surface(
                color = if (spotCount > 0) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "$spotCount",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (spotCount > 0) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    )
                    Text(
                        "libres",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (spotCount > 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                    )
                }
            }
            // Contexto: parking activo o ubicación cámara
            Column(modifier = Modifier.weight(1f)) {
                if (parkingLabel != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Filled.DirectionsCar,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            parkingLabel,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (parkingTime != null) {
                        Text(
                            parkingTime,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Outlined.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            locationLabel.ifEmpty { "Buscando ubicación..." },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            // Botón "Ver lista"
            Surface(
                onClick = onShowList,
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    "Ver lista",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

// ─── C — FloatingParkingCardC ─────────────────────────────────────────────────

/**
 * Tarjeta flotante sobre el mapa que muestra el parking activo (Propuesta C).
 * Incluye ícono de coche, dirección/POI, tiempo + distancia, y acción "Liberar".
 * Se posiciona en la parte inferior del área de mapa, encima de la barra mínima.
 */
@Composable
private fun FloatingParkingCardC(
    placeLabel: String,
    timeAgo: String,
    distanceLabel: String,
    modifier: Modifier = Modifier,
    onRelease: () -> Unit = {},
) {
    Surface(
        modifier = modifier.border(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
            RoundedCornerShape(16.dp),
        ),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 5.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Mini car marker — consistent con MyCarMarkerContent del mapa
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(PapForestDark, CircleShape)
                    .border(2.5.dp, PapGreen, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.DirectionsCar,
                    contentDescription = null,
                    tint = PapGreen,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    placeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "$timeAgo  ·  $distanceLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                )
            }
            Surface(
                onClick = onRelease,
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Text(
                    "Liberar",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                )
            }
        }
    }
}

// ─── C — Previews de componentes aislados ─────────────────────────────────────

@Preview(name = "C — MinimalPeekBarC: con coche (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES, heightDp = 120)
@Composable
private fun MinimalPeekBarCWithParkingDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            MinimalPeekBarC(
                spotCount = 3,
                parkingLabel = "⛽ Repsol Av. Castellana",
                parkingTime = "hace 30 min  ·  280m",
            )
        }
    }
}

@Preview(name = "C — MinimalPeekBarC: sin coche (claro)", showBackground = true, heightDp = 110)
@Composable
private fun MinimalPeekBarCNoParkingLightPreview() {
    PaparcarTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            MinimalPeekBarC(spotCount = 3, locationLabel = "Salamanca, Madrid")
        }
    }
}

@Preview(name = "C — MinimalPeekBarC: 0 spots (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES, heightDp = 110)
@Composable
private fun MinimalPeekBarCZeroSpotsDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            MinimalPeekBarC(spotCount = 0, locationLabel = "Gran Vía, Madrid")
        }
    }
}

@Preview(name = "C — FloatingParkingCardC (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES, heightDp = 90)
@Composable
private fun FloatingParkingCardCDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        Box(Modifier.padding(12.dp)) {
            FloatingParkingCardC(
                placeLabel = "⛽ Repsol Av. de la Castellana",
                timeAgo = "hace 30 min",
                distanceLabel = "280 m",
            )
        }
    }
}

@Preview(name = "C — FloatingParkingCardC (claro)", showBackground = true, heightDp = 90)
@Composable
private fun FloatingParkingCardCLightPreview() {
    PaparcarTheme(darkTheme = false) {
        Box(Modifier.padding(12.dp)) {
            FloatingParkingCardC(
                placeLabel = "🛒 Mercadona Fuencarral",
                timeAgo = "hace 1 h",
                distanceLabel = "420 m",
            )
        }
    }
}

// ─── C — Pantallas completas ──────────────────────────────────────────────────

@Preview(name = "C — Pantalla: con coche aparcado (oscuro)",
    showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES,
    widthDp = 393, heightDp = 851)
@Composable
private fun ProposalCScreenWithParkingDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        Column(Modifier.fillMaxSize()) {
            // Área de mapa con tarjeta flotante
            Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFF1C2030))) {
                Text(
                    "[mapa — protagonista]",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.12f),
                    modifier = Modifier.align(Alignment.Center),
                )
                // Tarjeta flotante anclada sobre la barra mínima
                FloatingParkingCardC(
                    placeLabel = "⛽ Repsol Av. de la Castellana",
                    timeAgo = "hace 30 min",
                    distanceLabel = "280 m",
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                        .fillMaxWidth(),
                )
            }
            // Barra mínima
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            ) {
                MinimalPeekBarC(
                    spotCount = 3,
                    parkingLabel = "Repsol Av. Castellana",
                    parkingTime = "hace 30 min  ·  280m",
                )
            }
        }
    }
}

@Preview(name = "C — Pantalla: sin coche, 3 spots (claro)",
    showBackground = true, widthDp = 393, heightDp = 851)
@Composable
private fun ProposalCScreenNoParkingLightPreview() {
    PaparcarTheme(darkTheme = false) {
        Column(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFFE4DDD5)),
                contentAlignment = Alignment.Center,
            ) {
                Text("[mapa — protagonista]", style = MaterialTheme.typography.labelSmall,
                    color = Color.Black.copy(alpha = 0.12f))
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            ) {
                MinimalPeekBarC(spotCount = 3, locationLabel = "Salamanca, Madrid")
            }
        }
    }
}

@Preview(name = "C — Pantalla: con coche aparcado (claro)",
    showBackground = true, widthDp = 393, heightDp = 851)
@Composable
private fun ProposalCScreenWithParkingLightPreview() {
    PaparcarTheme(darkTheme = false) {
        Column(Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFFE4DDD5))) {
                Text(
                    "[mapa — protagonista]",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Black.copy(alpha = 0.12f),
                    modifier = Modifier.align(Alignment.Center),
                )
                FloatingParkingCardC(
                    placeLabel = "🛒 Mercadona Fuencarral",
                    timeAgo = "hace 1 h",
                    distanceLabel = "420 m",
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                        .fillMaxWidth(),
                )
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            ) {
                MinimalPeekBarC(
                    spotCount = 0,
                    parkingLabel = "Mercadona Fuencarral",
                    parkingTime = "hace 1 h  ·  420m",
                )
            }
        }
    }
}