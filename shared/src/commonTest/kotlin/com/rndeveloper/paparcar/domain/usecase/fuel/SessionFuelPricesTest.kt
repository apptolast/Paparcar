package com.rndeveloper.paparcar.domain.usecase.fuel

import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.Money
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.VehicleSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * De qué país se cobra un periodo. [FUEL-PRICE-RESOLVER-001]
 *
 * Esta regla vivía privada dentro de `HistoryTimeline.resolve`, y por eso el segundo consumidor —el
 * use case que resume el mes cerrado para la notificación— no pasaba precio ninguno: su fila de
 * gasto era código inalcanzable. [STATS-A-PACE-ROW-NAMES-THE-PERIOD-IT-PROJECTS-001]
 */
class SessionFuelPricesTest {

    private val resolve = ResolveFuelPriceUseCase()

    private fun car(fuel: FuelType? = FuelType.GASOLINE) = Vehicle(
        id = "v-1",
        userId = "u-1",
        sizeCategory = VehicleSize.MEDIUM_SUV,
        fuelType = fuel,
    )

    private fun session(timestamp: Long, countryCode: String?) = UserParking(
        id = "s-$timestamp",
        location = GpsPoint(0.0, 0.0, accuracy = 5f, timestamp = timestamp, speed = 0f),
        isActive = false,
        address = countryCode?.let {
            AddressInfo(street = null, city = null, region = null, country = null, countryCode = it)
        },
    )

    /** Un periodo que cruza una frontera se cobra donde el conductor ACABÓ: es donde repostará, y
     *  sumar cada sesión en su moneda daría un total en monedas mezcladas que el agregador rechaza. */
    @Test
    fun should_priceWhereTheDriverEndedUp_when_thePeriodCrossesABorder() {
        val prices = resolve.forSessions(
            vehicle = car(),
            sessions = listOf(session(100L, "ES"), session(900L, "IT")),
        )
        assertEquals(resolve.litre(car(), "IT")?.perUnit, prices.litre?.perUnit)
    }

    /** Si la más reciente no sabe su país, se cae a la primera que sí — no a «ninguno». */
    @Test
    fun should_fallBackToAnySessionThatKnowsItsCountry() {
        val prices = resolve.forSessions(
            vehicle = car(),
            sessions = listOf(session(100L, "ES"), session(900L, null)),
        )
        val litre = prices.litre
        assertNotNull(litre, "había un país en el periodo y se ignoró")
        assertEquals(resolve.litre(car(), "ES")?.perUnit, litre.perUnit)
    }

    /** Sin coche no se adivina nada: las cifras de consumo se ausentan, no se estiman. */
    @Test
    fun should_answerEmpty_when_thereIsNoVehicle() {
        val prices = resolve.forSessions(vehicle = null, sessions = listOf(session(1L, "ES")))
        assertNull(prices.litre)
        assertNull(prices.kwh)
        assertNull(prices.quoted)
    }

    /**
     * El litro se pregunta primero porque la mayoría de los coches sólo tienen ese escalón — y los
     * dos no pueden discrepar, así que cualquiera responde por la etiqueta.
     */
    @Test
    fun should_quoteTheLitreFirst_when_theCarHasBoth() {
        val phev = Vehicle(
            id = "v-2",
            userId = "u-1",
            sizeCategory = VehicleSize.MEDIUM_SUV,
            fuelType = FuelType.PLUG_IN_HYBRID,
            fuelPricePerLiter = Money(1.30, "EUR"),
            energyPricePerKwh = Money(0.18, "EUR"),
        )
        val prices = resolve.forSessions(phev, listOf(session(1L, "ES")))
        assertNotNull(prices.kwh, "un enchufable necesita los DOS escalones")
        assertEquals(prices.litre, prices.quoted)
    }
}
