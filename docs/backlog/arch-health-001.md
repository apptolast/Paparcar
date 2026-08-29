# ARCH-HEALTH-001 — Auditoría de salud del código + plan de limpieza por fases

**Estado:** 🔵 Abierto — plan por fases. **F7 (split `:app`+`:shared`) ✅ EJECUTADA el 29-08-2026**
junto con el rename del paquete a `com.rndeveloper.paparcar`; el resto de fases siguen pendientes
de go-ahead una por una, y F8 sigue pospuesta sine die.

> ## ⚠️ Revisión 2026-08-27 — este plan se escribió ANTES del refactor F6 de detección
>
> El doc se mantuvo en una rama sin mergear y el código siguió avanzando, así que sus cifras ya no
> describen el repo. Se mergea igualmente porque el **plan por fases sigue siendo válido** y un spec
> escondido en una rama es invisible para `docs/backlog`, que es la fuente de verdad de lo pendiente
> desde DOCS-BACKLOG-TRUTH-001. Pero antes de ejecutar cualquier fase, **volver a medir**:
>
> | el doc dice | realidad el 27-08-2026 |
> |---|---|
> | `domain/coordinator/CoordinatorParkingDetector.kt` | el paquete ya no existe: F6 lo movió a `domain/detection/` |
> | 2.311 líneas | **1.378** |
> | «`CoordinatorParkingDetector` CONGELADO» como precondición de F8 | F6 lo refactorizó entero; la precondición caducó |
>
> ⚠️ **Colisión de nombres:** la «F6 — Capa de datos DRY» de ESTE plan no tiene nada que ver con el
> «refactor F6» de detección, que es otro trabajo y ya está hecho. Al hablar de fases, decir de cuál.
>
> 📌 **F7 de este plan ES el split `:app` + `:shared`.** No abrir un ticket paralelo para eso: es
> esta fase, y AGP 9 la convirtió de «diferida por gusto» en «deprecación con fecha».

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

- [x] **F7 — Split `:app` + `:shared` (L, riesgo medio) — ✅ HECHA (2026-08-28)**
  Ejecutada junto con el renombrado de paquete `io.apptolast.paparcar` →
  `com.rndeveloper.paparcar` (namespace + applicationId + árbol de fuentes; app nueva
  registrada en Firebase pap-26 con las mismas SHA y `google-services.json` regenerado).
  - `:shared` = KMP con `com.android.library` + `androidTarget()`; tests en `androidUnitTest`
    (`:shared:testDebugUnitTest`). `:app` = `com.android.application` + KGP android.
  - ⚠️ **Los flags `android.builtInKotlin=false` / `android.newDsl=false` SIGUEN puestos, y F7
    NO los elimina.** Era su disparador declarado, así que conviene decirlo claro. El intento
    con el plugin recomendado por AGP 9 (`com.android.kotlin.multiplatform.library`) compiló,
    pasó los 1.762 tests y **murió en el primer frame en el Redmi**: Compose Multiplatform
    1.12.0 registra `copyAndroidMainComposeResourcesToAndroidAssets` sin `outputDirectory`
    para ese plugin, y los 35 composeResources del módulo (16 drawables, 10 fuentes, los 9
    locales de strings) desaparecían del APK sin un solo aviso —
    `MissingResourceException: …/drawable/paparcar_logo_dark.xml`. Cablearlo a mano se
    descartó: es exactamente la clase de fontanería que vuelve a romperse en silencio.
    **Lo que F7 sí elimina** es la causa original de los flags —KMP y
    `com.android.application` en el MISMO módulo—; lo que queda es la incompatibilidad
    `com.android.library` + KMP, que se destapa sola cuando Compose MP soporte el plugin
    nuevo (el cambio son dos líneas en `shared/build.gradle.kts`).
  - Frontera: `BuildConfig` vive en `:app`; shared lee build facts vía `AppBuildInfo`
    (seteado por ambas Application antes de Koin) y el expect `isDebugBuild`.
    `AppNotificationManagerImpl` (usa `R.*` y `MainActivity`) se mudó a `:app` con sus
    3 bindings en `appModule`. Mock flavor en `app/src/mock/` (composables de la galería
    publicizados: `internal` cross-módulo ya no aplica).
  - El manifest completo queda en `:app` con nombres relativos: resuelven contra el
    namespace `com.rndeveloper.paparcar`, que sigue siendo el paquete Kotlin de shared.
  - Barrido: CI workflows, skills, README, CLAUDE.md, docs vivos, Xcode
    (`:shared:embedAndSignAppleFrameworkForXcode`), `.gitignore` (`!shared/schemas/`),
    schema Room regenerado bajo el FQCN nuevo. **1.762 tests verdes**; prod+mock ensamblan.
  - 🔁 **RECOMPUTADO sobre master `4059eb55` (29-08), no rebaseado.** El primer intento vivía
    sobre `866ca62d` y master avanzó 22 commits: un `git rebase` habría dado 136 conflictos
    rename/modify + 6 rename/delete, y —lo grave— habría dejado **19 ficheros nuevos de master
    huérfanos en `composeApp/`** (uploader de diagnósticos, `LocaleUnits`, `locales_config.xml`,
    el trace de replay del 28-08…), que git reporta como limpios porque el commit del rename no
    los menciona. Como el trabajo es 93% mecánico, se re-ejecutaron los pasos sobre master y se
    portaron literal los ficheros escritos a mano. Verificado por construcción: el conjunto de
    ficheros del árbol nuevo == el del intento anterior − los 6 que master borró + los 46 que
    master añadió. Doctrina: la misma de DET-PACKAGE-CLUSTERS-001 — *recomputar, no resolver
    a mano*.
  - ⚠️ Pendientes externos: ✅ secret CI `GOOGLE_SERVICES_JSON` ya actualizado (JSON oficial con
    los 3 paquetes, así compilan también las ramas viejas); queda restringir la Maps key en GCP
    al paquete nuevo, y en los móviles la app vieja `io.apptolast.paparcar` queda instalada en
    paralelo (desinstalarla a mano: dos apps detectando a la vez). iOS: klib no compilable en
    Windows — validará el compañero, y el bundle id de iosApp no cambia en este ticket.

- [ ] **F8 — Coordinator (L, riesgo alto) — POSPUESTA sine die**
  Extraer transiciones de fase de `CoordinatorParkingDetector` a evaluadores puros, con
  el replay harness como red. Precondición dura: semanas de detección estable en campo.

## Registro de ejecución

| Fecha | Fase | Resultado |
|---|---|---|
| 2026-07-27 | Spec | Auditoría completada, plan aprobado, rama creada |
| 2026-08-28 | F7 | Split `:app`+`:shared` + rename a `com.rndeveloper.paparcar`; 1.707 tests verdes, workarounds AGP eliminados |
| 2026-08-29 | F7 | Recomputado sobre master `4059eb55` (22 commits nuevos) en vez de rebasar; 1.762 tests verdes |
| 2026-08-29 | F7 | Verificado EN DEVICE (Redmi): el plugin KMP-library de AGP 9 vaciaba los composeResources del APK → `:shared` vuelve a `com.android.library`; los 2 flags de compatibilidad se quedan, documentados |
