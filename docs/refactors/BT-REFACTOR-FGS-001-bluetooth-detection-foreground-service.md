# BT-REFACTOR-FGS-001 — BluetoothConnectionReceiver → ForegroundService pattern

> **Status:** shipped — `refactor/BT-REFACTOR-FGS-001-bt-detection-foreground-service`
> **Affects:** `BluetoothConnectionReceiver`, `BluetoothParkingDetector`, `AndroidDetectionModule`
> **New file:** `BluetoothDetectionService`
> **No behaviour changes:** detection logic, thresholds, confidence, and `ConfirmParkingUseCase`
> call-site are identical.

---

## 1. Why

### 1.1 Problema A — scope huérfano en el Receiver

`BluetoothConnectionReceiver` era `KoinComponent` e inyectaba `BluetoothParkingDetector` por Koin.
El Receiver tiene un `CoroutineScope(SupervisorJob() + Dispatchers.IO)` propio que arranca la
detección y **nunca se cancela**.

Android instancia el Receiver desde cero en cada evento ACL. Cada instancia crea un scope nuevo.
Con múltiples eventos BT durante una jornada (conexión al encender el coche, desconexión al salir,
conexión al volver...) el proceso acumula scopes huérfanos que el GC no puede reclamar mientras
el `SupervisorJob` esté vivo, que es para siempre.

El Koin `single(named("btDetectorScope"))` fue introducido en §13 del `BUGS_AND_DEBT.md` como
solución parcial (mover el scope fuera del Receiver), pero el scope seguía siendo app-global y sin
dueño con un ciclo de vida definido.

### 1.2 Problema B — proceso asesinado durante detección larga

El flujo BT puede tardar hasta ~5 minutos:

```
BT disconnect
   └── delay(30 000 ms)                    ← debounce BT-005
       └── GPS sampling hasta accuracy ≤ 50 m (timeout: 60 s)
           └── distance watch hasta que el usuario camine ≥ 30 m
               └── confirmParking(...)
```

Un proceso sin ninguna protección puede ser matado por el OS en ese tiempo cuando la app
está en background. Si el proceso muere a mitad del `GPS_SAMPLE_TIMEOUT_MS` o durante el distance
watch, la sesión de aparcamiento se pierde silenciosamente. El usuario no recibe confirmación y
la plaza no se publica.

### 1.3 Por qué no unificar con `ParkingDetectionService`

`ParkingDetectionService` (el Coordinator path) usa `START_STICKY` porque puede ser relanzado por
Play Services via `PendingIntent.getForegroundService()` (IN_VEHICLE_ENTER). El BT path **no puede
reanudarse** tras un kill porque el estado crítico (coordenadas del `parkingFix`, vehicleId) es
in-memory y no se persiste antes de confirmación. `START_NOT_STICKY` es el contrato correcto.

Además, mezclar dos estrategias (Activity Recognition y Bluetooth) en el mismo Service viola el
principio de responsabilidad única y añade complejidad al `VehicleState` machine que ya gestiona
`DETECT-SERVICE-RACE-001` y `BUG-DETECT-ENTER-DEBOUNCE-001`.

---

## 2. Arquitectura anterior

```
┌─────────────────────────────────────────────────────────────┐
│ BluetoothConnectionReceiver (manifest-registered)          │
│                                                             │
│  scope = CoroutineScope(SupervisorJob + IO)  ← NUNCA ACABA │
│  detector: BluetoothParkingDetector by inject()            │
│                                                             │
│  onReceive() {                                              │
│    goAsync()                                                │
│    scope.launch {                                           │
│      vehicleRepository.getVehicleByBluetoothDeviceId()     │
│      detector.onCarDisconnected()  ← lanza JOB AQUÍ        │
│                                     (puede durar 5 min)    │
│    }                                                        │
│  }                                                          │
└─────────────────────────────────────────────────────────────┘
           ▼ Koin inject (single)
┌────────────────────────────────┐
│ BluetoothParkingDetector       │
│  scope: CoroutineScope (Koin)  │  ← btDetectorScope single
│  detectionJob: Job?            │
│                                │
│  onCarDisconnected() {         │
│    detectionJob = scope.launch │  ← JOB EN SCOPE APP-GLOBAL
│      delay(30s)                │
│      GPS sampling (60s max)    │
│      distance watch            │
│      confirmParking(...)       │
│    }                           │
│  }                             │
│                                │
│  onCarConnected() {            │
│    detectionJob?.cancel()      │
│  }                             │
└────────────────────────────────┘
```

