package com.rndeveloper.paparcar.domain.notification

/**
 * De qué carril habla un movimiento de precio. [FUEL-A-NEARBY-PRICE-IS-FREE-NEWS-001]
 *
 * Los dos dicen la misma cifra y la misma dirección; lo que cambia es **de quién hablan**, y de ahí
 * salen las dos únicas cosas que el aviso no comparte: el sujeto del título y la tarjeta en la que
 * aparece.
 *
 * ⛔ No es «free vs Plus». Es «tu alrededor vs lo que nombraste»: un usuario Plus recibe LOS DOS, y
 * leer esto como el plan sería volver a mezclar el precio con la factura.
 */
enum class FuelMoveLane {
    /** Una gasolinera que el usuario estrelló — esté donde esté. Carril Plus. */
    FOLLOWED,

    /** Una de las que le rodean, que nadie estrelló. Carril free. */
    NEARBY,
}
