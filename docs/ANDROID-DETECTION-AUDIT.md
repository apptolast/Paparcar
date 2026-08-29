# Auditoría Android — Sistema de detección de aparcamiento

**Fecha:** 2026-08-11 · **Alcance:** todos los componentes que participan en la detección
(services, workers, receivers, alarms, sensores, geofences, BT, permisos, persistencia,
núcleo commonMain) + doc drift. **Método:** lectura exhaustiva de código con verificación
file:line; el código manda sobre docs.

**Propósito doble:** (a) dejar Android más limpio; (b) fijar la frontera común/plataforma
antes de abrir el frente iOS. Ver `docs/IOS-IMPLEMENTATION-PLAN.md` para la Fase 2.

---

## 0. Resumen ejecutivo

- **commonMain está limpio: CERO fugas de plataforma.** Verificado import a import
  (grep `android.*`, `com.google.*`, `java.*`, `platform.*` → 0 hits reales; los ~29
  imports `androidx.*` no-Compose son artefactos KMP legítimos: Room, sqlite, lifecycle,
  navigation, savedstate). No hay remediación de pureza pendiente. §5.
- **El núcleo de decisión ya es portable:** 10 `Evaluate*UseCase` puros + coordinator de
  2480 líneas con reloj inyectable + harness de replay con 9 trazas de campo reales y 12
  tests de regresión. La detección **no usa expect/actual**: todo son puertos Koin —
  patrón correcto, mantener.
- **El gap iOS real es UNO:** no existe orquestador (equivalente de
  `CoordinatorDetectionService`). Los evaluadores están compartidos; nadie los alimenta
  en iOS (`IosManualParkingDetectionImpl` es el único stub no-op restante).
- **El modo replay YA EXISTE** (`DetectionTraceReplayer` + `tools/trace2fixture`), pero es
  test-only (commonTest). Promoverlo a commonMain como vía de ingesta es la tarea F0 clave.
- Deuda principal en Android: `ParkingSafetyNetWorker` (859 líneas, 6 responsabilidades,
  SharedPreferences como base de datos compartida entre 3 componentes), asimetría de
  hardening entre los dos services, `KMH_PER_MPS` declarado 8 veces, y un lote grande de
  comentarios/docs que contradicen el código (§6) — uno de ellos peligroso.

---

## 1. Services

Solo existen **dos** `Service` en todo el repo (ambos `LifecycleService`, androidMain).

### 1.1 `CoordinatorDetectionService` — **LIMPIAR**

`androidMain/.../detection/service/CoordinatorDetectionService.kt` (1436 líneas),
`foregroundServiceType="location"`, `exported=false` (¡`true` en el manifest de debug!).

Lo que está BIEN (no tocar):
- **Intake serializado [DET-INTAKE-001]**: un solo `Channel(UNLIMITED)`, un consumidor,
  una decisión de teardown por comando (`resolveIdleEpilogue`), `DetectionEnded` enviado
  desde `invokeOnCompletion` (no desde el `finally` — fix DET-ENDED-VETO-RACE-001), guard
  de identidad `detectionJob === startedJob`. Diseño maduro nacido de bugs de campo.
- **Modelo híbrido trip-scoped ACTIVE + residente SENTRY** (F1–F3 de DET-RESIDENT-FGS-001
  completas): `resolvePostDetectionLifecycle` puro decide residencia (toggle ∧ sesión
  aparcada ∧ strategy==COORDINATOR); notificación MIN silenciosa en SENTRY; ledger de
  kills (`SentryResidenceStore` + `resolveSentryKillVerdict` puro).
- **Gate único de armado** `coordinatorMayArm(strategy, trigger)` en
  `startParkingDetection` [DET-STRATEGY-GATE-001] + short-circuit temprano en el carril
  EXIT. El service solo hace I/O + side-effects; toda decisión delega en use cases puros.

Deuda concreta:
| # | Problema | Ref |
|---|---|---|
| C1 | 1436 líneas con responsabilidades mezcladas: `handleGeofenceExit` 235 líneas, `handleArTransition` 142. Extraer handlers a colaboradores (el patrón puro ya existe; es solo orquestación Android) | `:399-633`, `:647-788` |
| C2 | Tipos fully-qualified inline en vez de imports (≥10 sitios) — inconsistente con el bloque de imports de 70 líneas | `:98,:531,:648-655,:1148...` |
| C3 | `KMH_PER_MPS = 3.6f` local (+ un `* 3.6f` crudo en `:858`) — ver deuda transversal T1 | `:1387` |
| C4 | `updateCrashlyticsContext` lee `coordinator.hasDetectedMovement`, campo declarado no fiable cross-session por BUG-SERVICE-109 | `:1322` vs `:317-319` |
| C5 | Multi-vehículo: un EXIT con dos coches saliendo arma solo `.first()` (orden arbitrario); `handleSentryWake` cae a `firstOrNull()` silencioso | `:561`, `:298` |
| C6 | `onTaskRemoved` no está sobrescrito (con `START_STICKY`, el swipe-away queda a merced del OEM, sin test) | — |
| C7 | Strings de debug en español hardcodeadas (gated por `BuildConfig.DEBUG`, pero literales largas inline) | `:447,:492,:986...` |
| C8 | Un solo test unitario (sticky-restart null-intent). Cero cobertura del intake, epilogue, `enterSentry` | `CoordinatorDetectionServiceTest.kt` |
| C9 | El manifest de debug exporta el service con protección "comment-only" ("NEVER ship this") — sin lint/CI que lo garantice | `src/debug/AndroidManifest.xml:10-13` |

