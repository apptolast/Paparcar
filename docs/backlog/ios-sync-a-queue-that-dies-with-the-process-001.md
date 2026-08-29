# IOS-SYNC-A-QUEUE-THAT-DIES-WITH-THE-PROCESS-001 · el outbox durable + BGTask

**Estado:** ✅ Done (06-09-2026) · plegado en la rama iOS (PR #3) · ⏳ declarados: veredicto
`xcodebuild` en CI (el Swift del bootstrap/BGTask) y, en device, matar la app con un write
pendiente → llega en el siguiente dren, y el delete interrumpido NO resucita el coche.

## Problema

Los 3 schedulers iOS (sync/enrichment/report-spot) son corrutinas con backoff en memoria: una
muerte de proceso pierde el trabajo pendiente. El caso que más muerde está documentado en el
propio código (`IosParkingSyncScheduler:96-99`): **un delete remoto interrumpido no deja fila
donde colgar un flag → el coche RESUCITA en el siguiente sync** (un update perdido se auto-cura
vía `pendingSync`; un delete no). Además el mesh de F2 dejó esperando el tick de `BGAppRefreshTask`
(su registro debía llegar con este ticket — un solo sitio).

**Hallazgo nuevo de este ticket**: en un relanzamiento EN BACKGROUND (SLC/region/BGTask), SwiftUI
no construye `ContentView` → `MainViewController()` no corre → **ni Koin ni el controller ni el
mesh arrancaban en los wakes con app muerta**. El `AppDelegate.didFinishLaunching` es el hook
garantizado y nadie lo usaba para el bootstrap.

## Diseño

**El outbox es commonMain; solo el STORE es iosMain** (y el drainer queda a un binding de que
Android lo adopte algún día):

1. `domain/sync/SyncOutbox.kt` (commonMain): `OutboxOp` sellado y `@Serializable` — `SaveSession`
   (re-lee la sesión de ROOM al drenar: `updatedAt` fresco para LWW, mejor que hoy) ·
   `ClearActive` · `UpdateAddressAndPlace` (re-lee dirección de Room) · **`DeleteVehicle` — el
   tombstone que cierra la resurrección: persistido ANTES de intentar, borrado solo en éxito** ·
   `EnrichSession(sessionId, lat, lon)` · `ReportSpot(payload mínimo + enqueuedAtMs)`. Clave de
   REPLACE por op + contador de intentos. Puro y testeado:
   `reportSpotExpiredInQueue` (el gate TTL-en-cola de Android, oro cuando la cola puede tardar
   horas — un spot caducado en cola NO se publica).
2. `domain/sync/SyncOutboxDrainer.kt` (commonMain, testeado con fakes): dren serial bajo mutex —
   un intento por op y drenada; fallo → `attempts+1` y se queda; tope de intentos con drop
   logueado (una op envenenada no puede acumularse para siempre); expirado → drop.
   ⛔ `AddressInfo`/`PlaceInfo` NO se serializan (dominio sin anotaciones de framework): el op de
   ReportSpot replayado tras muerte de proceso llega sin etiqueta de calle — el primer intento
   (in-memory, payload completo) sí la lleva. Degradación declarada, no accidente.
3. Schedulers refactorizados: persist PRIMERO (crash-safe) → intento inmediato vía dren (misma
   latencia online que hoy); los reintentos son re-drenes (wake/foreground/BGTask), no bucles en
   memoria.
4. **`PaparcarIosBootstrap`** (iosMain, invocable desde Swift): `ensureStarted()` idempotente
   (Koin + controller.start + dren) — lo llaman `MainViewController` **y el `AppDelegate`**, que
   además registra los BGTasks. Entradas `onBackgroundRefresh/onBackgroundSync(completion)`:
   tick del mesh (`controller.systemTick`) + dren + completion.
5. **BGTask, todo el `BGTaskScheduler` en SWIFT a propósito** (inmunidad a huecos del klib K/N —
   lección `startMonitoringVisits`): registro en `didFinishLaunching`, submit al pasar a
   background (scenePhase), handlers → entradas Kotlin + re-programación. `Info.plist` gana
   `BGTaskSchedulerPermittedIdentifiers` (`.refresh` + `.sync`, este último
   `requiresNetworkConnectivity`).

## Criterio de éxito

- Tests commonTest del outbox+drainer verdes (JVM y simulador vía CI).
- `xcodebuild` verde (Swift del bootstrap/BGTask + registro).
- Matar la app con un write pendiente → el write llega en el siguiente dren; el delete
  interrumpido NO resucita el coche (test con fakes + device del compañero).

## Consumidores auditados

- Los 3 puertos de scheduler (`ParkingSyncScheduler`/`ParkingEnrichmentScheduler`/
  `ReportSpotScheduler`): contratos SIN cambios — todos los llamadores commonMain
  (`ConfirmParkingUseCase`, workers de Android, `ProcessConfirmedDeparture`…) convergen intactos.
  Android conserva sus impls WorkManager — exento con razón (ya es durable allí).
- `RemoteUserProfileDataSource`/`SpotRepository`/`GetAddressAndPlaceUseCase`: consumidor nuevo
  (drainer), firmas intactas.
- `MainViewController`: adelgazado a `PaparcarIosBootstrap.ensureStarted()` — el delegate de
  notificaciones y el arranque del controller se MUEVEN, no se duplican (un solo bootstrap,
  dos puertas idempotentes).
- `IosWakeMonitors`/mesh: sin cambios — `systemTick` es una puerta nueva del controller que
  reutiliza los dos comandos existentes.
- Serialización: `OutboxOp` es el ÚNICO tipo anotado; los modelos de dominio quedan limpios
  (la degradación del ReportSpot replayado va declarada en su KDoc y en PARKING-DETECTION.md).
- Dev Catalog/strings: sin superficie nueva — exento.

## Ejecutado (06-09, pendiente de plegar)

`SyncOutbox.kt` (domain, modelo+gate) · `SyncOutboxDrainer.kt` (data, commonMain) ·
`SyncOutboxDrainerTest` **7/7** (tombstone sobrevive al fallo y un "proceso nuevo" lo termina ·
cap de envenenadas · gate TTL no publica caducados · TTL anclado al enqueue · Room como verdad ·
`updatedAt` fresco al ENVIAR) · `IosSyncOutbox` (NSUserDefaults+JSON, registro ilegible se
descarta CON log) · 3 schedulers refactorizados · `PaparcarIosBootstrap` + `AppDelegate` lo llama
(**fix del hallazgo**: los relanzamientos en background no arrancaban NADA) ·
BGTask todo en Swift + `Info.plist` identifiers · `controller.systemTick`. Suite JVM completa +
verify mock + compiles verdes; el Swift lo juzga `xcodebuild` en CI.
