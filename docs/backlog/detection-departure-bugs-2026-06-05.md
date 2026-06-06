# Paparcar — Departure detection bugs + arquitectura — 2026-06-05

Origen: test de campo real. Usuario pasó andando junto a su coche aparcado y la plaza se liberó sola.
Segunda reproducción: usuario movió la posición manualmente y al pasar junto al coche también saltó.

## Status legend
✅ **Done** — merged/committed
⚪ **Pending** — not started
🟡 **Blocked / Deferred** — waiting on decision

---

## BUG-WALK-DEPART-001 — Plaza liberada al pasar andando junto al coche · ✅ Fixed 2026-06-05

**Root cause:** dos fallos combinados.

### Fallo 1 — DepartureEventBus nunca se reseteaba al confirmar parking

`DepartureEventBusImpl` almacena en memoria el timestamp del último `IN_VEHICLE_ENTER`.
Ese timestamp lo escribe `ActivityTransitionReceiver` cuando el usuario SE SUBE al coche para ir a aparcar.
Ninguno de los dos casos de uso que crean/recrean la sesión lo borraba:

- `ConfirmParkingUseCase` → guardaba sesión y creaba geofence **sin resetear** el bus.
- `UpdateParkingLocationUseCase` → recreaba el geofence **sin resetear** el bus.

Resultado: el `lastVehicleEnteredAt` del viaje de llegada seguía vivo. Al salir caminando dentro
de los 30 min del `vehicleEnterWindowMs`, el worker veía señal válida y confirmaba salida.

**Fix:** ambos casos de uso llaman `departureEventBus.reset()` tras la operación exitosa.

### Fallo 2 — Fallthrough incondicional tras max retries

`DepartureDetectionWorker` tiene lógica de reintentos (hasta 3) cuando la decisión es `Inconclusive`.
Tras agotar los reintentos, el worker caía siempre a `Confirmed` con el comentario
"geofence exit alone is strong enough evidence". Esto es incorrecto cuando no hay ninguna
señal de vehículo (`lastVehicleEnteredAt == null`) — el proceso fue reiniciado por el OS y el
bus es null — y la velocidad es de paso (~5 km/h).

Escenario real: usuario aparcó ayer → proceso matado overnight → `lastVehicleEnteredAt = null`.
Hoy movió la posición → nuevo geofence creado. Al pasar andando:
- `vehicleEnteredAt = null`, `speed = 5 km/h` → `Inconclusive` × 3 → `Confirmed` (💥 bug)

**Fix:** guard antes del fallthrough:
```kotlin
if (decision == Inconclusive && departureEventBus.lastVehicleEnteredAt == null) {
    return Result.success()  // sin señal de vehículo → rechazar silenciosamente
}
```

El fallthrough solo ocurre si `vehicleEnteredAt != null` (usuario en coche saliendo lento de garaje).

### Cambios del fix

| Archivo | Cambio |
|---|---|
| `ConfirmParkingUseCase` | + `DepartureEventBus` dep, llama `reset()` tras `saveNewParkingSession` OK |
| `UpdateParkingLocationUseCase` | + `DepartureEventBus` dep, llama `reset()` tras `updateParkingSessionPosition` OK |
| `DepartureDetectionWorker` | guard `lastVehicleEnteredAt == null` antes de fallthrough |
| `ProcessConfirmedDepartureUseCase` | **nuevo** — extrae lógica post-confirmación del worker |
| `DomainModule` | actualiza bindings de los 3 casos de uso + registra `ProcessConfirmedDepartureUseCase` |
| `ConfirmParkingUseCaseTest` | 2 tests nuevos de reset del bus; `buildUseCase` acepta `bus` param |
| 5 test files | + `departureEventBus = FakeDepartureEventBus()` en todos los call sites |
| `docs/detection/PARKING-DETECTION.md` | documenta ciclo de vida del bus (reseteo dual) |

### ¿Por qué ProcessConfirmedDepartureUseCase?

El worker anterior contenía lógica de dominio inline (buscar sesión, publicar spot, limpiar sesión,
resetear bus, quitar geofence). Un Worker es capa de infraestructura Android — igual que un Service.
Su responsabilidad es orquestar WorkManager (reintentos, backoff) y delegar a casos de uso.
El nuevo `ProcessConfirmedDepartureUseCase` encapsula todos los side-effects post-confirmación;
el worker queda como ~30 líneas de lógica de WorkManager pura.

---

## ARCH-DEPARTURE-GEOFENCE-DUAL-TRIGGER-001 · ⚪ Pending

**Tipo:** Refactor arquitectónico — mejora robustez del trigger de detección de aparcamiento.

