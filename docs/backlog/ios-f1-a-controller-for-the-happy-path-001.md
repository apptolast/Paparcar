# IOS-F1-A-CONTROLLER-FOR-THE-HAPPY-PATH-001 · el orquestador iOS: el port aparca de verdad

**Estado:** ✅ Done (05-09-2026) · plegado en `feature/IOS-F0-001-fase0` (**la rama iOS del PR #3
— decisión user: todo el trabajo iOS viaja en un solo PR**; llegará a master cuando la rama
mergee, tras su gate de campo) · ⏳ declarados: veredicto del job `apple` del PR (compile de
`iosMain` + suite en simulador) y el ciclo park→exit→re-park en device (compañero del Mac,
plan §8-F1.9).

## Problema

iOS tiene toda la periferia (GPS, geocerca, pasos, AR, notificaciones, side-records) y **nadie
alimenta al `CoordinatorParkingDetector`**: `IosManualParkingDetectionImpl` es un no-op de 4
métodos, el bus de geocerca emite EXITs que nadie consume, y los side-records de F0 se escriben
sin lector. El auditado completo: `docs/backlog/ios-parity-audit-2026-09-03.md`. Plan de fase:
`docs/IOS-IMPLEMENTATION-PLAN.md` §8-F1 (puntos 7 y 8; el 9 —validación en device— es del
compañero del Mac).

## Doctrina (la misma, ni una excepción)

- *El evento NOMINA, solo el movimiento MEDIDO confirma* — el armado manual iOS arma, jamás
  confirma; confirma el evaluador con conducción medida del stream CLLocation.
- *La DECISIÓN vive en use cases puros de commonMain* — el controller iOS, como el service
  Android, solo hace I/O y side-effects, y serializa triggers en un intake único [DET-INTAKE-001].
- *Carriles separados* — en iOS solo existe el carril Coordinator (BT es imposible por diseño,
  `DeviceCapabilities(false,false)`); nada que mezclar, pero tampoco inventar.
- *Fallo asimétrico* — con la densidad de datos que iOS dé, ante la duda se pregunta.

## Alcance F1 (camino feliz, app viva)

1. `IosDetectionController` (iosMain puro): intake serializado + armado MANUAL
   (`DetectionTrigger` + `ArmEvidence` correctos) + sesión GPS trip-scoped
   (`allowsBackgroundLocationUpdates` ya está en `IosLocationDataSourceImpl`) + coordinator en
   vivo (fixes + pasos + AR) + confirmación → `ConfirmParkingUseCase` (pipeline común) +
   notificación.
2. Registro de región al confirmar + consumo del `GeofenceEventBus` (en iOS el bus SÍ es el canal
   real) → `EvaluateGeofenceExitUseCase` → re-armado.
3. Reconcile `monitoredRegions` ↔ sesiones Room en el arranque (presupuesto 20 regiones,
   `VehicleFenceOwnershipPolicy`).
4. Los 3 puertos no-op (`ManualParkingDetection`, `ArrivalHandoffDetection`,
   `DepartureWatchResumer`) pasan a hablar con el controller — cada uno con SU semántica
   (una puerta por significado [DET-HANDOFF-NOT-MANUAL-001]).

**Fuera de alcance (F2+):** reconstrucción wake-and-query, safety-net mesh, departure inline con
escalera, colas BGTask, escalado Always (F3). No se inventa ningún keep-alive (doctrina §3 del
plan).

## Restricción de verificación

Kotlin/Native NO compila en Windows. Verificación en tres anillos: (1) toda lógica nueva de
decisión va en commonMain con test → JVM + simulador vía CI; (2) el controller iosMain lo
compila el job `apple` del PR de esta rama; (3) el ciclo park→exit→re-park en device es del
compañero (§8-F1 punto 9) y se lista aquí como ⏳ al cerrar.

## Diseño

