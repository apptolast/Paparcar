# Paparcar — Detección de aparcamiento: mejoras + bugs reales — 2026-05-27

Origen: sesión de análisis y refactor del 2026-05-27. Sale del primer test de campo en Redmi (Oppo) + feedback de otros dos usuarios.

Este documento agrupa:
1. **Refactor técnico ya shippeado** (Service / Coordinator / Receiver) — referencia, no acción.
2. **Decisiones pendientes** — pendientes de razonar antes de implementar.
3. **Bugs funcionales reales reportados** — con análisis y propuestas.
4. **Nueva feature** — Home parking marker con geocerca + auto-detección.

## Status legend
✅ **Done** — merged
🔵 **Branch ready** — work complete, awaiting commit/merge
⚪ **Pending** — not started
🟡 **Blocked** — waiting on user/decision
🧠 **Decision** — necesita razonamiento previo

---

## 0 · Refactor de limpieza del flujo de detección — ✅ Done

**Commit:** `935e6fc` (2026-05-27).

**Resumen de lo que toca:**
- `ParkingDetectionCoordinator.kt` — `collectLatest` → `collect` (M1); `update {} + .value` → `updateAndGet {}` (M2); eliminado `withContext(NonCancellable)` redundante alrededor de `notifyParkingConfirmation` (era necesario solo por la cancelación del `collectLatest`).
- `ParkingDetectionService.kt` — Extraído `guardPermissions(actionLabel)` (C2): consolida los tres checks duplicados en START_TRACKING, VEHICLE_TRANSITION e IN_VEHICLE_ENTER.
- `ActivityRecognitionLabels.kt` — Nuevo archivo común con `activityLabel()` y `transitionLabel()` (M3). Eliminadas las duplicaciones inline en `ParkingDetectionService` y `ActivityTransitionReceiver`.
- `ActivityRecognitionManagerImpl.kt` — `STILL_REQUEST_CODE` y `VEHICLE_REQUEST_CODE` co-localizados en el manager (M4). Eliminada constante huérfana en `ActivityTransitionReceiver`.

**Lo que NO se tocó (pendiente de definición):**
- C1 — cuándo matar el service (ver sección 1) · 🟡 deferred.
- O2 — fusionar BT detection con Coordinator (ver sección 2) · 🟡 deferred.

---

## 1 · Decisión pendiente — Lifecycle del Service · 🧠

**Ticket:** `DECISION-SERVICE-LIFECYCLE-001`
**Prioridad:** Media (no bloquea, pero afecta a batería + experiencia)

**Pregunta:** ¿Cuándo tiene sentido matar `ParkingDetectionService`?

**Estado actual (lo que hace hoy):**
- Se inicia en `IN_VEHICLE_ENTER` (PendingIntent foreground service desde Play Services) o `START_TRACKING` manual.
- Vive mientras el `detectionJob` esté activo.
- Se mata vía `stopSelf()` en estos casos:
  - `IN_VEHICLE_EXIT` (si `detectionJob` no está activo).
  - `STOP_TRACKING` explícito.
  - `finally` del job de detección (siempre cuando el coordinator termina).
  - Permission revoked.
- `START_STICKY` → el framework lo resucita si el OS lo mata bajo presión de memoria.

**Tensión:**
- **Vivir mucho** → drena batería, riesgo de matar la app en background por OEM aggressive killers (Xiaomi/Oppo), notificación FGS visible más tiempo del necesario.
- **Vivir poco** → perdemos eventos de transición de actividad (`STILL` puede llegar bastante después de aparcar; si matamos el service antes, perdemos la confirmación).

**Opciones a evaluar:**
- **A. Mantener actual:** vive hasta que el coordinator devuelva normal (parking confirmado o reset).
- **B. Vivir solo durante "ventana activa":** matar más agresivamente tras `IN_VEHICLE_EXIT` aunque el job esté activo — confiar en que `STILL` re-arrancará el receiver.
- **C. Doble vida:** service ligero en background siempre (sin notificación FGS) + service pesado solo durante detección activa. *Probable violación de la política Android 12+ FGS.*

**Datos que necesitamos antes de decidir:**
- Tiempo medio entre `IN_VEHICLE_EXIT` y `STILL` confirmado en tests reales.
- ¿Cuántas veces el coordinator termina con `reset` vs `confirm`?
- ¿Cuántas veces el OS resucita el service vía `START_STICKY` por minuto?

