package io.apptolast.paparcar.data.session

import io.apptolast.paparcar.data.datasource.local.room.AppDatabase
import io.apptolast.paparcar.domain.session.LocalSessionCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RoomLocalSessionCache(
    private val database: AppDatabase,
) : LocalSessionCache {
    /**
     * `RoomDatabase.clearAllTables()` is a blocking method (not `suspend`) and Room asserts
     * it is NOT called on the Main thread. The enclosing `wipe()` is suspending but that
     * alone doesn't switch dispatchers — `viewModelScope.launch` runs on `Main.immediate`,
     * so we have to hop to IO explicitly. Without this, `clearAllTables()` throws
     * `IllegalStateException` and the wipe silently fails. [SESSION-ISOLATION-001]
     */
    override suspend fun wipe() = withContext(Dispatchers.IO) {
        database.clearAllTables()
    }
}
