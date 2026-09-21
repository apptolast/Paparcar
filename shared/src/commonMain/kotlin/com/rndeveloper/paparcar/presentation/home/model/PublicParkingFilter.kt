package com.rndeveloper.paparcar.presentation.home.model

import com.rndeveloper.paparcar.domain.parkings.FeeInfo
import com.rndeveloper.paparcar.domain.parkings.PublicParkingKind
import com.rndeveloper.paparcar.domain.usecase.parkings.NearbyPublicParking

/**
 * Por qué se puede filtrar el carril de parkings — **y sólo por lo que sabemos de verdad**.
 * [PARKINGS-THE-LANE-FILTERS-BY-WHAT-WE-KNOW-001]
 *
 * ⛔ **No hay «más barato», y no es un olvido.** Un parking público no tiene precio en nuestros
 * datos: OpenStreetMap trae `fee` (sí/no) y un `charge` en texto libre («1.50 EUR/hour», «first 30
 * min free») con cobertura residual. Ordenar por un precio que no existe exigiría inventárselo, y
 * un orden inventado es peor que no ofrecerlo. Lo que sí sabemos es **si se paga**.
 *
 * ⛔ **Tampoco hay «cabe mi coche»**, que es el filtro que de verdad hace falta para una furgoneta:
 * el parking trae su gálibo (`maxHeightMeters`, y sólo el 0-7% lo declara) pero **el coche no trae
 * su altura** — `CarbodyType` describe anchura e identidad visual, no altura. Compararlos exigiría
 * inventar una altura por carrocería. Queda apuntado como ticket propio.
 *
 * Y por eso tampoco hay chips de ORDEN aquí, al revés que en gasolineras: sin precio, el único
 * orden que existe es la distancia, y ése ya es el de siempre.
 */
enum class PublicParkingFilter {
    /** Gratuito según la fuente. ⛔ Nunca incluye los de tarifa DESCONOCIDA: el 71% no la declara,
     *  y meterlos aquí sería prometer gratis lo que nadie ha comprobado. */
    FREE,

    /** De pago según la fuente. Misma regla: el desconocido no cuenta como de pago. */
    PAID,

    /** Techado — subterráneo o de varias plantas. Lo que se busca cuando llueve o hay granizo. */
    COVERED,
    ;

    fun matches(nearby: NearbyPublicParking): Boolean = when (this) {
        FREE -> nearby.parking.fee == FeeInfo.FREE
        PAID -> nearby.parking.fee == FeeInfo.PAID
        COVERED -> nearby.parking.kind in COVERED_KINDS
    }

    private companion object {
        val COVERED_KINDS = setOf(PublicParkingKind.UNDERGROUND, PublicParkingKind.MULTI_STOREY)
    }
}

/** La lista tal y como la deja el filtro. `null` = sin filtro, que es el estado normal. */
fun List<NearbyPublicParking>.filteredBy(active: PublicParkingFilter?): List<NearbyPublicParking> =
    if (active == null) this else filter { active.matches(it) }
