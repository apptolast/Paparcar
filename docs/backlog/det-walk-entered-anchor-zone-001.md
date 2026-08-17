# DET-WALK-ENTERED-ANCHOR-ZONE-001 · un ancla dudosa degrada la PRECISIÓN del pin, nunca borra el aparcamiento

**Estado:** ✅ **DONE — master `7bdb6a18`** (ff-only 17-08, sin pushear; rama + worktree borrados) · ⏳ pendiente validar en campo

## Problema

Field 16-08 (madrugada del 17), Redmi `WZB7oftWLDY1toGJrDwoRHnnYHx2`, sesión de diagnóstico
`1786918991116`. Viaje real, completo y perfectamente medido:

```
22:23:11Z  SESSION_STARTED            (ARM:SIGNIFICANT_MOTION sentry-wake geof=5d9c6bb1)
22:23–22:31  conducción real          pico 96,7 km/h · 47 fixes de conducción sobre 266 · 25,6 min
22:32        llegada y maniobra       velocidades ya en banda peatonal (≤3 m/s)
22:33:42Z  DECISION CONFIRM_DEGRADED_PROMPT   pathLabel=steps+egress
22:34:00Z  ACTIVITY_TRANSITION IN_VEHICLE EXIT
22:48:43Z  DECISION UNATTENDED_WALK_ENTERED_NUDGE
22:48:48Z  SESSION_ENDED  outcome=aborted_unattended_walk_entered_anchor
```

**Resultado: cero pines.** El usuario se quedó con la sesión anterior activa (Calle Puerta del Mar,
`5d9c6bb1`, "Activa Mar&Aventura") y perdió el aparcamiento de casa. En el trace completo (484
eventos) **no aparece ni `UNATTENDED_ZONE_SAVED` ni `UNATTENDED_ZONE_SAVE_FAILED`**: el fallback de
zona honesta ni siquiera se intentó.

### El mismo hecho castiga dos veces

`CoordinatorParkingDetector.isAnchorWalkEntered` (línea 1883):

```kotlin
if (s.anchorWalkFixesAtCapture <= config.anchorFreezeMaxWalkFixes) return false   // 3
val maneuverEntry = s.anchorStepEventsAtCapture == 0 &&
    s.anchorSawStepsAtCapture &&                                                  // ← exige contador VIVO
    s.anchorWalkFixesAtCapture <= config.maneuverEntryMaxWalkFixes                // 8
return !maneuverEntry
```

1. La **maniobra de aparcar** (llegada lenta, velocidades en banda peatonal) gasta el presupuesto de
   `anchorWalkFixesAtCapture`. Con el contador de pasos **mudo** en la captura
   (`anchorSawStepsAtCapture == false`), la exención `maneuverEntry` es inalcanzable por
   construcción → el ancla queda marcada como "entrada andando".
   El comentario del código en la línea 1281 afirma lo contrario: *"the taint requires step
   corroboration"*. La implementación sólo exime el caso `stepEvents == 0 && sawSteps == true`.
2. Esa marca fuerza `ParkingDecision.Prompt` en `EvaluateParkingDecisionUseCase` (línea 213-215) →
   `CONFIRM_DEGRADED_PROMPT`. Correcto por doctrina: ante la duda se pregunta.
3. Nadie contesta. En el timeout desatendido, la rama walk-entered (línea 1290-1311) exige
   **la misma `anchorSawStepsAtCapture`** para salvar una zona aproximada:

```kotlin
val walkedInBoundMeters = state.anchorStepEventsAtCapture * config.anchorStrideMeters.toDouble()
if (center != null && state.anchorSawStepsAtCapture &&
    saveUnattendedZone("walk_entered_anchor", center, walkedInBoundMeters, …))
```

El hecho que causó la duda es el que bloquea el único remedio que existe para esa duda. Con contador
mudo la cota de duda vale `0 × stride = 0 m`, así que aunque el guard se saltara, el radio saldría
degenerado.

## Doctrina violada

- **"Todo trigger dispara SIEMPRE" / parking perdido con datos = bug NUESTRO.** Hubo despertares,
  hubo stream vivo 25 minutos, hubo 47 fixes de conducción a 97 km/h. No hay excusa de OS
  (`batteryUnrestricted=true`, `requiresOemBatteryFreeze=false`).
