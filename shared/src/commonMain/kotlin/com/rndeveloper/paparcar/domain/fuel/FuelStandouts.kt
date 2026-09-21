package com.rndeveloper.paparcar.domain.fuel

import com.rndeveloper.paparcar.domain.usecase.fuel.NearbyFuelStation

/**
 * Which forecourt in a list deserves to be pointed at: the CHEAPEST and the NEAREST.
 * [FUEL-THE-CHEAPEST-AND-THE-NEAREST-WEAR-A-BADGE-001]
 *
 * A pure function and not a use case on purpose — its result never appears in a diagnostic and
 * never changes a pin: it only decides what a row says about itself. [DET-VERDICT-NOT-PREDICATE-001]
 *
 * ## Why "nearest" is not redundant with the order
 *
 * The list is sorted with `compareBy { price == null }.thenBy { distance }`: priced stations first,
 * each block by distance. So the first row is the nearest **that has a price**, and in a country
 * whose feed carries none — which is most of the world, and the reason the owner asked for this —
 * the genuinely nearest forecourt can sit far down the list with no price at all. The badge names
 * what the ordering cannot.
 *
 * ## What it refuses to claim
 *
 * - **No superlative over one candidate.** With a single station there is no "cheapest": there is
 *   just a station. Same for "nearest".
 * - **A tie SHARES the badge, it does not kill it.** ⛔ Esto era al revés y estaba mal medido: la
 *   primera versión exigía ser *estrictamente* más barata que todas las demás, con el argumento de
 *   que en un empate ninguna es *la* más barata. En un mapa real eso borra el badge casi siempre —
 *   los surtidores se copian el precio a la milésima, y en la captura de la review había dos a
 *   1,809 €, así que la gasolinera que era a la vez la más barata y la más cercana sólo llevaba
 *   «MÁS CERCANA». La verdad comprobable en el surtidor es que **las dos son las más baratas**, y
 *   eso es lo que se dice: el badge va a TODAS las que cotizan el mínimo.
 * - **Lo que sí se calla: un empate UNIVERSAL.** Si todas las gasolineras con precio cobran lo
 *   mismo, «la más barata» no distingue nada — es ruido en todas las filas, no información.
 * - **Distance ties do not happen in practice** (metres from a GPS fix), so the nearest stays
 *   single: si empataran, ninguna es la más cercana y el badge se cae.
 * - **No cheapest across currencies.** [Money] carries one, and near a border the same list can hold
 *   euros and pounds. 1.42 £/l is not cheaper than 1.65 €/l because the number is smaller, so a
 *   mixed list gets no cheapest badge at all. The nearest one still stands: metres are metres.
 */
data class FuelStandouts(
    /** `stationKey` de TODAS las que cotizan el mínimo — vacío cuando nadie puede reclamarlo.
     *  Un conjunto y no una clave: el empate se comparte, ver el ⛔ de arriba. */
    val cheapestKeys: Set<String> = emptySet(),
    /** `stationKey` of the closest one — null when there is nothing to be closer than. */
    val nearestKey: String? = null,
) {
    fun badgeFor(stationKey: String): FuelStandoutBadge? {
        val isCheapest = stationKey in cheapestKeys
        val isNearest = stationKey == nearestKey
        return when {
            // Una sola insignia cuando es las dos cosas: una fila es un nombre, no un marcador.
            isCheapest && isNearest -> FuelStandoutBadge.CHEAPEST_AND_NEAREST
            isCheapest -> FuelStandoutBadge.CHEAPEST
            isNearest -> FuelStandoutBadge.NEAREST
            else -> null
        }
    }

    /**
     * Orden de aparición: **sólo la más barata sube**; el resto no se mueve.
     * [FUEL-THE-CHEAPEST-AND-THE-NEAREST-WEAR-A-BADGE-001]
     *
     * ⛔ La más CERCANA no sube, y es deliberado: la lista pone las gasolineras CON PRECIO primero,
     * y la más cercana es muy a menudo una sin precio — subirla dejaba la lista encabezada por una
     * fila sin cifra, que es justo lo que ese orden existe para evitar. Su badge sigue nombrando lo
     * que el orden no puede decir; lo que no hace es reordenar la respuesta.
     */
    fun rankOf(stationKey: String): Int =
        if (stationKey in cheapestKeys) 0 else 1

    companion object {
        val NONE = FuelStandouts()
    }
}

/** What a row can wear. One badge per row, never two — a row is a name, not a scoreboard. */
enum class FuelStandoutBadge {
    CHEAPEST,
    NEAREST,
    CHEAPEST_AND_NEAREST,
}

/**
 * Reads [FuelStandouts] off a list. Order-independent: it never assumes the caller sorted anything,
 * because the sort is a presentation choice and this is not.
 */
fun List<NearbyFuelStation>.fuelStandouts(): FuelStandouts {
    if (size < 2) return FuelStandouts.NONE

    val priced = filter { it.price != null }
    val oneCurrency = priced.map { it.price!!.perUnit.currency }.distinct().size == 1
    val amounts = priced.map { it.price!!.perUnit.amount }
    val cheapestKeys = when {
        !oneCurrency -> emptySet()
        priced.size < 2 -> emptySet()
        // Empate universal: «la más barata» no distingue nada, así que no se dice.
        amounts.distinct().size < 2 -> emptySet()
        else -> {
            val min = amounts.min()
            priced.filter { it.price!!.perUnit.amount == min }.map { it.stationKey }.toSet()
        }
    }
    val nearest = minByOrNull { it.distanceMeters }
        ?.takeIf { winner ->
            none { it.stationKey != winner.stationKey && it.distanceMeters <= winner.distanceMeters }
        }

    return FuelStandouts(cheapestKeys = cheapestKeys, nearestKey = nearest?.stationKey)
}
