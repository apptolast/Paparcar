# 05 — Cobertura de tests del subsistema de detección · matriz tag → test → escenario

> Subagente E · refactor de solo-lectura · 2026-08-18. Generado con Grep sobre producción
> (`commonMain/domain/{coordinator,detection,diagnostics,usecase/detection,usecase/parking}` +
> `androidMain/{detection,bluetooth,location,geofence-adjacent}`) y sobre TODOS los source sets de
> test (`commonTest`, `androidUnitTest`). "Con test" = al menos un fichero de test menciona el tag
> (comentario de guard o aserción); no se ha medido cobertura de líneas.

## Cifras

> 🔄 **Re-medido el 2026-08-24 sobre master `073f80f7`** (la medición original era del 18-08, y el
> subsistema ha crecido un 36 % en tags desde entonces). Mismos directorios, misma definición.

| Métrica | 18-08 | **24-08** |
|---|---|---|
| Tags únicos en producción del subsistema | 129 | **176** |
| Tags con al menos un test que los menciona | 86 (66.7 %) | **122 (69.3 %)** |
| Tags 🔴 sin ningún test que los nombre | 43 (33.3 %) | **54 (30.7 %)** |
| Trazas de campo en el harness de replay | 9 → 12 tests | **13 `Trace_*.kt` → 18 tests** |

**El agregado engaña, y el desglose es la cifra que importa para el refactor:**

| | tags | con test | 🔴 |
|---|---|---|---|
| **Dominio puro `commonMain`** — lo que F6 mueve | 136 | **113 (83.1 %)** | 23 |
| **Solo `androidMain`** — servicio, workers, sensores, BT | 40 | **9 (22.5 %)** | 31 |

Y de esos **23 del dominio, los 23 viven en ficheros que SÍ tienen tests** (comprobado uno a uno:
cero huérfanos). Lo que falta ahí no es cobertura, es la **etiqueta**: el guard no cita su tag en
ningún test, aunque el símbolo que lo contiene se ejercita. ⚠️ Ojo con sobreleerlo: *"su fichero
tiene tests"* es una afirmación mucho más débil que *"esa rama está cubierta"* — esta métrica cuenta
menciones, no líneas. Pero sirve para lo que importa aquí: **en la mitad que F6 va a partir no hay
ni un fichero de dominio sin test**, y el 30.7 % rojo es abrumadoramente la capa Android, que F6 no
toca.

## 1 · Harness de replay — trazas de campo reales (`domain/coordinator/replay/`)

`DetectionTraceReplayer.kt` reproduce eventos FIX/STEP con reloj inyectado contra el
`CoordinatorParkingDetector` REAL (env con fakes; `confirmHoldMs=0` salvo donde el hold es
load-bearing). `DetectionTraceReplayTest.kt` (12 tests) es la materialización de
[DET-SOLID-001][C4]: *cada bug de campo se convierte en fixture permanente*. Todas las trazas
citan su sesión Firestore `diagnostics/…/sessions/<id>`.

| Traza | Escenario de campo (fecha · device · lugar) | Tags que fija | Veredicto fijado |
|---|---|---|---|
| `Trace_BugReparkWalk001.kt` | 2026-07-03 22:13Z · El Puerto — parkeo real, paseo, EXIT arma sobre peatón (BUG-REPARK-WALK-001) | DET-SOLID-001 | `aborted_false_enter`, 0 saves, 0 prompts; contraste: mismo stream con arm verificado SÍ confirma |
| `Trace_CalleGavia001.kt` | 2026-07-04 17:11Z · Calle Gavia — detección CORRECTA con 2 pasos espurios en semáforo | ANCHOR-LOCK-001 | ancla en Gavia, no en el semáforo (umbral lock=8) |
| `Trace_Supermarket001.kt` | 2026-07-04 16:46Z · supermercado — EXIT tardío, arm con el coche ya aparcado, deriva del ancla dentro de la tienda | ANCHOR-LOCK-001 | prompt (nunca save silencioso), ancla BLOQUEADA en el coche, "Sí" ancla en el coche |
| `Trace_CameliasHop001.kt` | 2026-07-14 19:19Z · Redmi Note 11 · Camelias — hop 300 m, EXIT entregado con el viaje ya acabado (FN silencioso) | DET-HONEST-CLOSE-001 | characterization: `aborted_false_enter` + outcome/fix expuestos para la escalera honest-close |
| `Trace_LateExitOnFoot001.kt` | 2026-07-15 02:11Z · Oppo CPH2371 — EXIT tardío con el usuario a 1.1 km A PIE (el coche NO se movió) | DET-HONEST-CLOSE-001 | contraejemplo permanente: silencio correcto (`aborted_no_movement`), sin zona ni prompt |
| `Trace_Enamorados001.kt` | 2026-07-15 16:11Z · Redmi/MIUI — FP de 1.11 km: ancla congelada en un semáforo | DET-ANCHOR-EGRESS-001, DET-CREDIBLE-DRIVE-001, DET-SHORT-TRIP-FREEZE-001, DET-FROZEN-COUNTER-001 | 3 tests: unfreeze por desplazamiento → confirm real; sin recovery → ceiling degrada a prompt; timeout desatendido → ZONA en el egress-birth |
| `Trace_CameliasOppo001.kt` | 2026-07-15 16:11Z · Oppo CPH2371 — pin dentro de la casa (contador de pasos MUDO), ground truth corroborado por el Redmi | DET-CREDIBLE-DRIVE-001 (+DET-C-02 hold real) | ancla walk-entered → prompt, nunca pin silencioso |
| `Trace_GaleoteOppo001.kt` | 2026-07-16 19:45Z · Oppo CPH2371 · Galeote 31 — FN: la deceleración final tiñó el ancla como walk-entered | DET-CREDIBLE-DRIVE-001 | confirm silencioso en el ancla verdadera (`confirmed_steps+egress`) |
| `Trace_RedmiLateExitHome001.kt` | 2026-07-27 18:36Z · Redmi 2201117TY — EXIT retenido 4 110 m por MIUI, sesión nace tras el viaje | DET-NODRIVE-ZONE-001 (+DET-DRIVE-PROOF-001, DET-CONJUNCTION-001) | zona honesta en el ancla del bordillo (`confirmed_unattended_zone_no_drive_egress`) |
| `Trace_MotorwayRedmi001.kt` | 2026-08-20 · Redmi — 102 min, 967 fixes, 131,4 km/h pico, y un sello AR `ON_BICYCLE` mata la sesión | DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001 | la medición refuta al sello: `confirmed_*` con el sello puesto; control sin sello confirma igual |
| `Trace_HouseMirage001.kt` | 2026-08-22 20:50 · Oppo — espejismo GPS interior (36 km/h parado), FP dentro de la casa a 49 m del pin bueno | DET-EXIT-FIX-CANNOT-PROVE-ITS-OWN-EXIT-001 | 2 tests: arm honesto → `ended` sin pin; arm `verified_speed` (el viejo) → `aborted_false_enter` |
| `Trace_CameliasGondola001.kt` | **2026-08-22 18:41 · Oppo · viaje 2** — 3 fixes declarando `speed=0.0` separados 122,5 m en 9,56 s | DET-STOP-MUST-BE-STILL-IN-SPACE-001 | pin en el reposo real; ⚠️ neutralizando el guard **reproduce la coordenada de campo exacta** |
| `Trace_GondolaCamelias001.kt` | **2026-08-22 14:41 · Redmi · viaje 1** — viaje de 75 km/h dictaminado "propulsado por humanos" 36 s DESPUÉS de congelar el ancla | DET-CADENCE-CANNOT-ACCUSE-AFTER-EGRESS-001 (+DET-GAP-ANCHOR-ZONE-001, DET-USER-YES-IS-NOT-A-COORDINATE-001) | sin `human_powered`; **sigue degradando y está bien** (hueco GPS real de 100,5 s → `anchor_gap_entered`); el "sí" guarda ÁREA, no punto |

