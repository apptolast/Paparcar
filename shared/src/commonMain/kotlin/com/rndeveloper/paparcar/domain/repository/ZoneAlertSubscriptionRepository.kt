package com.rndeveloper.paparcar.domain.repository

import com.rndeveloper.paparcar.domain.zone.ZoneAlertSubscription

/**
 * The server-side index of who wants to be told about which ground.
 * [ZONE-A-SPOT-TELLS-THE-SERVER-AND-THE-SERVER-TELLS-YOU-001]
 *
 * ⛔ Write-only from the app, and the security rules say so too: nobody — not even its owner — may
 * READ this collection from a client. The function queries it by `cells array-contains`, so a
 * readable index would let any signed-in user sweep a cell and pull out where their neighbours live.
 * The app never needs to read it: what it is subscribed to is in Room already.
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
