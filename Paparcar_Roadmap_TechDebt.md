# Paparcar — Roadmap Deuda Técnica & Calidad

> Generado el 2026-04-16. Actualizado el 2026-04-25 con la fase QA-6 (refactors
> derivados de Phase 4.5 — UX Refinements).
> Cada tarea corresponde a una rama Git y uno o más commits siguiendo Conventional Commits.
> Las fases son secuenciales por impacto: completar una fase antes de empezar la siguiente.

---

## Resumen de fases

| Fase | Nombre | Tickets | Duración estimada | Estado |
|------|--------|---------|-------------------|--------|
| **QA-1** | Bugs Críticos (producción) | QA-001 → QA-006 | 1–2 semanas | ✅ Done |
| **QA-2** | Integridad Arquitectónica | ARCH-002 → ARCH-004 | 1–2 semanas | ✅ Done (ARCH-001 cancelled) |
| **QA-3** | Cobertura de Tests | TEST-001 → TEST-004 | 2–3 semanas | ✅ Partial (16 tests) |
| **QA-4** | UX & Estados Vacíos | UI-001 → UI-004 | 3–4 días | ✅ Done |
| **QA-5** | Limpieza & Refactor | REF-001 → REF-003 | 2–3 días | ✅ Done |
| **QA-6** | Refactors UX Phase 4.5 | D-* / NET-ARCH-* / NAV-ARCH-* / THEME-ARCH-* / DS-* | 2–3 semanas | 📦 ~60% (ramas listas, sin merge) |

---

## PHASE QA-1 — Bugs Críticos

> Estos bugs afectan directamente a datos de producción o a funcionalidades core rotas.
> **Bloquean cualquier release.** Prioridad máxima.

---

### QA-001 — `clearActive()` no actualiza Firestore — ✅ Done

**Commit:** `acc4a0c` fix(data): mark parking session inactive in Firestore on clearActive() [QA-001]

---

### QA-002 — Coordenadas del mapa de detalle siempre nulas — ✅ Done

**Commit:** `a74a9ba` fix(navigation): read lat/lon from backStack.arguments, not savedStateHandle [QA-002]

---

### QA-003 — `userId = ""` cuando la sesión de auth es nula — ✅ Done

**Commit:** `f17615c` fix(domain): fail fast in ConfirmParkingUseCase when userId is null [QA-003]

---

### QA-004 — Spots eliminados en Firestore no se borran de Room — ✅ Done

**Commits:** `d20c774` + `791f2c8` fix(data): delete expired spots + consolidate QA-004/QA-005 [QA-004]

---

### QA-005 — Error Firestore mata el flujo Room de spots — ✅ Done

**Commit:** `791f2c8` — consolidated with QA-004 fix

---

### QA-006 — `registerTransitions()` sin manejo de errores mata la cadena GPS — ✅ Done

**Commit:** `472c4cc` fix(presentation): wrap registerTransitions() in runCatching [QA-006]

---

## PHASE QA-2 — Integridad Arquitectónica

> Mejoras estructurales que previenen bugs futuros y facilitan los tests de QA-3.
> Recomendado completar QA-1 antes de empezar esta fase.

---

### ARCH-001 — Desacoplar domain layer de `BaseLogin` — ❌ Cancelled

**Decisión (2026-04-16):** Implementado en rama `refactor/ARCH-001-decouple-auth-from-domain` (commit `9847422`) y revertido deliberadamente (commit `3c71996`). BaseLogin es una librería propia (JitPack), no una dependencia de terceros. Añadir `CurrentUserProvider` como capa de abstracción intermedia es sobre-ingeniería sin beneficio real.

---

### ARCH-002 — Extraer use cases para observar y liberar la sesión activa — ✅ Done

**Commits:** `07b097e` + `402d684` — Added then simplified (dropped delegator without logic) [ARCH-002]

> Nota: `ObserveActiveParkingSessionUseCase` fue creada y luego eliminada por ser un delegador sin lógica. `ReleaseActiveParkingSessionUseCase` se mantiene.

---

