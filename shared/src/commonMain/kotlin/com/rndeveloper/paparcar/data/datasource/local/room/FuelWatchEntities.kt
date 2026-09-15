package com.rndeveloper.paparcar.data.datasource.local.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A station the USER put here. [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001]
 *
 * ⛔ Deliberately NOT a flag on [FuelStationCacheEntity], even though it would fit there and save a
 * table. The cache is swept: rows expire on a 30-day retention and are deleted wholesale when a disc
 * is reconciled. A follow that lived in that table would be **erased by a cache eviction** — the
 * user's own choice thrown away by a housekeeping pass that has no idea it was holding one. The two
 * have opposite lifetimes, so they get opposite tables.
 *
 * Geography is copied rather than joined for the same reason a cache row cannot be trusted to be
 * there: a followed station 300 km away must still be nameable and placeable when its province has
 * long fallen out of the disc.
 */
@Entity(tableName = "followed_fuel_stations")
data class FollowedFuelStationEntity(
    /** `source|id` — the same composite identity the cache uses. See `fuelStationKey`. */
    @PrimaryKey val stationKey: String,
    val source: String,
    val id: String,
    val name: String?,
    val latitude: Double,
    val longitude: Double,
    val followedAtMs: Long,
)

/**
 * One day's reading of one pump. [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001]
 *
 * ⛔ **This is the table `fuel_station_fuels` structurally cannot be.** That one is a cache, and its
 * contract is to REPLACE the fuel set on every write — `deleteFuelsFor` + `upsertFuels`, never
 * skipped, so that a station which stopped selling diesel stops quoting it. Correct for a cache, and
 * exactly what destroys yesterday's price. A cache is a photo of now; a movement needs two photos,
 * and asking one table for both is asking it to stop being a cache.
 *
 * A SERIES, not a log: the primary key ends in [observedDay], so eight readings on one afternoon are
 * one row and the last wins. Otherwise a user who opens the app often would build a differently
 * shaped history from one who opens it once, and the same feed would produce two different answers.
 *
 * ⛔ No TTL, same as its neighbour: rows are PRUNED by retention because a 30-day window is all the
 * free tier shows, not because an old measurement stopped being true. The database stores a
 * measurement, never a supposition.
 */
@Entity(
    tableName = "fuel_price_history",
    primaryKeys = ["stationKey", "fuelType", "observedDay"],
    indices = [Index(value = ["stationKey", "fuelType"])],
)
data class FuelPriceHistoryEntity(
    val stationKey: String,
    /** [com.rndeveloper.paparcar.domain.model.FuelType] by name. */
    val fuelType: String,
    /** ⛔ Never null and never zero: a station with no price for this fuel has no row at all. */
    val priceAmount: Double,
    val priceCurrency: String,
    /**
     * The hour the FEED stamped. Null is real (most of the world publishes none) and it is what
     * decides whether a price MOVED — two rows differing is not the same statement as a price
     * changing, because a feed can answer for a week with the same stale reading.
     */
    val reportedAtMs: Long?,
    /** When the app looked. */
    val observedAtMs: Long,
    /** Local day of [observedAtMs], days since epoch. See `localDayOf` for why local and not UTC. */
    val observedDay: Long,
)
