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

## A · `IN_VEHICLE ENTER` duplicado dispara cancel/restart → coordinator pierde el viaje · ⚪ Pending

**Ticket:** `BUG-DETECT-ENTER-DEBOUNCE-001`
**Prioridad:** Alta — explica 3 de 6 fallos (50%) en el test de ayer.

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

## C · `IN_VEHICLE EXIT` llega muy tarde — Step Detector ya existe pero no auto-confirmó · 🔍 Investigation

**Ticket:** `BUG-DETECT-EXIT-LAG-VS-STEPS-001`
**Prioridad:** Media — afecta UX (notificación premature en mitad del viaje) pero hay workaround natural (siguiente parada).

### Evidencia

Trayecto 5 Oppo:
- `21:39:43` IN_VEHICLE ENTER
- `22:05:06` Notify **High(0.8)** ← HIGH alcanzado, aparcamiento real (parada ATM)
- `22:18:45` IN_VEHICLE EXIT ← **13 minutos tarde**
- `22:20:24` Notify High(0.75) ← segundo HIGH (parada final)
- `22:20:28` **Confirm SUCCESS** ← finalmente confirma, pero con location de la 2ª parada, no del ATM

El usuario confirma: "a las 22:05 nos bajamos del coche, EXIT lo detecto bastante tarde".

### Lo que ya está implementado (revisado en `ParkingDetectionCoordinator.kt:296`)

```kotlin
val confirmNow = when {
    isMismatch -> false
    hasStepsProof -> true                                      // ← Step Detector path
    windowElapsed && state.highCandidateHadVehicleExit -> true // ← AR EXIT path
    else -> false
}
```

El path de Step Detector **ya existe** y tiene precedencia sobre AR EXIT. Si en el trayecto 5 no disparó, las hipótesis son:

**H1.** `stepCount` no llegó a `minStepsToConfirm` durante la parada del ATM. Plausible: 2 min de parada, usuario sale del coche → camina pocos pasos al cajero → vuelve. Si `minStepsToConfirm` está calibrado para una caminata más larga, no se cumple. **Verificar el valor actual en `ParkingDetectionConfig`** y revisar logs del Coord en la ventana 22:05-22:18 buscando `✦ step #N (stopped)`.

**H2.** El candidato HIGH se descartó (`highConfidenceReachedAt = null`) cuando el coche se movió tras el ATM (`isDriving` en `updateStopTracking`). En ese caso los pasos contados también se borran (`stepCount = if (isDriving) 0`). Cuando llegan a la 2ª parada (22:20) ya no hay historia previa — la confirmación es válida pero con la location equivocada (la del re-aparcamiento, no la del aparcamiento real del ATM).

**H3.** Step Detector no estaba registrado / no recibió eventos en Oppo en esa franja. Verificar con `grep stepDetector` en el log.

### Plan de investigación

- 🔍 **C.1** Extraer del log `oppo.log` la franja 22:00-22:25 con TODAS las líneas `PARKDIAG/Coord` (incluyendo scoring, step counts, transiciones de estado). Reconstruir qué decisión tomó el coordinator en cada loc fix.
- 🔍 **C.2** Leer el valor actual de `minStepsToConfirm` en `ParkingDetectionConfig` y evaluar si es realista para una parada de 2 min con poca caminata.
- 🔍 **C.3** Verificar que `StepDetectorSource` esté emitiendo eventos en Android (la implementación `AndroidStepDetectorSource` debe registrar `Sensor.TYPE_STEP_DETECTOR`).
- 🧠 **C.4** Decisión: ¿bajar `minStepsToConfirm`? Trade-off con falso positivo (usuario que se estira en el coche).
- 🧠 **C.5** Decisión: ¿proteger el candidato HIGH durante una ventana corta tras movimiento, en lugar de borrarlo en cuanto el coche acelera? Permitiría capturar el caso "salí del coche, el coche se movió 5 min después en convoy con otro conductor". Probablemente fuera de scope.

### Lo que NO está roto

El Notify a las 22:05 con HIGH(0.8) **es correcto** dado el código actual: política "always notify on HIGH". El "bug" percibido por el usuario es que el Notify llegó *antes* del Confirm — pero esa es la separación deliberada entre **sugerir** y **auto-guardar**. La pregunta real es por qué no se auto-guardó por pasos.

### Files a leer (investigación, sin tocar todavía)
- `composeApp/src/commonMain/kotlin/io/apptolast/paparcar/domain/model/ParkingDetectionConfig.kt` — valores actuales.
- `composeApp/src/androidMain/kotlin/io/apptolast/paparcar/detection/sensor/AndroidStepDetectorSource.kt` (o nombre similar).
- `diagnostics/2026-05-27/oppo.log` líneas 22:00-22:25 completas.

---

## Resumen de prioridades

| # | Ticket | Tipo | Prioridad | Bloqueado por |
|---|---|---|---|---|
| A | `BUG-DETECT-ENTER-DEBOUNCE-001` | Fix de código | **Alta** | — |
| B | `BUG-DETECT-OEM-KILLER-001` | UX + onboarding | Alta | Decisión sobre B.3/B.4 |
| C | `BUG-DETECT-EXIT-LAG-VS-STEPS-001` | Investigación | Media | Análisis de logs C.1-C.3 |

A es el más mecánico y de mayor impacto inmediato (3 de 6 fallos del test). C es el más interesante porque ya tenemos la infraestructura — solo falta verificar por qué no disparó. B requiere conversación de producto.
