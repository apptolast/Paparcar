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
 * [IOS-DI-A-MOCK-GRAPH-ONLY-PROD-IS-VERIFIED-001] Twin of `:shared`'s `KoinModuleVerifyTest`,
 * for the OTHER startable graph. That test proved the prod graph and nothing watched this one, so
 * `DeviceCapabilities` — bound only in the platform modules the mock graph never loads — passed
 * 2000+ green tests and crashed Home on the first mock launch. Every graph an Application class
 * can start gets its own verify, assembled from the REAL modules, never from a copy of their
 * binding list.
 *
 * The module set mirrors `MockPaparcarApp.startKoin` exactly. No boundary stand-ins are needed
 * here: unlike prod, `mockModule` itself binds the `:app`-owned and BaseLogin-owned contracts
 * (FakeAppNotificationManager, FakeAuthRepository, paparcarLoginConfig).
 */
@OptIn(KoinExperimentalAPI::class, KoinInternalApi::class)
class MockKoinGraphVerifyTest {

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
    fun should_resolveEveryConstructorDependency_when_mockGraphIsAssembled() {
        mockGraph.verify(
            extraTypes = listOf(
                // Provided at runtime by startKoin { androidContext(...) }, not by a definition.
                Context::class,
                // Function-typed constructor params (`() -> T`, `(A) -> T`) are inline factories
                // written in the module lambda itself — they cannot be "missing" as bindings.
                Function0::class,
                Function1::class,
                // `List<T>` params are unwrapped by verify() to their ELEMENT type: the list is
                // assembled inline in the module lambda (`listOf(get(), …)`), so the element
                // contract is what gets whitelisted, not List itself.
                com.rndeveloper.paparcar.domain.repository.UserScopedRepository::class,
                // OPTIONAL port: consumers take it nullable and the module fills it with
                // `getOrNull()` — the mock graph deliberately leaves it unbound (same degradation
                // as prod iOS, which has no impl either). Whitelisting matches that contract; if
                // it ever becomes a required `get()`, the prod verify in `:shared` catches it.
                com.rndeveloper.paparcar.domain.detection.ports.DrivingRouteStore::class,
                com.rndeveloper.paparcar.domain.diagnostics.LocalDiagnosticsLog::class,
                com.rndeveloper.paparcar.domain.detection.ports.TripTrail::class,
                // [IOS-F0-03] `DeviceCapabilities` is built from LITERALS (here in mockModule,
                // true/true); verify() reflects its constructor without seeing inside the lambda
                // and reads the Boolean as an unbound dependency. A bare Boolean can never be a
                // legitimate Koin binding, so no real missing definition can hide behind this.
                Boolean::class,
            ),
        )
    }

    /**
     * Population witness: the verify above would stay green over a graph that silently lost its
     * subjects. The very binding this ticket exists for must be among them, alongside the
     * consumers whose resolution used to crash.
     */
    @Test
    fun should_containTheReliabilityChainAndItsConsumers_when_theVerifiedModuleSetIsFlattened() {
        val mappingKeys = flatten(mockGraph).flatMap { it.mappings.keys }
        val roots = listOf(
            "DeviceCapabilities",
            "EvaluateDetectionReliabilityUseCase",
            "HomeViewModel",
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
