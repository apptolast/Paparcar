# DET-A-DEGRADED-PROMPT-MUST-NOT-AUTO-CONFIRM-001 · el silencio recompraba el pin EXACTO que la app acababa de negarse a afirmar

**Estado:** ✅ Done — en master. Suite completa verde: **3856 tests, 0 fallos**
(`testAll --rerun-tasks`), más `:app:assembleMockDebug` y `:app:compileProdDebugKotlin`.

⛔ Sin captura: **sin resultado visual** [PR-A-VISUAL-CHANGE-SHOWS-ITSELF-001]. Lo que cambia es qué
se guarda al expirar una pregunta degradada; no hay pantalla, estado ni copy nuevos. El defecto que
el user SÍ ve —una zona de 155,7 m dibujada como un pin exacto— es el ticket de presentación que
queda abierto más abajo.

## Problema

De los tres falsos positivos que el user reportó el 05-10-2026 (Oppo CPH2371, app 1.4.0, carril
COORDINATOR, uid `0l7X59ptXeQ4WUMlpFOZHLPIJAI3`, base `(default)`), **uno** sale de aquí — y los
dos que llegaron por esta puerta habían PREGUNTADO primero:

```
sesión 1791123836936   🔴 ESTE es el bug
  16:25:49  DECISION CONFIRM_DEGRADED_PROMPT  steps+egress  reason=weak_evidence
  16:40:51  DECISION CONFIRMED  unattended_timeout  conf=0.5        ← +15:02, sin respuesta
  → pin EXACTO (zoneRadiusMeters: null) en Reyes Católicos 51, calle peatonal donde el coche
    no puede entrar. Y la «conducción» que lo autorizó fueron DOS fijaciones de arranque en
    frío: `drive 2/401fix`, vmax 21 km/h (5,88 m/s contra una barra de 5,0), acc 5,2/13,5/18,2 m,
    en los primeros 4 segundos. 1192 eventos STEP decían que el usuario iba andando.

sesión 1791232803918   🔵 la decisión fue CORRECTA
  22:50:16  DECISION CONFIRM_DEGRADED_PROMPT  kinematic+egress  reason=egress_not_at_anchor
  23:05:21  DECISION CONFIRMED  unattended_zone_egress_mismatch  conf=0.5
  → ZONA de 155,7 m de radio, con conducción real (59 fijaciones creíbles, 54 km/h) y el coche
    dentro del círculo. Lo que el user ve mal es la PRESENTACIÓN (ver más abajo).
```

**La cadena:** `DegradeToPrompt` → `DetectionEffectDispatcher:243` escribe
`confirmation.notified(shownAt = now)` → fase `ConfirmationPhase.Notified` → `promptShownAt != null`
→ a los `confirmationResponseTimeoutMs` (15 min) `ResponseTimeoutStage` llama al evaluador
unattended → **pin exacto**.

**El supuesto roto está escrito en el KDoc de `ResponseTimeoutStage`** [DET-RECONCILE-001]: *«el
prompt sólo sale tras un viaje real, una parada real y una señal de vehicle-exit, así que sólo falta
un tap humano»*. Eso dejó de ser cierto cuando nació el segundo productor,
`CONFIRM_DEGRADED_PROMPT`, que pregunta **precisamente porque la prueba no era de fiar**. Los dos
escriben la MISMA `Notified(shownAt)`, y el `PromptReason` que explica la degradación **se emite a
la traza y no viaja al estado**: el timeout no puede distinguirlos aunque quisiera.

No es OEM-kill ni Doze: 444 y 1597 eventos, `batteryUnrestricted=true`. Es lógica nuestra.

## Doctrina violada

- **Fallo asimétrico: ante la duda se PREGUNTA, nunca se planta una plaza fantasma.** La app
  pregunta y luego **se responde «sí» a sí misma**, que es peor que no haber preguntado: la
  degradación no sirve para nada si el silencio la revierte.
- **El evento NOMINA, sólo el movimiento MEDIDO confirma.** El silencio del usuario no es movimiento
  medido, y un pin EXACTO es la afirmación más fuerte que el sistema automático sabe hacer.
- Parentesco con [DET-A-USER-YES-DOES-NOT-SHRINK-A-WALK-ENTERED-DOUBT-001]: allí un «sí» real no
  encogía una duda ya entrada. Aquí es peor — **no hubo ningún «sí»**.

## Señales / datos disponibles

Todo lo necesario ya existe y se tira: `PromptReason` (6 valores, con `key` propio) se construye en
`DegradeToPrompt`/`degradeToPrompt` y sólo llega a la traza. El estado (`ConfirmationPhase`) guarda
el *cuándo* de la pregunta pero no el *por qué*.

## Diseño

**El invariante vive en la FASE, no en el stage**: la pregunta lleva su razón, y quien la cosecha la
lee. Un guard en el stage sería re-derivar la precedencia fuera del evaluador, que es justo lo que
el KDoc de `ResponseTimeoutStage` prohíbe.

1. `ConfirmationPhase.Notified` / `.Candidate` llevan `degradedFrom: PromptReason?`.
   `ConfirmationLifecycle.notified(shownAt, degradedFrom)` **sin default**: `null` es la respuesta
   PERMISIVA, así que un productor futuro no puede comprarla callándose. Los cuatro productores los
   encontró el compilador.
2. `UnattendedSaveInput.promptDegradedFrom` lo transporta al evaluador puro.
3. `EvaluateUnattendedParkingSaveUseCase` gana `UnattendedParkingSave.DiscardDegraded(promptReason)`
   **en el último peldaño, sustituyendo únicamente a `SaveExact`**.
