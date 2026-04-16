# Paparcar — Roadmap Deuda Técnica & Calidad

> Generado el 2026-04-16 a partir del análisis técnico exhaustivo del codebase.
> Cada tarea corresponde a una rama Git y uno o más commits siguiendo Conventional Commits.
> Las fases son secuenciales por impacto: completar una fase antes de empezar la siguiente.

---

## Resumen de fases

| Fase | Nombre | Tickets | Duración estimada | Estado |
|------|--------|---------|-------------------|--------|
| **QA-1** | Bugs Críticos (producción) | QA-001 → QA-006 | 1–2 semanas | ⏳ Pending |
| **QA-2** | Integridad Arquitectónica | ARCH-001 → ARCH-004 | 1–2 semanas | ⏳ Pending |
| **QA-3** | Cobertura de Tests | TEST-001 → TEST-004 | 2–3 semanas | ⏳ Pending |
| **QA-4** | UX & Estados Vacíos | UI-001 → UI-004 | 3–4 días | ⏳ Pending |
| **QA-5** | Limpieza & Refactor | REF-001 → REF-003 | 2–3 días | ⏳ Pending |

---

## PHASE QA-1 — Bugs Críticos

> Estos bugs afectan directamente a datos de producción o a funcionalidades core rotas.
> **Bloquean cualquier release.** Prioridad máxima.

---

### QA-001 — `clearActive()` no actualiza Firestore

**Problema:** Al liberar el parking, Room se limpia pero Firestore mantiene la sesión con `isActive = true`.
Al siguiente login, `syncParkingHistoryFromRemote()` re-importa la sesión activa antigua y el usuario
vuelve a ver su coche "aparcado".

**Archivo:** `data/repository/UserParkingRepositoryImpl.kt:59`

**Rama:**
```
bugfix/QA-001-clear-active-firestore-sync
```

**Commits:**
```
fix(data): mark parking session inactive in Firestore on clearActive() [QA-001]

fix(data): update UserParkingRepositoryImpl.clearActive() to persist isActive=false
to Firestore via userProfileDataSource before clearing local Room entry.
Prevents stale active sessions from being re-imported on next login via
syncParkingHistoryFromRemote().
```

**Código:**
```kotlin
override suspend fun clearActive(): Result<Unit> = runCatching {
    val active = dao.getActive()
    dao.clearActive()
    active?.let { entity ->
        currentUserId()?.let { userId ->
            userProfileDataSource.saveParkingSession(
                userId,
                entity.toDomain().copy(isActive = false).toParkingHistoryDto(),
            )
        }
    }
}
```

---

### QA-002 — Coordenadas del mapa de detalle siempre nulas

**Problema:** `App.kt:296` lee `lat`/`lon` de `savedStateHandle` en lugar de `arguments`.
`savedStateHandle` es para result-passing, no para nav arguments. Resultado: `ParkingLocationScreen`
siempre recibe `initialFocus = null` y no centra el mapa en la ubicación correcta.

**Archivo:** `App.kt:296–297`

**Rama:**
```
bugfix/QA-002-parking-location-nav-args
```

**Commits:**
```
fix(navigation): read lat/lon from backStack.arguments, not savedStateHandle [QA-002]

savedStateHandle is for back-navigation result passing. Nav arguments defined
via navArgument() must be read from backStack.arguments. This fixes
ParkingLocationScreen always receiving initialFocus=null, breaking the
"View on map" action from HistoryScreen.
```

**Código:**
```kotlin
// Antes (roto):
val lat = backStack.savedStateHandle.get<String>("lat")?.toDoubleOrNull()
val lon = backStack.savedStateHandle.get<String>("lon")?.toDoubleOrNull()

// Después (correcto):
val lat = backStack.arguments?.getString("lat")?.toDoubleOrNull()
val lon = backStack.arguments?.getString("lon")?.toDoubleOrNull()
```

---

### QA-003 — `userId = ""` cuando la sesión de auth es nula

**Problema:** `ConfirmParkingUseCase.kt:47` usa `?: ""` como fallback. Si `getCurrentSession()` devuelve null
(auth expirada, race condition en startup), se guarda una `UserParking` con `userId = ""` en Room y Firestore.
Esa sesión nunca puede ser encontrada por consultas de usuario ni asociada a ningún perfil.

**Archivo:** `domain/usecase/parking/ConfirmParkingUseCase.kt:47`

**Rama:**
```
bugfix/QA-003-confirm-parking-null-userid
```

