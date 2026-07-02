package io.apptolast.paparcar.data.datasource.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vehicle: VehicleEntity)

    @Query("SELECT * FROM vehicles WHERE userId = :userId ORDER BY isActive DESC")
    fun observeByUser(userId: String): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles WHERE userId = :userId AND isActive = 1 LIMIT 1")
    fun observeActive(userId: String): Flow<VehicleEntity?>

    @Query("SELECT * FROM vehicles WHERE userId = :userId AND isActive = 1 LIMIT 1")
    suspend fun getActive(userId: String): VehicleEntity?

    @Query("SELECT * FROM vehicles WHERE id = :id AND userId = :userId LIMIT 1")
    suspend fun getById(id: String, userId: String): VehicleEntity?

    @Query("SELECT * FROM vehicles WHERE userId = :userId AND bluetoothDeviceId = :address LIMIT 1")
    suspend fun getByBluetoothDevice(userId: String, address: String): VehicleEntity?

    @Query("SELECT * FROM vehicles WHERE userId = :userId ORDER BY isActive DESC")
    suspend fun getByUser(userId: String): List<VehicleEntity>

    @Query("DELETE FROM vehicles WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE vehicles SET isActive = 0 WHERE userId = :userId")
    suspend fun clearActive(userId: String)

    @Query("UPDATE vehicles SET isActive = 1 WHERE id = :id")
    suspend fun setActive(id: String)

    @Query("UPDATE vehicles SET bluetoothDeviceId = :address WHERE id = :vehicleId")
    suspend fun updateBluetoothDevice(vehicleId: String, address: String?)

    @Query("SELECT COUNT(*) FROM vehicles WHERE userId = :userId")
    suspend fun countByUser(userId: String): Int

    /** Marks a row as locally-mutated-not-yet-synced so the inbound sync won't clobber it. [SYNC-RECONCILE-001] */
    @Query("UPDATE vehicles SET pendingSync = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun markPending(id: String, updatedAt: Long)

    /** Clears the pending flag once the remote write is confirmed (server ack). [SYNC-RECONCILE-001] */
    @Query("UPDATE vehicles SET pendingSync = 0 WHERE id = :id")
    suspend fun clearPending(id: String)

    /** The outbound outbox: rows with a local edit not yet confirmed by Firestore. [SYNC-RECONCILE-001] */
    @Query("SELECT * FROM vehicles WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<VehicleEntity>

    /**
     * REPLACE-conflict bulk insert used by [VehicleRepository.syncFromRemote]. Required so a
     * change to `isActive` on another device actually lands in this Room when re-synced —
     * the previous IGNORE policy left local rows frozen at their first-sync state. Callers
     * MUST merge any local-only fields (currently [VehicleEntity.bluetoothDeviceId]) into
     * the entities they pass here before invoking, or those values will be wiped. [VEHICLES-001]
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(vehicles: List<VehicleEntity>)

    @Query("DELETE FROM vehicles WHERE userId = :userId")
    suspend fun deleteByUser(userId: String)

    /** Unconditional wipe of every row. Used by [LocalSessionCache.wipe] on sign-out. */
    @Query("DELETE FROM vehicles")
    suspend fun deleteAll()
}
