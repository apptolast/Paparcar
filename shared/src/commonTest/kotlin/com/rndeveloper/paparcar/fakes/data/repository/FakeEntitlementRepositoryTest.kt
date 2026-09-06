package com.rndeveloper.paparcar.fakes.data.repository

import com.rndeveloper.paparcar.domain.model.Entitlement
import com.rndeveloper.paparcar.domain.model.PlusPlan
import com.rndeveloper.paparcar.fakes.MockScenario
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The production fake behind the Dev Catalog's "Plus activo" lever. These tests are the
 * witness that the lever actually drives the entitlement stream both ways — the downgrade
 * direction is the one the real store makes painful to reproduce. [MONEY-001]
 */
class FakeEntitlementRepositoryTest {

    @Test
    fun `should_followTheScenarioLever_when_scenarioIsPresent`() = runTest {
        val scenario = MockScenario()
        val repository = FakeEntitlementRepository(scenario)
        assertEquals(Entitlement.FREE, repository.observeEntitlement().first())

        scenario.isPlus.value = true
        assertEquals(Entitlement.PLUS, repository.observeEntitlement().first())

        scenario.isPlus.value = false
        assertEquals(Entitlement.FREE, repository.observeEntitlement().first())
    }

    @Test
    fun `should_flipTheScenarioLever_when_purchaseSucceedsWithScenario`() = runTest {
        val scenario = MockScenario()
        val repository = FakeEntitlementRepository(scenario)

        val result = repository.purchasePlus(PlusPlan.MONTHLY)

        assertTrue(result.isSuccess)
        assertTrue(scenario.isPlus.value)
        assertEquals(Entitlement.PLUS, repository.observeEntitlement().first())
    }

    @Test
    fun `should_activatePlusInMemory_when_thereIsNoScenario`() = runTest {
        val repository = FakeEntitlementRepository()
        assertEquals(Entitlement.FREE, repository.observeEntitlement().first())

        repository.purchasePlus(PlusPlan.YEARLY)

        assertEquals(Entitlement.PLUS, repository.observeEntitlement().first())
    }

    @Test
    fun `should_resetToFree_when_scenarioResets`() = runTest {
        val scenario = MockScenario()
        val repository = FakeEntitlementRepository(scenario)
        repository.purchasePlus(PlusPlan.MONTHLY)

        scenario.reset()

        assertEquals(Entitlement.FREE, repository.observeEntitlement().first())
    }
}