- **Fallo asimétrico mal aplicado.** El fallo asimétrico dice "ante la duda, PREGUNTA, no plantes una
  plaza fantasma". No dice "ante la duda, borra un aparcamiento que sabes que ocurrió". Una duda
  sobre *dónde* está el coche no es una duda sobre *si* aparcó.

## Directiva del usuario (17-08)

> *"Si ya hemos validado conducción medida y nos hemos parado en un sitio, si estamos un rato
> parados, mejor guardar el aparcamiento aunque no tengamos los pasos completos, ya que es obvio que
> nos hemos movido en coche, mejor que perderlo."*

Conducción medida + parada sostenida ⇒ **el aparcamiento existe**. Lo único discutible es su
precisión, y para eso ya existe la zona aproximada.

## Señales / datos disponibles

| Señal | Estado hoy | Sirve |
|---|---|---|
| `state.maxSpeedMps` (drive-proof gated) | 26,9 m/s en la sesión | ✅ conducción medida, ya fiable |
| `stoppedDurationMs` / `updateStopTracking` | ~16 min parado al timeout | ✅ parada sostenida |
| `anchorWalkFixesAtCapture` | > 3 (maniobra lenta) | ✅ pero hoy sólo sirve para MANCHAR |
| `anchorStepEventsAtCapture` | 0 (contador mudo) | ❌ cota de duda degenerada |
| **posiciones GPS de esos walk-fixes** | grabadas, nunca leídas | 🆕 **cota de duda MEDIDA** |

La clave: la mancha nace de unos fixes que son **posiciones GPS reales**. La distancia entre el
primero de esa racha y el ancla capturada es una cota de duda *medida*, disponible tenga o no pasos
el dispositivo. El contador de pasos no es la única forma de acotar el error del ancla — sólo era la
única que estábamos leyendo.

## Diseño

**Invariante:** *una vez probada la conducción y consumada una parada sostenida, la sesión SIEMPRE
produce un aparcamiento. La desconfianza sobre el ancla decide la FORMA (punto exacto → zona
acotada), nunca la EXISTENCIA.*

Tres piezas:

1. **Cota de duda medida.** Nuevo campo de sesión `anchorWalkInSpanMeters`: distancia entre el primer
   fix de la racha en banda peatonal que llevó a la captura y el ancla capturada. Se sella en el
   mismo `if (anchorStopOfRecord != s.anchorCapturedAtStop)` que ya sella los otros `*AtCapture`
   (línea 2239-2252). Sustituye a `stepEvents × stride` como cota de duda cuando el contador está
   mudo; con contador vivo se usa el **máximo** de ambas (los dos son cotas inferiores del error real).

2. **Un solo evaluador puro para la salida desatendida.** La cadena de 5 ramas de
   `CoordinatorParkingDetector` (líneas 1180-1350: unpinned → egress_mismatch → gap_entered →
   walk_entered → vehicular_egress → save) se extrae a
   `EvaluateUnattendedParkingSaveUseCase` (commonMain, puro), que devuelve:
   - `SaveExact` — ancla de confianza.
   - `SaveZone(radiusMeters, reason)` — ancla dudosa pero acotable: se guarda igual.
   - `Ask(reason)` — el error es **inacotable hacia delante** (único caso que puede perder el pin:
     `anchorGapEntered`, donde el coche pudo seguir conduciendo dentro del agujero, y
     `egressExceedsWalkReach`, donde el coche demostrablemente se fue).
   El coordinator conserva los side-effects (notificación, `runConfirm`, diagnósticos).

3. **La licencia explícita de la directiva.** `SaveZone` exige `driveProven && stoppedDurationMs ≥
   config.sustainedStopForSaveMs` (nuevo, 5 min). Sin conducción probada se mantiene el nudge de hoy
   (línea 1158, `DET-AR-FIRST-001`), que es correcto.

El prompt de las 22:33 **no se toca**: preguntar ante un ancla dudosa es doctrina. Lo que se elimina
es que el silencio del usuario cueste el aparcamiento.

### Por qué NO se quita la mancha `walk_entered`

