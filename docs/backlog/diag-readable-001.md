# DIAG-READABLE-001 — Diagnósticos de campo legibles de un vistazo

**Rama:** `feature/DIAG-READABLE-001` · **Estado:** implementado, compila (`compileProdDebugKotlinAndroid` ✓), pendiente merge + device.

## Motivación
En el field-test del 12-07 (Oppo + Redmi, mismo coche, misma cuenta imposible de distinguir) el análisis costó de más porque las sesiones de `diagnostics/{uid}/sessions/{id}`:
1. **No dicen de qué dispositivo son** → hubo que triangular por garaje (`users/{uid}/vehicles`) y por la ubicación del ancla del FP. Frágil.
2. **Sesiones huérfanas** (`outcome=null`, sin `SESSION_ENDED`) no se distinguen de las abiertas.
3. Para saber velocidad máx / pasos / ancla final había que **bajar 400 eventos** de la subcolección.

## Cambio (observabilidad pura — NO toca la lógica de detección)
El `FirestoreDetectionEventLogger` ya drena todos los eventos; ahora **él mismo acumula un rollup por sesión** y lo vuelca en la cabecera al recibir `SESSION_ENDED`. Cero cambios en Coordinator/UseCases.

### Nuevos campos en la cabecera `DetectionSessionDto`
- **Identidad:** `deviceModel`, `appVersion`, `osVersion` (estampados en `SESSION_STARTED` vía `DeviceInfoProvider`).
- **Rollup (en `SESSION_ENDED`):** `endedAt`, `maxSpeedKmh`, `drivingFixes` (nº fixes ≥18 km/h), `fixCount`, `maxStepCount`, `finalLat`, `finalLon`, y `summary` (string legible).

### Ejemplo de `summary`
```
aborted_no_movement · 4.3min · vmax 0km/h · drive 0/30fix · steps 0 · end 36.60388,-6.23032
confirmed_steps+egress · 12.9min · vmax 107km/h · drive 20/136fix · steps 252 · end 36.60388,-6.23035
```
El mismo `summary` se espeja al log local (`PaparcarLogger.i`) → sale también en logcat.

## Archivos
- `domain/diagnostics/DeviceInfoProvider.kt` (interfaz + `UnknownDeviceInfoProvider`).
- `diagnostics/AndroidDeviceInfoProvider.kt` (Build.MANUFACTURER+MODEL, packageInfo, SDK).
- `iosMain/.../diagnostics/IosDeviceInfoProvider.kt` (UIDevice + NSBundle) — paridad, no compila en Windows.
- `data/.../dto/DetectionEventDto.kt` (`DetectionSessionDto` + campos, backward-compatible: todos nullable).
- `data/.../FirestoreDetectionEventLogger.kt` (rollup + flush + stamp + mirror local).
- DI: `DataModule` (`deviceInfo = get()`), `AndroidPlatformModule`, `IosPlatformModule`. Mock usa `NoOpDetectionEventLogger` → no afectado.

## Pendiente (no incluido — requiere tocar detección, va por su cuenta)
- **`arm_<ms>` estructurado:** hoy los docs de armado meten todo en el string `strategy`
  ("ARM:GEOFENCE_EXIT (geof=… d=… acc=… exitLoc=… dep=…)"). Restructurar a campos
  (`armTrigger`, `armGeofenceId`, `armDistanceM`, `armAccuracyM`, `armExitLat/Lon`, `departureEvidence`).
- **Never-silent SESSION_ENDED:** las sesiones huérfanas (`outcome=null`) son un bug de ciclo de vida
  de detección, no de logging → va en el ticket de "detección nunca en silencio" (watchdog + notify).
