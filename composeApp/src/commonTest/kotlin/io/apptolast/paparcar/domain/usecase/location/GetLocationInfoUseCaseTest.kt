package io.apptolast.paparcar.domain.usecase.location

import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.LocationInfo
import io.apptolast.paparcar.domain.model.PlaceCategory
import io.apptolast.paparcar.domain.model.PlaceInfo
import io.apptolast.paparcar.fakes.FakeLocationInfoRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GetLocationInfoUseCaseTest {

    private val address = AddressInfo(street = "Calle Mayor", city = "Madrid", region = null, country = "ES")
    private val place = PlaceInfo(name = "El Corte Inglés", category = PlaceCategory.MALL)

    @Test
    fun `should_forward_repository_flow_to_caller`() = runTest {
        val info = LocationInfo(address, null)
        val repo = FakeLocationInfoRepository(info)
        val useCase = GetLocationInfoUseCase(repo)

        val result = useCase(40.416775, -3.703790).toList()

        assertEquals(listOf(info), result)
    }

    @Test
    fun `should_forward_multi_emission_flow`() = runTest {
        val addressOnly = LocationInfo(address, null)
        val withPlace = LocationInfo(address, place)
        val repo = object : io.apptolast.paparcar.domain.repository.LocationInfoRepository {
            override fun getLocationInfo(lat: Double, lon: Double) = flowOf(addressOnly, withPlace)
        }
        val useCase = GetLocationInfoUseCase(repo)

        val result = useCase(40.416775, -3.703790).toList()

        assertEquals(listOf(addressOnly, withPlace), result)
    }
}
