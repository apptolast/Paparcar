# TRIP-THE-TRACKER-RIDES-THE-SENTINEL-001 · El tracker de trayecto vive en el sentinel, y un enganche BT lo arranca

**Estado:** ✅ Done (2026-09-14) — mergeado a master con squash; el hash vive en `MEMORY.md`.
Gate de campo pendiente (no bloqueante): un viaje BT real con `routePolyline` — mismo viaje Kamiq.
**Verificado:** suite completa verde (`BtTripShutoffTest` 5/5 · `TripTrackerTest` 5/5) · verify del
grafo mock · `compileMockDebugKotlin` + `compileProdDebugKotlin` + `compileKotlinIosSimulatorArm64`
OK · frescura del confirm BT comprobada contra `ROUTE_FRESHNESS_MS` (30 min ≫ minutos de la caza).
**Abierto:** 2026-09-14 · desde master `23183c71` · fase 3 del pivote trayectos-como-producto.
Es la **vía B** de `docs/backlog/det-a-bt-drive-leaves-no-trace-001.md` — el "separate gap" que el
comentario de `EnrichParkingSessionWorker` reclama desde agosto.

## Problema

El carril BT —el determinista, el que mejor detecta— es el que peor historia cuenta: despierta en
el DESTINO, así que sus sesiones se guardan sin `routePolyline` ni `routeDistanceMeters`. Con el
trayecto como producto principal, el usuario mejor configurado es el que no ve ni ruta ni km.

La señal de arranque existe y está desaprovechada: el **ACL CONNECT** del MAC emparejado ya se
escucha (`BluetoothConnectionReceiver`), ya estampa origen (`BtConnectionStore.recordConnected`,
`2a1a42c6`) — pero no arranca ningún muestreo.

## Doctrina

- *El evento NOMINA, solo el movimiento MEDIDO confirma* — *reforzada*: el CONNECT no arma ni
  confirma NADA; solo enciende un grabador. La decisión de aparcamiento del carril BT queda
  intacta (disconnect → fix → ≥30 m), y `ConfirmParkingUseCase.encodeFreshRoute` —punto de
  convergencia común— le adjunta la ruta MEDIDA sin tocar una línea del carril.
- ⛔ **El tracking no influye en la decisión**: el tracker solo alimenta `DrivingRouteStore`.
  No toca `DetectionRuntimeState` (ni `isRunning` ni `phase` ni `trip`) — a los ojos de la máquina
  de estados de detección, un viaje BT trackeado es exactamente igual que hoy.
- Las estrategias siguen sin mezclarse: el tracker no es una estrategia — no puntúa, no arma, no
  confirma.

## Diseño

**FGS único (decisión del user): el tracker monta en `CoordinatorDetectionService`**, entra por el
intake serializado [DET-INTAKE-001] como dos acciones nuevas. La lógica nueva es pura y común.

1. **`TripTracker`** (`domain/detection/`, commonMain, testeable con fakes): consume el stream
   adaptativo + el tap pasivo, alimenta `DrivingRouteStore` (los mismos dos taps que el job de
   detección, misma forma) y aplica el **apagado por viaje nulo**: radio encendido en el garaje sin
   conducir → el muestreo se apaga solo. Devuelve por qué terminó (`NullTrip` / cancelación).
2. **`BtTripShutoff`** (`domain/detection/`, puro): tras `NULL_TRIP_WINDOW` (10 min) sin ningún fix
   a velocidad de conducción Y sin desplazarse del primer fix más de `NULL_TRIP_RADIUS` (150 m) →
   `NullTrip`. Un fix conduciendo lo desarma para el resto del viaje. Sin fixes no hay absolución:
   la ventana corre por reloj inyectado (`nowMs`), no por timestamps de fixes — un garaje
   subterráneo sin GPS también se apaga.
