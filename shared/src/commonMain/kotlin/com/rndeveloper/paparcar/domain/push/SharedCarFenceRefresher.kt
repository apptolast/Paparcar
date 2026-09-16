package com.rndeveloper.paparcar.domain.push

/**
 * «Reconcilia las vallas YA». [DET-A-SHARED-FENCE-FOLLOWS-THE-CAR-001]
 *
 * ⛔ **No registra ni quita ninguna valla**: solo le dice al reconciliador de cada plataforma que
 * pase ahora en vez de en su siguiente pasada. El inventario de regiones sigue teniendo un único
 * dueño —el janitor en Android, el intake del controlador en iOS— porque dos pasadas distintas sobre
 * el mismo inventario acaban discrepando.
 *
 * ⛔ Y no lee ni un campo del mensaje que lo dispara: relee el espejo. Ese es exactamente el motivo
 * de que se llame ANTES que la tarjeta — un campo que falte puede costar la tarjeta, nunca la
 * vigilancia.
 */
fun interface SharedCarFenceRefresher {

    fun refreshNow()
}
