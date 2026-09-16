package com.rndeveloper.paparcar.domain.usecase.vehicle

import com.rndeveloper.paparcar.domain.error.PaparcarError
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.VehicleParkingFootprint
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.fakes.FakeAppPreferences
import com.rndeveloper.paparcar.fakes.FakeVehicleRepository
import com.rndeveloper.paparcar.domain.repository.SharedCarRepository
import com.rndeveloper.paparcar.fakes.data.repository.FakeSharedCarRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Borrar un coche que pertenece a un grupo. [PRIV-A-DELETED-CAR-MUST-NOT-ORPHAN-ITS-GROUP-001]
 *
 * Lo que se prueba aquí no es que la fila desaparezca —eso ya lo probaba el repositorio— sino que
 * **no quede un grupo detrás**, que es lo que pasaba antes: `deleteVehicle` no tocaba `sharedCars`
 * en ningún punto y el único sitio que llamaba a `stopSharing` era el botón de la ficha del coche,
 * la ficha que acababa de desaparecer.
 */
class DeleteVehicleUseCaseTest {

    private val ownCar = Vehicle(
        id = "car-1",
        userId = "marta",
        name = "Kamiq",
        sizeCategory = VehicleSize.MEDIUM_SUV,
    )

    private fun useCase(
        vehicles: FakeVehicleRepository,
        shared: SharedCarRepositorySpy,
        prefs: FakeAppPreferences = FakeAppPreferences(),
    ) = DeleteVehicleUseCase(
        vehicleRepository = vehicles,
        sharedCarRepository = shared,
        appPreferences = prefs,
    )

    @Test
    fun should_dissolveTheGroupForEveryone_when_theDeletedCarIsMineAndShared() = runTest {
        val shared = SharedCarRepositorySpy()
        val car = ownCar.copy(sharedCarId = "car-1")
        val vehicles = FakeVehicleRepository(defaultVehicle = car)

        useCase(vehicles, shared)(car).getOrThrow()

        // Mismo verbo que usa el borrado de cuenta: el espejo es una proyección de SU coche.
        assertEquals(listOf("stopSharing:car-1"), shared.calls)
        assertNull(vehicles.getVehicleById("marta", "car-1"))
    }

    @Test
    fun should_walkOutOfTheGroup_when_theDeletedCarWasLentToMe() = runTest {
        val shared = SharedCarRepositorySpy()
        val lent = ownCar.copy(userId = "nacho", ownerUid = "marta", sharedCarId = "car-1")
        val vehicles = FakeVehicleRepository(defaultVehicle = lent)

        useCase(vehicles, shared)(lent).getOrThrow()

        // No es suyo para disolverlo: sale él, con sus sesiones, su nombre y sus tokens.
        assertEquals(listOf("leave:car-1"), shared.calls)
    }

    @Test
    fun should_notTouchAnyGroup_when_theCarWasNeverShared() = runTest {
        val shared = SharedCarRepositorySpy()
        val vehicles = FakeVehicleRepository(defaultVehicle = ownCar)

        useCase(vehicles, shared)(ownCar).getOrThrow()

        assertEquals(emptyList(), shared.calls)
    }

    @Test
    fun should_refuseBeforeTouchingTheGroup_when_theCarIsParked() = runTest {
        val shared = SharedCarRepositorySpy()
        val car = ownCar.copy(sharedCarId = "car-1")
        val vehicles = FakeVehicleRepository(defaultVehicle = car).apply {
            parkingFootprints["car-1"] = VehicleParkingFootprint(endedParkings = 3, hasActiveParking = true)
        }

        val error = useCase(vehicles, shared)(car).exceptionOrNull()

        assertEquals(PaparcarError.Vehicle.DeleteBlockedByActiveParking, error)
        // ⛔ Lo que importa no es el error, es que el grupo siga EN PIE: comprobar después habría
        // disuelto un grupo de cuatro personas para luego negarse a borrar el coche.
        assertEquals(emptyList(), shared.calls)
        assertTrue(vehicles.getVehicleById("marta", "car-1") != null)
    }

    @Test
    fun should_forgetThePendingQuestion_when_theOrphanedCarIsDeleted() = runTest {
        val shared = SharedCarRepositorySpy()
        val vehicles = FakeVehicleRepository(defaultVehicle = ownCar)
        val prefs = FakeAppPreferences().apply { addDissolvedSharedCar("car-1") }

        useCase(vehicles, shared, prefs)(ownCar).getOrThrow()

        assertEquals(emptySet(), prefs.dissolvedSharedCars.value)
    }
}

/**
 * Anota QUÉ puerta se usó, que es justo lo que el caso de uso decide; el resto del contrato lo
 * delega en el fake de siempre, que es final a propósito.
 */
private class SharedCarRepositorySpy(
    delegate: FakeSharedCarRepository = FakeSharedCarRepository(),
) : SharedCarRepository by delegate {
    val calls = mutableListOf<String>()
    override suspend fun stopSharing(carId: String): Result<Unit> {
        calls += "stopSharing:$carId"
        return Result.success(Unit)
    }

    override suspend fun leave(carId: String): Result<Unit> {
        calls += "leave:$carId"
        return Result.success(Unit)
    }
}