**Commits:**
```
fix(domain): fail fast in ConfirmParkingUseCase when userId is null [QA-003]

Replace ?: "" fallback with an early Result.failure(PaparcarError.Auth.NotAuthenticated).
Saving a parking session with empty userId produces corrupt data that cannot be
queried or associated with any user profile.
```

**Código:**
```kotlin
val userId = authRepository.getCurrentSession()?.userId
    ?: return Result.failure(PaparcarError.Auth.NotAuthenticated)
```

> **Nota:** Requiere añadir `NotAuthenticated` a `PaparcarError.Auth` si no existe.

---

### QA-004 — Spots eliminados en Firestore no se borran de Room

**Problema:** `SpotRepositoryImpl.kt:65` solo llama `spotDao.upsertAll()` con el snapshot recibido, pero
nunca elimina entradas obsoletas. Si un spot expira o es eliminado en Firestore, permanece en Room
indefinidamente. La UI muestra plazas ya ocupadas como disponibles.

**Archivo:** `data/repository/SpotRepositoryImpl.kt:65`

**Rama:**
```
bugfix/QA-004-stale-spots-room-cleanup
```

**Commits:**
```
fix(data): delete expired spots from Room when Firestore snapshot updates [QA-004]

observeNearbySpots now calls replaceForBoundingBox() instead of upsertAll().
This atomically replaces all spots within the bbox with the current Firestore
snapshot, ensuring deleted/expired spots are removed from the local cache.
```

**SpotDao — nueva query:**
```kotlin
@Transaction
suspend fun replaceForBoundingBox(
    minLat: Double, maxLat: Double,
    minLon: Double, maxLon: Double,
    spots: List<SpotEntity>,
) {
    deleteInBoundingBox(minLat, maxLat, minLon, maxLon)
    upsertAll(spots)
}

@Query("""
    DELETE FROM spots
    WHERE latitude BETWEEN :minLat AND :maxLat
    AND longitude BETWEEN :minLon AND :maxLon
""")
suspend fun deleteInBoundingBox(
    minLat: Double, maxLat: Double,
    minLon: Double, maxLon: Double,
)
```

---

### QA-005 — Error Firestore mata el flujo Room de spots

**Problema:** En `SpotRepositoryImpl.observeNearbySpots()`, el collect del listener Firestore no tiene
`.catch{}`. Una excepción Firestore (timeout, permisos, `FirebaseException`) propagada al `channelFlow`
lo cancela, incluyendo el `launch { }` que emite desde Room. La UI queda sin spots hasta el siguiente
GPS fix que regenera el `channelFlow`.

**Archivo:** `data/repository/SpotRepositoryImpl.kt:65`

**Rama:**
```
bugfix/QA-005-firestore-error-isolation
```

**Commits:**
```
fix(data): isolate Firestore errors from Room stream in observeNearbySpots [QA-005]

Add .catch{} to the Firestore collect inside channelFlow so a Firestore error
is logged but does not cancel the Room observation coroutine. The UI continues
showing cached spots while Firestore is unavailable.
```

**Código:**
```kotlin
firebaseDataSource.observeNearbySpots(location.latitude, location.longitude, radiusMeters)
    .catch { e -> PaparcarLogger.w(TAG, "Firestore spots listener error — using cache", e) }
    .collect { dtoMap ->
        spotDao.replaceForBoundingBox(
            bbox.minLat, bbox.maxLat, bbox.minLon, bbox.maxLon,
            dtoMap.values.map { it.toEntity() },
        )
    }
```

---

### QA-006 — `registerTransitions()` sin manejo de errores mata la cadena GPS

**Problema:** `HomeViewModel.kt:62` llama `activityRecognitionManager.registerTransitions()` dentro de un
`flatMapLatest` sin try-catch. Si lanza (Play Services no disponible, permisos revocados entre
recomposiciones), la excepción escapa al `.catch{}` global del flujo GPS, matando toda la cadena
GPS + spots. La app queda sin mapa ni lista de plazas.

**Archivo:** `presentation/home/HomeViewModel.kt:62`

**Rama:**
```
bugfix/QA-006-activity-recognition-error-handling
```

**Commits:**
```
fix(presentation): wrap registerTransitions() call in runCatching [QA-006]

Activity Recognition registration is best-effort — if it fails (Play Services
unavailable, revoked permissions) the GPS + spots chain must not die.
Log the failure and continue; parking detection degrades gracefully to GPS-only.
```

