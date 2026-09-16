package com.rndeveloper.paparcar.domain.repository

import com.rndeveloper.paparcar.domain.zone.ZoneAlertSubscription

/**
 * The server-side index of who wants to be told about which ground.
 * [ZONE-A-SPOT-TELLS-THE-SERVER-AND-THE-SERVER-TELLS-YOU-001]
 *
 * ⛔ **Un índice de dónde vive la gente no se puede barrer**, y eso es lo que protegen las reglas:
 * la función pregunta por `cells array-contains`, así que un cliente que pudiera hacer ESA consulta
 * sacaría las coordenadas de sus vecinos. Lo que las reglas permiten es leer **acotado al propio
 * uid** y nada más — Firestore exige poder demostrar una consulta permitida *antes* de ejecutarla,
 * así que `array-contains` se rechaza de plano y solo pasa `where('uid','==',<el tuyo>)`.
 *
 * Esa lectura acotada no es un adorno: es la que hace posible el *replace* de abajo, que tiene que
 * saber qué filas sobran para borrarlas.
 *
 * ⛔ Este KDoc decía «write-only… nobody — not even its owner — may READ this collection». Era
 * falso: lo contradicen las propias reglas y la implementación, que lee en la primera línea de
 * `replaceAll`. Un comentario que promete una prohibición que no existe es peor que no tenerlo,
 * porque el siguiente lector razona sobre ella.
 */
interface ZoneAlertSubscriptionRepository : UserScopedRepository {

    /**
     * Erasure is this same verb with an empty list — the rows hold the zones you watch and the push
     * tokens of your phone, and both are yours. It went unswept until
     * [PRIV-A-DELETED-ACCOUNT-LEAVES-TWO-TOKENS-BEHIND-001] because write-only collections are easy
     * to forget: nothing in the app ever reads this one back to notice it was still there.
     */
    override suspend fun deleteAllData(userId: String): Result<Unit> = replaceAll(userId, emptyList())

    /**
     * Makes the server hold exactly [subscriptions] for this user, and nothing else.
     *
     * ⛔ **Replace, not upsert.** The interesting case is the one that DELETES: losing Plus, closing
     * the switch or unsubscribing a zone all arrive here as a shorter list, and an implementation
     * that only wrote what it was given would leave the old rows behind — the user would keep being
     * pushed about a zone they turned off, from a server that has no way of knowing they did.
     */
    suspend fun replaceAll(uid: String, subscriptions: List<ZoneAlertSubscription>): Result<Unit>
}
