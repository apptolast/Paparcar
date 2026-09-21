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

/**
 * v8 [TRIP-A-LONG-DRIVE-KEEPS-ITS-ORIGIN-001]: the live trip's driven route moves from the
 * per-platform single-blob stores (SharedPreferences / NSUserDefaults, 500-point sliding cap) into
 * Room — one row per admitted fix, uncapped, so a long drive keeps its origin and each tick costs
 * one insert instead of a full-list rewrite.
 *
 * Nothing is back-filled: the table holds only the trip in progress, and a device that upgrades
 * mid-trip simply starts recording from its next fix (the backdated origin in the trip controller
 * covers the drawn line, exactly as it covers a process death today). The orphaned blob files stay
 * behind (~KB) — deliberately not worth a cleanup pass.
 */
private val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `trip_route_points` (" +
                "`seq` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`latitude` REAL NOT NULL, `longitude` REAL NOT NULL, " +
                "`accuracy` REAL NOT NULL, `timestamp` INTEGER NOT NULL, " +
                "`speed` REAL NOT NULL, `provider` TEXT, `satelliteCount` INTEGER)"
        )
    }
}

/**
 * El código postal, en las TRES tablas que guardan una dirección.
 * [FUEL-A-POSTCODE-IS-THE-PROVINCE-ID-001]
 *
 * ⛔ Las tres, y no solo la que hacía falta para los precios: *una dirección viaja entera*
 * ([FUEL-A-PARKING-WITHOUT-A-COUNTRY-HAS-NO-PRICE-001]). Decidir tabla por tabla quién «necesita»
 * el campo es la forma exacta que tenía el fallo que aquel ticket cerró.
 *
 * Gemela de la de `province` en [MIGRATION_5_6], que añadió las mismas tres columnas.
 */
private val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `parking_sessions` ADD COLUMN `addressPostalCode` TEXT")
        connection.execSQL("ALTER TABLE `cached_spots` ADD COLUMN `addressPostalCode` TEXT")
        connection.execSQL("ALTER TABLE `geocoder_cache` ADD COLUMN `addressPostalCode` TEXT")
    }
}

/**
 * v10 [TRIP-THE-SEARCH-FOR-A-SPOT-IS-MEASURED-001]: a parking remembers how long the spot took to
 * find. Measured once at confirm from the recorded route's clocks (the stored polyline has none);
 * nothing is back-filled — a pre-v10 row is honestly "unmeasured", never 0.
 */
private val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `parking_sessions` ADD COLUMN `searchTimeMs` INTEGER")
    }
}

/**
 * v11 [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001]: the two tables a price alert needs —
 * the stations the user follows, and one reading per station, fuel and DAY.
 *
 * ⛔ Nothing is back-filled, and there is nothing that could be: the fuel cache deletes and rewrites
 * its whole fuel set on every write, so no device has ever held yesterday's price. The series starts
 * the first day the watch worker runs, which is also why the first alert cannot arrive before the
 * second day — a movement needs two readings and we have never kept one.
 */