**Código:**
```kotlin
if (permissionState.allPermissionsGranted) {
    runCatching { activityRecognitionManager.registerTransitions() }
        .onFailure { PaparcarLogger.w(TAG, "AR registration failed — GPS-only mode", it) }
    locationDataSource.observeBalancedLocation()
}
```

---

## PHASE QA-2 — Integridad Arquitectónica

> Mejoras estructurales que previenen bugs futuros y facilitan los tests de QA-3.
> Recomendado completar QA-1 antes de empezar esta fase.

---

### ARCH-001 — Desacoplar domain layer de `BaseLogin`

**Problema:** `ConfirmParkingUseCase` y `UserParkingRepositoryImpl` importan directamente
`com.apptolast.customlogin.domain.AuthRepository`. Esto viola la inversión de dependencias: el
domain layer no debe conocer ninguna librería externa. Hace imposible testear esas clases sin el JAR
de BaseLogin.

**Archivos:**
- `domain/usecase/parking/ConfirmParkingUseCase.kt:5`
- `data/repository/UserParkingRepositoryImpl.kt:2`

**Rama:**
```
refactor/ARCH-001-decouple-auth-from-domain
```

**Commits:**
```
refactor(domain): introduce CurrentUserProvider interface to decouple from BaseLogin [ARCH-001]

Add domain/auth/CurrentUserProvider.kt as the only auth abstraction the domain
layer knows about. Inject it into ConfirmParkingUseCase and UserParkingRepositoryImpl
instead of com.apptolast.customlogin.domain.AuthRepository.
```
```
refactor(data): implement CurrentUserProvider via BaseLogin AuthRepository [ARCH-001]

Add data/auth/CurrentUserProviderImpl.kt adapting BaseLogin's AuthRepository
to the domain interface. Wire it in dataModule Koin.
```

**Código:**
```kotlin
// domain/auth/CurrentUserProvider.kt (nueva interfaz — SIN imports externos)
interface CurrentUserProvider {
    suspend fun currentUserId(): String?
}

// data/auth/CurrentUserProviderImpl.kt (adaptador en data layer)
class CurrentUserProviderImpl(
    private val authRepository: com.apptolast.customlogin.domain.AuthRepository,
) : CurrentUserProvider {
    override suspend fun currentUserId(): String? =
        authRepository.getCurrentSession()?.userId
}
```

---

### ARCH-002 — Extraer use cases para observar y liberar la sesión activa

**Problema:** `HomeViewModel.kt:51–56` llama directamente `userParkingRepository.observeActiveSession()`
y `userParkingRepository.clearActive()`, saltándose la capa domain. Lógica de negocio directamente
en la capa de presentación.

**Archivo:** `presentation/home/HomeViewModel.kt:51`

**Rama:**
```
refactor/ARCH-002-extract-parking-session-usecases
```

**Commits:**
```
refactor(domain): add ObserveActiveParkingSessionUseCase [ARCH-002]
```
```
refactor(domain): add ReleaseActiveParkingSessionUseCase with Firestore sync [ARCH-002]

Moves clearActive() + Firestore update logic out of the repository and into
a dedicated use case. The repository stays as a data-only abstraction.
```
```
refactor(presentation): replace direct repo calls with new use cases in HomeViewModel [ARCH-002]
```

**Código:**
```kotlin
// domain/usecase/parking/ObserveActiveParkingSessionUseCase.kt
class ObserveActiveParkingSessionUseCase(
    private val repo: UserParkingRepository,
) {
    operator fun invoke(): Flow<UserParking?> = repo.observeActiveSession()
}

// domain/usecase/parking/ReleaseActiveParkingSessionUseCase.kt
class ReleaseActiveParkingSessionUseCase(
    private val reportSpotReleased: ReportSpotReleasedUseCase,
    private val userParkingRepository: UserParkingRepository,
) {
    suspend operator fun invoke(
        lat: Double,
        lon: Double,
        parking: UserParking?,
    ): Result<Unit> { ... }
}
```

---

### ARCH-003 — Corregir `isLoading` — siempre `false`

**Problema:** `HomeState.isLoading` se inicializa a `false` y nunca se pone a `true` en ningún lugar
del ViewModel. Cualquier spinner/skeleton condicionado a este flag nunca se muestra.

**Archivo:** `presentation/home/HomeViewModel.kt`

**Rama:**
```
fix/ARCH-003-isloading-state
```

