# 01 — Inventario de casos de uso de detección/parking

> Refactor de solo-lectura, Subagente A. Fecha: 2026-08-18. Base: master `66c25d64`.
> Alcance leído ENTERO fichero a fichero. **Nota de alcance:** el encargo hablaba de 13 ficheros en
> `usecase/detection/` y 23 en `usecase/parking/`; el árbol real contiene **10** y **18**
> respectivamente (verificado con `ls`). Total inventariado: **36 use cases + 12 políticas puras**.
>
> Convenciones: paquete abreviado (`d.` = `io.apptolast.paparcar.domain.`). "Invocadores" lista
> solo call sites de PRODUCCIÓN (commonMain/androidMain/iosMain); "Koin" indica registro en
> `di/DomainModule.kt`. Las menciones que son solo KDoc se excluyen (verificado con grep -n).
> "¿Puro?" = sin I/O ni dependencias de repositorio/plataforma; decisión función de sus argumentos.

---

## 1 · `d.usecase.detection` (10 ficheros)

| Clase | Paquete | LOC | Responsabilidad | Entradas | Salidas | ¿Puro? | ¿Estado mutable? | Quién lo invoca | Tags | Tests |
|---|---|---|---|---|---|---|---|---|---|---|
| `EvaluateArEnterArmUseCase` | d.usecase.detection | 99 | Escalera de decisión para armar (o no) con un AR `IN_VEHICLE_ENTER` fresco atado al propio coche | `session`, `fix`, `enterTrueTimeMs`, `nowMs`, `recentStaleExitRecorded` (+`config` en ctor) | `ArEnterDecision` (NoSession/StaleEnter/NoFix/ArmAtCar/ArmMidTrip/TickOnly) | Sí | No | `CoordinatorDetectionService` (androidMain). Koin: sí | DET-AR-FIRST-001, DET-SESSION-BIRTH-001, DET-CONJUNCTION-001, DET-RIDE-PROOF-001, BUG-FGS-001 | `EvaluateArEnterArmUseCaseTest` |
| `EvaluateBtArbitrationUseCase` | d.usecase.detection | 109 | Árbitro: un edge BT del coche emparejado SUPERSEDE/VETA la sesión probabilística del coordinator (nunca puntúa en ella) | `event` (CONNECT/DISCONNECT), `coordinatorRunning`, `coordinatorPhase`, `btVehicleId`, `coordinatorVehicleId` | `BtArbitrationVerdict` (NoOp/SupersedeWithBluetooth/VetoReturnToVehicle/YieldToConnectedCar) | Sí | No | `BluetoothConnectionReceiver`, `CoordinatorDetectionService` (androidMain). Koin: sí | DET-TIERS-001, DET-BT-WRONG-CAR-ABORT-001, DET-BT-CONNECTED-NOT-PAIRED-001, DET-AUDIT-002 | `EvaluateBtArbitrationUseCaseTest` |
| `EvaluateBtParkUseCase` | d.usecase.detection | 89 | Núcleo puro de la ruta BT: clasificar fixes de candidato y de walk-away (gates de velocidad/precisión/ritmo peatonal) | `fix` / (`candidate`, `current`, `elapsedMs`) (+`config`) | `BtParkVerdict` (KeepWaiting/DrivingAbort/CandidateAccepted/WalkAwayConfirmed) | Sí | No | `BluetoothParkingDetector` (androidMain). Koin: sí | DET-AUDIT-002 T2/T3, BUG-WALK-DEPART-001 (espejo) | `EvaluateBtParkUseCaseTest` |
| `EvaluateDetectionReliabilityUseCase` | d.usecase.detection | 69 | Nivel de fiabilidad (OPTIMAL/GOOD/REDUCED) + `DetectionTier` a partir de BT emparejado, exención de batería y OEM agresivo | 3 booleans | `DetectionReliabilityReport` (level, tier, issues) | Sí | No | Solo `ObserveDetectionReliabilityUseCase` en prod (los VM tests lo instancian directo). Koin: sí | DET-RELIABILITY-001, DET-TIERS-001 | `EvaluateDetectionReliabilityUseCaseTest` (+ 3 VM tests) |
| `EvaluateFirstParkNudgeUseCase` | d.usecase.detection | 60 | Decidir si toca el nudge de "primer aparcamiento" (cold-start Coordinator, throttling 3×/3 días, autodesactivado tras primer park) | `nowMillis`; internamente readiness + 3 prefs | `Boolean` | No (lee `AppPreferences` y readiness) — la función top-level `shouldSendFirstParkNudge` SÍ es pura | No | `FirstParkNudgeWorker` (androidMain). Koin: sí | DET-TOGGLE-002 | `EvaluateFirstParkNudgeUseCaseTest` |
| `EvaluateGeofenceExitUseCase` | d.usecase.detection | 106 | Clasificar un batch de EXITs de geocerca: huérfanas a limpiar, salidas boundary vs stale, y sesión con la que armar | `List<GeofenceExitLookup>`, `activeVehicleId`, `triggerLat/Lon` (+`config`) | `GeofenceExitDecision` (orphans, boundaryDepartures, staleDepartures, armTarget) | Sí | No | `CoordinatorDetectionService` (androidMain). Koin: sí | AUDIT-A9-KMP-001, DET-EXIT-TRUST-001, DET-RIDE-PROOF-001 | `EvaluateGeofenceExitUseCaseTest` |
| `EvaluateShortHopDriveProofUseCase` | d.usecase.detection | 110 | 2ª prueba independiente de conducción para hops cortos: desplazamiento medido desde el pin de salida, fix a fix, con techo peatonal | `departureAnchor`, `fix`, `fenceRadiusMeters`, `elapsedSinceArmMs`, `consecutiveQualifyingFixes` (+`config`) | `Boolean` (+`qualifies()` por fix) | Sí | No | `CoordinatorParkingDetector` (commonMain, como default-param del ctor — **NO está en Koin**) | DET-SHORT-HOP-PROOF-001, DET-DRIVE-PROOF-001, DET-SENTRY-ARM-PEDESTRIAN-CLOCK-001 | `EvaluateShortHopDriveProofUseCaseTest` |
| `ObserveDepartureWatchGapUseCase` | d.usecase.detection | 63 | Stream `true` mientras el watcher de salidas DEBERÍA estar vivo (regla `resolvePostDetectionLifecycle`) pero el servicio está `Dead` | — (combina 4 flows: sesiones, vehículos, toggle, presence) | `Flow<Boolean>` + `current()` | No (repos + runtime) | No | `DepartureWatchResumerImpl`, `MainActivity`, `PaparcarApp` (androidMain). Koin: sí | DET-WATCH-REACTIVATE-001, DET-WATCH-RESUME-RACE-001, DET-STRATEGY-GATE-001 | `ObserveDepartureWatchGapUseCaseTest` |
| `ObserveDetectionReadinessUseCase` | d.usecase.detection | 130 | Fuente única del banner de detección de Home: Disabled → Blocked → Monitoring → Parked → Ready | — (combina 5 fuentes: vehículos, sesiones, permisos, runtime, toggle) | `Flow<DetectionReadiness>` | No | No | `HomeViewModel`, `EvaluateFirstParkNudgeUseCase`. Koin: sí (+PresentationModule) | DET-READY-001b/c/i, DET-TOGGLE-001, DET-READY-TRIP-OVER-PARKED-001, UI-PREFERRED-SESSION-RECENCY-001, MULTI-PARKING-001, DET-PHASE-001 | `ObserveDetectionReadinessUseCaseTest` (+HomeViewModelTest) |
| `ObserveDetectionReliabilityUseCase` | d.usecase.detection | 39 | Wrapper reactivo del evaluador de fiabilidad: liga sus 3 entradas a fuentes vivas | — (vehículos + permisos + OEM manager + resolver) | `Flow<DetectionReliabilityReport>` | No | No | `HomeViewModel`, `PermissionsViewModel`, `SettingsViewModel`. Koin: sí | DET-RELIABILITY-001 | Vía 3 VM tests (sin test propio — patrón Evaluate/Observe) |

