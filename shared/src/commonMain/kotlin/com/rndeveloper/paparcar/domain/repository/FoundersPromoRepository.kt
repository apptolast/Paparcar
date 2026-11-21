package com.rndeveloper.paparcar.domain.repository

/** A receipt for the claim. RevenueCat determines whether Plus is currently active. */
sealed interface FoundersClaimOutcome {
    data class Granted(val expiresAtMillis: Long?) : FoundersClaimOutcome
    data object NotGranted : FoundersClaimOutcome
}

interface FoundersPromoRepository {

    /** Registers arrival at Home for [uid] and waits for the terminal receipt, reusing any existing claim. */
    suspend fun claim(uid: String): Result<FoundersClaimOutcome>
}
