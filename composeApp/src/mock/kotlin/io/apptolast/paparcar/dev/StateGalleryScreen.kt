package io.apptolast.paparcar.dev

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import io.apptolast.paparcar.presentation.home.sections.sheet.components.homeSheetItems
import io.apptolast.paparcar.presentation.onboarding.OnboardingScreen
import io.apptolast.paparcar.presentation.permissions.PermissionsContent
import io.apptolast.paparcar.presentation.permissions.PermissionsState
import io.apptolast.paparcar.presentation.preview.FakeData
import io.apptolast.paparcar.presentation.settings.SettingsContent
import io.apptolast.paparcar.presentation.settings.SettingsState
import io.apptolast.paparcar.presentation.vehicleregistration.VehicleRegistrationContent
import io.apptolast.paparcar.presentation.vehicleregistration.VehicleRegistrationState
import io.apptolast.paparcar.presentation.vehicles.VehiclesContent
import io.apptolast.paparcar.presentation.vehicles.VehiclesState
import io.apptolast.paparcar.ui.theme.PaparcarTheme

/** One renderable screen state. [content] must NOT wrap a theme — the gallery owns the theme. */
private class Variant(val name: String, val content: @Composable () -> Unit)
private class ScreenGroup(val title: String, val variants: List<Variant>)

private val sampleProfile = UserProfile(
    userId = "u1",
    email = "user@paparcar.app",
    displayName = "Carlos López",
    photoUrl = null,
    createdAt = 0L,
    updatedAt = 0L,
)

/**
 * Hosts a Home detection surface on the sheet's container background, as it appears in Home.
 * (Full HomeContent is private + map-bound, so the gallery shows the detection surface in
 * isolation — that's where the no-permission / no-vehicle / coordinator-running states live.)
 */
@Composable
private fun DetectionHost(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
private fun detectionSurface(state: DetectionUiState) {
    DetectionHost {
        HomeDetectionSurface(
            state = state,
            onAddVehicle = {},
            onOpenPermissions = {},
            onMarkSpot = {},
            onStartDrivingDetection = {},
            allowDrivingDetection = true,
        )
    }
}

private val sampleGps = GpsPoint(40.4165, -3.7030, 12f, 0L, 0f)

/** Renders a Home peek (collapsed handle) on the sheet container background. */
@Composable
private fun peek(state: HomeState) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)) {
        HomePeekHandle(state = state)
    }
}

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

// Recipes mirror the existing *Previews.kt so the gallery shows the same curated states on-device.
private val galleryGroups: List<ScreenGroup> = listOf(
    ScreenGroup(
        "Home · detección",
        listOf(
            Variant("Sin permiso CORE (BlockedCore)") { detectionSurface(DetectionUiState.BlockedCore) },
            Variant("Sin permiso detección (BlockedProducer)") { detectionSurface(DetectionUiState.BlockedProducer) },
            Variant("Sin coche registrado (NoVehicle)") { detectionSurface(DetectionUiState.NoVehicle) },
            Variant("Sin aparcar aún (AwaitingFirstPark)") { detectionSurface(DetectionUiState.AwaitingFirstPark) },
            Variant("Coordinator corriendo (Monitoring)") {
                DetectionHost {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        MonitoringPillContent(elapsedLabel = "4 min")
                    }
                }
            },
        ),
    ),
    ScreenGroup(
        "Home · peek / sheet",
        listOf(
            Variant("Peek · spots cerca (POI)") {
                peek(HomeState(cameraAddressAndPlace = FakeData.addressAndPlaceFuel, nearbySpots = FakeData.nearbySpots))
            },
            Variant("Peek · dirección simple") {
                peek(HomeState(cameraAddressAndPlace = FakeData.addressAndPlaceStreet, nearbySpots = FakeData.nearbySpots))
            },
            Variant("Peek · spot seleccionado") {
                peek(
                    HomeState(
                        nearbySpots = FakeData.nearbySpots,
                        userGpsPoint = sampleGps,
                        selectedItemId = FakeData.nearbySpots.first().id,
                    ),
                )
            },
            Variant("Peek · plaza propia seleccionada") {
                peek(
                    HomeState(
                        activeSessions = listOf(FakeData.activeSession),
                        userGpsPoint = sampleGps,
                        nearbySpots = FakeData.nearbySpots,
                        selectedItemId = FakeData.activeSession.id,
                    ),
                )
            },
            Variant("Peek · cargando (skeleton)") { peek(HomeState()) },
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
            Variant("Sheet · sin spots (vacío)") {
                sheet(HomeState(hasCorePermissions = true, nearbySpots = emptyList()))
            },
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
            Variant("Lista") { VehiclesContent(state = VehiclesState(vehicles = FakeData.vehiclesWithStats, isLoading = false)) },
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
        var dark by remember { mutableStateOf(false) }
        BackHandler { selected = null }
        PaparcarTheme(darkTheme = dark) {
            Box(Modifier.fillMaxSize()) {
                current.content()
                // Overlay controls — kept compact + at opposite corners to minimise clash with
                // each screen's own chrome. Hardware/gesture back also returns to the list.
                ElevatedButton(
                    onClick = { selected = null },
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                ) { Text("← Lista") }
                ElevatedButton(
                    onClick = { dark = !dark },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                ) { Text(if (dark) "☀ Claro" else "🌙 Oscuro") }
            }
        }
    }
}
