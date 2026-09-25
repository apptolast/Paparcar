@file:OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)

package com.rndeveloper.paparcar.data.datasource

import com.rndeveloper.paparcar.domain.detection.DetectionRuntimeState
import com.rndeveloper.paparcar.domain.location.LocationDataSource
import com.rndeveloper.paparcar.domain.location.UserLocationUi
import com.rndeveloper.paparcar.domain.model.GpsPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

/**
 * Mock [LocationDataSource]. By default emits a fixed point.
 *
 * When wired with a [runtime] (mock flavor only), [observeUiLocation] switches to a **moving** track
 * while a trip is being "detected" ([DetectionRuntimeState.isRunning]) — so the Home driving puck
 * actually drives along a looping route, letting CHIP-DRIVING-001 + FOLLOW-001 be exercised without a
 * real trip. Toggle it from the Dev Catalog ("Conduciendo") or the Home "I'm driving" CTA. [DRIVE-SIM-001]
 */
class FakeLocationDataSource(
    private val runtime: DetectionRuntimeState? = null,
) : LocationDataSource {
    private val mockLocation = GpsPoint(
        latitude = 53.3518,
        longitude = -6.2619,
        accuracy = 8.5f,
        timestamp = Clock.System.now().toEpochMilliseconds(),
        speed = 0f,
    )

    override fun observeBalancedLocation(): Flow<GpsPoint> = flow {
        while (true) {
            emit(mockLocation.copy(timestamp = Clock.System.now().toEpochMilliseconds()))
            delay(3.seconds)
        }
    }

    override fun observeHighAccuracyLocation(): Flow<GpsPoint> = flow {
        while (true) {
            emit(mockLocation.copy(timestamp = Clock.System.now().toEpochMilliseconds()))
            delay(1.seconds)
        }
    }

    // Cruise sits between the 1 s HA tick and the 3 s balanced one. [DET-ADAPTIVE-SAMPLING-EARNS-ITS-FIXES-001]
    override fun observeCruiseLocation(): Flow<GpsPoint> = flow {
        while (true) {
            emit(mockLocation.copy(timestamp = Clock.System.now().toEpochMilliseconds()))
            delay(2.seconds)
        }
    }

    // No other apps in the mock world → the passive piggyback stream stays silent. [ROUTE-PASSIVE-FILL-001]
    override fun observePassiveLocation(): Flow<GpsPoint> = emptyFlow()

    override fun observeUiLocation(): Flow<UserLocationUi> {
        val rt = runtime ?: return staticUi()
        // While a trip is "running", drive the route; otherwise sit still. flatMapLatest swaps the
        // emitter the instant the flag flips, so toggling from the Dev Catalog reacts immediately.
        return rt.isRunning.flatMapLatest { running -> if (running) drivingUi() else staticUi() }
    }

    override suspend fun getLastKnownLocation(): GpsPoint =
        mockLocation.copy(timestamp = Clock.System.now().toEpochMilliseconds())

    /** Idle puck — parked over the fixed point, slowly spinning so its rotation is visible in mocks. */
    private fun staticUi(): Flow<UserLocationUi> = flow {
        var bearing = 0f
        while (true) {
            emit(
                UserLocationUi(
                    latitude = mockLocation.latitude,
                    longitude = mockLocation.longitude,
                    accuracy = mockLocation.accuracy,
                    speed = 0f,
                    bearingDegrees = bearing,
                ),
            )
            bearing = (bearing + 15f) % 360f
            delay(1.seconds)
        }
    }

    /** Walks a looping route around the start; heading follows the path so the puck points forward. */
    private fun drivingUi(): Flow<UserLocationUi> = flow {
        var leg = 0
        while (true) {
            val from = DRIVING_ROUTE[leg % DRIVING_ROUTE.size]
            val to = DRIVING_ROUTE[(leg + 1) % DRIVING_ROUTE.size]
            repeat(STEPS_PER_LEG) { step ->
                val t = step.toFloat() / STEPS_PER_LEG
                val lat = from.first + (to.first - from.first) * t
                val lon = from.second + (to.second - from.second) * t
                // Deterministic wander (~8 m) so the raw trail leaves the street and the map-matching
                // visibly snaps it back. [ROUTE-SNAP-001]
                val noiseLat = sin(leg * 2.3 + step * 0.9) * GPS_NOISE_DEG
                val noiseLon = cos(leg * 1.7 + step * 1.1) * GPS_NOISE_DEG
                emit(
                    UserLocationUi(
                        latitude = lat + noiseLat,
                        longitude = lon + noiseLon,
                        accuracy = mockLocation.accuracy,
                        speed = DRIVING_SPEED_MPS,
                        bearingDegrees = bearingTo(lat, lon, to.first, to.second),
                    ),
                )
                delay(STEP_DELAY_MS.milliseconds)
            }
            leg++
        }
    }

    private fun bearingTo(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val dLon = (lon2 - lon1) * PI / 180.0
        val l1 = lat1 * PI / 180.0
        val l2 = lat2 * PI / 180.0
        val y = sin(dLon) * cos(l2)
        val x = cos(l1) * sin(l2) - sin(l1) * cos(l2) * cos(dLon)
        return ((atan2(y, x) * 180.0 / PI + 360.0) % 360.0).toFloat()
    }

    private companion object {
        const val STEPS_PER_LEG = 10      // interpolated points between waypoints (smooth movement)
        const val STEP_DELAY_MS = 500L    // 2 emits/s — faster so the longer route builds quickly
        const val DRIVING_SPEED_MPS = 9f
        // GPS "noise" (degrees) added to each emitted fix. 0 = clean test of the snapping itself; a
        // small value (~3 m, 0.00003) makes the raw trail wander so matching visibly corrects it.
        const val GPS_NOISE_DEG = 0.0
        // A longer real-street circuit (~1.4 km) around Dublin, traced densely along
        // the visible OSM grid so the raw line already hugs streets and on-device map-matching only
        // refines it: Av. de Valencia → Av. Sanlúcar → a full loop of the Glorieta Juan de Austria
        // roundabout → the southern grid (C. San Juan) → back up. [ROUTE-SNAP-001]
        val DRIVING_ROUTE = listOf(
            53.3656 to -6.2568,  // Av. de Valencia (NE roundabout)
            53.3652 to -6.2574,
            53.3648 to -6.2582,
            53.3644 to -6.2591,
            53.3641 to -6.2600,
            53.3640 to -6.2609,  // onto Navan Road / Phoenix Park gate
            53.3637 to -6.2610,
            53.3633 to -6.2609,  // approach Glorieta Juan de Austria
            53.3630 to -6.2610,  // — roundabout, one loop CCW —
            53.3628 to -6.2613,
            53.3626 to -6.2615,
            53.3625 to -6.2612,
            53.3627 to -6.2609,
            53.3630 to -6.2611,  // — back to entry, then exit —
            53.3627 to -6.2616,  // exit SW
            53.3622 to -6.2618,
            53.3616 to -6.2614,  // C. San Juan
            53.3611 to -6.2607,
            53.3607 to -6.2598,
            53.3610 to -6.2588,
            53.3616 to -6.2580,
            53.3623 to -6.2574,
            53.3631 to -6.2570,
            53.3640 to -6.2568,
            53.3648 to -6.2566,  // back N toward the roundabout
            53.3655 to -6.2566,
        )
    }
}