### 1.2 `BluetoothDetectionService` — **LIMPIAR** (3 fixes de prioridad alta)

`androidMain/.../bluetooth/BluetoothDetectionService.kt` (219 líneas), `START_NOT_STICKY`
(correcto: estado in-memory no sobrevive kill), notificación id **1003**.

| # | Problema | Severidad |
|---|---|---|
| B1 | `withLocationPermission = true` **hardcodeado** en el promote (`:119`): con localización revocada en Android 14+ puede lanzar `SecurityException` — exactamente la clase BUG-FGS-001a que el coordinator SÍ guarda (`hasRequiredPermissions()`). Asimetría de hardening | **Alta** |
| B2 | **El reaper de FGS fantasma solo cubre la notificación 1001**: una sesión BT congelada deja la 1003 pegada para siempre (el service BT no arma `PendingDetectionStore`, así que `hasStalePending` nunca puede activarse para él) | **Alta** |
| B3 | Teardown sin yielding: `stopForegroundAndSelf()` sin `startId` en 4 sitios. La justificación ("start commands cannot race") es dudosa: un DISCONNECT llegando mientras se procesa un CONNECT (la oscilación BT es la razón de existir del debounce de 30 s) tiene la forma exacta del bug que DET-INTAKE-001 arregló en el coordinator | **Alta** |
| B4 | Constantes a medias: `BT_DISCONNECT_DEBOUNCE_MS`/`GPS_SAMPLE_TIMEOUT_MS` hardcodeadas en el detector mientras sus hermanas (`btWalkAwayTimeoutMs`...) viven en `ParkingDetectionConfig` con `require()`. + líneas en blanco fósiles de constantes borradas | Media |

### 1.3 `ForegroundServiceController` — **MANTENER**

94 líneas, único lugar con `startForeground`/`stopSelf`. Dos overloads de teardown
(incondicional para BT, yielding con `stopSelfResult(startId)` para el coordinator) —
correcto; B3 pide que BT migre al yielding, no que cambie el controller.

---

## 2. Workers (10) + AlarmManager

Todos `CoroutineWorker` + `KoinComponent` en `androidMain/.../detection/worker/`. No hay
`WorkerFactory`/`Configuration.Provider` (factory reflexiva + Koin global). Tres
adaptadores `WorkManager*Scheduler` implementan los puertos commonMain (única frontera
código común → WorkManager). **Ningún worker muerto.**

| Worker | Veredicto | Diagnóstico |
|---|---|---|
| **ParkingSafetyNetWorker** (859 líneas, 15-min periodic + `enqueueCheckNow` desde 11 call sites) | **MODIFICAR** | Hace 6 trabajos distintos: telemetría de kills, force-stop detection, reaper FGS, nudge de pendings, espejo sig-motion/alarma exacta, y la evaluación de red de seguridad (la única con use case puro). Dividir. SharedPreferences `parking_safety_net` usada como BD compartida por 3 componentes (service escribe, worker lee, backfill lee) con serialización pipe a mano y un **hazard documentado de colisión de prefijos** `anchor_*` (el pruning depende del ORDEN de los checks, `:743`). Extraer a un store tipado. Magic numbers fuera de config (`INTERVAL_MINUTES`, `PROMPT_THROTTLE_MS` 6 h, `KILL_GAP_THRESHOLD_MS` 3 h). Estado mutable estático en companion (`curedFencesThisProcess`) intesteable. Sin retry (todo `Result.success()` — tick perdido = perdido hasta el siguiente; aceptable por diseño de red, pero no está documentado como decisión). Es **trabajo time-critical en scheduler diferible** — evidencia: existe `ExactHeartbeatScheduler` precisamente porque el periodic es Doze-batched |
| **DepartureDetectionWorker** | **LIMPIAR** | Correctamente delgado (parse → `RunDepartureCheckUseCase` puro). Dos agujeros: (1) `ProcessFailedRetry` **sin tope** de reintentos (hasta el límite interno de WM); (2) tres productores encolan sobre el MISMO unique name con `REPLACE` — un dispatch de la safety net puede cancelar silenciosamente un check en vuelo del coordinator para la misma geocerca. `INITIAL_BACKOFF_SECONDS=15` acoplado por comentario (no por código) a `MAX_INCONCLUSIVE_ATTEMPTS=3` en otro módulo |
| **ParkingBackfillWorker** | **LIMPIAR** | Lee las prefs de OTRO worker (acoplamiento cross-worker con constantes `internal`); defaults mágicos `50f`/`0.5f` que sombrean `config.reliabilityUnattendedSave`; parsing `"lat,lon"` a mano. La lógica (guards `isRunning` + `EvaluateBackfillDeferralUseCase`) está bien |
| **GeofenceJanitorWorker** | **LIMPIAR** | Único worker que salta la capa repo e inyecta `AppDatabase` crudo. Su KDoc y el comentario del caller en `PaparcarApp` se **contradicen** (NEVER_EXPIRE + restauración vs "TTL 24 h que el janitor renueva" — el segundo es falso desde SESSION-ISOLATION-001). Duplica el re-registro de geocercas con el carril `CureGeofence` de la safety net con throttling distinto — unificar en una única política invocable |
| **RegisterActivityTransitionsWorker** | **MANTENER** | Uso de libro de WorkManager. Nit: en Boot/MainActivity el mismo side-effect se dispara dos veces (directo + worker) |
| **FirstParkNudgeWorker** | **MANTENER** | El worker más limpio del repo |
| **EnrichParkingSessionWorker** | **LIMPIAR** | Dos concerns independientes (geocode, route-snap) comparten UN presupuesto de retry y un resultado: una sesión sin ruta quema 3 intentos y acaba `failure` si el geocoder no da nada. `ROADS_BBOX_MARGIN_DEG` duplicado con el fetch live |
| **ReportSpotWorker** | **MANTENER** | El mejor diseñado (gate TTL-in-queue honesto, autocontenido, red constraint). Nit → T2 |
| **SaveNewParkingSessionWorker** | **LIMPIAR** | Serializer Data↔DTO a mano (→ T2); TAG de log (`PARKDIAG/...`) usado también como tag de WorkManager; literal de cadena `parking_chain_$id` duplicado sin constante |
| **ClearActiveParkingSessionWorker** | **UNIFICAR** | NO está en la cadena `parking_chain_<sessionId>` (a diferencia de save y update-address) → un clear puede correr en paralelo con el save de la misma sesión en dos colas unique independientes. Además duplica el flip de `clearParkingSessionActiveFlag` que `SaveNewParkingSessionWorker` ya hace para la sesión previa. Meterlo en la cadena |
| **UpdateParkingSessionAddressAndPlaceWorker** | **LIMPIAR** | Race conocida y reconocida en comentario con el save worker (degradada de error a warning en vez de resuelta); `AddressDto` aquí pierde `countryCode` que `ReportSpotWorker` sí lleva |

