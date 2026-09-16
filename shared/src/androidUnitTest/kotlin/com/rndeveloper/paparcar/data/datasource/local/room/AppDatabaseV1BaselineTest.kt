package com.rndeveloper.paparcar.data.datasource.local.room

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [DATA-ROOM-RETURNS-TO-VERSION-ONE-001] What a real file meets when it opens against the collapsed
 * v1 baseline. Three rows, because the collapse gave the v1 number **two** possible meanings and
 * only one of them opens.
 *
 * | file on the device                       | outcome                    |
 * |------------------------------------------|----------------------------|
 * | v2, hash `e8bc446…` (the bench build)    | downgrade → wiped          |
 * | v1, hash `e8bc446…` (fresh post-change)  | opens, row survives        |
 * | v1, hash `03390c9c…` (the OLD bench v1)  | **deleted → opens empty**  |
 *
 * The third row is the point, and it changed on 2026-09-03. Room still cannot rescue that file —
 * it decides by VERSION, and same version + different identity hash is invisible to it, exactly as
 * this doc used to say. What was wrong was the conclusion drawn from it: the case was pinned as
 * "refused, forever", which was true about Room and false about the product. In the field it meant
 * *"No se pudo cargar tu cuenta"*, a sign-out, a sign-in, and the same dialog again — a loop with no
 * exit inside the app. [DATA-A-DB-IT-CANNOT-OPEN-MUST-NOT-DEAD-END-THE-USER-001] moved the recovery
 * one level up, into [buildAppDatabase]: if the file will not open, delete it and start from empty.
 * The local database is a CACHE; its truth is re-fetched from Firestore.
 *
 * Clearing the bench phones is therefore no longer a step of the release — the app clears itself.
 *
 * The fourth case exists to keep that repair honest: a builder that deletes on failure must not
 * delete when nothing failed.
 *
 * Every case drives [buildAppDatabase], the production builder, so a witness cannot drift from the
 * configuration it claims to describe.
 *
 * The seeded DDL is copied verbatim from the actual bench database (`Redmi 2201117TY`, pulled
 * 2026-09-01), and both identity hashes are the real ones read off that phone and off the field
 * error message.
 *
 * Since [MONEY-001] the app declares **v2** (`zones.notifyOnSpot`), which re-reads this table
 * without invalidating it: the v1-current files now travel through `MIGRATION_1_2` and must come
 * out the other side with their rows intact (that IS the migration witness), while the old bench
 * v2 file — v1-shaped schema under a v2 number — becomes the same-version-different-hash case the
 * builder resolves by deleting.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppDatabaseV1BaselineTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun should_wipeAndReopen_when_theFileIsTheOldV2BenchDatabase() = runTest {
        // The old bench v2 file: v1-shaped schema under a v2 number. When the app itself was v1
        // this was a downgrade; now that [MONEY-001] declares v2 it is the same-version-different-
        // hash shape, the ONE Room neither migrates nor wipes — the builder's delete-on-failure is
        // what resolves it. Either way the outcome the user meets is identical: wiped, reopened.
        val name = "baseline-from-v2.db"
        seedBenchDatabase(name, version = 2, withRetractedColumn = true, identityHash = CURRENT_IDENTITY_HASH)

        val db = buildAppDatabase(context, name)
        val rows = db.parkingSessionDao().getByUser("user-bench")
        db.close()

        assertTrue(rows.isEmpty(), "the unreadable v2 file must not leak its row back")
        assertEquals(PAPARCAR_DB_VERSION, readUserVersion(name), "the file must end up at the declared version")
    }

    @Test
    fun should_openAndKeepTheRow_when_theFileIsV1WithTheCurrentSchema() = runTest {
        // Same version, same identity hash: nothing to migrate, nothing to wipe. This is also what
        // every fresh install produces, so it doubles as the proof that `1.json` still describes
        // the entities — a drift there would change the hash and turn this into the third case.
        val name = "baseline-v1-current.db"
        seedBenchDatabase(name, version = 1, withRetractedColumn = true, identityHash = CURRENT_IDENTITY_HASH)

        val db = buildAppDatabase(context, name)
        val rows = db.parkingSessionDao().getByUser("user-bench")
        // The same open is also MIGRATION_1_2's witness [MONEY-001]: the zone must come across
        // with the new alert flag OFF — existing zones never opt in alone.
        val zones = db.zoneDao().getByUser("user-bench")
        // …and MIGRATION_2_3's witness [VEH-FUEL-001]: a vehicle written WITH the fuel block must
        // round-trip through the migrated file — proof the four columns actually exist, not just
        // that nothing crashed on the way here.
        db.vehicleDao().insert(
            VehicleEntity(
                id = "v-fuel-witness",
                userId = "user-bench",
                sizeCategory = "MEDIUM_SUV",
                fuelType = "DIESEL",
                consumptionPer100 = 5.1f,
                energyLabel = "C",
            ),
        )
        val fuelRow = db.vehicleDao().getById("v-fuel-witness", "user-bench")
        // …and MIGRATION_5_6's witness [FUEL-A-PARKING-WITHOUT-A-COUNTRY-HAS-NO-PRICE-001]: the
        // address province must round-trip through the migrated file. Same argument as the fuel
        // block above — running the chain without throwing only proves nothing threw, and the whole
        // point of this ticket is that a column can exist while nothing ever writes it.
        db.parkingSessionDao().updateAddressAndPlace(
            id = "pin-1",
            street = "Calle Mayor 1",
            city = "Madrid",
            region = "Comunidad de Madrid",
            country = "España",
            countryCode = "es",
            province = "Madrid",
            postalCode = "28013",
            placeInfoName = null,
            placeInfoCategory = null,
            now = 1L,
        )
        val migratedAddress = db.parkingSessionDao().getByUser("user-bench").single()
        // …and MIGRATION_6_7's witness [CACHE-A-MAP-MUST-NOT-EMPTY-ON-COLD-START-001]: the two new
        // tables must be REACHABLE through the migrated file, not merely created without throwing.
        // This step is the first CREATE TABLE of the chain and its DDL is hand-written, so "the
        // migration ran" and "the schema Room expects is the schema that exists" are two different
        // claims — a column typed by hand where Room wanted another would only surface on the write.
        db.publicParkingCacheDao().upsertAll(
            listOf(
                PublicParkingCacheEntity(
                    id = "osm-witness",
                    name = "Parking Mayor",
                    operator = null,
                    kind = "UNDERGROUND",
                    latitude = 36.6084,
                    longitude = -6.2781,
                    capacity = 120,
                    fee = "PAID",
                    maxHeightMeters = 2.1,
                    openingHours = null,
                    cachedAt = 1L,
                ),
            ),
        )
        db.osmCoverageDao().upsert(
            OsmCoverageEntity(
                discKey = "parkings|1500|36.608,-6.278",
                layer = "parkings",
                latitude = 36.6084,
                longitude = -6.2781,
                radiusMeters = 1_500,
                fetchedAt = 1L,
            ),
        )
        val cachedParking = db.publicParkingCacheDao()
            .getInBoundingBox(36.60, 36.61, -6.28, -6.27)
        val cachedDisc = db.osmCoverageDao().getLive("parkings", minFetchedAt = 0L)
        // The fuel half of the same step, and its own witness: two tables whose rows are joined by
        // `stationKey`, so "the station survived" and "its price survived" are separate claims.
        db.fuelStationCacheDao().upsertStations(
            listOf(
                FuelStationCacheEntity(
                    stationKey = "es-minetur|1234",
                    source = "es-minetur",
                    id = "1234",
                    name = "Repsol Cádiz",
                    address = "Avenida Principal 1",
                    town = "Cádiz",
                    latitude = 36.6084,
                    longitude = -6.2781,
                    schedule = "24H",
                    cachedAt = 1L,
                ),
            ),
        )
        db.fuelStationCacheDao().upsertFuels(
            listOf(
                FuelStationFuelEntity(
                    stationKey = "es-minetur|1234",
                    fuelType = "DIESEL",
                    priceAmount = 1.539,
                    priceCurrency = "EUR",
                    reportedAtMs = 1_788_204_119_405,
                    availability = null,
                    // MIGRATION_14_15's witness [FUEL-ITALY-A-PRICE-DEPENDS-ON-WHO-PUMPS-IT-001]:
                    // written as ATTENDED on purpose, because the default the column would have
                    // taken is the opposite claim and a `false` that survives by accident proves
                    // nothing. Nothing else in this file would notice the column missing.
                    isSelf = false,
                ),
            ),
        )
        val cachedStation = db.fuelStationCacheDao()
            .getInBoundingBox(36.60, 36.61, -6.28, -6.27)
        val cachedFuels = db.fuelStationCacheDao().getFuelsFor(listOf("es-minetur|1234"))
        // …and MIGRATION_7_8's witness [TRIP-A-LONG-DRIVE-KEEPS-ITS-ORIGIN-001]: a trip route point
        // must round-trip through the migrated file — hand-written DDL, so "created without
        // throwing" and "the schema Room expects" stay two different claims here too.
        db.tripRoutePointDao().insert(
            TripRoutePointEntity(
                latitude = 36.6084,
                longitude = -6.2781,
                accuracy = 5.0f,
                timestamp = 1_788_204_119_405,
                speed = 8.3f,
                provider = "gps",
                satelliteCount = 17,
            ),
        )
        val routePoints = db.tripRoutePointDao().getAllOrdered()
        // …and MIGRATION_9_10's witness [TRIP-THE-SEARCH-FOR-A-SPOT-IS-MEASURED-001]: a session
        // written WITH the search-time figure must round-trip through the migrated file — the
        // column has to exist AND be readable, not merely not-throw.
        db.parkingSessionDao().insert(
            UserParkingEntity(
                id = "pin-search-witness",
                userId = "user-bench",
                latitude = 36.6084,
                longitude = -6.2781,
                accuracy = 5.0f,
                timestamp = 1_788_204_119_405,
                isActive = false,
                routeSnapped = false,
                publishedSpot = false,
                updatedAt = 2L,
                pendingSync = false,
                searchTimeMs = 187_000L,
            ),
        )
        val searchWitness = db.parkingSessionDao().getByUser("user-bench")
            .firstOrNull { it.id == "pin-search-witness" }
        db.close()

        assertEquals(1, rows.size, "an untouched schema must not cost the user their parked session")
        assertEquals("pin-1", rows.single().id)
        assertEquals(1, zones.size, "the 1→2 migration must carry the zone across, not recreate the table")
        assertEquals("zone-1", zones.single().id)
        assertFalse(zones.single().notifyOnSpot, "existing zones default to alerts OFF")
        assertEquals("DIESEL", fuelRow?.fuelType, "the 2→3 migration must add the fuel columns")
        assertEquals(5.1f, fuelRow?.consumptionPer100)
        assertEquals(
            "Madrid",
            migratedAddress.addressProvince,
            "the 5→6 migration must add the province column, and the write path must fill it",
        )
        assertEquals("es", migratedAddress.addressCountryCode, "the country must survive the chain")
        assertEquals(
            "Parking Mayor",
            cachedParking.singleOrNull()?.name,
            "the 6→7 migration must create osm_public_parkings with the columns Room expects",
        )
        assertEquals(2.1, cachedParking.single().maxHeightMeters)
        assertEquals(
            1_500,
            cachedDisc.singleOrNull()?.radiusMeters,
            "the 6→7 migration must create osm_coverage — without it nothing can be removed or aged",
        )
        assertEquals(
            "Repsol Cádiz",
            cachedStation.singleOrNull()?.name,
            "the 6→7 migration must create fuel_stations with the columns Room expects",
        )
        assertEquals(
            1.539,
            cachedFuels.singleOrNull()?.priceAmount,
            "the 6→7 migration must create fuel_station_fuels — the perishable half of a station",
        )
        assertEquals(
            1_788_204_119_405,
            cachedFuels.single().reportedAtMs,
            "a stored price keeps its own hour, which is what makes storing it honest at all",
        )
        assertEquals(
            false,
            cachedFuels.single().isSelf,
            "the 14→15 migration must carry the pump's mode, or the disk relabels an attended " +
                "Italian price as self-service on the next cold start",
        )
        assertEquals(
            8.3f,
            routePoints.singleOrNull()?.speed,
            "the 7→8 migration must create trip_route_points with the columns Room expects",
        )
        assertEquals("gps", routePoints.single().provider, "provenance must survive the file")
        assertEquals(
            187_000L,
            searchWitness?.searchTimeMs,
            "the 9→10 migration must add searchTimeMs, and the write path must fill it",
        )
        assertEquals(PAPARCAR_DB_VERSION, readUserVersion(name))
    }

    @Test
    fun should_deleteTheFileAndOpenEmpty_when_theFileIsV1FromBeforeTheRetractedColumn() = runTest {
        // [DATA-A-DB-IT-CANNOT-OPEN-MUST-NOT-DEAD-END-THE-USER-001] This case used to assert the
        // refusal was permanent, and the assertion was TRUE about Room while being wrong about the
        // product: the user met "No se pudo cargar tu cuenta", got signed out, signed back in, and
        // met it again — for ever. Room still cannot rescue this file; the BUILDER now does, by
        // deleting it and starting from empty.
        val name = "baseline-v1-stale.db"
        seedBenchDatabase(name, version = 1, withRetractedColumn = false, identityHash = STALE_IDENTITY_HASH)

        val db = buildAppDatabase(context, name)
        val rows = db.parkingSessionDao().getByUser("user-bench")
        db.close()

        assertTrue(rows.isEmpty(), "the unreadable file must be gone, not carried over")
        assertEquals(
            PAPARCAR_DB_VERSION,
            readUserVersion(name),
            "the recreated file must be at the declared version",
        )
    }

    @Test
    fun should_notDeleteAnything_when_theFileOpensFine() = runTest {
        // The other half of the recovery, and the one worth pinning: a builder that repairs must not
        // repair what is not broken. Without this, "delete on failure" could quietly become
        // "delete on every start" and nobody would notice — the app would work, and every user
        // would silently lose their local cache on each launch.
        val name = "baseline-v1-untouched.db"
        seedBenchDatabase(name, version = 1, withRetractedColumn = true, identityHash = CURRENT_IDENTITY_HASH)

        val db = buildAppDatabase(context, name)
        val rows = db.parkingSessionDao().getByUser("user-bench")
        db.close()

        assertEquals(1, rows.size, "a healthy file must survive the open probe untouched")
        assertEquals("pin-1", rows.single().id)
    }

    // ── seeding: the REAL bench schema, verbatim ────────────────────────────────────────────────

    /**
     * Writes a file Room did not create, at the given [version], carrying [identityHash] in its
     * `room_master_table` and one parked session. Only `parking_sessions` differs between the two
     * v1 variants ([withRetractedColumn]); the other tables were identical on the bench.
     */
    private fun seedBenchDatabase(
        name: String,
        version: Int,
        withRetractedColumn: Boolean,
        identityHash: String,
    ) {
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        file.delete()
        val retracted = if (withRetractedColumn) ", `retractedAtMs` INTEGER" else ""
        SQLiteDatabase.openOrCreateDatabase(file, null).use { seed ->
            seed.execSQL(
                "CREATE TABLE `parking_sessions` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, " +
                    "`vehicleId` TEXT, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, " +
                    "`accuracy` REAL NOT NULL, `timestamp` INTEGER NOT NULL, `spotId` TEXT, " +
                    "`geofenceId` TEXT, `isActive` INTEGER NOT NULL, `addressStreet` TEXT, " +
                    "`addressCity` TEXT, `addressRegion` TEXT, `addressCountry` TEXT, " +
                    "`addressCountryCode` TEXT, `placeInfoName` TEXT, `placeInfoCategory` TEXT, " +
                    "`detectionReliability` REAL, `sizeCategory` TEXT, `carbodyType` TEXT, " +
                    "`spotType` TEXT, `privateZoneId` TEXT, `tripMaxSpeedMps` REAL, " +
                    "`provisionalDepartureAtMs` INTEGER, `armEvidence` TEXT, `detectionPath` TEXT, " +
                    "`zoneRadiusMeters` REAL, `routePolyline` TEXT, `routeSnapped` INTEGER NOT NULL, " +
                    "`routeInferredSpans` TEXT, `routeInferredResolution` TEXT, " +
                    "`routeDistanceMeters` REAL, `endedAtMs` INTEGER, `publishedSpot` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL, `pendingSync` INTEGER NOT NULL$retracted, " +
                    "PRIMARY KEY(`id`))",
            )
            seed.execSQL(
                "CREATE TABLE `user_profile` (`userId` TEXT NOT NULL, `email` TEXT, `displayName` TEXT, " +
                    "`photoUrl` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                    "`defaultVehicleId` TEXT, PRIMARY KEY(`userId`))",
            )
            seed.execSQL(
                "CREATE TABLE `vehicles` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `name` TEXT, " +
                    "`brand` TEXT, `model` TEXT, `sizeCategory` TEXT NOT NULL, `carbodyType` TEXT, " +
                    "`vehicleType` TEXT NOT NULL, `bluetoothDeviceId` TEXT, " +
                    "`showBrandModelOnSpot` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, " +
                    "`licensePlate` TEXT, `color` TEXT, `updatedAt` INTEGER NOT NULL, " +
                    "`pendingSync` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            seed.execSQL(
                "CREATE TABLE `cached_spots` (`id` TEXT NOT NULL, `latitude` REAL NOT NULL, " +
                    "`longitude` REAL NOT NULL, `accuracy` REAL NOT NULL, `reportedAt` INTEGER NOT NULL, " +
                    "`reportedBy` TEXT NOT NULL, `speed` REAL NOT NULL, `addressStreet` TEXT, " +
                    "`addressCity` TEXT, `addressRegion` TEXT, `addressCountry` TEXT, " +
                    "`addressCountryCode` TEXT, `geohash` TEXT, `placeInfoName` TEXT, " +
                    "`placeInfoCategory` TEXT, `type` TEXT NOT NULL, `confidence` REAL NOT NULL, " +
                    "`sizeCategory` TEXT, `carbodyType` TEXT, `enRouteCount` INTEGER NOT NULL, " +
                    "`expiresAt` INTEGER NOT NULL, `acceptCount` INTEGER NOT NULL, " +
                    "`rejectCount` INTEGER NOT NULL, `status` TEXT NOT NULL, PRIMARY KEY(`id`))",
            )
            seed.execSQL(
                "CREATE TABLE `zones` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                    "`lat` REAL NOT NULL, `lon` REAL NOT NULL, `iconKey` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, `radiusMeters` REAL NOT NULL, " +
                    "`isPrivate` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                    "`pendingSync` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            seed.execSQL(
                "CREATE TABLE `geocoder_cache` (`locationKey` TEXT NOT NULL, `addressStreet` TEXT, " +
                    "`addressCity` TEXT, `addressRegion` TEXT, `addressCountry` TEXT, " +
                    "`addressCountryCode` TEXT, `placeInfoName` TEXT, `placeInfoCategory` TEXT, " +
                    "`cachedAt` INTEGER NOT NULL, `poiChecked` INTEGER NOT NULL, PRIMARY KEY(`locationKey`))",
            )
            seed.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            seed.execSQL(
                "INSERT INTO room_master_table (id, identity_hash) VALUES (42, ?)",
                arrayOf(identityHash),
            )
            seed.execSQL(
                "INSERT INTO parking_sessions (id, userId, latitude, longitude, accuracy, timestamp, " +
                    "isActive, routeSnapped, publishedSpot, updatedAt, pendingSync) " +
                    "VALUES ('pin-1', 'user-bench', 36.6084, -6.2781, 5.0, 1788204119405, 1, 0, 0, 1788204119405, 0)",
            )
            // A zone row too, so the v1→v2 migration ([MONEY-001]) has a subject to carry across.
            seed.execSQL(
                "INSERT INTO zones (id, userId, name, lat, lon, iconKey, createdAt, radiusMeters, " +
                    "isPrivate, updatedAt, pendingSync) " +
                    "VALUES ('zone-1', 'user-bench', 'Casa', 36.6084, -6.2781, 'HOME', 1788204119405, 250.0, 0, 1788204119405, 0)",
            )
            seed.version = version
        }
        assertEquals(version, readUserVersion(name), "the seed itself must be at the version claimed")
    }

    private fun readUserVersion(name: String): Int =
        SQLiteDatabase.openDatabase(
            context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY,
        ).use { it.version }

    private companion object {
        /** What the current entities hash to — identical in `1.json` and the retired `2.json`. */
        const val CURRENT_IDENTITY_HASH = "e8bc446bd23663f9f0febbeda06c375e"

        /** What the bench databases born on 2026-08-29 carry, from before `retractedAtMs`. */
        const val STALE_IDENTITY_HASH = "03390c9c44e62b92f2c0e85cf92e5eaa"
    }
}