### ARCH-003 — Corregir `isLoading` — siempre `false` — ✅ Done

**Commit:** `70ae65d` fix(presentation): set isLoading=true during initial GPS acquisition [ARCH-003]

---

### ARCH-004 — GPS subscription — evitar re-suscripción Firestore por cada fix — ✅ Done

**Commit:** `87b1de1` perf(presentation): throttle Firestore re-subscription to 100m GPS displacement [ARCH-004]

---

## PHASE QA-3 — Cobertura de Tests

> El proyecto tiene **cero tests**. Esta fase cubre la lógica de negocio crítica en orden de impacto.
> Usar fakes sobre mocks (ver CLAUDE.md). Naming: `should_expectedBehavior_when_condition`.

---

### TEST-001 — Tests para `CalculateParkingConfidenceUseCase` — ✅ Done

**Commit:** `1577df3` — 6 test cases covering fast/slow paths and boundary values [FND-007]

---

### TEST-002 — Tests para `ParkingDetectionCoordinator` — ⏳ Pending

**Cobertura objetivo:** tres paths de confirmación (usuario / vehicle-exit / slow-path), reset en denial, guard de maxNoMovementMs.

---

### TEST-003 — Tests para `SpotRepositoryImpl` — ⏳ Pending

**Cobertura objetivo:** offline-first strategy, Room-first emission, Firestore error fallback, bounding box cleanup.

---

### TEST-004 — Tests para `ConfirmParkingUseCase` — ✅ Partial

**Commit:** `1577df3` — ConfirmParkingUseCase test exists (FND-007). Falta: geofence radius per VehicleSize, side-effect ordering.

---

## PHASE QA-4 — UX & Estados Vacíos

> Mejoras de experiencia de usuario derivadas del análisis. No bloquean releases pero impactan
> la percepción de calidad.

---

### UI-001 — Skeleton de carga para la sección de spots — ✅ Done

**Commit:** `d4b746c` feat(home): add shimmer skeleton while nearby spots load [UI-001]

---

### UI-002 — Feedback diferenciado cuando el filtro de tamaño produce cero resultados — ✅ Done

**Commit:** `5d889b6` feat(home): show clear-filter CTA when size filter yields no results [UI-002]

---

### UI-003 — Accesibilidad: content descriptions y roles semánticos — ✅ Done

**Commit:** `f8ee06b` feat(ui): add Role.Button semantics + content description to ParkingSpotItem [UI-003]

---

### UI-004 — Eliminar parámetro `badge` muerto en `HomeSectionHeader` — ✅ Done

**Commit:** `b658d67` refactor(home): remove unused badge param from HomeSectionHeader [UI-004]

---

## PHASE QA-5 — Limpieza & Refactor Menor

> Deuda técnica menor que no afecta funcionalidad pero mejora mantenibilidad.
> Hacer en una sola sesión de cleanup al final.

---

### REF-001 — Constante `MAP_TYPE_NORMAL` faltante — ✅ Done

**Commit:** `8194a78` refactor(home): extract MAP_TYPE_NORMAL constant in HomeViewModel [REF-001]

---

### REF-002 — Named arguments en llamada a `MainAppNavigation` — ✅ Done

**Commit:** `7ec48f5` refactor(nav): use named args in MainAppNavigation call + rename onHandleIntent [REF-002]

---

### REF-003 — Reemplazar debounce manual con operadores Flow — ✅ Done

**Commit:** `350b567` refactor(home): replace manual Job+delay search debounce with Flow.debounce [REF-003]

---

## PHASE QA-6 — Refactors UX Phase 4.5

> Tareas de arquitectura/refactor que habilitan las features A, B, C, D, E
> del roadmap de producto (`Paparcar_Roadmap_Completo.md` §7.5).
> Hacer **antes** o **en paralelo** con sus features correspondientes.

> Tamaños: `[SHORT]` (<2h) · `[MEDIUM]` (2–6h) · `[LARGE]` (>6h / dividir).

