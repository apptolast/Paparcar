package com.rndeveloper.paparcar.data.repository

import com.rndeveloper.paparcar.data.datasource.local.room.UserParkingEntity
import com.rndeveloper.paparcar.data.datasource.remote.RemoteUserProfileDataSource
import com.rndeveloper.paparcar.data.datasource.remote.dto.AddressDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.ParkingHistoryDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.PlaceInfoDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.UserProfileDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.VehicleDto
import com.rndeveloper.paparcar.fakes.FakeUserParkingDao
import com.rndeveloper.paparcar.fakes.data.repository.FakeParkingSyncScheduler
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Qué sale hacia Firestore cuando una sesión cambia de estado de cierre.
 * [SYNC-A-CLOSED-SESSION-MUST-REACH-FIRESTORE-WHOLE-001]
 *
 * ⛔ EL TESTIGO. El espejo remoto de una sesión cerrada decía «cerrada» **sin decir cuándo**: el
 * parche rápido sólo escribe `isActive`, y el único push de documento completo que existía lo
 * dispara `AppViewModel` al arrancar online. Un cierre en background —el caso normal— no llegaba
 * entero hasta que el usuario volvía a abrir la app. Medido en Firestore el 22-09-2026: un documento
 * con `isActive:false`, `endedAtMs:null` y el `updatedAt` del alta, tres días después.
 */
class UserParkingRepositoryImplTest {

    private val dao = FakeUserParkingDao()
    private val scheduler = FakeParkingSyncScheduler()
    private val repository = UserParkingRepositoryImpl(
        dao = dao,
        userProfileDataSource = UntouchedRemote(),
        parkingSyncScheduler = scheduler,
    )

    private val parked = UserParkingEntity(
        id = "s-1",
        userId = "u-1",
        vehicleId = "v-1",
        latitude = 36.618,
        longitude = -6.274,
        accuracy = 10f,
        timestamp = 1_000L,
        isActive = true,
    )

    @Test
    fun should_pushTheWholeSession_when_aSessionIsClosed() = runTest {
        dao.seed(parked)

        repository.clearActiveParkingSession(sessionId = "s-1", endedAtMs = 9_000L, publishedSpot = true)

        val pushed = scheduler.snapshots.single()
        assertEquals("s-1", pushed.id)
        assertEquals(9_000L, pushed.endedAtMs, "la hora de cierre viaja: es lo que el espejo no tenía")
        assertTrue(pushed.publishedSpot, "y si liberó plaza, también")
        assertTrue(pushed.isActive.not())
    }

    @Test
    fun should_pushTheStateAfterTheWrite_notTheOneTheCallerHeld() = runTest {
        // El snapshot se relee de la fila YA escrita. Si se cogiera el objeto que el llamante tenía
        // en la mano, subiría la sesión ABIERTA y el espejo se quedaría peor que antes.
        dao.seed(parked)

        repository.clearActiveParkingSession(sessionId = "s-1", endedAtMs = 9_000L, publishedSpot = false)

        assertEquals(dao.row("s-1")?.endedAtMs, scheduler.snapshots.single().endedAtMs)
        assertEquals(dao.row("s-1")?.isActive, scheduler.snapshots.single().isActive)
    }

    @Test
    fun should_pushTheWholeSession_when_aRefutedDepartureReopensIt() = runTest {
        // [DET-A-TRAIN-IS-NOT-A-CAR-001] La reapertura NO puede encolar el parche del flag —lo
        // desharía—, así que el push completo es su único camino al espejo. Antes no tenía ninguno.
        dao.seed(parked.copy(isActive = false, endedAtMs = 9_000L))

        repository.restoreParkingSession("s-1")

        val pushed = scheduler.snapshots.single()
        assertTrue(pushed.isActive, "vuelve a estar aparcado")
        assertNull(pushed.endedAtMs, "y sin hora de cierre, porque la salida quedó refutada")
    }

    @Test
    fun should_pushTheWholeSession_when_aPinIsRetracted() = runTest {
        dao.seed(parked)

        repository.retractParkingSession("s-1", retractedAtMs = 12_000L)

        assertEquals(12_000L, scheduler.snapshots.single().retractedAtMs)
    }

    @Test
    fun should_pushNothing_when_theRowIsAlreadyGone() = runTest {
        // Una fila que ya no existe no es un error: no hay nada que espejar.
        repository.clearActiveParkingSession(sessionId = "desaparecida", endedAtMs = 1L, publishedSpot = false)

        assertTrue(scheduler.snapshots.isEmpty())
    }

    /** El cierre no habla con la red: si este doble se despierta, el repositorio hizo I/O de más. */
    private class UntouchedRemote : RemoteUserProfileDataSource {
        private fun unexpected(): Nothing = error("el cierre no debe tocar la red directamente")
        override suspend fun getProfile(userId: String): UserProfileDto? = unexpected()
        override suspend fun createOrUpdateProfile(profile: UserProfileDto) = unexpected()
        override suspend fun updateIdentityFields(
            userId: String, email: String?, displayName: String?, photoUrl: String?, updatedAt: Long,
        ) = unexpected()
        override suspend fun updateDefaultVehicleId(userId: String, vehicleId: String?) = unexpected()
        override suspend fun updateFirstStepsDismissedAt(userId: String, dismissedAtMs: Long?) = unexpected()
        override suspend fun updateDetectionState(
            userId: String, autoDetectParking: Boolean, detectionBlockers: List<String>,
            batteryOptimizationExempt: Boolean, platform: String, appVersion: String, updatedAtMs: Long,
        ) = unexpected()
        override suspend fun saveParkingSession(userId: String, session: ParkingHistoryDto) = unexpected()
        override suspend fun clearParkingSessionActiveFlag(userId: String, sessionId: String) = unexpected()
        override suspend fun updateParkingSessionAddressAndPlace(
            userId: String, sessionId: String, address: AddressDto?, placeInfo: PlaceInfoDto?,
        ) = unexpected()
        override suspend fun getParkingHistory(userId: String): List<ParkingHistoryDto> = unexpected()
        override suspend fun deleteParkingSessionsForVehicle(userId: String, vehicleId: String) = unexpected()
        override suspend fun getVehicles(userId: String): List<VehicleDto> = unexpected()
        override suspend fun saveVehicle(userId: String, vehicle: VehicleDto) = unexpected()
        override suspend fun deleteVehicle(userId: String, vehicleId: String) = unexpected()
        override suspend fun updateVehicleActiveFlag(userId: String, vehicleId: String, isActive: Boolean) = unexpected()
        override suspend fun updateVehicleBluetoothDevice(userId: String, vehicleId: String, deviceAddress: String?) = unexpected()
        override suspend fun deleteUserData(userId: String) = unexpected()
    }
}
