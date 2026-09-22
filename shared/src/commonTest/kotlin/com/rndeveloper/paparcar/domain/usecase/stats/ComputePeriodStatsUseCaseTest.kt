package com.rndeveloper.paparcar.domain.usecase.stats

import com.rndeveloper.paparcar.domain.detection.DetectionPath
import com.rndeveloper.paparcar.domain.entitlement.gateHistoryToEntitlement
import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.Money
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.VehicleSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ComputePeriodStatsUseCaseTest {

    private val compute = ComputePeriodStatsUseCase()
    private val nowMs = 1_790_000_000_000L
    private val dayMs = 86_400_000L

    private fun vehicle(
        id: String = "v-1",
        fuel: FuelType? = FuelType.GASOLINE,
        consumption: Float? = null,
        consumptionElectric: Float? = null,
    ) = Vehicle(
        id = id,
        userId = "u-1",
        sizeCategory = VehicleSize.MEDIUM_SUV,
        fuelType = fuel,
        consumptionPer100 = consumption,
        consumptionElectricPer100 = consumptionElectric,
    )

    private fun session(
        ageDays: Int,
        vehicleId: String = "v-1",
        distanceMeters: Float? = 10_000f,
        active: Boolean = false,
        parkedForHours: Long = 2,
        street: String? = null,
        searchTimeMs: Long? = null,
    ) = UserParking(
        id = "s-$ageDays-$vehicleId-$active",
        userId = "u-1",
        vehicleId = vehicleId,
        location = GpsPoint(40.4, -3.7, 8f, nowMs - ageDays * dayMs, 0f),
        isActive = active,
        routeDistanceMeters = distanceMeters,
        endedAtMs = if (active) null else nowMs - ageDays * dayMs + parkedForHours * 3_600_000L,
        address = street?.let { AddressInfo(street = it, city = "Madrid", region = null, country = "ES") },
        searchTimeMs = searchTimeMs,
    )

    /** Igual que [session], pero declarando QUIÉN puso el pin. [DET-A-YES-IS-NOT-A-HAND-001] */
    private fun sessionVia(ageDays: Int, path: DetectionPath) =
        session(ageDays, vehicleId = "v-$ageDays").copy(detectionPath = path.label)

    // ── Cobertura de la detección ────────────────────────────────────────────

    @Test
    fun `should_notCountAnAskAsDetectedAlone_when_theUserOnlyConfirmedIt`() {
        // ⛔ El consumidor que este ticket cambia PARA QUE NADA CAMBIE. La cifra era
        // `source != Manual`, y `Manual` significaba a la vez «lo puso tu mano» y «la app preguntó
        // y dijiste que sí». Al separarlos, dejarla igual habría empezado a contar como detectada
        // sola una sesión que la app TUVO que preguntar. Si hubo que preguntar, no se detectó solo.
        // [DET-A-YES-IS-NOT-A-HAND-001]
        val stats = assertNotNull(
            compute(
                sessions = listOf(
                    sessionVia(1, DetectionPath.StepsEgress),
                    sessionVia(2, DetectionPath.KinematicEgress),
                    sessionVia(3, DetectionPath.Bt),
                    sessionVia(4, DetectionPath.ManualPin),
                    sessionVia(5, DetectionPath.UserAnswered),
                    sessionVia(6, DetectionPath.Nudge()),
                ),
                vehicles = (1..6).map { vehicle("v-$it") },
            ),
        )

        assertEquals(6, stats.detectionKnownCount, "las seis declaran su procedencia")
        assertEquals(
            3,
            stats.autoDetectedCount,
            "sólo las tres MEDIDAS cuentan: ni la mano, ni el «sí» a un aviso",
        )
    }

    // ── Energy ───────────────────────────────────────────────────────────────

    @Test
    fun `should_burnTheOverride_when_theUserGaveOne_andTheDefaultOtherwise`() {
        // 10 km at 5 l/100 (override) + 10 km at 6.5 l/100 (gasoline default) = 0.5 + 0.65 l.
        val stats = compute(
            sessions = listOf(session(1, "v-override"), session(2, "v-default")),
            vehicles = listOf(
                vehicle("v-override", consumption = 5f),
                vehicle("v-default"),
            ),
        )
        assertEquals(1.15, stats?.fuelLiters!!, absoluteTolerance = 1e-6)
        assertNull(stats.energyKwh, "a gasoline car draws no battery")
        assertNotNull(stats.co2Kg)
    }

    @Test
    fun `should_carryBothEnergies_when_theVehicleIsAPlugInHybrid`() {
        // 10 km at 3 l/100 + 12 kWh/100 (PHEV defaults) → 0.3 l AND 1.2 kWh.
        val stats = compute(
            sessions = listOf(session(1)),
            vehicles = listOf(vehicle(fuel = FuelType.PLUG_IN_HYBRID)),
        )
        assertEquals(0.3, stats?.fuelLiters!!, absoluteTolerance = 1e-6)
        assertEquals(1.2, stats.energyKwh!!, absoluteTolerance = 1e-6)
    }

    @Test
    fun `should_drawOnlyKwh_when_theVehicleIsElectric`() {
        // 10 km at 17 kWh/100 → 1.7 kWh, no litres — and CO₂ from the grid mix, not combustion.
        val stats = compute(
            sessions = listOf(session(1)),
            vehicles = listOf(vehicle(fuel = FuelType.ELECTRIC)),
        )
        assertNull(stats?.fuelLiters)
        assertEquals(1.7, stats?.energyKwh!!, absoluteTolerance = 1e-6)
        assertEquals(1.7 * 0.25, stats.co2Kg!!, absoluteTolerance = 1e-6)
    }

    @Test
    fun `should_reportNoEnergy_when_noSessionHasAMeasuredRoute`() {
        val stats = compute(
            sessions = listOf(session(1, distanceMeters = null)),
            vehicles = listOf(vehicle()),
        )
        assertNull(stats?.distance)
        assertNull(stats?.fuelLiters)
        assertNull(stats?.co2Kg)
    }

    @Test
    fun `should_carryTheCoverage_when_onlySomeSessionsMeasuredADistance`() {
        // [UI-HISTORY-A-PARTIAL-SUM-IS-NOT-A-TOTAL-001] The subtotal names what it covers.
        val stats = compute(
            sessions = listOf(session(1), session(2, distanceMeters = null)),
            vehicles = listOf(vehicle()),
        )
        assertEquals(1, stats?.distance?.fromParkings)
        assertEquals(2, stats?.distance?.ofParkings)
        assertEquals(false, stats?.distance?.isComplete)
    }

    // ── Cost ─────────────────────────────────────────────────────────────────

    @Test
    fun `should_estimateTheCost_when_everyConsumedEnergyHasItsPrice`() {
        val stats = compute(
            sessions = listOf(session(1)),
            vehicles = listOf(vehicle()),
            fuelPricePerLiter = Money(1.60, "EUR"),
        )
        // 0.65 l × 1.60 €.
        assertEquals(1.04, stats?.costEstimate?.amount!!, absoluteTolerance = 1e-6)
        assertEquals("EUR", stats.costEstimate.currency)
    }

    @Test
    fun `should_refuseAPartialCost_when_oneConsumedEnergyLacksItsPrice`() {
        // A PHEV burned litres AND kWh; pricing only the litres would print a lie.
        val stats = compute(
            sessions = listOf(session(1)),
            vehicles = listOf(vehicle(fuel = FuelType.PLUG_IN_HYBRID)),
            fuelPricePerLiter = Money(1.60, "EUR"),
            energyPricePerKwh = null,
        )
        assertNull(stats?.costEstimate)
    }

    @Test
    fun `should_haveNoCost_until_pricesExist`() {
        val stats = compute(sessions = listOf(session(1)), vehicles = listOf(vehicle()))
        assertNull(stats?.costEstimate)
    }

    // ── Duration ─────────────────────────────────────────────────────────────

    @Test
    fun `should_excludeTheActiveSession_fromDuration_andAverageTheEndedOnes`() {
        val stats = compute(
            sessions = listOf(
                session(1, parkedForHours = 2),
                session(2, parkedForHours = 4),
                session(3, parkedForHours = 6),
                // Parked right now: the present has no duration yet.
                session(0, active = true),
            ),
            vehicles = listOf(vehicle()),
        )
        assertEquals(4 * 3_600_000L, stats?.avgParkedDurationMs)
    }

    @Test
    fun `should_suppressDuration_belowThreeEndedSessions`() {
        val stats = compute(
            sessions = listOf(session(1), session(2)),
            vehicles = listOf(vehicle()),
        )
        assertNull(stats?.avgParkedDurationMs)
    }

    // ── Comparison ───────────────────────────────────────────────────────────

    @Test
    fun `should_attachThePreviousPeriod_when_given_andNeverNestFurther`() {
        val stats = compute(
            sessions = listOf(session(1), session(2)),
            previousSessions = listOf(session(10)),
            vehicles = listOf(vehicle()),
        )
        assertEquals(2, stats?.parkings)
        assertEquals(1, stats?.previous?.parkings)
        assertNull(stats?.previous?.previous, "one step back IS the comparison")
    }

    @Test
    fun `should_leaveTheComparisonNull_when_thePreviousPeriodWasEmpty`() {
        val stats = compute(
            sessions = listOf(session(1)),
            previousSessions = emptyList(),
            vehicles = listOf(vehicle()),
        )
        assertNull(stats?.previous)
    }

    // ── Entitlement composition ──────────────────────────────────────────────

    @Test
    fun `should_giveFreeAndPlusDifferentFigures_overTheSameDataset`() {
        // The gate feeds the aggregator: a free user's stats are computed over what they may SEE
        // (7 days) — anything else would be a paywall with a hole. [FREE-LIMITS-001]
        val sessions = listOf(session(1), session(2), session(20), session(30))
        val vehicles = listOf(vehicle())

        val plus = compute(gateHistoryToEntitlement(sessions, isPlus = true, nowMs = nowMs).visible, vehicles)
        val free = compute(gateHistoryToEntitlement(sessions, isPlus = false, nowMs = nowMs).visible, vehicles)

        assertEquals(4, plus?.parkings)
        assertEquals(2, free?.parkings)
        assertNotEquals(plus?.fuelLiters, free?.fuelLiters)
        assertTrue(free?.fuelLiters!! < plus?.fuelLiters!!)
    }

    // ── Search time [TRIP-THE-SEARCH-FOR-A-SPOT-IS-MEASURED-001] ─────────────

    @Test
    fun `should_sumTheSearchTime_when_sessionsMeasuredIt_andStayNullOtherwise`() {
        val withMeasure = compute(
            sessions = listOf(
                session(1, searchTimeMs = 120_000L),
                session(2, searchTimeMs = 60_000L),
                session(3), // unmeasured contributes nothing — absent, never 0
            ),
        )
        assertEquals(180_000L, withMeasure?.searchTime?.value, "time LOST is a sum, not an average")
        // ...and the sum says what it covers: two of the three parkings produced it.
        // [STATS-A-MEASURE-CARRIES-ITS-COVERAGE-001]
        assertEquals(2, withMeasure?.searchTime?.fromParkings)
        assertEquals(3, withMeasure?.searchTime?.ofParkings)
        assertEquals(false, withMeasure?.searchTime?.isComplete, "2 of 3 is not a period total")

        val without = compute(sessions = listOf(session(1), session(2)))
        assertNull(without?.searchTime, "no measured session -> no figure, never 0")
    }

    @Test
    fun `should_reportCompleteCoverage_when_everySessionMeasuredItsSearch`() {
        val stats = compute(
            sessions = listOf(
                session(1, searchTimeMs = 120_000L),
                session(2, searchTimeMs = 60_000L),
            ),
        )
        assertEquals(true, stats?.searchTime?.isComplete, "the whole period is a plain figure")
    }
}
