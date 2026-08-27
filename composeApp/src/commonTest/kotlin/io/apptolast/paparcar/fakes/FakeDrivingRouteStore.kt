package io.apptolast.paparcar.fakes

import io.apptolast.paparcar.domain.detection.DrivingRoute
import io.apptolast.paparcar.domain.detection.ports.DrivingRouteStore
import io.apptolast.paparcar.domain.model.GpsPoint

/** In-memory [DrivingRouteStore] for tests. Preload via [initial] to simulate a route the service
 *  recorded and persisted before a cold restart. Uses the real [DrivingRoute] accumulation rule. */
class FakeDrivingRouteStore(initial: List<GpsPoint> = emptyList()) : DrivingRouteStore {

    private var route: List<GpsPoint> = initial

    override fun append(point: GpsPoint) {
        route = DrivingRoute.append(route, point)
    }

    override fun points(): List<GpsPoint> = route

    override fun clear() {
        route = emptyList()
    }
}
