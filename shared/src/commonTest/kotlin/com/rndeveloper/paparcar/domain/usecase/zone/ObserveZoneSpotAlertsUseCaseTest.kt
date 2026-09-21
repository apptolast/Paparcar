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
import com.rndeveloper.paparcar.fakes.FakeZoneWatchRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * El stream de alertas de zona: qué cuenta como NOVEDAD, y qué no.
 *
 * ⛔ Ya no gatea por Plus [ZONE-AN-ALERT-IS-DELIVERED-WHERE-YOU-CAN-SEE-IT-001]. El entitlement
 * sigue apareciendo en los fixtures porque el resto de reglas —baseline, dedupe, interruptor
 * maestro— tienen que seguir valiendo con y sin él.
 */
class ObserveZoneSpotAlertsUseCaseTest {

    private val zoneRepo = FakeZoneRepository()
    private val spotRepo = FakeSpotRepository()
    private val entitlementRepo = FakeEntitlementRepository()
    private val prefs = FakeAppPreferences()

    /** The memory both lanes share now. [ZONE-AN-ALERT-NOBODY-IS-AWAKE-TO-RECEIVE-001] */
    private val watchRepo = FakeZoneWatchRepository()

    private val useCase = ObserveZoneSpotAlertsUseCase(
        zoneRepository = zoneRepo,
        spotRepository = spotRepo,
        watchRepository = watchRepo,
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
    fun `should_alertAFreeUserToo_because_theGateIsGone`() = runTest {
        // ⛔ Este test decía lo CONTRARIO («should_stayQuiet_when_free») y se invierte a propósito,
        // no se borra: decisión del user el 21-09, «no será de pago, al menos al principio»
        // [ZONE-AN-ALERT-IS-DELIVERED-WHERE-YOU-CAN-SEE-IT-001]. Dejarlo escrito como afirmación es
        // lo que impide que el gate vuelva sin querer con el próximo refactor de entitlements.
        //
        // ⚠️ Y va contra el eje del producto —free = tú preguntas, Plus = la app te avisa—, así que
        // el día que se cierre, se cierra aquí y este testigo es el que hay que dar la vuelta.
        entitlementRepo.emitEntitlement(Entitlement.FREE)
        zoneRepo.zones = listOf(zone())

        useCase().test {
            spotRepo.spots = listOf(spot("s-new"))
            val alert = awaitItem()
            assertEquals("s-new", alert.spot.id)
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
    fun `should_ignoreSpotsOutsideTheAlertRadius_when_theGeoQueryOverfetches`() = runTest {
        entitlementRepo.emitEntitlement(Entitlement.PLUS)
        zoneRepo.zones = listOf(zone(radius = 100f))

        useCase().test {
            // Beyond the 500 m alert floor: the geohash ranges overfetch, the circle decides.
            spotRepo.spots = listOf(spot("s-far", offsetMeters = 900.0))
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * ⛔ Changed by [ZONE-A-LOOK-THAT-READS-THE-CACHE-IS-NOT-A-LOOK-001]: this spot used to be
     * ignored, because the alert clipped to the zone's own radius. A 100 m zone is someone marking a
     * place, not declaring that a free space 400 m from home is of no interest — and the app itself
     * calls 2 km "nearby" everywhere else. The floor is 500 m of walking.
     */
    @Test
    fun `should_reachPastTheDrawnRadius_when_theZoneIsSmallerThanAWalk`() = runTest {
        entitlementRepo.emitEntitlement(Entitlement.PLUS)
        zoneRepo.zones = listOf(zone(radius = 100f))

        useCase().test {
            spotRepo.spots = listOf(spot("s-400m", offsetMeters = 400.0))
            assertEquals("s-400m", awaitItem().spot.id)
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

    /**
     * THE downgrade rule: losing Plus silences delivery; the zone's own opt-in survives, so
     * regaining Plus resumes the same subscription with no re-configuration.
     *
     * ⚠️ **Behaviour changed on 15-09 with the durable memory**
     * [ZONE-AN-ALERT-NOBODY-IS-AWAKE-TO-RECEIVE-001]. It used to assert that a spot which appeared
     * WHILE free stayed unannounced for ever: the "already alerted" set lived in memory, so
     * regaining Plus rebuilt the stream, took a fresh baseline, and swallowed it. That silence was
     * a side effect of where the set lived, not a decision — and the set had to leave memory,
     * because a worker cannot inherit it.
     *
     * The decision now made on purpose: a spot **still alive** when the watch comes back IS worth
     * one interruption. It is not a backlog dump — the TTL filter bounds it to what is still free
     * (two hours at most), and both lanes post on the same notification id, so what the user
     * actually sees on returning to Plus is ONE card about a space that really is there.
     *
     * ⛔ This is NOT the same question as the Settings master gate one test below, which still
     * swallows rather than defers: muting a channel is the user saying "do not tell me", and
     * honouring that cannot mean telling them everything at once when they unmute.
     */
    @Test
    fun `should_keepAnnouncingAndKeepItsMemory_when_theEntitlementChanges`() = runTest {
        // ⛔ Este test comprobaba la PAUSA por downgrade de Plus, y esa regla ya no existe
        // [ZONE-AN-ALERT-IS-DELIVERED-WHERE-YOU-CAN-SEE-IT-001]. Lo que sí sigue valiendo —y es lo
        // que de verdad protegía— es la MEMORIA: pase lo que pase con el plan, una plaza ya contada
        // no vuelve a contarse. Por eso se reescribe en vez de borrarse.
        entitlementRepo.emitEntitlement(Entitlement.PLUS)
        zoneRepo.zones = listOf(zone())

        useCase().test {
            spotRepo.spots = listOf(spot("s-1"))
            assertEquals("s-1", awaitItem().spot.id)

            // Perder Plus ya no calla nada: la plaza nueva se cuenta igual, en el momento.
            entitlementRepo.emitEntitlement(Entitlement.FREE)
            spotRepo.spots = listOf(spot("s-1"), spot("s-while-free"))
            assertEquals("s-while-free", awaitItem().spot.id)

            // Y recuperarlo tampoco repite lo ya dicho: `s-1` no vuelve con el cambio de plan.
            entitlementRepo.emitEntitlement(Entitlement.PLUS)
            spotRepo.spots = listOf(spot("s-1"), spot("s-while-free"), spot("s-after-return"))
            assertEquals("s-after-return", awaitItem().spot.id)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(true, zoneRepo.zones.single().notifyOnSpot, "el opt-in de la zona no lo toca nadie")
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