> **Estado actual (auditado 2026-04-27).** 6 de las 9 tareas QA-6 están entregadas
> en ramas independientes pero **ninguna está mergeada en `master`**. Ver detalle
> por tarea abajo y la tabla "Estado de ramas" en `Paparcar_Roadmap_Completo.md` §7.5.
> Tareas pendientes 100%: `THEME-ARCH-002`, `DS-001`, `NAV-ARCH-002`.

---

### D-001 — Extraer `PaparcarMapView` reutilizable — 📦 Code-complete  `[LARGE]`

**Rama:** `refactor/D-001-paparcar-map-view` @ `23e47b4` — sin merge a master.

**Motivación.** El mapa core hoy vive en
`presentation/home/components/PlatformMap.kt` y se consume desde `HomeScreen`,
`ParkingLocationScreen` y será necesario en `AddFreeSpotScreen`. Mezcla
responsabilidades de UI específica de Home (clusters, markers de spot, badges,
loading arc, indicador de posición central) con la base genérica del mapa.

**Plan.**
1. Mover `PlatformMap` → `ui/components/PaparcarMapView.kt` y renombrar.
2. Definir `PaparcarMapConfig`:
   ```kotlin
   data class PaparcarMapConfig(
       val interactionMode: MapInteractionMode = MapInteractionMode.FULL,
       val showFreeSpotOverlays: Boolean = true,
       val showAnimatedCenterPin: Boolean = false,
       val initialCamera: CameraTarget? = null,
       val mapType: MapType = MapType.NORMAL,
       val styleMode: MapStyleMode = MapStyleMode.AUTO, // AUTO | LIGHT | DARK
   )
   enum class MapInteractionMode { FULL, POSITION_ONLY, READ_ONLY }
   enum class MapStyleMode { AUTO, LIGHT, DARK }
   ```
3. Callbacks opcionales: `onCameraMove`, `onMapReady`, `onSpotClick`.
4. Sacar fuera del componente lo que es exclusivo de Home: lista de spots,
   sheet, FAB column, glass overlays. Eso se queda en `HomeScreen`.
5. La animación de "pin drop" (Feature C) vive en `PaparcarMapView` cuando
   `showAnimatedCenterPin = true`; usa `Animatable` con `Spring(MediumBouncy)`
   sobre la posición Y del pin (drop de -8dp a 0dp + escala 1.1→1.0).

**Criterios de aceptación.**
- [ ] HomeScreen, AddFreeSpotScreen y ParkingLocationScreen consumen `PaparcarMapView` con configuraciones distintas.
- [ ] Ningún recompose extra introducido vs. el `PlatformMap` actual (verificar con Layout Inspector).
- [ ] `READ_ONLY` desactiva drag, zoom y `onCameraMove`.
- [ ] Loading overlay y crosshair siguen funcionando idénticos en HomeScreen.

---

### D-002 — Extraer `PaparcarBottomActionBar` — 📦 Code-complete  `[MEDIUM]`

**Rama:** `refactor/D-002-paparcar-bottom-action-bar` @ `fb86fb1` — sin merge a master. (Misma commit incluida en `feat/C-add-free-spot`.)

**Motivación.** El componente `HomeNavBar` en
`presentation/home/components/HomeReportBar.kt` ya implementa la "barra de
acción inferior" (icono + texto + onClick + onPrimary container). Es la base
del componente reutilizable que necesitan HomeScreen ("Navegar a…") y
AddFreeSpotScreen ("Publicar plaza").

**Plan.**
1. Mover a `ui/components/PaparcarBottomActionBar.kt`.
2. API:
   ```kotlin
   @Composable
   fun PaparcarBottomActionBar(
       label: String,
       onClick: () -> Unit,
       modifier: Modifier = Modifier,
       icon: ImageVector? = Icons.Outlined.Navigation,
       isLoading: Boolean = false,
       enabled: Boolean = true,
   )
   ```
3. Estados:
   - **Normal**: surface `colorScheme.primary` + `onPrimary`.
   - **Disabled**: alpha 0.4f sobre surface; ignora ripple.
   - **Loading**: reemplaza icon por `CircularProgressIndicator` 18dp; ignora taps.