private val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `followed_fuel_stations` (" +
                "`stationKey` TEXT NOT NULL, `source` TEXT NOT NULL, `id` TEXT NOT NULL, " +
                "`name` TEXT, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, " +
                "`followedAtMs` INTEGER NOT NULL, PRIMARY KEY(`stationKey`))"
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `fuel_price_history` (" +
                "`stationKey` TEXT NOT NULL, `fuelType` TEXT NOT NULL, " +
                "`priceAmount` REAL NOT NULL, `priceCurrency` TEXT NOT NULL, " +
                "`reportedAtMs` INTEGER, `observedAtMs` INTEGER NOT NULL, " +
                "`observedDay` INTEGER NOT NULL, " +
                "PRIMARY KEY(`stationKey`, `fuelType`, `observedDay`))"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_fuel_price_history_stationKey_fuelType` " +
                "ON `fuel_price_history` (`stationKey`, `fuelType`)"
        )
    }
}

/**
 * v12 [ZONE-AN-ALERT-NOBODY-IS-AWAKE-TO-RECEIVE-001]: what the zone watch has already announced,
 * and when each zone was first looked at.
 *
 * ⛔ Nothing is back-filled and nothing could be: until now the "already alerted" set lived in
 * memory inside the stream and died with the process. The practical consequence on upgrade is one
 * silent first look per zone — it records the ground instead of reporting it — which is exactly
 * what a first look is supposed to do.
 *
 * ⚠️ It was WRITTEN as 10 → 11 and renumbered on the way in: the fuel watch and the zone watch were
 * developed in parallel off the same v10 and both claimed v11. A version number is the one kind of
 * collision git cannot see — two files edited in different places, one number silently shared — so
 * the second to land renumbers. The tables are unrelated; only the step is.
 */
private val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `zone_alerted_spots` (" +
                "`zoneId` TEXT NOT NULL, `spotId` TEXT NOT NULL, " +
                "`alertedAtMs` INTEGER NOT NULL, `spotExpiresAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`zoneId`, `spotId`))"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_zone_alerted_spots_spotExpiresAt` " +
                "ON `zone_alerted_spots` (`spotExpiresAt`)"
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `zone_watch_baseline` (" +
                "`zoneId` TEXT NOT NULL, `baselineAtMs` INTEGER NOT NULL, " +
                "PRIMARY KEY(`zoneId`))"
        )
    }
}

/**
 * v13 [VEH-SHARED-001]: a car two people drive.
 *
 * Two columns on `vehicles` — `sharedCarId` (the mirror this car projects onto) and `ownerUid`
 * (non-null ⇒ a LINKED row: somebody else's car, joined by invitation) — plus the read-only cache
 * of the mirror head. Nothing is back-filled and nothing changes for a car that is not shared: both
 * columns are null on every existing row, which is exactly what "this car is mine and only mine"
 * means.
 *
 * The cache table carries no `pendingSync`: the mirror is written straight to Firestore by whoever
 * parks, so there is no local edit here that could be owed to the server.
 *
 * ⚠️ WRITTEN as 10 → 11 and renumbered on the way in, exactly like the zone watch one step above:
 * this branch and the two watches all left the same v10. A version number is the one collision git
 * cannot see — two files edited in different places, one number silently shared — so whoever lands
 * last renumbers. The tables are unrelated; only the step is.
 */
private val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `vehicles` ADD COLUMN `sharedCarId` TEXT")
        connection.execSQL("ALTER TABLE `vehicles` ADD COLUMN `ownerUid` TEXT")
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `shared_cars` (" +
                "`carId` TEXT NOT NULL, `ownerUid` TEXT NOT NULL, `ownerName` TEXT, " +
                "`displayName` TEXT NOT NULL, `btMacHash` TEXT, `sizeCategory` TEXT, " +
                "`carbodyType` TEXT, `color` TEXT, `sharingEnabled` INTEGER NOT NULL, " +
                "`createdAtMs` INTEGER NOT NULL, `activeInviteCode` TEXT, " +
                "`activeInviteExpiresAtMs` INTEGER, `stateSessionId` TEXT, `stateLatitude` REAL, " +
                "`stateLongitude` REAL, `stateAccuracy` REAL, `stateParkedAtMs` INTEGER, " +
                "`stateAuthorUid` TEXT, `stateAuthorName` TEXT, `stateStreet` TEXT, " +
                "`stateCity` TEXT, `stateRegion` TEXT, `stateCountry` TEXT, " +
                "`stateCountryCode` TEXT, `stateProvince` TEXT, `statePostalCode` TEXT, " +
                "`statePlaceName` TEXT, `statePlaceCategory` TEXT, " +
                "`stateIsParked` INTEGER NOT NULL, `stateUpdatedAtMs` INTEGER NOT NULL, " +
                "PRIMARY KEY(`carId`))"
        )
    }
}