Tentación descartada: hacer que la mancha exija pasos (lo que el comentario dice). Resucitaría el FP
de Camelias (2026-07-15): contador mudo, la vuelta andando desde una reposición leída como conducción,
ancla en la puerta de casa a 37 m del coche. La mancha es correcta; lo que estaba mal era su
consecuencia.

## Criterio de éxito

- Test de regresión que replica la forma del campo (conducción medida + ancla walk-entered + contador
  mudo al capturar + timeout sin respuesta) → **verificado ROJO sin el fix**, verde con él, guardando
  zona en lugar de nada.
- Se mantiene verde el anti-resurrección de Camelias: mismo ancla manchada **sin** conducción probada
  → sigue sin pin.
- Se mantiene verde `DET-GAP-ANCHOR-001` (ancla gap-entered → nudge, error inacotable).
- Campo: un viaje real que termine en maniobra lenta y prompt ignorado deja pin (zona) en vez de nada.

## Consumidores auditados

`grep -rn "anchorSawStepsAtCapture\|anchorWalkFixesAtCapture\|anchorStepEventsAtCapture" composeApp/src`

| Sitio | Clasificación |
|---|---|
| `CoordinatorParkingDetector:1296-1298` cota + guard del zone-save | **cerrado** — es el bug |
| `CoordinatorParkingDetector:1883-1888` `isAnchorWalkEntered` | **exento con razón** — la mancha se conserva (ver arriba) |
| `CoordinatorParkingDetector:1406,1784` → `ParkingDecisionInput.anchorWalkEntered` | **exento** — sigue degradando a prompt, correcto |
| `CoordinatorParkingDetector:2239-2252` sellado en la captura | **cubierto** — se amplía con `anchorWalkInSpanMeters` |
| `CoordinatorParkingDetector:1293` log | cubierto (se amplía con el span) |
| `EvaluateParkingDecisionUseCase:213` | **exento** — el prompt es correcto |

`grep -rn "saveUnattendedZone"` → único llamante la cadena desatendida, absorbida por el evaluador.

## Registro

- 2026-08-17 — abierto tras el diagnóstico del field 16-08. Worktree + rama creados.
- 2026-08-17 — **implementado, sin commitear.**
  - `EvaluateUnattendedParkingSaveUseCase` (commonMain, puro) absorbe la cadena de 7 ramas;
    `UnattendedSaveReason` transporta las 3 cadenas de diagnóstico de cada rama juntas.
  - `ParkingDetectionState.walkRunOriginFix` + `anchorWalkInSpanMeters` (cota MEDIDA del walk-in),
    selladas en el mismo instante que los demás `*AtCapture`.
  - `ParkingDetectionConfig.sustainedStopForSaveMs = 5 min` + su `require`.
  - Coordinator: la rama del response-timeout queda como side-effects; helper `nudgeUnattended`
    unifica las 6 salidas de nudge.
  - **1205 tests verdes** (1190 en master + 15 nuevos). `compileProdDebugKotlinAndroid` y
    `compileMockDebugKotlinAndroid` OK.
  - Regresión **verificada ROJA sin el fix** (`should_saveBoundedZone_when_walkEnteredAnchorHas
    MuteCounterButMeasuredWalkInSpan` falla, y sólo ésa, al restaurar la compuerta antigua).
  - `docs/detection/PARKING-DETECTION.md` actualizado (Sección 2).
  - Dev Catalog / strings / `detectionPath`: sin cambios — no hay pantalla, estado ni provenance
    nueva (`unattended_zone_walk_entered_anchor` ya existía; sólo pasa a ser alcanzable).
- 2026-08-17 — **mergeado a master `7bdb6a18`** con `--ff-only` (no `--squash`: el árbol principal
  tenía trabajo del user staged y un squash lo habría metido en el commit; la rama tenía un único
  commit, así que el ff da el mismo "un ticket = un commit" sin tocar su index). APK firmado
  `prodRelease` compilado, sha256 `6c44efa3db7edeb77eb06d1b7f485274236cecb242f839a2ed310b3102602e79`
  — **sin instalar: no había ningún móvil conectado**.
- ⚠️ **Solape conocido con DET-BIKE-NOT-A-CAR-001**: ese ticket veta exactamente esta cadena. Al
  cerrarse éste, aquél debe rebasar sobre master y meter su veto como rama temprana del evaluador,
  en vez de tocar el `when` del coordinator.
