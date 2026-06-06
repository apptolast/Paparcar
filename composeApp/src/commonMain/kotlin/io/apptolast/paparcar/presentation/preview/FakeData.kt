@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.preview

import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.AddressAndPlace
import io.apptolast.paparcar.domain.model.PlaceCategory
import io.apptolast.paparcar.domain.model.PlaceInfo
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.model.VehicleType
import io.apptolast.paparcar.domain.model.VehicleWithStats
import io.apptolast.paparcar.domain.model.bluetooth.BluetoothDeviceInfo
import io.apptolast.paparcar.domain.model.bluetooth.BluetoothDeviceType
import io.apptolast.paparcar.presentation.vehicles.WeekDayStats
import kotlin.time.Clock

/**
 * Fake domain data for @Preview composables. Not used in production.
 */
internal object FakeData {

    private val now get() = Clock.System.now().toEpochMilliseconds()

    // ── GPS helpers ───────────────────────────────────────────────────────────

    private fun gps(
        agoMs: Long = 0L,
        lat: Double = 40.4168,
        lon: Double = -3.7038,
        accuracy: Float = 12f,
    ) = GpsPoint(
        latitude = lat,
        longitude = lon,
        accuracy = accuracy,
        timestamp = now - agoMs,
        speed = 0f,
    )

    // ── Addresses ─────────────────────────────────────────────────────────────

    val addrStreet = AddressInfo(
        street = "Calle Gran Vía 32",
        city = "Madrid",
        region = "Comunidad de Madrid",
        country = "España",
    )

    val addrFuel = AddressInfo(
        street = "Av. de la Castellana 110",
        city = "Madrid",
        region = "Comunidad de Madrid",
        country = "España",
    )

    val addrSupermarket = AddressInfo(
        street = "Calle Fuencarral 78",
        city = "Madrid",
        region = "Comunidad de Madrid",
        country = "España",
    )

    val addrMall = AddressInfo(
        street = "Calle Orense 4",
        city = "Madrid",
        region = "Comunidad de Madrid",
        country = "España",
    )

    val addrCafe = AddressInfo(
        street = "Calle Serrano 25",
        city = "Madrid",
        region = "Comunidad de Madrid",
        country = "España",
    )

    val addrHighAccuracy = AddressInfo(
        street = "Paseo del Prado 8",
        city = "Madrid",
        region = "Comunidad de Madrid",
        country = "España",
    )

    // ── PlaceInfo ─────────────────────────────────────────────────────────────

    val placeInfoFuel = PlaceInfo("Repsol Av. Castellana", PlaceCategory.FUEL)
    val placeInfoSupermarket = PlaceInfo("Mercadona Fuencarral", PlaceCategory.SUPERMARKET)
    val placeInfoMall = PlaceInfo("Moda Shopping", PlaceCategory.MALL)
    val placeInfoCafe = PlaceInfo("Starbucks Serrano", PlaceCategory.CAFE)

    // ── AddressAndPlace (for HomeState.userAddressAndPlace) ─────────────────────────

    val addressAndPlaceFuel = AddressAndPlace(addrFuel, placeInfoFuel)
    val addressAndPlaceStreet = AddressAndPlace(addrStreet, null)

    // ── Sessions ──────────────────────────────────────────────────────────────

    /** Active session — parked 30 min ago at a fuel station. User confirmed. */
    val activeSession = UserParking(
        id = "s_active",
        location = gps(agoMs = 1_800_000L),
        spotId = null,
        geofenceId = "geo_001",
        isActive = true,
        address = addrFuel,
        placeInfo = placeInfoFuel,
        detectionReliability = 1.0f,
    )

    /** Active session at a supermarket — auto-detected via vehicle-exit signal. */
    val activeSessionSupermarket = UserParking(
        id = "s_active_2",
        location = gps(agoMs = 3_600_000L, lat = 40.420, lon = -3.710, accuracy = 8f),
        spotId = null,
        geofenceId = "geo_002",
        isActive = true,
        address = addrSupermarket,
        placeInfo = placeInfoSupermarket,
        detectionReliability = 0.90f,
    )

    // Ended sessions
    private val endedToday1 = UserParking(
        id = "s_t1",
        location = gps(agoMs = 7_200_000L),
        spotId = "spot_001", geofenceId = "geo_003", isActive = false,
        address = addrSupermarket,
        placeInfo = placeInfoSupermarket,
        detectionReliability = 0.90f,
    )
    private val endedToday2 = UserParking(
        id = "s_t2",
        location = gps(agoMs = 18_000_000L, lat = 40.419, lon = -3.706, accuracy = 25f),
        spotId = "spot_002", geofenceId = "geo_004", isActive = false,
        address = addrStreet,
        placeInfo = null,
        detectionReliability = 0.75f,
    )
    private val endedYesterday1 = UserParking(
        id = "s_y1",
        location = gps(agoMs = 86_400_000L + 3_600_000L, lat = 40.415, lon = -3.695),
        spotId = "spot_003", geofenceId = "geo_005", isActive = false,
        address = addrMall,
        placeInfo = placeInfoMall,
        detectionReliability = 1.0f,
    )
    private val endedYesterday2 = UserParking(
        id = "s_y2",
        location = gps(agoMs = 86_400_000L + 14_400_000L, lat = 40.413, lon = -3.700),
        spotId = "spot_004", geofenceId = "geo_006", isActive = false,
        address = addrCafe,
        placeInfo = placeInfoCafe,
        detectionReliability = 0.90f,
    )
    private val endedOld = UserParking(
        id = "s_old",
        location = gps(agoMs = 3 * 86_400_000L),
        spotId = "spot_005", geofenceId = "geo_007", isActive = false,
        address = addrHighAccuracy,
        placeInfo = null,
        detectionReliability = 0.75f,
    )