## 2 · `d.usecase.parking` (18 ficheros)

| Clase | Paquete | LOC | Responsabilidad | Entradas | Salidas | ¿Puro? | ¿Estado mutable? | Quién lo invoca | Tags | Tests |
|---|---|---|---|---|---|---|---|---|---|---|
| `CalculateParkingConfidenceUseCase` | d.usecase.parking | 74 | Score de confianza fast/slow-path a partir de `ParkingSignals` (scoring legacy del coordinator) | `ParkingSignals` (+`config`) | `ParkingConfidence` (NotYet/Low/Medium/High) | Sí | No | `CoordinatorParkingDetector`. Koin: sí | DET-SOLID-001 C1, BUG-DETECT-310503, BUG-COORD-106 | `CalculateParkingConfidenceUseCaseTest` (+coordinator/replay tests) |
| `ClearParkNudgeUseCase` | d.usecase.parking | 20 | Resolver el nudge pendiente "¿dónde dejaste el coche?": limpia pref durable + notificación de bandeja | — | `Result<Unit>` | No (prefs + notif) | No | `HomeViewModel` (único call site). Koin: sí | DET-NUDGE-PERSIST-001 | `ParkNudgeUseCasesTest` |
| `ConfirmParkingUseCase` | d.usecase.parking | 434 | Persistir un park confirmado: auth→vehículo→zona privada→guard de repark→ruta (trim/seed/encode)→save→geofence→seal de pasos→limpiar nudges→enrichment | `location`, `detectionReliability`, `spotType`, `sizeCategory`, `carbodyType`, `vehicleId`, `tripMaxSpeedMps`, `armEvidence`, `detectionPath`, `zoneRadiusMeters`, `sealPoint` | `Result<UserParking>` | No (repos, geofence, schedulers, auth, prefs, sensores) | No (11 deps, 5 nullable) | `BluetoothParkingDetector`, `ParkingBackfillWorker`, `SaveNewParkingSessionWorker` (androidMain); `CoordinatorParkingDetector`, `RunHonestCloseUseCase`, `SaveManualParkingUseCase` (commonMain). Koin: sí (+MockModule) | DET-SOLID-001, DET-PIN-PROVENANCE-001, DET-HONEST-CLOSE-001, DET-STEP-BUDGET-ORIGIN-001, DET-ROUTE-TRACK-001, ROUTE-QUALITY/START-AT-CAR/END-AT-CAR/GAP-HONEST-001, VEH-ACTIVE-FENCE-001, BUG-WALK-DEPART-001, DET-TOGGLE-002, DET-NUDGE-PERSIST-001, CONFIRM-NO-NOTIF-CLEANUP | `ConfirmParkingUseCaseTest`, `ParkingEdgeCaseTest`, `ParkingFlowIntegrationTest` |
| `DetectParkingDepartureUseCase` | d.usecase.parking | 211 | Veredicto de un EXIT de geocerca: sesión+fence+ENTER admisible+velocidad independiente → Confirmed/Rejected/Inconclusive | `geofenceId`, `exitTimestampMs`, `currentFix` (+repo, bus, config) | `DepartureDecision` | No (lee repo + bus) | No | Solo `RunDepartureCheckUseCase` en prod. Koin: sí | DET-SESSION-BIRTH-001, DET-DEPART-PROOF-001, DET-EXIT-TRUST-001, DET-RIDE-PROOF-001, MULTI-PARKING-001 | `DetectParkingDepartureUseCaseTest` (+RunDepartureCheckUseCaseTest) |
| `EvaluateBackfillDeferralUseCase` | d.usecase.parking | 63 | ¿Debe el backfill del safety net CEDER ante una llegada que el coordinator ya resolvió nudge-only? (frescura + match espacial) | `backfillFix`, `nowMs`, `resolutionAtMs`, `resolutionPoint` (+`config`) | `Boolean` | Sí | No | `ParkingBackfillWorker` (androidMain). Koin: sí | DET-BACKFILL-TAINT-001, DET-ARRIVAL-DOUBLE-PIN-001 | `EvaluateBackfillDeferralUseCaseTest` |
| `EvaluateHonestCloseUseCase` | d.usecase.parking | 328 | Escalera del cierre honesto en un abort: pin aproximado / zona / silencio, con step-budget, contador congelado, seal caducado, pin del usuario, walk floor | `stalePin`, `abortFix`, `stepsSinceStalePin`, `stepSealPoint`, `sealAgeMs`, `sessionStepEvents`, `sessionMaxSpeedMps` (+`config`) | `HonestCloseVerdict` (decisión + telemetría completa) | Sí | No | Solo `RunHonestCloseUseCase` en prod. Koin: sí | DET-HONEST-CLOSE-001, DET-FROZEN-COUNTER-001, DET-TRIP-WITNESS-001, DET-STEP-BUDGET-ORIGIN-001, DET-WALK-FLOOR-001, BUG-WALK-DEPART-001 | `EvaluateHonestCloseUseCaseTest` (+RunHonestCloseUseCaseTest) |
| `EvaluateParkingDecisionUseCase` | d.usecase.parking | 253 | Decisión de fase CANDIDATE: precedencia de 9 paths (steps+egress / kinematic / vehicleExit+window) con todos los vetos (rolling, mismatch, weak-evidence, anchor taints, human-powered) | `ParkingDecisionInput` (14 campos) (+`config`) | `ParkingDecision` (Confirmed/Rejected/Inconclusive/Prompt) | Sí | No | `CoordinatorParkingDetector`. Koin: sí | DET-D-01/02, DET-C-01, DET-SOLID-001, DET-KINEMATIC-EGRESS-001, DET-STEP-SPEED-GATE-001, DET-ANCHOR-EGRESS-001, DET-CREDIBLE-DRIVE-001, DET-GAP-ANCHOR-001, DET-EGRESS-PEDESTRIAN-CEILING-001, DET-UNVERIFIED-CONFIRM-001, DET-BIKE-NOT-A-CAR-001, BUG-SCOOTER-001 | `EvaluateParkingDecisionUseCaseTest` (+coordinator/replay) |
| `EvaluateSafetyNetCheckUseCase` | d.usecase.parking | 439 | Cerebro del safety net: por wake-up y sesión aparcada decide CureGeofence / DispatchDeparture (live o preconfirmado) / PromptStillParked / None; + `shouldReregisterCure` | `session`, `fix`, `lastSeenNearCarAtMs`, `nowMs`, `stepsSinceAnchor`, `lastVehicleEnteredAtMs`, `exitDeliveredAtMs`, `userPresent`, `vehicleBtGated`, `lastBtConnectedAtMs` (+`config`) | `SafetyNetAction`; `shouldReregisterCure`: `Boolean` | Sí | No | `ParkingSafetyNetWorker` (androidMain). Koin: sí | DET-SAFETY-NET-001, DET-RECONCILE-001, DET-SESSION-BIRTH-001, DET-BT-IDENTITY-GATE-001, DET-RIDE-PROOF-001, DET-CONJUNCTION-001, DET-EXIT-TRUST-001, DET-ANCHOR-FREEZE-001, DET-CURE-FRESH-001, DET-AR-FIRST-001 F4, SAFETYNET-STATIONARY-001 | `EvaluateSafetyNetCheckUseCaseTest` |
| `EvaluateUnattendedParkingSaveUseCase` | d.usecase.parking | 320 | Veredicto del timeout desatendido: SaveExact / SaveZone (duda acotada) / Ask, con precedencia de 7 ramas (human-powered, no-drive, unpinned, egress-mismatch, vehicular, gap, walk-entered) | `UnattendedSaveInput` (17 campos) (+`config`) | `UnattendedParkingSave` + `UnattendedSaveReason` (vocabulario de diagnóstico) | Sí | No | `CoordinatorParkingDetector` (default-param del ctor — **NO está en Koin**) | DET-WALK-ENTERED-ANCHOR-ZONE-001, DET-GAP-ANCHOR-ZONE-001, DET-GAP-ANCHOR-001, DET-CONFIRM-FRESHNESS-001, DET-NODRIVE-ZONE-001, DET-SENTRY-ARM-PEDESTRIAN-CLOCK-001, DET-BIKE-NOT-A-CAR-001, DET-AR-FIRST-001 F3, DET-FROZEN-COUNTER-001 | `EvaluateUnattendedParkingSaveUseCaseTest` (+coordinator) |
| `ObserveParkedVehiclesUseCase` | d.usecase.parking | 48 | Sesiones activas enriquecidas con datos de vehículo (multi-parking, `stableRank` estable) | — (combina 2 flows) | `Flow<List<ParkedVehicleSummary>>` | No | No | `HomeViewModel`, `PaparcarMapView`. Koin: sí | MULTI-PARKING-001 | `ObserveParkedVehiclesUseCaseTest` |
| `ProcessConfirmedDepartureUseCase` | d.usecase.parking | 95 | Side-effects de una salida confirmada por geofenceId: publicar spot (si procede) + clear sesión + reset bus + quitar geofence + log | `geofenceId`, `publishSpot` | `Result<Unit>` | No | No | `CoordinatorDetectionService` (androidMain), `RunDepartureCheckUseCase`. Koin: sí | DET-RECONCILE-001, DET-SOLID-001, SPOT-PREFETCH-001 | `ProcessConfirmedDepartureUseCaseTest` |
| `ReleaseActiveParkingSessionUseCase` | d.usecase.parking | 108 | Liberar la sesión activa con un `ParkingReleaseReason` (publica o no) + clear + quitar geofence + log | `lat`, `lon`, `currentSession`, `reason` | `Result<Unit>` | No | No | `HomeViewModel` (único call site prod). Koin: sí | PEEK-ACTIONS-001, PARK-DELETE-NO-DECLARE-001, DET-AUDIT-002 T5/M4, VEH-ACTIVE-FENCE-001 | `ReleaseActiveParkingSessionUseCaseTest`, `ParkingEdgeCaseTest`, `ParkingFlowIntegrationTest` |
| `RevertParkingUseCase` | d.usecase.parking | 98 | Deshacer un auto-confirm ("No, cancelar"): clear sesión + quitar geofence + dismiss notif + telemetría de FP etiquetado por el usuario | `parkingId` | `Result<Unit>` (siempre success — best-effort) | No | No | `CoordinatorDetectionService`, `AppNotificationManagerImpl` (androidMain), `IosNotificationActionHandler` (iosMain). Koin: sí | DET-SOLID-001, TODO-REVERT-P1 | `RevertParkingUseCaseTest` |
| `RunDepartureCheckUseCase` | d.usecase.parking | 164 | Un intento completo de departure-check: fix fresco → decisión → fall-through admisible → upgrade de sesión viva → freshness gate → process | `geofenceId`, `exitTimestampMs`, `attempt`, `preconfirmed` | `DepartureCheckOutcome` (Retry/Dismissed/Processed/ProcessFailedRetry) | No | No | `DepartureDetectionWorker` (androidMain). Koin: sí | DET-SOLID-001, DET-RECONCILE-001, DET-G-05, DET-DEPART-PROOF-001, BUG-WALK-DEPART-001, DET-SESSION-BIRTH-001 | `RunDepartureCheckUseCaseTest` |
| `RunHonestCloseUseCase` | d.usecase.parking | 129 | Orquestar el cierre honesto: escalera pura → confirmar pin/zona vía ConfirmParking → nudge; devuelve resultado + veredicto para telemetría | `vehicleId`, `abortFix`, `stepsSinceStalePin`, `stepSealPoint`, `sealAgeMs`, `sessionStepEvents`, `sessionMaxSpeedMps` | `HonestCloseResult` | No | No | `CoordinatorDetectionService` (androidMain). Koin: sí | DET-HONEST-CLOSE-001, DET-NUDGE-PERSIST-001, DET-PIN-PROVENANCE-001 | `RunHonestCloseUseCaseTest` |
| `SaveManualParkingUseCase` | d.usecase.parking | 106 | El único flujo de pin CONFIRMADO POR EL USUARIO: crear (manual/nudge/detección) o mover; notif + teardown de detección en curso | `lat/lon/accuracy`, `editingParkingId`, `targetVehicleId`, `fromDetectionNudge` / `confirmDetected(gps)` | `Result<Unit>` | No | No | `HomeViewModel`. Koin: sí | HOME-ATOMIZE-001 F4, DET-MANUAL-CANCEL-001, DET-PIN-PROVENANCE-001, DET-NUDGE-PIN-PROVENANCE-001, MULTI-PARKING-001 | `SaveManualParkingUseCaseTest` |
| `UpdateParkingLocationUseCase` | d.usecase.parking | 88 | Reposicionar una sesión activa (flujo "Mover ubicación"): quitar fence → update fila → enrichment → reset bus → recrear fence | `parkingId`, `newLocation` | `Result<UserParking>` | No | No | Solo `SaveManualParkingUseCase` en prod. Koin: sí | BUG-WALK-DEPART-001 | `UpdateParkingLocationUseCaseTest` |
| `VerifyDepartureEvidenceUseCase` | d.usecase.parking | 108 | Verificador pre-arm de un GEOFENCE_EXIT: qué evidencia de VEHÍCULO lo respalda (speed / ENTER corroborado / Unverified) | `exitTimestampMs`, `currentSpeedKmh`, `currentAccuracyM`, `sessionStartMs`, `distanceFromCarMeters`, `fenceRadiusMeters` (+bus, config) | `ArmEvidence` | Casi (lee `DepartureEventBus.lastVehicleEnteredAt`) | No | `CoordinatorDetectionService` (androidMain, 2 sitios). Koin: sí | DET-G-05, DET-SOLID-001, DET-SESSION-BIRTH-001, DET-RIDE-PROOF-001, BUG-REPARK-WALK-001 | `VerifyDepartureEvidenceUseCaseTest` |

