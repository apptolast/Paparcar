package io.apptolast.paparcar.domain.usecase.location

import io.apptolast.paparcar.domain.model.AddressAndPlace
import io.apptolast.paparcar.domain.repository.AddressAndPlaceRepository
import kotlinx.coroutines.flow.Flow

class GetAddressAndPlaceUseCase(private val repository: AddressAndPlaceRepository) {
    operator fun invoke(lat: Double, lon: Double): Flow<AddressAndPlace> =
        repository.getAddressAndPlace(lat, lon)
}
