# Auditoría completa de proyecto — Paparcar

- **Fecha:** 2026-07-04
- **Rama auditada:** `refactor/DET-SOLID-001-evidence-system` en el commit `e54baf38` (snapshot del inicio de la auditoría).
- **Método:** 6 auditorías paralelas independientes (detección · doc drift · arquitectura/KMP · data/sync · código+testing · madurez), con verificación `fichero:línea`, más una regresión ejecutable local (661 tests verdes en `testProdDebugUnitTest` y `testMockDebugUnitTest`). Solo lectura.
- **Aviso de vigencia:** la rama avanzó **9 commits** después del snapshot (`e54baf38..32ce4265`). Varios hallazgos ya fueron cerrados por sesiones posteriores. El estado re-verificado de los hallazgos de **detección** vive en `docs/backlog/DET-AUDIT-REMEDIATION-001.md`; los demás ejes de este documento están al snapshot y deben re-verificarse antes de actuar.

> Este documento es el registro durable de la auditoría. La remediación se planifica y sigue en
> `docs/backlog/DET-AUDIT-REMEDIATION-001.md` (detección primero) y en las fases posteriores allí descritas.

---

## 1. Resumen ejecutivo

Proyecto muy por encima de la media para un desarrollador solo: dominio Kotlin puro real (1 impureza en 107 ficheros), MVI uniforme con contrato base, límites de capa *enforzados por Konsist*, offline-first con Room como única fuente de la UI, 661 tests verdes, y un replay harness de detección que reproduce la traza real del incidente de campo contra el detector de producción. DET-SOLID-001 hace lo que dice y es apto para el gate de device con condiciones.

Los problemas graves están en las **fronteras**: infraestructura (CI/distribución), contrato con Firestore, y los bordes del sistema de detección que el rediseño no atacó.

**5 riesgos mayores:**
1. **CI no ejecuta tests desde 2026-05-31.** `ci.yml:59` invoca `testDebugUnitTest`, task ambigua desde que hay flavors → 661 tests + Konsist ~5 semanas sin correr. Los pipelines `distribute-alpha/beta` también fallan (rutas de APK sin flavor, keystore fuera del workspace).
2. **Contrato `spots` con Firestore roto en ambos sentidos.** La regla de `delete` exige `reportedBy==uid` pero el cliente escribe `displayName`/`""` → nadie borra spots, expirados nunca se limpian, colección crece sin límite. `spots.update` abierto a cualquier autenticado.
3. **Dos vías de plaza fantasma que DET-SOLID-001 no cierra:** fall-through del departure (ENTER espurio andando) y ruta BT sin gate de parada.
4. **Race de UserParking offline con daño permanente** (save/clear en cadenas WorkManager distintas + NOT_FOUND tragado). *(Cerrado luego por SYNC-UP-GUARD-001 — ver plan.)*
5. **iOS ~55-60% real e2e:** sensores implementados, pero nadie arranca el coordinator ni consume el geofence-exit → detección automática inoperante; sin firma, sin plist de Firebase, sin Crashlytics cableado, sin CI.

---

## 2. Scorecard

| Eje | Nota | Justificación |
|---|---|---|
| Funcional | 6.5/10 | Núcleo de detección sólido y demostrado por replay, pero sobreviven 2 vías de plaza fantasma, el delete de spots es imposible por contrato roto, y hay una race offline con daño permanente. |
| Arquitectura | 8.5/10 | Dominio puro (1 impureza/107), 0 violaciones de límites, concurrencia sin leaks, Koin limpio; penaliza el canal de effects perdible del `BaseViewModel` y la lógica de negocio en androidMain. |
| Modularización | Todavía-no | 0 violaciones reales + Konsist ya enforza; modularizar hoy sería ceremonia sin retorno. |
| Código limpio | 8/10 | Disciplina verificada (0 println, 0 wildcards, magic numbers tokenizados); penaliza `invoke()` de ~400 líneas, 106 `runCatching` ciegos a `CancellationException`, 14 keys sin ES. |
| Testing | 7.5/10 | Pirámide de dominio excepcional (25/26 use cases, 9/9 VMs, 100% fakes, replay con clock virtual); pero ruta BT a cero, `UserParkingRepositoryImpl` a cero, tiempo solo inyectado en el coordinator, 0 tests de UI. |
| Madurez | Android 6/10 · iOS 2.5/10 | Fundamentos notables (secrets, rules versionadas e idénticas a producción, R8 con criterio, flavors aislados) hundidos por CI/distribución rotos y mapping de Crashlytics nunca subido. |