**Hallazgo que dimensiona todo** (mapa funcional del service, 05-09): el service Android NO llama
a `ConfirmParkingUseCase` — la confirmación entera (confirm + geocerca nueva + notificación +
diagnóstico + seal) vive DENTRO del coordinator, en commonMain. **iOS la hereda gratis.** El
controller solo reproduce el envoltorio de I/O. Segundo hallazgo: todas las decisiones del
envoltorio ya son funciones puras de commonMain (`UserStopQuietPeriod`, `SessionSupersede`,
`ParkingStrategyResolver`+`coordinatorMayArm`, `resolvePostDetectionLifecycle`,
`EvaluateGeofenceExitUseCase`, `VerifyDepartureEvidenceUseCase`) — lo único Android-only del
camino F1 es el STORE del user-stop (SharedPrefs).

### Piezas

1. **`IosDetectionController` (iosMain)** — espejo del invariante [DET-INTAKE-001], no del
   `Service`: `Channel<Command>(UNLIMITED)` + consumidor único en scope propio; un comando se
   maneja ENTERO antes del siguiente; el handler que peta no mata el bucle (catch que re-lanza
   `CancellationException`). Comandos F1: `StartTracking(trigger, evidence, trip?, staleExit,
   armingFenceId?)` · `StopTracking` (cancel interno, sin quiet period) · `UserStop` (orden
   crítico heredado: `onUserStoppedDetection()` → stamp → cancel) · `GeofenceDelivery(event)` ·
   `Reconcile(source)`.
   - **Armado** (espejo de `startParkingDetection` menos FGS/sentry/workers/BT-override):
     supresión por user-stop (pura) → strategy gate (en iOS resuelve siempre COORDINATOR —
     degradación emergente, mismo código) → `SessionStarted` al `DetectionEventLogger` →
     `runtime.setRunning/setPresence(Active)/setTrip` → `PendingArmRecords.arm` + heartbeat con
     latch `sawDriving` por fase → `coordinator.invoke(observeAdaptiveLocation(), evidence,
     nominatingVehicleId, staleExit, …)`.
   - **Epílogo post-retorno**: `ArrivalResolutionRecord.stamp` (gated por
     `outcome.resolvesTheArrival`); honest-close y witness-fix quedan para F2 (⛔ su
     `stepsSinceSeal()` es `null` hasta la query CMPedometer — dejarlo mudo aquí es lo honesto,
     no un olvido). `finally`: clear del pending + `setRunning(false)` + presence `Dead` (sin
     sentry en iOS).
   - **Lane del EXIT** (el bus ES el canal en iOS; sin replay → suscripción en `start()` ANTES de
     que el delegate pueda disparar, y reconcile cubre lo perdido): lookups por fence contra
     Room (mismos 3 casos: `LookupFailed` ≠ `NoSession`) → `EvaluateGeofenceExitUseCase`
     (triggerLat/Lon `null`: CLRegion no trae ubicación del evento) → huérfanas fuera →
     supersede-o-suprimir (puro; orden semántico `notifySuperseded()` ANTES del cancel) → fix
     fresco (`GetOneLocationUseCase`) → `VerifyDepartureEvidenceUseCase` → re-arm con
     `GEOFENCE_EXIT` + `TripContext`. ⚠️ En F1 el EXIT **no publica la plaza liberada** (el
     departure inline con escalera es F2.11 del plan) — arma el seguimiento del siguiente park.
   - **Ciclo de vida**: sin análogo de `stopSelfResult`; la guarda que importa
     (`detectionJob?.isActive`) sí se porta. Sin keep-alive: al terminar el job, la app vuelve a
     suspenderse sola (la sesión CLLocation viva ERA lo que la mantenía).
2. **`FenceReconciliation` (commonMain, NUEVO, función pura + test)** — la única decisión nueva:
   `monitoredRegions` ↔ sesiones activas → qué registrar / qué retirar, presupuesto 20 explícito.
   Patrón `VehicleFenceOwnershipPolicy` (función de nivel superior, no clase inyectada
   [DET-VERDICT-NOT-PREDICATE-001]: alimenta al reconcile del controller, no es un veredicto
   diagnosticable).
3. **`IosUserStopStore` (iosMain)** — gemelo NSUserDefaults del de SharedPrefs (mismas
   semánticas stamp/read/clear) para [DET-STOP-BUTTON-001].