/**
 * v14 [VEH-SHARED-001]: a shared car is measured like any other, so the mirror keeps the car's spec
 * and each driver keeps a switch over their own routes.
 *
 * `shareRoutesInGroup` defaults to **1**, and the default IS the decision (user, 15-09): the routes
 * of a shared car reach the group the way its figures do, and the switch exists to take one back
 * rather than to have to ask for it. On an existing row the column means nothing until that car
 * joins a group, which is exactly when the value starts mattering.
 */
private val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE `vehicles` ADD COLUMN `shareRoutesInGroup` INTEGER NOT NULL DEFAULT 1"
        )
        connection.execSQL("ALTER TABLE `shared_cars` ADD COLUMN `fuelType` TEXT")
        connection.execSQL("ALTER TABLE `shared_cars` ADD COLUMN `consumptionPer100` REAL")
        connection.execSQL("ALTER TABLE `shared_cars` ADD COLUMN `consumptionElectricPer100` REAL")
    }
}

/**
 * v15 [FUEL-ITALY-A-PRICE-DEPENDS-ON-WHO-PUMPS-IT-001]: a remembered price says whether you pumped
 * it yourself.
 *
 * ⛔ **Nullable on purpose, with no default.** Null is the honest value for every row already on
 * disk and for every row Spain, France, Germany and OSM will ever write: their feeds quote one
 * price per pump and the question does not arise. A `NOT NULL DEFAULT 0` would have written
 * "attended service" across four countries' worth of cache in one statement.
 */
private val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `fuel_station_fuels` ADD COLUMN `isSelf` INTEGER")
        // ⛔ BOTH tables, or the price watch keeps the half that lets it lie. A followed Italian
        // station that stops quoting `self` still quotes `servito` — 0,35 €/L dearer, measured —
        // and without the mode on the HISTORY row the overnight verdict reads that as a rise.
        connection.execSQL("ALTER TABLE `fuel_price_history` ADD COLUMN `isSelf` INTEGER")
    }
}

/**
 * v16 — la sesión guarda **con qué números** decidió la rama que la colocó.
 * [DET-A-WALK-IS-NOT-AN-EGRESS-001]
 *
 * Una columna nullable: las filas anteriores se quedan en `NULL`, que es la verdad —de aquellas
 * sesiones no lo sabemos, y no se inventa.
 */
private val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `parking_sessions` ADD COLUMN `decisionEvidence` TEXT")
    }
}

/** v17: el vehículo puede declarar su ALTO, y sólo declarado autoriza un veredicto de gálibo.
 *  ⚠️ `REAL`, no `INTEGER`: son metros con decimales (1,47), y guardarlo en centímetros enteros
 *  obligaría a cada lector a recordar la unidad. [VEH-A-HEIGHT-UNKNOWN-IS-NOT-A-FIT-001] */
private val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `vehicles` ADD COLUMN `heightMeters` REAL")
    }
}

/**
 * v18: se retira `spotId` de `parking_sessions`, un campo MUERTO cuyo nombre mentía.
 * [DATA-A-DEAD-FIELD-THAT-LIES-IS-WORSE-THAN-A-DEAD-FIELD-001]
 *
 * ⚠️ **Se recrea la tabla, no se suelta la columna.** `ALTER TABLE … DROP COLUMN` llegó a SQLite en
 * la 3.35 (Android 12) y el minSdk es 26, que trae la 3.19: en la mitad del parque la sentencia
 * simple no existe. Copiar-y-renombrar es la única que funciona en todas.
 *
 * ⚠️ El `INSERT` nombra las 40 columnas UNA a UNA en vez de `SELECT *`: con el asterisco, el orden
 * lo decidiría la tabla vieja y bastaría que una versión futura insertara una columna en medio para
 * que los valores entraran corridos, sin error y sin ruido.
 *
 * ⛔ Y no lleva `DROP TABLE IF EXISTS parking_sessions_new` de cortesía por delante: si esa tabla ya
 * existiera, algo habría dejado una migración a medias y borrarla en silencio taparía el rastro.
 */
