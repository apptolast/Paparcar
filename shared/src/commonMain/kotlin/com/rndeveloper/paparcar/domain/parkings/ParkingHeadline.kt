package com.rndeveloper.paparcar.domain.parkings

/**
 * **El ÚNICO hecho que una superficie dice de un parking**, elegido por mejor evidencia.
 * [PARKINGS-A-LIVE-COUNT-OUTRANKS-A-TARIFF-001]
 *
 * Hay **una ranura** —el `trailing` de la fila, la cápsula del marcador, el eyebrow del peek— y
 * **cuatro hechos** que la quieren. La escalera decide, y la decide **una vez**: las tres
 * superficies leen esta función, así que no pueden discrepar. Hoy el color ya cumple ese invariante
 * (*«el pin del mapa y su fila son la MISMA cosa»*); a partir de aquí lo cumple también el hecho.
 *
 * ```
 * 1 · ocupación viva y FRESCA   →  «42 libres»  ·  0 ⇒ «Completo»
 * 2 · oferta reservable          →  «desde 8 €» + su ventana        (ticket 5, declarado y vacío)
 * 3 · tarifa declarada           →  «De pago» / «Gratuito»
 * 4 · el 71 % restante           →  NADA
 * ```
 *
 * ⛔ **Por qué 2 va encima de 3 y aun así «Gratuito» nunca se pierde**: «desde 8 €» y «De pago» son
 * la misma afirmación dicha con distinta precisión, así que gana la precisa. Pero `fee=no` y una
 * oferta de pago **se contradicen**, y una contradicción no se resuelve eligiendo: manda **la base**
 * y la discrepancia significa que algo está mal emparejado.
 *
 * ⛔ **Y no es un caso de uso**, con el precedente explícito de `FuelStandouts`: *«una función pura y
 * no un caso de uso a propósito — su resultado no aparece nunca en un diagnóstico y no cambia nunca
 * un pin: sólo decide qué dice una fila de sí misma»* [DET-VERDICT-NOT-PREDICATE-001]. Los textos
 * los resuelve presentación: el dominio es Kotlin puro y no conoce recursos.
 */
sealed interface ParkingHeadline {

    /** Hay sitio, y cuántos. [agedMinutes] es para que el peek pueda decir «hace X min». */
    data class FreeSpaces(val free: Int, val agedMinutes: Int) : ParkingHeadline

    /**
     * No queda sitio.
     *
     * ⛔ **Es su propio escalón y no un `FreeSpaces(0)`**, porque «0 libres» **se lee como un dato
     * que falta** — la misma razón por la que `capacity` no se pinta como cero. `Parking Benavente:
     * 0` es un caso real medido en Madrid, no un supuesto.
     */
    data class Full(val agedMinutes: Int) : ParkingHeadline

    /**
     * Reservable con precio de referencia. **Declarado y hoy inalcanzable**: lo rellena
     * `PARCLICK-OFFER-BEHIND-A-FLAG-001`, que está bloqueado hasta que Parclick conteste por escrito.
     *
     * ⛔ Lleva su **ventana** dentro a propósito: un «desde 8 €» sin decir de cuántas horas es el
     * precio de una ventana que el usuario no ha pedido.
     */
    data class Offer(val fromMinorUnits: Int, val currency: String, val windowMinutes: Int) :
        ParkingHeadline

    /** Lo que la fuente dice del precio. Sólo `PAID` o `FREE` llegan aquí. */
    data class Tariff(val fee: FeeInfo) : ParkingHeadline

    /**
     * No hay nada que afirmar, y **es el caso NORMAL**: el 73,6 % de los parkings de España no
     * declara tarifa [MEDIDO sobre el snapshot publicado]. La fila deja su ranura vacía y el
     * marcador se dibuja como un disco pelado, sin cápsula.
     */
    data object Silent : ParkingHeadline
}

/**
 * Cuánto vive una cifra **sellada por la fuente** antes de dejar de enseñarse.
 *
 * 3× la cadencia de Madrid (que se mueve cada 1-3 min) [SUPUESTO declarado: hay que medir en el feed
 * cuánto tarda de verdad en moverse, en hora punta y de noche].
 */
const val AVAILABILITY_FRESH_MINUTES = 10

/**
 * ⛔ Y cuánto vive una cifra **sellada por NOSOTROS**, que es menos y no es simetría mal entendida:
 * cuando la fuente no sella su dato, nuestra marca dice **cuándo preguntamos**, no cuándo se midió
 * — así que la cifra ya nace con una antigüedad desconocida encima. Darle los mismos 10 minutos
 * sería sumarle a un dato de edad desconocida diez más y presentarlo igual de fresco.
 */
