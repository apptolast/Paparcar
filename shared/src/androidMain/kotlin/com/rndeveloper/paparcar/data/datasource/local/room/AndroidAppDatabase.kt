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
 * fallback Room refuses to open the file at all.
 *
 * @param name overridable so tests can seed their own file instead of the shared production one.
 */
fun buildAppDatabase(context: Context, name: String = PAPARCAR_DB_NAME): AppDatabase =
    Room.databaseBuilder(context, AppDatabase::class.java, name)
        // [DB-A-NEW-COLUMN-NEEDS-ITS-MIGRATION-001] Registered migrations WIN over the destructive
        // fallback below — that precedence is what lets both lines coexist: known upgrades migrate
        // (bench data survives), while the unknown pre-release downgrades the fallback exists for
        // (v20→v1) still wipe instead of refusing to open.
        .addMigrations(*ALL_MIGRATIONS)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
