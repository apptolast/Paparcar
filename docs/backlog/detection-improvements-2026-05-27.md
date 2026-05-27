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

## 0 · Refactor de limpieza del flujo de detección — 🔵 branch ready

**Estado:** Cambios aplicados y `compileDebugKotlinAndroid` verde. Pendiente de commit.

**Resumen de lo que toca:**
- `ParkingDetectionCoordinator.kt` — `collectLatest` → `collect` (M1); `update {} + .value` → `updateAndGet {}` (M2); eliminado `withContext(NonCancellable)` redundante alrededor de `notifyParkingConfirmation` (era necesario solo por la cancelación del `collectLatest`).
- `ParkingDetectionService.kt` — Extraído `guardPermissions(actionLabel)` (C2): consolida los tres checks duplicados en START_TRACKING, VEHICLE_TRANSITION e IN_VEHICLE_ENTER.
- `ActivityRecognitionLabels.kt` — Nuevo archivo común con `activityLabel()` y `transitionLabel()` (M3). Eliminadas las duplicaciones inline en `ParkingDetectionService` y `ActivityTransitionReceiver`.
- `ActivityRecognitionManagerImpl.kt` — `STILL_REQUEST_CODE` y `VEHICLE_REQUEST_CODE` co-localizados en el manager (M4). Eliminada constante huérfana en `ActivityTransitionReceiver`.

**Lo que NO se tocó (pendiente de razonar):**
- C1 — cuándo matar el service (ver sección 1).
- O2 — fusionar BT detection con Coordinator (ver sección 2).

**Próximo paso:** commit con mensaje `refactor(detection): clean up service+coordinator+receiver flow`.

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

## 3 · BUG-GARAGE-COLA-001 — Falso positivo aparcando en cola de parking · ⚪

**Origen:** reportado por usuario real.
**Síntoma:** El usuario está esperando en la puerta del garaje a que se abra, o haciendo cola en un parking público para sacar el ticket. La velocidad es muy baja (< 5 km/h) o cero durante varios segundos. La app detecta `STILL` y dispara el flujo de confirmación de aparcamiento → publica una plaza inexistente.

**Causa raíz:**
1. `DetectedActivity.STILL ENTER` se dispara con cualquier parada larga (semáforos, atascos, colas).
2. El coordinator no diferencia entre "parado en zona conocida de tránsito" y "parado en una plaza real".
3. No hay validación de que `IN_VEHICLE_EXIT` haya ocurrido — un coche detenido en cola sigue siendo `IN_VEHICLE`.

**Propuestas de mitigación (combinables):**

### 3.1 — Requerir `IN_VEHICLE_EXIT` antes de aceptar `STILL` como señal de aparcamiento
Hoy, `STILL ENTER` es suficiente para entrar en el scoring. Si añadimos la regla **"STILL solo cuenta tras un EXIT de IN_VEHICLE"**, una cola con motor encendido (sigue siendo IN_VEHICLE) no dispara nada. La transición real cuando uno aparca y sale del coche es: `IN_VEHICLE EXIT → STILL ENTER`. Sin EXIT no hay aparcamiento.

### 3.2 — Walking confirmation antes de confirmar (como BT strategy)
Replicar el "walking ≥ 30 m" del BT path en el Coordinator: tras `STILL ENTER`, esperar a que la nueva posición esté ≥ 30 m de donde se detectó STILL. Esto descarta colas (el usuario no camina; está dentro del coche).

### 3.3 — Velocidad de los últimos N segundos
Si la velocidad media de los últimos 60 s antes del STILL fue < 8 km/h *continuamente* (cola lenta), descartar. Si hubo picos de velocidad de carretera y luego frenazo, sí parece aparcamiento real.

### 3.4 — Geofence negativa: "zonas de tránsito conocidas"
Mantenido por el usuario: si tu casa tiene un garaje con cola, el usuario puede marcar manualmente esa zona como "no aparcamiento". Solución que se enlaza con FEAT-HOME-PARKING-001 invertida.