---

## 3. Doc drift (documentación ↔ código, al snapshot)

| Doc | Afirmación | Realidad | Veredicto |
|---|---|---|---|
| `CLAUDE.md` §Detección | "HIGH ≥0.75 → auto-confirm" | HIGH solo abre CANDIDATE; todo auto-confirm exige pasos+egreso (`EvaluateParkingDecisionUseCase.kt:66,82-83`); sin step detector nunca auto-confirma | ❌ Drift grave (doc vinculante para IA) |
| `CLAUDE.md` | `resolveStrategy(vehicle, isBluetoothEnabled)` 2 ramas; "BT connect → DetectDepartureUseCase" | Real: `ParkingStrategyResolver.strategyFor(vehicles)` → enum con `NONE`; `DetectDepartureUseCase` no existe; el connect solo cancela | ❌ Drift |
| `CLAUDE.md` §Stack | Todas las versiones | Coinciden con `libs.versions.toml` | ✅ Único doc de stack al día |
| `docs/PARKING_DETECTION.md` (raíz) | Ventanas 3/20 min, STILL como señal, auto-confirm por score | Config real 2/5 min; STILL purgado; contradice al canónico | ❌ Doc zombi — archivar |
| `docs/detection/PARKING-DETECTION.md` §DET-SOLID-001 | Las 7 piezas del rediseño | Verificadas una a una: fidelidad total | ✅ El doc fiable |
| Ídem §1.3/§1.7 | "scaffolding kept", AR ENTER vía FGS PendingIntent | C1a ya purgó STILL; el ENTER es broadcast pasivo — el §1 no se reescribió | ⚠️ Contradicción interna |
| Ídem matriz S5 | "ENTER espurio andando → lo cubre enter-precedes-exit" | El gate de orden NO cubre el ENTER previo al cruce del radio; el fall-through lo publica (A1) | ❌ La fila sobrevende la defensa |
| `docs/ARCHITECTURE.md` | 7 versiones, "Room v3 sin migraciones", 6 workers, `ParkingDetectionCoordinator`, "~330 ficheros" | Room v11 + 6 migraciones, 10 workers, `CoordinatorParkingDetector`, 535 ficheros | ❌ Snapshot de mayo, drift grave |
| `docs/BUGS_AND_DEBT.md` | Inventario 17 items | Casi todo ✅ resuelto; §11 y §16 describen código inexistente; §6 regresión a11y reabierta sin registrar (`VehicleRegistrationScreen.kt:252`); tabla-resumen incoherente | ⚠️ Regenerar |
| `docs/ROADMAP.md` | `HomeParkingDepartureWorker` "Done" | Congelado desde 2026-06-05; esa clase no existe | ❌ Archivar/reescribir |
| `HANDOFF-refactor-deteccion.md`, `HYPOTHESIS.md`, `IOS_PLAN.md`, `README.md` | Tareas "pendientes" ya hechas, iOS ~70%, doble AppPreferences | Ejecutado/superado; contradicciones internas (`onStillDetected` ya no existe) | ⚠️ Archivar/purgar |
| Código (KDocs) | `ParkingDetectionConfig.kt:17-21,97-113,466` y `ConfirmationPhase.kt:18-20` citan STILL/AR-rearm/clase purgada | Señales purgadas | ⚠️ Drift embebido |

