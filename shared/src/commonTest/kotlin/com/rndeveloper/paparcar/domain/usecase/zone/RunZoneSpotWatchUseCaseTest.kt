package com.rndeveloper.paparcar.domain.usecase.zone

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
import com.rndeveloper.paparcar.fakes.FakeZoneWatchRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The background look. [ZONE-AN-ALERT-NOBODY-IS-AWAKE-TO-RECEIVE-001]
 *
 * ⛔ The test this ticket exists for is [should_announceOnce_when_theSameSpotsAreFoundTwice]. A
 * worker starts from nothing every fifteen minutes, so before the durable memory the only two
 * possible behaviours were "never alerts" and "dumps the zone four times an hour".
 */
class RunZoneSpotWatchUseCaseTest {

    private val zoneRepo = FakeZoneRepository()
    private val spotRepo = FakeSpotRepository()
    private val watchRepo = FakeZoneWatchRepository()
    private val entitlementRepo = FakeEntitlementRepository(initialEntitlement = Entitlement.PLUS)
    private val prefs = FakeAppPreferences()
    private var now = 1_000_000L

    private val useCase = RunZoneSpotWatchUseCase(
        zoneRepository = zoneRepo,
        spotRepository = spotRepo,
        watchRepository = watchRepo,
        observeIsPlus = ObserveIsPlusUseCase(entitlementRepo),
        appPreferences = prefs,
        authRepository = FakeAuthRepository(
            initialSession = FakeAuthRepository.authenticatedSession(userId = "me"),
        ),
        clock = { now },
    )

    private fun zone(id: String = "z-1", notify: Boolean = true) = Zone(
        id = id,
        userId = "me",
        name = "Casa",
        lat = 40.0,
        lon = -3.0,
        iconKey = "home",
        createdAt = 0L,
        radiusMeters = 250f,
        notifyOnSpot = notify,
    )

    private fun spot(
        id: String,
        reportedBy: String = "someone-else",
        status: SpotStatus = SpotStatus.CONFIRMED,
        latOffset: Double = 0.0,
        expiresAt: Long = 9_999_999L,
    ) = Spot(
        id = id,
        location = GpsPoint(
            latitude = 40.0 + latOffset,
            longitude = -3.0,
            accuracy = 5f,
            timestamp = now,
            speed = 0f,
        ),
        reportedBy = reportedBy,
        status = status,
        expiresAt = expiresAt,
    )

    /** The first look records the ground instead of reporting it. */
    @Test
    fun should_staySilent_when_itIsTheFirstLookAtTheZone() = runTest {
        zoneRepo.zones = listOf(zone())
        spotRepo.spots = listOf(spot("s-1"), spot("s-2"))

        val outcome = useCase()

        assertTrue(outcome is ZoneWatchOutcome.Looked)
        assertTrue(outcome.alerts.isEmpty(), "spots already there when you started are not news")
    }

    @Test
    fun should_announceOnce_when_theSameSpotsAreFoundTwice() = runTest {
        zoneRepo.zones = listOf(zone())
        // Baseline look.
        spotRepo.spots = listOf(spot("old"))
        useCase()

        // A new spot appears and is announced.
        spotRepo.spots = listOf(spot("old"), spot("fresh"))
        val first = useCase() as ZoneWatchOutcome.Looked
        assertEquals(listOf("fresh"), first.alerts.map { it.spot.id })

        // Fifteen minutes later the very same spots are still there. THIS is the regression.
        now += 15 * 60 * 1_000L
        val second = useCase() as ZoneWatchOutcome.Looked
        assertTrue(second.alerts.isEmpty(), "a second look must not re-announce what it already said")
    }

    /** A provisional spot lives 12 min and this lane runs every 15 — it can never arrive in time. */
    @Test
    fun should_ignoreProvisionalSpots_when_lookingInTheBackground() = runTest {
        zoneRepo.zones = listOf(zone())
        spotRepo.spots = emptyList()
        useCase()

        spotRepo.spots = listOf(spot("provisional", status = SpotStatus.PROVISIONAL))
        val outcome = useCase() as ZoneWatchOutcome.Looked

        assertTrue(outcome.alerts.isEmpty())
    }

    @Test
    fun should_ignoreYourOwnSpot_when_itAppearsInYourZone() = runTest {
        zoneRepo.zones = listOf(zone())
        spotRepo.spots = emptyList()
        useCase()

        spotRepo.spots = listOf(spot("mine", reportedBy = "me"))
        val outcome = useCase() as ZoneWatchOutcome.Looked

        assertTrue(outcome.alerts.isEmpty(), "the place YOU just left is not news to you")
    }

