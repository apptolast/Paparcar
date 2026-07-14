# HOME-ATOMIZE-001 — Atomización de estado y descomposición de la presentación de Home

**Estado:** Backlog — arranca en rama propia cuando `feature/DRIVE-PUCK-NATIVE-001-kmpmaps-fork` esté mergeada.
**Rama propuesta:** `refactor/HOME-ATOMIZE-001-presentation`
**Motivación:** MANTENIBILIDAD, no rendimiento. El único hot-path medido (puck a fix-rate) ya se
resolvió en DRIVE-PUCK-NATIVE-001 (hoisting de `tripRender` fuera de `HomeState`; arrastres 1.6%
jank). Este refactor es de estructura: `presentation/home/` son 8.154 líneas donde 3 ficheros
concentran el 38% (HomePeekHandle 1.353, HomeScreen 1.025, HomeViewModel 720) y el estado se
enhebra entero (`state = state`) a ~13 call-sites.

> Diagnóstico producido el 2026-07-14 con 3 análisis exhaustivos (VM+controllers, Screen+map/header,
> subsistema sheet). Las referencias de línea son de **master** en esa fecha; tras el merge de
> DRIVE-PUCK-NATIVE-001 hay drift en HomeScreen/HomeState (el trip ya NO vive en HomeState:
> `drivingPuck/tripTrail/matchedTrail/departurePoint` fueron sustituidos por `drivingMeta` +
> `HomeViewModel.tripRender`). Verificar líneas al empezar cada fase.

---

## 1. Diagnóstico (resumen con evidencia)

### 1.1 Lo que YA está bien (no tocar el patrón, replicarlo)

- **Controllers desacoplados y confinados** — HomeTripController, HomeGeocodingController,
  HomeSpotsController, HomeSearchController: cada uno posee su estado local, expone un único
  `updates: Flow<XxxUpdate>`, no conocen al VM ni entre sí. Es el patrón HOMEVM-CTRL-001..004
  y funciona. El refactor EXTIENDE este patrón, no lo sustituye.
- **`HomeDetectionSurface`** recibe un enum (`DetectionUiState`), no la state entera, y sus
  sub-composables son claros → **modelo a seguir** para el resto del sheet.
- **`PapSheet`** es un molde genérico correcto (5 slots); las 6 variantes de peek ya lo usan todas.
- **`HomeStateTransitions`** (`clearedModeFields()`, `applyNewSpots()`) factoriza bien los
  invariantes mode↔selection.
- Strings vía `stringResource`, constantes nombradas (no magic numbers inline): conforme a CLAUDE.md.

### 1.2 Los tres problemas estructurales

**A) `HomePeekHandle.kt` (1.353 líneas) no es un handle — es 3 niveles pegados en un fichero:**
1. Decisión modal (qué `PeekState` renderizar) — ~20 líneas útiles.
2. SEIS variantes de peek completas y privadas (SpotPeekRow ~83, ParkingPeekRow ~73,
   AddingParkingPeekRow ~89, ReportPeekRow ~40, AddingZonePeekRow ~124, CameraLocationRow ~132).
3. Helpers compartidos (PeekMetaRow, FiabilityIndicator, DistanceRow, rememberNowMinuteTick,
   paletas, skeleton).
- Firma con **20 parámetros de callback**. Añadir una acción = +1 param en 3 niveles
  (HomeContent → HomeBottomSheet → HomePeekHandle).
- Tokens divergentes entre ficheros: `META_VALUE_ALPHA` = 0.7 en HomePeekHandle vs 0.6 en
  HomeSpotRows; `META_SEPARATOR` repetida en 2+ ficheros.

**B) `HomeContent` (~760 líneas dentro de HomeScreen.kt) es un god-composable:**
- ~260 líneas de **geometría del sheet** (5 snap points: full/expanded/half/peek/minimized,
  content-aware full snap sobre `lazyListState.layoutInfo`, reset-to-peek con tolerancia,
  nested-scroll estilo Instagram, nav-progress hoisting) entrelazadas con la orquestación de las
  4 secciones (map/header/fabs/sheet).