## 3 · Otros use cases del alcance (location / vehicle / notification / spot)

| Clase | Paquete | LOC | Responsabilidad | Entradas | Salidas | ¿Puro? | ¿Estado mutable? | Quién lo invoca | Tags | Tests |
|---|---|---|---|---|---|---|---|---|---|---|
| `ObserveAdaptiveLocationUseCase` | d.usecase.location | 78 | Stream GPS que conmuta HIGH_ACCURACY↔BALANCED por velocidad con histéresis + burst inicial de 3 min | — | `Flow<GpsPoint>` | No (fuente de plataforma) | Sí — estado local por invocación (`mode`, `burstStartMs`), no compartido | `BluetoothParkingDetector`, `CoordinatorDetectionService` (androidMain), `ParkingLocationViewModel`. Koin: sí | DET-BURST-001 | `ObserveAdaptiveLocationUseCaseTest` |
| `GetOneLocationUseCase` | d.usecase.location | 64 | Un fix único con timeout 15 s + gate de frescura; cada fix aceptado alimenta el `TripTrail` | `maxAgeMs` | `GpsPoint?` | No | No | `CoordinatorDetectionService`, `ParkingSafetyNetWorker` (androidMain), `RunDepartureCheckUseCase`. Koin: sí | DET-RECONCILE-001, DET-BREADCRUMBS-001 | `GetOneLocationUseCaseTest` |
| `GetLastKnownLocationUseCase` | d.usecase.location | 19 | Fix cacheado sin muestrear (no provoca EXITs espurios) | — | `GpsPoint?` | No | No | **NADIE** — solo el registro Koin (`DomainModule:92`). Sus consumidores documentados (AR proximity re-arm, watchdog) fueron purgados [DET-SOLID-001 C1b] | DET-AR-REARM-001 | **Ninguno** |
| `DeclareActiveVehicleUseCase` | d.usecase.vehicle | 28 | El único camino "este es el coche que conduzco": set active + swap de fences, idempotente | `vehicleId` | `Result<Unit>` | No | No | `HomeViewModel`, `VehiclesViewModel`. Koin: sí | VEH-ACTIVE-FENCE-001 | Sin test propio — solo vía `HomeViewModelTest`/`VehiclesViewModelTest` |
| `SwapActiveVehicleFencesUseCase` | d.usecase.vehicle | 68 | Reconciliar geocercas OS al cambiar el vehículo activo (consulta `VehicleFenceOwnershipPolicy.planActiveSwap`; BT saliente nunca se desregistra) | `outgoingVehicleId`, `incomingVehicleId` | `Result<Unit>` | No | No | Solo `DeclareActiveVehicleUseCase` en prod. Koin: sí | VEH-ACTIVE-FENCE-001 2c | `SwapActiveVehicleFencesUseCaseTest` |
| `NotifyParkingConfirmationUseCase` | d.usecase.notification | 33 | Mostrar la notificación de confirmación según `ParkingConfidence` con el nombre del vehículo activo | `confidence` | `Unit` | No | No | `CoordinatorParkingDetector` (único call site). Koin: sí | — | `NotifyParkingConfirmationUseCaseTest` |
| `ReportSpotReleasedUseCase` | d.usecase.spot | 85 | Geocodificar best-effort (5 s cap, o `prefetched`) y encolar el upload durable del spot liberado | `lat/lon`, `spotId`, `spotType`, `confidence`, `sizeCategory`, `carbodyType`, `prefetched` | `Unit` (fire-and-forget) | No | No | `ProcessConfirmedDepartureUseCase`, `ReleaseActiveParkingSessionUseCase`, `ReportManualSpotUseCase`, `HomeViewModel`. Koin: sí | SPOT-PREFETCH-001, AUDIT-RULES-001 C4 | `ReportSpotReleasedUseCaseTest` (+4 tests indirectos) |
| `SendSpotSignalUseCase` | d.usecase.spot | 21 | Señal comunitaria "sigue libre"/"ocupada" — delegación pura al repositorio | `spotId`, `accepted` | `Result<Unit>` | No | No | `HomeViewModel` (único call site). Koin: sí | — | `SendSpotSignalUseCaseTest` |

