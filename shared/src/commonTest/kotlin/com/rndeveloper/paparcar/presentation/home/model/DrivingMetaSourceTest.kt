package com.rndeveloper.paparcar.presentation.home.model

import com.rndeveloper.paparcar.domain.detection.DetectionPhase
import com.rndeveloper.paparcar.domain.detection.ParkingStrategy
import com.rndeveloper.paparcar.domain.model.DetectionReadiness
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.presentation.home.DrivingMeta
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [HOME-A-TAP-THAT-ARMS-MUST-LOOK-ARMED-001] El viaje de la UI lo declara la DETECCIÓN, no el GPS.
 */
class DrivingMetaSourceTest {

    private val active = Vehicle(
        id = "veh-active",
        userId = "u1",
        brand = "Seat",
        model = "León",
        sizeCategory = VehicleSize.MEDIUM_SUV,
        isActive = true,
    )
    private val paired = Vehicle(
        id = "veh-bt",
        userId = "u1",
        brand = "Škoda",
        model = "Kamiq",
        sizeCategory = VehicleSize.MEDIUM_SUV,
        bluetoothDeviceId = "AA:BB",
    )

    private fun monitoring(
        departing: String? = null,
        phase: DetectionPhase = DetectionPhase.Driving,
        strategy: ParkingStrategy = ParkingStrategy.COORDINATOR,
    ) = DetectionReadiness.Monitoring(
        strategy = strategy,
        phase = phase,
        departingVehicleId = departing,
    )

    @Test
    fun should_tell_the_trip_the_instant_detection_arms_even_without_a_single_fix() {
        // ⛔ EL TESTIGO del reporte de campo: «le daba a *Voy conduciendo* y no hacía nada». La
        // detección se armaba; lo que no cambiaba era la pantalla, porque el viaje salía del PUCK y
        // el puck necesita un fix. Sin fix (puck = null) la card volvía a decir «Sin marcar»: lo
        // mismo que decía ANTES de tocar el botón, sólo que ya sin los botones.
        assertEquals(
            DrivingMeta(vehicleId = "veh-active", phase = DetectionPhase.Driving),
            resolveDrivingMeta(puck = null, readiness = monitoring(), vehicles = listOf(active)),
        )
    }

    @Test
    fun should_name_the_car_the_service_said_departed_over_the_active_one() {
        // El nominado gana: el servicio lo resolvió desde la sesión de la geocerca, y es el único
        // que sabe cuál salió cuando el activo es otro.
        assertEquals(
            "veh-bt",
            resolveDrivingMeta(null, monitoring(departing = "veh-bt"), listOf(active, paired))?.vehicleId,
        )
    }

    @Test
    fun should_keep_the_pucks_word_when_there_is_one() {
        // El puck trae la fase CONGELADA del `Candidate` — la que impide que el pin persiga al
        // peatón — así que cuando existe manda él, aunque la disponibilidad diga otra fase.
        val puck = DrivingMeta(vehicleId = "veh-bt", phase = DetectionPhase.Candidate)
        assertEquals(
            puck,
            resolveDrivingMeta(puck, monitoring(phase = DetectionPhase.Driving), listOf(active, paired)),
        )
    }

    @Test
    fun should_say_there_is_no_trip_when_detection_is_not_monitoring_one() {
        // Y la mitad que impide el fallo opuesto: un arme que no cuaja se cae solo, y esta línea se
        // apaga con él. Si esto devolviera un viaje, la card se quedaría «En ruta» para siempre.
        assertNull(
            resolveDrivingMeta(null, DetectionReadiness.Ready(ParkingStrategy.COORDINATOR), listOf(active)),
        )
    }

    @Test
    fun should_say_there_is_no_trip_when_there_is_no_car_to_attribute_it_to() {
        // Sin coche no hay a quién atribuirlo, y un viaje sin coche no se puede dibujar en ninguna
        // card. Vale la pena el testigo: la flota puede llegar DESPUÉS del arme.
        assertNull(resolveDrivingMeta(null, monitoring(), vehicles = emptyList()))
    }

    @Test
    fun should_follow_the_bluetooth_car_when_that_is_the_lane_watching() {
        // El carril BT vigila al coche emparejado, sea o no el primario: si esta elección se
        // separara de la del puck, el mapa dibujaría un coche y la hoja nombraría otro.
        assertEquals(
            "veh-bt",
            resolveDrivingMeta(
                null,
                monitoring(strategy = ParkingStrategy.BLUETOOTH),
                listOf(active, paired),
            )?.vehicleId,
        )
    }
}
