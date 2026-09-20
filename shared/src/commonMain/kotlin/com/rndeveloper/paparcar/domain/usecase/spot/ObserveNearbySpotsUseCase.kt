package com.rndeveloper.paparcar.domain.usecase.spot

import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.repository.SpotRepository
import kotlinx.coroutines.flow.Flow

class ObserveNearbySpotsUseCase(private val spotRepository: SpotRepository) {

    operator fun invoke(location: GpsPoint, radiusMeters: Double): Flow<List<Spot>> {
        return spotRepository.observeNearbySpots(location, radiusMeters)
    }

    companion object {
        /**
         * Cuánto alcanza la suscripción de plazas alrededor de donde MIRA la cámara.
         * [NEARBY-A-DELIBERATE-LOOK-IS-NOT-A-PAN-001]
         *
         * 5 km — el mismo alcance que las gasolineras, que era el mayor de los tres carriles. Eran
         * 2 km, y en el sitio donde más se nota: el producto de la app son las plazas, y el carril
         * que menos lejos veía era justo ése. Pedido en la issue #44.
         */
        const val DEFAULT_SEARCH_RADIUS_METERS = 5000.0
    }
}
