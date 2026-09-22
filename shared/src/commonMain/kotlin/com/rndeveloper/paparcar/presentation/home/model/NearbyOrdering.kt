package com.rndeveloper.paparcar.presentation.home.model

import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.model.SpotStatus
import com.rndeveloper.paparcar.domain.fuel.fuelStandouts
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
 * - **Free spots: confirmed first, nearest first inside each group.** [HOME-A-LANE-FRAMES-ITS-SETTLED-FIRST-ROW-001]
 */
object NearbyOrdering {

    /**
     * Las plazas del carril, **en el orden en que se leen**: las confirmadas antes que las que
     * están sin confirmar, y dentro de cada grupo la que tienes más cerca.
     * [HOME-A-LANE-FRAMES-ITS-SETTLED-FIRST-ROW-001]
     *
     * ⛔ Este orden vivía escrito a mano en `HomeState.filteredNearbySpots()` y era **sólo** el
     * `sortedBy { PROVISIONAL }`: dentro de cada grupo mandaba el orden en que contestó el feed. O
     * sea que el carril del PRODUCTO era el único de los tres sin criterio de lectura, y su primera
     * fila —la que encuadra la cámara al cambiar de carril y por la que empieza el stepper ‹ / ›—
     * era una plaza cualquiera. Issue #124.
     *
     * Se mide desde el USUARIO, como las otras dos y como la propia fila de plaza. **Sin fix no se
     * ordena**: una distancia que no se ha medido no puede decidir qué va primero, y el orden de la
     * fuente es tan bueno como cualquier invento. El agrupado por estado sí se aplica siempre — no
     * necesita medir nada.
     *
     * ⛔ La distancia NO desempata el estado: una plaza sin confirmar a diez metros sigue detrás de
     * una confirmada a un kilómetro. Lo que decide el orden primero es de qué nos fiamos, igual que
     * antes de este ticket. [DET-HANDOFF-NOT-MANUAL-001 §B.3]
     */
    fun spots(spots: List<Spot>, user: GpsPoint?): List<Spot> {
        val unconfirmedLast = compareBy<Spot> { it.status == SpotStatus.PROVISIONAL }
        // `sortedWith` es estable, así que sin usuario el orden de la fuente sobrevive dentro de
        // cada grupo — exactamente lo que hacía el `sortedBy` al que sustituye.
        val order = user?.let { fix ->
            unconfirmedLast.thenBy { spot ->
                haversineMeters(fix.latitude, fix.longitude, spot.location.latitude, spot.location.longitude)
            }
        } ?: unconfirmedLast
        return spots.sortedWith(order)
    }

    /**
     * El CONJUNTO: las mismas gasolineras, cotizadas para el coche que conduces y medidas desde el
     * usuario, **en el orden en que contestó la fuente**.
     *
     * Existe separado de [fuelStations] porque *qué gasolineras hay* y *en qué orden se leen* son
     * dos preguntas, y sólo la primera es del mapa. Un pin no tiene fila ni scroll: reordenarlo no
     * se ve. [MAP-THE-MAP-READS-THE-SET-NOT-THE-ORDER-001]
     */
    fun fuelStationsPriced(
        stations: List<NearbyFuelStation>,
        pricedFor: FuelType?,
        user: GpsPoint?,
    ): List<NearbyFuelStation> =
        stations.map { nearby ->
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

    /** El conjunto de [fuelStationsPriced], en el ORDEN en que lo lee el carril. */
    fun fuelStations(
        stations: List<NearbyFuelStation>,
        pricedFor: FuelType?,
        user: GpsPoint?,
        /** Qué pregunta está haciendo el usuario. [FUEL-THE-LANE-IS-FILTERED-BY-WHAT-YOU-BURN-001] */
        sort: FuelLaneSort = FuelLaneSort.NEAREST,
    ): List<NearbyFuelStation> =
        fuelStationsPriced(stations, pricedFor, user)
            .let { priced ->
                // ⛔ La MÁS BARATA sube al principio (review de #37: «debería haber también para
                // barato y ordenado arriba»). Sin esto puede vivir a diez filas de scroll: la lista
                // ordena por distancia, y el precio no tiene nada que ver con lo cerca que esté. Un
                // badge que hay que buscar no es un badge. La más CERCANA no sube — ver el ⛔ de
                // `FuelStandouts.rankOf`.
                //
                // El resto del orden NO cambia — dentro de cada grupo siguen mandando «con precio
                // primero» y la distancia —, así que la lista sigue siendo la de siempre con dos
                // filas adelantadas. `fuelStandouts()` es puro y ajeno al orden, así que da lo mismo
                // aquí que en la fila que pinta el badge: las dos preguntan sobre la misma lista.
                // [FUEL-THE-CHEAPEST-AND-THE-NEAREST-WEAR-A-BADGE-001]
                val standouts = priced.fuelStandouts()
                when (sort) {
                    // ⛔ Con el orden por PRECIO el adelanto de la más barata no se aplica: ya es la
                    // primera por definición, y sumar el rango encima haría que dos reglas dijeran
                    // lo mismo — el día que una cambie, discrepan.
                    FuelLaneSort.CHEAPEST -> priced.sortedWith(
                        compareBy<NearbyFuelStation> { it.price == null }
                            .thenBy { it.price?.perUnit?.amount ?: Double.MAX_VALUE }
                            // Mismo precio (que es lo normal: se copian a la milésima) → la de al lado.
                            .thenBy { it.distanceMeters },
                    )
                    FuelLaneSort.NEAREST -> priced.sortedWith(
                        compareBy<NearbyFuelStation> { standouts.rankOf(it.stationKey) }
                            .thenBy { it.price == null }
                            .thenBy { it.distanceMeters },
                    )
                }
            }

    /** El CONJUNTO de parkings, medido desde el usuario y en el orden de la fuente. Mismo reparto
     *  que en gasolineras: el mapa lee esto, el carril lee [publicParkings].
     *  [MAP-THE-MAP-READS-THE-SET-NOT-THE-ORDER-001] */
    fun publicParkingsMeasured(
        parkings: List<NearbyPublicParking>,
        user: GpsPoint?,
    ): List<NearbyPublicParking> =
        parkings.map { nearby ->
            user?.let {
                nearby.copy(
                    distanceMeters = haversineMeters(
                        it.latitude, it.longitude, nearby.parking.latitude, nearby.parking.longitude,
                    ),
                )
            } ?: nearby
        }

    fun publicParkings(
        parkings: List<NearbyPublicParking>,
        user: GpsPoint?,
    ): List<NearbyPublicParking> =
        publicParkingsMeasured(parkings, user).sortedBy { it.distanceMeters }
}
