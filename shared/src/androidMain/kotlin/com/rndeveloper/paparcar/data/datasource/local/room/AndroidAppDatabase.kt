package com.rndeveloper.paparcar.data.datasource.local.room

import android.content.Context
import androidx.room.Room

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
fun buildAppDatabase(context: Context, name: String = PAPARCAR_DB_NAME): AppDatabase =
    Room.databaseBuilder(context, AppDatabase::class.java, name)
        // [DB-A-NEW-COLUMN-NEEDS-ITS-MIGRATION-001] Registered migrations WIN over the destructive
        // fallback below — that precedence is what lets both lines coexist: known upgrades migrate
        // (user data survives), while the unknown pre-release downgrades the fallback exists for
        // still wipe instead of refusing to open. [ALL_MIGRATIONS] is empty today; the call stays
        // so the first migration after launch lands here and not somewhere newly invented.
        .addMigrations(*ALL_MIGRATIONS)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