Además hay **trazas de campo embebidas como fixtures inline** (no en `replay/`) dentro de
`CoordinatorParkingDetectorTest.kt` — p. ej. el hop late-armed del **field 2026-08-15 21:26 (Redmi)**
para DET-UNVERIFIED-ARM-DRIVE-PROOF-001 (líneas ~57 y ~2953). Fuera de Kotlin **no existen fixtures
json/csv de campo en el repo** (el barrido solo encontró schemas de Room y configs).

⚠️ **Corrección al 24-08 sobre la fuente.** Este doc daba por supuesto que la telemetría cruda vive
en Firestore `diagnostics/`. **Hay una segunda fuente, y a veces es la única**: el
`parkdiag.log` del propio móvil (`adb exec-out run-as com.rndeveloper.paparcar cat files/parkdiag.log`).
Las tres trazas del 22-08 salieron de ahí porque **la sesión del Oppo de esa noche nunca llegó
íntegra a Firestore** (el móvil murió de batería). El log rota una sola vez, así que la ventana es
finita: el 24-08 todavía alcanzaba el 08-22 14:08 en los dos aparatos. Receta de transcripción y
base de epoch, en `docs/backlog/det-2208-trips-become-replays-001.md`.

## 2 · Inventario de tests del subsistema

`shared/src/commonTest/kotlin/com/rndeveloper/paparcar/…` (nº de `@Test`):