**`ExactHeartbeatScheduler` + `ExactHeartbeatReceiver` — MODIFICAR.** La alarma exacta de
5 min [DET-EXACT-HEARTBEAT-001] está bien diseñada (trigger, no segundo cerebro; sync en
un solo sitio; métrica de Doze-stretch persistida). PERO: con `targetSdk=36`,
`SCHEDULE_EXACT_ALARM` **no es auto-granted** (los comentarios del manifest y del
scheduler dicen "auto-granted hasta targetSdk 32" — falso para esta app) y **no existe
ninguna UI** que lleve al usuario a `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`. En campo el
heartbeat "exacto" corre casi siempre en el fallback inexacto. Decidir: añadir la petición
(fila opcional en Permisos, con la declaración de policy de Play que conlleva) o asumir el
modo inexacto y borrar la vía exacta. **DECISIÓN pendiente del usuario.**

Transversal WorkManager:
- **T1 — `KMH_PER_MPS = 3.6f` declarado 8 veces** (BluetoothParkingDetector,
  CoordinatorDetectionService, ParkingSafetyNetWorker, EvaluateBtParkUseCase,
  DetectParkingDepartureUseCase, EvaluateParkingDecisionUseCase,
  EvaluateSafetyNetCheckUseCase, RunDepartureCheckUseCase). Regresión de manual de
  FND-002. Una constante en commonMain.
- **T2 — Tres serializers Data↔DTO casi idénticos con nombres de key DISTINTOS**
  (`address_street` vs `addr_street`) en ReportSpot / SaveNewParkingSession /
  UpdateParkingSessionAddressAndPlace. Un helper compartido.
- **T3 — Constraints casi ausentes**: solo los 4 workers Firestore ponen
  `NetworkType.CONNECTED`. Cero usos de `setRequiresBatteryNotLow` etc. en todo el repo.
  Para el periodic de 15 min que toma un fix GPS activo, es un riesgo deliberado no
  declarado — documentar o corregir.
- **T4 —** `LEGACY_TAG = "DetectionHeartbeatWorker"` se cancela en cada `enqueueKeep`:
  shim de migración sin fecha de borrado. **DESCARTAR** cuando la base instalada rote.
- Tests: un solo fichero de worker tests (con nombre que no coincide con su clase) cubre
  los 3 workers de sync. Workers 1–8 solo probados vía sus use cases puros.

---

## 3. Receivers, PendingIntents y Manifest

### 3.1 Receivers (7 estáticos + 1 dinámico) — inventario y veredictos

