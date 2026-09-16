@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.domain.usecase.stats

import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.Entitlement
import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.domain.usecase.entitlement.ObserveIsPlusUseCase
import com.rndeveloper.paparcar.fakes.FakeAppPreferences
import com.rndeveloper.paparcar.fakes.FakeEntitlementRepository
import com.rndeveloper.paparcar.fakes.FakeUserParkingRepository
import com.rndeveloper.paparcar.fakes.FakeVehicleRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * El resumen del mes cerrado que LLAMA. [STATS-PLUS-THE-RECAP-KNOCKS-001]
 *
 * Dos veredictos que este use case no tenía y que este test fija:
 * el gate de plan, que se creía innecesario, y el precio, sin el cual la notificación no podía decir
 * nunca el gasto. [STATS-A-PACE-ROW-NAMES-THE-PERIOD-IT-PROJECTS-001]
 */
class RunMonthRecapUseCaseTest {

    private val tz = TimeZone.currentSystemDefault()

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12) =
        LocalDateTime(year, month, day, hour, 0).toInstant(tz).toEpochMilliseconds()

    /** El día 2 de octubre: dentro de la ventana de noticia, y con septiembre ya cerrado. */
    private val nowMs = at(2026, 10, 2)

    private val car = Vehicle(
        id = "v-1",
        userId = "u-1",
        sizeCategory = VehicleSize.MEDIUM_SUV,
        fuelType = FuelType.GASOLINE,
        isActive = true,
    )

    private fun drive(day: Int, meters: Float = 12_000f) = UserParking(
        id = "s-$day",
        vehicleId = car.id,
        location = GpsPoint(
            40.0,
            -3.7,
            accuracy = 5f,
            timestamp = at(2026, 9, day),
            speed = 0f,
        ),
        isActive = false,
        routeDistanceMeters = meters,
        address = AddressInfo(
            street = null,
            city = null,
            region = null,
            country = null,
            countryCode = "ES",
        ),
    )

    private fun useCase(
        sessions: List<UserParking>,
        entitlement: Entitlement,
        preferences: FakeAppPreferences = FakeAppPreferences(),
    ) = RunMonthRecapUseCase(
        userParkingRepository = FakeUserParkingRepository(initialSessions = sessions),
        vehicleRepository = FakeVehicleRepository(defaultVehicle = car),
        observeIsPlus = ObserveIsPlusUseCase(FakeEntitlementRepository(entitlement)),
        appPreferences = preferences,
        clock = { nowMs },
    )

    /**
     * ⛔ **El gate que no existía.** La ventana free de 7 días el 2 de octubre llega hasta el 25 de
     * septiembre, así que las sesiones del final del mes cerrado SOBREVIVEN al gate del historial:
     * la cuenta free habría recibido una notificación resumiendo una semana de septiembre bajo el
     * nombre del mes entero.
     */
    @Test
    fun should_notKnock_when_theUserCannotSeeTheWholeClosedMonth() = runTest {
        // A propósito al final del mes: dentro de los 7 días rodantes de una cuenta free, así que
        // sin este gate el agregador SÍ habría encontrado sesiones y la notificación habría salido.
        val outcome = useCase(listOf(drive(28), drive(29)), Entitlement.FREE)()
        val skipped = assertIs<MonthRecapOutcome.Skipped>(outcome, "un free no ve septiembre entero")
        assertEquals("month-not-fully-visible", skipped.reason)
    }

    @Test
    fun should_knock_when_thePlanSeesTheWholeMonth() = runTest {
        val outcome = useCase(listOf(drive(3), drive(28)), Entitlement.PLUS)()
        val told = outcome as? MonthRecapOutcome.Tell
        assertNotNull(told, "Plus ve el mes cerrado entero")
        assertEquals(2, told.stats.parkings)
    }

    /**
     * ⛔ **El gasto.** Sin precio, `costEstimate` es null por construcción y la notificación no
     * podía decir nunca cuánto costó el mes — que es lo que hizo desde el día que se escribió.
     */
    @Test
    fun should_priceTheClosedMonth_soTheNotificationCanSayWhatItCost() = runTest {
        val told = useCase(listOf(drive(3), drive(28)), Entitlement.PLUS)() as MonthRecapOutcome.Tell
        val cost = told.stats.costEstimate
        assertNotNull(cost, "el resumen se calculaba sin precio y la fila de gasto era inalcanzable")
        assertTrue(cost.amount > 0.0)
    }

    /** Una vez al mes: el segundo tick del mismo mes ya no llama. */
    @Test
    fun should_tellOnlyOnce_perClosedMonth() = runTest {
        val preferences = FakeAppPreferences()
        val sessions = listOf(drive(3), drive(28))
        assertTrue(useCase(sessions, Entitlement.PLUS, preferences)() is MonthRecapOutcome.Tell)
        assertTrue(useCase(sessions, Entitlement.PLUS, preferences)() is MonthRecapOutcome.Skipped)
    }
}