| Fichero | Cubre | Tests |
|---|---|---|
| `domain/coordinator/CoordinatorParkingDetectorTest.kt` | CoordinatorParkingDetector (unidad, 29 tags) | 76 |
| `domain/coordinator/ConfirmationPhaseMappingTest.kt` | mapeo de fase [DET-PHASE-001] | 1 |
| `domain/coordinator/replay/DetectionTraceReplayTest.kt` | replays de campo (§1) | 12 |
| `domain/detection/DrivingRouteTest.kt` | DrivingRoute (ROUTE-END-AT-CAR, ROUTE-FIX-ACCURACY) | 12 |
| `domain/detection/GhostFgsReapDecisionTest.kt` | GhostFgsReapDecision [DET-FGS-REAPER-001] | 4 |
| `domain/detection/HumanPoweredRideTest.kt` | HumanPoweredRide [DET-BIKE-NOT-A-CAR-001] | 7 |
| `domain/detection/ParkingStrategyResolverTest.kt` | resolveStrategy [DET-STRATEGY-GATE-001] | 16 |
| `domain/detection/PendingNudgeDecisionTest.kt` | PendingNudgeDecision [DET-NEVER-SILENT-001] | 3 |
| `domain/detection/SentryLifecycleDecisionTest.kt` | SentryLifecycleDecision | 11 |
| `domain/detection/SentryWakeCooldownTest.kt` | SentryWakeCooldown [DET-SENTRY-COOLDOWN-001] | 9 |
| `domain/detection/SessionSupersedeTest.kt` | SessionSupersede [DET-SUPERSEDE-001, DET-AR-REARM-001] | 3 |
| `domain/detection/VehicleFenceOwnershipPolicyTest.kt` | VehicleFenceOwnershipPolicy [VEH-ACTIVE-FENCE-001, DET-TIERS-001] | 16 |
| `domain/diagnostics/DetectionEventLoggerTest.kt` | DetectionEventLogger (sin tags) | 2 |
| `domain/usecase/detection/EvaluateArEnterArmUseCaseTest.kt` | escalera AR-arm [DET-AR-FIRST-001] | 8 |
| `domain/usecase/detection/EvaluateBtArbitrationUseCaseTest.kt` | arbitraje BT [DET-BT-WRONG-CAR-ABORT-001] | 12 |
| `domain/usecase/detection/EvaluateBtParkUseCaseTest.kt` | veredicto de park BT | 11 |
| `domain/usecase/detection/EvaluateDetectionReliabilityUseCaseTest.kt` | fiabilidad [DET-RELIABILITY-001] | 9 |
| `domain/usecase/detection/EvaluateFirstParkNudgeUseCaseTest.kt` | first-park nudge | 7 |
| `domain/usecase/detection/EvaluateGeofenceExitUseCaseTest.kt` | veredicto de EXIT | 7 |
| `domain/usecase/detection/EvaluateShortHopDriveProofUseCaseTest.kt` | short-hop drive proof [DET-SHORT-HOP-PROOF-001] | 12 |
| `domain/usecase/detection/ObserveDepartureWatchGapUseCaseTest.kt` | watch gap [DET-WATCH-REACTIVATE-001, DET-WATCH-RESUME-RACE-001] | 15 |
| `domain/usecase/detection/ObserveDetectionReadinessUseCaseTest.kt` | readiness [DET-READY-001i, DET-READY-TRIP-OVER-PARKED-001] | 16 |
| `domain/usecase/parking/CalculateParkingConfidenceUseCaseTest.kt` | confianza [BUG-DETECT-310503] | 9 |
| `domain/usecase/parking/ConfirmParkingUseCaseTest.kt` | ConfirmParking + rutas (9 tags) | 44 |
| `domain/usecase/parking/DetectParkingDepartureUseCaseTest.kt` | salida [DET-DEPART-PROOF-001, DET-EXIT-TRUST-001] | 17 |
| `domain/usecase/parking/EvaluateBackfillDeferralUseCaseTest.kt` | backfill [DET-BACKFILL-TAINT-001] | 7 |
| `domain/usecase/parking/EvaluateHonestCloseUseCaseTest.kt` | honest close (5 tags) | 18 |
| `domain/usecase/parking/EvaluateParkingDecisionUseCaseTest.kt` | decisión de confirm (8 tags) | 33 |
| `domain/usecase/parking/EvaluateSafetyNetCheckUseCaseTest.kt` | safety net (9 tags) | 49 |
| `domain/usecase/parking/EvaluateUnattendedParkingSaveUseCaseTest.kt` | save desatendido (8 tags) | 22 |
| `domain/usecase/parking/ObserveParkedVehiclesUseCaseTest.kt` | coches aparcados | 7 |
| `domain/usecase/parking/ParkNudgeUseCasesTest.kt` | nudges [DET-NUDGE-PERSIST-001] | 7 |
| `domain/usecase/parking/ParkingEdgeCaseTest.kt` | bordes [QA-004] | 13 |
| `domain/usecase/parking/ParkingFlowIntegrationTest.kt` | flujo integrado (sin tags) | 14 |
| `domain/usecase/parking/ProcessConfirmedDepartureUseCaseTest.kt` | salida confirmada | 6 |
| `domain/usecase/parking/ReleaseActiveParkingSessionUseCaseTest.kt` | release [PARK-DELETE-NO-DECLARE-001] | 21 |
| `domain/usecase/parking/RevertParkingUseCaseTest.kt` | revert | 4 |
| `domain/usecase/parking/RunDepartureCheckUseCaseTest.kt` | departure check (7 tags) | 14 |
| `domain/usecase/parking/RunHonestCloseUseCaseTest.kt` | honest close runner | 5 |
| `domain/usecase/parking/SaveManualParkingUseCaseTest.kt` | park manual [DET-MANUAL-CANCEL-001] | 7 |
| `domain/usecase/parking/UpdateParkingLocationUseCaseTest.kt` | mover pin | 10 |
| `domain/usecase/parking/VerifyDepartureEvidenceUseCaseTest.kt` | evidencia de salida [DET-G-05, DET-RIDE-PROOF-001] | 10 |

`androidUnitTest` (Robolectric): `detection/service/CoordinatorDetectionServiceTest.kt` (1 test,
[DET-B-02][DET-B-03] — solo ciclo de vida FGS) · `worker/ParkingSyncWorkerTest.kt` (12 tests,
[MAPPER-003]). **Es TODO el testing de la capa androidMain del subsistema.**

Tags de detección fijados por tests FUERA del subsistema (cuentan como cubiertos):
`ParkingSessionMapperTest` (DET-PIN-PROVENANCE-001, DET-ROUTE-SNAP-STORE-001, DET-ROUTE-TRACK-001),
`DetectionStoryTest`/`DetectionUiStateTest` (DET-WATCH-HONEST-001, DET-TOGGLE-001),
`SettingsViewModelTest` (DET-TOGGLE-002), `GetOneLocationUseCaseTest` (DET-BREADCRUMBS-001),
`VehicleCatalogTest` (VEHICLE-CATEGORIZATION-001), `SwapActiveVehicleFencesUseCaseTest` +
`HomeViewModelTest` (VEH-ACTIVE-FENCE-001).

## 3 · Matriz tag → test(s) → escenario de campo

Leyenda: ✅ = con test que menciona el tag · 🔴 = sin ningún test. "Producción" = primera
ubicación (`shared/src/{commonMain,androidMain}/kotlin/com/rndeveloper/paparcar/` + fichero:línea).
Escenario: de comentarios de traza/test o de `docs/backlog/`; «backlog» = existe
`docs/backlog/<tag>.md` con el contexto; — = escenario NO VERIFICADO en esta pasada.

### DET-* núcleo del coordinator y decisión

