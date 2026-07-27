# ARCH-HEALTH-001 — Auditoría de salud del código + plan de limpieza por fases

> Estado: 📋 SPEC — plan aprobado en diagnóstico, ejecución pendiente de go-ahead por fase.
> Rama: `chore/ARCH-HEALTH-001-code-health-plan` (desde master `574c94c8`).
> Origen: auditoría 2026-07-27 con 4 barridos independientes (estructura/modularización,
> código muerto, duplicación, consistencia arquitectónica).

## Contexto

Solo-dev manteniendo ~46k líneas commonMain + 12k androidMain + 2.4k iosMain en un único
módulo Gradle. Objetivo: frenar deuda técnica antes de que crezca. La auditoría concluyó
que el proyecto está **estructuralmente sano** — el trabajo es de limpieza selectiva, no
de rescate.

## Diagnóstico (resumen)

### Lo que está bien (verificado, no tocar por deporte)
- Capas limpias sin dependencias circulares; dominio 100% puro KMP (grep verificado).
- MVI completo en los 9 ViewModels (`BaseViewModel` fuerza el contrato).
- 5 tests Konsist activos: `ArchitectureTest`, `TypographyGuardrailTest`,
  `DividerGuardrailTest`, `HomeSliceGuardrailTest` (+ regla domain-puro).
- Naming uniforme: `Evaluate*`/`Observe*`/`Get*`/`Run*`; Controller/Manager/Coordinator
  con roles distinguibles. Eventos de detección = sealed interfaces en `DetectionEvent.kt`
  con discriminadores string a propósito (no existe el problema GF_*/AR_* que se temía).
- DI limpia: `KoinComponent` solo en Workers/Receivers/Service (impuesto por Android).
- Sin catches silenciosos en lógica de negocio; los `.getOrNull()` son fallbacks
  deliberados de entradas opcionales.
- Umbrales de detección centralizados en `ParkingDetectionConfig`.
- Formatters (tiempo/distancia), modelos de ubicación y primitivos UI (Pap*) sin duplicación.

### Verificaciones de supuestos del prompt original
- **FND-004 está resuelto en código**: ruta geofence EXIT → `EvaluateGeofenceExitUseCase` →
  `DepartureDetectionWorker` → `RunDepartureCheckUseCase` → `ProcessConfirmedDepartureUseCase`
  → publicación spot vía WorkManager, trazada de punta a punta. Si reaparece el síntoma en
  campo, buscar en OEM-kill/Doze, no en el cableado.
- `docs/IOS-PORT-PLAN.md` no existe; el vigente es `docs/IOS_PLAN.md` (8/8 adaptadores
  nativos iOS reales; no impone cambios estructurales).
- `[PUCK-FLICKER-001]` vive solo en docs; el código sensible que referencia es la rama de
  proyección `puckOffsetFromCenterPx` en `PaparcarMapView.kt` (junto a `[FOLLOW-001]`).
- BOOT_COMPLETED re-registra vallas de forma idempotente (KEEP + FLAG_UPDATE_CURRENT). OK.

### Veredicto de modularización: NO (todavía)
- No dividir en módulos feature: el coste (N build.gradle.kts, KSP/Room por módulo,
  recursos Compose por módulo, flavor mock repartido) supera el beneficio para un solo dev.
  La disciplina que un split forzaría ya la dan los tests Konsist.
- **Sí a medio plazo**: split mínimo `:app` (com.android.application) + `:shared` (KMP),
  que elimina los workarounds `android.builtInKotlin=false` / `android.newDsl=false` de
  `gradle.properties` (el propio comentario del fichero lo anticipa). Disparador: cuando
  una subida de AGP convierta los warnings en bloqueos. Es la fase F7, no antes.

### Hotspots reales (por impacto)
1. `domain/coordinator/CoordinatorParkingDetector.kt` — 2.311 líneas, 9 invariantes
   documentados. **CONGELADO** (ver restricciones): campaña de field-tests de detección viva.
2. `presentation/home/sections/sheet/` — 4.847 líneas (mayor concentración de presentación;
   `HomeSliceGuardrailTest` acota el daño).
3. `HomeViewModel` — patrón controller a medias: 4/6 dominios extraídos; quedan inline los
   streams de repos (`collectInto`) y `mapForeground` como MutableStateFlow suelto.
4. `ui/components/PaparcarMapView.kt` — 1.686 líneas, contiene ramas sensibles
   [FOLLOW-001]/[PUCK-FLICKER-001]. Cualquier split = cambio de riesgo + device-test.
5. `CoordinatorDetectionService.handleGeofenceExit` — 234 líneas de orquestación I/O
   (layering correcto, la decisión está en el use case; solo hotspot de legibilidad).

### Código muerto (3 hallazgos, confianza alta)
| Ruta | Motivo |
|---|---|
| `gradle/libs.versions.toml` → `geofire-android` | Declarada, cero imports en todo src |
| `composeResources/drawable/paparcar_iconmark_white.xml` | Cero referencias; superado por forest/green |
| `ui/theme/Color.kt:22` → `PapForest` | "legacy" en comentario, jamás asignado a rol de tema |