**Acción:** instrumentar telemetría de duración del service en un sprint corto, decidir con datos.

---

## 2 · Decisión pendiente — Fusionar BT detection con Coordinator · 🧠

**Ticket:** `DECISION-MERGE-BT-COORDINATOR-002`
**Prioridad:** Alta (afecta arquitectura del dual-strategy completo)

**Pregunta del usuario:**
> "Aún queda por valorar si vamos a fusionar la detección por bluetooth con la del coordinator, ya que al tener bluetooth creo que no es necesaria tanta lógica ya que sabemos dónde está el vehículo cuando se conecta/desconecta."

**Hipótesis:** cuando hay BT pareado y activo, la señal es tan determinista (BT disconnect → walking ≥ 30 m → confirmar) que cualquier scoring/Activity Recognition adicional es ruido.

**Estado actual:**
- `BluetoothDetectionStrategy` y `CoordinatorDetectionStrategy` son completamente independientes — no se mezclan señales (regla explícita en `CLAUDE.md`).
- El resolver elige UNA en función de `vehicle.bluetoothDeviceId != null && isBluetoothEnabled`.

**Opciones:**
- **A. Mantener separación estricta (status quo).** Sin cambios. Cada estrategia es testeable de forma aislada y la lógica BT no contamina el scoring.
- **B. Fusión total:** un solo `ParkingDetectionCoordinator` que recibe BT signals como input adicional. Riesgo: complejidad del coordinator crece exponencialmente. Más difícil de razonar.
- **C. Compartir solo el "post-procesamiento":** ambas estrategias terminan en `ConfirmParkingUseCase` (ya hoy). Eliminar duplicación en el "candidate fix + walking confirmation" extrayendo un helper común (`WaitForUserToWalkAwayUseCase`). El coordinator no toca BT.

**Recomendación previa (a confirmar):** **C** es el camino. La separación de estrategias es buena arquitectura; lo que hay que eliminar son duplicaciones tácticas (sample de GPS hasta accuracy bar, esperar a que el usuario camine ≥ 30 m). No mezclar señales, solo extraer helpers.

**Acción:** revisión técnica conjunta antes de decidir.

---

## 3 · BUG-GARAGE-COLA-001 — Falso positivo aparcando en cola de parking · 🔵 Branch ready

**Origen:** reportado por usuario real.

### 3.1 Casos reales que tenemos que cubrir

**Caso A — Garaje de casa con puerta automática.**
- Usuario llega calle ↓ velocidad de 25 km/h a 0 frente al portón.
- Espera ~30-60 s a que la puerta abra (motor encendido, sigue dentro del coche).
- Entra a 5 km/h, baja la rampa, aparca dentro a 0 km/h.
- Sale del coche, camina 4 m al ascensor.

**Caso B — Parking público de pago, cola del ticket.**
- Usuario llega a 30 km/h, se para en cola (motor encendido, sigue dentro).
- Espera 2 min en cola. La cola avanza a 2 km/h cada 15 s.
- Tras sacar el ticket, conduce 1 min más a su plaza, aparca.
- Sale del coche, camina ~20 m al ascensor.

**Caso C — Atasco severo / semáforo largo.**
- Coche parado 90 s, motor encendido, usuario dentro. Igual que Caso A/B pero no aparca: vuelve a moverse y continúa viaje.

En los tres casos la app **hoy** dispara `STILL ENTER` y entra en la lógica de aparcamiento. En A y B termina publicando una plaza falsa donde no debería; en C ya hemos mitigado parcialmente con BUG-3 (sin EXIT/STILL después, no notificamos Low/Medium), pero la lógica High puede dispararse igual si el coche se queda parado mucho rato.

### 3.2 Por qué la propuesta "walking ≥ 30 m" NO es suficiente

El usuario lo señaló: la distancia caminada es muy variable.
- En el Caso A (garaje casa) el usuario camina **4 m al ascensor**. Si exigimos 30 m, perdemos el aparcamiento real.
- En el Caso B (cola parking público) el usuario camina **20 m al ascensor** tras aparcar. También por debajo del umbral.
- Y al revés: un usuario que aparca y va andando a una tienda recorre 200 m sin que eso confirme nada nuevo.

La distancia caminada **no es** la señal correcta. El tiempo y la velocidad tampoco son discriminantes fiables — un parado de 60 s puede ser un semáforo o el comienzo de un aparcamiento.

