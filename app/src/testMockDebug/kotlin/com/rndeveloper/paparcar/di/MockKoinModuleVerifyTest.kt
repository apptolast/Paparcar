package com.rndeveloper.paparcar.di

import android.content.Context
import com.apptolast.baselogin.di.loginPresentationModule
import kotlin.test.Test
import kotlin.test.assertTrue
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.test.verify.verify

/**
 * The same guarantee [KoinModuleVerifyTest] gives production, for the graph the mock build boots.
 * [MOCK-A-SECOND-GRAPH-NEEDS-A-SECOND-VERIFY-001]
 *
 * ⛔ **The mock stack has no `dataModule`.** It re-declares every port by hand in [mockModule], so
 * each port `domainModule` consumes exists TWICE and nothing tied the two lists together. Adding a
 * port to `dataModule` alone leaves the mock build resolvable everywhere except the one screen that
 * asks for it — and it dies there, at runtime, on a `NoDefinitionFoundException`.
 *
 * That is not hypothetical and it is not new: `MockModule` already carries three comments saying
 * "the mock stack has no dataModule, so the port must exist here or resolving X crashes", one per
 * time somebody found out the same way. `FuelStationsSource` was the fourth — measured on the
 * emulator, killing the app on entering Home, **after** a full round of on-device checking had
 * passed. It passed because the state gallery calls composables directly and never builds a
 * ViewModel: a gallery pass is not an app pass, and neither of them is a graph check.
 *
 * What this catches is exactly that class of bug: [verify] resolves the constructor of every
 * definition declared as a concrete class — every use case, every ViewModel — against the bindings
 * of the whole module set. A port declared in prod and forgotten in mock fails here, in seconds,
 * instead of on the third tap of a manual walkthrough.
 */
@OptIn(KoinExperimentalAPI::class, KoinInternalApi::class)
class MockKoinModuleVerifyTest {

    /** The mock graph, assembled exactly as `MockPaparcarApp.startKoin` lists it. */
    private val mockGraph = module {
        includes(
            loginPresentationModule,
            presentationModule,
            domainModule, // includes(detectionModule)
            mockModule,
        )
    }

    @Test
    fun should_resolveEveryConstructorDependency_when_theMockGraphIsAssembled() {
        mockGraph.verify(
            extraTypes = listOf(
                // Provided at runtime by startKoin { androidContext(...) }, not by a definition.
                Context::class,
                // Rides the navigation route into the paywall VM. [FREE-LIMITS-001]
                com.rndeveloper.paparcar.domain.entitlement.PaywallReason::class,
                // `() -> T` / `(A) -> T` params are inline factories written in the module lambda.
                Function0::class,
                Function1::class,
                // verify() unwraps `List<T>` to its element type; the list itself is built inline.
                com.rndeveloper.paparcar.domain.repository.UserScopedRepository::class,
                // OPTIONAL ports: consumers take them nullable and the module fills them with
                // `getOrNull()` — the mock graph leaves them unbound on purpose (the catalog needs no
                // trip memory). If one ever becomes a required `get()`, the prod verify in `:shared`
                // catches it. [IOS-A-PARITY-TAIL-001]
                com.rndeveloper.paparcar.domain.detection.ports.DrivingRouteStore::class,
                com.rndeveloper.paparcar.domain.diagnostics.LocalDiagnosticsLog::class,
                com.rndeveloper.paparcar.domain.detection.ports.TripTrail::class,
                // [IOS-F0-03] `DeviceCapabilities` is built from literals in mockModule; verify()
                // reflects its constructor and reads the Boolean as an unbound dependency. A bare
                // Boolean is never a legitimate binding, so no real gap can hide behind this.
                Boolean::class,
            ),
        )
    }

    /**
     * Population witness: an emptied [mockModule] — or an `includes` that quietly stopped pulling it
     * in — would let the verify above pass with nothing to look at. A green with no subjects and a
     * green with no findings must not be the same green.
     *
     * The roots are picked to be the ones that actually bit: a port only the mock graph declares
     * ([FakeFuelStationsSource]'s contract among them), plus the ViewModel whose construction is
     * where a missing binding surfaces.
     */
    @Test
    fun should_containTheMockPorts_when_theVerifiedModuleSetIsFlattened() {
        val mappingKeys = flatten(mockGraph).flatMap { it.mappings.keys }
        val roots = listOf(
            "HomeViewModel",
            "FuelStationsSource",
            "VehicleRepository",
            "AppPreferences",
            // Bound only in platform modules the mock graph never loads until
            // [IOS-DI-A-MOCK-GRAPH-ONLY-PROD-IS-VERIFIED-001] — and its consumers, which crashed Home.
            "DeviceCapabilities",
            "EvaluateDetectionReliabilityUseCase",
            "SettingsViewModel",
            "PermissionsViewModel",
        )
        roots.forEach { root ->
            assertTrue(
                mappingKeys.any { it.contains(root) },
                "The verified module set no longer contains a '$root' definition — " +
                    "the verify() above is looking at a graph that lost its subjects.",
            )
        }
    }

    private fun flatten(root: Module): List<Module> {
        val seen = LinkedHashSet<Module>()
        fun visit(m: Module) {
            if (seen.add(m)) m.includedModules.forEach(::visit)
        }
        visit(root)
        return seen.toList()
    }
}
