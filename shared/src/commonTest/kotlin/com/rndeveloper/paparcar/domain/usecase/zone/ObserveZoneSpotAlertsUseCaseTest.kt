package com.rndeveloper.paparcar.domain.usecase.zone

import app.cash.turbine.test
import com.rndeveloper.paparcar.domain.model.Entitlement
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.model.SpotStatus
import com.rndeveloper.paparcar.domain.model.Zone
import com.rndeveloper.paparcar.domain.usecase.entitlement.ObserveIsPlusUseCase
import com.rndeveloper.paparcar.fakes.FakeAppPreferences
import com.rndeveloper.paparcar.fakes.FakeAuthRepository
import com.rndeveloper.paparcar.fakes.FakeEntitlementRepository
import com.rndeveloper.paparcar.fakes.FakeSpotRepository
import com.rndeveloper.paparcar.fakes.FakeZoneRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The zone-alert stream and its downgrade rules [MONEY-001]: Plus gates delivery LIVE, the
 * per-zone opt-in survives untouched, and losing Plus pauses — never deletes — the alerts.
 */
class ObserveZoneSpotAlertsUseCaseTest {

    private val zoneRepo = FakeZoneRepository()
    private val spotRepo = FakeSpotRepository()
    private val entitlementRepo = FakeEntitlementRepository()
    private val prefs = FakeAppPreferences()

    private val useCase = ObserveZoneSpotAlertsUseCase(
        zoneRepository = zoneRepo,
        spotRepository = spotRepo,
        observeIsPlus = ObserveIsPlusUseCase(entitlementRepo),
        appPreferences = prefs,
        authRepository = FakeAuthRepository(initialSession = FakeAuthRepository.authenticatedSession(userId = "me")),
    )

    private fun zone(id: String = "z-1", notify: Boolean = true, radius: Float = 250f) = Zone(
        id = id,
        userId = "me",
        name = "Casa",
        lat = 40.0,
        lon = -3.0,
        iconKey = "home",
        createdAt = 0L,
        radiusMeters = radius,
        notifyOnSpot = notify,
    )

    /** ~[offsetMeters] north of the zone centre (1e-5 deg lat ≈ 1.11 m). */
    private fun spot(id: String, offsetMeters: Double = 0.0, reportedBy: String = "someone-else") = Spot(
        id = id,
        location = GpsPoint(
            latitude = 40.0 + offsetMeters / 111_111.0,
            longitude = -3.0,
            accuracy = 8f,
            timestamp = 0L,
            speed = 0f,
        ),
        reportedBy = reportedBy,
    )

    @Test
    fun `should_alertOnANewSpotInASubscribedZone_when_plus`() = runTest {
        entitlementRepo.emitEntitlement(Entitlement.PLUS)
        zoneRepo.zones = listOf(zone())

        useCase().test {
            spotRepo.spots = listOf(spot("s-new"))
            val alert = awaitItem()
            assertEquals("z-1", alert.zoneId)
            assertEquals("s-new", alert.spot.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should_stayQuiet_when_free`() = runTest {
        entitlementRepo.emitEntitlement(Entitlement.FREE)
        zoneRepo.zones = listOf(zone())

        useCase().test {
            spotRepo.spots = listOf(spot("s-new"))
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should_notTreatTheExistingMapAsNews_when_subscribing`() = runTest {
        entitlementRepo.emitEntitlement(Entitlement.PLUS)
        zoneRepo.zones = listOf(zone())
        spotRepo.spots = listOf(spot("s-already-there"))

        useCase().test {
            // The pre-existing spot is baseline, only the later one is news.
            spotRepo.spots = listOf(spot("s-already-there"), spot("s-new"))
            assertEquals("s-new", awaitItem().spot.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should_alertOncePerSpot_when_theStreamRepeatsIt`() = runTest {
        entitlementRepo.emitEntitlement(Entitlement.PLUS)
        zoneRepo.zones = listOf(zone())

        useCase().test {
            spotRepo.spots = listOf(spot("s-1"))
            assertEquals("s-1", awaitItem().spot.id)
            spotRepo.spots = listOf(spot("s-1"), spot("s-2"))
            assertEquals("s-2", awaitItem().spot.id)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should_ignoreSpotsOutsideTheZoneRadius_when_theGeoQueryOverfetches`() = runTest {
        entitlementRepo.emitEntitlement(Entitlement.PLUS)
        zoneRepo.zones = listOf(zone(radius = 100f))

        useCase().test {
            spotRepo.spots = listOf(spot("s-far", offsetMeters = 400.0))
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should_ignoreRetractedSpotsAndOwnSpots_when_theyAppear`() = runTest {
        entitlementRepo.emitEntitlement(Entitlement.PLUS)
        zoneRepo.zones = listOf(zone())

        useCase().test {
            spotRepo.spots = listOf(
                spot("s-retracted").copy(status = SpotStatus.RETRACTED),
                spot("s-mine", reportedBy = "me"),
            )
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should_ignoreZonesWithoutTheOptIn_when_plus`() = runTest {
        entitlementRepo.emitEntitlement(Entitlement.PLUS)
        zoneRepo.zones = listOf(zone(notify = false))

        useCase().test {
            spotRepo.spots = listOf(spot("s-new"))
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should_pauseAndResumeWithoutTouchingTheOptIn_when_plusIsLostAndRegained`() = runTest {
        // THE downgrade rule: losing Plus silences delivery; the zone's own opt-in survives,
        // so regaining Plus resumes the same subscription with no re-configuration.
        entitlementRepo.emitEntitlement(Entitlement.PLUS)
        zoneRepo.zones = listOf(zone())

        useCase().test {
            spotRepo.spots = listOf(spot("s-1"))
            assertEquals("s-1", awaitItem().spot.id)

            entitlementRepo.emitEntitlement(Entitlement.FREE)
            spotRepo.spots = listOf(spot("s-1"), spot("s-while-free"))
            expectNoEvents()

            entitlementRepo.emitEntitlement(Entitlement.PLUS)
            spotRepo.spots = listOf(spot("s-1"), spot("s-while-free"), spot("s-after-return"))
            assertEquals("s-after-return", awaitItem().spot.id)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(true, zoneRepo.zones.single().notifyOnSpot, "the opt-in must survive the downgrade")
    }

    @Test
    fun `should_swallowAlerts_when_theSettingsMasterGateIsClosed`() = runTest {
        entitlementRepo.emitEntitlement(Entitlement.PLUS)
        zoneRepo.zones = listOf(zone())
        prefs.setNotifyZoneSpots(false)

        useCase().test {
            spotRepo.spots = listOf(spot("s-silenced"))
            expectNoEvents()
            // Reopening the gate must not dump what appeared while it was closed.
            prefs.setNotifyZoneSpots(true)
            spotRepo.spots = listOf(spot("s-silenced"), spot("s-new"))
            assertEquals("s-new", awaitItem().spot.id)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