## 4 · Políticas/funciones puras de nivel superior en `d.detection` (el patrón destino)

> Estas NO son use cases inyectados: son funciones/objects puros top-level — el patrón que
> [DET-VERDICT-NOT-PREDICATE-001] fija para predicados compartidos por 2+ veredictos. Todas
> testeables directamente, sin ceremonia de clase.

| Símbolo | Paquete | LOC (fichero) | Responsabilidad | Entradas | Salidas | ¿Puro? | ¿Estado mutable? | Quién lo invoca | Tags | Tests |
|---|---|---|---|---|---|---|---|---|---|---|
| `nextSentryWakeAbortStreak` / `sentryWakeRearmCooldownMs` (+`DetectionSessionOutcomes`) | d.detection (SentryWakeCooldown.kt) | 59 | Damper de tormentas del sentry-wake: fold del streak de aborts andantes → cooldown exponencial acotado | `previousStreak`, `armedBySentryWake`, `sessionOutcome` / `abortStreak`, `config` | `Int` / `Long` | Sí | No | `CoordinatorDetectionService`, `SignificantMotionMonitor` (androidMain) | DET-SENTRY-COOLDOWN-001 | `SentryWakeCooldownTest` |
| `resolvePostDetectionLifecycle` / `resolveSentryKillVerdict` | d.detection (SentryLifecycleDecision.kt) | 79 | ¿El FGS muere o queda residente (Sentry) al acabar un job? / ¿Un sello de residencia huérfano prueba un kill del OS? | toggle+parked+strategy / residencyExpected+presence+rebootedSince | `PostDetectionLifecycle` / `SentryKillVerdict` | Sí | No | `CoordinatorDetectionService`, `SentryResidenceStore`, `ParkingSafetyNetWorker` (androidMain); `ObserveDepartureWatchGapUseCase` (commonMain) | DET-RESIDENT-FGS-001, DET-STRATEGY-GATE-001, DET-WATCH-REACTIVATE-001 | `SentryLifecycleDecisionTest` |
| `VehicleFenceOwnershipPolicy` (object: `shouldOwnFence`, `planActiveSwap`, `resolveSessionVehicleId`, `shouldDeclareActiveOnRelease`) | d.detection | 96 | "Solo el vehículo activo (o BT) posee geocerca OS" — propiedad, swap, atribución de sesión y declaración inferida al liberar | booleans / `FenceOwner`s / ids | `Boolean` / `FenceSwapPlan` / `String?` | Sí | No | `ConfirmParkingUseCase`, `SwapActiveVehicleFencesUseCase`, `CoordinatorParkingDetector`, `HomeViewModel` (commonMain); `GeofenceJanitorWorker` (androidMain) | VEH-ACTIVE-FENCE-001, DET-BT-OWNERSHIP-001, PARK-DELETE-NO-DECLARE-001 | `VehicleFenceOwnershipPolicyTest` |
| `isHumanPoweredRide` | d.detection (HumanPoweredRide.kt) | 60 | ¿El movimiento de ESTA sesión fue a tracción humana? (perfil bici/patinete, o AR ON_BICYCLE reciente no superado por IN_VEHICLE) | `vehicleType`, `bicycleRideAtMs`, `vehicleRideAtMs`, `nowMs`, `config` | `Boolean` | Sí | No | `CoordinatorParkingDetector` (alimenta 2 veredictos: `EvaluateParkingDecisionUseCase` y `EvaluateUnattendedParkingSaveUseCase`) | DET-BIKE-NOT-A-CAR-001, DET-SOLID-001 C2 | `HumanPoweredRideTest` |
| `shouldSupersedeRunningSession` | d.detection (SessionSupersede.kt) | 32 | ¿Un trigger nuevo en OTRO sitio supersede la sesión en curso? (distancia > radio+accuracy) | `newParkLocation`, `runningAnchor`, `newFenceRadiusMeters` | `Boolean` | Sí | No | `CoordinatorDetectionService` (androidMain) | DET-SUPERSEDE-001, DET-AR-REARM-001 | `SessionSupersedeTest` |
| `shouldNudgeForStalePending` | d.detection (PendingNudgeDecision.kt) | 20 | ¿Qué pending detection con heartbeat caducado merece el nudge? (EXIT/MANUAL siempre; AR solo si condujo) | `trigger`, `sawDriving` | `Boolean` | Sí | No | `ParkingSafetyNetWorker` (androidMain) | DET-NEVER-SILENT-001 | `PendingNudgeDecisionTest` |
| `shouldReapGhostDetectionFgs` | d.detection (GhostFgsReapDecision.kt) | 30 | ¿Puede el safety net descartar la notificación FGS fantasma este tick? (stale pending + tick periódico + no running) | 3 booleans | `Boolean` | Sí | No | `ParkingSafetyNetWorker` (androidMain) | DET-FGS-REAPER-001 | `GhostFgsReapDecisionTest` |
| `DrivingRoute` (object: `append`, `endAtAnchor`) | d.detection | 80 | Reglas puras de acumulación de la ruta conducida: decimación, segmentación por gap, cap, trim al ancla | `List<GpsPoint>`, `point`/`anchor` | `List<GpsPoint>` | Sí | No | `DrivingRouteStoreImpl` (androidMain), `ConfirmParkingUseCase`, `TrailMapMatcher` (commonMain) | DET-ROUTE-TRACK-001, ROUTE-FIX-ACCURACY-001, ROUTE-END-AT-CAR-001 | `DrivingRouteTest` |
| `shouldShowParkNudgeBanner` (+`PendingParkNudge`) | d.detection (PendingParkNudge.kt) | 48 | Visibilidad del banner de Home para un nudge sin responder (se autoresuelve si el vehículo vuelve a tener sesión) | `nudge`, `activeSessions` | `Boolean` | Sí | No | `HomeViewModel`, `HomeSlices` (commonMain) | DET-NUDGE-PERSIST-001 | `ParkNudgeUseCasesTest` |
| `coordinatorMayArm` (+`ParkingStrategyResolver`) | d.detection (ParkingStrategyResolver.kt) | 129 | Regla única de admisión de arm del coordinator (MANUAL siempre; automáticos solo bajo COORDINATOR). El resolver de estrategia (clase, no pura: lee adapter BT) vive en el mismo fichero | `strategy`, `trigger` / `vehicles` | `Boolean` / `ParkingStrategy` | `coordinatorMayArm` sí; `ParkingStrategyResolver` no (BT adapter) | No | `CoordinatorDetectionService` (androidMain); resolver: readiness/reliability/watch-gap use cases | DET-STRATEGY-GATE-001, DET-BT-CONNECTED-NOT-PAIRED-001, BUG-SCOOTER-001 | `ParkingStrategyResolverTest` |
| `shouldSendFirstParkNudge` | d.usecase.detection (EvaluateFirstParkNudgeUseCase.kt) | (60) | Mitad pura del nudge de primer park — ya sigue el patrón función-top-level dentro del fichero del use case | readiness + 4 escalares | `Boolean` | Sí | No | `EvaluateFirstParkNudgeUseCase` | DET-TOGGLE-002 | `EvaluateFirstParkNudgeUseCaseTest` |