internal val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `parking_sessions_new` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `vehicleId` TEXT, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `accuracy` REAL NOT NULL, `timestamp` INTEGER NOT NULL, `geofenceId` TEXT, `isActive` INTEGER NOT NULL, `addressStreet` TEXT, `addressCity` TEXT, `addressRegion` TEXT, `addressCountry` TEXT, `addressCountryCode` TEXT, `addressProvince` TEXT, `addressPostalCode` TEXT, `placeInfoName` TEXT, `placeInfoCategory` TEXT, `detectionReliability` REAL, `sizeCategory` TEXT, `carbodyType` TEXT, `spotType` TEXT, `privateZoneId` TEXT, `tripMaxSpeedMps` REAL, `provisionalDepartureAtMs` INTEGER, `armEvidence` TEXT, `decisionEvidence` TEXT, `detectionPath` TEXT, `zoneRadiusMeters` REAL, `routePolyline` TEXT, `routeSnapped` INTEGER NOT NULL, `routeInferredSpans` TEXT, `routeInferredResolution` TEXT, `routeDistanceMeters` REAL, `searchTimeMs` INTEGER, `endedAtMs` INTEGER, `publishedSpot` INTEGER NOT NULL, `retractedAtMs` INTEGER, `updatedAt` INTEGER NOT NULL, `pendingSync` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        connection.execSQL(
            "INSERT INTO `parking_sessions_new` (`id`, `userId`, `vehicleId`, `latitude`, `longitude`, `accuracy`, `timestamp`, `geofenceId`, `isActive`, `addressStreet`, `addressCity`, `addressRegion`, `addressCountry`, `addressCountryCode`, `addressProvince`, `addressPostalCode`, `placeInfoName`, `placeInfoCategory`, `detectionReliability`, `sizeCategory`, `carbodyType`, `spotType`, `privateZoneId`, `tripMaxSpeedMps`, `provisionalDepartureAtMs`, `armEvidence`, `decisionEvidence`, `detectionPath`, `zoneRadiusMeters`, `routePolyline`, `routeSnapped`, `routeInferredSpans`, `routeInferredResolution`, `routeDistanceMeters`, `searchTimeMs`, `endedAtMs`, `publishedSpot`, `retractedAtMs`, `updatedAt`, `pendingSync`) SELECT `id`, `userId`, `vehicleId`, `latitude`, `longitude`, `accuracy`, `timestamp`, `geofenceId`, `isActive`, `addressStreet`, `addressCity`, `addressRegion`, `addressCountry`, `addressCountryCode`, `addressProvince`, `addressPostalCode`, `placeInfoName`, `placeInfoCategory`, `detectionReliability`, `sizeCategory`, `carbodyType`, `spotType`, `privateZoneId`, `tripMaxSpeedMps`, `provisionalDepartureAtMs`, `armEvidence`, `decisionEvidence`, `detectionPath`, `zoneRadiusMeters`, `routePolyline`, `routeSnapped`, `routeInferredSpans`, `routeInferredResolution`, `routeDistanceMeters`, `searchTimeMs`, `endedAtMs`, `publishedSpot`, `retractedAtMs`, `updatedAt`, `pendingSync` FROM `parking_sessions`",
        )
        connection.execSQL("DROP TABLE `parking_sessions`")
        connection.execSQL("ALTER TABLE `parking_sessions_new` RENAME TO `parking_sessions`")
    }
}

/** v19: la sesión dice qué plaza de la comunidad ocupó — lo que el difunto `spotId` fingía ser.
 *  ⚠️ Aquí SÍ basta un `ADD COLUMN`: añadir columnas lo sabe hacer hasta el SQLite de Android 8.
 *  Lo que no sabe es soltarlas, y de eso iba MIGRATION_17_18.
 *  [SPOT-A-SPACE-THE-COMMUNITY-GAVE-YOU-IS-ONE-YOU-VOUCHED-FOR-001] */
private val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `parking_sessions` ADD COLUMN `communitySpotId` TEXT")
    }
}

val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19)
