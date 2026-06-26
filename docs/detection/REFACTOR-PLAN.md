# Plan de refactor de detección — DET-001

> Rama: `refactor/DET-001-detection-decision-engine`
> Origen: sesión de diseño en `HANDOFF-refactor-deteccion.md`, validada contra el repo el 2026-06-25.
> Regla: 1 ticket = 1 commit. No se mergea sin aprobación explícita del usuario.

Este documento es la fuente de verdad de la ejecución. El HANDOFF queda como memoria de
diseño + correcciones validadas; aquí van las tareas accionables y su estado.

---

## Principio rector
**Fallo asimétrico.** Falso negativo (no detecto plaza) ≈ coste 0. Falso positivo (publico
plaza fantasma) = coste alto, rompe la confianza de la red. Toda confirmación exige la
**conjunción de dos señales independientes**, nunca una sola. La fuerza de la confirmación
viene del egreso (la persona se aleja del coche), no de la quietud.

---

## Decisiones cerradas (2026-06-25)
- **Log remoto → flag en Firestore** (`diagnostics_config/{userId}.enabled`), toggle por MCP `firestore_update_document`. *(Decisión original era Remote Config GitLive, descartada en DET-LOG-02: la red bloquea descargar `dev.gitlive:firebase-config` por inspección SSL/PKIX. El flag Firestore no añade dependencias y es más directo de togglear por MCP.)*
- **Renombrado (Fase F) → después de Fase D.** Estabilizar lógica antes de recablear nombres.
- **FGS restart → no promover sin sesión.** Mantener `START_STICKY`, pero verificar `UserParking`/sesión válida antes de `fgs.promote`; generalizado a todos los caminos de `onStartCommand` que acaban en `Ignore`/sin job.
- **Trigger de salida = GEOFENCE_EXIT (departure), no AR `IN_VEHICLE_ENTER`** (cableado de motor, Fase G-01).
- **UI de armado en frío = tarea independiente del trigger** (Fase G-02): cuando no hay plaza
  aparcada ni detección activa, dar al usuario una forma de registrar "he aparcado" para crear
  el `UserParking` + geocerca, habilitando la salida por geocerca. La geocerca solo existe si
  hay `UserParking` activo (la crea `ConfirmParkingUseCase`).

---

## Estado de fases

| Fase | Descripción | Estado |
|------|-------------|--------|
| 0 | Andamiaje de diagnóstico (log remoto Firestore) | ✅ `DET-LOG-01..04` · `DET-LOG-05` (departure trace) diferido |
| A | Parar el sangrado (gate de egreso, FP de Praga) | ✅ código + tests verdes (sin commit aprobado para merge) |
| B | Cerrar FGS huérfano (bug 2) | ✅ código + tests verdes |
| C | Endurecer scoring | ✅ C-01 done · C-02 diferido a post-field-test |
| D | Reestructurar a decisión pura | 🔄 D-01/02/04 ✅ · D-03 diferido a post-field-test |
| E | Determinismo BT: corroboración por geocerca | ⏪ E-01 revertido (FP) · E-02 ya satisfecho |
| F | Renombrado / limpieza | ✅ F-01 + F-02 done |
| G | Trigger geocerca + UI | 🔄 G-01 implementado (pendiente verificar FGS-geofencing en device) · G-02 diseñado |

---

## Tickets (1 ticket = 1 commit)

### Fase 0 — Andamiaje de diagnóstico *(en paralelo con A; habilita el resto)*
- `DET-LOG-01` ✅ · Port `DetectionEventLogger` + `NoOpDetectionEventLogger` + modelo `DetectionEvent` (sealed, 8 tipos, reusa `GpsPoint`) en `domain/diagnostics/` + `FakeDetectionEventLogger` + test. Verde.
- `DET-LOG-02` ✅ · `FirestoreDetectionEventLogger` (GitLive) + `DetectionEventDto`/`DetectionSessionDto`; esquema `diagnostics/{userId}/sessions/{id}` + subcolección `events`; buffer no bloqueante por `Channel`. **Gate por flag Firestore `diagnostics_config/{userId}.enabled`** (NO Remote Config: la red del proyecto bloquea descargar `dev.gitlive:firebase-config` por inspección SSL/PKIX; el flag Firestore se togglea por MCP `firestore_update_document`, más directo). DI en `DataModule`. Compila + suite verde.
- `DET-LOG-03` ✅ · Instrumentado **Coordinator (coordinator-first)**: `SessionStarted`/`SessionEnded` (con outcome), `Step`, `Candidate` OPENED/DISCARDED, `Decision` CONFIRMED/CONFIRM_FAILED. `sessionId` = epoch-ms de inicio. Inyectado en DI + tests (incl. test de emisión de traza). Suite verde.
- `DET-LOG-04` ✅ · **Reenfocado.** El `DetectionSessionHolder` resultó innecesario: los eventos geofence/BT-departure ocurren **fuera** de la sesión de detección (en la salida, sin coordinator activo) → no correlacionan con su `sessionId`. Lo valioso (y que habilita el replay de Fase D) se hace **interno al coordinator**: `LocationFix` (cada fix GPS + `stoppedDurationMs`) + `ActivityTransition` EXIT/STILL (edge-detectados del flip de estado). Tipo `LocationFix` nuevo + DTO. Tests. Suite verde.
- `DET-LOG-05` ⏸️ **diferido** · Traza de **departure** (geofence/BT-connect/disconnect) con su propio modelo de sesión (`diagnostics/{userId}/departures/...`), ya que esos eventos no pertenecen a una sesión de detección. Hacer si el field test muestra que hace falta diagnosticar la salida.
- **Reglas Firestore (DET-LOG):** auditadas por MCP — el catch-all `match /{document=**}{ if false }` **denegaba** `diagnostics/**` y `diagnostics_config/**`. Añadidas reglas en `firestore.rules` (sólo el propio uid escribe sus trazas; el flag sólo se lee desde cliente). **Falta desplegarlas** (`firebase deploy --only firestore:rules`) antes del primer field test.
- *(fuera de commit: activar `diagnostics_config/{userId}.enabled=true` por MCP + validación en campo)*

