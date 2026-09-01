package com.rndeveloper.paparcar.di

import android.content.Context
import com.apptolast.baselogin.domain.AuthRepository
import com.rndeveloper.paparcar.domain.notification.AppNotificationManager
import com.rndeveloper.paparcar.fakes.data.repository.FakeAuthRepository
import com.rndeveloper.paparcar.notification.FakeAppNotificationManager
import kotlin.test.Test
import kotlin.test.assertTrue
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.test.verify.verify

/**
 * [DET-KOIN-MODULE-VERIFY-001] A `get()` with no definition on the other side breaks neither the
 * build nor any test: it throws `NoDefinitionFoundException` the first time someone asks for that
 * object — for detection, that means mid-drive, exactly where nothing can be debugged.
 * `DET-DI-DETECTION-MODULE-001` moved 36 registrations between files backed only by a hand-made
 * set diff and a manual app start. This test makes the graph a compile-adjacent fact instead:
 * deleting a definition another one consumes fails here, not on the road.
 *
 * What `verify()` checks: for every definition declared as a CONCRETE Kotlin class (all the
 * detection use cases, the coordinator, the platform monitors), each constructor parameter type
 * must be bound somewhere in the verified module set. Definitions declared behind an interface
 * (`single<Foo> { FooImpl(get()) }`) have no reflectable constructor and are skipped — so this
 * test proves the CONSUMER side of the graph, which is where a missing binding actually bites.
 *
 * The module set mirrors `PaparcarApp` (prod): presentation + domain (which `includes`
 * detectionModule) + data + androidDetection + androidPlatform. The two bindings owned outside
 * `:shared` are stood in by their production fakes, declared under the same contracts
 * `PaparcarApp` binds them under: [AppNotificationManager] (owned by `:app`'s `appModule`) and
 * [AuthRepository] (owned by BaseLogin's `initLoginKoin`). If a shared definition is ever moved
 * out to `:app`, this test fails — that is intended: `:shared` must stay resolvable from its own
 * modules plus these two declared boundary contracts.
 */
@OptIn(KoinExperimentalAPI::class, KoinInternalApi::class)
class KoinModuleVerifyTest {

    /** Same contracts `PaparcarApp` binds from outside `:shared`, stood in by production fakes. */
    private val boundaryModule = module {
        single<AppNotificationManager> { FakeAppNotificationManager() }
        single<AuthRepository> { FakeAuthRepository() }
    }

    /** The production Android graph, assembled exactly as `PaparcarApp.startKoin` lists it. */
    private val productionGraph = module {
        includes(
            presentationModule,
            domainModule, // includes(detectionModule)
            dataModule,
            androidDetectionModule,
            androidPlatformModule,
            boundaryModule,
        )
    }

    @Test
    fun should_resolveEveryConstructorDependency_when_productionAndroidGraphIsAssembled() {
        productionGraph.verify(
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
            ),
        )
    }

    /**
     * Population witness: an emptied `detectionModule` — or a wrapper that silently stops
     * including it — would make the verify above pass with nothing to look at. A green with
     * no subjects and a green with no findings must not be the same green.
     */
    @Test
    fun should_containTheDetectionRoots_when_theVerifiedModuleSetIsFlattened() {
        val mappingKeys = flatten(productionGraph).flatMap { it.mappings.keys }
        val roots = listOf(
            "CoordinatorParkingDetector",
            "ConfirmParkingUseCase",
            "RunDepartureCheckUseCase",
            "ObserveDetectionReadinessUseCase",
            "GeofenceManager",
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
