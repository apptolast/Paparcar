# Paparcar — Parking detection

> Visión consolidada del sistema de detección. La spec algorítmica canónica (referida en `CLAUDE.md`) sigue siendo `docs/detection/PARKING-DETECTION.md` — este documento es la **vista de producto + estado actual + plan de mejora**.
> Última auditoría: **2026-05-24**.

---

## 1. Resumen

Paparcar usa **dos estrategias independientes** de detección de aparcamiento que nunca se mezclan en runtime. La elección se hace al inicio de cada conducción según las capacidades del vehículo:

| Estrategia | Trigger | Confianza típica | Cuándo se usa |
|-----------|---------|------------------|---------------|
| `BluetoothParkingDetector` | BT disconnect → GPS fix → distance > 30m | `reliability=0.95` | Vehículo con `bluetoothDeviceId != null` y BT activo en el móvil |
| `ParkingDetectionCoordinator` | Activity Recognition + GPS speed + still + confidence scoring | `reliability=0.75–0.90` | Resto de casos |

La resolución está en `ParkingStrategyResolver`:

```kotlin
fun resolveStrategy(vehicle: Vehicle, isBluetoothEnabled: Boolean): ParkingDetectionStrategy =
    if (vehicle.bluetoothDeviceId != null && isBluetoothEnabled) {
        BluetoothDetectionStrategy(vehicle.bluetoothDeviceId)
    } else {
        CoordinatorDetectionStrategy()
    }
```

Ambas convergen en el mismo pipeline post-confirmación:

```
Strategy → ConfirmParkingUseCase
            ├→ UserParkingRepository.insertActive(...)       (Room, sync)
            ├→ GeofenceManager.register(...)                  (Play Services / CLLocationManager)
            ├→ AppNotificationManager.notifyConfirmed()
            └→ ParkingSyncScheduler.schedule(sessionId)
                  └→ ParkingSyncWorker → Firestore
```

---

## 2. Estrategia Bluetooth (determinista)

### 2.1 Flujo

```
ACL_DISCONNECTED                              (BluetoothConnectionReceiver)
        │
        ▼
┌───────────────────────────────────────┐
│ Debounce 30s                          │   ← BT_DISCONNECT_DEBOUNCE_MS
│ (si reconecta dentro: abort)          │
└───────────────────────────────────────┘
        │
        ▼
┌───────────────────────────────────────┐
│ GPS fix con accuracy ≤ 50m            │   ← timeout 60s (GPS_SAMPLE_TIMEOUT_MS)
│ (timeout → no confirm, log y salir)   │
└───────────────────────────────────────┘
        │
        ▼
┌───────────────────────────────────────┐
│ Espera distance ≥ 30m                 │   ← DISTANCE_THRESHOLD_M
│ desde la ubicación del disconnect     │
└───────────────────────────────────────┘
        │
        ▼
   ConfirmParkingUseCase(reliability=0.95)
```

### 2.2 Por qué funciona

El BT del coche es una señal extremadamente fuerte: si el usuario se desconectó del coche, salió. El debounce de 30s elimina falsos positivos de paradas de tráfico donde el BT puede oscilar.

### 2.3 Limitaciones conocidas

- Solo si el usuario emparejó el coche en `BluetoothConfigScreen` (asignación manual obligatoria)
- iOS: `getBondedDevices()` devuelve vacío por diseño de iOS — la asignación necesita UUID manual o detección por anuncio de servicio
- Si el coche tiene varios módulos BT (manos libres + audio), pueden generar eventos duplicados — el `@Volatile detectionJob` cancela cualquier job en curso al iniciar uno nuevo

### 2.4 Archivos involucrados