### Contexto

Hoy los dos pipelines tienen triggers asimétricos:

```
LLEGADA (nuevo parking):
  IN_VEHICLE_ENTER → HandleVehicleTransitionUseCase → ParkingDetectionCoordinator arranca

SALIDA (departure):
  GEOFENCE_EXIT → DepartureDetectionWorker
                  + IN_VEHICLE_ENTER como señal de confirmación secundaria
```

`IN_VEHICLE_ENTER` arranca el coordinator para cualquier vehículo en cualquier lugar.
Si alguien recoge al usuario con un taxi desde un sitio distinto al coche aparcado,
el coordinator arrancará — aunque el coche esté quieto y no sea relevante.

### Propuesta

Usar `GEOFENCE_EXIT` como **trigger dual**:

```
GEOFENCE_EXIT
  ├── DepartureDetectionWorker     (ya existe — departure del parking actual)
  └── ParkingDetectionService      (nuevo — arrancar coordinator para el siguiente viaje)
```

Con esto:
- El coordinator solo arranca cuando el coche cruza el radio del geofence del aparcamiento previo
  → el usuario está realmente saliendo con ese coche, no subiendo a un taxi en otro sitio.
- Los dos pipelines quedan alineados con el mismo evento raíz.
- `HandleVehicleTransitionUseCase` en `IN_VEHICLE_ENTER` mantiene el rol de fallback
  para el primer viaje (sin sesión previa → sin geofence → sin GEOFENCE_EXIT posible).

### Lógica bifurcada en `GeofenceBroadcastReceiver`

```
GEOFENCE_EXIT recibido
  ├── si hay sesión activa para ese geofenceId:
  │     → enqueue DepartureDetectionWorker
  │     → start ParkingDetectionService (intent GEOFENCE_TRIGGERED_START)
  └── si NO hay sesión activa:
        → ignorar (geofence fantasma de sesión ya limpiada)
```

O alternativamente, el arranque del service se hace desde dentro de `ProcessConfirmedDepartureUseCase`
(tras confirmar la salida), evitando arrancar el coordinator si la salida es rechazada.

### HandleVehicleTransitionUseCase — rol tras el cambio

Con el nuevo diseño, IN_VEHICLE_ENTER pasa a ser señal de **confirmación** de salida
(ya lo es hoy via `DepartureEventBus`), no trigger primario del coordinator.
El bifurcado pendiente que HVT necesitaría:

```kotlin
// IN_VEHICLE_ENTER
val hasActiveSession = userParkingRepository.hasAnyActiveSession()
if (hasActiveSession) {
    departureEventBus.onVehicleEntered(ms)    // confirmación de salida
    // NO arrancar coordinator — lo arrancó GEOFENCE_EXIT
} else {
    // Sin sesión previa → primer viaje → arrancar coordinator como hoy
    strategyResolver.resolve(vehicle, btEnabled) → start coordinator
}
```

### Trade-offs

| Ventaja | Descripción |
|---|---|
| Simetría | Misma fuente de verdad (GEOFENCE_EXIT) para departure + nuevo viaje |
| Precisión | Coordinator solo arranca para viajes desde el sitio real del coche |
| Taxi elsewhere | Eliminado como falso arranque del coordinator |

| Riesgo | Descripción |
|---|---|
| Primer viaje | Sin sesión previa no hay geofence → fallback a IN_VEHICLE_ENTER necesario |
| Walking start | Caminar fuera del radio arrancaría el coordinator (se detendría rápido por falta de movimiento) |
| Process death | Si el proceso muere entre GEOFENCE_EXIT y el arranque del service, el coordinator no arranca. WorkManager cubre la departure, pero la detección del siguiente viaje dependería del próximo IN_VEHICLE_ENTER |

### Archivos a tocar

- `GeofenceBroadcastReceiver.kt` — arrancar también `ParkingDetectionService` en exit
  (o delegar desde `ProcessConfirmedDepartureUseCase`)
- `HandleVehicleTransitionUseCase.kt` — bifurcación por sesión activa
- `ParkingDetectionService.kt` — nuevo intent action `GEOFENCE_TRIGGERED_START`
- `docs/detection/PARKING-DETECTION.md` — diagrama de triggers actualizado

### Esfuerzo estimado

Mediano (~1 día). Tocar el receptor de geofence y el contrato entre los dos pipelines
requiere tests de integración cuidadosos.

### Estado

⚪ Pending — no bloquea nada. El bug `BUG-WALK-DEPART-001` está resuelto sin este cambio.
Abordar cuando el pipeline de detección esté estabilizado en campo.
