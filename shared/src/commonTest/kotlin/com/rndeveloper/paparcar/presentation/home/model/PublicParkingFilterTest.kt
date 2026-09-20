package com.rndeveloper.paparcar.presentation.home.model

import com.rndeveloper.paparcar.domain.parkings.FeeInfo
import com.rndeveloper.paparcar.domain.parkings.PublicParking
import com.rndeveloper.paparcar.domain.parkings.PublicParkingKind
import com.rndeveloper.paparcar.domain.usecase.parkings.NearbyPublicParking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * El carril de parkings filtra por lo que SABEMOS.
 * [PARKINGS-THE-LANE-FILTERS-BY-WHAT-WE-KNOW-001]
 */
class PublicParkingFilterTest {

    private fun parking(
        id: String,
        fee: FeeInfo = FeeInfo.UNKNOWN,
        kind: PublicParkingKind = PublicParkingKind.UNKNOWN,
    ) = NearbyPublicParking(
        parking = PublicParking(
            id = id, name = id, operator = null, kind = kind,
            latitude = 0.0, longitude = 0.0, fee = fee,
        ),
        distanceMeters = 100.0,
    )

    private val all = listOf(
        parking("free", fee = FeeInfo.FREE),
        parking("paid", fee = FeeInfo.PAID),
        parking("unknown"),
        parking("underground", kind = PublicParkingKind.UNDERGROUND),
        parking("multi", fee = FeeInfo.PAID, kind = PublicParkingKind.MULTI_STOREY),
    )

    @Test
    fun should_keepEverything_when_thereIsNoFilter() {
        assertEquals(all.size, all.filteredBy(null).size)
    }

    /**
     * ⛔ EL TESTIGO. La tarifa DESCONOCIDA es el 71% de los parkings, y no cuenta ni como gratis ni
     * como de pago: decir «gratuito» de lo que nadie ha comprobado es el engaño que el marcador ya
     * se negó a hacer.
     */
    @Test
    fun should_leaveTheUnknownFeeOut_when_filteringByFee() {
        assertEquals(listOf("free"), all.filteredBy(PublicParkingFilter.FREE).map { it.parking.id })
        assertEquals(
            listOf("paid", "multi"),
            all.filteredBy(PublicParkingFilter.PAID).map { it.parking.id },
        )
    }

    @Test
    fun should_keepOnlyRoofedOnes_when_filteringByCovered() {
        assertEquals(
            listOf("underground", "multi"),
            all.filteredBy(PublicParkingFilter.COVERED).map { it.parking.id },
            "subterráneo y varias plantas tienen techo; superficie y desconocido no lo declaran",
        )
    }
}
