# DET-ASK-STATE-001 — la pregunta de detección es un ESTADO de Home, no solo una notificación

**Estado:** 📋 spec (2026-07-26) · sin rama · implementar tras el field-test de DET-FROZEN-COUNTER-001
**Origen:** field-test 2026-07-25/26 (el prompt de las 00:35 y el nudge de las 00:50 solo existieron
como notificaciones; abrir la app durante la ventana no mostraba la pregunta por ningún sitio) +
observación de diseño del user 2026-07-26: *"debería pertenecer al mismo flujo de estados"*.

## Problema

Hoy hay TRES superficies de detección en Home que se arbitran de forma implícita:

1. `DetectionUiState` (DET-READY-001h) — la máquina de estados real del sheet
   (`NoVehicle / Inactive / BlockedCore / Parked / Monitoring / AwaitingFirstPark / Silent`).
2. La fila `pendingParkNudge` (DET-NUDGE-PERSIST-001) — campo suelto en `HomeState`, fuera de la
   máquina.
3. El prompt "¿Has aparcado?" — **no existe en la UI de la app**: solo `ConfirmationPhase.Notified`
   dentro del coordinator + la notificación del sistema. Con la ventana abierta, el sheet enseña
   `Monitoring` ("siguiendo el viaje"), que oculta la verdad accionable: *responde*.

Consecuencia de campo: el prompt ignorado degrada a timeout aunque el usuario abra la app durante
los 15 minutos — la pregunta era invisible in-app.

## Diseño

### Un solo flujo: `DetectionUiState` gana dos estados

Precedencia (de más a menos urgente; la resolución vive en el dominio, como hoy):

```
BlockedCore / NoVehicle / Inactive        (como hoy)
AwaitingAnswer   ← NUEVO  ventana de prompt abierta
PendingAsk       ← NUEVO  nudge "¿dónde has dejado el coche?" sin resolver (absorbe pendingParkNudge)
Monitoring → Parked → AwaitingFirstPark → Silent   (como hoy)
```

- **`AwaitingAnswer(vehicleName?, shownAtMs)`** — el sheet renderiza la pregunta
  "¿Has aparcado el %s?" con acciones **"Sí, he aparcado"** / **"No, sigo conduciendo"** cableadas
  a las MISMAS acciones que la notificación (`ParkingConfirmationReceiver.ACTION_CONFIRMED` /
  `ACTION_DENIED` → intake del service → `coordinator.onUserConfirmedParking()` / deny). La
  notificación pasa a ser el espejo del estado, no la única superficie. Muestra cuenta atrás
  suave de la ventana (opcional, sin mecánica interna en el copy — regla de copy).
- **`PendingAsk(nudge: PendingParkNudge)`** — la fila actual del nudge entra en la máquina.
  Resolución sin cambios: marcar plaza (deep-link `AddingParking`), "Descartar"
  (`ClearParkNudgeUseCase`), o el janitor reactivo cuando reaparece sesión del vehículo.

### Fontanería (el prompt como estado observable y durable)

1. **`DetectionRuntime`** (androidMain, ya observable para Monitoring) gana
   `promptWindow: StateFlow<PromptWindow?>` con `data class PromptWindow(vehicleId?, shownAtMs)`.
   - Se ABRE en el único choke point de posteo del prompt (coordinator: los dos carriles que ya
     loguean `PROMPT_SHOWN` — `advanceLowMedium`, `advanceHigh`, `degradeToPrompt`, implausible-
     repark). Señal desde el coordinator vía port (igual que `setRunning`).
   - Se CIERRA en: respuesta del usuario (ambas), cualquier confirm (auto/zona/unattended),
     timeout de respuesta, y el finally de la sesión (un cierre de sesión SIEMPRE cierra la
     ventana — invariante, no N parches).
2. **Durabilidad (patrón DET-NUDGE-PERSIST):** persistir la ventana en `AppPreferences`
   (slot único `pendingPromptWindow`) al abrir, limpiar al cerrar. Al arrancar el proceso con una
   ventana persistida: si su edad < `confirmationResponseTimeoutMs` Y no hay sesión coordinator
   viva → la ventana está muerta (el proceso murió con ella): degradarla al nudge persistente
   (`showMarkParkingNudge(source="prompt_orphaned")`) — nunca resucitar un prompt sin sesión que
   lo respalde. Si hay sesión viva, el coordinator re-señala solo.
3. **`ObserveDetectionReadinessUseCase`** combina el nuevo flow y el `pendingParkNudge` existente
   → `DetectionReadiness` gana `AwaitingAnswer` y `PendingAsk` (o se proyectan directamente en
   `toUiState()` si preferimos no tocar el dominio — decidir en implementación; la precedencia
   debe quedar en UN sitio).
4. **`HomeState.pendingParkNudge` se elimina** como campo suelto (la fila actual se borra limpio,
   regla feedback_no_silent_overload) — su información viaja dentro del estado.

### UI

- `HomeDetectionSurface` gana las dos variantes (fila de pregunta con dos botones; fila de nudge
  como la actual). `rendersActionSurface` incluye ambas. `isDetectionWorking`: `AwaitingAnswer`
  cuenta como working (hay sesión viva detrás); `PendingAsk` no.
- Strings: reutilizar `notif_confirmation_title_vehicle` / `notif_action_yes_parked` /
  `notif_action_no_not_parked` como base; si hace falta copy propio de sheet → 9 locales
  (regla i18n).

### Sistema de pruebas mock (regla ⛔ Dev Catalog — MISMA tarea)

- `StateGalleryScreen`: variantes `AwaitingAnswer` y `PendingAsk` del Home sheet (paridad con
  `*Previews.kt`).
- `MockScenario` + fake scenario-aware del runtime: preset "prompt abierto" y "nudge pendiente"
  en `DevCatalogScreen`.
- Verificar `assembleMockDebug`.

### Tests

- Proyección: readiness+ventana → `AwaitingAnswer` con precedencia sobre `Monitoring`; nudge →
  `PendingAsk` sobre `AwaitingFirstPark`; ventana+nudge simultáneos → gana `AwaitingAnswer`.
- Ciclo de ventana: abre en cada carril de prompt, cierra en respuesta/confirm/timeout/finally
  (usar los fixtures de coordinator existentes — ya cubren los carriles).
- Huérfana al arranque → degrada a nudge, nunca prompt zombi.
- Naming `should_x_when_y`, fakes sobre mocks.

## Fuera de alcance (explícito)

- El círculo de la zona aproximada en el mapa (pendiente de DET-HONEST-CLOSE-001, tarea hermana).
- Cambiar la mecánica del timeout/candados (eso es DET-FROZEN-COUNTER-001, ya en el stack).
- Tocar la notificación en sí (copy/acciones quedan igual; solo deja de ser la única superficie).

## Riesgos / decisiones abiertas

- ¿`AwaitingAnswer` en `DetectionReadiness` (dominio) o solo en la proyección UI? Inclinación:
  dominio, porque la precedencia ya vive ahí y el resolver es la única fuente de verdad.
- Doble fuente de "respuesta": sheet y notificación responden a la vez → las acciones ya son
  idempotentes en el intake (verificar con test).
- MIUI/ColorOS pueden retrasar la señal de cierre si el proceso se congela con la ventana abierta
  — la degradación a nudge al arrancar cubre el caso.
