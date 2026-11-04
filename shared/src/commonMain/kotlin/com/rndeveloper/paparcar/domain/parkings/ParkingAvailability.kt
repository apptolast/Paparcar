package com.rndeveloper.paparcar.domain.parkings

/**
 * **Cuántas plazas quedan AHORA en un parking**, y de cuándo es esa cifra.
 * [PARKINGS-A-LIVE-COUNT-OUTRANKS-A-TARIFF-001]
 *
 * La trae el backend ya fusionada con la base (`PARKINGS-LIVE-OCCUPANCY-001`). Hoy sólo la publica
 * Madrid: **24 parkings**, cada uno con su propia marca de tiempo.
 *
 * ⛔ **No lleva la licencia ni el tipo de fuente como texto.** La atribución es una pantalla
 * (`LicensesScreen`), no un campo que cada fila tenga que interpretar; y `source` está aquí porque
 * es **procedencia** —de quién es el dato— que es lo que un diagnóstico cita.
 */
data class ParkingAvailability(
    /** ⛔ Un **0 es un dato**, no una ausencia: `Parking Benavente: 0` es un caso real medido. */
    val free: Int,
    /**
     * El aforo, donde la fuente lo diga.
     *
     * ⛔ **Madrid NO lo publica** — medido el 03-11 sobre la respuesta real: dentro de
     * `<lstOccupation>` hay `code`, `free`, `moment`, `name` y `renewalIndex`, y nada más. Así que
     * esto es null en las 24 medidas de hoy, y **«42 libres» no puede decir «de 587»**. El
     * `capacity` de OSM lo trae en el 3,2 % y **no se mezcla**: sería atribuirle a Madrid un número
     * que no ha dado.
     */
    val total: Int? = null,
    /** Cuándo se midió, según [isStampedByUs] quién lo dice. */
    val updatedAtMs: Long,
    /** Procedencia: `madrid`. ⛔ Nunca confianza. */
    val source: String,
    /**
     * ⛔ **`true` cuando la fuente NO sella su dato y [updatedAtMs] es la hora en que lo LEÍMOS.**
     *
     * Lo obligó Málaga al medirla: su CSV es `dato,id,libres` y no trae marca de tiempo, mientras
     * Madrid sella cada parking. *«Hace 3 minutos»* significa dos cosas distintas — «la ciudad lo
     * midió hace 3 min» y «lo preguntamos hace 3 min, y no sabemos de cuándo es» — y por eso el
     * umbral de caducidad **no puede ser el mismo**: ver [ParkingHeadline].
     */
    val isStampedByUs: Boolean = false,
) {
    /** Cuánto hace que se midió, en minutos. Negativo imposible: un reloj adelantado da 0. */
    fun agedMinutesAt(nowMs: Long): Int =
        ((nowMs - updatedAtMs).coerceAtLeast(0) / MILLIS_PER_MINUTE).toInt()

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
