package com.rndeveloper.paparcar.domain.usecase.entitlement

import com.rndeveloper.paparcar.domain.model.Entitlement
import com.rndeveloper.paparcar.domain.preferences.AppPreferences
import com.rndeveloper.paparcar.domain.repository.EntitlementRepository
import com.rndeveloper.paparcar.domain.repository.FoundersClaimOutcome
import com.rndeveloper.paparcar.domain.repository.FoundersPromoRepository
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.delay

/** Process the account's founders claim on arrival at Home. [MONEY-FOUNDERS-PLUS-PROMO-001] */
class ClaimFoundersPromoUseCase(
    private val repository: FoundersPromoRepository,
    private val appPreferences: AppPreferences,
    private val entitlementRepository: EntitlementRepository,
) {
    suspend operator fun invoke(uid: String) {
        if (uid in appPreferences.foundersPromoClaimedUids) return

        val outcome = repository.claim(uid).getOrElse { error ->
            PaparcarLogger.w(TAG, "Founders claim could not be completed", error)
            return
        }
        if (outcome is FoundersClaimOutcome.Granted) {
            // ⛔ Se apunta ANTES de reconciliar, y a propósito: el servidor YA dijo que esta cuenta
            // es una de las 2.000, y eso es verdad aunque RevenueCat tarde o la app muera aquí
            // mismo. Perder esa prueba es perder la felicitación para siempre en este dispositivo.
            // Es la ÚNICA puerta por la que se escribe. [MONEY-FOUNDERS-PLUS-PROMO-001]
            appPreferences.addFoundersPromoGrantedUid(uid)
            if (!reconcileGrant(uid, outcome)) return
        }
        appPreferences.addFoundersPromoClaimedUid(uid)
    }

    private suspend fun reconcileGrant(uid: String, outcome: FoundersClaimOutcome.Granted): Boolean {
        for (delayMillis in REFRESH_DELAYS_MS) {
            delay(delayMillis)
            val refresh = entitlementRepository.refreshEntitlement(uid).getOrElse { error ->
                PaparcarLogger.w(TAG, "Founders entitlement refresh failed", error)
                return false
            }
            if (refresh.entitlement == Entitlement.PLUS) return true
            // An old receipt on a second device can outlive the gift. Use the store's time
            // after a fresh response so changing the phone clock cannot end the reconciliation.
            val expiresAtMillis = outcome.expiresAtMillis
            if (expiresAtMillis != null && refresh.observedAtMillis >= expiresAtMillis) return true
        }
        PaparcarLogger.w(TAG, "Founders grant is still pending in RevenueCat")
        return false
    }

    private companion object {
        const val TAG = "FoundersPromo"
        val REFRESH_DELAYS_MS = listOf(0L, 1_000L, 2_000L, 4_000L)
    }
}