| Tag | Producción (1ª de N) | Tests | Escenario de campo |
|---|---|---|---|
| ✅ ANCHOR-LOCK-001 | domain/coordinator/CoordinatorParkingDetector.kt:180 (×8) | CoordinatorParkingDetectorTest, replay (Gavia, Supermarket) | 2026-07-04 supermercado: pin derivó dentro de la tienda |
| ✅ DET-ANCHOR-EGRESS-001 | CoordinatorParkingDetector.kt:204 (×9) | replay (Enamorados), EvaluateUnattendedParkingSaveUseCaseTest | 2026-07-15 Redmi: FP 1.11 km (ancla en semáforo) · backlog |
| ✅ DET-ANCHOR-FREEZE-001 | CoordinatorParkingDetector.kt:185 (×15) | CoordinatorParkingDetectorTest, EvaluateSafetyNetCheckUseCaseTest | congelar ancla al fin de conducción — NO VERIFICADO viaje concreto |
| ✅ DET-AR-FIRST-001 | CoordinatorParkingDetector.kt:660 (×21) | CoordinatorParkingDetectorTest, EvaluateArEnterArmUseCaseTest | backlog (det-ar-first-001.md) |
| ✅ DET-AR-REARM-001 | domain/detection/SessionSupersede.kt:12 (×12) | SessionSupersedeTest | — |
| ✅ DET-BIKE-NOT-A-CAR-001 | CoordinatorParkingDetector.kt:279 (×16) | CoordinatorParkingDetectorTest, HumanPoweredRideTest | field 2026-08-16: FP bici (AR ON_BICYCLE) · backlog |
| ✅ DET-C-01 | domain/usecase/parking/EvaluateParkingDecisionUseCase.kt:165 | EvaluateParkingDecisionUseCaseTest | — |
| ✅ DET-C-02 | CoordinatorParkingDetector.kt:118 (×9) | CoordinatorParkingDetectorTest, replay (CameliasOppo, hold real) | errand-stop: descarte del primer confirm tentativo |
| ✅ DET-CONFIRM-ANCHOR-001 | CoordinatorParkingDetector.kt:1062 (×3) | CoordinatorParkingDetectorTest | backlog |
| ✅ DET-CONFIRM-FRESHNESS-001 | CoordinatorParkingDetector.kt:224 (×18) | CoordinatorParkingDetectorTest, EvaluateUnattendedParkingSaveUseCaseTest | backlog |
| ✅ DET-CREDIBLE-DRIVE-001 | CoordinatorParkingDetector.kt:216 (×13) | CoordinatorParkingDetectorTest, replay (3 trazas) | 2026-07-15/16: pin en casa (Oppo) + FN Galeote · backlog |
| 🔴 DET-D-01 | EvaluateParkingDecisionUseCase.kt:9 | — | doctrina de decisión — NO VERIFICADO |
| 🔴 DET-D-02 | CoordinatorParkingDetector.kt:1685 (×3) | — | — |
| ✅ DET-D-03 | CoordinatorParkingDetector.kt:74 (×4) | CoordinatorParkingDetectorTest | — |
| ✅ DET-DRIVE-PROOF-001 | CoordinatorParkingDetector.kt:324 (×14) | CoordinatorParkingDetectorTest, replay (RedmiLateExitHome) | backlog (det-drive-proof-001.md) |
| ✅ DET-EGRESS-PEDESTRIAN-CEILING-001 | CoordinatorParkingDetector.kt:1870 (×5) | EvaluateParkingDecisionUseCaseTest | derivado del FP Enamorados (ceiling del egress) |
| ✅ DET-FROZEN-COUNTER-001 | domain/diagnostics/DetectionEvent.kt:113 (×24) | 5 ficheros + replay | field 2026-07-25/26 Redmi: viaje de 33 min perdido por nudge · backlog |
| ✅ DET-G-04 | CoordinatorParkingDetector.kt:485 (×4) | CoordinatorParkingDetectorTest, replay (ReparkWalk contraste) | semántica short-hop con arm verificado |
| ✅ DET-G-05 | CoordinatorParkingDetector.kt:450 (×11) | CoordinatorParkingDetectorTest, VerifyDepartureEvidenceUseCaseTest | — |
| ✅ DET-GAP-ANCHOR-001 | CoordinatorParkingDetector.kt:254 (×10) | 3 ficheros | backlog (det-gap-anchor-001.md) |
| ✅ DET-GAP-ANCHOR-ZONE-001 | CoordinatorParkingDetector.kt:260 (×10) | CoordinatorParkingDetectorTest, EvaluateUnattendedParkingSaveUseCaseTest | 2026-08: hueco GPS acota su propia duda (`645bb09e`) · backlog |
| ✅ DET-HONEST-CLOSE-001 | CoordinatorParkingDetector.kt:398 (×17) | replay (2 trazas) + Evaluate/RunHonestCloseUseCaseTest | 2026-07-14 Camelias FN + 2026-07-15 contraejemplo · backlog |
| ✅ DET-JAM-WINDOW-001 | CoordinatorParkingDetector.kt:538 (×6) | CoordinatorParkingDetectorTest | atasco: ventana de vehicle-exit · backlog |
| ✅ DET-KINEMATIC-EGRESS-001 | CoordinatorParkingDetector.kt:198 (×7) | CoordinatorParkingDetectorTest, EvaluateParkingDecisionUseCaseTest | backlog |
| 🔴 LOC-001 | CoordinatorParkingDetector.kt:2095 | — | — |
| 🔴 LOC-002 | CoordinatorParkingDetector.kt:749 | — | — |
| ✅ DET-NODRIVE-ZONE-001 | CoordinatorParkingDetector.kt:335 (×3) | replay (RedmiLateExitHome), 2 unit | 2026-07-27 Redmi: EXIT retenido 4 110 m · backlog |
| ✅ DET-PHASE-001 | CoordinatorParkingDetector.kt:116 (×8) | ConfirmationPhaseMappingTest, HomeTripControllerTest | — |
| ✅ DET-RECONCILE-001 | CoordinatorParkingDetector.kt:1115 (×27) | 4 ficheros | backlog (det-reconcile-001.md) |
| ✅ DET-SENTRY-ARM-PEDESTRIAN-CLOCK-001 | CoordinatorParkingDetector.kt:330 (×5) | 3 ficheros | backlog |
| ✅ DET-SHORT-HOP-PROOF-001 | CoordinatorParkingDetector.kt:121 (×9) | CoordinatorParkingDetectorTest, EvaluateShortHopDriveProofUseCaseTest | field 2026-08-14 rutas (short-hop) · backlog |
| ✅ DET-SHORT-TRIP-FREEZE-001 | CoordinatorParkingDetector.kt:2125 (×2) | CoordinatorParkingDetectorTest, replay (Enamorados) | — |
| ✅ DET-SOLID-001 | DetectionEvent.kt:38 (×54) | 13 ficheros incl. todo el harness de replay | doctrina "cada bug de campo → fixture"; BUG-REPARK-WALK 2026-07-03 |
| ✅ DET-STEP-BUDGET-ORIGIN-001 | CoordinatorParkingDetector.kt:1585 (×17) | Evaluate/RunHonestCloseUseCaseTest | backlog |
| ✅ DET-STEP-SPEED-GATE-001 | CoordinatorParkingDetector.kt:349 (×6) | EvaluateParkingDecisionUseCaseTest | — |
| ✅ DET-UNVERIFIED-CONFIRM-001 | EvaluateParkingDecisionUseCase.kt:185 | EvaluateParkingDecisionUseCaseTest | backlog (det-unverified-confirm-001.md) |
| ✅ DET-VERDICT-NOT-PREDICATE-001 | domain/detection/HumanPoweredRide.kt:13 | HumanPoweredRideTest, EvaluateShortHopDriveProofUseCaseTest | doctrina (e5df4cc6) · backlog |
| ✅ DET-WALK-ENTERED-ANCHOR-ZONE-001 | CoordinatorParkingDetector.kt:125 (×15) | EvaluateUnattendedParkingSaveUseCaseTest | backlog |
| ✅ DET-WALK-FLOOR-001 | EvaluateHonestCloseUseCase.kt:128 (×4) | EvaluateHonestCloseUseCaseTest | backlog |
| ✅ DET-ZOMBIE-PROBE-001 | CoordinatorParkingDetector.kt:492 (×5) | CoordinatorParkingDetectorTest | backlog |
| 🔴 BUG-COORD-112 | CoordinatorParkingDetector.kt:594 | — | — |
| 🔴 BUG-COORD-115 | CoordinatorParkingDetector.kt:1050 | — | — |
| 🔴 BUG-DETECT-310502 | CoordinatorParkingDetector.kt:2437 | — | — |
| ✅ BUG-DETECT-310503 | CalculateParkingConfidenceUseCase.kt:16 (×2) | CalculateParkingConfidenceUseCaseTest | — |
| 🔴 BUG-GARAGE-COLA-001 | CoordinatorParkingDetector.kt:1729 | — | cola de garaje — NO VERIFICADO |
| 🔴 BUG-SERVICE-109 | CoordinatorParkingDetector.kt:444 | — | — |
| 🔴 BUG-SCOOTER-001 | domain/detection/ParkingStrategyResolver.kt:29 (×2) | — | — |
| ✅ BUG-WALK-DEPART-001 | ConfirmParkingUseCase.kt:287 (×4) | ConfirmParkingUseCaseTest, RunDepartureCheckUseCaseTest | el nag afirmaría que el coche está donde el peatón |
| ✅ PARKING-001 | CoordinatorParkingDetector.kt:2199 (×2) | CoordinatorParkingDetectorTest | — |
| 🔴 REFACTOR-200 | CoordinatorParkingDetector.kt:162 (×4) | — | refactor histórico — NO VERIFICADO |
| 🔴 REFACTOR-300 | CoordinatorParkingDetector.kt:1429 (×9) | — | refactor histórico — NO VERIFICADO |
| 🔴 DET-LOG-03 | CoordinatorParkingDetector.kt:549 (×4) | — | — |
| ✅ DET-LOG-04 | DetectionEvent.kt:84 (×3) | CoordinatorParkingDetectorTest | — |

