@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.domain.usecase.parking

import com.rndeveloper.paparcar.domain.detection.DepartureProof
import com.rndeveloper.paparcar.domain.detection.DetectionPath
import com.rndeveloper.paparcar.domain.diagnostics.DetectionEvent
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.spot.SpotReleasePolicy
import com.rndeveloper.paparcar.domain.usecase.location.GetAddressAndPlaceUseCase
import com.rndeveloper.paparcar.domain.usecase.spot.ReportSpotReleasedUseCase
import com.rndeveloper.paparcar.domain.util.haversineMeters
import com.rndeveloper.paparcar.domain.analytics.AnalyticsEvent
import com.rndeveloper.paparcar.domain.analytics.SessionEndReason
import com.rndeveloper.paparcar.fakes.FakeAddressAndPlaceRepository
import com.rndeveloper.paparcar.fakes.FakeAnalyticsTracker
import com.rndeveloper.paparcar.fakes.FakeBtConnectStampReader
import com.rndeveloper.paparcar.fakes.FakeAuthRepository
import com.rndeveloper.paparcar.fakes.FakeDepartureEventBus
import com.rndeveloper.paparcar.fakes.FakeDetectionEventLogger
import com.rndeveloper.paparcar.fakes.FakeGeofenceManager
import com.rndeveloper.paparcar.fakes.FakeReportSpotScheduler
import com.rndeveloper.paparcar.fakes.FakeUserParkingRepository
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * [DET-SOLID-001] First coverage of the departure side-effect chain — the corrective path
 * that publishes the freed spot, clears the session, and tears down the geofence.
 */
class ProcessConfirmedDepartureUseCaseTest {

    private fun activeSession(
        id: String = "session-1",
        vehicleId: String? = "v-1",
        privateZoneId: String? = null,
    ) = UserParking(
        id = id,
        userId = "user-42",
        vehicleId = vehicleId,
        location = PARK_A,
        geofenceId = id,
        isActive = true,
        privateZoneId = privateZoneId,
    )

    @Test
    fun should_publish_spot_and_clear_session_for_public_departure() = runTest {
        val repo = FakeUserParkingRepository(initialSession = activeSession())
        val spotScheduler = FakeReportSpotScheduler()
        val geofence = FakeGeofenceManager()
        val bus = FakeDepartureEventBus(initialTimestamp = 999L)
        val useCase = buildUseCase(repo = repo, spotScheduler = spotScheduler, geofence = geofence, bus = bus)

        val result = useCase("session-1", publishSpot = true)

        assertTrue(result.isSuccess)
        assertEquals(1, spotScheduler.scheduleCallCount, "public departure must publish the freed spot")
        assertFalse(repo.getActiveSessionByGeofence("session-1") != null, "session must be cleared")
        assertEquals(listOf("session-1"), geofence.removedIds, "geofence must be removed")
        assertEquals(null, bus.lastVehicleEnteredAt, "departure bus must reset for the next trip")
    }

    @Test
    fun should_not_publish_spot_for_private_zone_departure() = runTest {
        val repo = FakeUserParkingRepository(initialSession = activeSession(privateZoneId = "zone-1"))
        val spotScheduler = FakeReportSpotScheduler()
        val useCase = buildUseCase(repo = repo, spotScheduler = spotScheduler)

        val result = useCase("session-1", publishSpot = true)

        assertTrue(result.isSuccess)
        assertEquals(0, spotScheduler.scheduleCallCount, "private-zone spots are never published")
        assertFalse(repo.getActiveSessionByGeofence("session-1") != null, "session must still be cleared")
    }

    @Test
    fun should_succeed_without_side_effects_when_no_session_matches_geofence() = runTest {
        val repo = FakeUserParkingRepository()
        val spotScheduler = FakeReportSpotScheduler()
        val useCase = buildUseCase(repo = repo, spotScheduler = spotScheduler)

        val result = useCase("unknown-geofence", publishSpot = true)

        assertTrue(result.isSuccess)
        assertEquals(0, spotScheduler.scheduleCallCount)
    }

