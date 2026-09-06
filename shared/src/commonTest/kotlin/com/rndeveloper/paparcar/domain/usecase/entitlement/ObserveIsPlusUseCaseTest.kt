package com.rndeveloper.paparcar.domain.usecase.entitlement

import com.rndeveloper.paparcar.domain.model.Entitlement
import com.rndeveloper.paparcar.fakes.FakeEntitlementRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ObserveIsPlusUseCaseTest {

    @Test
    fun `should_emitFalse_when_entitlementIsFree`() = runTest {
        val useCase = ObserveIsPlusUseCase(FakeEntitlementRepository(Entitlement.FREE))

        assertFalse(useCase().first())
    }

    @Test
    fun `should_emitTrue_when_entitlementIsPlus`() = runTest {
        val useCase = ObserveIsPlusUseCase(FakeEntitlementRepository(Entitlement.PLUS))

        assertTrue(useCase().first())
    }

    @Test
    fun `should_emitTrue_when_entitlementUpgradesMidStream`() = runTest {
        val repository = FakeEntitlementRepository(Entitlement.FREE)
        val useCase = ObserveIsPlusUseCase(repository)
        assertFalse(useCase().first())

        repository.emitEntitlement(Entitlement.PLUS)

        assertTrue(useCase().first())
    }

    @Test
    fun `should_emitFalse_when_plusIsLost`() = runTest {
        val repository = FakeEntitlementRepository(Entitlement.PLUS)
        val useCase = ObserveIsPlusUseCase(repository)
        assertTrue(useCase().first())

        repository.emitEntitlement(Entitlement.FREE)

        assertFalse(useCase().first())
    }
}
