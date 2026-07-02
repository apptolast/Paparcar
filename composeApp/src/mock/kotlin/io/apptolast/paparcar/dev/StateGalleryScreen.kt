package io.apptolast.paparcar.dev

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.connectivity.ConnectivityBannerPhase
import io.apptolast.paparcar.domain.model.CarbodyType
import io.apptolast.paparcar.domain.model.UserProfile
import io.apptolast.paparcar.domain.model.VehicleColor
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.model.VehicleType
import io.apptolast.paparcar.domain.preferences.ThemeMode
import io.apptolast.paparcar.presentation.bluetooth.BluetoothConfigContent
import io.apptolast.paparcar.presentation.bluetooth.BluetoothConfigState
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.presentation.home.HomeState
import io.apptolast.paparcar.presentation.home.model.DetectionUiState
import io.apptolast.paparcar.presentation.home.sections.map.components.MonitoringPillContent
import io.apptolast.paparcar.presentation.home.sections.sheet.components.HomeDetectionSurface
import io.apptolast.paparcar.presentation.home.sections.sheet.components.HomePeekHandle
import io.apptolast.paparcar.presentation.home.sections.sheet.components.SpotFitRow
import io.apptolast.paparcar.presentation.home.sections.sheet.components.homeSheetItems
import io.apptolast.paparcar.presentation.onboarding.OnboardingScreen
import io.apptolast.paparcar.presentation.permissions.PermissionsContent
import io.apptolast.paparcar.presentation.permissions.PermissionsState
import io.apptolast.paparcar.presentation.preview.FakeData
import io.apptolast.paparcar.presentation.settings.SettingsContent
import io.apptolast.paparcar.presentation.settings.SettingsState
import io.apptolast.paparcar.presentation.vehicleregistration.VehicleRegistrationContent
import io.apptolast.paparcar.presentation.vehicleregistration.VehicleRegistrationState
import io.apptolast.paparcar.presentation.vehicles.HistoryContent
import io.apptolast.paparcar.presentation.vehicles.HistoryFilter
import io.apptolast.paparcar.presentation.vehicles.HistoryState
import io.apptolast.paparcar.presentation.vehicles.VehiclesContent
import io.apptolast.paparcar.presentation.vehicles.VehiclesState
import io.apptolast.paparcar.ui.components.ConnectivityBanner
import io.apptolast.paparcar.ui.theme.PaparcarTheme

/**
 * How a variant should be presented in the viewer.
 * - [FullScreen]: the composable already fills the screen (Settings, History, the expanded sheet…).
 * - [Surface]: a partial Home surface (detection card, peek handle, SpotFit row, monitoring pill).
 *   Shown bottom-anchored like Home's sheet by default ("Completa"), with a "Solo" toggle to inspect
 *   the bare composable centered.
 */
private enum class Placement { FullScreen, Surface }

/** One renderable screen state. [content] is the RAW composable — the viewer owns theme + host. */
private class Variant(
    val name: String,
    val placement: Placement = Placement.FullScreen,
    val content: @Composable () -> Unit,
)
private class ScreenGroup(val title: String, val variants: List<Variant>)

private val sampleProfile = UserProfile(
    userId = "u1",
    email = "user@paparcar.app",
    displayName = "Carlos López",
    photoUrl = null,
    createdAt = 0L,
    updatedAt = 0L,
)

// Full HomeContent is private + map-bound, so the gallery renders the partial Home surfaces
// (detection card / peek / SpotFit / monitoring pill) on their own; the viewer hosts them
// bottom-anchored (Placement.Surface) so they read like Home's sheet.
@Composable
private fun detectionSurface(state: DetectionUiState) {
    HomeDetectionSurface(
        state = state,
        onAddVehicle = {},
        onOpenPermissions = {},
        onMarkSpot = {},
        onStartDrivingDetection = {},
        onActivateDetection = {},
        allowDrivingDetection = true,
    )
}

private val sampleGps = GpsPoint(40.4165, -3.7030, 12f, 0L, 0f)

@Composable
private fun peek(state: HomeState) = HomePeekHandle(state = state)