**Commits:**
```
fix(presentation): set isLoading=true during initial GPS acquisition [ARCH-003]

Add .onStart { updateState { copy(isLoading = true) } } to the location flow
chain. Set it back to false in .onEach when the first GPS fix arrives.
This enables the spots skeleton state in SpotsSection.
```

---

### ARCH-004 — GPS subscription — evitar re-suscripción Firestore por cada fix

**Problema:** `HomeViewModel.kt:76` — el `flatMapLatest` de spots se dispara por cada `GpsPoint` emitido
(cada 3–5 s en balanced mode). Cada emisión cancela y re-abre el listener Firestore. Hasta 20
subscripciones Firestore por minuto de uso activo.

**Archivo:** `presentation/home/HomeViewModel.kt:76`

**Rama:**
```
perf/ARCH-004-gps-spot-subscription-throttle
```

**Commits:**
```
perf(presentation): throttle Firestore re-subscription to 100m GPS displacement [ARCH-004]

Add distinctUntilChanged with haversineMeters threshold before the flatMapLatest
that opens observeNearbySpots(). Prevents opening a new Firestore listener
on every GPS fix when the user is stationary or moving slowly.
```

**Código:**
```kotlin
private companion object {
    const val SPOT_RESUBSCRIBE_THRESHOLD_METERS = 100.0
}

// En el init, antes del segundo flatMapLatest:
.distinctUntilChanged { old, new ->
    haversineMeters(
        old.latitude, old.longitude,
        new.latitude, new.longitude,
    ) < SPOT_RESUBSCRIBE_THRESHOLD_METERS
}
.flatMapLatest { userLocation ->
    observeNearbySpots(userLocation, ObserveNearbySpotsUseCase.DEFAULT_SEARCH_RADIUS_METERS)
        .catch { ... }
}
```

---

## PHASE QA-3 — Cobertura de Tests

> El proyecto tiene **cero tests**. Esta fase cubre la lógica de negocio crítica en orden de impacto.
> Usar fakes sobre mocks (ver CLAUDE.md). Naming: `should_expectedBehavior_when_condition`.

---

### TEST-001 — Tests para `CalculateParkingConfidenceUseCase`

**Cobertura objetivo:** fast path / slow path, bonuses de accuracy/speed/STILL, umbrales HIGH/MEDIUM/LOW.

**Rama:**
```
test/TEST-001-calculate-parking-confidence
```

**Commits:**
```
test(domain): add unit tests for CalculateParkingConfidenceUseCase fast path [TEST-001]
```
```
test(domain): add unit tests for CalculateParkingConfidenceUseCase slow path [TEST-001]
```
```
test(domain): add unit tests for confidence boundary values (HIGH/MEDIUM/LOW) [TEST-001]
```

**Casos a cubrir:**
```kotlin
should_returnHigh_when_vehicleExitAndStoppedOverThreshold()
should_returnMedium_when_slowPathAndPartialSignals()
should_returnLow_when_shortStopWithNoExitSignal()
should_returnNotYet_when_stoppedUnderMinimumDuration()
should_applyAccuracyBonus_when_gpsAccuracyIsHigh()
should_notExceedHighThreshold_when_multipleSmallBonuses()
```

---

### TEST-002 — Tests para `ParkingDetectionCoordinator`

**Cobertura objetivo:** tres paths de confirmación (usuario / vehicle-exit / slow-path), reset en denial,
guard de maxNoMovementMs, CANDIDATE phase observation window.

**Rama:**
```
test/TEST-002-parking-detection-coordinator
```

**Commits:**
```
test(domain): add fake LocationDataSource and test infrastructure [TEST-002]
```
```
test(domain): test user-confirmed parking path in ParkingDetectionCoordinator [TEST-002]
```
```
test(domain): test vehicle-exit auto-confirmation path [TEST-002]
```
```
test(domain): test slow-path auto-confirmation and denial reset [TEST-002]
```
```
test(domain): test maxNoMovementMs spurious session guard [TEST-002]
```

---

### TEST-003 — Tests para `SpotRepositoryImpl`

**Cobertura objetivo:** offline-first strategy, Room-first emission, Firestore error fallback, bounding box.

**Rama:**
```
test/TEST-003-spot-repository-offline-first
```

**Commits:**
```
test(data): add FakeSpotDao and FakeFirebaseDataSource [TEST-003]
```
```
test(data): verify Room cache emits before Firestore in observeNearbySpots [TEST-003]
```
```
test(data): verify Firestore error falls back to Room in getNearbySpots [TEST-003]
```
```
test(data): verify stale spots deleted on Firestore snapshot update [TEST-003]
```

