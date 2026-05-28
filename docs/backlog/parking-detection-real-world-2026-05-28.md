# Paparcar — Detección real-world: 3 patrones de fallo encontrados — 2026-05-28

Origen: test de campo del 2026-05-27 con **dos móviles juntos** (Oppo CPH2371 + Redmi Note 11) en la misma ruta a partir de las 18:00. Logs en `diagnostics/2026-05-27/{oppo,redmi-note-11}.log`.

**Marcador del día:** Oppo 3/6 confirmaciones, Redmi 3/6, ambos 1/6 (solo trayecto 2 a las 19:02 confirmaron los dos). Cada móvil falló en trayectos distintos — perfecto para aislar causas porque el viaje fue idéntico.

## Status legend
✅ **Done** — merged
🔵 **Branch ready** — work complete, awaiting commit/merge
⚪ **Pending** — not started
🟡 **Blocked** — waiting on user/decision
🧠 **Decision** — needs reasoning first
🔍 **Investigation** — root cause not confirmed yet

---

## A · `IN_VEHICLE ENTER` duplicado dispara cancel/restart → coordinator pierde el viaje · ✅ Done (2026-05-28)

**Ticket:** `BUG-DETECT-ENTER-DEBOUNCE-001`
**Prioridad:** Alta — explica 3 de 6 fallos (50%) en el test de ayer.
**Shipped en commit `61a024d`** (master). Opción 1 elegida (estado binario `VehicleState.OUT/IN` en `ParkingDetectionService`). Doc actualizada en `docs/detection/PARKING-DETECTION.md` §2.

### Evidencia

| Trayecto | Móvil | Secuencia |
|---|---|---|
| 1 (18:33-18:49) | Redmi | ENTER 18:33:16 → ENTER 18:33:32 (cancel/restart) → ENTER 18:38:43 → ENTER 18:38:45 (cancel/restart) → EXIT 18:52 — **0 Notify, 0 Confirm** |
| 3 (19:31-19:51) | Oppo | ENTER 19:31:08 → ENTER 19:35:50 → ENTER 19:35:52 (cancel/restart) → EXIT 19:51 — **0 Notify, 0 Confirm** |
| 5 (21:39-22:20) | Redmi | ENTER 21:39:42 → coordinator `returned NORMALLY` a las 21:49 (10 min antes del EXIT real) → EXIT 22:19 sin job vivo — **0 Notify, 0 Confirm** |

Los `✗ detection cancelled: StandaloneCoroutine was cancelled` y `■ finally → calling stopSelf()` en plena ráfaga son la pista clara.

### Root cause

`ParkingDetectionService.kt:113`:

```kotlin
if (detectionJob?.isActive != true || !parkingDetectionCoordinator.hasDetectedMovement) {
    detectionJob?.cancel()
    detectionJob = null
    startParkingDetection()
}
```

El guard intenta evitar el restart pero está **mal compuesto**. Restart si `(job inactivo) OR (no movement yet)`. En los primeros segundos tras ENTER `hasDetectedMovement=false` (necesita locations + `minimumTripSpeedMps` + `minimumTripDistanceMeters`), así que **cualquier ENTER duplicado en los primeros segundos** reinicia el job aunque ya esté corriendo. El log `↻ Coordinator already active + hasDetectedMovement=true` (rama del else) **nunca aparece** en la ventana de ayer — el guard nunca cazó nada.

### Fix propuesto

Discutido con el usuario. Distinguir ruido AR (ENTER → ENTER sin EXIT entre medio) de reentrada legítima (ENTER → EXIT → ON_FOOT → ENTER):

**Opción 1 — La más simple (estado binario en el service):**
```kotlin
// ParkingDetectionService
private var currentVehicleState: VehicleState = VehicleState.OUT  // OUT | IN

// En handleVehicleTransition, IN_VEHICLE ENTER branch:
if (currentVehicleState == VehicleState.IN && detectionJob?.isActive == true) {
    PaparcarLogger.d(DIAG, "  ↻ IN_VEHICLE_ENTER ignored — already IN + job active (AR noise debounce)")
    return@launch
}
currentVehicleState = VehicleState.IN
// ... resto del start logic

// En IN_VEHICLE EXIT branch:
currentVehicleState = VehicleState.OUT
```

Ventajas: explícito, debuggeable, cubre el caso "ceda / ralentí" del usuario sin tocar el coordinator.