Otros ficheros de `d.detection` fuera de esta tabla por no ser políticas (modelos/puertos):
`ArmEvidence` (sealed + `isVerifiedLabel`, consultado por ConfirmParking y EvaluateParkingDecision),
`DetectionTrigger`, `DetectionRuntimeState`/`MutableDetectionRuntimeState` (⚠️ ÚNICO estado mutable
compartido del paquete — singleton escrito por el servicio y el coordinator), `TripTrail`,
`DrivingRouteStore`, `ManualParkingDetection`, `DepartureConfirmationListener`,
`DepartureWatchResumer` (interfaces de puerto, impl en androidMain).

---

## 5 · MUERTOS

| Clase | Evidencia |
|---|---|
| **`GetLastKnownLocationUseCase`** | Único match fuera de su fichero: el registro Koin (`DomainModule.kt:92`). Grep de `getLastKnownLocation` en commonMain+androidMain no encuentra ningún consumidor fuera de `LocationDataSource` (la interfaz que envuelve). Sus consumidores documentados en KDoc — "AR proximity re-arm y watchdog" — corresponden al path AR-arming legacy purgado en [DET-SOLID-001 C1b] (ver `DetectionTrigger`: "AR_PROXIMITY was purged"). Sin test. **Borrar clase + registro Koin.** |