/** Renders the expanded Home sheet (spots list + own-parking card) via homeSheetItems. */
@Composable
private fun sheet(state: HomeState) {
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)) {
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

@Composable
private fun history(state: HistoryState) {
    HistoryContent(
        state = state,
        contentPadding = PaddingValues(0.dp),
        onViewOnMap = { _, _, _ -> },
        onFilterSelected = {},
    )
}

// SpotFit outcomes derived from computeSpotFit(spot, vehicle): same body = OPTIMAL,
// car ≤ spot length = FITS, car > spot = DOES_NOT_FIT, spot without size = UNKNOWN.
private fun fitSpot(size: VehicleSize?, carbody: CarbodyType? = null) =
    FakeData.nearbySpots.first().copy(sizeCategory = size, carbodyType = carbody)
private fun fitVehicle(size: VehicleSize, carbody: CarbodyType? = null) =
    FakeData.vehicleSedan.copy(sizeCategory = size, carbodyType = carbody)

@Composable
private fun spotFit(spot: io.apptolast.paparcar.domain.model.Spot, vehicle: io.apptolast.paparcar.domain.model.Vehicle) =
    SpotFitRow(spot = spot, vehicle = vehicle)

// Recipes mirror the existing *Previews.kt so the gallery shows the same curated states on-device.
private val galleryGroups: List<ScreenGroup> = listOf(
    ScreenGroup(
        "Home · detección",
        listOf(
            Variant("Sin permiso CORE (BlockedCore)", Placement.Surface) { detectionSurface(DetectionUiState.BlockedCore) },
            Variant("Detección inactiva — flag off o permisos (Inactive)", Placement.Surface) { detectionSurface(DetectionUiState.Inactive) },
            Variant("Sin coche registrado (NoVehicle)", Placement.Surface) { detectionSurface(DetectionUiState.NoVehicle) },
            Variant("Sin aparcar aún (AwaitingFirstPark)", Placement.Surface) { detectionSurface(DetectionUiState.AwaitingFirstPark) },
            Variant("Coordinator corriendo (Monitoring · conduciendo)", Placement.Surface) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    MonitoringPillContent(elapsedLabel = "4 min")
                }
            },
            Variant("Coordinator corriendo (Monitoring · aparcando)", Placement.Surface) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    MonitoringPillContent(
                        elapsedLabel = "4 min",
                        phase = io.apptolast.paparcar.domain.detection.DetectionPhase.Candidate,
                    )
                }
            },
        ),
    ),
    ScreenGroup(
        "Conectividad · banner",
        listOf(
            Variant("Sin conexión (Offline · persistente, rojo)") {
                Column(Modifier.fillMaxSize()) { ConnectivityBanner(ConnectivityBannerPhase.Offline) }
            },
            Variant("Conexión restablecida (Restored · verde, ~2,5s)") {
                Column(Modifier.fillMaxSize()) { ConnectivityBanner(ConnectivityBannerPhase.Restored) }
            },
        ),
    ),
    ScreenGroup(
        "Home · peek / sheet",
        listOf(
            Variant("Peek · spots cerca (POI)", Placement.Surface) {
                peek(HomeState(cameraAddressAndPlace = FakeData.addressAndPlaceFuel, nearbySpots = FakeData.nearbySpots))
            },
            Variant("Peek · dirección simple", Placement.Surface) {
                peek(HomeState(cameraAddressAndPlace = FakeData.addressAndPlaceStreet, nearbySpots = FakeData.nearbySpots))
            },
            // Empty discovery: the badge reads a calm "Sin plazas cerca", not a dead grey "0". [FOCUS-003]
            Variant("Peek · sin plazas (vacío)", Placement.Surface) {
                peek(HomeState(cameraAddressAndPlace = FakeData.addressAndPlaceStreet, nearbySpots = emptyList()))
            },
            Variant("Peek · spot seleccionado", Placement.Surface) {
                peek(
                    HomeState(
                        nearbySpots = FakeData.nearbySpots,
                        userGpsPoint = sampleGps,
                        selectedItemId = FakeData.nearbySpots.first().id,
                    ),
                )
            },
            Variant("Peek · plaza propia seleccionada", Placement.Surface) {
                peek(
                    HomeState(
                        activeSessions = listOf(FakeData.activeSession),
                        userGpsPoint = sampleGps,
                        nearbySpots = FakeData.nearbySpots,
                        selectedItemId = FakeData.activeSession.id,
                    ),
                )
            },
            Variant("Peek · cargando (skeleton)", Placement.Surface) { peek(HomeState()) },
            Variant("Sheet · coche + spots") {
                sheet(
                    HomeState(
                        hasCorePermissions = true,
                        activeSessions = listOf(FakeData.activeSession),
                        userGpsPoint = sampleGps,
                        nearbySpots = FakeData.nearbySpots,
                    ),
                )
            },
            Variant("Sheet · spots primero (sin coche)") {
                sheet(HomeState(hasCorePermissions = true, userGpsPoint = sampleGps, nearbySpots = FakeData.nearbySpots))
            },
            // Driving chip: the monitored vehicle's trip is in progress (drivingPuck.vehicleId == its
            // id, no active session) → chip shows "Conduciendo" + radar halo, floated first. [CHIP-DRIVING-001]
            Variant("Sheet · coche conduciendo (driving)") {
                sheet(
                    HomeState(
                        hasCorePermissions = true,
                        userGpsPoint = sampleGps,
                        vehicles = listOf(FakeData.vehicleSedan),
                        nearbySpots = FakeData.nearbySpots,
                        drivingPuck = io.apptolast.paparcar.domain.model.DrivingPuck(
                            latitude = sampleGps.latitude,
                            longitude = sampleGps.longitude,
                            bearingDegrees = 42f,
                            accuracy = 8f,
                            carbodyType = FakeData.vehicleSedan.carbodyType,
                            sizeCategory = FakeData.vehicleSedan.sizeCategory,
                            color = FakeData.vehicleSedan.color,
                            vehicleId = FakeData.vehicleSedan.id,
                        ),
                    ),
                )
            },
            // Candidate phase: stopped + walking away → chip flips to "Aparcando…" in green. [DET-PHASE-001]
            Variant("Sheet · coche candidato (aparcando)") {
                sheet(
                    HomeState(
                        hasCorePermissions = true,
                        userGpsPoint = sampleGps,
                        vehicles = listOf(FakeData.vehicleSedan),
                        nearbySpots = FakeData.nearbySpots,
                        drivingPuck = io.apptolast.paparcar.domain.model.DrivingPuck(
                            latitude = sampleGps.latitude,
                            longitude = sampleGps.longitude,
                            bearingDegrees = 42f,
                            accuracy = 8f,
                            carbodyType = FakeData.vehicleSedan.carbodyType,
                            sizeCategory = FakeData.vehicleSedan.sizeCategory,
                            color = FakeData.vehicleSedan.color,
                            vehicleId = FakeData.vehicleSedan.id,
                            phase = io.apptolast.paparcar.domain.detection.DetectionPhase.Candidate,
                        ),
                    ),
                )
            },
            // Single vehicle → full-width HomeVehicleCard: identity + status pin + size chip, and a
            // footer with the parked address (location icon + "Aparcado en …" + chevron). [HOME-VEH-REFINE-001]
            Variant("Sheet · 1 coche aparcado (card + dirección)") {
                sheet(
                    HomeState(
                        hasCorePermissions = true,
                        userGpsPoint = sampleGps,
                        vehicles = listOf(FakeData.vehicleSedan),
                        activeSessions = listOf(FakeData.activeSession.copy(vehicleId = FakeData.vehicleSedan.id)),
                        nearbySpots = FakeData.nearbySpots,
                    ),
                )
            },
            // 2+ vehicles → compact chips. Status ICON before the name (green active / blue BT / grey
            // inactive); foot = address (parked) or the "Sin marcar" glyph. Spots stay visible. [HOME-VEH-REFINE-001]
            Variant("Sheet · chips mixtos (aparcado + sin marcar)") {
                sheet(
                    HomeState(
                        hasCorePermissions = true,
                        userGpsPoint = sampleGps,
                        vehicles = listOf(
                            FakeData.vehicleSedan,   // activo + aparcado → icono verde + dirección
                            FakeData.vehicleCorolla, // BT + sin marcar → icono azul + "Sin marcar"
                            FakeData.vehicleMoto,    // inactivo + aparcado → icono gris + dirección
                        ),
                        activeSessions = listOf(
                            FakeData.activeSession.copy(vehicleId = FakeData.vehicleSedan.id),
                            FakeData.activeSessionSupermarket.copy(vehicleId = FakeData.vehicleMoto.id),
                        ),
                        nearbySpots = FakeData.nearbySpots,
                    ),
                )
            },
            // All unmarked → every chip shows the "Sin marcar" glyph across the three status colours.
            Variant("Sheet · chips sin marcar (BT + activo/inactivo)") {
                sheet(
                    HomeState(
                        hasCorePermissions = true,
                        userGpsPoint = sampleGps,
                        vehicles = listOf(
                            FakeData.vehicleSedan,   // activo, sin BT → icono verde
                            FakeData.vehicleCorolla, // BT → icono azul
                            FakeData.vehicleMoto,    // inactivo, sin BT → icono gris
                            FakeData.vehicleVan,     // BT → icono azul
                        ),
                        nearbySpots = FakeData.nearbySpots,
                    ),
                )
            },
            Variant("Sheet · sin spots (vacío)") {
                sheet(HomeState(hasCorePermissions = true, nearbySpots = emptyList()))
            },
        ),
    ),
    ScreenGroup(
        "Home · compatibilidad (SpotFit)",
        listOf(
            Variant("OPTIMAL (mismo carrocería)", Placement.Surface) {
                spotFit(
                    fitSpot(VehicleSize.MEDIUM_SUV, CarbodyType.HATCHBACK_MEDIUM),
                    fitVehicle(VehicleSize.MEDIUM_SUV, CarbodyType.HATCHBACK_MEDIUM),
                )
            },
            Variant("FITS (coche ≤ plaza)", Placement.Surface) {
                spotFit(fitSpot(VehicleSize.MEDIUM_SUV), fitVehicle(VehicleSize.MICRO_SMALL))
            },
            Variant("DOES_NOT_FIT (coche > plaza)", Placement.Surface) {
                spotFit(fitSpot(VehicleSize.MICRO_SMALL), fitVehicle(VehicleSize.VAN_HIGH))
            },
            Variant("UNKNOWN (plaza sin tamaño)", Placement.Surface) {
                spotFit(fitSpot(null), fitVehicle(VehicleSize.MEDIUM_SUV))
            },
        ),
    ),
    ScreenGroup(
        "Historial",
        listOf(
            Variant("Lista (con sesiones)") {
                history(HistoryState(sessions = FakeData.allSessions, filteredSessions = FakeData.allSessions))
            },
            Variant("Filtro: esta semana") {
                history(
                    HistoryState(
                        sessions = FakeData.allSessions,
                        filteredSessions = FakeData.allSessions,
                        activeFilter = HistoryFilter.ThisWeek,
                    ),
                )
            },
            Variant("Vacío") { history(HistoryState()) },
            Variant("Cargando") { history(HistoryState(isLoading = true)) },
        ),
    ),
    ScreenGroup(
        "Settings",
        listOf(
            Variant("Con perfil") { SettingsContent(state = SettingsState(userProfile = sampleProfile)) },
            Variant("Sin perfil") { SettingsContent(state = SettingsState(userProfile = null)) },
            Variant("Detección/notif off, imperial") {
                SettingsContent(
                    state = SettingsState(
                        userProfile = sampleProfile,
                        autoDetectParking = false,
                        notifyParkingDetected = false,
                    ),
                    themeMode = ThemeMode.DARK,
                    imperialUnits = true,
                )
            },
            Variant("Diálogo borrar cuenta") {
                SettingsContent(state = SettingsState(userProfile = sampleProfile, showDeleteAccountConfirmation = true))
            },
        ),
    ),
    ScreenGroup(
        "Vehicles",
        listOf(
            // Full history per vehicle so the activity chart + filter bar + timeline actually render
            // (the hero card alone doesn't exercise the History section). [VEHICLES-REDESIGN-001]
            Variant("Lista") {
                val history = FakeData.vehiclesWithStats.associate { vws ->
                    vws.vehicle.id to HistoryState(
                        sessions = FakeData.allSessions,
                        activeFilter = HistoryFilter.All,
                        filteredSessions = FakeData.allSessions,
                    )
                }
                VehiclesContent(
                    state = VehiclesState(
                        vehicles = FakeData.vehiclesWithStats,
                        isLoading = false,
                        historyCache = history,
                    ),
                )
            },
            // Low-data: a single session in the window → the chart collapses to the compact summary
            // instead of a near-empty full-height chart. [VEHICLES-REDESIGN-001 · Task 3]
            Variant("Pocos datos") {
                val oneSession = FakeData.endedSessions.take(1)
                val history = FakeData.vehiclesWithStats.associate { vws ->
                    vws.vehicle.id to HistoryState(
                        sessions = oneSession,
                        activeFilter = HistoryFilter.All,
                        filteredSessions = oneSession,
                    )
                }
                VehiclesContent(
                    state = VehiclesState(
                        vehicles = FakeData.vehiclesWithStats,
                        isLoading = false,
                        historyCache = history,
                    ),
                )
            },
            // Bluetooth ficha (page 1 = Corolla): blue status pin, no method label. [HOME-VEH-REFINE-001]
            Variant("Ficha Bluetooth") {
                val history = FakeData.vehiclesWithStats.associate { vws ->
                    vws.vehicle.id to HistoryState(
                        sessions = FakeData.allSessions,
                        activeFilter = HistoryFilter.All,
                        filteredSessions = FakeData.allSessions,
                    )
                }
                VehiclesContent(
                    state = VehiclesState(
                        vehicles = FakeData.vehiclesWithStats,
                        isLoading = false,
                        selectedVehicleIndex = 1,
                        historyCache = history,
                    ),
                )
            },
            // Inactive ficha (page 2 = Moto): grey pin, MUTED stats it still keeps, plus the separate
            // "Establecer como activo" row (absent for active / BT vehicles). [HOME-VEH-REFINE-001]
            Variant("Ficha inactiva (métricas atenuadas + activar)") {
                val history = FakeData.vehiclesWithStats.associate { vws ->
                    vws.vehicle.id to HistoryState(
                        sessions = FakeData.allSessions,
                        activeFilter = HistoryFilter.All,
                        filteredSessions = FakeData.allSessions,
                    )
                }
                VehiclesContent(
                    state = VehiclesState(
                        vehicles = FakeData.vehiclesWithStats,
                        isLoading = false,
                        selectedVehicleIndex = 2,
                        historyCache = history,
                    ),
                )
            },
            Variant("Vacío") { VehiclesContent(state = VehiclesState(vehicles = emptyList(), isLoading = false)) },
            Variant("Cargando") { VehiclesContent(state = VehiclesState(isLoading = true)) },
        ),
    ),
    ScreenGroup(
        "Permisos",
        listOf(
            Variant("Todo denegado") { PermissionsContent(state = PermissionsState(), onRequestPermissions = {}) },
            Variant("Parcial (falta background)") {
                PermissionsContent(
                    state = PermissionsState(
                        hasFineLocation = true,
                        hasActivityRecognition = true,
                        hasNotifications = true,
                        isLocationServicesEnabled = true,
                        hasBackgroundLocation = false,
                    ),
                    onRequestPermissions = {},
                )
            },
            Variant("Críticos concedidos") {
                PermissionsContent(
                    state = PermissionsState(
                        hasFineLocation = true,
                        hasBackgroundLocation = true,
                        hasActivityRecognition = true,
                        hasNotifications = true,
                        isLocationServicesEnabled = true,
                    ),
                    onRequestPermissions = {},
                )
            },
            Variant("Todo + Bluetooth") {
                PermissionsContent(
                    state = PermissionsState(
                        hasFineLocation = true,
                        hasBackgroundLocation = true,
                        hasActivityRecognition = true,
                        hasNotifications = true,
                        isLocationServicesEnabled = true,
                        hasBluetoothConnect = true,
                    ),
                    onRequestPermissions = {},
                )
            },
            Variant("Prompt de ajustes") {
                PermissionsContent(state = PermissionsState(showSettingsPrompt = true), onRequestPermissions = {})
            },
            // [DET-TOGGLE-002] Diálogo educativo "Maybe later" — core+GPS concedidos, producer pendiente.
            Variant("Diálogo saltar detección") {
                PermissionsContent(
                    state = PermissionsState(
                        hasFineLocation = true,
                        isLocationServicesEnabled = true,
                        hasBackgroundLocation = false,
                        showSkipDetectionDialog = true,
                    ),
                    onRequestPermissions = {},
                )
            },
            Variant("Tarjeta autostart (OEM)") {
                PermissionsContent(
                    state = PermissionsState(
                        hasFineLocation = true,
                        hasBackgroundLocation = true,
                        hasActivityRecognition = true,
                        hasNotifications = true,
                        isLocationServicesEnabled = true,
                        isBatteryOptimizationExempt = true,
                        showAutostartCard = true,
                    ),
                    onRequestPermissions = {},
                )
            },
        ),
    ),
    ScreenGroup(
        "Registro de vehículo",
        listOf(
            Variant("Nuevo") { VehicleRegistrationContent(state = VehicleRegistrationState()) },
            Variant("Error de validación (marca vacía)") {
                VehicleRegistrationContent(state = VehicleRegistrationState(hasInteractedWithForm = true))
            },
            Variant("Edición") {
                VehicleRegistrationContent(
                    state = VehicleRegistrationState(
                        editingVehicleId = "v-edit",
                        brand = "Toyota",
                        model = "Corolla",
                        sizeCategory = VehicleSize.MEDIUM_SUV,
                        showBrandModelOnSpot = true,
                    ),
                )
            },
            Variant("Con color") {
                VehicleRegistrationContent(
                    state = VehicleRegistrationState(
                        editingVehicleId = "v-color",
                        brand = "Seat",
                        model = "León",
                        vehicleType = VehicleType.CAR,
                        carbodyType = CarbodyType.HATCHBACK_MEDIUM,
                        sizeCategory = VehicleSize.MEDIUM_SUV,
                        color = VehicleColor.RED,
                    ),
                )
            },
            Variant("Guardando") {
                VehicleRegistrationContent(
                    state = VehicleRegistrationState(
                        brand = "Seat",
                        model = "Ibiza",
                        sizeCategory = VehicleSize.MICRO_SMALL,
                        isSaving = true,
                    ),
                )
            },
        ),
    ),
    ScreenGroup(
        "Bluetooth",
        listOf(
            Variant("Lista de dispositivos") {
                BluetoothConfigContent(
                    state = BluetoothConfigState(
                        vehicleName = "Toyota Corolla",
                        bondedDevices = FakeData.btDevices,
                        selectedAddress = FakeData.btDevices.first().address,
                        currentDeviceAddress = FakeData.btDevices.first().address,
                        isBluetoothEnabled = true,
                        isLoading = false,
                    ),
                )
            },
            Variant("Sin dispositivos") {
                BluetoothConfigContent(
                    state = BluetoothConfigState(
                        vehicleName = "Ford Transit",
                        bondedDevices = emptyList(),
                        isBluetoothEnabled = true,
                        isLoading = false,
                    ),
                )
            },
            Variant("BT desactivado") {
                BluetoothConfigContent(
                    state = BluetoothConfigState(vehicleName = "Toyota Corolla", isBluetoothEnabled = false, isLoading = false),
                )
            },
            Variant("Cargando") { BluetoothConfigContent(state = BluetoothConfigState(isLoading = true)) },
        ),
    ),
    ScreenGroup(
        "Onboarding",
        listOf(
            Variant("Onboarding") { OnboardingScreen(onComplete = {}) },
        ),
    ),
)