| Archivo | Rol |
|---------|-----|
| `androidMain/.../bluetooth/BluetoothConnectionReceiver.kt` | Receiver de ACL_CONNECT/DISCONNECT, resuelve `vehicleId` |
| `androidMain/.../bluetooth/BluetoothParkingDetector.kt` | Orquesta debounce + GPS + distance check |
| `androidMain/.../bluetooth/AndroidBluetoothScanner.kt` | Lista dispositivos emparejados para `BluetoothConfigScreen` |
| `commonMain/.../domain/detection/ParkingStrategyResolver.kt` | Decide qué estrategia usar |
| `commonMain/.../domain/usecase/parking/ConfirmParkingUseCase.kt` | Pipeline post-confirmación |

---

## 3. Estrategia Coordinator (probabilística)

### 3.1 Modelo de estados

```
                        ┌─────────┐
                        │  IDLE   │ ◀────────┐
                        └────┬────┘          │
              activity:      │               │
              IN_VEHICLE     │               │ HIGH no se
              ENTER          ▼               │ confirma en
                        ┌─────────┐          │ ventana →
                        │ DRIVING │          │ reset
                        └────┬────┘          │
                  GPS speed   │               │
                  < 1 m/s     │               │
                  durante     ▼               │
                  initialStop                 │
                  Window 30s                  │
                        ┌─────────┐          │
                        │ STOPPED │          │
                        └────┬────┘          │
                             │ confidence    │
                             │ HIGH ≥0.75    │
                             ▼               │
                       ┌──────────┐          │
                       │CANDIDATE │──────────┘
                       └────┬─────┘
                            │ ventana de observación
                            │ (3 min con AR exit, ~20 min sin)
                            ▼
                       ┌──────────┐
                       │CONFIRMED │ → ConfirmParkingUseCase
                       └──────────┘   reliability=0.75-0.90
```

### 3.2 Señales que alimentan el scoring

- **GPS speed** — `< 1 m/s` cuenta como "stopped". Reposicionamiento detectado a 2 fixes consecutivos con `speed ≥ 1.7 m/s` (repositionSpeedMps) y `accuracy ≤ minGpsAccuracyForDriving`
- **GPS accuracy** — solo fixes con accuracy razonable cuentan; el "bestStopLocation" se reescribe si llega un fix mejor durante la ventana
- **Activity Recognition `IN_VEHICLE → EXIT`** — confirma que el usuario salió del vehículo; reduce la ventana de observación a `vehicleExitObservationWindowMs` (~3 min)
- **Activity Recognition `STILL`** — bonus de confianza

El cálculo agregado vive en `CalculateParkingConfidenceUseCase` que recibe un `ParkingSignals(speed, stoppedDurationMs, gpsAccuracy, activityExit, activityStill)` y emite `ParkingConfidence.High/Medium/Low`.

### 3.3 Umbrales

Configurados en `ParkingDetectionConfig` (singleton inyectable):

- `HIGH_CONFIDENCE_THRESHOLD = 0.75`
- `MEDIUM_CONFIDENCE_THRESHOLD = 0.55`
- `initialStopWindowMs = 30_000`
- `confirmationObservationWindowMs ≈ 1_200_000` (slow path, ~20 min)
- `vehicleExitObservationWindowMs ≈ 180_000` (fast path con AR exit, 3 min)
- `repositionSpeedMps = 1.7`
- `minGpsAccuracyForDriving ≈ 30m`

### 3.4 Niveles de respuesta

- **HIGH (≥0.75)** → auto-confirm tras la ventana
- **MEDIUM (≥0.55)** → notificación al usuario preguntando "¿Has aparcado aquí?" con acciones Confirm/Deny
- **LOW** → no se hace nada; reset al observar movimiento

### 3.5 Foreground service

`ParkingDetectionService.kt` arranca en `IN_VEHICLE_ENTER` de Activity Recognition o cuando el usuario inicia sesión y hay vehículo activo. Mantiene viva la observación de GPS adaptativa (`ObserveAdaptiveLocationUseCase`) y los listeners de AR.

- `FOREGROUND_SERVICE_TYPE_LOCATION` (API 30+ obligatorio)
- `START_STICKY` para reanudar tras kill
- Sin `WakeLock` explícito — depende del foreground state

