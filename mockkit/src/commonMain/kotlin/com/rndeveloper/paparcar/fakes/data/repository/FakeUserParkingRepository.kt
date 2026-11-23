@file:OptIn(ExperimentalTime::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.rndeveloper.paparcar.fakes.data.repository

import com.rndeveloper.paparcar.domain.detection.DetectionRuntimeState
import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.PlaceCategory
import com.rndeveloper.paparcar.domain.model.PlaceInfo
import com.rndeveloper.paparcar.domain.model.RefuelStopAnswer
import com.rndeveloper.paparcar.domain.model.RouteInferenceResolution
import com.rndeveloper.paparcar.domain.model.SpotType
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.repository.UserParkingRepository
import com.rndeveloper.paparcar.fakes.data.DemoStreets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import com.rndeveloper.paparcar.domain.detection.DetectionPath

/**
 * Fake repository for tests and debug DI.
 *
 * The four cars are four PROFILES, not four copies with different names — each one puts the
 * vehicle ficha in a state worth looking at on a device: [STATS-UI-001]
 *   mock_vehicle_001 — Seat León · FULL DATA. Dense and recent, so every figure clears its
 *                      significance threshold and the free 7-day window is not empty either.
 *   mock_vehicle_002 — Toyota Corolla · BLUETOOTH. Sessions without a route, so km, CO₂, cost
 *                      AND el tiempo de búsqueda honestly disappear while the rest stay.
 *   mock_vehicle_003 — Honda CBR 600 · EMPTY. No history at all: the empty-state illustration and
 *                      "aún sin aparcar". Distinct from "hidden behind Plus", which the free
 *                      window produces on any car whose parkings are all older than 7 days.
 *   mock_vehicle_004 — Ford Transit · MODERATE. The ordinary middle case.
 */
class FakeUserParkingRepository(
    private val runtime: DetectionRuntimeState? = null,
    private val scenario: com.rndeveloper.paparcar.fakes.MockScenario? = null,
) : UserParkingRepository {
    private val now = Clock.System.now().toEpochMilliseconds()

    /** Seeded by the Dev Catalog "own parked session" lever: an ACTIVE session for the ACTIVE
     *  vehicle, so the real Home reaches the watching/parked state. [UX-PARKED-STATE-001] */
    private val ownParkedSeed = UserParking(
        id = "parking_own_active",
        geofenceId = "mock-own-parking-fence",
        userId = "mock_user_001",
        vehicleId = "mock_vehicle_001",
        // On Av. de la Concepción — matching the address below; the old point sat on the riverbank.
        location = GpsPoint(40.4236, -3.7007, 6f, now - 40 * 60_000L, 0f),
        isActive = true,
        spotType = SpotType.AUTO_DETECTED,
        detectionReliability = 0.95f,
        address = AddressInfo("Calle de Fuencarral, 21", "Madrid", "Madrid", "España", "ES"),
    )

    /**
     * The seeded own session, exact or as an AREA. A real honest close saves a radius when it could
     * not follow the end of the trip; 154 m is the figure the 21-08 field case actually produced,
     * so the gallery shows a zone the size of a real one, not a decorative one.
     * [UI-APPROXIMATE-PARKING-DRAWS-ITS-DOUBT-001]
     */
    private fun seedFor(approximate: Boolean): UserParking =
        if (approximate) ownParkedSeed.copy(zoneRadiusMeters = APPROXIMATE_SEED_RADIUS_M) else ownParkedSeed

    /** Sessions saved at runtime (manual mark / re-park) — the fake behaves like a repo. */
    private val savedSessions = MutableStateFlow<List<UserParking>>(emptyList())

    /** Ids released at runtime — releasing in the mock really frees the session. */
    private val releasedIds = MutableStateFlow<Set<String>>(emptySet())

    /** [DET-A-TRAIN-IS-NOT-A-CAR-001] Ids given back after a refuted departure. Exposed so a test
     *  can assert the car RETURNED, not merely that nothing new was pinned — the whole point of the
     *  repair is a state that reads right, and "no phantom" alone cannot witness it. */
    val restoredIds = MutableStateFlow<Set<String>>(emptySet())

    /** [PARK-A-REFUTED-PIN-LEAVES-THE-HISTORY-001] Ids withdrawn at runtime, with the instant.
     *  Kept apart from [releasedIds] because the two are different facts: a release ENDS a
     *  parking, a withdrawal says it never happened. */
    private val retractedIds = MutableStateFlow<Map<String, Long>>(emptyMap())

    /**
     * [DET-A-STOP-AT-A-PUMP-IS-NOT-A-PARKING-001] Qué paradas están preguntando «¿Repostaste
     * aquí?» y qué se ha contestado.
     *
     * Arranca con la parada sembrada abajo ya PENDIENTE: el aviso de Home y la fila del historial
     * son estados NUEVOS, y un catálogo que exigiera salir a repostar para verlos no los enseñaría
     * nunca.
     */
    private val refuelAnswers = MutableStateFlow<Map<String, RefuelStopAnswer>>(emptyMap())

    /** La pregunta de la parada sembrada: PENDIENTE mientras el escenario la pida y nadie la haya
     *  contestado, y lo que se contestó en cuanto hay respuesta. */
    private fun seededRefuelAnswers(): Flow<Map<String, RefuelStopAnswer>> {
        val lever = scenario?.refuelStopPending ?: MutableStateFlow(false)
        return combine(lever, refuelAnswers) { pending, answers ->
            when {
                answers.containsKey(REFUEL_STOP_SESSION_ID) -> answers
                pending -> answers + (REFUEL_STOP_SESSION_ID to RefuelStopAnswer.PENDING)
                else -> answers
            }
        }
    }

    private val mockSessions: List<UserParking> = buildList {
        // ── Active session ────────────────────────────────────────────────────
        add(UserParking(
            id = "parking_active_001",
            userId = "mock_user_001",
            vehicleId = "mock_vehicle_002",
            // In the city grid — the old point floated in the Guadalete on the Home map.
            location = GpsPoint(40.4244, -3.7015, 5f, now - 3_600_000L, 0f),
            isActive = true,
            spotType = SpotType.AUTO_DETECTED,
            detectionReliability = 0.92f,
            address = AddressInfo("Calle de Génova, 12", "Madrid", "Madrid", "España", "ES"),
        ))

        // ── Parada en el surtidor, sin contestar ──────────────────────────────
        // [DET-A-STOP-AT-A-PUMP-IS-NOT-A-PARKING-001] Cerrada hace dos horas tras cuatro minutos
        // en una gasolinera, con `publishedSpot = false` porque el gate retuvo la plaza. Es la fila
        // de la que viven el aviso de Home y el badge «Posible repostaje» del historial.
        add(UserParking(
            id = REFUEL_STOP_SESSION_ID,
            userId = "mock_user_001",
            vehicleId = "mock_vehicle_001",
            location = GpsPoint(
                40.4301, -3.7038, 7f,
                now - REFUEL_STOP_ENDED_AGO_MS - REFUEL_STOP_DWELL_MS, 0f,
            ),
            isActive = false,
            endedAtMs = now - REFUEL_STOP_ENDED_AGO_MS,
            publishedSpot = false,
            spotType = SpotType.AUTO_DETECTED,
            detectionReliability = 0.9f,
            address = AddressInfo("Calle de Bravo Murillo, 190", "Madrid", "Madrid", "España", "ES"),
            placeInfo = PlaceInfo("Repsol Bravo Murillo", PlaceCategory.FUEL),
        ))

        // ── Seat León (vehicle_001) — 65 sessions over 2 years ───────────────
        // La CALLE es la fuente única: de una fila de `DemoStreets` salen el pin, la dirección,
        // el trayecto y sus metros. Antes eran cuatro cuentas independientes —la calle por
        // `i % 9`, la coordenada por `i % 7` y `i % 5`, la polilínea dibujada a ojo hacia ella y
        // la distancia inventada— y no había forma de que hablaran del mismo sitio: el rótulo
        // decía una calle, el pin caía en otra y la línea cruzaba las manzanas de en medio.
        // Las calles siguen REPITIÉNDOSE por módulo, que es lo que hace que el dato de "tu calle
        // de siempre" llegue a su umbral de ≥3. [MOCK-A-DEMO-ROUTE-MUST-FOLLOW-THE-STREETS-001]
        for (i in 1..65) {
            // ⛔ NOT `i * 11` days. That spacing put the newest parking ELEVEN days back, so the
            // free window (7 days) was empty on every car and the ficha opened on "no hay nada"
            // for the one profile that is supposed to be full. A car in daily use parks several
            // times a week; the first fortnight of sessions is dense on purpose, and the tail
            // stretches back two years behind it. [STATS-UI-001]
            val daysAgo = if (i <= 14) (i - 1) / 2L else 7L + (i - 14) * 11L
            // The clock hour has to MOVE, or every session lands at the same time of day and
            // "hora punta" becomes whatever hour the emulator was launched at. A mode plus a
            // spread is what a real week looks like.
            val hourJitter = listOf(0L, 0L, 0L, 2L, 5L, 7L, 9L)[i % 7] * 3_600_000L
            val atMs = now - daysAgo * 86_400_000L - hourJitter
            val street = DemoStreets.leon[i % DemoStreets.leon.size]
            // Un pin a mano no tiene conducción grabada que dibujar ni que medir; el resto llega
            // a la calle por una de las aproximaciones reales, y a la misma calle se llega desde
            // rumbos distintos, como en la vida.
            // [MOCK-A-TRIP-WITHOUT-ITS-LINE-CANNOT-BE-SHOWN-001]
            val manual = i % 6 == 0
            val drive = if (manual) null else street.approaches[i % street.approaches.size]
            add(UserParking(
                id = "parking_leon_$i",
                userId = "mock_user_001",
                vehicleId = "mock_vehicle_001",
                // El coche acaba donde acaba el trayecto: este punto ES el último de la polilínea.
                location = GpsPoint(street.endLat, street.endLon, 10f, atMs, 0f),
                isActive = false,
                spotType = if (manual) SpotType.MANUAL_REPORT else SpotType.AUTO_DETECTED,
                detectionReliability = when {
                    manual -> 1.0f
                    i % 3 == 0 -> 0.90f
                    else -> 0.77f
                },
                // Streets REPEAT so the "usual street" fact reaches its ≥3 threshold, like a real
                // history does. Close provenance + route length feed the new stats.
                // [VEH-STATS-SAY-SOMETHING-USEFUL-001]
                detectionPath = if (manual) "manual" else "steps+egress",
                publishedSpot = i % 4 != 0,
                // Varied stays (1 h to 8 h) so the average is a real average, not one number
                // repeated 65 times.
                endedAtMs = atMs + (1 + i % 8) * 3_600_000L,
                // ⛔ La distancia es la LONGITUD DE ESA LÍNEA, no un número aparte: su contrato
                // (`UserParking:109`) dice que la estampa el repositorio al escribir la ruta. La
                // serie inventada de antes contradecía al dibujo y encima marcaba kilómetros en
                // pines manuales, que no tienen viaje ninguno.
                routeDistanceMeters = drive?.meters,
                routePolyline = drive?.polyline,
                // ⛔ La polilínea SOLA no basta: `routeAvailability()` exige además
                // `routeSnapped`, y sin él la sesión se queda en «Recalculating route…»
                // para siempre — que es lo que pasaba y parecía que no había ruta. Ahora la
                // afirmación es cierta: la línea viene casada a la calzada de origen.
                routeSnapped = drive != null,
                // El veredicto de ParkingSearchTime es de TRES valores, y el catálogo reproduce
                // los tres: un pin manual no tiene conducción grabada que medir (null), una
                // llegada directa MIDE cero —cero es una medida, no un hueco— y el resto son
                // vueltas de verdad, entre MIN_SEARCH_MS y ~9 min. Sin nulls la línea de
                // cobertura («14 of 16 trips») no diría nada, y sin ceros la media mentiría hacia
                // arriba. [MOCK-THE-FLAGSHIP-FIGURE-HAS-NO-MOCK-DATA-001]
                searchTimeMs = when {
                    manual -> null
                    i % 5 == 0 -> 0L
                    else -> 90_000L + (i % 11) * 47_000L
                },
                address = AddressInfo(street.name, "Madrid", "Madrid", "España", "ES"),
            ))
        }

        // ── Toyota Corolla BT (vehicle_002) — 45 sessions over 18 months ─────
        val corollaStreets = listOf(
            "Calle de Ponzano", "Calle de Santa Engracia", "Calle de Raimundo Fernández Villaverde", "Calle de Modesto Lafuente", "Calle de Alonso Cano",
        )
        for (i in 1..45) {
            // Same fix, gentler: this car is the BT profile (no route, so no km, no CO2 and no
            // cost), and it needs recent sessions for that ABSENCE to be visible in free.
            val daysAgo = if (i <= 6) (i - 1) / 2L else 4L + (i - 6) * 12L
            add(UserParking(
                id = "parking_corolla_$i",
                userId = "mock_user_001",
                vehicleId = "mock_vehicle_002",
                location = GpsPoint(
                    40.4184 + (i % 4) * 0.0006,
                    -3.6998 - (i % 6) * 0.0004,
                    8f,
                    now - daysAgo * 86_400_000L,
                    0f,
                ),
                isActive = false,
                spotType = if (i % 8 == 0) SpotType.MANUAL_REPORT else SpotType.AUTO_DETECTED,
                detectionReliability = if (i % 8 == 0) 1.0f else 0.95f,
                // This vehicle's history carries NO route on purpose — a park whose drive no
                // recorder covered is a real state, and the km line must honestly disappear for it.
                // (Not a property of the BT lane: since [TRIP-THE-TRACKER-RIDES-THE-SENTINEL-001] a
                // BT connect records its ride like any other.) [VEH-STATS-SAY-SOMETHING-USEFUL-001]
                detectionPath = if (i % 8 == 0) "manual" else "bt",
                // Y por eso `searchTimeMs` se queda null aquí, en las 45: la búsqueda se mide
                // desde los fixes crudos de la ruta, así que un aparcamiento sin ruta no tiene
                // NADA que medir. Es la misma ausencia que ya se lleva los km, no un olvido —
                // este coche existe para que el chip de tiempo de búsqueda NO se ofrezca.
                // [MOCK-THE-FLAGSHIP-FIGURE-HAS-NO-MOCK-DATA-001]
                publishedSpot = i % 3 != 0,
                endedAtMs = now - daysAgo * 86_400_000L + 8 * 3_600_000L,
                address = AddressInfo(corollaStreets[i % 5], "Madrid", "Madrid", "España", "ES"),
            ))
        }

        // ── One honest-close AREA in the Seat León history ───────────────────
        // The approximate variant is a state the timeline must render, so the device catalog
        // carries one: its row reads "Near X", never the exact claim the session itself refused.
        // 154 m is the 21-08 field figure; path/reliability are what `RunHonestCloseUseCase`
        // really stamps on a zone save. [UI-APPROXIMATE-ZONE-IN-HISTORY-001]
        add(UserParking(
            id = "parking_leon_zone",
            userId = "mock_user_001",
            vehicleId = "mock_vehicle_001",
            location = GpsPoint(40.4255, -3.6996, 12f, now - 4L * 86_400_000L, 0f),
            isActive = false,
            spotType = SpotType.AUTO_DETECTED,
            detectionReliability = 0.5f,
            detectionPath = "closed_approximate_zone",
            publishedSpot = false,
            endedAtMs = now - 4L * 86_400_000L + 2 * 3_600_000L,
            zoneRadiusMeters = 154f,
            address = AddressInfo("Calle de Alcalá, 43", "Madrid", "Madrid", "España", "ES"),
        ))

        // ── Honda CBR 600 (vehicle_003) — DELIBERATELY EMPTY ────────────────
        // A car with no history at all is a state the app has to draw (the empty-state
        // illustration + "aún sin aparcar" on the identity card), and until now no mock vehicle
        // was in it: the moto had 12 hand-marked parkings, all older than the free window, which
        // renders the "hidden by Plus" footer instead — a DIFFERENT state that looks like a bug.
        // The two must both be reachable in the mock, so this one is now truly empty.
        // [UI-HISTORY-A-LOADING-LIST-MUST-NOT-CLAIM-TO-BE-EMPTY-001] [STATS-UI-001]

        // ── Ford Transit BT (vehicle_004) — 18 sessions over 6 months ────────
        // Misma fuente única que el León, en el norte de la ciudad.
        // [MOCK-A-DEMO-ROUTE-MUST-FOLLOW-THE-STREETS-001]
        for (i in 1..18) {
            val daysAgo = i * 10L
            val atMs = now - daysAgo * 86_400_000L
            val street = DemoStreets.transit[i % DemoStreets.transit.size]
            val manual = i % 5 == 0
            val drive = if (manual) null else street.approaches[i % street.approaches.size]
            add(UserParking(
                id = "parking_transit_$i",
                userId = "mock_user_001",
                vehicleId = "mock_vehicle_004",
                location = GpsPoint(street.endLat, street.endLon, 12f, atMs, 0f),
                isActive = false,
                spotType = if (manual) SpotType.MANUAL_REPORT else SpotType.AUTO_DETECTED,
                detectionReliability = if (manual) 1.0f else 0.93f,
                detectionPath = if (manual) DetectionPath.ManualPin.label else DetectionPath.VehicleExitWindow.label,
                publishedSpot = i % 2 == 0,
                endedAtMs = atMs + 10 * 3_600_000L,
                routeDistanceMeters = drive?.meters,
                routePolyline = drive?.polyline,
                routeSnapped = drive != null,
                // Mismo reparto de tres valores que el 001, con otros módulos: dos coches con la
                // misma serie calcada delatarían que el dato es de pega.
                searchTimeMs = when {
                    i % 5 == 0 -> null
                    i % 4 == 0 -> 0L
                    else -> 120_000L + (i % 7) * 63_000L
                },
                address = AddressInfo(street.name, "Madrid", "Madrid", "España", "ES"),
            ))
        }
    }

    private val _sessionsFlow = MutableStateFlow(mockSessions)

    /** Base list + Dev-Catalog seed + runtime saves, with runtime releases applied. */
    private fun allSessionsFlow(): Flow<List<UserParking>> {
        val baseWithSeed = if (scenario == null) {
            _sessionsFlow.map { it }
        } else {
            combine(
                _sessionsFlow, scenario.ownParkedSession, scenario.approximateParking, scenario.firstHomeEntry,
            ) { list, own, approx, firstEntry ->
                val base = if (firstEntry) emptyList() else list
                if (own) base + seedFor(approx) else base
            }
        }
        return combine(
            baseWithSeed, savedSessions, releasedIds, retractedIds, seededRefuelAnswers(),
        ) { base, saved, released, retracted, refuels ->
            (base + saved)
                .map { s -> if (s.id in released) s.copy(isActive = false) else s }
                // [DET-A-STOP-AT-A-PUMP-IS-NOT-A-PARKING-001] La respuesta se aplica SOBRE la lista,
                // igual que la liberación: así contestar en el Dev Catalog hace desaparecer la
                // pregunta de la fila, que es justo el bucle que hay que poder probar en device.
                .map { s -> refuels[s.id]?.let { answer -> s.copy(refuelStop = answer) } ?: s }
                // [PARK-A-REFUTED-PIN-LEAVES-THE-HISTORY-001] The mock hides a withdrawn row from
                // the history exactly as the Room queries do, so the Dev Catalog plays the real loop.
                .filterNot { it.id in retracted }
        }
    }

    private fun currentSessions(): List<UserParking> {
        val seed = if (scenario?.ownParkedSession?.value == true) {
            listOf(seedFor(scenario.approximateParking.value))
        } else {
            emptyList()
        }
        val base = if (scenario?.firstHomeEntry?.value == true) emptyList() else mockSessions
        return (base + seed + savedSessions.value)
            .map { s -> if (s.id in releasedIds.value) s.copy(isActive = false) else s }
            // The by-id and active lookups are DIAGNOSTIC reads: they still see a withdrawn row,
            // stamped, like production. [PARK-A-REFUTED-PIN-LEAVES-THE-HISTORY-001]
            .map { s -> retractedIds.value[s.id]?.let { at -> s.copy(retractedAtMs = at) } ?: s }
            // [DET-A-STOP-AT-A-PUMP-IS-NOT-A-PARKING-001] La lectura por id ve lo mismo que el
            // stream; sin esto, contestar desde Home no cambiaría lo que el peek vuelve a leer.
            .map { s ->
                val answered = refuelAnswers.value[s.id]
                    ?: RefuelStopAnswer.PENDING.takeIf {
                        s.id == REFUEL_STOP_SESSION_ID && scenario?.refuelStopPending?.value == true
                    }
                answered?.let { s.copy(refuelStop = it) } ?: s
            }
    }

    override suspend fun saveNewParkingSession(session: UserParking): Result<String?> {
        savedSessions.value = savedSessions.value + session.copy(isActive = true)
        return Result.success(session.id)
    }

    override suspend fun getActiveSessionByGeofence(geofenceId: String): UserParking? =
        currentSessions().find { it.isActive && it.geofenceId == geofenceId }

    override suspend fun getActiveSessionByVehicle(vehicleId: String): UserParking? =
        currentSessions().find { it.isActive && it.vehicleId == vehicleId }

    override suspend fun getSessionById(id: String): UserParking? =
        currentSessions().find { it.id == id }

    override fun observeActiveSessions(): Flow<List<UserParking>> {
        val active = allSessionsFlow().map { list -> list.filter { it.isActive } }
        val rt = runtime ?: return active
        // Sim fidelity: ALWAYS surface the parked session first, so a starting trip captures it as the
        // faded "departure point", THEN release only the DEPARTING car's session while running —
        // exactly what a confirmed departure does in production. The other cars stay parked, so the
        // sim reproduces the multi-parking field case (one car driving, one still parked) instead of
        // hiding it behind an empty list. Readiness reaches Monitoring on its own now that a trip
        // outranks another car's parked session. [DRIVE-SIM-001] [TRIP-TRAIL-001]
        // [DET-READY-TRIP-OVER-PARKED-001]
        return active.flatMapLatest { sessions ->
            flow {
                emit(sessions)
                emitAll(
                    combine(rt.isRunning, rt.trip) { running, trip ->
                        when {
                            !running -> sessions
                            // No resolved origin (manual arm): fall back to clearing them all, the
                            // single-car park→depart the sim modelled before.
                            trip?.departingVehicleId == null -> emptyList()
                            else -> sessions.filterNot { it.vehicleId == trip.departingVehicleId }
                        }
                    },
                )
            }
        }
    }

    override fun observeAllSessions(): Flow<List<UserParking>> = allSessionsFlow()

    override fun observeSessionsByVehicle(vehicleId: String): Flow<List<UserParking>> =
        allSessionsFlow().map { list -> list.filter { it.vehicleId == vehicleId } }

    override suspend fun getSessionsPaged(limit: Int, offset: Int): List<UserParking> =
        currentSessions().drop(offset).take(limit)

    override suspend fun getSessionsByVehiclePaged(vehicleId: String, limit: Int, offset: Int): List<UserParking> =
        currentSessions()
            .filter { it.vehicleId == vehicleId && !it.isActive }
            .sortedByDescending { it.location.timestamp }
            .drop(offset)
            .take(limit)

    /** [DET-HANDOFF-NOT-MANUAL-001 §B] Pending deduced departures, by session id. */
    val provisionalDepartures = mutableMapOf<String, Long?>()

    override suspend fun markProvisionalDeparture(sessionId: String, atMs: Long?): Result<Unit> {
        provisionalDepartures[sessionId] = atMs
        return Result.success(Unit)
    }

    /** [PARK-A-REFUTED-PIN-LEAVES-THE-HISTORY-001] Withdrawn, not removed — the Dev Catalog plays
     *  the same loop production does: the row leaves the history and stays on file. */
    override suspend fun retractParkingSession(sessionId: String, retractedAtMs: Long): Result<Unit> {
        retractedIds.value = retractedIds.value + (sessionId to retractedAtMs)
        return Result.success(Unit)
    }

    override suspend fun clearActiveParkingSession(
        sessionId: String,
        endedAtMs: Long,
        publishedSpot: Boolean,
        refuelStop: RefuelStopAnswer?,
    ): Result<Unit> {
        // Releasing really frees the session so the mock plays the whole loop: the parked card
        // and the watching line drop, and the story returns to its cold-start row. [UX-PARKED-STATE-001]
        releasedIds.value = releasedIds.value + sessionId
        // [DET-A-STOP-AT-A-PUMP-IS-NOT-A-PARKING-001] El cierre que marca una parada en el surtidor
        // la deja preguntando, igual que en producción, para que el Dev Catalog pueda enseñar el
        // aviso de Home y la fila del historial sin inventarse un estado.
        refuelStop?.let { refuelAnswers.value = refuelAnswers.value + (sessionId to it) }
        return Result.success(Unit)
    }

    override suspend fun answerRefuelStop(
        sessionId: String,
        answer: RefuelStopAnswer,
    ): Result<Unit> {
        // Como el `WHERE refuelStop IS NOT NULL` de producción: sólo alcanza a la parada por la que
        // la app preguntó. [DET-A-STOP-AT-A-PUMP-IS-NOT-A-PARKING-001]
        if (sessionId == REFUEL_STOP_SESSION_ID) {
            refuelAnswers.value = refuelAnswers.value + (sessionId to answer)
        }
        return Result.success(Unit)
    }

    override fun observePendingRefuelStop(): Flow<UserParking?> =
        allSessionsFlow().map { sessions ->
            // `allSessionsFlow` ya aplica las respuestas, así que esto pregunta lo mismo que la
            // consulta de Room: la parada cerrada cuya pregunta sigue abierta.
            sessions.firstOrNull { it.hasPendingRefuelQuestion && !it.isActive }
        }

    /** [DET-A-TRAIN-IS-NOT-A-CAR-001] The inverse of the release above: the car comes back. */
    override suspend fun restoreParkingSession(sessionId: String): Result<Unit> {
        restoredIds.value = restoredIds.value + sessionId
        releasedIds.value = releasedIds.value - sessionId
        return Result.success(Unit)
    }

    override suspend fun syncFromRemote(userId: String): Result<Unit> = Result.success(Unit)

    override suspend fun pushPendingParkingSessions(): Result<Unit> = Result.success(Unit)

    override suspend fun updateParkingSessionAddressAndPlace(
        id: String,
        address: AddressInfo?,
        placeInfo: PlaceInfo?,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun updateParkingSessionPosition(
        id: String,
        location: GpsPoint,
        detectionPath: String,
        detectionReliability: Float,
    ): Result<UserParking> {
        val session = currentSessions().find { it.id == id } ?: return Result.failure(Exception("Not found"))
        // Mirrors the real DAO's UPDATE, clearing the doubt radius with the provenance rewrite —
        // a fake that kept the old radius would let a test pass on behaviour production does not
        // have. [PARK-A-PIN-MUST-SAY-WHO-PLACED-IT-001]
        val moved = session.copy(
            // ⛔ …y conservando el RELOJ, como el UPDATE real, que no toca la columna `timestamp`:
            // se corrige dónde está el coche, no desde cuándo lleva ahí. Copiar la `location` entera
            // metía el reloj nuevo por la puerta de atrás.
            // [PARK-A-CORRECTION-MOVES-THE-PLACE-NOT-THE-CLOCK-001]
            location = location.copy(timestamp = session.location.timestamp),
            detectionPath = detectionPath,
            detectionReliability = detectionReliability,
            zoneRadiusMeters = null,
        )
        savedSessions.value = savedSessions.value.map { if (it.id == id) moved else it }
        return Result.success(moved)
    }

    override suspend fun updateParkingSessionRoute(
        id: String,
        routePolyline: String?,
        snapped: Boolean,
        inferredSpans: String?,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun getPreviousSession(vehicleId: String, beforeTimestamp: Long): UserParking? =
        null

    override suspend fun resolveInferredRoute(
        id: String,
        resolution: RouteInferenceResolution,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun deleteAllData(userId: String): Result<Unit> = Result.success(Unit)

    private companion object {
        const val APPROXIMATE_SEED_RADIUS_M = 154f

        /** [DET-A-STOP-AT-A-PUMP-IS-NOT-A-PARKING-001] La parada en el surtidor sembrada: la
         *  pregunta de Home y el badge del historial cuelgan de esta fila. */
        const val REFUEL_STOP_SESSION_ID = "parking_refuel_pending"

        /** Hace dos horas: dentro de la ventana en la que Home todavía pregunta. */
        const val REFUEL_STOP_ENDED_AGO_MS = 2L * 60L * 60L * 1000L

        /** Cuatro minutos en el surtidor — un depósito, no un aparcamiento. */
        const val REFUEL_STOP_DWELL_MS = 4L * 60L * 1000L
    }
}
