package io.apptolast.paparcar.domain.repository

import io.apptolast.paparcar.domain.model.Zone
import kotlinx.coroutines.flow.Flow

/**
 * Persistence + sync contract for the user's [Zone] list. Mirrors the
 * VehicleRepository pattern: auth-reactive observe + idempotent
 * syncFromRemote + suspending CRUD that updates Room then Firestore.
 */
interface ZoneRepository {
    /** Stream the current user's zones, ordered by createdAt asc (oldest first). */
    fun observeZones(): Flow<List<Zone>>

    /** Idempotent one-shot pull of remote zones into Room. Called during splash bootstrap. */
    suspend fun syncFromRemote(userId: String): Result<Unit>

    /** Insert or update a zone — writes to Room then Firestore. */
    suspend fun saveZone(zone: Zone)

    /** Remove a zone by id — Room first then Firestore. */
    suspend fun deleteZone(id: String)

    /** One-shot read of the current user's private zones. Used by [ConfirmParkingUseCase] to check geofence membership. */
    suspend fun getPrivateZonesSnapshot(): List<Zone>

    /** Deletes all local and remote zones for [userId]. Called during account deletion. */
    suspend fun deleteAllData(userId: String): Result<Unit>
}