### DET-* triggers, salida, safety net, sesión

| Tag | Producción (1ª de N) | Tests | Escenario |
|---|---|---|---|
| ✅ DET-BACKFILL-TAINT-001 | EvaluateBackfillDeferralUseCase.kt:9 (×10) | EvaluateBackfillDeferralUseCaseTest | backlog |
| ✅ DET-CONJUNCTION-001 | EvaluateArEnterArmUseCase.kt:67 (×10) | EvaluateSafetyNetCheckUseCaseTest | — |
| ✅ DET-CURE-FRESH-001 | EvaluateSafetyNetCheckUseCase.kt:420 (×2) | EvaluateSafetyNetCheckUseCaseTest | — |
| ✅ DET-DEPART-PROOF-001 | DetectParkingDepartureUseCase.kt:43 (×6) | DetectParkingDepartureUseCaseTest, RunDepartureCheckUseCaseTest | backlog |
| ✅ DET-EXIT-TRUST-001 | EvaluateGeofenceExitUseCase.kt:67 (×11) | 3 ficheros | evento re-entregado pierde autoridad directa |
| ✅ DET-MANUAL-CANCEL-001 | domain/detection/ManualParkingDetection.kt:20 (×3) | SaveManualParkingUseCaseTest, HomeViewModelTest | — |
| ✅ DET-NEVER-SILENT-001 | domain/detection/PendingNudgeDecision.kt:4 (×7) | PendingNudgeDecisionTest | — |
| ✅ DET-NUDGE-PERSIST-001 | domain/detection/PendingParkNudge.kt:6 (×4) | ParkNudgeUseCasesTest | backlog |
| ✅ DET-NUDGE-PIN-PROVENANCE-001 | SaveManualParkingUseCase.kt:42 (×2) | SaveManualParkingUseCaseTest | backlog |
| ✅ DET-PIN-PROVENANCE-001 | CoordinatorParkingDetector.kt:1581 (×10) | ParkingSessionMapperTest | backlog |
| ✅ DET-RIDE-PROOF-001 | EvaluateArEnterArmUseCase.kt:56 (×18) | 4 ficheros | nunca liberar por distancia sola · backlog |
| ✅ DET-SAFETY-NET-001 | EvaluateSafetyNetCheckUseCase.kt:9 (×7) | EvaluateSafetyNetCheckUseCaseTest (49 tests) | reconciliar salidas que el OS no entregó |
| ✅ DET-SESSION-BIRTH-001 | EvaluateArEnterArmUseCase.kt:82 (×8) | 5 ficheros | backlog |
| ✅ DET-SUPERSEDE-001 | DetectionEvent.kt:207 (×6) | SessionSupersedeTest | — |
| ✅ DET-BT-IDENTITY-GATE-001 | EvaluateSafetyNetCheckUseCase.kt:133 (×9) | EvaluateSafetyNetCheckUseCaseTest | — |
| ✅ DET-BT-OWNERSHIP-001 | CoordinatorParkingDetector.kt:1011 (×2) | CoordinatorParkingDetectorTest, VehicleFenceOwnershipPolicyTest | backlog |
| ✅ DET-BT-WRONG-CAR-ABORT-001 | EvaluateBtArbitrationUseCase.kt:36 (×3) | EvaluateBtArbitrationUseCaseTest | backlog |
| ✅ DET-BT-CONNECTED-NOT-PAIRED-001 | EvaluateBtArbitrationUseCase.kt:34 (×8) | ParkingStrategyResolverTest | backlog |
| ✅ DET-TIERS-001 | EvaluateBtArbitrationUseCase.kt:5 (×9) | 4 ficheros | backlog |
| ✅ DET-STRATEGY-GATE-001 | ObserveDepartureWatchGapUseCase.kt:49 (×6) | 3 ficheros | backlog |
| ✅ DET-RELIABILITY-001 | ObserveDetectionReliabilityUseCase.kt:13 (×3) | EvaluateDetectionReliabilityUseCaseTest | backlog |
| ✅ DET-READY-001i | ObserveDetectionReadinessUseCase.kt:86 | ObserveDetectionReadinessUseCaseTest, AppPermissionStateTest | — |
| 🔴 DET-READY-001b | ObserveDetectionReadinessUseCase.kt:26 (×2) | — | — |
| 🔴 DET-READY-001c | domain/detection/DetectionRuntimeState.kt:77 (×7) | — | — |
| ✅ DET-READY-TRIP-OVER-PARKED-001 | ObserveDetectionReadinessUseCase.kt:36 (×2) | ObserveDetectionReadinessUseCaseTest | field 2 coches: readiness mataba la UI de viaje (`c55ab8eb`) · backlog |
| ✅ DET-WATCH-REACTIVATE-001 | domain/detection/DepartureWatchResumer.kt:5 (×7) | ObserveDepartureWatchGapUseCaseTest | backlog |
| ✅ DET-WATCH-RESUME-RACE-001 | ObserveDepartureWatchGapUseCase.kt:60 (×2) | ObserveDepartureWatchGapUseCaseTest | backlog |
| ✅ DET-SENTRY-COOLDOWN-001 | DetectionEvent.kt:258 (×13) | SentryWakeCooldownTest | field 17/18-08: cooldown cubrió el viaje del Oppo · backlog |
| ✅ DET-FGS-REAPER-001 | domain/detection/GhostFgsReapDecision.kt:4 (×3) | GhostFgsReapDecisionTest | backlog |
| ✅ DET-TOGGLE-001 | ObserveDetectionReadinessUseCase.kt:31 (×4) | DetectionUiStateTest (solo presentación) | — |
| ✅ DET-TOGGLE-002 | EvaluateFirstParkNudgeUseCase.kt:9 (×5) | SettingsViewModelTest (solo presentación) | — |
| ✅ DET-TRIP-WITNESS-001 | EvaluateHonestCloseUseCase.kt:115 (×9) | Evaluate/RunHonestCloseUseCaseTest | backlog |
| 🔴 DET-G-01b | domain/detection/ManualParkingDetection.kt:4 (×2) | — | — |
| 🔴 DET-SESSION-RELIABILITY-STAMP-001 | domain/diagnostics/DeviceInfoProvider.kt:26 | — | fiabilidad estampada por sesión — NO VERIFICADO |
| ✅ DET-BREADCRUMBS-001 | domain/detection/TripTrail.kt:7 (×2) | GetOneLocationUseCaseTest | backlog |
| ✅ DET-WATCH-HONEST-001 | detection/service/CoordinatorDetectionService.kt:258 (×3) | DetectionStoryTest (solo presentación) | — |
| ✅ ARCH-MONITORING-002 | ParkingStrategyResolver.kt:36 (×2) | ParkingStrategyResolverTest | — |

