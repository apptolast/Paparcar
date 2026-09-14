package com.rndeveloper.paparcar.domain.usecase.location

import com.rndeveloper.paparcar.domain.model.AddressAndPlace
import com.rndeveloper.paparcar.domain.repository.AddressAndPlaceRepository
import kotlinx.coroutines.flow.Flow

class GetAddressAndPlaceUseCase(private val repository: AddressAndPlaceRepository) {
    /**
     * [persisting] = "I am going to store this answer, not paint it and forget it". See
     * [AddressAndPlaceRepository.getAddressAndPlace]. Default false: most callers are decorating a
     * map. [POI-A-FAILED-LOOKUP-IS-NOT-AN-ANSWER-001]
     */
    operator fun invoke(
        lat: Double,
        lon: Double,
        persisting: Boolean = false,
    ): Flow<AddressAndPlace> =
        repository.getAddressAndPlace(lat, lon, persisting)
}
