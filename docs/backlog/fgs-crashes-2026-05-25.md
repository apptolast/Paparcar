# Paparcar — FGS Crashes diagnosticados 2026-05-25

Origen: Crashlytics — captura de pantalla del 2026-05-25.
Total: 4 issues en Crashlytics, 2 root causes, todos en el pipeline de detección.

## Status legend
✅ **Done** — merged to master
🔵 **Branch ready** — work complete, awaiting merge
⚪ **Pending** — not started
🔴 **P0** — bloquea beta

---

## BUG-FGS-001 · ForegroundServiceStartNotAllowedException desde BroadcastReceiver ✅

**Ticket:** `BUG-FGS-001`
**Prioridad:** P0 — crítico | **Esfuerzo:** Pequeño–Medio
**Estado:** ✅ Done 2026-05-25

### Crashes cubiertos
| Crash | Evento | Usuarios | Versión |
|-------|--------|----------|---------|
| ActivityTransitionReceiver.startDrivingService (recurrente) | 19 | 7 | 1.0 – beta01 |
| ParkingDetectionService.onStartCommand — ForegroundServiceStartNotAllowedException | 3 | 1 | beta01 |
| ActivityTransitionReceiver.startDrivingService:73 | 2 | 2 | 1.0 |
| **Total** | **24** | **~9** | |

### Root cause

`ActivityTransitionReceiver.startDrivingService()` llamaba a `context.startForegroundService()`
directamente. En **Android 12+ (API 31+)**, el sistema NO concede exención de inicio de FGS en
background a broadcasts de Activity Recognition de Google Play Services — no están en la lista de
senders privilegiados. Resultado: `ForegroundServiceStartNotAllowedException` cuando la app está en
background (que es el 99% del tiempo en que se detecta un evento de conducción).

### Fix implementado (2026-05-25) — PendingIntent.getForegroundService()

El fix provisional documentado aquí (WorkManager bridge) fue **descartado** en favor del fix correcto:

`ActivityRecognitionManagerImpl` ahora registra **dos suscripciones separadas** con la API de Activity Recognition:

1. **STILL_ENTER** → `PendingIntent.getBroadcast()` → `ActivityTransitionReceiver`
   - No necesita FGS. `coordinator.onStillDetected()` es fire-and-forget.

2. **IN_VEHICLE_ENTER + IN_VEHICLE_EXIT** → `PendingIntent.getForegroundService()` → `ParkingDetectionService`
   - Play Services llama `.send()` con privilegios de sistema — bypassa la restricción de Android 12+ sin intermediarios.
   - El intent entregado al servicio contiene el `ActivityTransitionResult` embebido (igual que en un BroadcastReceiver).

`ParkingDetectionService.onStartCommand()` maneja el nuevo `ACTION_VEHICLE_TRANSITION`:
- `startForeground()` siempre primero (contrato Android 8+, 5 s window).
- Guard de permisos (`ACCESS_FINE_LOCATION` + `ACCESS_BACKGROUND_LOCATION`).
- `ActivityTransitionResult.extractResult(intent)` → enruta por tipo de evento.
- IN_VEHICLE_ENTER: `departureEventBus.onVehicleEntered()` + check `strategyResolver.shouldUseCoordinator()` → arranca Coordinator o `stopSelf()` (BT activo).
- IN_VEHICLE_EXIT: `coordinator.onVehicleExit()` → `stopSelf()` si no hay detection job activo.

`ActivityTransitionReceiver` queda simplificado a solo manejar STILL (30 → 3 líneas de lógica real).
`StartDetectionWorker` eliminado — ya no existe el problema que resolvía.

### Archivos modificados
- `ActivityRecognitionManagerImpl.kt` — dos PendingIntents y dos registros.
- `ParkingDetectionService.kt` — nuevo `ACTION_VEHICLE_TRANSITION` + `handleVehicleTransition()` + `startForegroundCompat()`.
- `ActivityTransitionReceiver.kt` — simplificado a STILL only.
- `StartDetectionWorker.kt` — **eliminado**.

---

## BUG-FGS-002 · SecurityException Starting FGS with type location (Android 14) ✅

**Ticket:** `BUG-FGS-002`
**Prioridad:** P0 — crítico | **Esfuerzo:** Trivial
**Estado:** ✅ Done 2026-05-25 — resuelto junto con BUG-FGS-001

### Crashes cubiertos
| Crash | Eventos | Usuarios | Versión |
|-------|---------|----------|---------|
| ParkingDetectionService.onStartCommand — SecurityException Starting FGS with type location | 5 | 1 | beta01 |

### Root cause

**Android 14 (API 34)** lanza `SecurityException` en `startForegroundService()` si el servicio
declarará `foregroundServiceType="location"` y `ACCESS_FINE_LOCATION` no está concedido en runtime.

### Fix implementado

Resuelto automáticamente: con `PendingIntent.getForegroundService()` registrado por el sistema,
el servicio solo se inicia cuando Play Services dispara la transición. `ParkingDetectionService.onStartCommand(ACTION_VEHICLE_TRANSITION)`
llama a `startForeground()` primero y luego a `hasRequiredPermissions()` — si no están concedidos,
`showPermissionRevoked()` + `stopSelf()` + `START_NOT_STICKY`.

---

## Relación con BUG-WORKER-001 y BUG-WORKER-002

Los bugs FGS bloqueaban el **arranque** del servicio de detección. ✅ Resueltos.
Los bugs WORKER bloquean la **sincronización remota** de la sesión. ⚪ Pendientes.
Ver `docs/backlog/worker-bugs-2026-05-25.md`.