### androidMain — servicio, workers, sensores, BT (la zona roja)

| Tag | Producción (1ª de N) | Tests | Escenario |
|---|---|---|---|
| 🔴 DET-INTAKE-001 | detection/service/ForegroundServiceController.kt:64 (×11) | — | intake único serializado del servicio |
| 🔴 DET-B-01 | detection/service/CoordinatorDetectionService.kt:288 (×2) | — | — |
| ✅ DET-B-02 | CoordinatorDetectionService.kt:200 (×2) | CoordinatorDetectionServiceTest (1 test Robolectric) | — |
| 🔴 DET-ENDED-VETO-RACE-001 | CoordinatorDetectionService.kt:1324 (×2) | — | backlog (det-ended-veto-race-001.md) |
| 🔴 DETECT-SERVICE-RACE-001 | CoordinatorDetectionService.kt:1314 (×2) | — | — |
| 🔴 DET-RESIDENT-FGS-001 | DetectionEvent.kt:250 (×22) | — | backlog (det-resident-fgs-001.md) |
| 🔴 BUG-FGS-001 / 001a / 100 | bluetooth/BluetoothDetectionService.kt:115 · ForegroundServiceController.kt:24/19 | — | crashes FGS 2026-05 (docs/backlog/fgs-crashes-2026-05-25.md) — NO VERIFICADO |
| 🔴 DET-EXACT-HEARTBEAT-001 | detection/ExactHeartbeatScheduler.kt:13 (×5) | — | backlog (det-exact-heartbeat-001.md) |
| 🔴 DET-SIGMOTION-001 | detection/SignificantMotionMonitor.kt:19 (×5) | — | — |
| 🔴 DET-STEP-SENSOR-REDMI-001 | detection/sensor/AndroidStepDetectorSource.kt:28 | — | contador de pasos mudo del Redmi — NO VERIFICADO |
| 🔴 DET-AR-FIRST-001b | detection/ActivityRecognitionManagerImpl.kt:73 (×3) | — | — |
| 🔴 DET-G-01 | detection/ActivityRecognitionManagerImpl.kt:38 (×9) | — | — |
| 🔴 ANCHOR-PERSIST-001 | detection/TripTrailImpl.kt:13 + ParkingSafetyNetWorker.kt (×5) | — | persistencia del ancla entre despertares del worker |
| 🔴 DET-ARRIVAL-HANDOFF-001 | detection/worker/ParkingSafetyNetWorker.kt:351 | — | — |
| 🔴 DET-ARRIVAL-DOUBLE-PIN-001 | detection/worker/ParkingBackfillWorker.kt:51 (×2) | — | backlog (det-arrival-double-pin-001.md) |
| 🔴 BUG-WORKER-001 | detection/WorkManagerParkingSyncScheduler.kt:50 | — | — |
| 🔴 BUG-WORKER-002 | detection/worker/SaveNewParkingSessionWorker.kt:68 | — | — |
| ✅ MAPPER-003 | detection/worker/SaveNewParkingSessionWorker.kt:138 | ParkingSyncWorkerTest | — |
| 🔴 SESSION-RESTORE-001 | detection/worker/GeofenceJanitorWorker.kt:91 | — | — |
| 🔴 GEOF-001 | detection/GeofenceManagerImpl.kt:194 (×3) | — | — |
| 🔴 GEOF-RESTORE-001 | detection/receiver/BootCompletedReceiver.kt:23 | — | re-registrar geocercas tras boot (el OS las borra) |
| 🔴 DET-EXIT-WITNESS-001 | detection/GeofenceManagerImpl.kt:90 (×4) | — | — |
| 🔴 DET-BT-TIMEOUT-SAVE-001 | bluetooth/BluetoothParkingDetector.kt:124 (×3) | — | backlog (det-bt-timeout-save-001.md) |
| 🔴 DET-RETURN-ANCHOR-001 | bluetooth/BluetoothDetectionService.kt:150 (×9) | — | — |
| 🔴 ROUTE-PASSIVE-FILL-001 | location/AndroidLocationDataSourceImpl.kt:93 (×5) | — | field 2026-08-14 rutas (`9625c0ed`) · backlog |
| 🔴 GEOCODE-DEADLINE-001* | location/AndroidGeocoderDataSourceImpl.kt:109 | (AddressAndPlaceRepositoryImplTest cubre la variante común) | — |
| 🔴 VEHICLE-SYNC-001 | ConfirmParkingUseCase.kt:130 | — | — |
| ✅ VEHICLE-CATEGORIZATION-001 | detection/worker/SaveNewParkingSessionWorker.kt:114 | VehicleCatalogTest | — |
| ✅ VEH-ACTIVE-FENCE-001 | DetectionEvent.kt:191 (×10) | 5 ficheros | backlog |

