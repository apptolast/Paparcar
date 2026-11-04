package com.rndeveloper.paparcar.domain.nearby

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.usecase.fuel.NearbyFuelStation
import com.rndeveloper.paparcar.domain.usecase.parkings.NearbyPublicParking
import com.rndeveloper.paparcar.domain.util.haversineMeters

/**
 * Qué merece estar en el PRIMER VISTAZO de Home, antes de que el usuario toque nada.
 * [HOME-THE-FIRST-GLANCE-SPEAKS-FOR-THE-CAR-001]
 *
 * **Una función pura, no un caso de uso, y es una corrección del plan de diseño** — que la llamaba
 * `ResolveNearbyNotablesUseCase`. La regla del proyecto reserva un caso de uso para un **veredicto**
 * citable en un diagnóstico (`detectionPath`, `outcome`, `armEvidence`, `sessionOutcome`), y dice
 * expresamente que crear uno se justifica *«cuando hay lógica inalcanzable para los tests»*. Esto es
 * aritmética sobre tres listas que ya existen, perfectamente alcanzable. El precedente más cercano
 * —[com.rndeveloper.paparcar.domain.fuel.FuelStandouts], que también decide qué dice una fila del
 * carril— es una función pura por el mismo motivo y lo documenta.
 * [DET-VERDICT-NOT-PREDICATE-001]
 *
 * ## Las tres decisiones, y ninguna es de maquetación
 *
 * ⛔ **UNO por sujeto, como máximo.** Dos plazas en el primer nivel son una lista, y la lista ya
 * existe un nivel más abajo.
 *
 * ⛔ **Orden FIJO** ([NearbySubject] los declara en él), nunca por «cuán notable es». Reordenar el
 * primer vistazo según los datos del momento es lo que hace impredecible una home: el usuario
 * aprende *dónde* está cada cosa, no *cuándo*. Y es el mismo orden que la fila de pestañas, así que
 * las dos superficies no pueden contradecirse.
 *
 * ⛔ **Un umbral POR SUJETO**, porque «notable» no significa lo mismo para los tres. Ver [notable].
 *
 * ⛔ **Lista vacía es una respuesta.** La superficie que la lee no dibuja sección: ni título, ni
 * hueco, ni caja diciendo que no hay nada. Cada carril ya dice lo suyo en su sitio, y repetirlo
 * arriba es llenar el primer nivel con la noticia de que no hay noticias.
 */
enum class NearbySubject {
    /** La plaza que la comunidad acaba de dejar libre. Es el producto. */
    SPOT,

    /** Un parking público: una instalación de un tercero en la que entras a aparcar. */
    CAR_PARK,

    /** Dónde repostar. */
    FUEL,
}

/**
 * El item que el primer vistazo señala, con lo justo para abrirlo.
 *
 * ⛔ Lleva el **id** y no el objeto: quien pinta la tarjeta lee el sujeto de la MISMA lista
 * proyectada que pinta el carril, así que la tarjeta y la fila no pueden nombrar cosas distintas —
 * que es lo que pasaría si esta función devolviera una copia.
 */
data class NearbyNotable(
    val subject: NearbySubject,
    val id: String,
    /** Metros hasta el item, o null cuando no hay desde dónde medir. ⛔ Null NO es cero. */
    val distanceMeters: Double?,
)

/**
 * Los notables de esta respuesta, en orden fijo y como máximo uno por sujeto.
 *
 * @param spots las plazas que el carril ya muestra (filtradas por talla incluidas).
 * @param parkings los parkings que el carril ya muestra, **ya medidos y ordenados**.
 * @param fuel las gasolineras que el carril ya muestra, **ya cotizadas para el coche que conduces**.
 * @param from desde dónde se mide una plaza. ⛔ Las otras dos llegan medidas; una [Spot] no lleva
 *   distancia porque la distancia no es suya, es tuya.
 */