| Receiver | Registro | Exported | Veredicto | Notas |
|---|---|---|---|---|
| `ActivityTransitionReceiver` (carril evidencia AR) | manifest, sin filter (PI explícito) | `true` + permiso GMS AR | **MANTENER** | Koin `by inject()` síncrono en main thread dentro de `onReceive` (grafo Room/DataStore) — vigilar; hoy solo hace enqueues |
| `BootCompletedReceiver` | manifest, BOOT + MY_PACKAGE_REPLACED | `true` sin guard | **MANTENER** | Broadcasts protegidos → seguro; único exported sin `android:permission` (estilo). Doobla como hook post-force-stop en Android 15+ (BOOT sintético) |
| `BluetoothConnectionReceiver` | manifest, ACL_CONNECTED/DISCONNECTED | `true` + BLUETOOTH_CONNECT | **LIMPIAR** | El KDoc dice `exported=false` — **contradicción peligrosa**, ver drift D1. `goAsync()` + scope por delivery correcto |
| `GeofenceEnterReceiver` (twin ENTER) | manifest, PI explícito | `false` | **MANTENER** | Solo `enqueueCheckNow(SOURCE_GEOFENCE_ENTER)` — contrato "nunca arma" respetado |
| `GeofenceExitWitnessReceiver` | manifest, PI explícito | `false` | **MANTENER** | Log puro por contrato [DET-EXIT-WITNESS-001] |
| `ParkingConfirmationReceiver` | manifest + filter de 4 acciones | `false` | **LIMPIAR** | Rutea 5 acciones pero el filter lista 4 (falta `ACTION_DEPARTURE_CONFIRMED`); funciona solo porque el PI es explícito. Sincronizar (o quitar el filter, que es innecesario con PI explícito) |
| `ExactHeartbeatReceiver` | manifest, PI explícito | `false` | **MANTENER** | |
| `gpsToggleReceiver` (anónimo en MainActivity) | dinámico onStart/onStop, `RECEIVER_NOT_EXPORTED` | — | **MANTENER** | Único registerReceiver del repo |

**`GeofenceBroadcastReceiver` NO EXISTE** — sigue descrito como vivo en 6 documentos
(drift D6). Su rol se partió en witness (log) + carril `getForegroundService`.

PendingIntents (14 sitios inventariados): simetría de carril doble AR
(getBroadcast evidencia RC 102 + getForegroundService decisión RC 103 [DET-AR-FIRST-001],
con throttle de re-registro de 30 min por la re-entrega stale de GMS) y geofence
(getForegroundService EXIT RC 9100 + getBroadcast ENTER 9101 + witness 9102). `FLAG_MUTABLE`
es load-bearing en los PI de GMS (BUG-GEOFENCE-001).

### 3.2 Permisos del manifest — veredictos

| Permiso | Veredicto | Justificación |
|---|---|---|
| FINE / BACKGROUND_LOCATION, FOREGROUND_SERVICE(+_LOCATION), ACTIVITY_RECOGNITION, POST_NOTIFICATIONS, INTERNET, RECEIVE_BOOT_COMPLETED, BLUETOOTH_CONNECT, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | **MANTENER** | Todos con uso verificado file:line |
| `ACCESS_COARSE_LOCATION` | **MODIFICAR** | Declarado pero **nunca pedido en runtime**: `step1Permissions = arrayOf(FINE)` solo. En API 31+ el diálogo ofrece "Aproximada"; si el usuario la elige, FINE se deniega y la app se lee como BlockedCore sin fallback. Añadir COARSE al array de la petición (guía oficial: pedir ambas juntas) |
| `FOREGROUND_SERVICE_DATA_SYNC` | **DESCARTAR** | Muerto: ningún service declara `dataSync`; el `SystemForegroundService` de WM llega sin type; el único expedited usa `RUN_AS_NON_EXPEDITED`. Solo añade carga de declaración en Play |
| `SCHEDULE_EXACT_ALARM` | **MODIFICAR** | Ver §2 (comentarios falsos con targetSdk 36 + sin UI de petición) |
| `USE_BIOMETRIC` + `USE_FINGERPRINT` (transitivos, BaseLogin/credentials) | **DESCARTAR** | Sin código propio que los use; `tools:node="remove"` para mantener honesto el listado de la store |
| Legacy `BLUETOOTH` (maxSdk 30) | **AGREGAR (verificar)** | minSdk=26 y solo se declara BLUETOOTH_CONNECT (S+): en API 26–30 `bondedDevices` necesita el permiso normal `BLUETOOTH`. Verificar en device API<31 antes de dar por bug |
| `<uses-feature bluetooth/gps required="false">` | **AGREGAR** | Sin uses-feature declarados, los permisos implican hardware **required** → excluye de Play a dispositivos sin BT, siendo BT una estrategia opcional |
| `allowBackup="true"` sin `dataExtractionRules` | **MODIFICAR (decisión)** | Restaurar prefs de detección (anclas, seals, BtConnectionStore) y Room en OTRO dispositivo es un vector plausible de corrupción de estado de detección |

### 3.3 Superficie exportada resultante

App-owned tras merge: `MainActivity`, `ActivityTransitionReceiver` (guarded),
`BootCompletedReceiver` (unguarded, protegido por broadcast), `BluetoothConnectionReceiver`
(guarded), **+ `CoordinatorDetectionService` exported SOLO en debug**. Librerías añaden
receivers DUMP-guarded y actividades de Firebase Auth con deep links BROWSABLE (superficie
conocida, aceptable).

---

## 4. Geofencing y Bluetooth