\* GEOCODE-DEADLINE-001 aparece con test en `AddressAndPlaceRepositoryImplTest`/`HomeGeocodingControllerTest`;
la instancia androidMain concreta (deadline del geocoder nativo) no tiene test propio — cuenta como cubierto
en las cifras globales.

### ROUTE-* (ruta del viaje)

| Tag | Producción (1ª de N) | Tests | Escenario |
|---|---|---|---|
| ✅ ROUTE-START-AT-CAR-001 | ConfirmParkingUseCase.kt:224 | ConfirmParkingUseCaseTest | field 17/18-08 Chema→Balsa (`12b1969a`) · backlog |
| ✅ ROUTE-END-AT-CAR-001 | ConfirmParkingUseCase.kt:231 (×5) | DrivingRouteTest, ConfirmParkingUseCaseTest | backlog |
| ✅ ROUTE-FIX-ACCURACY-001 | domain/detection/DrivingRoute.kt:20 (×2) | DrivingRouteTest, TrailMapMatcherTest | field 2026-08-14 (`214850cf`, σ por medición) · backlog |
| ✅ ROUTE-GAP-HONEST-001 | ConfirmParkingUseCase.kt:396 (×6) | TrailMapMatcherTest, ConfirmParkingUseCaseTest | field 2026-08-14 (`a244d975`, agujero honesto) · backlog |
| ✅ ROUTE-QUALITY-001 | ConfirmParkingUseCase.kt:218 (×6) | TrailMapMatcherTest, ConfirmParkingUseCaseTest | backlog |
| ✅ ROUTE-SNAP-001 | location/OverpassRoadNetworkDataSourceImpl.kt:18 (×2) | HomeTripControllerTest | — |
| ✅ DET-ROUTE-TRACK-001 | ConfirmParkingUseCase.kt:69 (×11) | 3 ficheros | backlog |
| ✅ DET-ROUTE-SNAP-STORE-001 | detection/worker/EnrichParkingSessionWorker.kt:32 (×2) | ParkingSessionMapperTest | backlog |
| 🔴 ROUTE-PASSIVE-FILL-001 | (ver androidMain arriba) | — | |

### Inversos — tags que viven SOLO en tests (sin tag en producción del subsistema)