fun nearbyNotables(
    spots: List<Spot>,
    parkings: List<NearbyPublicParking>,
    fuel: List<NearbyFuelStation>,
    from: GpsPoint?,
): List<NearbyNotable> = buildList {
    // ⛔ **Plaza → gasolinera → parking**, y el orden NO sigue la intención sino la NOVEDAD
    // (user, 03-11). Parece contradecir la agrupación por intención que propone
    // `HOME-ONE-TAB-ANSWERS-ONE-INTENTION-001` —que pondría plaza y parking juntos— y no la
    // contradice, porque esta tira contesta otra pregunta: un carril lista **opciones**, y esto
    // señala **noticias**. Ordenadas por cuánto cambia cada una:
    //
    //  1. **Plaza** — caduca en minutos. Si no se mira ahora, deja de existir.
    //  2. **Gasolinera** — el precio se mueve a diario, y la tarjeta dice *la más barata*: eso es
    //     información nueva cada día, no un hecho del mapa.
    //  3. **Parking** — el más cercano es casi siempre el mismo. Es la tarjeta que menos cambia, y
    //     la que menos pierde quien no llega a ella.
    //
    // ⚠️ Si las pestañas `Aparcar`/`Repostar` llegan a entrar, hay que volver a mirar esto: con
    // dos pestañas, una tira que salta de aparcar a repostar y vuelve a aparcar puede leerse como
    // desorden en vez de como prioridad.
    notable(spots, parkings, fuel, from, NearbySubject.SPOT)?.let(::add)
    notable(spots, parkings, fuel, from, NearbySubject.FUEL)?.let(::add)
    notable(spots, parkings, fuel, from, NearbySubject.CAR_PARK)?.let(::add)
}

/**
 * El notable de UN sujeto, con su umbral.
 *
 * | Sujeto | Qué se señala | Umbral, y por qué ése |
 * |---|---|---|
 * | [NearbySubject.SPOT] | la más cercana | **que exista**. Es el producto, y **caduca**: una plaza libre es noticia por estar ahí. Sin fix se señala la primera, que es lo único que se puede ordenar |
 * | [NearbySubject.CAR_PARK] | el más cercano | **que haya uno**. ⛔ Se ofrece también con el gálibo sin publicar: *no saber si cabe no es saber que no cabe* |
 * | [NearbySubject.FUEL] | la más barata | **que haya precio** para tu producto. ⛔ Sin precio no hay noticia — aunque haya gasolineras, y las hay: la lista sigue enseñándolas sin cifra |
 *
 * ⚠️ **Lo que a la gasolinera le falta todavía es el umbral de FRESCURA**, y es deliberado: la hora
 * admisible depende de la FUENTE y no de una constante (España sella la respuesta, Francia e Italia
 * sellan cada precio, Alemania no sella nada y garantiza tiempo real por ley). Ese umbral entra en
 * la fase 4 del plan, y su sitio es justo aquí: una condición más sobre `price`. Hasta entonces la
 * regla es la que se puede sostener hoy — *hay precio* —, no una ventana inventada.
 */
private fun notable(
    spots: List<Spot>,
    parkings: List<NearbyPublicParking>,
    fuel: List<NearbyFuelStation>,
    from: GpsPoint?,
    subject: NearbySubject,
): NearbyNotable? = when (subject) {
    NearbySubject.SPOT -> {
        val nearest = from
            ?.let { point -> spots.minByOrNull { it.location.metresFrom(point) } }
            ?: spots.firstOrNull()
        nearest?.let {
            NearbyNotable(subject, it.id, from?.let(it.location::metresFrom))
        }
    }

    // ⛔ `first()` y no `minByOrNull`: la lista llega ordenada por distancia
    // (`NearbyOrdering.publicParkings`), así que medir otra vez sería una segunda definición de
    // «el más cercano» que podría discrepar de la que el usuario está leyendo.
    NearbySubject.CAR_PARK -> parkings.firstOrNull()?.let {
        NearbyNotable(subject, it.parking.id, it.distanceMeters)
    }

    // ⛔ La más barata se BUSCA, no se asume la primera: el carril puede estar ordenado por
    // cercanía, y entonces la primera fila no es la más barata. Y sólo entran las que cotizan:
    // `price` nulo es una gasolinera real sin cifra, no un precio de cero.
    NearbySubject.FUEL -> fuel
        .filter { it.price != null }
        .minByOrNull { it.price!!.perUnit.amount }
        ?.let { NearbyNotable(subject, it.stationKey, it.distanceMeters) }
}

private fun GpsPoint.metresFrom(other: GpsPoint): Double =
    haversineMeters(latitude, longitude, other.latitude, other.longitude)