/**
 * Mock-only static gallery: lists every screen's curated states (mirroring the `*Previews.kt`)
 * and renders each full-screen on-device, with a light/dark toggle and back-to-list. Lets you eyeball
 * loading/empty/error/populated/permission-tier variants without driving the backend.
 */
@Composable
fun StateGalleryScreen(onBack: () -> Unit) {
    var selected by remember { mutableStateOf<Variant?>(null) }

    if (selected == null) {
        BackHandler(onBack = onBack)
        PaparcarTheme(darkTheme = isSystemInDarkTheme()) {
            Surface(color = MaterialTheme.colorScheme.surface) {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    item {
                        Text(
                            "Galería de estados",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                    galleryGroups.forEach { group ->
                        item {
                            Text(
                                group.title.uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
                            )
                        }
                        items(group.variants) { variant ->
                            Surface(
                                onClick = { selected = variant },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                Text(
                                    variant.name,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                    item {
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        ElevatedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                            Text("← Volver al catálogo")
                        }
                    }
                }
            }
        }
    } else {
        val current = selected!!
        // Light/dark is driven by the global toggle (DevRoot) via the shadowed configuration.
        val dark = isSystemInDarkTheme()
        // Surface variants default to the contextual (bottom-sheet) presentation; "Solo" isolates.
        var isolated by remember(current) { mutableStateOf(false) }
        BackHandler { selected = null }
        PaparcarTheme(darkTheme = dark) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                when (current.placement) {
                    Placement.FullScreen -> current.content()
                    Placement.Surface -> if (isolated) {
                        // Bare composable, centered with its own bounds visible.
                        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            ) {
                                Box(Modifier.padding(16.dp)) { current.content() }
                            }
                        }
                    } else {
                        // Contextual: anchored at the bottom like Home's sheet.
                        Column(Modifier.fillMaxSize()) {
                            Spacer(Modifier.weight(1f))
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                            ) {
                                Box(Modifier.padding(16.dp).navigationBarsPadding()) { current.content() }
                            }
                        }
                    }
                }

                // Control row — top-start, below the status bar, drawn last so it's always tappable.
                // (DEV button lives top-end at the DevRoot level.)
                Row(
                    modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ElevatedButton(onClick = { selected = null }) { Text("← Lista") }
                    if (current.placement == Placement.Surface) {
                        ElevatedButton(onClick = { isolated = !isolated }) {
                            Text(if (isolated) "Completa" else "Solo")
                        }
                    }
                }
            }
        }
    }
}
