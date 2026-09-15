package com.rndeveloper.paparcar.presentation.home.model

import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.usecase.fuel.NearbyFuelStation
import com.rndeveloper.paparcar.domain.usecase.parkings.NearbyPublicParking
import com.rndeveloper.paparcar.domain.util.haversineMeters

/**
 * How a complement's answer is SHOWN — the one projection the lane, the map, the peek, its stepper
 * and the selection all read. [NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001]
 *
 * Pure on purpose: the order a list walks is a decision a test must be able to pin without a
 * ViewModel.
 *
 * - **Distance is measured from the USER**, exactly like a free-spot row. The use case measures from
 *   the point it was asked about — the settled camera — and a row that says "300 m" from a point on
 *   the map nobody is standing on reads as a lie. With no fix yet, the asked point is all there is.
 * - **Fuel: priced stations first**, then nearest first inside each group (user decision, 13-09).
 *   The price shown is resolved here from the forecourt's own prices for the car being driven,
 *   never inherited from whichever request fetched the station.
 *   [MAP-A-PIN-IS-PRICED-FOR-THE-CAR-YOU-DRIVE-001]
 * - **Car parks: nearest first**, nothing else — OSM carries no price figure for them, and "fee
 *   known" was explicitly not made a priority.
 */
object NearbyOrdering {

    fun fuelStations(
        stations: List<NearbyFuelStation>,
        pricedFor: FuelType?,
        user: GpsPoint?,
    ): List<NearbyFuelStation> =
        stations
            .map { nearby ->
                nearby.copy(
                    // Through the pump product: a forecourt is indexed by what it SELLS, and «for
                    // the car being driven» includes the hybrid that buys petrol. Idempotent, so a
                    // caller that already mapped loses nothing. [FUEL-A-HYBRID-FILLS-UP-WITH-PETROL-001]
                    price = pricedFor?.let { nearby.station.prices[it.pumpProduct] },
                    distanceMeters = user?.let {
                        haversineMeters(it.latitude, it.longitude, nearby.station.latitude, nearby.station.longitude)
                    } ?: nearby.distanceMeters,
                )
            }
            .sortedWith(compareBy<NearbyFuelStation> { it.price == null }.thenBy { it.distanceMeters })

    fun publicParkings(
        parkings: List<NearbyPublicParking>,
        user: GpsPoint?,
    ): List<NearbyPublicParking> =
        parkings
            .map { nearby ->
                user?.let {
                    nearby.copy(
                        distanceMeters = haversineMeters(
                            it.latitude, it.longitude, nearby.parking.latitude, nearby.parking.longitude,
                        ),
                    )
                } ?: nearby
            }
            .sortedBy { it.distanceMeters }
}
