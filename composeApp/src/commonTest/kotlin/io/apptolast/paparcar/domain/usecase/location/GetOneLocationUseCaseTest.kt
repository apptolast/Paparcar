@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.domain.usecase.location

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.fakes.FakeLocationDataSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class GetOneLocationUseCaseTest {

    private val fakeDataSource = FakeLocationDataSource()
    private val useCase = GetOneLocationUseCase(fakeDataSource)

    private val point = GpsPoint(
        latitude = 40.416775,
        longitude = -3.703790,
        accuracy = 10f,
        timestamp = 1_000L,
        speed = 0f,
    )

    @Test
    fun `should return location when emitted within timeout`() = runTest {
        fakeDataSource.emitBalanced(point)
        val result = useCase()
        assertEquals(point, result)
    }

    @Test
    fun `should return null when timeout elapses before emission`() = runTest {
        val result = useCase()
        assertNull(result)
    }

    @Test
    fun `should return first emission only`() = runTest {
        val second = point.copy(latitude = 41.0)
        fakeDataSource.emitBalanced(point)
        fakeDataSource.emitBalanced(second)
        val result = useCase()
        assertEquals(point, result)
    }
}
