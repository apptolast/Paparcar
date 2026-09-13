# PARK-RELEASE-THE-SCREEN-NEVER-SAW-001 · "Liberar y avisar" a veces no hace nada

**Estado:** ✅ Done (sin probar en device) · rama `feature/IOS-F0-001-fase0`

## Problema
Reportado por el user (13-09-2026): en el diálogo *"Liberas la plaza, ¿la compartes?"* (peek →
"Me voy"), a veces pulsar **Liberar y avisar** / **Solo liberar** no hace nada visible. Sospecha
inicial: WorkManager.

## Recorrido auditado
`ParkingPeek` "Me voy" → `HomeSheetAction.RequestRelease` → `HomeScreen` (antes: `showReleaseDialog`
local) → `HomeReleaseDialog` → `HomeIntent.ReleaseParking` → `HomeViewModel.releaseParking`
→ `DeclareActiveVehicleUseCase` (+ `SwapActiveVehicleFencesUseCase`) → `ReleaseActiveParkingSessionUseCase`
→ `ReportSpotReleasedUseCase` (geocode ≤5 s si la sesión no está enriquecida + `ReportSpotWorker`)
→ `UserParkingRepository.clearActiveParkingSession` (Room + `ClearActiveParkingSessionWorker`)
→ `GeofenceManager.removeGeofence`.

## Causas encontradas

### A · (el síntoma) El diálogo se cerraba solo si la pantalla VEÍA la liberación en curso
El diálogo era estado local de `HomeScreen`, y se cerraba con un `LaunchedEffect` que esperaba ver
`isReleasingParking` pasar `true → false`. Pero el estado de Compose se conflaciona: si la
liberación entera (poner `releasingReason`, escribir en Room, quitarlo) cabe entre dos frames, la
recomposición solo ve `false → false` y **el diálogo no se cierra nunca**. La liberación SÍ ha
ocurrido, pero el usuario sigue viendo el diálogo con los botones activos. Al pulsar otra vez, la
sesión ya no existe → `releaseParking` sale por "no active session" con un snackbar que queda tras
el diálogo → *"no hace nada"*.
Es más probable justo en el caso rápido: "Solo liberar", coche ya activo, sesión enriquecida. El
propio `HomeViewModelTest` ya lo decía: *"the suite's Unconfined dispatcher would finish the
release before there is any in-flight window left to look at"*.
Misma consecuencia si la sesión la cierra OTRA vía con el diálogo abierto (salida detectada por
geocerca/BT, safety net): el diálogo sigue apuntando a una sesión que ya no existe.

### B · Doble pulsación = dos liberaciones concurrentes
Los botones se deshabilitan cuando la UI se recompone con `releasingReason`, pero dos toques dentro
del mismo frame llegan ambos al VM, que no tenía guard. Resultado: dos `releaseSession` en paralelo;
con "Liberar y avisar" + "Solo liberar" se publica la plaza y Room la registra como NO publicada,
y se cuenta `ParkingSessionEnded` dos veces.

### C · (WorkManager) El cierre remoto podía adelantarse al guardado y resucitar la sesión
`SaveNewParkingSessionWorker` (y los re-guardados de ruta/posición) van en la cola única
`parking_chain_<id>`; `ClearActiveParkingSessionWorker` iba en OTRA, `parking_clear_active_<id>`.
Sin orden entre ellas. Si ambas esperan red (aparcar en garaje, Doze, OEM) y se sueltan juntas:
el clear hace `update(isActive=false)` sobre un doc que aún no existe → NOT_FOUND tragado → SUCCESS;
después el save hace `set()` con la foto `isActive=true`. Si el drenador del outbox ya había
limpiado el `pendingSync`, el siguiente `syncFromRemote` toma el remoto → **la sesión liberada
vuelve a salir aparcada**.

## Doctrina violada
- *Sistemas, no parches*: el cierre del diálogo dependía de observar una transición, no de un hecho
  del estado. Un estado que la UI tiene que "pillar a tiempo" no es estado.
- Orden causal en la sincronización: una operación sobre un documento debe ir DETRÁS de la que lo crea.