- **14 lambdas `remember`** de eventos + explosión de params: HomeBottomSheet recibe 22 params
  (14 lambdas + 8 de estado).
- `state = state` (entera) a HomeMapSection, HomeHeaderSection, HomeMapFabsLayer, HomeBottomSheet.
  Utilización real: FABs usan ~4/35 campos (11%), Header ~9/35 (26%), Map ~16/35, Sheet ~25/35.
- Smell puntual: job holder con `arrayOfNulls<Job>(1)` (glass interaction) en vez de
  `mutableStateOf<Job?>`.

**C) `HomeViewModel` (720 líneas) sigue siendo orquestador universal pese a los controllers:**
- **24 inyecciones** directas; el módulo Koin ya no puede usar `viewModelOf` por >22 params.
- `handleIntent` = `when` de **~35 ramas** sin patrón: unas inline, otras a handler de 1 línea,
  otras a métodos async de 40-50 líneas.
- **Lógica de negocio inline en los confirm***: `confirmReportSpot` (fallback carbody),
  `confirmAddParking` (construcción de GpsPoint, condicional edit/create),
  `confirmAddZone` (lookup de zona `zones.find { it.id == editingId }`, edit/create) — espejo
  duplicado del anterior (chequeo `pinCameraLat/Lon` idéntico en ambos).
- `subscribeGpsLocation` (43 líneas) mezcla GPS + geocoding + registro de AR + reconnect;
  `reconnectTick` (contador que fuerza resubscripción) es un hack reconocido.
- Política de errores inconsistente: `collectInto` loguea por defecto, algunos streams emiten
  efecto, los event-bus no tienen `.catch`.
- Computed properties de `HomeState` que **alocan** en cada lectura: `filteredNearbySpots`
  (filter), `vehicleCards` (map+join). Rompen skippability de quien las consuma y son ruido.

---

## 2. Plan de refactor — 4 fases independientemente aterrizables

Cada fase compila, pasa la suite y es mergeable por sí sola (commits atómicos por fase, misma
rama). Orden elegido para que las fases posteriores se apoyen en las firmas de las anteriores.

### F1 — Slices de estado por sección + saneo de computed properties

*El "atomizar estados" pedido. Es la base: define QUÉ ve cada sección.*

1. Crear slices `@Immutable` por sección como **projections del VM** (no data classes derivadas
   en composición — así el `distinctUntilChanged` es gratis y el slice es testeable):
   - `HomeHeaderSlice` (search, mapType, zones, gps-accuracy, hasCorePermissions) — ~9 campos.
   - `HomeFabsSlice` (userParking, userGpsPoint, isParkingSelected, isDriving) — ~4 campos.
   - `HomeMapSlice` (mapType, nearbySpots filtrados, sesiones, zonas, mode, selection, vehicles…).
   - El sheet, con ~25 campos usados, NO se trocea a martillazos: se divide en dos slices
     naturales — `PeekSlice` (selección + mode + formularios activos) y `BrowseListSlice`
     (spots/vehículos/detección para la lista).
2. `HomeState` sigue siendo la fuente única en el VM (MVI intacto); los slices son `val xxx:
   StateFlow<XxxSlice>` = `state.map { … }.stateIn(…)` o simplemente funciones de proyección
   puras `HomeState.toHeaderSlice()` aplicadas en HomeScreen — **decisión al implementar**:
   empezar por proyección pura (menos plumbing) y solo subir a StateFlow si un slice lo pide.
3. Matar las computed-List con alocación: `filteredNearbySpots` y `vehicleCards` pasan a
   calcularse UNA vez donde corresponda (en la proyección del slice o precomputadas en el VM al
   aplicar el update), no en un `get()` por lectura.