**Acciones documentales:** archivar `docs/PARKING_DETECTION.md` (raíz), `HANDOFF-refactor-deteccion.md`, `HYPOTHESIS.md`; reescribir la sección de detección de `CLAUDE.md` **cuando DET-SOLID-001 pase el gate y se mergee**; regenerar `BUGS_AND_DEBT.md` (y registrar la regresión a11y del back de `VehicleRegistrationScreen.kt:252`).

---

## 4. Hallazgos priorizados (al snapshot `e54baf38`)

> El estado **re-verificado** de los hallazgos de detección (A1-A4, M1-M7) contra el HEAD actual
> está en `docs/backlog/DET-AUDIT-REMEDIATION-001.md`. Los de otros ejes están al snapshot.

### 🔴 Críticos

| # | Ubicación | Hallazgo | Recomendación |
|---|---|---|---|
| C1 | `.github/workflows/ci.yml:59` | `testDebugUnitTest` ambigua desde los flavors → tests + Konsist sin correr en CI desde 2026-05-31 | `testProdDebugUnitTest` |
| C2 | `distribute-alpha.yml:59`, `distribute-beta.yml:61` | Rutas de APK pre-flavors → entrega a testers rota | Corregir rutas (`apk/prod/debug/…-prod-debug.apk`) |
| C3 | `distribute-beta.yml:38,43` + `build.gradle.kts:302` | Keystore resuelto vía `rootProject.file` → fuera del workspace | Alinear la ruta |
| C4 | `firestore.rules:12-14` vs `ReportSpotWorker.kt:74` + `ReportSpotReleasedUseCase.kt:48` | `spots.delete` compara `reportedBy==uid` pero producción escribe `displayName ?: ""` → nadie borra spots; expirados nunca se limpian | `reportedBy=uid` + campo aparte de display; TTL policy server-side |

### 🟠 Altos (no-detección; detección en el plan)

| # | Ubicación | Hallazgo | Recomendación |
|---|---|---|---|
| A4-rules | `firestore.rules:11` | `spots.update` abierto a cualquier autenticado (reescribir spots ajenos, incl. `reportedBy`) | Restringir por diff de claves ±1 en contadores; resto solo owner |
| A6-vm | `BaseViewModel.kt:38,59` | Effects `MutableSharedFlow()` sin buffer pierden eventos sin colector (KDoc invertido); caso real `PermissionsViewModel.kt:51-53`; doble colector Home (`HomeScreen.kt:178,229`) | `Channel(BUFFERED).receiveAsFlow()` + un colector por pantalla |
| A7-obs | `build.gradle.kts:360-362` | Mapping de Crashlytics nunca se sube → crashes de release ofuscados | `-PuploadCrashlyticsMapping=true` en el beta |
| A8-sec | `docs/release/RELEASE-SECURITY.md:24` | Maps API key literal commiteada; checklist de rotación a cero | Rotar + restricciones GCP + borrar del doc |
| A9-kmp | `BluetoothParkingDetector.kt:53-111`, `CoordinatorDetectionService.kt:252-386`, `GeofenceJanitorWorker.kt:60-68` | Lógica de negocio en androidMain — causa raíz del gap iOS | Extraer a commonMain (`HandleGeofenceExitUseCase`; BT con `haversineMeters`) |
| A10-data | `VehicleRepositoryImpl.kt:163-203`, `ZoneRepositoryImpl.kt:80-87`, `SyncReconcile.kt:25-30` | Deletes offline sin tombstone → vehículos/zonas resucitan | Outbox de deletes o soft-delete drenado por `pushPending*` |
| A11-data | `data/geohash/Geohash.kt:48-52` + `FirebaseDataSourceImpl.kt:26-34` | Geoquery de 1 celda p4: agujero en frontera + descarga toda el área metropolitana | Patrón geofire (`geohashQueryBounds`, celdas vecinas p5-6) |
| A12-clean | `CoordinatorParkingDetector.kt:265-667` | `invoke()` de ~400 líneas en el fichero más crítico | Extraer fases del collect a métodos privados (el replay hace el refactor seguro) |

