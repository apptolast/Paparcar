package com.rndeveloper.paparcar.domain.usecase.entitlement

import com.rndeveloper.paparcar.domain.model.Entitlement
import com.rndeveloper.paparcar.domain.repository.EntitlementRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The one question every Plus gate asks: is this user Plus right now? Gates react to the
 * stream, so losing Plus downgrades live (history window shrinks, zone alerts pause)
 * without wiping anything. [MONEY-001]
 */
class ObserveIsPlusUseCase(
    private val entitlementRepository: EntitlementRepository,
) {
    operator fun invoke(): Flow<Boolean> =
        entitlementRepository.observeEntitlement().map { it == Entitlement.PLUS }
}