4. **Los 3 puertos** delegan en el controller, cada uno por SU puerta:
   `ManualParkingDetection.start()` → `StartTracking(MANUAL, Manual)`; `.stop()` → `StopTracking`;
   `.stopByUser()` → `UserStop`; `.answerPrompt()` → hooks del coordinator (como el Home row
   Android). `ArrivalHandoffDetection.start()` → `StartTracking(ARRIVAL_HANDOFF,
   ArrivalHandoff)` [DET-HANDOFF-NOT-MANUAL-001]. `DepartureWatchResumer.resume()` → en iOS el
   "watcher" es la región del OS: dispara `Reconcile` y responde si la sesión aparcada tiene
   valla en pie (el CTA "Reactivar" hace algo visible y verdadero).
5. **Arranque**: `MainViewController` llama `controller.start()` tras Koin (suscripción al bus +
   reconcile inicial).

### Qué NO se porta (consciente, del mapa §6)

FGS/notificación persistente · `START_STICKY` · SENTRY completo (cooldowns, triage, ledger) ·
WorkManager (departure/safety-net/backfill/janitor → F2 con su forma iOS) · BT override ·
tap pasivo de ruta (iOS no tiene proveedor pasivo) · `stopSelfResult`.

## Criterio de éxito

- Suite JVM verde + suite simulador verde en CI (nº reportado).
- El job `apple` compila el controller.
- `PARKING-DETECTION.md` documenta el carril iOS F1.
- Barrido de consumidores de los 3 puertos y del bus clasificado abajo.

## Consumidores auditados

- `ManualParkingDetection`: `HomeViewModel` (start/stopByUser/answerPrompt) y
  `SaveManualParkingUseCase` (.stop() al marcar manual) → **cubiertos por convergencia**: ambos
  hablan por el puerto y el puerto ahora llega al controller. Fakes de mock intactos.
- `ArrivalHandoffDetection`: sin llamador común hoy (el real es el safety-net, F2) → **la puerta
  existe antes que su llamador**, a propósito [DET-HANDOFF-NOT-MANUAL-001].
- `DepartureWatchResumer`: `HomeViewModel` (CTA "Reactivar" + cierre del gap en foreground) →
  **cubierto**: en iOS responde verdad (reconcile + ¿valla en pie?) en vez de `false` fijo.
- `GeofenceEventBus`: emisor iOS (`IosGeofenceManagerImpl`) ya existía; el consumidor nuevo es el
  controller. En Android el service sigue siendo quien re-publica — sin cambios.
- `detectionPath`/`armEvidence`: **ningún camino nuevo de confirmación** — el controller arma con
  triggers y evidencias EXISTENTES (`MANUAL`/`Manual`, `GEOFENCE_EXIT`/`VerifiedBySpeed`|
  `Unverified`|`InheritedDrive`, `ARRIVAL_HANDOFF`/`ArrivalHandoff`); los paths los estampan los
  evaluadores comunes de siempre. Nada que espejar a Firestore.
- Dev Catalog: sin pantalla/estado/routing nuevo — los grafos mock siguen con sus fakes
  scenario-aware; el controller es prod-only (iOS). Exento con razón.
- Strings: cero copy nueva (las notificaciones las emite el pipeline común existente).

## Ejecutado (05-09, pendiente de commit)

- `FenceReconciliation.kt` (commonMain, función pura) + `FenceReconciliationTest` (5/5 verdes).
- `IosDetectionController.kt` (iosMain, ~370 líneas): intake [DET-INTAKE-001], armado con
  supresión/gate/pending-arm/heartbeat, invoke del coordinator con el stream adaptativo, epílogo
  (arrival-resolution stamp; honest close mudo→F2 declarado), lane del EXIT por el bus
  (lookup 3-casos → `EvaluateGeofenceExitUseCase` → huérfanas → supersede → fix fresco →
  `VerifyDepartureEvidenceUseCase` → re-arm), reconcile Room↔`monitoredRegions`.
- `IosUserStopStore.kt` + rewire de los 3 puertos + Koin + `MainViewController.start()`.
- Verificado en Windows: suite JVM completa + verify mock + compiles prod/mock — BUILD SUCCESSFUL.
- ⏳ El compile de `iosMain` y la suite en simulador los da el job `apple` al pushear (pedir
  permiso); el ciclo park→exit→re-park en device es del compañero del Mac (plan §8-F1.9).