### 4.1 Modelo de TRES geocercas por sesión — **MANTENER** (documentarlo: drift D7)

Cada sesión aparcada registra 3 fences GMS sobre el mismo círculo:
`<sessionId>` (EXIT → `getForegroundService` RC 9100, la real), `enter_<id>` (ENTER →
receiver, cure/re-seal [DET-RETURN-ANCHOR-001]), `witness_<id>` (EXIT → log puro). Las
auxiliares son best-effort (no deciden el `Result`). `NEVER_EXPIRE`, `NO_INITIAL_TRIGGER`,
radio adaptativo `geofenceRadiusFor(size, accuracy)` (60–120 m base + accuracy×1.5, cap
200 m), `FLAG_MUTABLE` obligatorio. Solo para el coche propio; nunca para spots.

Deuda:
| # | Problema | Veredicto |
|---|---|---|
| G1 | `setLoiteringDelay(60 s)` en fences EXIT-only: **no-op** (solo aplica a DWELL). El const parece un knob y no lo es | **DESCARTAR** (borrar llamada+const) |
| G2 | `GeofenceEventBus`/`GeofenceEvent.Exited` **muertos en Android**: se emite en `:465` y nadie colecta — `Channel(UNLIMITED)` acumulando objetos sin consumidor (leak pequeño pero unbounded). En iOS el MISMO bus es el canal real de entrega | **MODIFICAR** (platform-aware: consumir o no emitir en Android; conservar el puerto para iOS) |
| G3 | Sin manejo del **límite de 100 geocercas GMS** ni de `GEOFENCE_TOO_MANY_GEOFENCES` en ningún sitio; a 3 fences/sesión el techo son ~33 sesiones; huérfanas de removals fallidos solo se reclaman si DISPARAN | **AGREGAR** (guard + reclamo proactivo en janitor) |
| G4 | Prefijos `"enter_"`/`"witness_"` concatenados en 5 sitios sin helper round-trip; request codes 9100-9102 sin cross-ref | **LIMPIAR** |
| G5 | El plan de recuperación si `getForegroundService`-geofencing regresa en un OEM es un comentario (el fallback getBroadcast se borró tras validar en device) | Documentar como riesgo aceptado |