Ningún otro use case del alcance está muerto: los 35 restantes tienen al menos un call site de
producción real (verificado por grep, distinguiendo menciones KDoc de invocaciones).

## 6 · CANDIDATOS A INLINE (un solo call site y triviales)

| Clase | Call site único | Nota |
|---|---|---|
| `SendSpotSignalUseCase` (21 LOC) | `HomeViewModel` | Delegación 1:1 al repositorio, cero lógica. Inline directo. |
| `ClearParkNudgeUseCase` (20 LOC) | `HomeViewModel` | 2 llamadas secuenciales (pref + dismiss). Defendible como "veredicto" del nudge resuelto, pero es plumbing puro; su lógica gemela ya vive duplicada dentro de `ConfirmParkingUseCase` (que limpia la pref por su cuenta y delega el dismiss al janitor). |
| `NotifyParkingConfirmationUseCase` (33 LOC) | `CoordinatorParkingDetector` | Un `when` sobre `ParkingConfidence` + lookup del nombre. Podría plegarse en el port de notificación o en el coordinator. |
| `EvaluateDetectionReliabilityUseCase` (69 LOC) | `ObserveDetectionReliabilityUseCase` (único consumidor prod) | Caso límite: el par Evaluate(puro)/Observe(reactivo) es patrón deliberado y los tests de 3 VMs usan el Evaluate directo. Si se toca, convertir el Evaluate en función top-level (patrón §4), no borrarlo. |
| `UpdateParkingLocationUseCase` (88 LOC) | `SaveManualParkingUseCase` | Un solo call site pero NO trivial (5 side-effects ordenados) — **no inlinear**; solo constatar que ya no tiene entrada Koin propia necesaria más allá de la composición. |
| `SwapActiveVehicleFencesUseCase` (68 LOC) | `DeclareActiveVehicleUseCase` | Un solo call site, no trivial, con test propio. Mantener o fusionar con Declare — decisión de fase posterior. |