**Problemas visibles:**
- `scope` en el Receiver: nunca cancelado, se acumula por instancia.
- `btDetectorScope` (Koin single): vive mientras el proceso vive, incluso si no hay detección activa.
- El trabajo de 5 min corre en background sin protección de FGS.

---

## 3. Arquitectura nueva

```
┌─────────────────────────────────────────────────────────────┐
│ BluetoothConnectionReceiver (manifest-registered)           │
│                                                             │
│  scope = CoroutineScope(SupervisorJob + IO)                 │
│  (SOLO para el lookup de vehicleId — termina en ms)         │
│                                                             │
│  onReceive() {                                              │
│    goAsync()                                                │
│    scope.launch {                                           │
│      vehicleRepository.getVehicleByBluetoothDeviceId()     │
│      if (DISCONNECTED)                                      │
│        ContextCompat.startForegroundService(               │
│          BluetoothDetectionService, ACTION_BT_DISCONNECTED) │
│      if (CONNECTED)                                         │
│        context.startService(                               │
│          BluetoothDetectionService, ACTION_BT_CONNECTED)   │
│    }  ← scope job termina aquí                              │
│  }                                                          │
└─────────────────────────────────────────────────────────────┘
             ▼ startForegroundService / startService
┌─────────────────────────────────────────────────────────────┐
│ BluetoothDetectionService : LifecycleService                │
│   foregroundServiceType="location"                          │
│   START_NOT_STICKY                                          │
│                                                             │
│  detector: BluetoothParkingDetector  ← creado en onCreate() │
│  detectionJob: Job?                                         │
│                                                             │
│  onStartCommand(ACTION_BT_DISCONNECTED) {                   │
│    startForegroundCompat()                                  │
│    detectionJob = lifecycleScope.launch {                   │
│      detector.detectParking(address, vehicleId)             │
│      stopSelf()                                             │
│    }                                                        │
│  }                                                          │
│                                                             │
│  onStartCommand(ACTION_BT_CONNECTED) {                      │
│    detectionJob?.cancel()                                   │
│    stopSelf()                                               │
│  }                                                          │
│                                                             │
│  onDestroy() { detectionJob?.cancel() }                     │
└─────────────────────────────────────────────────────────────┘
             ▼ detectParking(address, vehicleId) — suspend
┌────────────────────────────────┐
│ BluetoothParkingDetector       │
│  (stateless — sin scope,       │
│   sin Job, sin estado mutable) │
│                                │
│  suspend fun detectParking() { │
│    delay(30s)          ← cancellable   │
│    GPS sampling (60s)  ← cancellable   │
│    distance watch      ← cancellable   │
│    confirmParking(...)                 │
│  }                             │
└────────────────────────────────┘
```

---

## 4. Decisiones de diseño

### 4.1 `BluetoothParkingDetector` — de actor con scope a función suspend

La clase pasa de gestionar su propio `Job` a ser un **objeto stateless con una función suspend**.
El efecto sobre el flujo BT-005 (abort-on-reconnect) no cambia:

| Antes | Ahora |
|---|---|
| `onCarConnected()` llama `detectionJob?.cancel()` **dentro del detector** | El Service cancela `detectionJob` al recibir `ACTION_BT_CONNECTED`. `delay()` y `Flow.first()` son puntos de cancelación — el abort es cooperativo sin ningún flag extra. |

El detector ya no necesita saber que BT se ha reconectado. La cancelación de corrutinas
hace el trabajo.

### 4.2 `startService()` vs `startForegroundService()` para el evento CONNECTED