### Duplicaciones aprovechables
| Qué | Propuesta | Riesgo |
|---|---|---|
| Dual-stream local-first (Room + Firestore listener + retry) ×3 repos (Spot/UserParking/Zone) | Scaffold `localFirstFlow()` + helper snapshots con retry | Medio — preservar que la cache local emite PRIMERO |
| Reconcile LWW ×3 (`SyncReconcile`/`UserParkingReconcile`/`VehicleReconcile`) | Helper genérico `applyLww<T>()` | Medio |
| Overpass query+parsing duplicado androidMain/iosMain | Subir a commonMain; HTTP por plataforma | Bajo-medio (avanza port iOS) |
| Helpers mapper (`toAddressDto`/`toPlaceInfoDto`/factoría `GpsPoint`) inline en `SpotDtoMapper`, extraídos en `ParkingSessionMapper` | Utilidades de mapper compartidas | Bajo |
| `EARTH_RADIUS_METERS` ×3 (GeoUtils + 2 Overpass) | Exportar la de `GeoUtils` | Bajo |
| Radio zona `250f` hardcoded ×4 (Zone/FirebaseDataSource/ZoneEntity/ZoneDto) | Todos → `Zone.DEFAULT_RADIUS_METERS` | Bajo |
| Timeouts GPS 15s (`GetOneLocationUseCase`) vs 60s (`BluetoothParkingDetector`) | Probablemente intencional — solo documentar el porqué | Bajo |

## ⛔ Restricciones (do-not-touch)

- **`CoordinatorParkingDetector` CONGELADO** mientras dure la estabilización de detección
  (DET-DRIVE-PROOF-001 y sucesores en field-test). Precondición para F8: detección estable
  en campo varias semanas seguidas.
- **`PaparcarMapView`** (proyección puck [PUCK-FLICKER-001], follow [FOLLOW-001]): fuera
  del plan; cualquier cambio se marca aparte y se valida en device.
- **Ruta geofence EXIT** del `CoordinatorDetectionService` (FND-004) y
  **`BootCompletedReceiver`**: no se tocan en ninguna fase de este plan.
- Ninguna fase mezcla señales BT en el scoring del Coordinator (doctrina CLAUDE.md).

## Plan por fases

Cada fase deja la app compilando y los tests verdes. Riesgo creciente hacia el final.
Ejecutar en orden; pedir go-ahead por fase.

- [ ] **F1 — Limpieza (S, riesgo nulo)**
  Borrar `geofire-android`, `paparcar_iconmark_white.xml`, `PapForest`. Unificar
  `EARTH_RADIUS_METERS` (exportar de `GeoUtils`) y radio de zona 250f
  (→ `Zone.DEFAULT_RADIUS_METERS`). Documentar los dos timeouts GPS.
  Done = build prod+mock verdes, cero referencias rotas.

- [ ] **F2 — Mappers DRY (S, riesgo bajo)**
  Helpers compartidos `toAddressDto()`/`toPlaceInfoDto()`/factoría `GpsPoint`;
  `SpotDtoMapper` y `ParkingSessionMapper` los consumen. Los tests de mapper existentes
  son la red. Done = tests de mapper verdes sin cambiar expectativas.

- [ ] **F3 — Reubicar + guardarraíles (S, riesgo bajo)**
  Mover `VehicleCatalog.kt` (1.800 líneas de datos) fuera de
  `presentation/vehicleregistration/data/` a un paquete de datos puro. Añadir reglas
  Konsist: "ui/components no importa presentation/*" y "nadie importa `*RepositoryImpl`
  fuera de di/". Done = Konsist verde (allowlist inicial si hace falta, con ticket).

- [ ] **F4 — HomeDataController (M, riesgo bajo)**
  Cerrar el patrón controller en `HomeViewModel`: extraer los streams de repos
  (`collectInto`, activeSessions/parkedVehicles/zones/vehicles) a un `HomeDataController`
  con `Flow.updates`, y absorber `mapForeground` en `HomeUiController`.
  ⚠️ Memoria: no refactorizar combine/flatMapLatest por estética — validar en device.
  Done = tests VM verdes + smoke en device.

- [ ] **F5 — Overpass a commonMain (M, riesgo medio)**
  Query-building + parsing + distancia compartidos en commonMain; solo el cliente HTTP
  queda por plataforma. Done = tests nuevos del parser + build iOS verde.

- [ ] **F6 — Capa de datos DRY (L, riesgo medio)**
  `localFirstFlow()` + helper de snapshots Firestore con retry + `applyLww<T>()`.
  Un repositorio cada vez, empezando por **Zones** (menos crítico) → Spots → UserParking.
  Invariante: la cache local emite primero; el `.catch{}` del stream remoto se preserva.
  Device-test entre repositorio y repositorio. Done por repo = tests repo verdes + smoke.

- [ ] **F7 — Split `:app` + `:shared` (L, riesgo medio) — DIFERIDA**
  Disparador: subida de AGP que convierta los warnings en bloqueo. Elimina
  `android.builtInKotlin=false` / `android.newDsl=false`. El árbol de paquetes se mueve
  intacto; mock flavor queda en `:app`.

- [ ] **F8 — Coordinator (L, riesgo alto) — POSPUESTA sine die**
  Extraer transiciones de fase de `CoordinatorParkingDetector` a evaluadores puros, con
  el replay harness como red. Precondición dura: semanas de detección estable en campo.

## Registro de ejecución

| Fecha | Fase | Resultado |
|---|---|---|
| 2026-07-27 | Spec | Auditoría completada, plan aprobado, rama creada |
