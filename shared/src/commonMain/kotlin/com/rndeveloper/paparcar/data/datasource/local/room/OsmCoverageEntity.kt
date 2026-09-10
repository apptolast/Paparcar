package com.rndeveloper.paparcar.data.datasource.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A record of **having asked**: this disc, for this layer, at this moment.
 * [CACHE-A-MAP-MUST-NOT-EMPTY-ON-COLD-START-001]
 *
 * ⛔ This table is why the disk cache can be honest, and leaving it out is the mistake it exists to
 * prevent. Overpass never claims "this car park exists"; it claims **"this is everything of this
 * kind within R metres of P, now"**. That sentence is the thing with an age, not each row. Storing
 * only the rows throws it away, and three failures follow at once:
 *
 * 1. **Removals never propagate.** A bounding-box read is the union of everything ever written
 *    there, so a facility deleted upstream keeps being served forever and the table only grows.
 * 2. **A bounding-box read mixes ages.** Rows written minutes ago sit beside rows from last month's
 *    trip. Filtering row by row leaves a hole that reads as "there is nothing here" — a lie — and
 *    not filtering mixes them with no way to tell which is which.
 * 3. **"Nothing here" cannot be stored.** Zero rows is indistinguishable from never having looked.
 *
 * With the disc recorded, each one has an answer: a fresh response **reconciles its whole disc**
 * (anything inside it that the response does not carry is gone), the age shown is the disc's and not
 * the newest row's, and an empty area becomes a fact rather than a silence.
 *
 * It is the same principle the domain already applies one level up — `FeeInfo.UNKNOWN` and
 * `FuelAvailability.UNKNOWN` exist because absence is not free: **silence is not a fact unless
 * somebody recorded having asked.**
 */
@Entity(tableName = "osm_coverage")
data class OsmCoverageEntity(
    /**
     * `layer|radius|roundedLat,roundedLon` — see `coverageKey`. Asking again about the same disc
     * REPLACES the stamp rather than adding a second one, which is what keeps this table bounded.
     */
    @PrimaryKey val discKey: String,
    /** Which layer this disc answers for — the two never share coverage. */
    val layer: String,
    val latitude: Double,
    val longitude: Double,
    /**
     * How far this disc can honestly speak for, which is **not always what was requested**: an
     * Overpass query caps its output (`out center 80` for parkings), so a response that came back at
     * the cap was truncated and only really answers out to its farthest element. Storing the
     * effective radius keeps a truncated answer from claiming an area it never saw.
     */
    val radiusMeters: Int,
    val fetchedAt: Long,
)
