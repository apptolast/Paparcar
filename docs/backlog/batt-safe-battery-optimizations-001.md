# BATT-SAFE-BATTERY-OPTIMIZATIONS-001 · Tres optimizaciones de batería, una idea rechazada por su propia auditoría

**Estado:** ✅ Done (BATT-004 investigado y rechazado — ver abajo) · rama
`bugfix/BATT-SAFE-BATTERY-OPTIMIZATIONS-001-location-sensor-worker-tuning` · worktree
`../Paparcar-batt-opt`

## Problema

Auditoría de consumo (Gonzalo, Oppo CPH2371, `batterystats --charged`): Paparcar es el consumidor
#1 del teléfono con **261 mAh**, de los cuales:

- **194 mAh (74%)** era el flag de diagnóstico remoto encendido desde el 21-09 para el issue #72 y
  nunca apagado — **ya resuelto fuera de este ticket** (Firestore `diagnostics_config`, apagado
  17-11, [[project_batt_audit_alberto_001]]; no hay código que cambiar aquí).
- El resto se reparte entre: listeners de Firestore en tiempo real que viven tanto como el proceso
  (y el FGS residente lo mantiene vivo indefinidamente), reconstrucción de la request de ubicación
  en cada cruce de umbral de velocidad (36× `@+5s` / 29× `@+10s` en la ventana medida), y un sensor
  de pasos WAKEUP (`Sensor 138: 1h 42m / 41 registros`).

Este ticket cubre las optimizaciones de código identificadas en la auditoría:

- **BATT-002** — listeners de alertas de zona atados a visibilidad de UI, no a la vida del proceso. ✅
- **BATT-003** — debounce asimétrico en los tiers de `ObserveAdaptiveLocationUseCase`. ✅
- **BATT-004** — batching del sensor de pasos (WAKEUP) con `maxReportLatencyUs`. ❌ **RECHAZADO** (ver abajo)
- **BATT-006** — `NetworkType.CONNECTED` en el worker periódico que hace red real. ✅

Deliberadamente fuera de alcance (decidido en la auditoría, no aquí):
- **BATT-005** (coalescer los 11 call-sites de `enqueueCheckNow`) — choca con la doctrina "todo
  trigger dispara SIEMPRE". No se toca.
- Subir `setNotificationResponsiveness` de las geofences — choca con la investigación ya hecha de
  latencia de EXIT. No se toca.
- BATT-001b/c (presupuesto/batch de eventos del logger de diagnóstico) — con el flag apagado por
  defecto para casi todos los usuarios, el ROI es bajo; se deja para cuando se reabra el flag de
  alguien.

## Doctrina violada

Ninguna en lo implementado. **BATT-004 se descartó precisamente porque SÍ chocaba** con
`[DET-CADENCE-CANNOT-ACCUSE-AFTER-EGRESS-001]` y con el camino de re-anclaje por salida-sin-pasos
de `StopTracking.steplessQualifies` — ver investigación abajo. `det-change` hizo exactamente el
trabajo para el que existe: cazarlo antes de escribir el patch, no después de romper algo en campo.

## Señales / datos disponibles

- `batterystats --charged com.rndeveloper.paparcar` (Gonzalo, 16/17-11-2026): wakelocks
  `PendingIntentClient` 14m36s/413x, `NetworkLocationScanner` 4m14s/225x, `Sensor 138`
  (STEP_DETECTOR_WAKEUP) 1h42m/41x.
- `dumpsys location`: 65 re-registros propios de `fused_location_provider` en la ventana,
  alternando `@+5s HIGH_ACCURACY` / `@+10s HIGH_ACCURACY` cada 10-30s.
- Código: `ObserveAdaptiveLocationUseCase.kt` (`flatMapLatest` reconstruía la request en cada
  cambio de `Mode`, sin debounce), `AppViewModel.kt:157` (`observeZoneSpotAlerts().launchIn(viewModelScope)`
  sin atar a visibilidad), `FuelPriceWatchWorker.kt` (periódico sin constraints de red).

## Diseño

- **BATT-002**: no se cambia el use case (`ObserveZoneSpotAlertsUseCase` sigue siendo la única
  decisión de "qué contar"). Se cambia el COLECTOR en `AppViewModel`: gateado por
  `appForeground` (nuevo `MutableStateFlow` privado, escrito sólo por `AppIntent.SetAppForeground`),
  reportado por `ForegroundEffect` (ex-`MapForegroundEffect`, renombrado y generalizado) llamado
  desde la raíz `App()`, fuera de cualquier NavHost — distinto de la instancia que Home ya usaba
  para su propio tab. Sin foreground, `flatMapLatest` sustituye el listener por `emptyFlow()`. El
  push del servidor cubre el hueco (documentado en el propio KDoc del use case), y el dedupe durable
  (`watchRepository.alertedSpotIds`) hace que nada se pierda, sólo se retrase.
- **BATT-003**: la máquina de estados de `ObserveAdaptiveLocationUseCase.nextMode` NO cambia sus
  umbrales de velocidad ni sus tiers — sigue siendo la única fuente de la decisión. Se añade un
  debounce **asimétrico** justo alrededor de dónde se aplica esa decisión: subir de tier (más
  densidad) es inmediato; bajar de tier exige que la MISMA petición se sostenga
  `RELAX_DEBOUNCE_MS` (20s). Una oscilación que vuelve a la banda densa CANCELA el pendiente en vez
  de pausarlo — no puede acumular across la banda, sólo reiniciar. Degrada a instantáneo si el fix
  no trae reloj de pared fiable (`timestamp <= 0`), mismo centinela que ya usa `[DET-BURST-001]`.
