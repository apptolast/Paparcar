@file:OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)

package io.apptolast.paparcar.data.datasource

import io.apptolast.paparcar.domain.detection.DetectionRuntimeState
import io.apptolast.paparcar.domain.location.LocationDataSource
import io.apptolast.paparcar.domain.location.UserLocationUi
import io.apptolast.paparcar.domain.model.GpsPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
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
        latitude = 36.5928,
        longitude = -6.2319,
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
        // A longer real-street circuit (~1.4 km) around El Puerto de Santa María, traced densely along
        // the visible OSM grid so the raw line already hugs streets and on-device map-matching only
        // refines it: Av. de Valencia → Av. Sanlúcar → a full loop of the Glorieta Juan de Austria
        // roundabout → the southern grid (C. San Juan) → back up. [ROUTE-SNAP-001]
        val DRIVING_ROUTE = listOf(
            36.6066 to -6.2268,  // Av. de Valencia (NE roundabout)
            36.6062 to -6.2274,
            36.6058 to -6.2282,
            36.6054 to -6.2291,
            36.6051 to -6.2300,
            36.6050 to -6.2309,  // onto Av. Sanlúcar / Plaza del Jardinero
            36.6047 to -6.2310,
            36.6043 to -6.2309,  // approach Glorieta Juan de Austria
            36.6040 to -6.2310,  // — roundabout, one loop CCW —
            36.6038 to -6.2313,
            36.6036 to -6.2315,
            36.6035 to -6.2312,
            36.6037 to -6.2309,
            36.6040 to -6.2311,  // — back to entry, then exit —
            36.6037 to -6.2316,  // exit SW
            36.6032 to -6.2318,
            36.6026 to -6.2314,  // C. San Juan
            36.6021 to -6.2307,
            36.6017 to -6.2298,
            36.6020 to -6.2288,
            36.6026 to -6.2280,
            36.6033 to -6.2274,
            36.6041 to -6.2270,
            36.6050 to -6.2268,
            36.6058 to -6.2266,  // back N toward the roundabout
            36.6065 to -6.2266,
        )
    }
}