### 3.6 Workers de soporte

| Worker | Disparador | Constraint | Reintentos |
|--------|-----------|------------|-----------|
| `EnrichParkingSessionWorker` | tras `ConfirmParkingUseCase` | ninguna | exp backoff, 3 reintentos |
| `DepartureDetectionWorker` | exit de geofence | ninguna | 3 reintentos; tras max, confirma departure de todas formas (geofence es señal fuerte) |
| `ParkingSyncWorker` | tras confirm o release | `NetworkType.CONNECTED` | exp 30s, hasta 5 |
| `ReportSpotWorker` | tras departure | CONNECTED | exp |
| `LocationUpdateSyncWorker` | `UpdateParkingLocationUseCase` (move pin) | CONNECTED | exp |
| `ClearActiveSyncWorker` | tras release de sesión | CONNECTED | exp |

---

## 4. Problemas conocidos

### 4.1 Críticos

- **iOS Activity Recognition wiring incompleto** — `IosActivityRecognitionManagerImpl.kt` tiene los snapshots y la síntesis de transiciones, pero los TODOs en `onVehicleEntered` / `onStillDetected` impiden que el coordinator reciba las señales (ver `BUGS_AND_DEBT.md` §3)
- **iOS sync no persistente** — `StubParkingSyncScheduler` es no-op. Si la app muere antes de que el coroutine sync termine, la sesión nunca llega a Firestore en iOS (ver `BUGS_AND_DEBT.md` §14)

### 4.2 Altos

