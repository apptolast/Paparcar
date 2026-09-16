package com.rndeveloper.paparcar.di

import com.apptolast.baselogin.di.loginPresentationModule
import kotlin.test.Test
import kotlin.test.assertTrue
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.module.Module

/**
 * Every domain PORT the production graph binds must be bound by the mock graph too.
 * [MOCK-A-PORT-DECLARED-IN-PROD-MUST-EXIST-IN-MOCK-001]
 *
 * ⛔ **This is the check [MockKoinModuleVerifyTest] cannot do, and the reason it stayed green while
 * the app was dead.** `verify()` reflects CONSTRUCTORS: for a definition declared as a concrete
 * class it reads the parameter types and checks each one is bound. It is blind to what happens
 * inside a hand-written definition body —
 *
 * ```kotlin
 * factory { ResolveFuelPriceUseCase(averages = get<AverageFuelPricesRepository>()::current) }
 * ```
 *
 * — and that `get<>()` is exactly how the Vehicles tab died in mock: the port lived in `dataModule`
 * only, the mock graph never loads `dataModule`, and nothing tied the two lists together. Five
 * separate comments in `MockModule` say "the mock stack has no dataModule, so this port must exist
 * here"; each one is a time somebody found out by tapping.
 *
 * So this test does not look at definitions at all. It compares the SET of bound types, which is
 * immune to how each one happens to be declared.
 *
 * 🔑 **Why filtering to `domain.` is exact and not a heuristic.** A concrete class from `data/` or
 * from `:app` has no business existing in the mock graph — the fake binds the INTERFACE, and the
 * interface is the only thing `domainModule` and `presentationModule` are allowed to consume. That
 * is Clean Architecture doing the filtering, not a guess about naming.
 */
@OptIn(KoinInternalApi::class)
class MockGraphPortParityTest {

    /**
     * `dataModule` — the module `mockModule` exists to STAND IN FOR, one port at a time.
     *
     * ⛔ Deliberately not `androidPlatformModule` / `androidDetectionModule` / `appModule`. Those
     * bind the sensors, the geofence plumbing and the services, and the mock graph drops them on
     * purpose: the Dev Catalog runs no foreground service, so nothing in it can ask. Their ports
     * reach the shared graph only as constructor parameters — and constructor parameters are
     * exactly what [MockKoinModuleVerifyTest] already proves resolvable. Including them here would
     * buy an allowlist of eleven entries that nobody would ever re-read, in exchange for zero
     * coverage this pair of tests does not already have.
     */
    private val prodOnly = listOf(dataModule)

    /** The mock graph, assembled exactly as `MockPaparcarApp.startKoin` lists it. */
    private val mockGraph = listOf(
        loginPresentationModule,
        presentationModule,
        domainModule,
        mockModule,
    )

    @Test
    fun should_bindEveryDomainPortOfTheProdGraph_when_theMockGraphIsAssembled() {
        val mockKeys = keysOf(mockGraph)
        val missing = keysOf(prodOnly)
            .filter { it.contains(DOMAIN_PACKAGE) }
            .filterNot { key -> mockKeys.any { it.startsWith(key.substringBefore(':')) } }
            .filterNot { key -> INTENTIONALLY_UNBOUND.any { key.contains(it) } }
            .sorted()

        assertTrue(
            missing.isEmpty(),
            "These domain ports are bound in production and MISSING from the mock graph. " +
                "Each one is a screen that dies on NoDefinitionFoundException the moment it asks " +
                "for it — bind it in mockModule (a fake), or allowlist it here WITH its reason:\n" +
                missing.joinToString("\n") { "  · $it" },
        )
    }

    /**
     * Population witness: a filter that matched nothing — a renamed package, an `includes` that
     * stopped pulling a module in — would make the assertion above pass by looking at an empty set.
     * A green with no subjects and a green with no findings must not be the same green.
     * [TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001]
     */
    @Test
    fun should_haveDomainPortsToCompare_when_bothGraphsAreFlattened() {
        val prodPorts = keysOf(prodOnly).filter { it.contains(DOMAIN_PACKAGE) }
        val mockPorts = keysOf(mockGraph).filter { it.contains(DOMAIN_PACKAGE) }
        assertTrue(
            prodPorts.size >= MIN_EXPECTED_PORTS && mockPorts.size >= MIN_EXPECTED_PORTS,
            "The comparison has no subjects: prod=${prodPorts.size}, mock=${mockPorts.size} " +
                "domain ports. Either the package moved or a module stopped being included.",
        )
    }

    private fun keysOf(modules: List<Module>): List<String> =
        flatten(modules).flatMap { it.mappings.keys }.distinct()

    private fun flatten(roots: List<Module>): List<Module> {
        val seen = LinkedHashSet<Module>()
        fun visit(m: Module) {
            if (seen.add(m)) m.includedModules.forEach(::visit)
        }
        roots.forEach(::visit)
        return seen.toList()
    }

    private companion object {
        const val DOMAIN_PACKAGE = "com.rndeveloper.paparcar.domain."

        /**
         * Ports production binds that the mock graph leaves unbound ON PURPOSE. Every entry needs
         * its reason here, so "it was already failing" can never be the reason.
         */
        val INTENTIONALLY_UNBOUND = listOf<String>()

        /** Far below the real count; it only has to prove the sets are not empty. */
        const val MIN_EXPECTED_PORTS = 20
    }
}