---

### TEST-004 — Tests para `ConfirmParkingUseCase`

**Cobertura objetivo:** happy path, userId null early exit, geofence radio por sizeCategory, orden de side-effects.

**Rama:**
```
test/TEST-004-confirm-parking-usecase
```

**Commits:**
```
test(domain): add fakes for GeofenceManager, AppNotificationManager, ParkingEnrichmentScheduler [TEST-004]
```
```
test(domain): verify ConfirmParkingUseCase returns failure when userId is null [TEST-004]
```
```
test(domain): verify geofence radius computation per VehicleSize [TEST-004]
```
```
test(domain): verify side-effect ordering: save → enrich → geofence → notify [TEST-004]
```

---

## PHASE QA-4 — UX & Estados Vacíos

> Mejoras de experiencia de usuario derivadas del análisis. No bloquean releases pero impactan
> la percepción de calidad.

---

### UI-001 — Skeleton de carga para la sección de spots

**Problema:** Cuando `isLoading = true` (tras corregir ARCH-003), la UI muestra `HomeEmptySpots` en lugar
de un estado de carga. El usuario ve "no hay plazas" durante el cold start.

**Archivo:** `presentation/home/components/HomeSheetContent.kt`

**Rama:**
```
feat/UI-001-spots-loading-skeleton
```

**Commits:**
```
feat(ui): add SpotsSkeletonList composable for loading state [UI-001]
```
```
feat(ui): show skeleton instead of empty state while isLoading=true in SpotsSection [UI-001]
```

---

### UI-002 — Feedback diferenciado cuando el filtro de tamaño produce cero resultados

**Problema:** Si el filtro activo produce `filteredSpots.isEmpty()`, se muestra el mismo `HomeEmptySpots`
que cuando no hay plazas en absoluto. El usuario no sabe si debe ampliar la búsqueda o si el filtro
es el culpable.

**Archivo:** `presentation/home/components/HomeSheetContent.kt`

**Rama:**
```
feat/UI-002-empty-filtered-spots-feedback
```

**Commits:**
```
feat(ui): add HomeEmptyFilteredSpots composable with clear-filter CTA [UI-002]
```
```
feat(ui): show differentiated empty state when sizeFilter yields no results [UI-002]
```
```
feat(i18n): add home_empty_filtered_spots strings (EN + ES + all locales) [UI-002]
```

---

### UI-003 — Accesibilidad: content descriptions y roles semánticos

**Problema:** `ParkingSpotItem`, marcadores del mapa y botones del `HomeNavBar` carecen de
`contentDescription` y `Role.Button` en sus semánticas. La app no es usable con TalkBack.

**Rama:**
```
feat/UI-003-accessibility-content-descriptions
```

**Commits:**
```
feat(ui): add semantics Role.Button to ParkingSpotItem Surface [UI-003]
```
```
feat(ui): add contentDescription to map markers (spot, cluster, parking) [UI-003]
```
```
feat(ui): add contentDescription to HomeNavBar navigate and release buttons [UI-003]
```
```
feat(i18n): add cd_spot_item, cd_map_marker_spot, cd_map_marker_cluster strings [UI-003]
```

---

### UI-004 — Eliminar parámetro `badge` muerto en `HomeSectionHeader`

**Problema:** `HomeSectionHeader` expone `badge: String? = null` pero ninguna llamada en el codebase
lo pasa con valor no-null. Es dead code que ensucia la API pública del composable.

**Archivo:** `presentation/home/components/HomeSheetContent.kt:278`

**Rama:**
```
refactor/UI-004-remove-dead-badge-param
```

**Commits:**
```
refactor(ui): remove unused badge parameter from HomeSectionHeader [UI-004]

No call site in the codebase passes a non-null badge. Remove the parameter
and its rendering block to keep the composable API minimal. Re-add when
there is a real use case.
```

---

## PHASE QA-5 — Limpieza & Refactor Menor

> Deuda técnica menor que no afecta funcionalidad pero mejora mantenibilidad.
> Hacer en una sola sesión de cleanup al final.

---

### REF-001 — Constante `MAP_TYPE_NORMAL` faltante

**Rama:** `refactor/REF-001-map-type-normal-constant`

