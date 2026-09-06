package com.rndeveloper.paparcar.domain.usecase.entitlement

import com.rndeveloper.paparcar.domain.model.Entitlement
import com.rndeveloper.paparcar.domain.model.PlusPlan
import com.rndeveloper.paparcar.fakes.FakeEntitlementRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PurchasePlusUseCaseTest {

    @Test
    fun `should_purchaseTheRequestedPlan_when_invoked`() = runTest {
        val repository = FakeEntitlementRepository()
        val useCase = PurchasePlusUseCase(repository)

        val result = useCase(PlusPlan.YEARLY)

        assertTrue(result.isSuccess)
        assertEquals(1, repository.purchasePlusCallCount)
        assertEquals(PlusPlan.YEARLY, repository.lastPurchasedPlan)
    }

    @Test
    fun `should_activatePlusOnTheStream_when_purchaseSucceeds`() = runTest {
        val repository = FakeEntitlementRepository()
        val useCase = PurchasePlusUseCase(repository)

        useCase(PlusPlan.MONTHLY)

        assertEquals(Entitlement.PLUS, repository.observeEntitlement().first())
    }

    @Test
    fun `should_propagateFailure_when_purchaseFails`() = runTest {
        val repository = FakeEntitlementRepository().apply {
            purchasePlusResult = Result.failure(RuntimeException("user cancelled"))
        }
        val useCase = PurchasePlusUseCase(repository)

        val result = useCase(PlusPlan.MONTHLY)

        assertTrue(result.isFailure)
        assertEquals(Entitlement.FREE, repository.observeEntitlement().first())
    }
}
