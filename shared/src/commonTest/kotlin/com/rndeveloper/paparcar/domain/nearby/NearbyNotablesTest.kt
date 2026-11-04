package com.rndeveloper.paparcar.domain.nearby

import com.rndeveloper.paparcar.domain.fuel.FuelStation
import com.rndeveloper.paparcar.domain.fuel.StationPrice
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.Money
import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.parkings.FeeInfo
import com.rndeveloper.paparcar.domain.parkings.PublicParking
import com.rndeveloper.paparcar.domain.parkings.PublicParkingKind
import com.rndeveloper.paparcar.domain.usecase.fuel.NearbyFuelStation
import com.rndeveloper.paparcar.domain.usecase.parkings.NearbyPublicParking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [HOME-THE-FIRST-GLANCE-SPEAKS-FOR-THE-CAR-001] Qué puede ocupar el primer vistazo.
 *
 * Los tests de aquí son casi todos sobre lo que **no** se enseña: el orden que no cambia, el sujeto
 * que no entra sin su umbral, y la lista vacía que es una respuesta y no un hueco que rellenar.
 */
class NearbyNotablesTest {

    private fun at(lat: Double) =
        GpsPoint(latitude = lat, longitude = -3.70, accuracy = 5f, timestamp = 0L, speed = 0f)

    private val here = at(40.0)

    private fun spot(id: String, lat: Double) = Spot(
        id = id,
        location = at(lat),
        reportedBy = "test",
    )

    private fun carPark(id: String, distance: Double) = NearbyPublicParking(
        parking = PublicParking(
            id = id,
            name = id,
            operator = null,
            kind = PublicParkingKind.SURFACE,
            latitude = 0.0,
            longitude = 0.0,
            capacity = null,
            fee = FeeInfo.UNKNOWN,
            maxHeightMeters = null,
            openingHours = null,
        ),
        distanceMeters = distance,
    )

    private fun station(id: String, distance: Double, price: Double?) = NearbyFuelStation(
        station = FuelStation(
            id = id, name = id, address = null, town = null,
            latitude = 0.0, longitude = 0.0, prices = emptyMap(),
        ),
        distanceMeters = distance,
        stationKey = "test|$id",
        source = "test",
        price = price?.let { StationPrice(Money(it, "EUR")) },
    )

    // ── Lo que entra ──────────────────────────────────────────────────────────

    @Test
    fun should_keep_the_fixed_order_when_the_three_subjects_answer() {
        val notables = nearbyNotables(
            spots = listOf(spot("s", 40.001)),
            parkings = listOf(carPark("p", 240.0)),
            fuel = listOf(station("f1", 300.0, 1.489), station("f2", 1400.0, 1.419)),
            from = here,
        )

        assertEquals(
            listOf(NearbySubject.SPOT, NearbySubject.FUEL, NearbySubject.CAR_PARK),
            notables.map { it.subject },
            "el orden es FIJO —reordenar el primer vistazo según los datos del momento es lo que " +
                "hace que el usuario no aprenda dónde está cada cosa— y es por NOVEDAD, no por " +
                "intención: la plaza caduca en minutos, el precio se mueve a diario y el parking más " +
                "cercano es casi siempre el mismo",
        )
    }

    @Test
    fun should_name_one_item_per_subject_and_never_two() {
        val notables = nearbyNotables(
            spots = listOf(spot("a", 40.001), spot("b", 40.002), spot("c", 40.003)),
            parkings = listOf(carPark("p1", 240.0), carPark("p2", 520.0)),
            fuel = emptyList(),
            from = here,
        )

        assertEquals(2, notables.size, "tres plazas y dos parkings son DOS notables, no cinco")
        assertEquals(1, notables.count { it.subject == NearbySubject.SPOT })
    }

    @Test
    fun should_point_at_the_nearest_spot_and_not_the_first_of_the_list() {
        val notables = nearbyNotables(
            spots = listOf(spot("lejos", 40.010), spot("cerca", 40.001)),
            parkings = emptyList(),
            fuel = emptyList(),
            from = here,
        )

        assertEquals("cerca", notables.single().id)
    }

    @Test
    fun should_point_at_the_cheapest_station_even_when_the_lane_is_sorted_by_distance() {
        // El carril puede estar en «más cercanas primero», y entonces la primera fila NO es la más
        // barata. Asumirla sería nombrar en la tarjeta algo distinto de lo que el chip marca abajo.
        val notables = nearbyNotables(
            spots = emptyList(),
            parkings = emptyList(),
            fuel = listOf(
                station("cara_y_cerca", 300.0, 1.529),
                station("barata_y_lejos", 1400.0, 1.419),
            ),
            from = here,
        )

        assertEquals("test|barata_y_lejos", notables.single().id)
    }

    @Test
    fun should_trust_the_order_the_user_reads_for_the_nearest_car_park() {
        // La lista llega ordenada por distancia: medir otra vez sería una SEGUNDA definición de
        // «el más cercano», y dos definiciones de lo mismo acaban discrepando.
        val notables = nearbyNotables(
            spots = emptyList(),
            parkings = listOf(carPark("primero", 240.0), carPark("segundo", 520.0)),
            fuel = emptyList(),
            from = here,
        )

        assertEquals("primero", notables.single().id)
    }

    // ── Lo que NO entra ───────────────────────────────────────────────────────

    @Test
    fun should_answer_nothing_at_all_when_no_subject_has_anything() {
        val notables = nearbyNotables(emptyList(), emptyList(), emptyList(), here)

        assertTrue(
            notables.isEmpty(),
            "una lista vacía es la respuesta: la superficie no dibuja sección, ni hueco, ni una " +
                "caja diciendo que no hay nada",
        )
    }

    @Test
    fun should_not_offer_a_station_that_quotes_no_price() {
        // Sin cifra no hay noticia — aunque la gasolinera exista, y existe: el carril sigue
        // enseñándola sin precio. Lo que no puede es ocupar el primer vistazo.
        val notables = nearbyNotables(
            spots = emptyList(),
            parkings = emptyList(),
            fuel = listOf(station("sin_precio", 300.0, price = null)),
            from = here,
        )

        assertTrue(notables.isEmpty(), "una gasolinera sin precio no es un precio de cero")
    }

    @Test
    fun should_still_answer_for_spots_without_a_fix() {
        // Sin ubicación no hay «la más cercana» que medir, pero sí hay plazas. Se señala la
        // primera, que es lo único que se puede ordenar — y la distancia queda NULA, no en cero.
        val notables = nearbyNotables(
            spots = listOf(spot("a", 40.001), spot("b", 40.002)),
            parkings = emptyList(),
            fuel = emptyList(),
            from = null,
        )

        assertEquals("a", notables.single().id)
        assertEquals(
            null,
            notables.single().distanceMeters,
            "sin fix no hay distancia: null es la respuesta, nunca un 0",
        )
    }

    @Test
    fun should_measure_the_spot_from_where_the_user_is() {
        val notables = nearbyNotables(
            spots = listOf(spot("s", 40.0009)),
            parkings = emptyList(),
            fuel = emptyList(),
            from = here,
        )

        val metres = notables.single().distanceMeters
        assertTrue(
            metres != null && metres > 80.0 && metres < 120.0,
            "0,0009° de latitud son ~100 m; salieron $metres",
        )
    }
}
