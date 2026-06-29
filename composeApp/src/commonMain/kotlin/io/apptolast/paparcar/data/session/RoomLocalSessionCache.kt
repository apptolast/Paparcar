package io.apptolast.paparcar.data.session

import io.apptolast.paparcar.data.datasource.local.room.AppDatabase
import io.apptolast.paparcar.domain.session.LocalSessionCache

class RoomLocalSessionCache(
    private val database: AppDatabase,
) : LocalSessionCache {
    /**
     * Clears every table that belongs to the active user. Room KMP exposes no
     * common `clearAllTables()` (it is Android-only), so we delete each table
     * through its DAO instead. The `suspend` DAO calls are dispatched onto
     * Room's own executor, so no manual `Dispatchers.IO` hop is required.
     * [SESSION-ISOLATION-001]
     */
    override suspend fun wipe() {
        database.parkingSessionDao().deleteAll()
        database.spotDao().deleteAll()
        database.vehicleDao().deleteAll()
        database.userProfileDao().deleteAll()
        database.zoneDao().deleteAll()
        database.geocoderCacheDao().deleteAll()
    }
}