### 🟡 Medios (no-detección; selección)

- **M5-data** `VehicleRepositoryImpl.kt:125-126`, `ZoneRepositoryImpl.kt:59-60` — `deleteByUser`+`upsertAll` sin `@Transaction`: parpadeo + ventana de pérdida de un vehículo creado durante el bootstrap.
- **M6-clean** 106 `runCatching` en commonMain sin rethrow de `CancellationException` (p.ej. `VehicleRepositoryImpl.kt:99,130,163,205`) → cancelación cooperativa rota, trabajo zombie tras logout. Helper `runCatchingCancellable` + regla Konsist.
- **M7-data** `AndroidPlatformModule.kt:45-46` — `fallbackToDestructiveMigration(dropAllTables=true)` + huecos de migración (1→2, 4→5, 5→6, 7→8). Bloqueante de release público.
- **M8-i18n** 14 keys sin ES (13 `vehicle_color_*` + `vehicle_registration_section_color`); 11 keys huérfanas confirmadas.
- **M9-data** `UserProfileRepositoryImpl.kt:29` — `getOrCreateProfile` remote-first sin timeout en el splash. `withTimeoutOrNull(~3s)` → caché.
- **M10-data** LWW con reloj de cliente + `clearPending` incondicional (`SyncReconcile.kt:27`, `VehicleDao.kt:53-54`). `FieldValue.serverTimestamp()` + clear por generación.
- **M11-vm** VMs con lógica de dominio: `VehicleRegistrationViewModel.kt:241-312` reimplementa "primer vehículo → activo" ignorando `VehicleActiveStatePolicy`; `loadVehicle:200` `first{}` sin timeout; `VehiclesViewModel.kt:55-197` computa stats dentro de `update{}` y su `.catch` mata el stream.
- **M12-arch** Guardarraíles Konsist: falta regla de pureza de domain frente a frameworks (Napier ya se coló: `PaparcarLogger.kt:4`); matching textual (falsos +/-); scope no cubre androidMain/mock.
- **M13-kmp** TTLs 2h/15min duplicados en `ReportSpotWorker.kt:98-100` ↔ `IosReportSpotScheduler.kt:106-108` (viola la regla "compartida entre 2+ → config").
- **M14-ci** Sin lint estático alguno; el beta distribuye sin gate de CI verde (`needs:` ausente).

---

## 5. Veredicto de modularización: TODAVÍA NO

Datos duros medidos: **0 violaciones de límites** entre capas (todos los greps en cero); los límites **no** se sostienen solo por disciplina — `ArchitectureTest.kt` (Konsist) enforza 5 reglas citando ARCH-002. El único hueco barato: falta la regla "domain sin `android.*`/frameworks" (~10 líneas; Napier ya se coló). El problema con forma de módulo que sí existe es la lógica de negocio en androidMain (A9-kmp) — un `:domain` no lo arregla; lo arregla extraerla a commonMain (que además es el 80% del camino a iOS).

**Señales de disparo futuras:** clean build > 30 s sostenido, entrada de un 2º desarrollador, ~500+ ficheros (ya hay 535 → re-medir clean build y renovar la decisión conscientemente). Si algún día: empezar por `:core:model`+`:core:domain`, nunca por features.

---

## 6. Veredicto de nivel

**Android:** perfil típico de proyecto solo-dev excelente: todo lo que se ejercita a diario está impecable; todo lo escrito una vez y no re-ejecutado (CI, distribute, rules de spots, docs de mayo) ha driftado. Camino a beta = arreglar la infraestructura (C1-C3), rotar la key (A8), y las 2 vías de plaza fantasma antes de abrir a desconocidos.

**iOS:** no es "un 30% restante", es otra categoría de trabajo: extraer orquestación a commonMain (A9), cablear coordinator + consumidor de geofence-exit, BGTaskScheduler, firma/plist/Crashlytics, y validación de campo equivalente a la de Android. Semanas, no días — y decidir si la estrategia BT tiene sentido en iOS (sin bonded devices).
