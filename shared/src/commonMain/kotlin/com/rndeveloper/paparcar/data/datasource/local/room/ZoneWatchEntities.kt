package com.rndeveloper.paparcar.data.datasource.local.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A spot this zone's owner has already been told about.
 * [ZONE-AN-ALERT-NOBODY-IS-AWAKE-TO-RECEIVE-001]
 *
 * ⛔ **This table is the ticket.** The rule it replaces lived in a `mutableSetOf` inside
 * `ObserveZoneSpotAlertsUseCase` — correct for a stream that lives as long as the process, and
 * unusable for a worker that starts from nothing every fifteen minutes. Without it the background
 * look has only two behaviours, and both are wrong: take a baseline each run and never alert, or
 * skip it and dump the whole zone every quarter of an hour.
 *
 * Keyed by (zone, spot) and not by spot alone: the same free space can sit inside two zones that
 * overlap, and being told once about your street does not mean you were told about your office.
 */
@Entity(
    tableName = "zone_alerted_spots",
    primaryKeys = ["zoneId", "spotId"],
    indices = [Index(value = ["spotExpiresAt"])],
)
data class ZoneAlertedSpotEntity(
    val zoneId: String,
    val spotId: String,
    val alertedAtMs: Long,
    /**
     * The spot's own expiry, copied in so the sweep needs nothing else.
     *
     * ⛔ Pruned by the SPOT's death, never by the age of the notification: what stops a repeat is
     * that the space still exists. A row dropped early would re-announce a spot the user already
     * dismissed; one dropped by its own age would do it on a timetable of its own. 0 means the spot
     * carried no TTL, and those are swept by the orphan pass instead.
     */
    val spotExpiresAt: Long,
)

/**
 * When a zone was first looked at. [ZONE-AN-ALERT-NOBODY-IS-AWAKE-TO-RECEIVE-001]
 *
 * ⛔ Its own row and not "the alerted set is empty", because those are different facts: a zone
 * nobody has looked at yet and a zone that was looked at and had nothing in it both hold zero
 * alerted rows, and only the first must stay silent about what is already on the map.
 *
 * ⚠️ A local marker, deliberately: it records when THIS DEVICE started watching. A second phone
 * takes its own baseline and may therefore stay quiet about a spot the first one announced. The fix
 * is not another table — it is knowing when the SUBSCRIPTION was made rather than when a device
 * first looked, and that is a field on the zone. See the ticket's follow-up note.
 */
@Entity(tableName = "zone_watch_baseline")
data class ZoneWatchBaselineEntity(
    @PrimaryKey val zoneId: String,
    val baselineAtMs: Long,
)