## Diseño
- **A + B:** el diálogo pasa a ser estado del VM: `HomeState.releaseDialogSessionId`.
  - `HomeIntent.OpenReleaseDialog(sessionId)` / `HomeIntent.DismissReleaseDialog` (ignorado en vuelo).
  - `releaseParking` cierra el diálogo en la MISMA actualización atómica que apaga `releasingReason`
    (éxito y fallo) → no hay transición que observar.
  - Guard de re-entrada: con una liberación en vuelo, un segundo `ReleaseParking` se ignora.
  - Sesión objetivo desaparecida (antes de pulsar) → se cierra el diálogo + snackbar de error visible.
  - Regla pura `HomeState.withActiveSessions(...)`: si la sesión del diálogo deja de estar activa
    y no hay liberación en vuelo, el diálogo se cierra.
- **C:** `enqueueClearActiveParkingSession` se encadena en `parking_chain_<id>` con
  `APPEND_OR_REPLACE` — el mismo patrón que ya usa `UpdateParkingSessionAddressAndPlaceWorker`
  [BUG-WORKER-001]. Si la cadena terminó o falló, el clear arranca una nueva y corre solo.

## Revisado y NO cambiado (riesgo bajo / por diseño)
- `WorkManager.enqueue*` no bloquea: no puede dejar el botón colgado.
- `ReportSpotWorker` con `NETWORK_CONNECTED`: sin red la plaza sale cuando vuelve; si pasa el TTL en
  cola se descarta a propósito [SPOT-OFFLINE-TTL-001]. No da feedback en el momento: es por diseño.
- Sesión de zona más ancha que el techo de publicación → no se publica y no hay snackbar
  [SPOT-A-ZONE-PARK-FREES-A-ZONE-SPOT-001]. Por diseño.
- `removeGeofence` / `createGeofence` esperan a GMS sin timeout: si Play Services no responde, el
  spinner no termina y el diálogo no se puede cerrar (está bloqueado en vuelo). No reproducido.
- Geocoder en API < 33 es bloqueante y no cancelable: el techo de 5 s puede estirarse. Solo afecta
  si la sesión no estaba enriquecida y el móvil es Android 12 o anterior.
- Una excepción no capturada dentro de `ReleaseActiveParkingSessionUseCase` (p. ej. WorkManager no
  inicializado) tumbaría la app, no la dejaría "sin hacer nada".
- Liberar un coche NO activo registra su geocerca (swap) y la quita al instante: trabajo inútil pero
  sin huérfanas (`geofenceId == sessionId` en todas las vías de alta).

## Criterio de éxito
- Tests VM: el diálogo se cierra aunque no haya ventana en vuelo observable; segunda liberación
  ignorada; diálogo cerrado si la sesión se cierra por otra vía; no se cierra al descartar en vuelo.
- Test Robolectric: con el guardado pendiente, el clear queda BLOQUEADO detrás de él.
- En device: pulsar "Solo liberar" / "Liberar y avisar" cierra siempre el diálogo.

## Verificación (13-09-2026, rama `feature/IOS-F0-001-fase0`, sin commitear)
- Compilan `:app:compileProdDebugKotlin`, `:app:compileMockDebugKotlin`, `:shared:compileKotlinIosSimulatorArm64`.
- `:shared:testDebugUnitTest`: 2760 tests, 4 fallos **ajenos a este ticket y previos**:
  `LocaleParityGuardrailTest` (falta `home_nearby_section_header` en 7 locales) y el refetch de
  viewport (`ViewportRefetchTest` + 2 de `HomeViewModelTest` del mismo tema).
- Nuevos en verde: 7 en `HomeViewModelTest` (ciclo de vida del diálogo) y 3 en
  `WorkManagerParkingSyncSchedulerTest`.
- ⏳ Pendiente: prueba manual en device.

## Consumidores auditados
- Aperturas del diálogo: solo `ParkingPeek` → `RequestRelease` → `HomeScreen.onRelease` → **cerrado**.
- `ReleaseParking` desde `AddingParkingPeek` (borrar registro): no usa el diálogo; hereda el guard → **cubierto**.
- `enqueueClearActiveParkingSession`: `clearActiveParkingSession` (4 vías de cierre: manual, salida
  confirmada, salida deducida, revert) y `retractParkingSession` → **cubiertos** por el encadenado.
- iOS `IosParkingSyncScheduler`: outbox persistido que envía la verdad de Room en orden → **exento**.
