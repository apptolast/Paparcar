package io.apptolast.paparcar.data.datasource.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ZoneDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(zone: ZoneEntity)

    @Query("SELECT * FROM zones WHERE userId = :userId ORDER BY createdAt ASC")
    fun observeByUser(userId: String): Flow<List<ZoneEntity>>

    @Query("SELECT * FROM zones WHERE id = :id AND userId = :userId LIMIT 1")
    suspend fun getById(id: String, userId: String): ZoneEntity?

    /** One-shot read of all the user's zones — used by [ZoneRepository.syncFromRemote] to merge. */
    @Query("SELECT * FROM zones WHERE userId = :userId")
    suspend fun getByUser(userId: String): List<ZoneEntity>

    @Query("DELETE FROM zones WHERE id = :id AND userId = :userId")
    suspend fun deleteById(id: String, userId: String)

    /** Marks a row as locally-mutated-not-yet-synced so the inbound sync won't clobber it. [SYNC-RECONCILE-001] */
    @Query("UPDATE zones SET pendingSync = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun markPending(id: String, updatedAt: Long)

    /** Clears the pending flag once the remote write is confirmed (server ack). [SYNC-RECONCILE-001] */
    @Query("UPDATE zones SET pendingSync = 0 WHERE id = :id")
    suspend fun clearPending(id: String)

    /** The outbound outbox: rows with a local edit not yet confirmed by Firestore. [SYNC-RECONCILE-001] */
    @Query("SELECT * FROM zones WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<ZoneEntity>

    /** REPLACE-conflict bulk insert used by [ZoneRepository.syncFromRemote]. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(zones: List<ZoneEntity>)

    @Query("DELETE FROM zones WHERE userId = :userId")
    suspend fun deleteByUser(userId: String)

    /** [AUDIT-DATA-001 M5] Atomic replace — see [VehicleDao.replaceAllForUser]: the sync merge's
     *  delete-then-insert pair left the zones table momentarily empty on a process death. */
    @Transaction
    suspend fun replaceAllForUser(userId: String, zones: List<ZoneEntity>) {
        deleteByUser(userId)
        upsertAll(zones)
    }

    /** Unconditional wipe of every row. Used by [LocalSessionCache.wipe] on sign-out. */
    @Query("DELETE FROM zones")
    suspend fun deleteAll()
}