## 7 · CANDIDATOS A SPLIT (hacen más de una cosa)

| Clase | Responsabilidades mezcladas |
|---|---|
| **`ConfirmParkingUseCase`** (434 LOC, 11 deps) | (1) resolución auth+vehículo; (2) clasificación de zona privada→HOME_GEOFENCE; (3) guard de repark-plausibilidad; (4) **toda la construcción de la ruta** (`encodeFreshRoute`: frescura, trim al ancla, seed del origen, floor de extensión — ~80 LOC de lógica pura que pertenece a `DrivingRoute`/una función top-level, con 4 constantes ROUTE-* propias); (5) save + limpieza de geofence huérfana; (6) registro de geofence + janitor + telemetría; (7) seal del step-counter; (8) reset de nudges/prefs. Los pasos 5-8 son el "confirm" real; 1-4 son predicados/preparación con dueños naturales en otro sitio. |
| **`EvaluateSafetyNetCheckUseCase`** (439 LOC) | Dos veredictos en una clase: el `invoke` (acción del safety net) y `shouldReregisterCure` (throttle del cure — decisión independiente con sus propios tags DET-ANCHOR-FREEZE-001 F4 / DET-CURE-FRESH-001). Además el `invoke` encadena ≥6 pruebas independientes (BT-identity veto, frozen-counter, conjunction, AR-boarding, física peatonal, ask-when-blind) — es el mejor candidato a descomponerse en los predicados con dueño (`AnchorTrust`/`DriveProof`) que planea DET-VERDICT-NOT-PREDICATE-001. |
| `RunDepartureCheckUseCase` (164 LOC) | Menor: mezcla la decisión de reintentos (Retry/Dismissed, pura) con la orquestación de side-effects (upgrade + freshness + process). Aceptable como orquestador, pero la constante `MAX_INCONCLUSIVE_ATTEMPTS` + fall-through admisible son una política pura extraíble. |
| `ReleaseActiveParkingSessionUseCase` + `ProcessConfirmedDepartureUseCase` | No es split sino **fusión**: ver pares solapados. |