const val AVAILABILITY_FRESH_MINUTES_WHEN_WE_STAMPED_IT = 4

/**
 * El hecho que este parking dice de sí mismo, ahora.
 *
 * ⛔ **Una cuenta viva CADUCA, y al caducar BAJA la escalera** — no se enseña vieja. Pasada su
 * frescura la superficie cae al escalón de la tarifa, que es un dato que no caduca. Enseñar un
 * número rancio es peor que no enseñarlo: la ranura dejaría de ser fiable, y es la misma ranura que
 * F6a dejó deliberadamente sin cifra para no enseñar al usuario a desconfiar de ella.
 */
fun PublicParking.headline(nowMs: Long): ParkingHeadline {
    val live = availability
    if (live != null) {
        val aged = live.agedMinutesAt(nowMs)
        val ceiling = if (live.isStampedByUs) {
            AVAILABILITY_FRESH_MINUTES_WHEN_WE_STAMPED_IT
        } else {
            AVAILABILITY_FRESH_MINUTES
        }
        if (aged <= ceiling) {
            return if (live.free <= 0) {
                ParkingHeadline.Full(aged)
            } else {
                ParkingHeadline.FreeSpaces(live.free, aged)
            }
        }
    }
    return when (fee) {
        // El escalón 2 (la oferta) iría aquí cuando exista: `PARCLICK-OFFER-BEHIND-A-FLAG-001`.
        FeeInfo.PAID, FeeInfo.FREE -> ParkingHeadline.Tariff(fee)
        FeeInfo.UNKNOWN -> ParkingHeadline.Silent
    }
}

/**
 * **Qué puede decir una superficie del SELLO de la ocupación**, que son TRES estados y no dos.
 * [PARKINGS-SHOW-FEE-AND-OCCUPANCY-001]
 *
 * [headline] contesta *qué* se afirma, y al caducar una cifra baja la escalera y desaparece. Eso
 * deja **dos situaciones distintas contándose como la misma**: un parking que nunca tuvo cuenta y
 * uno de Madrid cuya cuenta se quedó rancia se leían igual —ranura vacía— porque las dos dan `null`.
 *
 * ⛔ **Y fundirlas es lo que le quita valor a la ranura.** «No lo sabemos» y «lo supimos y ya no
 * vale» le piden al conductor cosas distintas: en el primero no hay nada que esperar, en el segundo
 * la cifra vuelve en minutos. El invariante 1 de `data-providers-001.md` §F.1 ya lo había decidido
 * —*«el peek sigue diciendo «hace X min», que es lo único honesto cuando el dato ya no manda»*— y
 * nadie lo implementó: `agedMinutes` viajaba dentro de [ParkingHeadline.FreeSpaces] sin un lector.
 *
 * ⛔ **No es un segundo criterio de frescura**: el umbral no se vuelve a escribir aquí, se LEE de
 * [headline]. Si algún día cambia, cambia en un sitio y este tipo le sigue.
 */
sealed interface OccupancyStamp {

    /** La fuente no publica ocupación de este parking. No hay sello que enseñar. */
    data object None : OccupancyStamp

    /** La cifra manda, y [updatedAtMs] es de cuándo es. */
    data class Current(val updatedAtMs: Long) : OccupancyStamp

    /**
     * Hubo cifra y ya no manda — [headline] la ha retirado.
     *
     * ⛔ El sello **sigue saliendo**: es lo único que separa este estado de [None], y lo que dice es
     * que aquí SÍ se mide, sólo que la última medida es vieja.
     */
    data class Expired(val updatedAtMs: Long) : OccupancyStamp
}

/** El sello de este parking, ahora. Ver [OccupancyStamp]. */
fun PublicParking.occupancyStamp(nowMs: Long): OccupancyStamp {
    val measuredAtMs = availability?.updatedAtMs ?: return OccupancyStamp.None
    return when (headline(nowMs)) {
        // Los dos escalones que SÍ son una cuenta viva. Que exista `availability` y la escalera no
        // haya devuelto ninguno de ellos significa exactamente una cosa: caducó.
        is ParkingHeadline.FreeSpaces, is ParkingHeadline.Full -> OccupancyStamp.Current(measuredAtMs)
        else -> OccupancyStamp.Expired(measuredAtMs)
    }
}