**Opción 2 — Solo arreglar el guard existente:**
```kotlin
if (detectionJob?.isActive != true) {
    // restart
} else {
    PaparcarLogger.d(DIAG, "  ↻ IN_VEHICLE_ENTER ignored — job already active")
}
```

Más quirúrgico pero pierde el matiz de `hasDetectedMovement` que servía para detectar IN_VEHICLE fantasma del Play Services. Habría que verificar con tests que no regresa el bug que motivó el guard original.

**Recomendación: Opción 1.** El estado binario `currentVehicleState` es más cerca a cómo el usuario lo razona y no degrada el caso de spurious ENTER (porque el `maxNoMovementMs` guard en el coordinator todavía mata sesiones fantasma desde dentro).

### Tests a añadir
- `ParkingDetectionServiceTest`: ENTER duplicado en <30s con job activo → no se cancela, no se reinicia.
- ENTER tras EXIT → restart limpio (reentrada legítima como ATM del trayecto 5).
- ENTER mientras job activo pero `hasDetectedMovement=false` → no restart (caso de los primeros segundos).

### Files a tocar
- `composeApp/src/androidMain/kotlin/io/apptolast/paparcar/detection/service/ParkingDetectionService.kt`
- Tests: nuevo `ParkingDetectionServiceDebounceTest.kt`
- Doc: `docs/detection/PARKING-DETECTION.md` — sección "Service lifecycle" con el nuevo estado binario.

---

## B · Procesos matados por OEM-killer (MIUI / ColorOS) — 0 transiciones IN_VEHICLE registradas · 🟡 Blocked (UX)

**Ticket:** `BUG-DETECT-OEM-KILLER-001`
**Prioridad:** Alta para retención, pero **no es bug de código** — es política del fabricante.

### Evidencia

| Trayecto | Móvil | Síntoma |
|---|---|---|
| 4 (20:36-20:46) | Redmi | **0 actividad PARKDIAG en toda la ventana 20:30-20:55.** Proceso muerto. |
| 6 (01:48-02:05 del 28) | Oppo | Solo `STILL ENTER` a las 02:06 y un `Bootstrap.invoke` completo a las 02:48 (proceso re-creado desde cero). 0 `VEHICLE_TRANSITION`. |

En ambos casos, el otro móvil sí registró el viaje, lo que descarta que el coche se moviese poco — la app fue matada en background por el OEM-killer (Xiaomi MIUI / Oppo ColorOS) mientras el dispositivo estaba en doze.

### Sin solución 100% técnica

Estos OEMs ignoran las recomendaciones de Android y matan foreground services en background si la app no está whitelisted manualmente por el usuario. Confirmado en literatura: dontkillmyapp.com cataloga MIUI y ColorOS como los más agresivos.

### Mitigaciones reales (en orden de impacto)

**1. UX: pantalla guiada de "Permitir autoarranque" para Xiaomi/Oppo** — La más impactante. Detectar `Build.MANUFACTURER` y mostrar instrucciones específicas con screenshots del toggle correcto. Sin esto, ninguna app sobrevive en MIUI más de unas horas en background.

**2. UX: pedir exclusión de optimización de batería** (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) en el flujo de onboarding tras permisos de ubicación. Ya tenemos un `PermissionManager` — añadir este paso.

**3. Backend: geofences como wake-up de respaldo.** El sistema entrega `GeofenceBroadcastReceiver` aunque el proceso esté muerto. Ya creamos geofences alrededor de cada parking confirmado — extender el coordinator para que un geofence ENTER en una zona "conocida del usuario" arranque la detección preventivamente. Útil cuando el usuario vuelve a su coche.

**4. Backend: WorkManager periódico (15 min mín)** como heartbeat que revive `ParkingDetectionService` si fue matado. Drena batería pero garantiza al menos un check cada 15 min.

### Plan

- ⚪ **B.1** Pantalla onboarding "Permitir autoarranque" específica Xiaomi/Oppo. Solo se muestra si `Build.MANUFACTURER` matchea.
- ⚪ **B.2** Trigger `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` tras `ACCESS_BACKGROUND_LOCATION`.
- 🧠 **B.3** Decisión: ¿añadimos WorkManager heartbeat? Coste de batería vs ganancia de cobertura. Probablemente postponer hasta ver datos de retención reales tras B.1+B.2.
- 🧠 **B.4** Decisión: ¿geofences en "zonas del usuario" (casa/trabajo)? Necesita data — postponer.