### 3.3 La señal correcta: ¿el usuario ha salido del coche?

Lo único que distingue **inequívocamente** una cola de un aparcamiento real es:

> **El usuario sale del coche y empieza a caminar.**

En la cola/espera, el usuario sigue dentro del coche. En el aparcamiento real, sale y da pasos. Esto es independiente de:
- Distancia recorrida (puede ser 4 m al ascensor).
- Velocidad previa (puede haber llegado a la plaza a 5 km/h).
- Tiempo de parada (puede haber esperado 30 s o 5 min).

### 3.4 Tres señales que indican "usuario fuera del coche", por orden de fiabilidad

**a) Step Detector (`Sensor.TYPE_STEP_DETECTOR`)** — sensor hardware del teléfono. Dispara un evento por cada paso real. No requiere GPS, no se confunde con coche en marcha (los pasos del coche no son pasos del usuario porque el sensor mide aceleración + cadencia humana). **Permiso necesario:** `ACTIVITY_RECOGNITION` (ya lo tenemos).

**b) Bluetooth disconnect del coche (si pareado)** — ya cubierto por `BluetoothDetectionStrategy`. Aquí es complementario: si llega un BT disconnect mientras estamos en el flujo del Coordinator, confirmar.

**c) `ActivityTransition` ENTER de `WALKING` / `ON_FOOT`** — fallback si el step detector no está disponible (hardware viejo). Menos preciso pero suficiente.

### 3.5 Propuesta refinada

Reescribir el sub-flujo del Coordinator tras `STILL ENTER` así:

1. **Detectar `STILL ENTER`** → entrar en estado `Candidate` (igual que hoy).
2. **Suscribirse al `StepDetector` (sensor)** durante una ventana de `CANDIDATE_VALIDATION_WINDOW_MS = 180_000` (3 min).
3. **Tres salidas posibles:**
   - **Pasos detectados (≥ `MIN_STEPS_TO_CONFIRM = 8`):** confirmar aparcamiento. El usuario ha salido del coche.
   - **`IN_VEHICLE_ENTER` durante la ventana:** descartar — era una cola/parada técnica. Reset state.
   - **Timeout de 3 min sin pasos ni IN_VEHICLE:** fallback al scoring actual (con menor reliability — modo "podría ser aparcamiento real con el teléfono dentro del coche", ej: usuario aparcó y se olvidó el móvil dentro). Confidence MEDIUM como máximo.

Esto cubre los tres casos:
- Caso A: tras llegar abajo del garaje, usuario sale del coche → step detector dispara → confirmar. Aunque solo camine 4 m, los 8 pasos llegan en ~5 s.
- Caso B: cola → no hay pasos (sigue dentro) → IN_VEHICLE_ENTER cuando reanuda la cola → descartar.
- Caso C: atasco/semáforo → no hay pasos → IN_VEHICLE_ENTER cuando reanuda marcha → descartar.

### 3.6 Bonus: combinar con Vehicle Exit (cuando esté disponible)

Si llega `IN_VEHICLE_EXIT` durante la ventana, no hay que esperar al step counter — confirmar directamente. La API de Activity Recognition es ruidosa en EXIT (a veces tarda 30+ s, a veces no llega) por lo que NO podemos depender de ella exclusivamente, pero cuando llega, es una señal más temprana que los pasos.

### 3.7 Trade-offs

- **+** Step Detector es muy preciso (false positive rate < 1% según specs Android) y no consume batería significativa.
- **+** No añade permisos nuevos.
- **+** Resuelve A y B sin tocar BT strategy ni cambiar umbrales de scoring.
- **−** Hardware viejo (pre-API 19) podría no tener `Sensor.TYPE_STEP_DETECTOR`. Fallback a `WALKING ENTER` de Activity Recognition.
- **−** Si el usuario aparca y deja el móvil dentro del coche (raro, pero ocurre), no detectamos pasos. El timeout + fallback al scoring lo cubre con confidence MEDIUM.

### 3.8 Esfuerzo + alcance

**Esfuerzo:** Mediano (~1.5 días).