4. Adaptación tema: usa `MaterialTheme.colorScheme` — sin colores hardcoded.
5. Eliminar la versión actual `HomeNavBar` y migrar el call site de `HomeScreen` al nuevo componente.

**Criterios de aceptación.**
- [ ] HomeScreen y AddFreeSpotScreen comparten exactamente la misma altura, padding y typografía en la barra.
- [ ] Loading state visible al pulsar "Publicar" y vuelve a Normal en éxito o error.
- [ ] Funciona en modo claro y oscuro sin retoques.

---

### NAV-ARCH-001 — Reestructurar NavGraph para 3 tabs + AddFreeSpot — 📦 Code-complete (parcial)  `[MEDIUM]`

**Estado:** la parte "3 tabs" está en `feat/A-nav-tabs-vehicles` @ `17658e4` y la parte "ruta `add_free_spot`" está en `feat/C-add-free-spot` @ `c206eba`. Ambas ramas sin merge. La unificación final se valida sólo cuando ambas estén mergeadas; mientras tanto cada rama por separado pasa su build.

**Motivación.** Feature A reduce el BottomNav a 3 tabs y Feature C añade una
nueva ruta. El NavGraph actual en `App.kt` usa rutas string-based con `Routes`
object — la transformación es directa.

**Plan.**
1. Eliminar de `Routes` constants `MY_CAR` y `HISTORY`.
2. Añadir `Routes.VEHICLES = "vehicles"` y `Routes.ADD_FREE_SPOT = "add_free_spot"`.
3. Actualizar `BOTTOM_NAV_ROUTES` y `bottomNavItems`.
4. La ruta `vehicles` recibe un `vehicleId?` opcional para deep-link a un detalle concreto.
5. La ruta `add_free_spot` no participa en `BOTTOM_NAV_ROUTES` (no muestra BottomNav).
6. Conservar `Routes.PARKING_LOCATION` — el deep-link "Ver en mapa" desde el historial unificado debe seguir funcionando.

**Criterios de aceptación.**
- [ ] Backstack queda limpio al volver desde AddFreeSpot a Home (1 sólo `popBackStack`).
- [ ] Deep-link desde notificación (si llega) sigue resolviendo `PARKING_LOCATION`.
- [ ] No se rompen los `navigateToTab` existentes.

---

### THEME-ARCH-001 — Migrar `darkModeEnabled: Boolean` a `ThemeMode` enum — 📦 Code-complete  `[SHORT]`

**Rama:** `feat/B-theme-mode-tri-state` @ `51a9bf5` — sin merge a master. La migración lazy desde el key legacy `dark_mode_enabled` está implementada en Android (`AndroidAppPreferences`) e iOS (`IosAppPreferences`).

**Motivación.** Feature B exige tres modos. El esquema actual sólo soporta dos.

**Plan.**
1. `ThemeMode { LIGHT, DARK, SYSTEM }` en `domain/preferences`.
2. `AppPreferences.themeMode: ThemeMode` (getter) + `setThemeMode(mode)`.
3. Migración SharedPreferences: si existe `dark_mode_enabled`, leerlo una vez como `LIGHT`/`DARK` y persistir el nuevo key `theme_mode`. Borrar el viejo key.
4. `AppViewModel` calcula `darkTheme: Boolean` en composición usando `isSystemInDarkTheme()` cuando `mode == SYSTEM`.
5. `PaparcarTheme(darkTheme: Boolean)` se conserva — sólo cambia quién decide el booleano.

**Criterios de aceptación.**
- [ ] Tras la migración, usuarios actuales con `darkModeEnabled = true` aterrizan en `ThemeMode.DARK` y los que tenían `false` en `LIGHT`.
- [ ] No quedan referencias a `darkModeEnabled` ni a `KEY_DARK_MODE_ENABLED` en el código.

---

### THEME-ARCH-002 — Estilo de Google Maps por modo de tema — ⏳ Pending  `[MEDIUM]`

**Bloqueante:** depende del merge de D-001 y de B-THEME para tener `MapStyleMode` parametrizable y `themeMode` accesible. Iniciar después del orden de merge sugerido.