### Files a tocar (cuando empecemos)
- `composeApp/src/androidMain/kotlin/io/apptolast/paparcar/presentation/onboarding/` — nueva pantalla `AutostartGuideScreen`.
- `composeApp/src/androidMain/kotlin/io/apptolast/paparcar/permission/` — extender `PermissionManager` con `requestIgnoreBatteryOptimizations`.

---

## C · `IN_VEHICLE EXIT` llega muy tarde — Step Detector ya existe pero no auto-confirmó · ✅ Closed — No es bug (2026-05-28)

**Ticket:** `BUG-DETECT-EXIT-LAG-VS-STEPS-001`
**Resolución:** El algoritmo funcionó correctamente. El log demuestra que el `minStepsToConfirm=8` rechazó un falso positivo y luego confirmó el aparcamiento real en 4 segundos.

### Cronología real del trayecto 5 (reconstruida del Oppo log)

| Hora | Evento | Decisión del algoritmo |
|---|---|---|
| ~21:39 | IN_VEHICLE ENTER — arranca sesión | Coordinator inicia |
| **22:00-22:08** | Parada larga (~8 min). El usuario y acompañantes vieron a un amigo y se quedaron charlando en el coche sin bajarse. | HIGH(0.8) alcanzado, entró en CANDIDATE. `steps=5/8` (5 pasos espurios por vibración/movimiento dentro del coche). **NO confirma — rechazado falso positivo ✅** |
| 22:08 | Coche resume movimiento | CANDIDATE se cancela limpiamente |
| 22:19:22 | IN_VEHICLE EXIT — aparcamiento real | `vehicleExit=true` |
| 22:19:24 → 22:20:24 | Usuario camina al portal: 90 steps en 1 minuto | Step Detector emite eventos correctamente |
| 22:20:24 | HIGH(0.75) con `vehicleExit=true` | Entra CANDIDATE phase |
| **22:20:28** | `CANDIDATE confirmed via steps — steps=90/8` | `confirmParking(reliability=0.9)` ✅ **4 segundos desde HIGH** |

### Por qué la percepción inicial fue equivocada

El usuario recordó "a las 22:05 nos bajamos del coche" pero el log demuestra que a las 22:05 estaban **parados con un amigo dentro del coche sin salir**. El verdadero aparcamiento fue a las 22:19-22:20. La memoria humana fusionó los dos stops.

### Validación del diseño

Este trayecto es **evidencia directa de que el sistema discrimina correctamente**:

1. **Gate `minStepsToConfirm=8` cumple su propósito**: a las 22:00 el algoritmo estuvo a punto de confirmar un falso positivo (semáforo / parada social con HIGH score por 5 min de inmovilidad + speed=0 + accuracy excelente). Los 5 pasos espurios contados no llegaron al threshold → **no se guardó plaza fantasma**.
2. **Step Detector funciona en campo**: en el aparcamiento real registró 90 steps en 60 s mientras el usuario caminaba al portal → confirmación inmediata.
3. **Latencia de confirmación final = 4 segundos** (22:20:24 HIGH → 22:20:28 SUCCESS).

### Lo que NO es bug

- El Notify a las 22:05 con HIGH(0.8) **es correcto** dado el código actual: política "always notify on HIGH". Es una **sugerencia** al usuario, no un guardado. La separación Notify (sugerir) vs Confirm (auto-guardar) **funciona como diseñada**.
- El gate de 8 steps no es demasiado alto — es lo que evita que paradas sociales / atascos largos guarden plazas fantasma.

### Conclusión

Cerrar el ticket sin cambios de código. Mantener el valor `minStepsToConfirm=8` y el doble path `hasStepsProof || (windowElapsed && highCandidateHadVehicleExit)` exactamente como está.

---

## Resumen de prioridades

| # | Ticket | Tipo | Estado | Bloqueado por |
|---|---|---|---|---|
| A | `BUG-DETECT-ENTER-DEBOUNCE-001` | Fix de código | ✅ Done (`61a024d`) | — |
| B | `BUG-DETECT-OEM-KILLER-001` | UX + onboarding | 🟡 Blocked | Decisión sobre B.3/B.4 |
| C | `BUG-DETECT-EXIT-LAG-VS-STEPS-001` | Investigación | ✅ Closed — no bug | Validado en campo |

A se mergeó y resuelve los 3/6 fallos por ENTER duplicado. C resultó ser un falso positivo correctamente rechazado por el algoritmo (gate `minStepsToConfirm=8` funcionando). Pendiente real: B, que requiere conversación de producto sobre la UX de autoarranque en MIUI/ColorOS.