**Tickets sub-divisibles:**
| Ticket | Descripción |
|--------|-------------|
| `BUG-GARAGE-COLA-001a` ✅ | `StepDetectorSource` (common interface) + `AndroidStepDetectorSource` (`Sensor.TYPE_STEP_DETECTOR` via `callbackFlow`) + `IosStepDetectorSource` (emptyFlow stub) |
| `BUG-GARAGE-COLA-001b` ✅ | Coordinator: sibling step-listener job + `stepCount` en `ParkingDetectionState`; CANDIDATE confirm si `stepCount ≥ minStepsToConfirm`. Slow path expirado sin steps/exit → descartar candidate |
| `BUG-GARAGE-COLA-001c` ✅ | Koin wiring (Android + iOS + DomainModule); reset de `stepCount` cuando llega señal de driving |
| `BUG-GARAGE-COLA-001d` ✅ | `FakeStepDetectorSource` añadido; `ParkingDetectionCoordinatorTest.setup()` pasa el fake. Tests de step-driven confirm requieren CANDIDATE phase (time-driven) → deferred a integration test |

**Notas de implementación (2026-05-27):**
- `minStepsToConfirm = 8` con reliability `reliabilityVehicleExit = 0.90f` cuando confirma por steps.
- Reescritura del slow path: la ventana de 5 min ya no auto-confirma sola; requiere steps O vehicle-exit, si no descarta candidato (likely cola/atasco).
- Reset de `IN_VEHICLE_ENTER como cancelación`: en lugar de cancelar la ventana, se reusa el reset existente cuando llega un driving signal (speed ≥ 2.5 m/s + accuracy ≤ 50 m) — limpia `stepCount`, `highConfidenceReachedAt`, etc.
- Permiso `ACTIVITY_RECOGNITION` ya estaba pedido para AR transitions → cubre Step Detector sin cambios en manifest.
- iOS: `IosStepDetectorSource` devuelve `emptyFlow()`; integración con `CMPedometer` se rastrea en `docs/backlog/ios-detection-2026-05-27.md` cuando llegue (CMPedometer no es step-event-stream — necesitará polling diferencial).

---

## 4 · BUG-SCOOTER-001 — Patín eléctrico clasificado como IN_VEHICLE · ⚪

**Origen:** reportado por usuario real.

### 4.1 El caso real

Usuario tiene registrado un Ford Focus (su vehículo principal, `isDefault = true`). Puntualmente coge su patín eléctrico (≤ 25 km/h legal en España). Activity Recognition lo clasifica como `IN_VEHICLE`. Al detenerse → STILL → publicamos una plaza falsa del Ford Focus en un sitio donde no está.

**El twist clave que el usuario señaló:**
> "Se puede dar el caso que esté mi Ford Focus activo pero coja el patín puntualmente."

Por tanto, **no podemos asumir que `vehicleType == CAR` significa que el viaje actual es en coche**. El active vehicle puede no corresponder con lo que el usuario está usando ahora mismo.

### 4.2 Por qué el filtro simple "velocidad máxima ≤ 30 km/h" no es suficiente

El usuario también señaló (textual):
> "Durante un rato es normal ir a 25 en coche pero no un tramo tan largo."

Esto descarta el filtro ingenuo. En un Ford Focus por ciudad es perfectamente posible:
- 5 min a < 25 km/h en una zona 20 + un atasco.
- Salir de un aparcamiento a 5 km/h, recorrer 200 m antes de subir a 50 km/h.

Si ponemos un filtro "vel max ≤ 30 km/h → no es coche", romperíamos detecciones legítimas de aparcamiento tras un trayecto corto urbano.

**La diferencia real entre coche y patín no es el pico de velocidad, sino la distribución sostenida.**

### 4.3 Análisis de la distribución de velocidad

Datos de referencia (literatura + observación):

| Tipo de viaje | Vel media | Vel máxima típica | Vel P90 (90% del tiempo ≤) |
|---|---|---|---|
| Coche urbano denso | 15-25 km/h | 50+ km/h | 35 km/h |
| Coche urbano fluido | 25-40 km/h | 60+ km/h | 50 km/h |
| Coche interurbano | 60-90 km/h | 110+ km/h | 90 km/h |
| Patín eléctrico (legal ES) | 20-25 km/h | 25 km/h | 25 km/h |
| Bici eléctrica | 18-25 km/h | 25-30 km/h | 25 km/h |

**Lo distintivo del patín no es la velocidad media (que se solapa con coche urbano) sino el **techo plano**: nunca supera 25-28 km/h, mientras que un coche, incluso en ciudad, **eventualmente** mete acelerador y supera 30-35.**