- **MIUI / Doze fragility** — sin `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, el foreground service puede ser killed por Xiaomi/Oppo sin "Autostart" habilitado. Test en Redmi Note 11 pendiente (ver `BUGS_AND_DEBT.md` §7)
- **Geofences sin auto-expiry** — pueden quedar huérfanos en el OS si la app muere antes de `removeGeofence()` (ver `BUGS_AND_DEBT.md` §8)
- **`START_STICKY` sin re-check de permisos** — si el usuario revoca background location entre kills, el service intenta operar sin permiso (ver `BUGS_AND_DEBT.md` §9)

### 4.3 Medios

- **Falsos positivos en paradas largas de tráfico** — si el coordinator considera "stopped" sin AR EXIT, puede entrar en CANDIDATE durante un semáforo de 3+ minutos. El reposition speed mitiga pero no elimina
- **Falsos negativos sin BT y AR poco fiable** — usuarios con activity recognition mal calibrada en el OS pueden no pasar nunca a CONFIRMED si el coordinator nunca recibe `IN_VEHICLE → EXIT`

---

## 5. Plan de mejora de precisión

### 5.1 P0 — Hardening del coordinator (Android)

1. **Persistir estado del coordinator** — hoy todo en memoria. Si el service muere y `START_STICKY` lo reanuda, perdemos `bestStopLocation` y `lastVehicleEnteredAt`. Persistir snapshot en `LocalSessionCache` (Room) cada vez que cambia el estado.
2. **Sensor fusion explícito** — exponer el peso de cada señal en `CalculateParkingConfidenceUseCase` (config) para tuning empírico contra logs PARKDIAG.
3. **Doze whitelist flow** — onboarding/permissions screen muestra prompt explícito para añadir a la whitelist en OEMs agresivos.
4. **Periodic heartbeat worker** — cada 15 min, comprueba si el foreground service está vivo; si no y hay sesión activa esperada, lo reactiva.

### 5.2 P1 — Confidence dashboard interno

Pantalla de debug accesible vía 5-taps en Settings: gráfica en vivo de speed/accuracy/AR + score actual + último motivo de reset. Útil para QA y para entender false negatives reportados por usuarios.

### 5.3 P1 — iOS feature parity

1. Cerrar §3 BUGS_AND_DEBT.md (wire AR → coordinator)
2. Reemplazar `StubParkingSyncScheduler` por BGTaskScheduler real (§14 BUGS_AND_DEBT.md)
3. Probar en device real iPhone — el simulador no emite transiciones AR

### 5.4 P2 — ML server-side

Cuando haya volumen de eventos confirmados/denegados:
- Entrenar modelo de scoring (gradient boosting o LSTM sobre secuencias de speed/AR) con eventos etiquetados (`confidence` + `userResponse` Confirm/Deny)
- Privacy-first: agregación federada o sólo señales agregadas, nunca trayectorias
- Bajaría el umbral HIGH efectivo manteniendo precisión

### 5.5 P2 — Modelos por usuario

Algunos usuarios tienen patrones distintos (motorista vs coche, urbano vs interurbano). Pesos adaptativos por usuario tras N detecciones confirmadas.

---

## 6. Compatibilidad MIUI / HyperOS (Redmi Note 11)

El usuario tiene como device principal de QA un Redmi Note 11 (MIUI). MIUI es el sistema más agresivo del mercado contra background services. Acciones específicas:

### 6.1 Checklist in-app
- [ ] Detectar `Build.MANUFACTURER` contains "xiaomi" / "redmi"
- [ ] Mostrar pantalla `MiuiOnboardingScreen` con instrucciones específicas:
  - Settings → Apps → Paparcar → **Autostart ON**
  - Settings → Apps → Paparcar → **Battery saver → No restrictions**
  - Settings → Apps → Paparcar → **Other permissions → Show notifications while in background ON**
- [ ] Idem para Oppo (ColorOS) y Huawei (EMUI) con sus rutas equivalentes
- [ ] Idem para Samsung "Put unused apps to sleep" off

### 6.2 Telemetría
- Loggear cada vez que el foreground service es destruido sin que la sesión esté completada (`onTaskRemoved` y `onDestroy` del service)
- Si OEM en {xiaomi, oppo, huawei, vivo} y la sesión se pierde, dialog post-mortem al volver a abrir la app

### 6.3 Test bench Redmi
- Capturar logs PARKDIAG durante una sesión real (ver `diagnostics/README.md`)
- Verificar que `EnrichParkingSessionWorker` y `ParkingSyncWorker` ejecutan tras el kill
- Documentar tiempos de muerte del service en MIUI con app en background sin whitelist

---

## 7. Test bench

- Unit tests en `commonTest/`:
  - `CalculateParkingConfidenceUseCaseTest` — varía signals, asserta thresholds
  - `DetectParkingDepartureUseCaseTest` — sesión activa + geofence + AR window
  - `ParkingStrategyResolverTest` — BT vs Coordinator decision
  - `ObserveNearbySpotsUseCaseTest`, `SendSpotSignalUseCaseTest`
- Fakes en `commonTest/fakes/`: `FakeDepartureEventBus`, `FakeGeofenceManager`, `FakeGeocoderDataSource`, `FakePermissionManager`, `FakeParkingEnrichmentScheduler`
- **Pendiente**: test unitario de `ParkingDetectionCoordinator` con un `TestDispatcher` que simule una secuencia completa drive → stop → AR EXIT → confirm

---

## 8. Diagnóstico en producción

Sistema PARKDIAG documentado en `diagnostics/README.md`. Capturas históricas en `diagnostics/2026-05-12/`, `2026-05-14/`, `2026-05-18/`.

Flujo:
1. Usuario activa "Modo diagnóstico" en Settings (oculto en debug builds)
2. Logs estructurados se persisten en filesystem vía `FileAntilog`
3. Adb pull → procesado con scripts en `diagnostics/`

---

## Referencias

- `CLAUDE.md` — reglas obligatorias del proyecto y convenciones de detección
- `docs/detection/PARKING-DETECTION.md` — spec algorítmica canónica
- `docs/refactors/PIPE-001-confirm-parking-pipeline.md` — refactor planeado para extraer side-effects del service
- `docs/BUGS_AND_DEBT.md` — bugs §3, §7, §8, §9, §14
- `docs/IOS_PLAN.md` — plan completo iOS