**Motivación.** Hoy el JSON `DARK_MAP_STYLE` se aplica si la luminancia del
fondo del tema es < 0.5 — funciona para dark, pero no hay JSON light explícito
y la lógica está enterrada en `PlatformMap`. Con `MapStyleMode` parametrizado
(D-001), el estilo se elige fuera del componente y se inyecta como dato.

**Plan.**
1. Crear `LIGHT_MAP_STYLE` (paleta neutra, baja saturación, sin POIs ruidosos).
2. `PaparcarMapView` recibe `MapStyleMode` y resuelve el JSON apropiado.
3. Cuando `MapStyleMode.AUTO`, calcula el modo a partir de `MaterialTheme.colorScheme.background.luminance()` (mantener el comportamiento actual).
4. Conservar `DARK_MAP_STYLE` y empaquetar ambos en `ui/components/MapStyles.kt`.

---

### NET-ARCH-001 — Arquitectura de `ConnectivityObserver` — 📦 Code-complete  `[MEDIUM]`

**Rama:** `feat/E-connectivity-observer` @ `54a5dbc` — sin merge a master. Implementación: `domain/connectivity/{ConnectivityStatus, ConnectivityObserver}`, `androidMain/connectivity/AndroidConnectivityObserver`, `iosMain/ios/stub/StubConnectivityObserver`. Singleton inyectado vía Koin, `start()`/`stop()` en `MainActivity`.

**Motivación.** Feature E necesita un observable de conectividad reactivo,
lifecycle-safe, e inyectable. KMP exige `expect/actual`.

**Decisión arquitectónica recomendada.**

```
domain/
└── connectivity/
    ├── ConnectivityStatus.kt        // sealed { Online, Offline }
    └── ConnectivityObserver.kt      // interface { val status: StateFlow<ConnectivityStatus>; fun start(); fun stop() }

androidMain/
└── connectivity/
    └── AndroidConnectivityObserver.kt   // ConnectivityManager + NetworkCallback → MutableStateFlow

iosMain/
└── connectivity/
    └── IosConnectivityObserver.kt   // stub (Phase 6)
```

**Lifecycle.**
- Singleton inyectado en `AppModule` (Koin).
- `start()` se llama en `MainActivity.onCreate` y `stop()` en `onDestroy` — NO desde Composables (un `DisposableEffect` por pantalla causaría registrar/desregistrar en cada navegación).
- `status: StateFlow<ConnectivityStatus>` se expone como `Hot Flow` global.

**Consumo.**
- En `PaparcarApp` (root scaffold) se observa con `collectAsStateWithLifecycle()`. Eso decide visibilidad del banner.
- Pantallas que necesitan reaccionar (HomeViewModel para refrescar spots) lo inyectan vía constructor, no vía `CompositionLocal` — la lógica es de dominio, no de UI.
- Evitar `CompositionLocalProvider` para esto: complica los tests de ViewModels y mezcla niveles.

**Plan.**
1. Definir `ConnectivityStatus` y `ConnectivityObserver` en `domain/connectivity`.
2. `AndroidConnectivityObserver` con `ConnectivityManager.NetworkCallback` (API 24+ ya está cubierto).
3. Registrar singleton en Koin (`single<ConnectivityObserver> { AndroidConnectivityObserver(get()) }`).
4. `MainActivity` invoca `start()`/`stop()`.
5. `HomeViewModel` recibe `ConnectivityObserver` y, en `viewModelScope`, observa transiciones `Offline → Online` para emitir `LoadNearbySpots`.

**Criterios de aceptación.**
- [ ] El observer emite el estado correcto en menos de 1s tras un cambio (medido con tests instrumentados).
- [ ] No se filtran callbacks: `stop()` desregistra el `NetworkCallback` correctamente.
- [ ] El consumo no causa recomposiciones innecesarias (banner sólo recompone cuando cambia el estado).

---

### DS-001 — Migrar `AppPreferences` SharedPreferences → DataStore — ⏳ Pending  `[MEDIUM]`

