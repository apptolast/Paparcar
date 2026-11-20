package com.rndeveloper.paparcar.domain.repository

import com.rndeveloper.paparcar.domain.model.Entitlement
import com.rndeveloper.paparcar.domain.model.PlusGrant
import com.rndeveloper.paparcar.domain.model.PlusOffer
import com.rndeveloper.paparcar.domain.model.PlusPlan
import kotlinx.coroutines.flow.Flow

/**
 * Single door to the subscription state. Every consumer (history gate, zone alerts,
 * settings, paywall) asks HERE — never the store SDK directly, and never a mirrored
 * flag in Firestore or Room. [MONEY-001]
 */
interface EntitlementRepository {


    /**
     * The user's current entitlement. Emits [Entitlement.FREE] until the store says
     * otherwise, and keeps emitting on every subscription change (purchase, renewal,
     * expiration, restore) for as long as it is collected.
     *
     * ⛔ A **projection of [observePlusGrant]**, not a second stream over the same fact: the
     * implementation holds one grant and maps it. Two independent flows would diverge the first
     * time one of them was updated and the other was not, and a gate that disagrees with the
     * paywall about whether someone is Plus is the worst possible way to find that out.
     * [MONEY-FOUNDERS-WELCOME-001]
     */
    fun observeEntitlement(): Flow<Entitlement>

    /**
     * HOW the active Plus arrived and when it ends — null while the user is free.
     * [MONEY-FOUNDERS-WELCOME-001]
     *
     * The gates do not collect this and must not start: a Plus that was given away opens exactly
     * the same doors as a Plus that was bought. Its two consumers are the welcome moment and the
     * end-of-promo reminder, which are the only places in the app where *where it came from*
     * changes what the user reads.
     */
    fun observePlusGrant(): Flow<PlusGrant?>

    /**
     * The plans the store can currently sell, with their live localised prices.
     *
     * Empty is a normal answer, not an error: a build with no API key, or a store that has no
     * products registered yet, has nothing to offer. The paywall reads that as "show the
     * comparison, hide the price cards" — it never invents a price to fill the gap. [MONEY-001]
     */
    suspend fun loadOffers(): Result<List<PlusOffer>>

    /**
     * Launches the store purchase flow for the given plan. Success means the `plus`
     * entitlement is active; a user-cancelled flow is a failure like any other.
     */
    suspend fun purchasePlus(plan: PlusPlan): Result<Unit>

    /**
     * Re-grants whatever the signed-in store account already paid for. [MONEY-001]
     *
     * Nothing is charged. Returns true when Plus is active AFTER the restore, false when the
     * account genuinely owns nothing — a distinction the caller needs, because "we found nothing"
     * is a normal answer that deserves a plain sentence, not an error.
     */
    suspend fun restorePurchases(): Result<Boolean>

    /**
     * Binds the store identity to the signed-in account's uid, so the subscription follows
     * the ACCOUNT across devices and reinstalls. Idempotent and free of network when the
     * same user is already bound (every warm app start). Failure is non-fatal by contract:
     * callers log and move on — the entitlement stays as the store last knew it, and the
     * safe side of that is FREE, never a phantom Plus. [MONEY-001]
     */
    suspend fun logIn(userId: String): Result<Unit>

    /**
     * Unbinds the store identity on sign-out, returning to a fresh anonymous profile so
     * the next account on this device never sees the previous user's Plus.
     */
    suspend fun logOut(): Result<Unit>
}