### Fase A — Parar el sangrado (FP de Praga) ✅
- `DET-A` (config) · `minEgressDisplacementMeters = 18f` + `require(> minGpsAccuracyMeters)`; corregido comentario de cadencia GPS (2→2–5 s). → `ParkingDetectionConfig.kt`
- `DET-A` (gate) · Campo inmutable `egressAnchor` (capturado al primer fix parado, limpiado al
  conducir/reposición); helper `hasEgressDisplacement`; aplicado como AND en Path 8 y en el
  `hasStepsProof` del candidate. → `CoordinatorParkingDetector.kt`
- `DET-A` (tests) · Test de la traza Praga (EXIT+pasos sin desplazamiento → NO confirma) + actualizado
  el test de fast-confirm para exigir desplazamiento. → `CoordinatorParkingDetectorTest.kt`

### Fase B — Cerrar el FGS huérfano (bug 2) ✅
- `DET-B-01` ✅ · `processTransitionIntent`: `TransitionAction.Ignore` (ENTER duplicado debounced) ahora hace `stopIfIdle` en vez de `Unit` — era la fuente concreta del huérfano. + `else -> stopIfIdle` en el `when` de `onStartCommand` para acciones no manejadas.
- `DET-B-02` ✅ · `onStartCommand` maneja el **intent nulo** (sticky restart tras kill) **antes de promover**: sin sesión recuperable en memoria, para sin promover el FGS. (`START_STICKY` se mantiene.)
- `DET-B-03` ✅ · Test Robolectric `CoordinatorDetectionServiceTest` (androidUnitTest): null-intent → no promueve FGS + `stopSelf`. La decisión `Ignore` ya está cubierta por `HandleVehicleTransitionUseCaseTest`; la wiring a `stopIfIdle` es glue de una línea verificada por compile.

### Fase C — Endurecer scoring
- `DET-C-01` ✅ · Egreso como **precondición de TODO auto-confirm** del candidate (`!hasEgress -> false`). Cierra el hueco `windowElapsed && hadVehicleExit` (AR-exit + tiempo sin desplazamiento). Invariante resultante: ningún auto-confirm sin que el usuario se haya alejado ≥18 m del coche. STILL/dwell/exit-solo solo abren candidato + prompt. Doc actualizado. Suite verde.
- `DET-C-02` ⏸️ **diferido (2026-06-25)** · Revert automático post-confirm si reaparece velocidad sostenida. DET-C-01 ya hace el caso casi imposible por construcción (confirmar exige egreso → coche parado). El residual (egreso espurio por salto GPS estando en marcha) es raro; se retomará **con datos del field test** si aparece. Opciones evaluadas si se retoma: (B) age-based en departure (poco invasivo), (A) watch in-coordinator (captura GPS-jump más rápido pero cambia el lifecycle confirm→end).

### Fase D — Reestructurar a decisión pura
- `DET-D-01` ✅ · Sealed `ParkingDecision { Confirmed(pathLabel,reliability) / Rejected / Inconclusive }` (espejo de `DepartureDecision`).
- `DET-D-02` ✅ · `EvaluateParkingDecisionUseCase(ParkingDecisionInput): ParkingDecision` puro (primitivos, sin estado del coordinator). El `evaluateCandidatePhase` del Coordinator delega; comportamiento idéntico (instanciado internamente, sin churn de DI). Path 8 (EXIT+steps fast) se deja aparte por ahora.
- `DET-D-03` ⏸️ **diferido** · Reconvertir `CalculateParkingConfidenceUseCase` a mapeo ruta+GPS → confianza (metadato). **Cambia comportamiento** (cuándo se abre candidato / se notifica), no solo estructura → hacer tras field test o con validación en device.
- `DET-D-04` ✅ · 12 tests de la función pura: paths time-driven (`windowElapsed`) antes intesteables + replay de Praga (steps sin egreso → Inconclusive/Rejected) + mismatch scooter + selección de ventana.