**Motivación.** El proyecto persiste configuración con `SharedPreferences`.
Aunque funciona, DataStore aporta API basada en Flow, escritura asíncrona y
esquema más auditable. Aprovechar la migración del Boolean → enum (THEME-ARCH-001)
es el momento natural.

**Plan.**
1. Añadir dependencia `androidx.datastore:datastore-preferences-core`.
2. Crear `AppPreferencesDataStore` que implementa `AppPreferences`.
3. Mantener `AndroidAppPreferences` como capa legacy: en primer arranque tras
   la actualización, leer las prefs existentes y migrarlas al DataStore.
4. Borrar `AndroidAppPreferences` cuando todos los usuarios objetivo hayan migrado (≥1 versión publicada).

**Criterios de aceptación.**
- [ ] Lecturas devuelven `Flow<T>` en lugar de getter síncrono — refactor de los call sites.
- [ ] Migración idempotente: re-ejecutarla no corrompe datos.
- [ ] `themeMode`, `useImperialUnits`, `defaultMapType`, `autoDetectParking`, etc. se persisten correctamente.

> **Decisión a confirmar:** ¿Es DS-001 obligatorio para Phase 4.5 o puede
> diferirse? Recomendación: diferirlo. THEME-ARCH-001 funciona bien sobre
> SharedPreferences. DS-001 puede ejecutarse en una iteración aparte cuando
> haya capacidad para refactorizar todos los call sites.

---

### NAV-ARCH-002 — `VehiclesViewModel` unifica VehicleRepository + UserParkingRepository — ⏳ Pending  `[MEDIUM]`

**Estado:** sin abordar. La rama `feat/A-nav-tabs-vehicles` solo renombró `MyCarViewModel`→`VehiclesViewModel` sin unificar fuentes. Ejecutar tras el merge de A.

**Motivación.** Feature A requiere un único ViewModel que combine la lista de
vehículos con sus sesiones agrupadas. Las dos fuentes (`VehicleRepository.observeVehicles()`
+ `UserParkingRepository.observeAllSessions()`) deben combinarse con
`combine { vehicles, sessions -> ... }`.

**Plan.**
1. Crear `VehiclesViewModel` en `presentation/vehicles/`.
2. State expone:
   - `vehicles: List<VehicleWithStats>` (incluye sesiones agrupadas, último parking).
   - `selectedVehicleId: String?` para navegación a detalle.
3. Lógica de "vehículo activo": derivar en estado, no en repo:
   ```kotlin
   val activeVehicle: Vehicle? = bluetoothConnectedVehicle
       ?: vehicles.firstOrNull { it.isDefault }
   ```
4. Conservar `MyCarViewModel` y `HistoryViewModel` mientras dure la migración; eliminarlos cuando la nueva pantalla se cierre como done.

**Criterios de aceptación.**
- [ ] No regresiones en performance vs. `HistoryViewModel` actual (cold start <300ms para una lista de 50 sesiones).
- [ ] La lógica de "activo BT > activo manual" se cubre con tests unitarios.

---

### AFS-ARCH-001 — Separar `AddFreeSpotViewModel` de `HomeViewModel` — 📦 Code-complete  `[MEDIUM]`

**Rama:** `feat/C-add-free-spot` @ `c206eba` — sin merge a master. `HomeIntent.ReportManualSpot` eliminado, lógica de publicación migrada a `presentation/addspot/AddFreeSpotViewModel`.

**Motivación.** Hoy el HomeViewModel maneja `ReportManualSpot(lat, lon)`. Eso
debe migrar a un ViewModel propio con responsabilidad única.

**Plan.**
1. Localizar el `UseCase` actual que se invoca en `ReportManualSpot` (probablemente `ReportSpotUseCase` o similar bajo `domain/usecase/spot`).
2. Crear `AddFreeSpotViewModel` en `presentation/addfreespot/`.
3. State: `cameraCenter: GpsPoint?`, `isSubmitting: Boolean`, `submitError: PaparcarError?`.
4. Intent: `CameraMoved(lat, lon)`, `Publish`, `Cancel`.
5. Effect: `NavigateBack`, `ShowError`.
6. Eliminar `HomeIntent.ReportManualSpot` y la rama correspondiente del `HomeViewModel.handleIntent`.

