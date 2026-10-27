package com.rndeveloper.paparcar.fakes.data.repository

import com.rndeveloper.paparcar.domain.parkings.FeeInfo
import com.rndeveloper.paparcar.domain.parkings.PublicParking
import com.rndeveloper.paparcar.domain.parkings.PublicParkingKind
import com.rndeveloper.paparcar.domain.parkings.PublicParkingsQuery
import com.rndeveloper.paparcar.domain.parkings.PublicParkingsResult
import com.rndeveloper.paparcar.domain.parkings.PublicParkingsSource

/**
 * Car parks for the mock stack — no network, wherever the fake GPS says you are.
 * [PARKINGS-ON-THE-MAP-001]
 *
 * ⛔ Declared in `MockModule` in the same breath as it is written: the mock graph carries **no
 * `dataModule`**, so a port that only lives there compiles in both flavours and dies in mock, at
 * runtime, on the screen that needs it. That is what `FuelStationsSource` did to Home.
 *
 * The four entries are chosen to cover the four sentences the UI must be able to say, because the
 * real world says all four: named and priced, named and free, **nameless** (the majority worldwide —
 * only 2,6% of Cuenca's car parks carry a name), and known-nothing beyond its kind.
 */
class FakePublicParkingsSource(
    /**
     * When set, the fake answers as the DISK would: the same car parks, plus the moment the app last
     * managed to ask. [CACHE-A-MAP-MUST-NOT-EMPTY-ON-COLD-START-001]
     *
     * ⛔ A knob rather than a second fake, because the remembered answer is not a different SHAPE of
     * answer — it is the same list wearing its age. Building it as a separate fake would let the two
     * drift, which is how a state ends up impossible to reach in the gallery while being reachable
     * in the field.
     */
    private val rememberedAtMs: Long? = null,
) : PublicParkingsSource {

    override fun covers(countryCode: String): Boolean = true

    override suspend fun parkingsNear(query: PublicParkingsQuery): Result<PublicParkingsResult> =
        Result.success(
            PublicParkingsResult(
            parkings = listOf(
                parking(
                    query = query,
                    id = "mock-p1",
                    name = "Parking Plaza Mayor",
                    operator = "EMT",
                    kind = PublicParkingKind.UNDERGROUND,
                    northMeters = 260.0,
                    eastMeters = 140.0,
                    capacity = 420,
                    fee = FeeInfo.PAID,
                    maxHeightMeters = 2.1,
                    openingHours = "24/7",
                ),
                parking(
                    query = query,
                    id = "mock-p2",
                    name = "Parking Las Ventas",
                    operator = null,
                    kind = PublicParkingKind.SURFACE,
                    northMeters = -520.0,
                    eastMeters = 380.0,
                    capacity = 90,
                    fee = FeeInfo.FREE,
                    maxHeightMeters = null,
                    openingHours = null,
                ),
                // Nameless: it must still read like a place, titled by what it IS.
                parking(
                    query = query,
                    id = "mock-p3",
                    name = null,
                    operator = null,
                    kind = PublicParkingKind.MULTI_STOREY,
                    northMeters = 800.0,
                    eastMeters = -450.0,
                    capacity = null,
                    fee = FeeInfo.UNKNOWN,
                    maxHeightMeters = 1.9,
                    openingHours = null,
                ),
                // Knows nothing but its kind and where it is — no price claim of any sort.
                parking(
                    query = query,
                    id = "mock-p4",
                    name = null,
                    operator = "City Hall Car Park",
                    kind = PublicParkingKind.SURFACE,
                    northMeters = -1_100.0,
                    eastMeters = -700.0,
                    capacity = null,
                    fee = FeeInfo.UNKNOWN,
                    maxHeightMeters = null,
                    openingHours = null,
                ),
            ),
                rememberedAtMs = rememberedAtMs,
            ),
        )

    @Suppress("LongParameterList")
    private fun parking(
        query: PublicParkingsQuery,
        id: String,
        name: String?,
        operator: String?,
        kind: PublicParkingKind,
        northMeters: Double,
        eastMeters: Double,
        capacity: Int?,
        fee: FeeInfo,
        maxHeightMeters: Double?,
        openingHours: String?,
    ) = PublicParking(
        id = id,
        name = name,
        operator = operator,
        kind = kind,
        latitude = query.latitude + northMeters / METERS_PER_DEGREE,
        longitude = query.longitude + eastMeters / METERS_PER_DEGREE,
        capacity = capacity,
        fee = fee,
        maxHeightMeters = maxHeightMeters,
        openingHours = openingHours,
    )

    private companion object {
        /** Enough to spread pins over a couple of km at any latitude the Dev Catalog uses. */
        const val METERS_PER_DEGREE = 111_320.0
    }
}
