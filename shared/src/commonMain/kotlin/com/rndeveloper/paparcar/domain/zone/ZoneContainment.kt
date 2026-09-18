package com.rndeveloper.paparcar.domain.zone

import com.rndeveloper.paparcar.domain.model.Zone
import com.rndeveloper.paparcar.domain.util.haversineMeters

/**
 * Which saved [Zone] a point sits inside, if any.
 * [MAP-A-PARKED-CAR-INSIDE-A-ZONE-WEARS-THE-ZONE-001]
 *
 * A pure function, not a use case: it never reaches a diagnostic and never moves a pin — it decides
 * what a marker WEARS. [DET-VERDICT-NOT-PREDICATE-001]
 *
 * **The smallest one wins when zones overlap.** A car parked in "Garaje de casa" (40 m) inside
 * "Barrio" (600 m) is in both, and the useful answer is the specific one: the label the marker will
 * carry has to name the place the user would name. Ties by radius break on id so the answer is
 * stable across frames — a marker that alternates between two zones would re-rasterise forever.
 */
fun List<Zone>.zoneContaining(latitude: Double, longitude: Double): Zone? =
    filter { zone ->
        haversineMeters(latitude, longitude, zone.lat, zone.lon) <= zone.radiusMeters
    }.minWithOrNull(compareBy({ it.radiusMeters }, { it.id }))