**Heurística que sí funciona:**
> En una ventana ≥ N minutos, si el coche **nunca** ha superado un cierto umbral de velocidad, probablemente no es un coche.

### 4.4 Propuesta combinada (4 niveles)

#### Nivel 1 — Vehicle type explícito + opt-out de Activity Recognition

Añadir `vehicleType: VehicleType` al modelo `Vehicle` con valores `{ CAR, MOTORCYCLE, SCOOTER, BIKE }`.

- `CAR` / `MOTORCYCLE` → detección automática habilitada (Activity Recognition + BT).
- `SCOOTER` / `BIKE` → detección automática **deshabilitada**. Solo BT (si lo tiene) o registro manual.

**Esto resuelve el caso "el usuario cogió hoy el patín y el patín está activo"**, pero no el del usuario.

#### Nivel 2 — Smart confirmation antes de publicar plaza (red de seguridad para el twist)

Cuando el Coordinator está a punto de confirmar un aparcamiento, **analizar la sesión completa** (desde IN_VEHICLE_ENTER hasta STILL):

```
if (sessionDurationMs >= 8 * 60 * 1000      // sesión ≥ 8 min
    && maxSpeedKmh <= 28.0                  // nunca superó 28 km/h
    && activeVehicle.vehicleType == CAR) {  // el coche activo es coche
    // perfil sospechoso de "no es un coche real"
    showVehicleMismatchPrompt(activeVehicle, candidateLocation)
    return  // no publicar plaza directamente
}
```

`showVehicleMismatchPrompt()`:
> *"¿Acabas de aparcar tu Ford Focus o estabas en otro vehículo? La velocidad sugiere que no era un coche."*
> Opciones: `[Sí, era el Ford Focus]` / `[Estaba en otro]` / `[Cancelar]`

- `Sí` → confirmar aparcamiento normal.
- `Otro` → no publicar plaza, opcionalmente abrir selector de vehículo para registrar el aparcamiento contra el patín (si lo tiene registrado) o descartar.
- `Cancelar` → descartar sin publicar.

**Por qué ≥ 8 min y P de 28 km/h:**
- 8 min es suficiente para que CUALQUIER trayecto urbano real toque al menos 30 km/h en algún tramo (semáforo verde + aceleración).
- 28 km/h da margen de error sobre el límite legal del patín (25 km/h) — un patín en bajada puede llegar a 27-28 km/h ocasionalmente, no más.

Esto **NO** rompe el caso del usuario yendo al supermercado en coche por ciudad densa, porque incluso en un trayecto de 8 min en coche urbano el P95 supera 28 km/h casi seguro. Lo verificamos con telemetría antes de hacer la regla más estricta.

#### Nivel 3 — Pista visual en el aparcamiento (post-confirmación)

En la pantalla de Home / detalle del aparcamiento, si el perfil de velocidad de la sesión fue anómalo (`maxSpeedKmh ≤ 28`), mostrar un badge sutil "*Velocidad inusual para coche*" con un botón "Cambiar vehículo / borrar". Es una segunda oportunidad para corregir.

#### Nivel 4 — Cruce con `ON_BICYCLE` (señal complementaria, sin uso autónomo)

Google Activity Recognition a veces oscila entre `IN_VEHICLE` y `ON_BICYCLE` para patines/bicis eléctricas. Si en la ventana reciente hubo eventos de `ON_BICYCLE` con confianza > 50, **aumentar la probabilidad** de mostrar el prompt del Nivel 2 — pero **nunca** descartar la detección automáticamente solo por esto (la API es ruidosa también en el sentido contrario).

### 4.5 Recomendación

**Combo recomendado: Nivel 1 + Nivel 2.**

- Nivel 1 resuelve el caso limpio (usuario con patín registrado y activo).
- Nivel 2 resuelve el twist del usuario (Ford Focus activo + patín puntualmente).
- Nivel 3 y 4 son refinamientos posteriores.

### 4.6 Esfuerzo + tickets