| Evento | Método | Razón |
|---|---|---|
| `ACL_DISCONNECTED` | `startForegroundService()` | Trabajo largo (hasta 5 min); necesita FGS para sobrevivir en background. |
| `ACL_CONNECTED` | `startService()` | Trabajo instantáneo (cancel + stopSelf). Si el Service ya corre como FGS, este `onStartCommand` llega sobre la misma instancia — no hay constraint de los 5 s. Si el Service no corre, el background-execution-window del Receiver (<10 s) cubre el tiempo necesario. |

Esta separación evita el "flash de notificación" que ocurriría si también el CONNECTED hiciera
`startForegroundService()` y el Service tuviera que llamar `startForeground()` para luego parar
de inmediato.

### 4.3 `START_NOT_STICKY`

Si el OS mata `BluetoothDetectionService` mientras espera GPS o la distancia, un restart con
intent `null` no tiene sentido: el `vehicleId`, las coordenadas del `parkingFix`, y el estado
de la detección son in-memory y no se persisten antes de `confirmParking`. Mejor perder
esa detección que reanudar con estado vacío.

`ParkingDetectionService` (Coordinator path) puede usar `START_STICKY` porque Play Services
reenvía el `IN_VEHICLE_ENTER` event via `PendingIntent.getForegroundService()` que puede
arrancar de nuevo la detección desde cero sin state loss. El BT path no tiene ese mecanismo.

### 4.4 Notification ID separado

`ParkingDetectionService` usa `DETECTION_NOTIFICATION_ID = 1001`.
`BluetoothDetectionService` usa `BT_DETECTION_NOTIFICATION_ID = 1003`.

Aunque en operación normal ambas no corren simultáneamente (cuando la estrategia es BT,
`ParkingDetectionService` llama `stopSelf()` al detectar `ParkingStrategy.BLUETOOTH`), pueden
coincidir en edge cases de timing o si hay un bug de estado. Con IDs distintos, las notificaciones
son independientes y ninguna sobre-escribe a la otra.

### 4.5 Koin — `btDetectorScope` y `BluetoothParkingDetector` eliminados de `AndroidDetectionModule`

`BluetoothParkingDetector` es instanciado directamente en `BluetoothDetectionService.onCreate()`
con `lifecycleScope` como owner implícito (la función es `suspend`, no tiene scope propio).
Los use cases que consume (`ObserveAdaptiveLocationUseCase`, `ConfirmParkingUseCase`) siguen
siendo Koin singletons y se resuelven con `get<T>()` en `onCreate()`.

El named scope `btDetectorScope` desaparece completamente.

---

## 5. Flujo completo — BT DISCONNECT → parking confirmed

```
1.  El coche se para. BT se desconecta.
2.  OS entrega ACTION_ACL_DISCONNECTED a BluetoothConnectionReceiver.
    - Receiver extrae deviceAddress.
    - goAsync() → scope.launch { vehicleRepo.getVehicleByBluetoothDeviceId(address) }
    - Vehicle encontrado → ContextCompat.startForegroundService(BluetoothDetectionService,
                                                                 ACTION_BT_DISCONNECTED,
                                                                 extras: address, vehicleId)
    - pending.finish(). Scope del Receiver termina.

3.  BluetoothDetectionService.onStartCommand(ACTION_BT_DISCONNECTED):
    - startForegroundCompat()  ← proceso es ahora FGS, el OS no lo mata
    - detectionJob = lifecycleScope.launch {
        detector.detectParking(address, vehicleId)
        stopSelf()
      }

4.  BluetoothParkingDetector.detectParking() [suspend]:
    a. delay(30 000 ms)  ← BT-005 debounce. Cancellable.
    b. observeLocation().filter { accuracy ≤ 50 m }.first()  timeout 60 s
       → parkingFix capturado.
    c. observeLocation().filter { dist ≥ 30 m }.first()
       → usuario confirmado andando.
    d. confirmParking(parkingFix, 0.95, vehicleId)
       → Room write + geofence + WorkManager sync + notificación.
    e. return (la función suspend retorna normalmente).

5.  lifecycleScope.launch finally → stopSelf() → Service onDestroy → lifcecycleScope cancelado.
```