    @Test
    fun should_fail_when_session_clear_fails_so_worker_retries() = runTest {
        val repo = FakeUserParkingRepository(initialSession = activeSession()).apply {
            clearActiveParkingSessionResult = Result.failure(RuntimeException("db error"))
        }
        val useCase = buildUseCase(repo = repo)

        val result = useCase("session-1", publishSpot = true)

        assertTrue(result.isFailure, "a failed clear must propagate so the session is never left open")
    }

    @Test
    fun should_log_departure_processed_event() = runTest {
        val logger = FakeDetectionEventLogger()
        val repo = FakeUserParkingRepository(initialSession = activeSession())
        buildUseCase(repo = repo, logger = logger)("session-1", publishSpot = true)

        val event = logger.events.filterIsInstance<DetectionEvent.DepartureProcessed>().single()
        assertEquals("session-1", event.sessionId)
        assertTrue(event.published)
        assertTrue(event.sessionCleared)
    }

    @Test
    fun should_log_departure_processed_without_publish_for_private_zone() = runTest {
        val logger = FakeDetectionEventLogger()
        val repo = FakeUserParkingRepository(initialSession = activeSession(privateZoneId = "zone-1"))
        buildUseCase(repo = repo, logger = logger)("session-1", publishSpot = true)

        val event = logger.events.filterIsInstance<DetectionEvent.DepartureProcessed>().single()
        assertFalse(event.published)
        assertTrue(event.sessionCleared)
    }

    @Test
    fun should_not_claim_a_publication_when_the_departure_was_too_stale_to_publish_one() = runTest {
        // [DET-RETRACT-DENIED-FOREVER-001] Field 2026-08-23 04:10 (Oppo): a departure 60 min old hit
        // the [DET-RECONCILE-001] freshness gate and cleared WITHOUT publishing — correctly, the spot
        // is long gone. But the very next line claimed "spot published PROVISIONALLY" and the remote
        // event said published = true, so the session was marked as owing a withdrawal for a spot
        // that had never existed. Every session end for the next five days then tried to withdraw it.
        val logger = FakeDetectionEventLogger()
        val spotScheduler = FakeReportSpotScheduler()
        val repo = FakeUserParkingRepository(initialSession = activeSession())

        buildUseCase(repo = repo, logger = logger, spotScheduler = spotScheduler)(
            "session-1",
            publishSpot = false,
            proof = DepartureProof.Deduced,
        )

        val event = logger.events.filterIsInstance<DetectionEvent.DepartureProcessed>().single()
        assertFalse(event.published, "nothing was published, so the trace must not say it was")
        assertEquals(0, spotScheduler.scheduleCallCount, "and no spot went out")
    }

    // ── [DET-HANDOFF-NOT-MANUAL-001 §B] A DEDUCED departure publishes, but takes nothing ──

    @Test
    fun should_publish_provisionally_and_keep_the_car_when_the_departure_was_only_deduced() = runTest {
        // Field 2026-08-19 22:32: the safety net inferred a departure from the phone being far from
        // the parked car. The user was on a bicycle. The old code published a 2-hour ghost spot AND
        // released the session AND removed the geofence — three irreversible acts on a guess.
        val repo = FakeUserParkingRepository(initialSession = activeSession())
        val spotScheduler = FakeReportSpotScheduler()
        val geofence = FakeGeofenceManager()
        val useCase = buildUseCase(repo = repo, spotScheduler = spotScheduler, geofence = geofence)

        val result = useCase("session-1", publishSpot = true, proof = DepartureProof.Deduced)

        assertTrue(result.isSuccess)
        // The spot still goes out AT ONCE — freshness is its entire value — but with a short life.
        assertEquals(1, spotScheduler.scheduleCallCount)
        assertEquals(true, spotScheduler.lastProvisional)
        // …and nothing of the user's was given up.
        assertTrue(repo.getActiveSessionByGeofence("session-1") != null, "the car stays parked")
        assertEquals(emptyList(), geofence.removedIds, "its geofence stays armed for the real exit")
        assertTrue(repo.provisionalDepartures["session-1"] != null, "the deduction is marked pending")
    }