    val allSessions =
        listOf(activeSession, endedToday1, endedToday2, endedYesterday1, endedYesterday2, endedOld)
    val endedSessions = listOf(endedToday1, endedToday2, endedYesterday1, endedYesterday2, endedOld)
    val onlyEndedSessions = endedSessions

    // ── Weekly chart ──────────────────────────────────────────────────────────

    val weeklyStats = listOf(
        WeekDayStats("L", 2),
        WeekDayStats("M", 1),
        WeekDayStats("X", 3),
        WeekDayStats("J", 0),
        WeekDayStats("V", 2),
        WeekDayStats("S", 1),
        WeekDayStats("D", 4),
    )

    val weeklyStatsEmpty = listOf(
        WeekDayStats("L", 0),
        WeekDayStats("M", 0),
        WeekDayStats("X", 0),
        WeekDayStats("J", 0),
        WeekDayStats("V", 0),
        WeekDayStats("S", 0),
        WeekDayStats("D", 0),
    )

    // ── Nearby spots ──────────────────────────────────────────────────────────

    val nearbySpots = listOf(
        Spot(
            id = "sp_1",
            location = gps(agoMs = 4 * 60_000L, lat = 40.418, lon = -3.706),
            reportedBy = "user_1",
            address = addrStreet,
            placeInfo = null,
            confidence = 0.92f,
            enRouteCount = 2,
            sizeCategory = VehicleSize.MEDIUM,
        ),
        Spot(
            id = "sp_2",
            location = gps(agoMs = 15 * 60_000L, lat = 40.419, lon = -3.704),
            reportedBy = "user_2",
            address = addrFuel,
            placeInfo = placeInfoFuel,
            confidence = 0.65f,
            enRouteCount = 1,
            sizeCategory = VehicleSize.SMALL,
        ),
        Spot(
            id = "sp_3",
            location = gps(agoMs = 40 * 60_000L, lat = 40.417, lon = -3.708),
            reportedBy = "user_3",
            address = addrSupermarket,
            placeInfo = placeInfoSupermarket,
            confidence = 0.85f,
            enRouteCount = 0,
            // intentionally no sizeCategory — exercises the "unknown" CompatibilityRow state
        ),
        Spot(
            id = "sp_4",
            location = gps(agoMs = 8 * 60_000L, lat = 40.416, lon = -3.705),
            reportedBy = "user_4",
            address = addrHighAccuracy,
            placeInfo = null,
            confidence = 0.45f,
            enRouteCount = 0,
            sizeCategory = VehicleSize.LARGE,
        ),
    )

    // ── Vehicles ──────────────────────────────────────────────────────────────

    /** Default vehicle — no BT, Coordinator detection. Most-used. */
    val vehicleSedan = Vehicle(
        id = "v1",
        userId = "user-1",
        name = "Mi Seat",
        brand = "Seat",
        model = "León",
        sizeCategory = VehicleSize.MEDIUM,
        vehicleType = VehicleType.CAR,
        isActive = true,
    )

    /** BT-configured vehicle — BluetoothDetectionStrategy. Currently parked (has active session). */
    val vehicleCorolla = Vehicle(
        id = "v2",
        userId = "user-1",
        brand = "Toyota",
        model = "Corolla",
        sizeCategory = VehicleSize.MEDIUM,
        vehicleType = VehicleType.CAR,
        bluetoothDeviceId = "AA:BB:CC:DD:EE:FF",
        showBrandModelOnSpot = true,
    )

    /** Motorcycle — no BT, manual detection only. Rarely used. */
    val vehicleMoto = Vehicle(
        id = "v3",
        userId = "user-1",
        name = "La Moto",
        brand = "Honda",
        model = "CBR 600",
        sizeCategory = VehicleSize.MOTO,
        vehicleType = VehicleType.MOTORCYCLE,
    )

    /** Van with BT — BluetoothDetectionStrategy. Moderate use. */
    val vehicleVan = Vehicle(
        id = "v4",
        userId = "user-1",
        name = "Furgoneta",
        brand = "Ford",
        model = "Transit",
        sizeCategory = VehicleSize.VAN,
        vehicleType = VehicleType.CAR,
        bluetoothDeviceId = "11:22:33:44:55:66",
    )

    /** Kept for backwards-compat with previews that reference this name. */
    val vehicleNoName = vehicleMoto

    val vehiclesWithStats = listOf(
        VehicleWithStats(vehicle = vehicleSedan, sessionCount = 65, lastSession = endedSessions.firstOrNull()),
        VehicleWithStats(vehicle = vehicleCorolla, sessionCount = 46, lastSession = activeSession),
        VehicleWithStats(vehicle = vehicleMoto, sessionCount = 12, lastSession = endedSessions.lastOrNull()),
        VehicleWithStats(vehicle = vehicleVan, sessionCount = 18, lastSession = endedSessions.getOrNull(2)),
    )

    // ── Bluetooth devices ─────────────────────────────────────────────────────

    val btDevices = listOf(
        BluetoothDeviceInfo(address = "AA:BB:CC:DD:EE:FF", name = "Toyota BT Audio", type = BluetoothDeviceType.CLASSIC),
        BluetoothDeviceInfo(address = "11:22:33:44:55:66", name = "Ford Transit", type = BluetoothDeviceType.DUAL),
    )
}