**Recomendación previa:** combinar **3.1 + 3.2**. 3.1 elimina el caso "motor encendido en cola"; 3.2 elimina "motor apagado en cola del peaje". 3.3 y 3.4 son opcionales.

**Esfuerzo estimado:** Mediano. Tocaría `ParkingDetectionCoordinator` (añadir estado `WaitingForExit`) y posiblemente un nuevo use case `WaitForWalkingAwayUseCase` compartido con BT.

---

## 4 · BUG-SCOOTER-001 — Patín eléctrico clasificado como IN_VEHICLE · ⚪

**Origen:** reportado por usuario real.
**Síntoma:** Usuario en patín eléctrico (≤ 25 km/h). Google Activity Recognition lo clasifica como `IN_VEHICLE`. Al detener el patín → STILL ENTER → falso aparcamiento.

**Causa raíz:**
- La API de Activity Recognition de Google **no distingue** patines/bicis eléctricas de coches por debajo de cierta velocidad. La clasificación se basa en patrones de aceleración + GPS, y un patín a 25 km/h en línea recta es indistinguible de un coche en una zona 20.
- No hay un `DetectedActivity` específico para "scooter eléctrico".

**Propuestas de mitigación:**

### 4.1 — Filtrar por velocidad sostenida mínima
Si en los últimos 5 minutos de "IN_VEHICLE" la velocidad **máxima** registrada por GPS fue ≤ 30 km/h, descartar como vehículo de baja energía (probablemente bici/patín). Un coche real, incluso en ciudad, supera los 30 km/h en algún momento.

### 4.2 — Cruce con `ON_BICYCLE`
Si en la ventana reciente hubo eventos de `ON_BICYCLE` (con confidence > 50), aumenta la probabilidad de patín/bici. La API a veces oscila entre `ON_BICYCLE` e `IN_VEHICLE` para patinetes; capturar el `ON_BICYCLE` como señal de "esto NO es un coche".

### 4.3 — User preference: tipo de vehículo
En la pantalla de registro de vehículo, el usuario ya selecciona marca/modelo. Si elegimos un set de marcas/modelos de patines/bicis (Xiaomi M365, Cecotec Bongo, etc.), podemos clasificar el `Vehicle` como `BIKE` o `SCOOTER` y **desactivar la detección por Activity Recognition para ese vehículo** — solo BT (si tiene) o detección manual.

### 4.4 — Vehicle type field
Añadir `vehicleType: VehicleType` (CAR / SCOOTER / BIKE / MOTORCYCLE) al modelo `Vehicle`. Si no es CAR/MOTORCYCLE, ignorar Activity Recognition.

**Recomendación previa:** **4.4 + 4.1** como combo. 4.4 es la solución limpia a medio plazo (datos correctos); 4.1 es una red de seguridad que protege incluso si el usuario elige un tipo erróneo.

**Esfuerzo estimado:** Mediano (4.4 toca modelo + UI + Room + Firestore migration). 4.1 solo toca el Coordinator.

---

## 5 · FEAT-HOME-PARKING-001 — Marcador "mi parking de casa" con geocerca · ⚪

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

## Resumen — Prioridades sugeridas

| Orden | Item | Razón |
|-------|------|-------|
| 1 | Commit del refactor (sección 0) | Trabajo ya hecho, no bloquea nada |
| 2 | BUG-GARAGE-COLA-001 (sec. 3) | Bug real reportado, mitigación 3.1 es barata |
| 3 | FEAT-HOME-PARKING-001 (sec. 5) | Feature que además mitiga 3 en parte |
| 4 | BUG-SCOOTER-001 (sec. 4) | Bug real, pero más complejo (toca modelo) |
| 5 | DECISION-SERVICE-LIFECYCLE-001 (sec. 1) | Necesita datos antes de decidir |
| 6 | DECISION-MERGE-BT-COORDINATOR-002 (sec. 2) | Cambio arquitectural; debate técnico antes |