## 8 · Pares sospechosos de solape (solo señalados — análisis fino en otra fase)

1. **`ProcessConfirmedDepartureUseCase` ↔ `ReleaseActiveParkingSessionUseCase`** — misma
   secuencia (reportSpotReleased con prefetched → clearActiveParkingSession → removeGeofence →
   log), una resuelve por `geofenceId` + `publishSpot`, la otra por sesión + `ParkingReleaseReason`.
   Uno es casi superconjunto del otro; dos deciders para "cerrar una sesión" con semánticas de
   publicación paralelas.
2. **`DetectParkingDepartureUseCase` ↔ `VerifyDepartureEvidenceUseCase`** — mismas reglas de
   evidencia (velocidad creíble vía `isCredibleDrivingSpeed`, ENTER en ventana + admisibilidad
   session-birth + corroboración `isBeyondPedestrianReach`), aplicadas en dos momentos (worker vs
   pre-arm). El propio KDoc del safety net dice "Evidence rules mirror VerifyDepartureEvidenceUseCase".
   Tres implementaciones de la misma tríada de pruebas contando `EvaluateSafetyNetCheckUseCase`.
3. **`EvaluateHonestCloseUseCase` ↔ `EvaluateSafetyNetCheckUseCase`** — declarado "mirror of" en
   ambos KDoc: el mismo step-budget walked-vs-rode (`distance/stride × walkedStepFraction`) con
   guards de contador mudo/congelado duplicados en cada uno.
4. **`EvaluateHonestCloseUseCase` ↔ `EvaluateUnattendedParkingSaveUseCase`** — dos escaleras de
   cierre de sesión que degradan a pin-aproximado/zona/pregunta con la misma forma `zoneOrAsk`
   (una la tiene como helper privado, la otra la reimplementa inline como pin/zone por accuracy);
   entradas distintas (abort vs timeout) pero vocabulario y forma del veredicto paralelos.
5. **`EvaluateShortHopDriveProofUseCase` ↔ el drive-proof del coordinator** (`corroboratesDrive`,
   [DET-DRIVE-PROOF-001], dentro de `CoordinatorParkingDetector`, 2 573 LOC) — dos pruebas
   "¿esta sesión midió conducción?" (velocidad-ventana vs desplazamiento-desde-pin); una vive como
   use case y la otra como predicado privado del coordinator. Es exactamente el par que el plan
   `DriveProof` (DET-VERDICT-NOT-PREDICATE-001) quiere unificar bajo un dueño.
6. **`CalculateParkingConfidenceUseCase` ↔ `EvaluateParkingDecisionUseCase`** — el scoring
   Medium/High solo sirve para abrir la fase CANDIDATE cuyo veredicto real es del segundo
   (HIGH "por sí solo NO auto-confirma"); dos vocabularios de decisión encadenados sobre la misma
   parada.

## 9 · Notas y NO VERIFICADO

- **NO VERIFICADO:** cobertura de invocación en `iosMain` más allá de los matches de grep listados
  (`IosDepartureEventBusImpl`, `IosNotificationActionHandler`); iOS es target futuro y no compila
  en esta máquina dentro de esta tarea.
- **NO VERIFICADO:** que `EvaluateShortHopDriveProofUseCase` y `EvaluateUnattendedParkingSaveUseCase`
  no tengan un binding Koin en módulos de plataforma (`androidMain`/`iosMain` di): se verificó su
  ausencia en `di/DomainModule.kt` y que llegan al coordinator como default-params del constructor;
  no se barrieron todos los módulos Koin de plataforma.
- Las menciones descartadas como "solo KDoc" se comprobaron una a una con `grep -n`
  (`HomeSheetContent`, `VehicleRepositoryImpl`, `GeofenceJanitorWorker`, `UserParkingDao`,
  `ActivityRecognitionManagerImpl`, `AppNotificationManager` port).
- `MutableDetectionRuntimeState` es el único estado mutable compartido del área (singleton DI
  escrito por servicio + coordinator, leído por 3 use cases Observe*); ningún use case del
  inventario guarda estado propio entre invocaciones salvo el estado local por-Flow de
  `ObserveAdaptiveLocationUseCase`.
- Distribución de tamaño: 4 evaluadores concentran el 40 % del LOC del área
  (`EvaluateSafetyNetCheckUseCase` 439, `ConfirmParkingUseCase` 434, `EvaluateHonestCloseUseCase`
  328, `EvaluateUnattendedParkingSaveUseCase` 320); el resto media ~80 LOC.
- Todos los use cases del alcance tienen test unitario en commonTest **excepto**:
  `GetLastKnownLocationUseCase` (muerto), `DeclareActiveVehicleUseCase` (solo cubierto vía
  ViewModels) y `ObserveDetectionReliabilityUseCase` (solo vía VM tests; su mitad pura sí tiene test).