**Commit:**
```
refactor(presentation): add MAP_TYPE_NORMAL constant to HomeViewModel companion [REF-001]

Eliminates the only hardcoded string "NORMAL" in toPreferenceString(), making
all three map type constants consistent in their companion object definition.
```

---

### REF-002 — Named arguments en llamada a `MainAppNavigation`

**Rama:** `refactor/REF-002-named-args-main-app-navigation`

**Commits:**
```
refactor(navigation): use named arguments in MainAppNavigation call site [REF-002]

8 positional parameters including multiple lambdas are impossible to read
without cross-referencing the function signature. Named args make intent explicit.
```
```
refactor(navigation): rename onHandleIntent to onMarkOnboardingCompleted [REF-002]
```

---

### REF-003 — Reemplazar debounce manual con operadores Flow

**Rama:** `refactor/REF-003-search-debounce-flow-operator`

**Commits:**
```
refactor(presentation): replace manual Job/delay search debounce with Flow.debounce [REF-003]

The current pattern of cancelling a Job and calling delay() is functional but
not idiomatic. A MutableStateFlow + .debounce() is declarative, testable,
and eliminates the mutable Job? field.
```

---

## Referencia rápida: todas las ramas

```
bugfix/QA-001-clear-active-firestore-sync
bugfix/QA-002-parking-location-nav-args
bugfix/QA-003-confirm-parking-null-userid
bugfix/QA-004-stale-spots-room-cleanup
bugfix/QA-005-firestore-error-isolation
bugfix/QA-006-activity-recognition-error-handling

refactor/ARCH-001-decouple-auth-from-domain
refactor/ARCH-002-extract-parking-session-usecases
fix/ARCH-003-isloading-state
perf/ARCH-004-gps-spot-subscription-throttle

test/TEST-001-calculate-parking-confidence
test/TEST-002-parking-detection-coordinator
test/TEST-003-spot-repository-offline-first
test/TEST-004-confirm-parking-usecase

feat/UI-001-spots-loading-skeleton
feat/UI-002-empty-filtered-spots-feedback
feat/UI-003-accessibility-content-descriptions
refactor/UI-004-remove-dead-badge-param

refactor/REF-001-map-type-normal-constant
refactor/REF-002-named-args-main-app-navigation
refactor/REF-003-search-debounce-flow-operator
```

---

## Criterios de completado por fase

### QA-1 ✅ Completa cuando:
- [ ] `QA-001`: login después de liberar parking no muestra sesión activa antigua
- [ ] `QA-002`: "Ver en mapa" desde Historia centra el mapa en la plaza correcta
- [ ] `QA-003`: app retorna error visible si el usuario no está autenticado al confirmar parking
- [ ] `QA-004`: plazas expiradas desaparecen de la lista cuando Firestore las elimina
- [ ] `QA-005`: un error Firestore no vacía la lista de plazas (Room sigue emitiendo)
- [ ] `QA-006`: fallo de Activity Recognition no mata el mapa ni la lista de plazas

### QA-2 ✅ Completa cuando:
- [ ] `ARCH-001`: `ConfirmParkingUseCase` no importa ningún símbolo de `customlogin`
- [ ] `ARCH-002`: `HomeViewModel` no importa ningún `Repository` directamente
- [ ] `ARCH-003`: spinner/skeleton visible durante el cold start hasta llegar el primer GPS fix
- [ ] `ARCH-004`: con logging, confirmar que el listener Firestore no se reabre en menos de 100m

### QA-3 ✅ Completa cuando:
- [ ] `./gradlew :composeApp:allTests` pasa con >0 tests
- [ ] `CalculateParkingConfidenceUseCase`: ≥6 casos cubiertos
- [ ] `ParkingDetectionCoordinator`: los 3 paths de confirmación testeados
- [ ] `SpotRepositoryImpl`: offline-first strategy verificada con fakes
- [ ] `ConfirmParkingUseCase`: userId null early-exit testeado

### QA-4 ✅ Completa cuando:
- [ ] `UI-001`: skeleton visible en cold start antes de llegar spots
- [ ] `UI-002`: al seleccionar filtro sin resultados aparece CTA para limpiar filtro
- [ ] `UI-003`: TalkBack puede navegar por la lista de spots y activar cada item
- [ ] `UI-004`: `HomeSectionHeader` no tiene parámetro `badge`

### QA-5 ✅ Completa cuando:
- [ ] `REF-001..003`: 0 warnings de "unused" / "hardcoded" en los archivos modificados
