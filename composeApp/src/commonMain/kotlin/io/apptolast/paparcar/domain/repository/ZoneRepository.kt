package io.apptolast.paparcar.domain.repository

import io.apptolast.paparcar.domain.model.Zone
import kotlinx.coroutines.flow.Flow

/**
 * Persistence + sync contract for the user's [Zone] list. Mirrors the
 * VehicleRepository pattern: auth-reactive observe + idempotent
 * syncFromRemote + suspending CRUD that updates Room then Firestore.
 */
interface ZoneRepository : UserScopedRepository, RemoteSyncable {
    /** Stream the current user's zones, ordered by createdAt asc (oldest first). */
    fun observeZones(): Flow<List<Zone>>

    /** Idempotent one-shot pull of remote zones into Room. Called during splash bootstrap. */
    override suspend fun syncFromRemote(userId: String): Result<Unit>

    /** Insert or update a zone — writes to Room then Firestore. */
    suspend fun saveZone(zone: Zone): Result<Unit>

    /** Remove a zone by id — Room first then Firestore. */
    suspend fun deleteZone(id: String): Result<Unit>

    /** One-shot read of the current user's private zones. Used by [ConfirmParkingUseCase] to check geofence membership. */
    suspend fun getPrivateZonesSnapshot(): List<Zone>

    /** Deletes all local and remote zones for [userId]. Called during account deletion. */
    override suspend fun deleteAllData(userId: String): Result<Unit>

    /**
     * Drains the outbound outbox: pushes every locally-mutated-but-unconfirmed (pendingSync) zone to
     * Firestore and clears the flag on ack. Idempotent. Called on app start and on connectivity
     * restored so an offline edit reliably reaches the cloud (and other devices). [SYNC-RECONCILE-001]
     */
    suspend fun pushPendingZones(): Result<Unit>
}