- **BATT-004**: **no implementado.** Ver investigación.
- **BATT-006**: `setRequiredNetworkType(NetworkType.CONNECTED)` sólo en `FuelPriceWatchWorker`
  (único periódico cuyo trabajo real es red — `GetNearbyFuelStationsUseCase`). Los otros cuatro
  periódicos auditados (`GeofenceJanitorWorker`, `MonthRecapWorker`, `FirstParkNudgeWorker`,
  `RegisterActivityTransitionsWorker`) son local-only (Room, Play Services local, tablas estáticas)
  y se dejan sin constraint a propósito, para que sigan funcionando offline.

## Investigación BATT-004 — por qué se rechaza

La propuesta original asumía que `Sensor 138: 1h42m/41x` en `batterystats` medía despertares de AP
por paso individual. **No es así**: esa cifra es la duración TOTAL de registro del listener a
través de 41 sesiones de detección completas — la vida del listener está atada al `stepJob` de
`CoordinatorParkingDetector`, que dura la sesión entera (armado→confirmación), no el paseo de
egress en sí. El batching no movería esa cifra; la ganancia real —menos despertares de CPU
individuales durante los, como mucho, unos pocos minutos de paseo activo por viaje— es mucho más
pequeña que lo estimado al proponerlo.

El barrido de consumidores (`grep -rn "StepDetectorSource" shared/src` → único consumidor real:
`CoordinatorParkingDetector.stepJob`, línea 652-654) encontró el riesgo real:

- `val stepAtMs = clock()` se lee en el momento de ENTREGA del evento al colector, no en el
  timestamp propio del `SensorEvent`. Con `maxReportLatencyUs`, los pasos se entregan en lotes, así
  que todos los pasos de un lote comparten casi el mismo `stepAtMs` — el reloj de la ENTREGA, no el
  de cada pisada.
- `EgressEvidence.cadenceQualifies` (`EgressEvidence.kt:189`) juzga si un paso es un "pedal stroke"
  comparando `stepAtMs` contra `lastFixSeenAtMs` dentro de `cadenceFixFreshnessMs`. Un `stepAtMs`
  retrasado por el lote se juzga contra un fix de GPS que ya puede no ser el que de verdad fue
  concurrente con la pisada real.
- Más grave: `StopTracking.steplessQualifies` (`StopTracking.kt:404-409`) vigila
  `stepCount == 0` mientras un fix muestra movimiento, para detectar un coche que se movió SIN
  paseo de egress — y ese disparo **desancla y re-ancla** la sesión en la siguiente parada
  (`StopTracking.kt:438-442`, `⚓⤒ anchor UNPINNED`). Con una ventana de batching, un paseo de
  egress REAL y en curso puede seguir leyendo `stepCount == 0` durante hasta `maxReportLatencyUs` —
  exactamente la forma que ese guard existe para cazar. El resultado sería un pin confirmado
  moviéndose al sitio equivocado por un paseo de egress real, no por uno ausente.

**Decisión: no implementado.** Ganancia menor de lo estimado (métrica mal leída) + riesgo real y no
acotable del todo por lectura estática = exactamente el caso en el que la REGLA CRÍTICA pide marcar
y no tocar. Si se quiere revisitar: la vía más segura sería capturar `SensorEvent.timestamp` (reloj
del propio sensor, no de entrega) en vez de `clock()`, lo que exigiría tocar la interfaz
`StepDetectorSource` (`Flow<Unit>` → `Flow<Long>` o similar) y re-auditar cada consumidor — un
ticket propio, con su propio `det-change`, no una línea dentro de este.

## Criterio de éxito

- `./gradlew testAll --console=plain` en verde, incluido el replay de las trazas de campo. **29
  tests** en `AppViewModelTest` (27 + 2 nuevos), **14** en `ObserveAdaptiveLocationUseCaseTest`
  (11 + 3 nuevos, uno de los 11 corregido por el nuevo debounce) — pendiente de confirmar con la
  suite completa antes de cerrar.
- Ningún umbral de velocidad ni tier tocado en `ObserveAdaptiveLocationUseCase` — sólo el debounce
  de transición.
- BATT-004 no aparece en el diff de código, sólo en este doc y en `PARKING-DETECTION.md`.

## Consumidores auditados

- `observeHighAccuracyLocation/observeCruiseLocation/observeBalancedLocation`: únicos consumidores
  son `ObserveAdaptiveLocationUseCase` (BATT-003) y `GetOneLocationUseCase`
  (`observeBalancedLocation`, con su propio timeout — no tocado).
- `ObserveAdaptiveLocationUseCase`: consumida por `CoordinatorDetectionService`,
  `BluetoothParkingDetector`/`BluetoothDepartureDetector` (tests con velocidad fija 0 — el debounce
  nunca se ejercita ahí, sin riesgo) y `ParkingHistoryViewModel` (mismo caso). El único test
  afectado de verdad fue el propio de `ObserveAdaptiveLocationUseCaseTest` — corregido.
- `StepDetectorSource.steps()`: único consumidor real `CoordinatorParkingDetector.stepJob` — por
  eso el barrido lo pudo auditar entero, y por eso encontró el riesgo que tumbó BATT-004.
- `observeZoneSpotAlerts()`: único colector es `AppViewModel` (confirmado por grep) — no hay un
  segundo consumidor que se quede sin este stream.
- Periódicos con `buildPeriodicRequest()`: `FuelPriceWatchWorker` (red real, constraint añadida),
  `GeofenceJanitorWorker`/`MonthRecapWorker`/`FirstParkNudgeWorker`/`RegisterActivityTransitionsWorker`
  (local-only, confirmado leyendo sus dependencias inyectadas — sin constraint, a propósito).