    @Test
    fun should_publish_a_deduced_departure_only_once() = runTest {
        // The session now survives a deduction, so the 15-min safety net will deduce the same
        // departure again while the user stays away. Re-publishing each time would blink a ghost
        // spot on and off all afternoon; the first publication stands and its short TTL bounds it.
        val repo = FakeUserParkingRepository(initialSession = activeSession())
        val spotScheduler = FakeReportSpotScheduler()
        val useCase = buildUseCase(repo = repo, spotScheduler = spotScheduler)

        useCase("session-1", publishSpot = true, proof = DepartureProof.Deduced)
        useCase("session-1", publishSpot = true, proof = DepartureProof.Deduced)

        assertEquals(1, spotScheduler.scheduleCallCount, "one deduction, one publication")
        assertTrue(repo.getActiveSessionByGeofence("session-1") != null)
    }

    @Test
    fun should_still_commit_everything_when_the_departure_was_witnessed() = runTest {
        // The measured case is untouched: a fresh fix at driving speed releases the car and the
        // spot goes out with the normal lifetime.
        val repo = FakeUserParkingRepository(initialSession = activeSession())
        val spotScheduler = FakeReportSpotScheduler()
        val geofence = FakeGeofenceManager()
        val useCase = buildUseCase(repo = repo, spotScheduler = spotScheduler, geofence = geofence)

        useCase("session-1", publishSpot = true, proof = DepartureProof.Witnessed)

        assertEquals(false, spotScheduler.lastProvisional)
        assertFalse(repo.getActiveSessionByGeofence("session-1") != null)
        assertEquals(listOf("session-1"), geofence.removedIds)
    }

    // ── [SPOT-A-ZONE-PARK-FREES-A-ZONE-SPOT-001] The spot inherits the session's shape ───

    @Test
    fun should_publishAZoneSpot_when_theFreedSessionWasABoundedZone() = runTest {
        // Field corpus 08-29→09-01: a walk-entered anchor saved the park as a 60 m zone. Its
        // departure used to publish an EXACT pin at the circle's centre.
        val repo = FakeUserParkingRepository(
            initialSession = activeSession().copy(zoneRadiusMeters = 60f, detectionReliability = 0.5f),
        )
        val spotScheduler = FakeReportSpotScheduler()

        buildUseCase(repo = repo, spotScheduler = spotScheduler)("session-1", publishSpot = true)

        assertEquals(1, spotScheduler.scheduleCallCount)
        assertEquals(60f, spotScheduler.lastZoneRadiusMeters, "the spot must carry the session's doubt radius")
        assertEquals(0.5f, spotScheduler.lastConfidence)
        assertEquals(true, spotScheduler.lastProvisional, "a zone spot lives short: the place was never proven")
    }

    @Test
    fun should_capAZoneSpotsConfidence_when_theUserConfirmedTheSessionWithFullReliability() = runTest {
        // The "Sí" proves the park, not the spot — reliability 1.0 must not reach the community
        // attached to a coordinate the machine chose. [DET-USER-YES-IS-NOT-A-COORDINATE-001]
        val repo = FakeUserParkingRepository(
            initialSession = activeSession().copy(zoneRadiusMeters = 60f, detectionReliability = 1f),
        )
        val spotScheduler = FakeReportSpotScheduler()

        buildUseCase(repo = repo, spotScheduler = spotScheduler)("session-1", publishSpot = true)

        assertEquals(SpotReleasePolicy.ZONE_MAX_CONFIDENCE, spotScheduler.lastConfidence)
    }

    @Test
    fun should_withholdTheSpotButStillReleaseTheCar_when_theZoneIsWiderThanTheCeiling() = runTest {
        // The measured gap-anchor zones (r=250 m): sending a seeker to scan a neighbourhood is the
        // ghost pin's damage on foot. The user's own session cleanup is untouched.
        val logger = FakeDetectionEventLogger()
        val repo = FakeUserParkingRepository(
            initialSession = activeSession().copy(zoneRadiusMeters = 250f, detectionReliability = 0.5f),
        )
        val spotScheduler = FakeReportSpotScheduler()
        val geofence = FakeGeofenceManager()

        val result = buildUseCase(repo = repo, spotScheduler = spotScheduler, geofence = geofence, logger = logger)(
            "session-1", publishSpot = true,
        )

        assertTrue(result.isSuccess)
        assertEquals(0, spotScheduler.scheduleCallCount, "a neighbourhood is not a plaza")
        assertFalse(repo.getActiveSessionByGeofence("session-1") != null, "the session still closes")
        assertEquals(listOf("session-1"), geofence.removedIds, "and its geofence still comes down")
        val event = logger.events.filterIsInstance<DetectionEvent.DepartureProcessed>().single()
        assertFalse(event.published, "the trace must record the withheld publish")
    }

