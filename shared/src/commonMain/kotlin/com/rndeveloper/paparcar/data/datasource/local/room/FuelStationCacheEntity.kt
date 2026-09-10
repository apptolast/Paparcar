package com.rndeveloper.paparcar.data.datasource.local.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The part of a filling station that does NOT age: where it is, what it is called, when it opens.
 * [CACHE-A-MAP-MUST-NOT-EMPTY-ON-COLD-START-001]
 *
 * ⛔ The split from [FuelStationFuelEntity] is the whole reason this table may exist at all.
 * `CachingFuelStationsSource` argued against persisting stations, with a measurement behind it:
 * Italy's feed carries 69% of its prices two to seven days old with a tail reaching 2013, so
 * *"deciding a disk TTL before measuring one would bake a guess into the database"*. That is right
 * about the PRICE and does not apply to the FORECOURT — a petrol station does not move, change its
 * name or relocate its entrance inside a month.
 *
 * So the guess never gets baked: geography is kept with a long retention, and the perishable half
 * lives next door carrying its own hour instead of a TTL.
 */
@Entity(
    tableName = "fuel_stations",
    indices = [Index(value = ["latitude", "longitude"])],
)
data class FuelStationCacheEntity(
    /**
     * `source|id`, and the prefix is **not decoration**.
     *
     * ⛔ [FuelStation.id] is documented as stable *within its source*, and unlike the car-park layer
     * this one has THREE: Spain's `IDEESS`, France's official ref and an OSM element id all land in
     * this single table. Keying by the bare id would let Spain's station `1234` overwrite an OSM
     * station `1234` from another country — one upsert, one forecourt silently replaced by another
     * a thousand kilometres away.
     *
     * One table for every country is deliberate: the cache key is GEOGRAPHIC, not national. A disc
     * near Irún covers both sides of a border, and the country comes from a geocoder that fails
     * exactly when the radio does — which is the case this whole disk exists for. Per-country tables
     * would be unreadable precisely when they were needed.
     */
    @PrimaryKey val stationKey: String,
    /** Which feed answered — provenance, and the half of [stationKey] that makes it unique. */
    val source: String,
    /** The id as its own source words it. */
    val id: String,
    val name: String?,
    val address: String?,
    val town: String?,
    val latitude: Double,
    val longitude: Double,
    /** Verbatim, never parsed: "24H", "L-D: 07:00-22:00" and 24/7 all appear in the same feed. */
    val schedule: String?,
    val cachedAt: Long,
)

/**
 * What one station is known to do about ONE fuel: what it charges, when it said so, and whether it
 * sells it at all. [CACHE-A-MAP-MUST-NOT-EMPTY-ON-COLD-START-001]
 *
 * Price and availability share a row because they share a key — a station and a fuel — and because
 * they answer the same question from two directions. A price is proof of sale; an availability is
 * what the source says when there is no price.
 *
 * ⛔ **No TTL here, and that is the point.** The obvious version picks a number for how long a
 * cached price stays usable and writes it into the schema, which is exactly the guess
 * `CachingFuelStationsSource` refused to make. Instead the row keeps [reportedAtMs], the hour the
 * source itself stamped, and the READER decides — the same doctrine that put a date inside
 * `FuelPrice` [FUEL-AN-AVERAGE-MUST-SAY-WHEN-IT-WAS-MEASURED-001]. The database stores a
 * measurement, never a supposition.
 */
@Entity(
    tableName = "fuel_station_fuels",
    primaryKeys = ["stationKey", "fuelType"],
    indices = [Index(value = ["stationKey"])],
)
data class FuelStationFuelEntity(
    /** [FuelStationCacheEntity.stationKey] — `source|id`, for the collision reason documented there. */
    val stationKey: String,
    /** [com.rndeveloper.paparcar.domain.model.FuelType] by name. */
    val fuelType: String,
    /** Null when this station has no price for this fuel. ⛔ Never a zero — a feed reports "does not
     *  sell it" as an empty string, and reading that as 0,000 € would top a price-ranked list. */
    val priceAmount: Double?,
    val priceCurrency: String?,
    /** When the source said it. Null is real: an unparseable date costs the stamp, not the price. */
    val reportedAtMs: Long?,
    /**
     * [com.rndeveloper.paparcar.domain.fuel.FuelAvailability] by name, or null for "nobody has said".
     *
     * Null and the stored string `UNKNOWN` mean the same thing to every reader — `availabilityOf`
     * resolves an absent entry to UNKNOWN — so collapsing them loses nothing. What must NOT collapse
     * is UNKNOWN into NOT_SOLD: OSM tags `fuel:diesel` on 44% of stations, and reading the silent
     * 56% as "no diesel" would hide most of the world's forecourts from most of its drivers.
     */
    val availability: String?,
)