| Ticket | Descripción | Esfuerzo | Estado |
|--------|-------------|----------|--------|
| `BUG-SCOOTER-001a` | `VehicleType` enum + campo en Room + Firestore mapper + migration | Pequeño-Medio | ✅ Done — MIGRATION_3_4 (schema v4), `vehicle_type` column con default `'CAR'`, Firestore `ifBlank → CAR` |
| `BUG-SCOOTER-001b` | UI de selección de vehicleType en registro/edición de vehículo | Pequeño | ✅ Done — `VehicleTypeSelector` (4 tipos sueltos: CAR/MOTORCYCLE/SCOOTER/BIKE), strings i18n en 9 locales |
| `BUG-SCOOTER-001c` | `ParkingStrategyResolver` ignora vehículos no-CAR/MOTORCYCLE (early-return) | Pequeño | ✅ Done — `ParkingStrategy { NONE, BLUETOOTH, COORDINATOR }` enum; `ParkingDetectionService` ramifica con `when` |
| `BUG-SCOOTER-001d` | Tracking de `maxSpeedKmh` + `sessionDurationMs` en `ParkingDetectionState`. Cálculo de P95 de velocidad como signal del state. | Pequeño | ✅ Done — `sessionStartMs` + `maxSpeedMps` en state, helpers `maxSpeedKmh`/`sessionDurationMs(now)` |
| `BUG-SCOOTER-001e` | `showVehicleMismatchPrompt` + intercepción en `ConfirmParkingUseCase` antes de publicar | Medio | ✅ Done (parcial) — guard de mismatch en `ParkingDetectionCoordinator.confirmNow`: si CAR + sesión ≥ 8 min + maxSpeedKmh ≤ 28 → no auto-confirm (cae al prompt MEDIUM). Umbrales en `ParkingDetectionConfig.mismatchMaxSpeedKmh` / `mismatchMinSessionDurationMs` |
| `BUG-SCOOTER-001f` | Tests del prompt + del path "Otro vehículo" | Pequeño | ✅ Done — `ParkingStrategyResolverTest` cubre NONE (SCOOTER/BIKE) + COORDINATOR (MOTORCYCLE) + BLUETOOTH. Tests time-driven del mismatch deferidos a integración futura |

### 4.7 Notas de implementación

- **`maxSpeedKmh` ya existe** (parcialmente) en `ParkingDetectionState`. Verificar y reusar antes de añadir tracking nuevo.
- **Telemetría previa**: antes de hardcodear los umbrales (8 min / 28 km/h), idealmente logear estos valores para sesiones confirmadas durante un sprint, ver la distribución real y ajustar.
- **El prompt no es bloqueante**: si el usuario lo ignora, tras un timeout corto (ej. 60 s) se elimina la notificación y NO se publica la plaza por defecto (fail-safe — preferimos no publicar nada que publicar una plaza falsa).

---

## 5 · FEAT-HOME-PARKING-001..004 — Marcador "mi parking de casa" con geocerca · ✅ Done 2026-06-05

**Estado:** Diferido hasta completar BUG-GARAGE-COLA-001 + BUG-SCOOTER-001 (decisión usuario 2026-05-27).

**Ticket family:** `FEAT-HOME-PARKING-001..004`
**Origen:** propuesta del usuario en sesión 2026-05-27.

### 5.1 Descripción funcional

El usuario tiene un parking habitual (su garaje, plaza alquilada, etc.) y quiere marcarlo en el mapa. Cuando entre en la geocerca de ese marcador con el coche, la app:
1. Detecta automáticamente que está en su parking marcado.
2. Establece el vehículo como aparcado en ese punto exacto (el del marcador, no el GPS actual — más preciso, especialmente bajo techo).
3. Envía notificación de confirmación: *"¿Has aparcado en tu parking?"*
4. Si el usuario confirma → se crea el `UserParking` con la ubicación del marcador.
5. Si deniega → se descarta y se sigue el flujo normal (puede que esté visitando a un vecino).

### 5.2 Por qué es importante

- **Resuelve parcialmente BUG-GARAGE-COLA-001:** en casa, la cola del garaje queda dentro de la geocerca → la detección "automática por scoring" se sustituye por "automática por geocerca" → no hay falso positivo.
- **Mejor precisión bajo techo:** GPS dentro de un garaje subterráneo es horrible. Si tenemos el marcador del usuario, sabemos exactamente dónde está el coche sin depender de GPS.
- **UX:** el usuario aparca en su casa todos los días — confirmar manualmente sería tedioso, pero un solo tap en la notificación es suave.

### 5.3 Modelo de datos

```kotlin
// Nueva entidad
data class HomeParkingMarker(
    val id: String,
    val userId: String,
    val vehicleId: String?,  // null = aplica a cualquier vehículo del usuario
    val location: LatLng,
    val label: String,       // "Mi casa", "Oficina", etc.
    val geofenceRadiusMeters: Float = 80f,  // mismo radio que UserParking
    val createdAt: Long,
)
```

