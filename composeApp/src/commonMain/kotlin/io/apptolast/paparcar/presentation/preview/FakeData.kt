@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.preview

import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.LocationInfo
import io.apptolast.paparcar.domain.model.PlaceCategory
import io.apptolast.paparcar.domain.model.PlaceInfo
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.presentation.history.WeekDayStats
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

    // ── LocationInfo (for HomeState.userLocationInfo) ─────────────────────────

    val locationInfoFuel = LocationInfo(addrFuel, placeInfoFuel)
    val locationInfoStreet = LocationInfo(addrStreet, null)

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
            location = gps(lat = 40.418, lon = -3.706),
            reportedBy = "user_1",

            address = addrStreet,
            placeInfo = null,
        ),
        Spot(
            id = "sp_2",
            location = gps(lat = 40.419, lon = -3.704),
            reportedBy = "user_2",

            address = addrFuel,
            placeInfo = placeInfoFuel,
        ),
        Spot(
            id = "sp_3",
            location = gps(lat = 40.417, lon = -3.708),
            reportedBy = "user_3",

            address = addrSupermarket,
            placeInfo = placeInfoSupermarket,
        ),
    )
}