`DET-UNVERIFIED-ARM-DRIVE-PROOF-001` (CoordinatorParkingDetectorTest, con fixture del field
2026-08-15 Redmi — el código del fix está en master `e9186a52` pero sin comentario-tag en
producción), `DET-B-03`, `DET-D-04`. No inflan ni deflactan el % (no cuentan como tags de producción).

## 4 · Clusters 🔴 sin fijar (43 tags)

1. **Capa de servicio Android** (11 tags): DET-INTAKE-001, DET-B-01, DET-ENDED-VETO-RACE-001,
   DETECT-SERVICE-RACE-001, DET-RESIDENT-FGS-001, BUG-FGS-001/001a/100, BUG-SERVICE-109,
   DET-EXACT-HEARTBEAT-001, DET-WATCH-HONEST (parcial). El intake serializado y las razas de
   ciclo de vida — precisamente lo que Doze/OEM estresa — solo tienen 1 test Robolectric.
2. **Workers + persistencia entre despertares** (8): ANCHOR-PERSIST-001, DET-ARRIVAL-HANDOFF-001,
   DET-ARRIVAL-DOUBLE-PIN-001, BUG-WORKER-001/002, SESSION-RESTORE-001, GEOF-001/RESTORE-001,
   DET-EXIT-WITNESS-001. El veredicto (EvaluateSafetyNetCheckUseCase, 49 tests) está fijado;
   la orquestación del worker que lo invoca, no.
3. **Estrategia Bluetooth androidMain completa** (3): DET-BT-TIMEOUT-SAVE-001,
   DET-RETURN-ANCHOR-001, BUG-FGS-001. `BluetoothParkingDetector` no tiene NINGÚN test — solo
   sus veredictos de dominio (EvaluateBtPark/BtArbitration).
4. **Sensores/AR** (5): DET-G-01, DET-AR-FIRST-001b, DET-SIGMOTION-001,
   DET-STEP-SENSOR-REDMI-001, ROUTE-PASSIVE-FILL-001.
5. **Guards de dominio testeables HOY sin test** (12): LOC-001, LOC-002, BUG-COORD-112/115,
   BUG-DETECT-310502, BUG-GARAGE-COLA-001, BUG-SCOOTER-001, DET-D-01/D-02, DET-G-01b,
   DET-READY-001b/001c, DET-LOG-03, DET-SESSION-RELIABILITY-STAMP-001, REFACTOR-200/300,
   VEHICLE-SYNC-001 — viven en commonMain puro, el harness existe, y nadie los fija.

> 🔄 **Corrección al cluster 5 (24-08).** Este era el punto más alarmante del doc y **estaba
> sobredimensionado**. Al comprobar los 23 tags de dominio sin test uno a uno: **los 23 viven en
> ficheros que sí tienen tests, cero huérfanos**. No hay ni un fichero de dominio del subsistema sin
> cobertura. Ejemplos de lo que había detrás de la alarma:
>
> - `DEPART-CONSISTENCY-001` → `ObserveDetectionReadinessUseCase`, con 2 ficheros de test.
> - `DET-CAR-REST-CLOCK-001` → `EvaluateUnattendedParkingSaveUseCase`, con 2 ficheros de test.
> - `DET-ARRIVAL-HANDOFF-001` → `ArrivalHandoffDetection.kt` es **una interfaz-puerto de un método
>   sin lógica**. No hay nada que testear; el tag inflaba la cuenta sin representar riesgo.
> - `BUG-COORD-112/115`, `BUG-SERVICE-109`, `BUG-DETECT-310502`, `REFACTOR-200` → IDs heredados de
>   trabajo ya cerrado que siguen citados en comentarios del coordinator.
>
> **Lo que sí queda en pie de los clusters 1-4**: la capa Android. Ahí el 22.5 % es real y refleja
> código realmente sin fijar — y es precisamente la mitad que el refactor NO toca.

## 5 · Trazas listas para characterization tests

- **En el repo, listas y en uso**: las **13** `Trace_*.kt` (§1) — patrón replicable (TraceEvent
  FIX/STEP + reloj del replayer). `Trace_CameliasHop001` demuestra el patrón *characterization*
  explícito ("pinned SILENCE de hoy" antes del fix).
- ⛔ **Regla de admisión (24-08)**: toda traza nueva se valida **neutralizando el guard que dice
  fijar** y comprobando que se pone roja. Una traza que pasaría igual con el fix revertido no vale
  nada, y el comentario que afirma más de lo que demuestra es un bug con forma de test verde. Las
  dos del 22-08 se admitieron así; una tercera afirmación se **retiró** por no discriminar (queda
  escrita esa limitación dentro del propio test).
- **Inline en unit tests**: field 2026-08-15 (Redmi late-armed hop) en CoordinatorParkingDetectorTest.
- **Fuera del repo, convertibles**: dos fuentes, no una.
  1. `diagnostics/{uid}/sessions` en Firestore (pap-26) — de ahí salieron las 10 primeras
     ("generated 1:1 from the 638/669/279 diagnostics events").
  2. **`parkdiag.log` del móvil** — de ahí salieron las 3 del 22-08, y para una de ellas era la
     ÚNICA fuente. Ventana finita (rota una vez); si un viaje interesa, **sacar el log antes de que
     rote**, no cuando toque escribir la traza.

  Candidatas pendientes (según memoria del proyecto, NO VERIFICADO en esta pasada): FN Oppo
  17/18-08 (EXIT entregado 11 h tarde), FP paseo→pin y FN 25 min del 16-08. ~~viaje del hueco GPS
  de DET-GAP-ANCHOR-ZONE-001~~ → **cubierto**: `Trace_GondolaCamelias001` lleva un hueco real de
  100,5 s y fija `anchor_gap_entered`.
- **No existe ningún fixture json/csv de campo en el repo** (verificado por barrido de extensiones).