    @Test
    fun should_deduceWithoutPublishing_when_theZoneIsWiderThanTheCeiling() = runTest {
        // A deduced departure of a wide-zone session keeps the car AND publishes nothing — the
        // freshness argument for provisional publishing does not buy back the position doubt.
        val repo = FakeUserParkingRepository(
            initialSession = activeSession().copy(zoneRadiusMeters = 250f, detectionReliability = 0.5f),
        )
        val spotScheduler = FakeReportSpotScheduler()

        buildUseCase(repo = repo, spotScheduler = spotScheduler)(
            "session-1", publishSpot = true, proof = DepartureProof.Deduced,
        )

        assertEquals(0, spotScheduler.scheduleCallCount)
        assertTrue(repo.getActiveSessionByGeofence("session-1") != null, "the car stays parked")
    }

    // ── [PARK-A-REFUTED-PIN-LEAVES-THE-HISTORY-001] A pin its own departure refuted ──────

    /**
     * Field 2026-08-27 (63 s) and 2026-08-30 (52 s): the backfill reconstructs a pin, and the app's
     * own geofence emits an EXIT from it seconds later. Closing the session was never enough — the
     * row stayed in the history looking like an ordinary parking, addressless, and the user
     * reported it as a false positive.
     */
    @Test
    fun should_withdrawTheParking_when_itsOwnDepartureRefutedTheBackfillThatPlacedIt() = runTest {
        val parkedAt = Clock.System.now().toEpochMilliseconds() - 60_000L
        val repo = FakeUserParkingRepository(
            initialSession = activeSession().copy(
                location = GpsPoint(40.4, -3.7, 8f, parkedAt, 0f),
                detectionPath = DetectionPath.SafetyNetBackfill.label,
            ),
        )
        val useCase = buildUseCase(repo = repo)

        val result = useCase("session-1", publishSpot = true)

        assertTrue(result.isSuccess)
        val stored = repo.getSessionById("session-1")
        assertTrue(stored?.isRetracted == true, "a refuted pin must leave the history")
        // Withdrawn, NEVER deleted — the row is what the next field report reads.
        assertEquals("session-1", stored.id, "the row itself must survive for diagnostics")
        assertFalse(stored.isActive, "and it is still closed, like any ended session")
    }

    @Test
    fun should_keepTheParking_when_theSamePinWasPlacedByAMeasuredPath() = runTest {
        val parkedAt = Clock.System.now().toEpochMilliseconds() - 60_000L
        val repo = FakeUserParkingRepository(
            initialSession = activeSession().copy(
                location = GpsPoint(40.4, -3.7, 8f, parkedAt, 0f),
                detectionPath = DetectionPath.StepsEgress.label,
            ),
        )

        buildUseCase(repo = repo)("session-1", publishSpot = true)

        assertFalse(
            repo.getSessionById("session-1")?.isRetracted == true,
            "a session that measured the walk away from the car ENDED; it was not refuted",
        )
    }