**Criterios de aceptación.**
- [ ] HomeViewModel ya no contiene lógica de "publicar plaza manual".
- [ ] `AddFreeSpotViewModel` tiene cobertura de test (success path + offline path).

---

## Referencia rápida: estado de ramas

QA-1 a QA-5: todas las ramas completadas fueron cherry-pickeadas a master vía `temp/linear-merge`.

**QA-6 — sprint Phase 4.5 (auditado 2026-04-27, ninguna mergeada):**
```
refactor/D-001-paparcar-map-view              📦 23e47b4 — listo
refactor/D-002-paparcar-bottom-action-bar     📦 fb86fb1 — listo
feat/E-connectivity-observer                  📦 54a5dbc — listo (NET-001..006 + NET-ARCH-001)
feat/C-add-free-spot                          📦 c206eba — listo (AFS-001..008 + AFS-ARCH-001 + D-002)
feat/A-nav-tabs-vehicles                      📦 17658e4 — listo parcial (NAV-001/002p/005p/007/008)
feat/B-theme-mode-tri-state                   📦 51a9bf5 — listo (THEME-001..004)
```

**QA-6 pendientes 100%:**
```
THEME-ARCH-002  Map style por tema (depende de D-001 + B-THEME)   ⏳
NAV-ARCH-002    VehiclesViewModel unificado (depende de NAV-A)    ⏳
DS-001          DataStore migration                                ⏳ (decisión: diferir)
```

**Pendientes Phase QA-3 (tests):**
```
test/TEST-002-parking-detection-coordinator     ⏳
test/TEST-003-spot-repository-offline-first     ⏳
```

**Canceladas:**
```
refactor/ARCH-001-decouple-auth-from-domain     ❌ (revertido — sobre-ingeniería)
```

---

## Criterios de completado por fase

### QA-1 ✅ COMPLETADA
- [x] `QA-001`: login después de liberar parking no muestra sesión activa antigua
- [x] `QA-002`: "Ver en mapa" desde Historia centra el mapa en la plaza correcta
- [x] `QA-003`: app retorna error visible si el usuario no está autenticado al confirmar parking
- [x] `QA-004`: plazas expiradas desaparecen de la lista cuando Firestore las elimina
- [x] `QA-005`: un error Firestore no vacía la lista de plazas (Room sigue emitiendo)
- [x] `QA-006`: fallo de Activity Recognition no mata el mapa ni la lista de plazas

### QA-2 ✅ COMPLETADA (ARCH-001 cancelled)
- [x] ~~`ARCH-001`~~: Cancelada — BaseLogin es librería propia, no requiere abstracción
- [x] `ARCH-002`: `HomeViewModel` usa use cases en lugar de repos directamente
- [x] `ARCH-003`: spinner/skeleton visible durante el cold start hasta llegar el primer GPS fix
- [x] `ARCH-004`: listener Firestore no se reabre en menos de 100m de desplazamiento

### QA-3 ⏳ PARCIAL
- [x] `./gradlew :composeApp:allTests` pasa con 16 tests
- [x] `CalculateParkingConfidenceUseCase`: ≥6 casos cubiertos
- [ ] `ParkingDetectionCoordinator`: los 3 paths de confirmación testeados
- [ ] `SpotRepositoryImpl`: offline-first strategy verificada con fakes
- [x] `ConfirmParkingUseCase`: userId null early-exit testeado (parcial — falta geofence/ordering)

### QA-4 ✅ COMPLETADA
- [x] `UI-001`: skeleton visible en cold start antes de llegar spots
- [x] `UI-002`: al seleccionar filtro sin resultados aparece CTA para limpiar filtro
- [x] `UI-003`: TalkBack puede navegar por la lista de spots y activar cada item
- [x] `UI-004`: `HomeSectionHeader` no tiene parámetro `badge`

### QA-5 ✅ COMPLETADA
- [x] `REF-001..003`: constantes, named args, y Flow.debounce aplicados
