package com.rndeveloper.paparcar.domain.repository

import com.rndeveloper.paparcar.domain.model.Entitlement
import com.rndeveloper.paparcar.domain.model.PlusPlan
import kotlinx.coroutines.flow.Flow

/**
 * Single door to the subscription state. Every consumer (history gate, zone alerts,
 * settings, paywall) asks HERE — never the store SDK directly, and never a mirrored
 * flag in Firestore or Room. [MONEY-001]
 */
interface EntitlementRepository {

    /**
     * Whether the store SDK is actually configured — false in the mock flavor (never calls
     * `configure()`) and in a release build with no API key yet. Presentation reads this to
     * decide between the real paywall template and a static fallback, instead of asking the
     * vendor SDK directly. Constant for the process lifetime: a blank key never becomes one.
     */
    val isStoreAvailable: Boolean

    /**
     * The user's current entitlement. Emits [Entitlement.FREE] until the store says
     * otherwise, and keeps emitting on every subscription change (purchase, renewal,
     * expiration, restore) for as long as it is collected.
     */
    fun observeEntitlement(): Flow<Entitlement>

    /**
     * Launches the store purchase flow for the given plan. Success means the `plus`
     * entitlement is active; a user-cancelled flow is a failure like any other.
     */
    suspend fun purchasePlus(plan: PlusPlan): Result<Unit>

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