4. Los composables de sección cambian de firma: `HomeHeaderSection(slice: HomeHeaderSlice, …)`.
   `HomeContent` deja de enhebrar `state` entera a hijos (puede seguir recibiéndola él).

**Aceptación F1:** ningún composable bajo `sections/` recibe `HomeState`; cero `get()` que
aloque List en `HomeState`; tests de proyección de cada slice; galería mock actualizada.

### F2 — Descomponer HomeContent: extraer la física del sheet

1. `rememberSheetPositioning(...)`: los 5 snap points + content-aware full snap + tolerancias →
   devuelve un `SheetPositioning` inmutable. Vive en `sections/sheet/` junto a lo que gobierna.
2. Los 3 LaunchedEffect de transición del sheet (reset-to-peek, auto-expand lista, nav-progress)
   → `SheetTransitionEffects(positioning, sheetOffsetPx, …)` composable sin UI.
3. El cálculo de snap (`HomeSheetSnap.snapTarget`) se convierte en función pura testeable sin
   Compose (queda en presentation, NO en domain — es geometría de UI, domain es Kotlin puro de
   negocio).
4. Micro-fixes de paso: job holder `arrayOfNulls<Job>(1)` → `mutableStateOf<Job?>`; borrar los
   logs `[SHEETDBG]` (ya cumplieron); `HomeMapFab` passthrough → inline.

**Aceptación F2:** HomeContent < 400 líneas y legible como tabla de contenidos (secciones +
efectos con nombre); tests unitarios del snap-target puro (peek↔expanded↔full, fling 1200f,
tolerancias); comportamiento del sheet idéntico en device.

### F3 — Partir HomePeekHandle + contrato de acciones del sheet

1. Nuevo paquete `sections/sheet/components/peek/`:
   - `SpotPeek.kt`, `ParkingPeek.kt`, `AddingParkingPeek.kt`, `ReportPeek.kt`,
     `AddingZonePeek.kt` (con el formulario troceado en sub-composables: campo nombre, picker
     icono, slider radio, toggle privacidad), `BrowsePeek.kt` (CameraLocationRow + skeleton),
     `PeekShared.kt` (PeekMetaRow, FiabilityIndicator, DistanceRow, rememberNowMinuteTick,
     paletas, títulos).
   - `HomePeekHandle.kt` queda como orquestador puro (~150 líneas): decide `PeekState` y anima.
2. **Un solo canal de acciones**: sealed interface `HomeSheetAction` + `onAction:
   (HomeSheetAction) -> Unit`. Sustituye los 20 callbacks del peek y colapsa la firma de
   HomeBottomSheet (22 → ~8 params). HomeContent traduce: acción → `onIntent(HomeIntent…)` o
   `uiController.moveCamera(…)`. Las acciones que ya son 1:1 con un intent NO se duplican:
   el peek emite el `HomeIntent` directamente vía `onIntent` (dos canales: `onIntent` para
   intents del VM, `onAction` para lo que necesita orquestación de UI local).
3. Unificar tokens divergentes en `SheetTokens.kt` (META_SEPARATOR, alphas — decidir UN valor,
   hoy 0.6 vs 0.7).
4. Cada variante de peek recibe su dato concreto (`spot`, `session`, formulario), no la state.

**Aceptación F3:** HomePeekHandle < 200 líneas; ninguna variante > 150; firma de HomeBottomSheet
≤ 10 params; paridad visual verificada contra `*Previews.kt` + galería mock actualizada
(cada variante de peek = entrada en StateGallery).

### F4 — Adelgazar HomeViewModel

*Con las UI-fases hechas, el VM es el último God-object. Aquí se aplica "sistemas, no parches":
no inventamos registry/framework — extraemos negocio a domain y agrupamos por dominio.*