Persistencia dual habitual: Room (local) + Firestore (sync).

### 5.4 Tickets propuestos (sub-tareas)

| Ticket | Descripción | Esfuerzo |
|--------|-------------|----------|
| `FEAT-HOME-PARKING-001` — Domain + persistencia | `HomeParkingMarker` model, Room entity + DAO, Firestore mapper, repository | Mediano |
| `FEAT-HOME-PARKING-002` — UI de gestión | Pantalla / sheet para añadir/editar/eliminar marcadores. Long-press en mapa → "Marcar como mi parking" | Mediano |
| `FEAT-HOME-PARKING-003` — Geocerca + auto-detección | Registrar geofence al crear el marcador. `HomeParkingGeofenceReceiver` → notificación de confirmación | Mediano |
| `FEAT-HOME-PARKING-004` — Confirmación + ConfirmParkingUseCase wiring | Tap "Sí" en la notificación → ConfirmParkingUseCase con `detectionMethod = HOME_GEOFENCE` y location del marcador | Pequeño |

### 5.5 Cuestiones a decidir antes de empezar

- **¿Qué pasa si entra en la geocerca pero NO está en el coche?** (caminando a casa) → Filtrar por `IN_VEHICLE` reciente. Si no ha estado IN_VEHICLE en los últimos 5 min, ignorar.
- **¿Qué pasa con la detección "normal" cuando hay marcador?** → Si entra en geocerca de home parking, **suspender** el coordinator scoring para ese ciclo. Evita doble confirmación.
- **¿Cuántos marcadores puede tener un usuario?** → Sin límite duro, pero UX-wise: probablemente 3-5 (casa, oficina, casa de los padres). Sugerencia: límite suave de 10.
- **¿La geocerca aquí compite con la de salida (departure)?** → No, son geofences distintas. La de salida se registra tras confirmar aparcamiento; la de home parking se registra al crear el marcador.
- **Nuevo `DetectionMethod`:** añadir `HOME_GEOFENCE` al enum existente (junto a `BLUETOOTH_DISCONNECT`, `ACTIVITY_RECOGNITION`, `MANUAL`).

### 5.6 Plan de implementación sugerido

1. **Fase 1 — Modelo y persistencia** (FEAT-HOME-PARKING-001): aterrizar el dominio sin tocar UI ni detección. Repositorio + tests.
2. **Fase 2 — UI básica** (FEAT-HOME-PARKING-002): "Marcar como mi parking" desde long-press del mapa. CRUD básico en pantalla de ajustes/coche.
3. **Fase 3 — Geofence + notificación** (FEAT-HOME-PARKING-003): registrar geofence + receiver + notificación de confirmación.
4. **Fase 4 — Integración con ConfirmParkingUseCase** (FEAT-HOME-PARKING-004): el "Sí" de la notificación llama a la pipeline existente con el `detectionMethod` correcto.

Cada fase es mergeable de forma independiente.

---

## Resumen — Estado y orden actualizado (2026-06-05)

| Orden | Item | Estado |
|-------|------|--------|
| 0 | Refactor de detección | ✅ Done (commit `935e6fc`) |
| 1 | `BUG-GARAGE-COLA-001` (sec. 3) — Step Detector como señal canónica | 🔵 Branch ready — pendiente commit/merge |
| 2 | `BUG-SCOOTER-001` (sec. 4) — VehicleType + smart confirmation prompt | 🔵 Branch ready — pendiente commit/merge |
| 3 | `FEAT-HOME-PARKING-001..004` (sec. 5) | ✅ Done 2026-06-05 |
| 4 | `BUG-WALK-DEPART-001` — Plaza liberada al pasar andando junto al coche | ✅ Done 2026-06-05 — ver `detection-departure-bugs-2026-06-05.md` |
| 5 | `ARCH-DEPARTURE-GEOFENCE-DUAL-TRIGGER-001` — GEOFENCE_EXIT como trigger dual | ⚪ Pending — ver `detection-departure-bugs-2026-06-05.md` |
| 6 | `DECISION-SERVICE-LIFECYCLE-001` (sec. 1) | 🟡 Deferred — necesita razonamiento + telemetría |
| 7 | `DECISION-MERGE-BT-COORDINATOR-002` (sec. 2) | 🟡 Deferred — necesita debate técnico |