## 6. Flujo completo — BT CONNECT → abort detección activa

```
1.  El usuario vuelve al coche (durante el debounce o la espera GPS).
2.  OS entrega ACTION_ACL_CONNECTED a BluetoothConnectionReceiver.
    - scope.launch { vehicleRepo.getVehicleByBluetoothDeviceId(address) }
    - Vehicle encontrado → context.startService(BluetoothDetectionService,
                                                ACTION_BT_CONNECTED)
    - pending.finish(). Scope termina.

3.  BluetoothDetectionService.onStartCommand(ACTION_BT_CONNECTED):
    - detectionJob?.cancel()   ← cancela la corrutina en detectParking()
      (si está en delay(30s), el delay se cancela; si está en Flow.first(), el flow se cancela)
    - detectionJob = null
    - stopSelf()

4.  La corrutina de detectParking() recibe CancellationException en el punto de suspensión
    activo (delay o first). Propaga la excepción. El bloque finally llama stopSelf()
    —que ya fue llamado, pero el Service maneja dobles stopSelf() sin problema.
```

---

## 7. Ficheros modificados

| Fichero | Cambio |
|---|---|
| `bluetooth/BluetoothConnectionReceiver.kt` | Elimina inyección del `detector`. El scope mínimo solo hace el vehicle lookup y dispara el Service. |
| `bluetooth/BluetoothParkingDetector.kt` | Elimina `scope: CoroutineScope`, `detectionJob`, `onCarConnected()`. `onCarDisconnected()` → `suspend fun detectParking()`. |
| `bluetooth/BluetoothDetectionService.kt` | **Nuevo.** `LifecycleService` dueño del scope. Maneja ambas acciones. `START_NOT_STICKY`. |
| `di/AndroidDetectionModule.kt` | Elimina `single(named("btDetectorScope"))` y `single { BluetoothParkingDetector(...) }`. Limpia imports. |
| `AndroidManifest.xml` | Añade `<service android:name=".bluetooth.BluetoothDetectionService" android:foregroundServiceType="location" android:exported="false" />`. |
| `domain/notification/AppNotificationManager.kt` | Añade `BT_DETECTION_NOTIFICATION_ID = 1003`. |

---

## 8. Tests

`BluetoothParkingDetector` es ahora una función suspend sin dependencias de Android:
**es directamente testeable con `runTest` y un `TestScope`**.

Tests pendientes (no bloqueantes para el merge):

```kotlin
// should_confirm_parking_when_user_walks_30m_after_gps_fix
// should_return_without_confirming_when_gps_times_out
// should_abort_when_coroutine_is_cancelled_during_debounce
// should_abort_when_coroutine_is_cancelled_during_gps_sampling
// should_abort_when_coroutine_is_cancelled_during_distance_watch
```

Los tres últimos verifican el abort-on-reconnect (BT-005) ahora que la cancelación es
responsabilidad del caller en vez del detector.

---

## 9. Compatibilidad con el flujo de Activity Recognition

`ParkingDetectionService` **no se toca en este refactor**. El path AR (Coordinator strategy)
es completamente independiente. Los dos cambios que afectan a la interacción entre estrategias
están en el Koin module (no hay nuevo `btDetectorScope`) y en el Manifest (un Service adicional).

`ParkingDetectionService.handleVehicleTransition()` continúa llamando `stopSelf()` cuando
`strategyResolver.resolve()` devuelve `ParkingStrategy.BLUETOOTH` — ahora ese stopSelf tiene
más sentido porque la detección BT está cubierta por `BluetoothDetectionService`.

---

## 10. Deuda técnica cerrada

- **§13 de `BUGS_AND_DEBT.md`** — marcado como "✅ Resuelto (2026-05-25)" pero la solución
  era parcial (scope Koin, no FGS). Este refactor cierra el problema completo.
- **FIXME: "Should be this class in a receiver?"** — eliminado. El Receiver ya sólo hace
  trabajo mínimo como prescribe el patrón.