1. **Confirm* a use cases de domain** (testables sin VM, patrón `Result<T>` + runCatching):
   - `SaveManualParkingUseCase` (absorbe construcción de GpsPoint + edit/create de
     confirmAddParking).
   - `SaveZoneUseCase` existente se amplía o se crea `SaveOrUpdateZoneUseCase` (absorbe lookup
     de zona + edit/create de confirmAddZone; mata la duplicación pinCameraLat/Lon con un
     helper único `requirePinCoordinates(state): Result<GpsPoint>`).
   - Fallback de carbody de confirmReportSpot → dentro de `ReportSpotReleasedUseCase`.
2. **`handleIntent` agrupado por dominio**, sin frameworks: el `when` se conserva (es idiomático
   y exhaustivo) pero cada rama delega a UN método por dominio en extensiones privadas del VM
   (`handleZoneIntent`, `handleParkingIntent`, `handleReportingIntent`…) — legibilidad sin
   indirection. NO handler-registry, NO reflection.
3. **Partir `subscribeGpsLocation`**: registro AR + readiness → junto a la suscripción de
   detección; GPS+geocode queda solo. Evaluar si `reconnectTick` puede sustituirse por el
   `ConnectivityObserver` expuesto como Flow que los pipes `combine`an directamente (matar el
   hack, no envolverlo).
4. **Política de errores única**: `collectInto` gana un parámetro de política
   (log-only / log+effect) y TODO stream del VM pasa por él — incluidos los event-bus hoy sin
   `.catch`. Documentar el criterio en el kdoc de collectInto.

**Aceptación F4:** HomeViewModel < 500 líneas; cero construcción de modelos de dominio inline
en el VM; nuevos UCs con tests (naming `should_x_when_y`); todos los streams con catch.

---

## 3. No-goals (decisiones deliberadas, no olvidos)

- **NO** IntentHandler-registry con Map<Class, Handler> — indirection sin beneficio a esta
  escala; el `when` exhaustivo del sealed es un feature de Kotlin, no un smell.
- **NO** PeekContentProvider pattern (clases render()) — boilerplate; reconsiderar solo si el
  peek supera 8+ variantes.
- **NO** reducer Redux formal — `updateState` + transitions puras ya dan el 90% del valor.
- **NO** mover la geometría del sheet a `domain/` — domain es negocio Kotlin-puro, no px de UI.
- **NO** cambios de comportamiento visibles: esto es un refactor 1:1; cualquier bug que se
  descubra por el camino se anota y se arregla en commit separado, no camuflado.
- **NO** tocar los controllers existentes (trip/geocoding/spots/search) salvo firmas si un
  slice lo exige — su patrón es el bueno.

## 4. Verificación transversal (obligatoria por fase)

- `assembleMockDebug` + prod compilan; suite completa verde.
- **Dev Catalog en sync** (regla ⛔ CLAUDE.md): slices/firmas nuevas reflejadas en
  `StateGalleryScreen` y presets de `DevCatalogScreen` en la MISMA fase que las introduce.
- Paridad visual: screenshots emulador claro/oscuro de peek (6 variantes), sheet en los 5
  anclajes, header y fabs, antes/después de cada fase.
- Los guardarraíles Konsist existentes (Typography/Divider) siguen verdes; evaluar añadir uno
  nuevo: "ningún composable de `sections/` acepta parámetro de tipo HomeState" (cierra F1 para
  siempre).
- Device Oppo al cierre de F2 y F3 (las fases con riesgo de gesto/física del sheet).

## 5. Estimación y orden

| Fase | Contenido | Tamaño relativo | Riesgo |
|---|---|---|---|
| F1 | Slices + computed props | M | Bajo (mecánico, firmas) |
| F2 | Física del sheet fuera de HomeContent | M-L | Medio (gestos — validar device) |
| F3 | Partir peek + HomeSheetAction | L | Medio (mucho movimiento, 1:1) |
| F4 | VM: UCs + agrupación + errores | M | Bajo-medio (tests cubren) |

F1→F2→F3→F4. Si hay que recortar, F4 puede diferirse a un ticket propio (HOME-VM-002); F1-F3
forman el bloque de UI que no conviene partir entre ramas.