**FND-004 — estado real: CERRADO desde 2026-03-31 (commit `8dea3420`) y su premisa fue
posteriormente REVERTIDA a conciencia.** El fix original ("un geofence exit basta tras N
Inconclusive") fue sustituido por el gate de admisibilidad `admissibleBoarding` en
`RunDepartureCheckUseCase:100-107` [BUG-WALK-DEPART-001][DET-SESSION-BIRTH-001]. No hay
ticket FND-004 abierto; las referencias en CLAUDE.md son ejemplos de estilo de commit.
La respuesta moderna a "la plaza no se publica al desaparcar" es un subsistema: split
boundary-vs-stale (`EvaluateGeofenceExitUseCase`), worker con gate de velocidad +
independencia del echo del EXIT (`departureProofMinGapMs` 20 s, DET-DEPART-PROOF-001),
freshness `spotPublishMaxAgeMs` 10 min, y la reconciliación de la safety net.
**PARKING-DETECTION.md §1.7 sigue documentando el comportamiento pre-fix (drift D8).**

### 4.2 Bluetooth — **MANTENER el diseño; LIMPIAR bordes**

- **Todo es Bluetooth Classic/ACL**: receiver de manifest sobre broadcasts del stack BT
  (uid `bluetooth` — por eso `exported="true"` es imprescindible,
  DET-BT-RECEIVER-EXPORT-001: antes del 2026-08-06 la ruta BT **nunca se había ejecutado
  en ningún build**), `bondedDevices` para el pairing UI, cero BLE/GATT/scan. Dato
  crítico para iOS: nada de esto existe allí.
- El **árbitro** es `EvaluateBtArbitrationUseCase` (puro, 12 tests, tabla de verdad
  completa incl. `YieldToConnectedCar` de DET-BT-WRONG-CAR-ABORT-001, que SÍ está en
  master pese a lo que dice su ticket — drift D5). BT nunca puntúa: **supersede**.
- `ParkingStrategyResolver.strategyFor`: BLUETOOTH solo con **conexión ACL viva** a un
  coche emparejado (no mero pairing) [DET-BT-CONNECTED-NOT-PAIRED-001], vía
  `BtConnectionStore` (prefs mantenidas por el receiver — dependencia dura; si el
  receiver enmudece, la estrategia se resuelve mal en silencio).
- Nota: la firma `resolveStrategy(vehicle, isBluetoothEnabled)` que citan CLAUDE.md y
  este prompt no existe; la API real es `resolve()` / `strategyFor(vehicles)`.

Deuda BT:
| # | Problema | Veredicto |
|---|---|---|
| BT1 | Dos gates independientes de "el usuario se alejó andando" con umbrales distintos: BT 30 m + maxPedestrianSpeed vs coordinator 18 m + pasos. Misma pregunta física, dos respuestas sin cross-ref | **LIMPIAR** (documentar la razón o unificar constantes en config) |
| BT2 | Un device desemparejado FUERA de la app deja `Vehicle.bluetoothDeviceId` apuntando a una MAC que ya no puede disparar, sin reconciliación | **AGREGAR** (chequeo en salud de detección) |
| BT3 | `BluetoothDeviceType` es decorativo; nada filtra relojes/auriculares al emparejar — la protección real es solo el early-return de MAC desconocida | Aceptado; documentar |
| BT4 | **Viajes BT-owned no graban ruta**: `DrivingRouteStore.append` solo se alimenta desde el coordinator; `BluetoothParkingDetector` nunca lo toca → parks BT con ruta vacía. Gap sin ticket | **AGREGAR** ticket |

---

## 5. Núcleo commonMain — **MANTENER** (cero remediación de pureza)

### 5.1 Tabla de fugas — resultado: VACÍA

Grep exhaustivo sobre commonMain y commonTest: **0 imports** de `android.*`,
`com.google.*`, `java.*`/`javax.*`, `platform.*`, `kotlinx.coroutines.android`. Los 4
hits textuales de `Context`/`PendingIntent`/`SharedPreferences` son comentarios KDoc.
Los imports `androidx.*` son todos artefactos KMP (Room KMP, sqlite KMP, lifecycle KMP,
Navigation Compose MP, savedstate KMP, Compose MP). El idioma "pure decision core +
platform executes" está declarado y cumplido en los KDoc de los evaluadores.

Componentes verificados individualmente puros: `CoordinatorParkingDetector` (2480 líneas,
reloj inyectable), `ConfirmationPhase` (máquina Idle→LowReached→Notified→Candidate),
`EvaluateParkingDecisionUseCase` (primitivas dentro, sealed fuera — replayable),
`EvaluateArEnterArmUseCase` (escalera AR), `EvaluateGeofenceExitUseCase` (armado EXIT,
extraído del service para iOS [AUDIT-A9-KMP-001]), `EvaluateSafetyNetCheckUseCase`,
`EvaluateHonestCloseUseCase`, `EvaluateBtArbitrationUseCase`, `EvaluateBtParkUseCase`,
`EvaluateBackfillDeferralUseCase`, `EvaluateDetectionReliabilityUseCase`, y el ancla de
egreso completa (campos puros de `ParkingDetectionState`).

### 5.2 Frontera de puertos (lo que iOS hereda)

- **La detección usa CERO expect/actual** — todo puertos Koin (los 15 expect/actual del
  repo son utilidades UI/plataforma). Patrón correcto: los puertos se testean con fakes.
- 26 puertos; 20 con impl iOS real. **Gaps iOS**: `StepCounterSource`,
  `DetectionStepAnchors`, `TripTrail`, `DrivingRouteStore`, `RoadNetworkDataSource`,
  `UiLocationLogger` (4 de ellos `getOrNull()`-bound, degradan con gracia) + el único
  stub no-op real: `IosManualParkingDetectionImpl`.
- **No hay orquestador iOS** — el equivalente de `CoordinatorDetectionService` + workers
  + receivers. Ese es el port entero, y es la Fase 1 del plan iOS.

### 5.3 Replay/batch — EXISTE, test-only — **MOVER A COMMON (production-grade)**

`commonTest/.../coordinator/replay/DetectionTraceReplayer.kt` (54 líneas: `TraceEvent`
FIX/STEP + reloj virtual cableado al `clock` del detector) + 9 trazas de campo (~112 KB)
+ 12 tests de regresión + pipeline `Firestore diagnostics → tools/trace2fixture.py →
Trace_*.kt`. Replaya contra el detector REAL. Doctrina: "every field bug becomes a
permanent fixture".

**Es exactamente la forma de la ingesta wake-and-query de iOS.** Tarea F0: promover
`TraceEvent` + replayer de commonTest a commonMain como puerto de ingesta batch
(manteniendo commonTest como consumidor). Beneficia a Android aunque el port se retrase
(fixtures de regresión ya lo usan; el habilitador queda formalizado).

### 5.4 Persistencia de sesión — deliberadamente partida (documentado y correcto)

- El estado del coordinator es **in-memory por diseño** [DET-B-02]: "Missing one park is
  a zero-cost false negative; a hung notification is not". El sticky-restart con intent
  nulo para SIN promover.
- La durabilidad vive en side-records: `pending_*` (armado + heartbeat,
  `PendingDetectionStore`), `sentry_residence`, `anchor_*`/`anchor_steps_*`/
  `anchor_seal_pos_*`/`anchor_seal_at_*` (anclas + seals con origen y momento),
  `exit_delivered_*`, `arrival_resolution_*`, `trip_trail` (60 pts/12 h),
  `driving_route` (~500 pts), nudge en DataStore (persist-FIRST antes de notificar), MAC
  en `bt_identity`, y la sesión en Room. Motor de recuperación: `ParkingSafetyNetWorker`
  + `BootCompletedReceiver`.
- **Nota para iOS**: estos stores son androidMain sin puerto (SharedPreferences directo).
  El plan iOS necesita decidir cuáles se portifican (ver plan, F2).

### 5.5 Restos del scorer — **DESCARTAR (D-03c)**

`CalculateParkingConfidenceUseCase` conserva scaffolding inerte: ramas `activityStill`
(siempre false desde DET-D-03), y la rama `vehicleExit+window+egress` del decisor es
estructuralmente inalcanzable end-to-end (documentado en DET-SOLID-001). Ejecutar el
rework D-03c (scorer→metadato) o borrar las ramas muertas con sus tests.

---

## 6. Permisos, tiers y notificaciones (resumen de veredictos)

El modelo (CORE/PRODUCER + `DetectionReadiness` + `DetectionReliabilityLevel` ⊥
`DetectionTier` ⊥ `ParkingStrategy`) es sólido y está bien separado. Deuda puntual:

| # | Problema | Veredicto |
|---|---|---|
| P1 | F1 COARSE en el array de petición (ver §3.2) | **MODIFICAR** |
| P2 | `startActivity(ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)` sin guard `resolveActivity`/try-catch → `ActivityNotFoundException` posible en ROMs raras (el OEM manager SÍ guarda los suyos) | **LIMPIAR** |
| P3 | Toggles de Settings `notifyParkingDetected`/`notifySpotFreed` **muertos**: persistidos y visibles pero ningún poster los consulta — toda notificación se publica incondicionalmente | **MODIFICAR** (honrarlos) o **DESCARTAR** (quitarlos de Settings). DECISIÓN |
| P4 | `showParkingConfirmation(score)` ignora `score` (documentado como "for display") | **LIMPIAR** (quitar el parámetro o usarlo) |
| P5 | iOS nunca puebla `isBatteryOptimizationExempt`/`hasBluetoothConnectPermission` → tier siempre ASSISTED con un issue de "batería" que en iOS no significa nada; la tier card no está gateada por plataforma | **MODIFICAR** — bloqueante conceptual para iOS, ver plan §tiers |
| P6 | `PermissionManagerImpl` es pull-only (refresh en RESUME); sin observer — aceptado, documentar | — |
| P7 | KDoc drift: `canContinueWithCore` menciona notificaciones (movidas a PRODUCER); id 2002 documentado como canal DETECTION/LOW siendo ACTION/HIGH | **LIMPIAR** |
| P8 | `det-ask-state-001` es spec-only (0 refs en código): con el prompt "¿Has aparcado?" abierto, Home muestra "Monitoring" y esconde la verdad accionable | Backlog, priorizar tras el port |
| P9 | `showPermissionRevoked` usa `COLOR_DEBUG` púrpura (probable descuido) | **LIMPIAR** |

Notificaciones: 5 canales bien tipados (detection LOW, sentry MIN, action HIGH, upload
DEFAULT, debug), ids sin colisión, morphing 2002 prompt→saved con `setOnlyAlertOnce`
correcto, nudge persist-FIRST [DET-NUDGE-PERSIST-001] con resolución por 3 vías, y
provenance de nudge [DET-NUDGE-PIN-PROVENANCE-001] en master. **MANTENER.**

---

## 7. Doc drift detectado (el código manda)

| # | Documento | Dice | Realidad |
|---|---|---|---|
| **D1** | `BluetoothConnectionReceiver.kt:40-41` (KDoc) | "Registered with `exported=false`" | Manifest `exported="true"` — y es **vital** (DET-BT-RECEIVER-EXPORT-001). **El drift más peligroso del repo**: quien "arregle" el manifest para casar con el KDoc mata la estrategia BT entera otra vez, en silencio. Corregir YA |
| D2 | `PaparcarApp.kt:90-91` | "Geofences have a 24h TTL… janitor renews" | `NEVER_EXPIRE` desde SESSION-ISOLATION-001; el comentario correcto está en el bloque siguiente |
| D3 | `VehicleFenceOwnershipPolicy.kt:14-16` | "NOT yet wired (Phase 2)" | Cableada en 3 sitios (ConfirmParking, Janitor, SwapActiveVehicleFences) |
| D4 | `GeofenceJanitorWorker` KDoc vs su caller | Se contradicen sobre TTL/restauración | Ver D2 |
| D5 | `det-bt-wrong-car-abort-001.md` | "staged, sin commit" | Commit `10842797` en master; queda field-test + el gap BT4 sin ticket |
| D6 | 6 docs (`SIGNAL-ARCHITECTURE.md:96`, `REFACTOR-PLAN.md:102`, `PARKING-DETECTION.md:788`, `HANDOFF…`, backlog) | `GeofenceBroadcastReceiver` existe/participa | Borrado; rol partido en witness + carril getForegroundService |
| D7 | `PARKING-DETECTION.md` §1.6-1.7 | UNA geocerca por sesión; API `computeGeofenceRadius` en ConfirmParkingUseCase; enums `MOTO/LARGE/VAN` | TRES geocercas; `ParkingDetectionConfig.geofenceRadiusFor`; `MOTORCYCLE/LARGE_SEDAN/VAN_HIGH` |
| D8 | `PARKING-DETECTION.md:376-381` | Fall-through FND-004 (`lastVehicleEnteredAt != null` → Confirmed tras retries) | Sustituido por gate `admissibleBoarding` (`RunDepartureCheckUseCase:100-107`) |
| D9 | `PARKING-DETECTION.md:128-131` | BT paso 2 = gate solo-accuracy | Es exactamente el agujero que `EvaluateBtParkUseCase` cerró (DrivingAbort etc.) |
| D10 | `IOS_PLAN.md` (2026-05-24) completo | "8/8 impls, wire STILL→coordinator, estimación 6–9 h" | Pre-DET-SOLID: STILL eliminada (DET-D-03), la arquitectura de puertos cambió (escalera AR, safety net, SENTRY, honest close), y el gap real es el orquestador. **Sustituir por `IOS-IMPLEMENTATION-PLAN.md`** |
| D11 | `BUGS_AND_DEBT.md §3` | iOS AR emite STILL/ENTER → `onStillDetected()` | `onStillDetected` ya no existe como señal (DET-D-03) |
| D12 | `AndroidManifest.xml:32-34` + `ExactHeartbeatScheduler.kt:28` | "SCHEDULE_EXACT_ALARM auto-granted hasta targetSdk 32" | targetSdk=36 → denegado por defecto, sin UI de petición |
| D13 | CLAUDE.md navegación | "Splash → Auth → VehicleRegistration → Onboarding → Permissions → Home" | Real: Onboarding → Permissions → GpsDisclaimer → VehicleSizeExplainer → VehicleRegistration → Home |
| D14 | CLAUDE.md resolución | `resolveStrategy(vehicle, isBluetoothEnabled)` | API real: `resolve()`/`strategyFor(vehicles)` con gate de conexión ACL viva |
| D15 | `ios-contracts.md` (archivado) | `GeofenceEventBusImpl` es un BroadcastReceiver | Es un Channel |
| D16 | `RECEIVE_BOOT_COMPLETED` comentario manifest | "para reactivar acelerómetro" | Re-arma AR + geofences + workers |

---

## 8. Limpieza priorizada previa al port iOS

### 8.1 BLOQUEANTE para iOS (Fase 0 del plan)

1. **Promover el replay a commonMain** como puerto de ingesta batch (§5.3). Sin esto no
   hay vía wake-and-query.
2. **Semántica del puerto de geocercas** (G2): decidir push (bus, como iOS hoy) vs
   entrega directa (Android hoy) y dejar UN contrato; el bus sin consumidor de Android se
   resuelve de paso.
3. **Tiers platform-aware** (P5): el modelo `DetectionReliability`/`DetectionTier` debe
   contemplar iOS (batería N/A, BT imposible → ASSISTED como techo, sin issue de batería
   fantasma). DET-TIERS-001 ya lo anticipa; falta el código.
4. **Puertos de pasos**: `StepCounterSource` y `DetectionStepAnchors` sin impl iOS — el
   diseño del seal (counter, position, moment) debe mapearse a CMPedometer (query por
   rango de fechas) antes de escribir el orquestador.
5. **Decidir el destino de los side-stores** (§5.4): qué side-records necesita iOS y si
   se portifican (puerto común + NSUserDefaults) o iOS define los suyos.

### 8.2 Higiene (no bloqueante, alto valor)

1. **D1** — corregir el KDoc del BluetoothConnectionReceiver (riesgo real de regresión).
2. B1–B3 — hardening del service BT (permiso en promote, reaper 1003, teardown yielding).
3. MODIFICAR ParkingSafetyNetWorker: split de responsabilidades + store tipado para
   `parking_safety_net` (el hazard de prefijos `anchor_*` se elimina de paso).
4. T1 (`KMH_PER_MPS` ×8) y T2 (serializers) — mecánico.
5. Manifest: quitar `FOREGROUND_SERVICE_DATA_SYNC`, `USE_BIOMETRIC`/`USE_FINGERPRINT`;
   añadir uses-feature `required=false`; COARSE en el request array (P1).
6. Muertos: G1 (loiteringDelay), P3 (toggles de notificación — DECISIÓN), scorer D-03c
   (§5.5), T4 (LEGACY_TAG).
7. G3 (límite GMS) + BT4 (ruta en viajes BT) + BT2 (MAC huérfana) — tickets nuevos.
8. Batch de comentarios stale (D2, D3, D12, P7) + actualización de PARKING-DETECTION.md
   (D6–D9) y CLAUDE.md (D13, D14).
9. UNIFICAR: ClearActiveParkingSessionWorker a la cadena `parking_chain`; política única
   de re-registro de geocercas (janitor + cure).
10. DEPARTURE worker: tope para `ProcessFailedRetry` + revisar la política REPLACE
    multi-productor.

### 8.3 DECISIÓN pendientes (no asumir)

> **RESUELTAS 2026-08-13** por el usuario — ver tabla y tickets en
> `docs/backlog/ios-f0-001.md`: alarma exacta → asumir inexacto (IOS-F0-09);
> toggles → eliminar (IOS-F0-08); allowBackup → excluir detección (IOS-F0-07).

- **DECISIÓN: alarma exacta** — pedir `SCHEDULE_EXACT_ALARM` al usuario (fila opcional +
  policy Play) o asumir modo inexacto y simplificar (§2).
- **DECISIÓN: toggles de notificación** — honrar o eliminar (P3).
- **DECISIÓN: allowBackup** — reglas de extracción para excluir el estado de detección
  (§3.2).