4. El cierre reusa el patrón de `closeNotARoadVehicle`: retira la notificación, emite su `Decision`
   (`UNATTENDED_DEGRADED_PROMPT_DISCARD`) y cierra con `AbortedUnattended("degraded_<reason>")`.

### ⛔ La regla es estrecha, y eso lo decidió el corpus de campo — no una preferencia

**La primera versión de este guard estaba mal y los replays la cazaron.** Corría antes de la
escalera y mataba TODO save degradado; tumbó cuatro trazas reales:

| replay | lo que afirma |
|---|---|
| `an_inherited_drive…` | *«the drive and the park are both real — losing them is not the fix for pointing wrong»* |
| `gondola_2608` | *«a bounded doubt costs precision, never the park»* |
| `house_mirage_001` | *«the park must be KEPT as a zone, not lost»* |
| `parafarmacia_2908` | ídem |

Las cuatro llegan al timeout con una pregunta DEGRADADA **y** conducción medida, y las cuatro
conservan el aparcamiento como área a propósito. O sea: *«degradada» no discrimina* — la
degradación es común y convive con aparcamientos reales.

Lo que sí discrimina es **qué se afirma**. Un pin exacto es una afirmación de PRECISIÓN; una zona es
una duda acotada. La degradación retira lo único que nunca estuvo acotado. Los dos FP de campo son
uno de cada:

- `1791123836936` → **pin EXACTO** (`zoneRadiusMeters: null`) en una calle peatonal, sobre una
  «conducción» de **2 fijaciones** de arranque en frío (5,88 m/s contra una barra de 5,0 —
  `drive 2/401fix`), con 1192 eventos STEP diciendo que iba andando. 🔴 **Lo cierra este ticket.**
- `1791232803918` → **zona de 155,7 m** de radio. Conducción real (59 fijaciones creíbles, 54 km/h)
  y el coche estaba dentro del círculo: **la decisión fue correcta**. 🔵 Este ticket no la toca.

## Lo que este ticket NO cierra

- **El FP de `1791232803918` sigue viéndose mal**, y ahora se sabe por qué: el aparcamiento se
  guardó como un área de 155,7 m y **la UI lo dibuja como un pin exacto** con un «?». El usuario lee
  «el coche está AQUÍ» cuando la app dice «está en algún punto de este círculo». Es un defecto de
  PRESENTACIÓN, no de decisión. → ticket aparte.
- **La causa raíz de `1791123836936` sigue viva**: `measuredDriving` es `provenMaxSpeedMps >=
  minimumTripSpeedMps` y **no mira cuántas fijaciones creíbles sostienen el pico**, así que dos
  muestras de arranque en frío cruzan la barra y toda la escalera pasa a asumir «un aparcamiento
  existe». Este ticket impide que eso acabe en pin exacto *cuando además hubo degradación*; no lo
  impide por los demás caminos. Es `DET-LONE-SAMPLE`, ya medido y abierto.

## Criterio de éxito

- Un prompt degradado que expira donde habría ido un `SaveExact` **no produce pin**, cierra la
  sesión y deja la traza `aborted_unattended_degraded_<reason>`. ✅
- Un prompt degradado con duda ACOTADA sigue guardando su zona. ✅ (4 replays de campo)
- Un prompt ORDINARIO que expira sigue guardando exactamente como hoy. ✅ (DET-RECONCILE-001 intacto)
- Suite completa: **3856 tests, 0 fallos** (`testAll --rerun-tasks`). ✅

## Consumidores auditados

`grep -rn "promptShownAt" shared/src app/src --include=*.kt` → 4 consumidores de producción:

| Consumidor | Pregunta que hace | Estado |
|---|---|---|
| `ResponseTimeoutStage:53` | ¿expiró la ventana? → guarda | 🔴 **cerrado por este ticket** |
| `DetectionEffectDispatcher:233` (`alreadyPrompted`) | ¿hay un prompt en pantalla? (no re-postear) | ✅ exento — la pregunta sigue siendo correcta para un prompt degradado |
| `StopTracking:514` | ¿retractar la pregunta porque vuelve a conducir? | ✅ exento — aplica igual a ambos, y es deseable |
| `CoordinatorParkingDetector:939` (promptWatchdog) | revive el stage cuando el stream muere | ✅ cubierto por convergencia — re-corre el MISMO stage vía `runStage`, hereda el arreglo |

Productores de `Notified(shownAt)` → 4, y **dos son degradaciones**:

| Productor | Tipo | Acción |
|---|---|---|
| `ConfidenceScoringStage:155` | ordinario (low/medium) | pasa `degradedFrom = null` |
| `DetectionEffectDispatcher:211` (`DiscardCandidate`, Candidate→Notified) | preserva el anterior | pasa el `degradedFrom` vigente |
| `DetectionEffectDispatcher:243` (`DegradeToPrompt`) | **degradado** | pasa su `PromptReason` |
| `DetectionEffectDispatcher:63` (`degradedToPrompt`, implausible repark) | **degradado** | pasa `IMPLAUSIBLE_REPARK` |

## Follow-up detectado, fuera de alcance

`ResponseTimeoutStage` mapea `UnattendedParkingSave.Discard` (el tren,
[DET-A-TRAIN-IS-NOT-A-CAR-001]) a **efecto `null`**, así que desde el timeout ese veredicto no
descarta la notificación pendiente ni estampa `AbortedUnattended("not_a_road_vehicle")` — a
diferencia de `CandidateStage`/`ConfidenceScoringStage`, que sí emiten `CloseNotARoadVehicle`.
Preexistente y de otro carril; no se toca aquí. → `docs/backlog/det-a-train-discard-must-close-the-session-001.md`