    @Test
    fun should_keepTheBackfillParking_when_itOutlivedTheRefutationWindow() = runTest {
        val config = ParkingDetectionConfig()
        val parkedAt = Clock.System.now().toEpochMilliseconds() - config.refutedPinMaxLifeMs - 60_000L
        val repo = FakeUserParkingRepository(
            initialSession = activeSession().copy(
                location = GpsPoint(40.4, -3.7, 8f, parkedAt, 0f),
                detectionPath = DetectionPath.SafetyNetBackfill.label,
            ),
        )

        buildUseCase(repo = repo, config = config)("session-1", publishSpot = true)

        assertFalse(
            repo.getSessionById("session-1")?.isRetracted == true,
            "a backfill pin the car actually sat at for an hour is a real parking",
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    // ── El coche se movió sin mí: A → B → C [DET-A-BT-CONNECT-KNOWS-WHERE-THE-CAR-IS-001] ────────
    //
    // Escenario del user (09-09-2026): el coche está aparcado en A y la app lo sabe. Un familiar lo
    // coge y lo aparca en B, sin que el móvil del user se entere de nada. Después el user se monta
    // en B, conduce y aparca en C.
    //
    // Estos dos tests no proponen nada: MIDEN qué hace hoy la app, para que la ficha deje de decir
    // "no demostrado" sobre algo que el código ya contesta. La pregunta se hizo en campo y se
    // responde aquí, que es donde es determinista.

    @Test
    fun should_publishTheFreedSpotAtA_when_nothingCanContradictTheSession() = runTest {
        // Sin carril BT no hay quien desmienta a la sesión: se publica en A, como siempre. Este es
        // el comportamiento que el guard NO puede cambiar — un coche sin MAC emparejada vive aquí.
        // La sesión dice A. El coche lleva horas en B. Alguien mide una salida (el EXIT de la
        // geocerca de A al salir de casa, el trayecto a B contado como conducción) y se procesa.
        val repo = FakeUserParkingRepository(initialSession = activeSession(id = "session-A"))
        val spotScheduler = FakeReportSpotScheduler()

        buildUseCase(repo = repo, spotScheduler = spotScheduler)("session-A", publishSpot = true)

        // Se publica, y se publica EN A: la plaza sale de la sesión que se cierra, no de donde el
        // coche esté. Nada en la firma de este caso de uso admite una posición actual.
        assertEquals(1, spotScheduler.scheduleCallCount)
        assertEquals(PARK_A.latitude, spotScheduler.lastLat, 1e-9)
        assertEquals(PARK_A.longitude, spotScheduler.lastLon, 1e-9)
    }

    @Test
    fun should_neverPublishTheSpotAtB_when_thatIsThePlaceActuallyFreed() = runTest {
        // La otra mitad, y la que más importa: la plaza que el user deja REALMENTE libre al salir
        // de B no se publica nunca — porque B no existe para la app. Aquí se mide como distancia:
        // lo publicado está a ~1,4 km de donde el coche estaba de verdad.
        val repo = FakeUserParkingRepository(initialSession = activeSession(id = "session-A"))
        val spotScheduler = FakeReportSpotScheduler()

        buildUseCase(repo = repo, spotScheduler = spotScheduler)("session-A", publishSpot = true)

        val offByMeters = haversineMeters(
            spotScheduler.lastLat, spotScheduler.lastLon,
            PARK_B.latitude, PARK_B.longitude,
        )
        assertTrue(
            offByMeters > 1_000.0,
            "hoy la plaza publicada cae a ${offByMeters.toInt()} m de la que se liberó de verdad",
        )
    }

    @Test
    fun should_refuseToPublishAnything_when_theCarConnectedSomewhereElseSinceThisPark() = runTest {
        // [DET-A-BT-CONNECT-KNOWS-WHERE-THE-CAR-IS-001 · mitad 1] El guard. Mismo escenario que los
        // dos de arriba, con la única diferencia que importa: el coche ENGANCHÓ en B después de que
        // esta sesión naciera en A. Eso desmiente al pin, y una sesión que ya no sabe dónde está su
        // coche pierde el derecho a anunciar plaza.
        val repo = FakeUserParkingRepository(initialSession = activeSession(id = "session-A"))
        val spotScheduler = FakeReportSpotScheduler()
        val stamps = FakeBtConnectStampReader().apply {
            stamp("v-1", atMs = PARK_A.timestamp + 3 * 60 * 60_000L, fix = PARK_B)
        }

        val result = buildUseCase(repo = repo, spotScheduler = spotScheduler, btConnectStamps = stamps)(
            "session-A",
            publishSpot = true,
        )

        assertTrue(result.isSuccess)
        assertEquals(0, spotScheduler.scheduleCallCount, "no se anuncia una plaza que otro liberó a una hora que nadie vio")
        // Y el resto del cierre SÍ corre: el estado propio tiene que converger igual.
        assertFalse(repo.getActiveSessionByGeofence("session-A") != null, "la sesión se cierra igual")
    }

    @Test
    fun should_stillPublish_when_theCarConnectedRightWhereItWasParked() = runTest {
        // El caso normal, que es el que el guard no puede romper: vuelves a tu coche, el enlace
        // engancha a unos metros del pin, conduces. Eso no es una contradicción.
        val repo = FakeUserParkingRepository(initialSession = activeSession(id = "session-A"))
        val spotScheduler = FakeReportSpotScheduler()
        val stamps = FakeBtConnectStampReader().apply {
            stamp("v-1", atMs = PARK_A.timestamp + 60 * 60_000L, fix = PARK_A.copy(timestamp = PARK_A.timestamp + 60 * 60_000L))
        }

        buildUseCase(repo = repo, spotScheduler = spotScheduler, btConnectStamps = stamps)(
            "session-A",
            publishSpot = true,
        )

        assertEquals(1, spotScheduler.scheduleCallCount)
        assertEquals(PARK_A.latitude, spotScheduler.lastLat, 1e-9)
    }

    private fun buildUseCase(
        repo: FakeUserParkingRepository = FakeUserParkingRepository(),
        spotScheduler: FakeReportSpotScheduler = FakeReportSpotScheduler(),
        geofence: FakeGeofenceManager = FakeGeofenceManager(),
        bus: FakeDepartureEventBus = FakeDepartureEventBus(),
        logger: FakeDetectionEventLogger = FakeDetectionEventLogger(),
        config: ParkingDetectionConfig = ParkingDetectionConfig(),
        tracker: FakeAnalyticsTracker? = null,
        btConnectStamps: FakeBtConnectStampReader? = null,
    ) = ProcessConfirmedDepartureUseCase(
        userParkingRepository = repo,
        reportSpotReleased = ReportSpotReleasedUseCase(
            reportSpotScheduler = spotScheduler,
            getAddressAndPlace = GetAddressAndPlaceUseCase(repository = FakeAddressAndPlaceRepository()),
            authRepository = FakeAuthRepository(initialSession = FakeAuthRepository.authenticatedSession(userId = "user-42")),
        ),
        geofenceService = geofence,
        departureEventBus = bus,
        detectionEventLogger = logger,
        config = config,
        analyticsTracker = tracker,
        btConnectStamps = btConnectStamps,
    )

    // ── Product analytics [ANALYTICS-EVENTS-001] ─────────────────────────────

    @Test
    fun should_emit_session_ended_as_departure_confirmed() = runTest {
        val tracker = FakeAnalyticsTracker()
        val repo = FakeUserParkingRepository(initialSession = activeSession())

        buildUseCase(repo = repo, tracker = tracker)("session-1", publishSpot = true)

        val ended = tracker.events.filterIsInstance<AnalyticsEvent.ParkingSessionEnded>().single()
        assertEquals(SessionEndReason.DEPARTURE_CONFIRMED, ended.reason)
    }

    @Test
    fun should_emit_no_session_ended_when_the_clear_fails() = runTest {
        val tracker = FakeAnalyticsTracker()
        val repo = FakeUserParkingRepository(initialSession = activeSession()).apply {
            clearActiveParkingSessionResult = Result.failure(RuntimeException("db error"))
        }

        buildUseCase(repo = repo, tracker = tracker)("session-1", publishSpot = true)

        assertTrue(tracker.events.filterIsInstance<AnalyticsEvent.ParkingSessionEnded>().isEmpty())
    }

    private companion object {
        /** Donde la app CREE que está el coche: la sesión activa. */
        val PARK_A = GpsPoint(40.4, -3.7, 8f, 1_000_000L, 0f)

        /** Donde el coche está DE VERDAD después de que otra persona lo moviera — ~1,4 km al norte.
         *  La app nunca se enteró: no hubo sesión, ni pin, ni evento. */
        val PARK_B = GpsPoint(40.4126, -3.7, 8f, 2_000_000L, 0f)
    }
}
