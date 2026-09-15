package com.rndeveloper.paparcar.domain.stats.insights

import com.rndeveloper.paparcar.domain.stats.CLUSTER_RADIUS_METERS
import com.rndeveloper.paparcar.domain.stats.ParkingCluster
import com.rndeveloper.paparcar.domain.util.haversineMeters
import kotlin.math.abs

/**
 * A place you use that has got harder — or easier — than it was last period.
 * [STATS-A-PLACE-CAN-GET-WORSE-001]
 *
 * This is the watch the Plus pitch promises, and the first sentence in the app that compares a PLACE
 * with itself over time: roadworks, a new resident, a car park that closed. The figures card can say
 * a place costs nine minutes; only this can say it used to cost three.
 *
 * ⛔ **Matched by GEOMETRY, never by name.** The same patch of ground can be called "Calle de Alcalá"
 * one month and "Calle de O'Donnell" the next — which is the whole reason a place stopped being a
 * string [STATS-A-USUAL-PLACE-IS-A-CLUSTER-NOT-A-STREET-001]. Two clusters are the same place when
 * their centroids sit within the radius that built them.
 *
 * ⛔ **Both sides need a real mean.** A mean over one hunt is not a mean, so a place with a single
 * measured visit on either side says nothing — the same threshold the day × hour grid lives by.
 */
fun placeTrend(
    places: List<ParkingCluster>,
    previousPlaces: List<ParkingCluster>,
): StatsInsight? = places
    .asSequence()
    .filter { !it.name.isNullOrBlank() && it.measuredHunts() >= MIN_HUNTS_PER_SIDE }
    .mapNotNull { place ->
        val before = previousPlaces
            .filter { it.measuredHunts() >= MIN_HUNTS_PER_SIDE }
            .minByOrNull { haversineMeters(place.latitude, place.longitude, it.latitude, it.longitude) }
            ?.takeIf {
                haversineMeters(place.latitude, place.longitude, it.latitude, it.longitude) <=
                    CLUSTER_RADIUS_METERS
            }
            ?: return@mapNotNull null
        val now = place.avgSearchTimeMs ?: return@mapNotNull null
        val then = before.avgSearchTimeMs ?: return@mapNotNull null
        if (then <= 0L) return@mapNotNull null
        val ratio = now.toDouble() / then
        if (abs(ratio - 1.0) < MIN_CHANGE) return@mapNotNull null
        StatsInsight(
            kind = InsightKind.PLACE_TREND,
            values = InsightValues(
                placeName = place.name,
                valueMs = now,
                baselineMs = then,
            ),
            samples = place.measuredHunts(),
            // Getting better is as worth saying as getting worse, so the effect is the SIZE of the
            // move either way — the same choice the period-wide trend made.
            effect = 1.0 + abs(ratio - 1.0),
        )
    }
    .maxByOrNull { it.score }

private fun ParkingCluster.measuredHunts(): Int = searchTime?.fromParkings ?: 0

/** Two measured hunts a side: below that there is no mean to compare. */
private const val MIN_HUNTS_PER_SIDE = 2

/** Half again, either way. Below that it is the ordinary noise of parking. */
private const val MIN_CHANGE = 0.5
