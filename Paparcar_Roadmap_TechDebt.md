# Paparcar — Roadmap Deuda Técnica & Calidad

> Generado el 2026-04-16. Actualizado el 2026-04-17 con estado real de commits en master.
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

## Referencia rápida: estado de ramas

Todas las ramas completadas fueron cherry-pickeadas a master vía `temp/linear-merge`.

**Pendientes:**
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
