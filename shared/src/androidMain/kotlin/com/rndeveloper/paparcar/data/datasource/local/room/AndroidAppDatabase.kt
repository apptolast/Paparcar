package com.rndeveloper.paparcar.data.datasource.local.room

import android.content.Context
import androidx.room.Room
import com.rndeveloper.paparcar.domain.util.PaparcarLogger

/** File name of the local database in the app's `databases/` directory. */
const val PAPARCAR_DB_NAME: String = "paparcar.db"

/**
 * The single place that says HOW the local database is opened on Android.
 *
 * It exists so the destructive fallback has exactly one definition. Inlined in the Koin module it
 * was untestable by construction: a test would have to restate the builder, and a restated builder
 * passes happily on the day someone drops `fallbackToDestructiveMigration` from the real one.
 * [AppDatabaseDowngradeTest] drives this function, so the witness and production cannot drift.
 *
 * The fallback is not decoration. [AppDatabase] went from `version = 20` to `version = 1` in
 * [DATA-ROOM-STARTS-AT-VERSION-ONE-001] because Paparcar has never shipped, so every pre-release
 * install — our own field-test phones — holds a file whose version is *higher* than the one the app
 * now asks for. That is a downgrade, a path this project had never exercised, and without the
 * fallback Room refuses to open the file at all. [DATA-ROOM-RETURNS-TO-VERSION-ONE-001] made that
 * true a second time by collapsing v2 back to v1 on the release wipe.
 *
 * @param name overridable so tests can seed their own file instead of the shared production one.
 */
fun buildAppDatabase(context: Context, name: String = PAPARCAR_DB_NAME): AppDatabase {
    val first = newAppDatabase(context, name)
    if (first.opensCleanly()) return first

    // The file is unopenable and Room will not fix it: this is the same-version-different-hash shape
    // the fallback cannot see. Delete it and start over — the local cache is a CACHE, and the truth
    // it holds is re-fetched from Firestore on the next bootstrap.
    // [DATA-A-DB-IT-CANNOT-OPEN-MUST-NOT-DEAD-END-THE-USER-001]
    PaparcarLogger.e(TAG, "local database refused to open — deleting it and starting from empty")
    runCatching { first.close() }
    context.deleteDatabase(name)  // takes the -wal/-shm/-journal siblings with it
    // Deliberately NOT probed again: a fresh file that still refuses is a genuine failure, and it
    // should surface where it always did (the first query) instead of turning into a Koin graph
    // error that reads like something else entirely.
    return newAppDatabase(context, name)
}

private fun newAppDatabase(context: Context, name: String): AppDatabase =
    Room.databaseBuilder(context, AppDatabase::class.java, name)
        // [DB-A-NEW-COLUMN-NEEDS-ITS-MIGRATION-001] Registered migrations WIN over the destructive
        // fallback below — that precedence is what lets both lines coexist: known upgrades migrate
        // (user data survives), while the unknown pre-release downgrades the fallback exists for
        // still wipe instead of refusing to open. [ALL_MIGRATIONS] is empty today; the call stays
        // so the first migration after launch lands here and not somewhere newly invented.
        .addMigrations(*ALL_MIGRATIONS)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

/**
 * Opens the file NOW instead of at the first query, so a refusal is met here — where it can be
 * repaired — rather than inside whichever coroutine happened to ask first.
 *
 * `build()` does not touch the disk; `writableDatabase` runs Room's `onOpen`, which is where the
 * identity check lives and throws. That eagerness is the cost of this fix: the open moves to
 * whenever Koin first resolves the database (a few ms of disk), instead of the first DAO call.
 *
 * Catches [Throwable], not [Exception]: the integrity refusal arrives as an [IllegalStateException]
 * today, but the point is "the file did not open", and narrowing it would let the next Room version
 * reintroduce the dead end through a type we did not predict.
 */
private fun AppDatabase.opensCleanly(): Boolean =
    runCatching { openHelper.writableDatabase }
        .onFailure { e -> PaparcarLogger.e(TAG, "database open probe failed", e) }
        .isSuccess

private const val TAG = "AppDatabase"