### Fase E — Determinismo BT: corroboración *(la ruta BT YA existe — ver HANDOFF §2.4 corregido)*
- `DET-E-01` ⏪ **REVERTIDO** (code-review, 2026-06-26) · Alimentar `onVehicleEntered` en el BT connect rompía el fallthrough `BUG-WALK-DEPART-001` de `DepartureDetectionWorker`: un BT-user **sentado** en su coche aparcado (BT connect → enter presente) + parpadeo de geocerca → publicaba plaza fantasma sin que el coche se moviera. El AR `IN_VEHICLE_ENTER` (movimiento real, no mero emparejamiento) ya cubre BT-users y es mejor señal. Revertido entero (código + test + doc).
- `DET-E-02` ✅ *(ya satisfecho)* · `handleConnected` no libera nada; la liberación sigue gateada en GEOFENCE_EXIT + velocidad sostenida (`minimumDepartureSpeedKmh`) dentro de `DetectParkingDepartureUseCase`. "Esperar salida real" ya se cumple. Verificado + documentado.

### Fase F — Renombrado / limpieza *(tras D)*
- `DET-F-01` ✅ · Movido `PARKING_DETECTION_RELIABILITY = 0.95f` → `config.reliabilityBluetooth` (+ require de orden vs vehicleExit/userConfirmed). Cierra el `TODO-BT-CONFIG-P2`. Los umbrales BT (`BT_DISCONNECT_DEBOUNCE_MS`, `GPS_ACCURACY_THRESHOLD_M`, `DISTANCE_THRESHOLD_M`…) **se quedan locales**: los usa una sola clase, así que por la regla de CLAUDE.md van en su `companion object`, no en config.
- `DET-F-02` ✅ · Renombrados `CoordinatorParkingDetector` (era `ParkingDetectionCoordinator`) y `CoordinatorDetectionService` (era `ParkingDetectionService`), simétricos con `BluetoothParkingDetector`/`BluetoothDetectionService`. Tokens inequívocos → sed scoped a `composeApp/src` + `git mv` de 4 ficheros (clase + test de cada uno) + AndroidManifest (main + debug) + doc vivo. Android + commonMain + suite verde. **iosMain no compilable en Windows (K/N iOS necesita macOS)** pero el renombrado ahí es consistente (grep limpio; la clase vive en commonMain). Docs históricos `docs/refactors/*` se dejan como registro point-in-time.

### Fase G — Trigger geocerca + UI — **diseño de señales en `SIGNAL-ARCHITECTURE.md`**
- `DET-G-01` *(motor)* 🔄 **implementado** (2026-06-26) · `GEOFENCE_EXIT` arma la detección vía
  `getForegroundService` (entrega privilegiada de Play Services, espejo de AR/`BUG-FGS-001`); el
  mismo intent despacha departure + arma. **AR ENTER ya no arma** (queda solo como timestamp).
  **Walk-out gate** (`BUG-WALK-DEPART-001` aplicado al armado): solo arma con `IN_VEHICLE` reciente
  → salir andando no arma. `GeofenceBroadcastReceiver` se mantiene como fallback revertible.
  **Pendiente: verificar en device que `getForegroundService`-geofencing arranca el FGS.**
  ✅ **AR movido a broadcast** (sin flash de FGS): `ActivityTransitionReceiver` registra el timestamp
  + alimenta EXIT/STILL al coordinator; AR ya no arma. `HandleVehicleTransitionUseCase`, `TransitionAction`
  y su test **borrados** (eran la maquinaria del armado por AR). Walk filtrado por **velocidad** (igual
  que la liberación), no por IN_VEHICLE.
- ~~`DET-G-01` bloqueante FGS~~ — **resuelto**: arrancar el FGS
  desde el worker/geocerca (background) puede lanzar `ForegroundServiceStartNotAllowedException` en
  Android 12+. Opción recomendada **C** (mantener arranque-FGS por AR ENTER —contexto exento— y mover
  el gate "Praga por construcción" al gate de sesión, gran parte ya cubierto por A+B). **Pregunta
  abierta:** ¿hace falta quitar AR-ENTER-arma (b) si A+B+C ya cubren Praga/FGS? El field test decide.
- `DET-G-02` *(UI)* 📐 · Affordance "he aparcado aquí" (reusa `HomeMode.AddingParking`) cuando no hay
  plaza ni detección + indicador "detección activa" (reusa `VehicleMonitoringStatus`). UI a diseñar
  con datos del field test (frecuencia de estados, % sesiones que acaban en prompt).

---

## Dependencias
- G depende de A+B+D estables (no diseñar UX sobre motor inestable).
- E depende del log (Fase 0) para validar el caso garaje.
- F después de D para no recablear nombres dos veces.
- G-01 (motor) y G-02 (UI) son independientes entre sí.
