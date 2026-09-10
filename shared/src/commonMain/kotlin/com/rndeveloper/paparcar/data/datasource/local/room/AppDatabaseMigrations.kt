package com.rndeveloper.paparcar.data.datasource.local.room

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * The migration chain — the one obvious place every schema change lands, kept alive through the
 * v1 collapse of [DATA-ROOM-RETURNS-TO-VERSION-ONE-001] for exactly this moment. The plumbing was
 * bought with a real field failure — see [DB-A-NEW-COLUMN-NEEDS-ITS-MIGRATION-001], where a column
 * added without a version bump left the safety net unable to read sessions with a parked car being
 * watched.
 *
 * ⛔ One list, both platforms. Every builder also carries
 * `fallbackToDestructiveMigration(dropAllTables = true)`, so a version bump whose migration is
 * missing from a builder does not crash there — it silently WIPES that device. A single shared list
 * is what keeps that from ever being a per-platform accident.
 */

/**
 * v1 → v2: `zones.notifyOnSpot` — the Paparcar Plus zone-alert opt-in. Defaults to 0 (off) for
 * every existing zone; the flag survives downgrades untouched because pausing is an entitlement
 * decision, not a data mutation. [MONEY-001]
 */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `zones` ADD COLUMN `notifyOnSpot` INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * v2 → v3: the vehicle learns what it burns — `fuelType`, the two consumption overrides and the
 * environmental label, all nullable (a pre-v3 row simply has not answered yet; the registration
 * edit screen asks). [VEH-FUEL-001]
 */
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `vehicles` ADD COLUMN `fuelType` TEXT")
        connection.execSQL("ALTER TABLE `vehicles` ADD COLUMN `consumptionPer100` REAL")
        connection.execSQL("ALTER TABLE `vehicles` ADD COLUMN `consumptionElectricPer100` REAL")
        connection.execSQL("ALTER TABLE `vehicles` ADD COLUMN `energyLabel` TEXT")
    }
}

/**
 * v3 → v4: a cached community spot can be an AREA — `cached_spots.zoneRadiusMeters`, nullable
 * (a pre-v4 row is what it always was: an exact pin). [SPOT-A-ZONE-PARK-FREES-A-ZONE-SPOT-001]
 */
private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `cached_spots` ADD COLUMN `zoneRadiusMeters` REAL")
    }
}

/**
 * v4 → v5: the vehicle can carry what its owner actually PAYS — a litre price, a kWh price and the
 * currency both are quoted in, all nullable (a pre-v5 row has not answered, and the national
 * average answers for it). One currency column for both amounts: someone who refuels and charges
 * in the same country pays in the same money. [FUEL-PRICE-RESOLVER-001]
 */
private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `vehicles` ADD COLUMN `fuelPricePerLiter` REAL")
        connection.execSQL("ALTER TABLE `vehicles` ADD COLUMN `energyPricePerKwh` REAL")
        connection.execSQL("ALTER TABLE `vehicles` ADD COLUMN `priceCurrency` TEXT")
    }
}

/**
 * v5 → v6: the address carries its PROVINCE, in all three places an address is stored.
 * [FUEL-A-PARKING-WITHOUT-A-COUNTRY-HAS-NO-PRICE-001]
 *
 * It was the last field the geocoder produced and nothing kept — deliberately, while no persisted
 * consumer read it. That reasoning ends here: an address now travels whole, so the "which fields
 * count" question that produced the country bug cannot be asked again field by field. Nullable,
 * because a pre-v6 row genuinely never captured one — and null keeps meaning "not captured", never
 * a claim.
 */
private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `parking_sessions` ADD COLUMN `addressProvince` TEXT")
        connection.execSQL("ALTER TABLE `cached_spots` ADD COLUMN `addressProvince` TEXT")
        connection.execSQL("ALTER TABLE `geocoder_cache` ADD COLUMN `addressProvince` TEXT")
    }
}

/**
 * v6 → v7: the public-car-park layer gets a disk, so the map is not empty on a cold start or without
 * a radio. [CACHE-A-MAP-MUST-NOT-EMPTY-ON-COLD-START-001]
 *
 * ⚠️ The **first `CREATE TABLE`** of this chain — every step before it adds a column. Two tables and
 * not one, and the second is the point: `osm_public_parkings` holds the facilities,
 * [OsmCoverageEntity] holds *having asked about an area*, which is the only thing that actually has
 * an age. Without it a bounding-box read is the union of everything ever written there, so nothing
 * can ever be removed and an empty area cannot be told from an unvisited one.
 *
 * Nothing is back-filled: a pre-v7 device simply has not cached anything yet, which is exactly what
 * empty tables say. The first answer the app receives fills them.
 */
private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `osm_public_parkings` (" +
                "`id` TEXT NOT NULL, `name` TEXT, `operator` TEXT, `kind` TEXT NOT NULL, " +
                "`latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `capacity` INTEGER, " +
                "`fee` TEXT NOT NULL, `maxHeightMeters` REAL, `openingHours` TEXT, " +
                "`cachedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_osm_public_parkings_latitude_longitude` " +
                "ON `osm_public_parkings` (`latitude`, `longitude`)"
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `osm_coverage` (" +
                "`discKey` TEXT NOT NULL, `layer` TEXT NOT NULL, `latitude` REAL NOT NULL, " +
                "`longitude` REAL NOT NULL, `radiusMeters` INTEGER NOT NULL, " +
                "`fetchedAt` INTEGER NOT NULL, PRIMARY KEY(`discKey`))"
        )
        // The fuel half, in the SAME version rather than a v8: v7 has never shipped, so splitting
        // one ticket across two numbers would invent a migration step no device will ever take.
        //
        // ⛔ Two tables and not one: `fuel_stations` is geography and keeps a long retention, while
        // `fuel_station_fuels` is the perishable half and keeps NO ttl at all — it stores the hour
        // the source stamped and lets the reader decide. That split is what answers the objection
        // `CachingFuelStationsSource` recorded against ever persisting this.
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `fuel_stations` (" +
                "`stationKey` TEXT NOT NULL, `source` TEXT NOT NULL, `id` TEXT NOT NULL, " +
                "`name` TEXT, `address` TEXT, `town` TEXT, `latitude` REAL NOT NULL, " +
                "`longitude` REAL NOT NULL, `schedule` TEXT, `cachedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`stationKey`))"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_fuel_stations_latitude_longitude` " +
                "ON `fuel_stations` (`latitude`, `longitude`)"
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `fuel_station_fuels` (" +
                "`stationKey` TEXT NOT NULL, `fuelType` TEXT NOT NULL, `priceAmount` REAL, " +
                "`priceCurrency` TEXT, `reportedAtMs` INTEGER, `availability` TEXT, " +
                "PRIMARY KEY(`stationKey`, `fuelType`))"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_fuel_station_fuels_stationKey` " +
                "ON `fuel_station_fuels` (`stationKey`)"
        )
    }
}

val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
