package com.rndeveloper.paparcar.data.parkings

import com.rndeveloper.paparcar.domain.parkings.FeeInfo
import com.rndeveloper.paparcar.domain.parkings.PublicParking
import com.rndeveloper.paparcar.domain.parkings.PublicParkingKind
import com.rndeveloper.paparcar.domain.parkings.PublicParkingsQuery
import com.rndeveloper.paparcar.domain.parkings.PublicParkingsResult
import com.rndeveloper.paparcar.domain.parkings.PublicParkingsSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the car-park stack can say **before** anybody answers.
 * [PARKINGS-A-REMEMBERED-MAP-ANSWERS-BEFORE-THE-NETWORK-001]
 *
 * The lane used to show a skeleton over a disk that already held this very zone, for however long
 * Overpass took — measured at 1,4 s warm, 4,7–7,7 s cold, and up to 40 s once the quota wait and the
 * mirror walk are counted. These tests pin the two halves that make reading it early safe:
 *
 * 1. **The live answer still wins.** Reading the disk early must not turn a live map into a stale
 *    one, which is the failure the old "only on failure" rule was protecting against.
 * 2. **A remembered answer always says it is one.** `rememberedAtMs` is what stops a surface from
 *    presenting month-old geography with the face of something fetched a second ago.
 */
class RememberedBeforeTheNetworkTest {

    private val lat = 40.4168
    private val lon = -3.7038
    private val query = PublicParkingsQuery(lat, lon, countryCode = "", radiusMeters = 1_500)

    private var now = 1_000_000L
    private val parkingDao = FakePublicParkingCacheDao()
    private val coverageDao = FakeOsmCoverageDao()
    private val disk = RoomPublicParkingsCache(parkingDao, coverageDao) { now }

    private fun parking(id: String) = PublicParking(
        id = id,
        name = "Car park $id",
        operator = null,
        kind = PublicParkingKind.UNDERGROUND,
        latitude = lat,
        longitude = lon,
        fee = FeeInfo.PAID,
    )

    private class Network(
        var answer: Result<List<PublicParking>> = Result.success(emptyList()),
    ) : PublicParkingsSource {
        var asked = 0
        override fun covers(countryCode: String) = true
        override suspend fun parkingsNear(query: PublicParkingsQuery): Result<PublicParkingsResult> {
            asked++
            return answer.map { PublicParkingsResult(it, rememberedAtMs = null) }
        }
    }

    // ── The disk layer ───────────────────────────────────────────────────────

    @Test
    fun `should_sayNothing_when_thisAreaWasNeverAskedAbout`() = runTest {
        val source = DiskBackedPublicParkingsSource(Network(), disk)

        assertNull(source.remembered(query))
    }

    @Test
    fun `should_sayWhatWasSavedAndWhen_when_theAreaIsOnDisk`() = runTest {
        val network = Network(Result.success(listOf(parking("a"), parking("b"))))
        val source = DiskBackedPublicParkingsSource(network, disk)
        source.parkingsNear(query)
        val savedAt = now

        now += 5 * 60_000
        val known = assertNotNull(source.remembered(query))

        assertEquals(listOf("a", "b"), known.parkings.map { it.id })
        assertEquals(savedAt, known.rememberedAtMs)
        // ⛔ It costs no request: that is the entire point of painting it while the live ask flies.
        assertEquals(1, network.asked)
    }

    /**
     * ⛔ **Today's answer still wins.** Reading the disk early changes what is shown WHILE asking,
     * never what is shown once the answer is in — the cheap version of this feature checks the disk
     * first "to save a request" and quietly turns a live map into a stale one.
     */
    @Test
    fun `should_stillPreferTheLiveAnswer_when_theDiskAlreadyHasTheArea`() = runTest {
        val network = Network(Result.success(listOf(parking("a"))))
        val source = DiskBackedPublicParkingsSource(network, disk)
        source.parkingsNear(query)

        now += 60_000
        network.answer = Result.success(listOf(parking("a"), parking("new")))
        val live = assertNotNull(source.parkingsNear(query).getOrNull())

        assertEquals(listOf("a", "new"), live.parkings.map { it.id })
        // A live answer carries no stamp — it says it is fresh by saying nothing.
        assertNull(live.rememberedAtMs)
        assertEquals(2, network.asked)
    }

    /** Unchanged by this ticket, and re-asserted because it is what the disk was built for. */
    @Test
    fun `should_fallBackToTheDisk_when_theLiveAnswerFails`() = runTest {
        val network = Network(Result.success(listOf(parking("a"))))
        val source = DiskBackedPublicParkingsSource(network, disk)
        source.parkingsNear(query)
        val savedAt = now

        now += 60_000
        network.answer = Result.failure(IllegalStateException("Overpass answered 504"))
        val served = assertNotNull(source.parkingsNear(query).getOrNull())

        assertEquals(listOf("a"), served.parkings.map { it.id })
        assertEquals(savedAt, served.rememberedAtMs)
    }

    /** ⛔ And a failure with nothing on disk stays the ORIGINAL failure, never "none here". */
    @Test
    fun `should_keepTheFailure_when_thereIsNothingToRememberEither`() = runTest {
        val network = Network(Result.failure(IllegalStateException("Overpass answered 504")))
        val source = DiskBackedPublicParkingsSource(network, disk)

        val result = source.parkingsNear(query)

        assertTrue(result.isFailure)
        assertEquals("Overpass answered 504", result.exceptionOrNull()?.message)
    }

    // ── The memory layer above it ────────────────────────────────────────────

    /**
     * ⛔ Memory first, disk second. Asking Room for something this object holds in a map would pay a
     * round-trip on every camera settle, for a list the very next call serves from the same entry.
     */
    @Test
    fun `should_answerFromMemory_when_itAlreadyHoldsTheArea_withoutTouchingTheDisk`() = runTest {
        val network = Network(Result.success(listOf(parking("a"))))
        val counting = CountingSource(DiskBackedPublicParkingsSource(network, disk))
        val source = CachingPublicParkingsSource(counting, clock = { now })
        source.parkingsNear(query)
        val readsAfterFetching = counting.rememberedCalls

        val known = assertNotNull(source.remembered(query))

        assertEquals(listOf("a"), known.parkings.map { it.id })
        assertEquals(readsAfterFetching, counting.rememberedCalls)
    }

    /** And when memory has nothing, the question goes down to the disk rather than dying here. */
    @Test
    fun `should_askTheDisk_when_memoryHasNothing`() = runTest {
        val network = Network(Result.success(listOf(parking("a"))))
        val backing = DiskBackedPublicParkingsSource(network, disk)
        backing.parkingsNear(query)

        // A brand-new memory layer: the process died, the disk did not.
        val source = CachingPublicParkingsSource(backing, clock = { now })
        val known = assertNotNull(source.remembered(query))

        assertEquals(listOf("a"), known.parkings.map { it.id })
        assertNotNull(known.rememberedAtMs)
    }

    private class CountingSource(private val delegate: PublicParkingsSource) : PublicParkingsSource {
        var rememberedCalls = 0
        override fun covers(countryCode: String) = delegate.covers(countryCode)
        override suspend fun parkingsNear(query: PublicParkingsQuery) = delegate.parkingsNear(query)
        override suspend fun remembered(query: PublicParkingsQuery): PublicParkingsResult? {
            rememberedCalls++
            return delegate.remembered(query)
        }
    }
}