    @Test
    fun should_ignoreRetractedAndExpiredSpots_when_looking() = runTest {
        zoneRepo.zones = listOf(zone())
        spotRepo.spots = emptyList()
        useCase()

        spotRepo.spots = listOf(
            spot("retracted", status = SpotStatus.RETRACTED),
            spot("dead", expiresAt = now - 1),
        )
        val outcome = useCase() as ZoneWatchOutcome.Looked

        assertTrue(outcome.alerts.isEmpty())
    }

    /** The geo query overfetches by geohash bounds; the circle is the contract. */
    @Test
    fun should_ignoreSpotsOutsideTheCircle_when_theQueryOverfetches() = runTest {
        zoneRepo.zones = listOf(zone())
        spotRepo.spots = emptyList()
        useCase()

        // ~1,1 km north of a 250 m zone.
        spotRepo.spots = listOf(spot("far", latOffset = 0.010))
        val outcome = useCase() as ZoneWatchOutcome.Looked

        assertTrue(outcome.alerts.isEmpty())
    }

    @Test
    fun should_skip_when_theUserIsNotPlus() = runTest {
        entitlementRepo.emitEntitlement(Entitlement.FREE)
        zoneRepo.zones = listOf(zone())
        spotRepo.spots = listOf(spot("s-1"))

        val outcome = useCase()

        assertTrue(outcome is ZoneWatchOutcome.Skipped)
        assertEquals("not-plus", outcome.reason)
    }

    /**
     * ⛔ The downgrade rule: losing Plus PAUSES. The zone keeps its opt-in and the memory keeps its
     * rows, so regaining Plus resumes the same watch instead of re-announcing the whole street.
     */
    @Test
    fun should_keepTheSubscriptionAndTheMemory_when_plusIsLostAndRegained() = runTest {
        zoneRepo.zones = listOf(zone())
        spotRepo.spots = listOf(spot("old"))
        useCase()
        spotRepo.spots = listOf(spot("old"), spot("fresh"))
        useCase()

        entitlementRepo.emitEntitlement(Entitlement.FREE)
        useCase()
        entitlementRepo.emitEntitlement(Entitlement.PLUS)
        val afterRegain = useCase() as ZoneWatchOutcome.Looked

        assertTrue(zoneRepo.zones.single().notifyOnSpot, "the opt-in is never touched on downgrade")
        assertTrue(afterRegain.alerts.isEmpty(), "regaining Plus must not re-announce what was said")
    }

    @Test
    fun should_skip_when_theSettingsMasterSwitchIsOff() = runTest {
        prefs.setNotifyZoneSpots(false)
        zoneRepo.zones = listOf(zone())
        spotRepo.spots = listOf(spot("s-1"))

        val outcome = useCase()

        assertTrue(outcome is ZoneWatchOutcome.Skipped)
        assertEquals("switch-off", outcome.reason)
    }

    @Test
    fun should_skip_when_noZoneAsksToBeWatched() = runTest {
        zoneRepo.zones = listOf(zone(notify = false))

        val outcome = useCase()

        assertTrue(outcome is ZoneWatchOutcome.Skipped)
        assertEquals("no-subscribed-zones", outcome.reason)
    }

    /**
     * A look we did not get must leave no trace: a zone marked as seen without being seen would
     * take a silent baseline and swallow whatever was there.
     */
    @Test
    fun should_reportUnavailableAndRecordNothing_when_theQueryFails() = runTest {
        zoneRepo.zones = listOf(zone())
        spotRepo.observeError = IllegalStateException("network down")

        val outcome = useCase()

        assertTrue(outcome is ZoneWatchOutcome.Unavailable)
        assertTrue(!watchRepo.hasBaseline("z-1"), "a failed look must not count as a first look")
    }

    @Test
    fun should_announcePerZone_when_twoZonesOverlapOnTheSameSpot() = runTest {
        zoneRepo.zones = listOf(zone(id = "z-1"), zone(id = "z-2"))
        spotRepo.spots = emptyList()
        useCase()

        spotRepo.spots = listOf(spot("shared"))
        val outcome = useCase() as ZoneWatchOutcome.Looked

        // Being told about your street is not being told about your office.
        assertEquals(listOf("z-1", "z-2"), outcome.alerts.map { it.zoneId })
    }
}