3. **Servicio** — `btTrackingJob` independiente de `detectionJob`:
   - `ACTION_BT_TRIP_START` (extra vehicleId): si hay `detectionJob` activo → `btTrackingPending`
     (el override de arbitraje que viaja delante lo aborta; el epílogo arranca el tracking al
     terminar). Si no → lanza el tracker.
   - `ACTION_BT_TRIP_STOP`: cancela job + limpia pending. El store NO se toca (el confirm del
     carril BT lo consume; el gap de 20 min segmenta los restos).
   - `resolveIdleEpilogue` / `stopIfIdle`: **guard nuevo** — con `btTrackingJob` activo no hay
     teardown ni decisión de sentry. Y el epílogo re-arranca el tracking si quedó `pending` (un
     coche BT sigue enganchado tras abortarse una sesión coordinator).
   - `startParkingDetection`: **cancela `btTrackingJob` antes de lanzar** — el tap del coordinator
     toma el relevo de la alimentación; nunca hay dos requests GPS del servicio a la vez.
   - Tras `NullTrip` NO se re-arranca hasta el siguiente CONNECT (el pending se consume al
     arrancar; no hay bucle apagado→epílogo→encendido).
   - Presencia: `Active` mientras trackea (es trabajo foreground real); el epílogo re-decide al
     terminar, como con cualquier job.
4. **Receiver** (CONNECT, tras el trabajo actual): `startForegroundService(ACTION_BT_TRIP_START)` —
   los broadcasts ACL están exentos de la restricción de arranque en background, y el servicio ya
   hace `startForeground` para toda acción. (DISCONNECT): `startService(ACTION_BT_TRIP_STOP)`
   además del arranque del hunt de siempre.

**El pago**: al confirmar el pin BT, `encodeFreshRoute` encuentra el store lleno → la sesión BT
lleva `routePolyline` + `routeDistanceMeters` **medidos**, y el `EnrichParkingSessionWorker` la
snapea como a cualquier otra. Cero cambios en el carril BT, cero en el confirm.

**Fuera de alcance, a propósito:**
- Dibujar el viaje BT vivo en Home (exigiría tocar `DetectionRuntimeState`, que este ticket
  declara intocable). El dato llega al historial; el directo es secuela de la fase 4.
- El sentinel despertado-por-BT para flotas solo-BT (ahorro de reposo) — secuela, toca
  `SentryLifecycleDecision`.
- La notificación FGS durante tracking reutiliza la de detección existente (es verdad — la
  detección automática ESTÁ activa vía BT): sin copy nuevo, sin locales.

## Criterio de éxito

- Tests: `BtTripShutoff` (ventana, radio, desarme por conducción, sin-fixes) + `TripTracker`
  (alimenta el store, NullTrip apaga, cancelación limpia).
- Suite completa verde + compiles mock/prod + iosSimulatorArm64.
- En campo (gate de la fase 4, no de este merge): un viaje BT real termina con `routePolyline`
  medido en su sesión.

## Consumidores auditados

| Punto | Riesgo | Clasificación |
|---|---|---|
| `resolveIdleEpilogue`/`stopIfIdle` (teardown) | matar el FGS mientras graba | **cerrado**: guard de `btTrackingJob` |
| Guards `detectionJob?.isActive` de los arm paths (:381,:524,:546,:568,:598…) | un tracking-only NO debe bloquear un armado | **cerrado**: el tracking vive en OTRO slot; los arm paths cancelan el tracking al lanzar |
| `EvaluateBtArbitrationUseCase` (DET-TIERS-001) | orden connect: override vs start | **cerrado**: el receiver ya envía el override antes; `cancelDetectionJob` es síncrono y el intake serializa |
| `ConfirmParkingUseCase.encodeFreshRoute` | frescura/extent con ruta BT | **cubierto**: mismos umbrales que el coordinator (ROUTE_FRESHNESS, MIN_EXTENT) |
| `DrivingRoute.NEW_TRIP_GAP_MS` (20 min) | parada larga BT entre connects | **cubierto**: segmentación existente |
| `DetectionRuntimeState` | contaminar la máquina de estados | **exento por diseño**: el tracker no la toca |
| `PIN_TO_PIN_ELIGIBLE_PATHS` ("bt" excluido) | ¿se habilita ya? | **exento**: sigue excluido — eso es la vía A (fase 6), gated por el Kamiq |
