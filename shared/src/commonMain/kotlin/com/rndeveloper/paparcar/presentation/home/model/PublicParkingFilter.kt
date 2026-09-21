package com.rndeveloper.paparcar.presentation.home.model

import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.parkings.FeeInfo
import com.rndeveloper.paparcar.domain.parkings.PublicParkingKind
import com.rndeveloper.paparcar.domain.usecase.parkings.NearbyPublicParking
import com.rndeveloper.paparcar.domain.vehicle.ClearanceVerdict
import com.rndeveloper.paparcar.domain.vehicle.clearanceUnder

/**
 * Por qué se puede filtrar el carril de parkings — **y sólo por lo que sabemos de verdad**.
 * [PARKINGS-THE-LANE-FILTERS-BY-WHAT-WE-KNOW-001]
 *
 * ⛔ **No hay «más barato», y no es un olvido.** Un parking público no tiene precio en nuestros
 * datos: OpenStreetMap trae `fee` (sí/no) y un `charge` en texto libre («1.50 EUR/hour», «first 30
 * min free») con cobertura residual. Ordenar por un precio que no existe exigiría inventárselo, y
 * un orden inventado es peor que no ofrecerlo. Lo que sí sabemos es **si se paga**.
 *
 * ⛔ **Y el del gálibo se llama por lo que HACE, no por lo que uno querría que hiciera**
 * [VEH-A-HEIGHT-UNKNOWN-IS-NOT-A-FIT-001]. «Cabe mi coche» suena mejor y sería mentira: sólo el
 * 0-7 % de los parkings declara `maxHeightMeters`, así que enseñar únicamente los que PODEMOS
 * prometer dejaría la lista casi vacía y el filtro sería inútil justo para la furgoneta que lo
 * necesita. Lo que sí podemos afirmar es lo contrario — que uno NO cabe — así que el chip descarta
 * ésos y deja pasar lo desconocido, y su palabra lo dice.
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

    /**
     * Fuera los que tienen el gálibo DEMASIADO BAJO para tu coche.
     * [VEH-A-HEIGHT-UNKNOWN-IS-NOT-A-FIT-001]
     *
     * ⛔ Descarta, no promete. Un parking sin gálibo declarado —la inmensa mayoría— **se queda**:
     * no sabemos que no quepas, y esconderlo sería inventarse un «no» igual de falso que un «sí».
     */
    NO_LOW_CLEARANCE,
    ;

    fun matches(nearby: NearbyPublicParking, vehicle: Vehicle?): Boolean = when (this) {
        FREE -> nearby.parking.fee == FeeInfo.FREE
        PAID -> nearby.parking.fee == FeeInfo.PAID
        COVERED -> nearby.parking.kind in COVERED_KINDS
        // Sin coche activo no hay contra qué medir, así que no se descarta nada: un filtro que no
        // puede preguntar no contesta que no.
        NO_LOW_CLEARANCE ->
            vehicle?.clearanceUnder(nearby.parking.maxHeightMeters) != ClearanceVerdict.DOES_NOT_FIT
    }

    private companion object {
        val COVERED_KINDS = setOf(PublicParkingKind.UNDERGROUND, PublicParkingKind.MULTI_STOREY)
    }
}

/** La lista tal y como la deja el filtro. `null` = sin filtro, que es el estado normal. */
fun List<NearbyPublicParking>.filteredBy(
    active: PublicParkingFilter?,
    /** El coche con el que se mide el gálibo. Null = no hay ninguno activo. */
    vehicle: Vehicle? = null,
): List<NearbyPublicParking> =
    if (active == null) this else filter { active.matches(it, vehicle) }
