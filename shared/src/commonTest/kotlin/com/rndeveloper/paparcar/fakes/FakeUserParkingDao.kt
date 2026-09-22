package com.rndeveloper.paparcar.fakes

import com.rndeveloper.paparcar.data.datasource.local.room.UserParkingDao
import com.rndeveloper.paparcar.data.datasource.local.room.UserParkingEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Doble en memoria de [UserParkingDao] — **la tabla, no la base de datos**.
 * [SYNC-A-CLOSED-SESSION-MUST-REACH-FIRESTORE-WHOLE-001]
 *
 * `UserParkingRepositoryImpl` no tenía un solo test, y el motivo es este: su única dependencia
 * interesante es un `@Dao` de Room. Pero es una **interfaz** (con cuerpos por defecto para los
 * `@Transaction`), así que se puede implementar a mano, y con eso el repositorio pasa a ser
 * testeable sin Robolectric ni SQLite.
 *
 * ⛔ Las consultas se reimplementan aquí en Kotlin, y eso es una copia del SQL con todos sus
 * riesgos: **lo que este doble prueba es el comportamiento del REPOSITORIO**, no el de las
 * consultas. Quien tiene que probar el SQL es un test de migración con el driver de Android, que es
 * como se prueban en este repo (`Migration19To20Test`). Las que no hace falta reproducir para eso
 * lanzan [NotImplementedError] a propósito: un doble que devuelve vacío calladamente es cómo un
 * test pasa sin ejercitar nada.
 */
class FakeUserParkingDao : UserParkingDao {

    private val rows = MutableStateFlow<Map<String, UserParkingEntity>>(emptyMap())

    /** Siembra directa, para montar el estado de partida de un test. */
    fun seed(vararg entities: UserParkingEntity) {
        rows.value = rows.value + entities.associateBy { it.id }
    }

    fun row(id: String): UserParkingEntity? = rows.value[id]

    private fun put(entity: UserParkingEntity) {
        rows.value = rows.value + (entity.id to entity)
    }

    override suspend fun insert(session: UserParkingEntity) = put(session)

    override suspend fun upsertAll(sessions: List<UserParkingEntity>) {
        rows.value = rows.value + sessions.associateBy { it.id }
    }

    override suspend fun getById(id: String): UserParkingEntity? = rows.value[id]

    override suspend fun getPendingSync(): List<UserParkingEntity> = rows.value.values.filter { it.pendingSync }

    override suspend fun clearPending(id: String) {
        rows.value[id]?.let { put(it.copy(pendingSync = false)) }
    }

    // `COALESCE(endedAtMs, :endedAtMs)`: el PRIMER cierre es el momento real de la salida, así que
    // un segundo cierre no lo reescribe. Reproducido porque es justo lo que el testigo mira.
    override suspend fun clearActiveById(sessionId: String, endedAtMs: Long, publishedSpot: Boolean, now: Long) {
        rows.value[sessionId]?.let {
            put(
                it.copy(
                    isActive = false,
                    endedAtMs = it.endedAtMs ?: endedAtMs,
                    publishedSpot = publishedSpot,
                    updatedAt = now,
                    pendingSync = true,
                ),
            )
        }
    }

    override suspend fun retractById(sessionId: String, retractedAtMs: Long, now: Long) {
        rows.value[sessionId]?.let {
            put(
                it.copy(
                    isActive = false,
                    retractedAtMs = retractedAtMs,
                    endedAtMs = it.endedAtMs ?: retractedAtMs,
                    updatedAt = now,
                    pendingSync = true,
                ),
            )
        }
    }

    override suspend fun reopenById(sessionId: String, now: Long) {
        rows.value[sessionId]?.let {
            put(
                it.copy(
                    isActive = true,
                    endedAtMs = null,
                    retractedAtMs = null,
                    updatedAt = now,
                    pendingSync = true,
                ),
            )
        }
    }

    override fun observeAll(): Flow<List<UserParkingEntity>> = rows.map { it.values.toList() }

    override fun observeActive(): Flow<List<UserParkingEntity>> =
        rows.map { all -> all.values.filter { it.isActive } }

    override suspend fun getAllActive(): List<UserParkingEntity> = rows.value.values.filter { it.isActive }

    // ── Lo que este doble NO reproduce ──────────────────────────────────────────────────────────

    override suspend fun getByUser(userId: String): List<UserParkingEntity> = notReproduced("getByUser")
    override suspend fun getActiveByGeofence(geofenceId: String): UserParkingEntity? = notReproduced("getActiveByGeofence")
    override suspend fun getActiveByVehicle(vehicleId: String): UserParkingEntity? = notReproduced("getActiveByVehicle")
    override suspend fun getSessionsPaged(limit: Int, offset: Int): List<UserParkingEntity> = notReproduced("getSessionsPaged")
    override suspend fun getEndedSessionsByVehiclePaged(vehicleId: String, limit: Int, offset: Int): List<UserParkingEntity> =
        notReproduced("getEndedSessionsByVehiclePaged")
    override fun observeByVehicle(vehicleId: String): Flow<List<UserParkingEntity>> = notReproduced("observeByVehicle")
    override suspend fun setProvisionalDeparture(sessionId: String, atMs: Long?, now: Long) = notReproduced("setProvisionalDeparture")
    override suspend fun clearActiveByVehicle(vehicleId: String, endedAtMs: Long, now: Long) = notReproduced("clearActiveByVehicle")
    override suspend fun clearActiveOrphans(endedAtMs: Long, now: Long) = notReproduced("clearActiveOrphans")
    override suspend fun getActiveDuplicates(): List<UserParkingEntity> = notReproduced("getActiveDuplicates")
    override suspend fun deleteByUser(userId: String) = notReproduced("deleteByUser")
    override suspend fun deleteByVehicle(vehicleId: String) = notReproduced("deleteByVehicle")
    override suspend fun countEndedByVehicle(vehicleId: String): Int = notReproduced("countEndedByVehicle")
    override suspend fun deleteAll() = notReproduced("deleteAll")
    override suspend fun getPreviousByVehicle(vehicleId: String, beforeTimestamp: Long): UserParkingEntity? =
        notReproduced("getPreviousByVehicle")
    override suspend fun updateRouteResolution(id: String, resolution: String?, now: Long) = notReproduced("updateRouteResolution")

    override suspend fun updateAddressAndPlace(
        id: String,
        street: String?,
        city: String?,
        region: String?,
        country: String?,
        countryCode: String?,
        province: String?,
        postalCode: String?,
        placeInfoName: String?,
        placeInfoCategory: String?,
        now: Long,
    ) = notReproduced("updateAddressAndPlace")

    override suspend fun updateLocation(
        id: String,
        lat: Double,
        lon: Double,
        accuracy: Float,
        detectionPath: String,
        detectionReliability: Float,
        now: Long,
    ) = notReproduced("updateLocation")

    override suspend fun updateRoute(
        id: String,
        routePolyline: String?,
        routeSnapped: Boolean,
        routeInferredSpans: String?,
        routeDistanceMeters: Float?,
        now: Long,
    ) = notReproduced("updateRoute")

    private fun notReproduced(name: String): Nothing =
        throw NotImplementedError("FakeUserParkingDao no reproduce `$name` — añádelo si tu test lo necesita de verdad")
}
