# DET-CAR-REST-CLOCK-001 · la parada sostenida del veredicto desatendido es del COCHE y la atestigua el ancla, no el stop-tracker del teléfono

**Estado:** 🔵 En progreso · rama `bugfix/DET-CAR-REST-CLOCK-001-anchor-rest-clock` · worktree `../Paparcar-car-rest-clock`

## Problema

Field 18-08 ~20:00, entreno → Calle Góndola, Redmi `WZB7oftWLDY1toGJrDwoRHnnYHx2`, sesión
`1787075310656`. Tercer FN idéntico en llegada a casa (16-08, 17-08, 18-08), cada uno una capa más
adentro del mismo pozo:

```
19:48:30  ARM:AR_VEHICLE_ENTER (enter_at_car, lag 156 ms)
19:48–20:00  conducción real       vmax 113 km/h · 53/270 driving fixes · 26,2 min
19:59:37  DECISION CONFIRM_DEGRADED_PROMPT  (steps+egress, ancla walk-entered — correcto)
19:59–20:14  prompt sin responder  GPS indoor: acc 100–266 m, Doppler fantasma 2–10 km/h
20:14:39  DECISION UNATTENDED_WALK_ENTERED_NUDGE
20:14:44  SESSION_ENDED aborted_unattended_walk_entered_anchor
```

**Cero pin, cero zona** — con el fix de DET-WALK-ENTERED-ANCHOR-ZONE-001 (`7bdb6a18`) ya en el APK.
La rama WALK_ENTERED_ANCHOR del evaluador SÍ se ejecutó y devolvió `Ask` porque
`bounded = doubt > 0 && stoppedDurationMs ≥ 5 min` — y el reloj de parada marcaba **~0 s tras 15
minutos con el coche quieto**. El Oppo confirmó el mismo viaje por la misma vía
(`confirmed_steps+egress`, sesión `1787075312357`, pin 20:02).

### Por qué el reloj estaba a cero

`updateStopTracking` solo acumula con `speed < 1 m/s` y resetea `stoppedSince = null` con
**cualquier** fix `speed ≥ 1 m/s`, **sin gate de accuracy** (`CoordinatorParkingDetector.kt:2347`).
Cada velocidad fantasma de 2 km/h con 200 m de error reiniciaba el contador; el máximo acumulado en
los 15 min fue ~15 s. La ironía: el ANCLA sí está blindada contra exactamente ese ruido (FROZEN
ignora banda peatonal, `🔒 ANCHOR-LOCK-001`); el reloj que licencia el zone-save, no.

### El error semántico de fondo

`UnattendedSaveInput.stoppedDurationMs` está documentado como *"How long the car has been at rest"*
— pero el feed mide la quietud del **teléfono**. Tras el egress, teléfono ≠ coche: incluso con GPS
limpio, quien aparca y entra andando a casa tendría el reloj a cero al llegar el timeout (el 17-08
funcionó de casualidad porque el GPS indoor estuvo silencioso y el teléfono quieto acumuló 14,7 min).

## Doctrina violada

- **Parking perdido con datos = bug NUESTRO.** Stream vivo 26 min, `batteryUnrestricted=true`,
  todos los triggers dispararon. No hay excusa de OS.
- **Invariante de DET-WALK-ENTERED-ANCHOR-ZONE-001 incumplido por su propia licencia:** "conducción
  probada + parada sostenida ⇒ el aparcamiento existe; la duda solo degrada la FORMA". La parada
  sostenida DEL COCHE existía (15 min); la señal que la representaba era del teléfono.

## Señales / datos disponibles

| Señal | Estado | Sirve |
|---|---|---|
| `stoppedDurationMs` (stop-tracker del teléfono) | ~0 s por ruido / por la caminata a casa | ❌ testigo equivocado tras el egress |
| `anchorCapturedAtStop` | timestamp de apertura de la parada a la que se ató el ancla | ✅ **inicio de la parada del coche** |
| ancla PINNED (frozen/locked) | solo la mueve conducción real (`≥ minimumTripSpeedMps` con accuracy creíble) | ✅ testigo de que el coche SIGUE ahí |
| `egressExceedsWalkReach` | guard de ausencia por encima de las ramas de zona | ✅ cubre "el coche se fue durante el prompt" |

Con ancla pinned, `now − anchorCapturedAtStop` es la parada del coche por construcción: la parada
que capturó el ancla la abrió el coche al detenerse, y desde entonces solo conducción real medida
habría limpiado el ancla.

## Diseño

**Invariante (dónde vive: `EvaluateUnattendedParkingSaveUseCase`):** *la parada sostenida que
licencia un zone-save es la del COCHE, y su testigo es el ancla pinned — nunca el stop-tracker del
teléfono, que tras el egress sigue al peatón.*

1. `UnattendedSaveInput.stoppedDurationMs` → **`anchorRestMs`**: ms desde que se abrió la parada del
   ancla pinned (`now − anchorCapturedAtStop`); `0` sin ancla pinned. El campo del teléfono
   desaparece del input — ningún consumidor legítimo queda.
2. Las dos ramas que leen `sustainedStopForSaveMs` (GAP_ANCHOR y WALK_ENTERED_ANCHOR) pasan a
   `anchorRestMs`. Ambas están tras el check `anchorPinned`, así que el valor siempre es del ancla.
3. El coordinator calcula el valor en la construcción del input (side-effect free, un `let`) y
   loguea `carRest=` junto al viejo `stopped=` para que las trazas de campo muestren ambos relojes.

**No se toca** `updateStopTracking` ni su reset sin gate de accuracy: ese tracker alimenta el
scoring ATENDIDO (fase pre-egress, donde teléfono = coche) y endurecerlo cambiaría señales que hoy
funcionan. El testigo equivocado se sustituye en el único sitio que lo malinterpretaba.

**Riesgo FP analizado:** conceder la zona aunque el teléfono se moviera no resucita ningún FP
conocido — "el coche se fue durante el prompt" lo cubre `egressExceedsWalkReach` (evidencia de
AUSENCIA, por encima de toda rama de zona), y un drive-past real limpia el ancla al reanudar
conducción medida. El caso Camelias (sin conducción) ni llega: la licencia sigue tras
`measuredDriving`.

## Criterio de éxito

- Regresión con la forma del field 18-08 (ancla pinned walk-entered, doubt > 0, reloj del teléfono
  ~10 s, ancla en reposo 15 min) → **verificada ROJA sin el fix**, verde con él: `SaveZone`.
- Gemela para la rama GAP (mismo reloj de teléfono muerto) → `SaveZone`.
- Siguen verdes: Camelias anti-resurrección (sin drive → nudge), Av. Sanlúcar (gap sin reposo →
  nudge; ahora "sin reposo" = ancla recién capturada), vehicular-egress outranks, y el vocabulario
  de trazas.
- Campo: llegada a casa con GPS indoor ruidoso y prompt ignorado deja zona en vez de nada.

## Consumidores auditados

`grep -rn "stoppedDurationMs" composeApp/src --include=*.kt`

| Sitio | Clasificación |
|---|---|
| `EvaluateUnattendedParkingSaveUseCase:263,287` (sustainedStop) | **cerrado** — es el bug; pasa a `anchorRestMs` |
| `CoordinatorParkingDetector:1149` (feed del input) | **cerrado** — pasa a alimentar `anchorRestMs` desde el ancla |
| `CoordinatorParkingDetector:2411` → `ParkingSignals` → `CalculateParkingConfidenceUseCase` | **exento con razón** — scoring ATENDIDO en fase pre-egress: ahí teléfono = coche y el reloj corto es el correcto |
| `DetectionEvent.LocationFix` / `DetectionEventDto` | **exento** — telemetría cruda; conservarla es lo que permitió este diagnóstico |
| Tests de los dos use cases | **cubierto** — actualizados en esta tarea |

`grep -rn "anchorCapturedAtStop"` → todos los usos actuales son la maquinaria de captura/pinned del
propio coordinator; el nuevo lector (feed del input) respeta su contrato (nullable, se limpia con
`shouldClearBestStop`).

## Registro

- 2026-08-19 — abierto tras el diagnóstico del field 18-08 (sesión `1787075310656`). Worktree + rama creados.
- 2026-08-19 — **implementado, sin commitear.**
  - `UnattendedSaveInput.stoppedDurationMs` → `anchorRestMs` (kdoc con el field que lo motivó);
    las dos ramas de `sustainedStop` (GAP_ANCHOR, WALK_ENTERED_ANCHOR) leen el reloj del ancla.
  - Coordinator: `anchorRestMs = now − anchorCapturedAtStop` (solo con ancla pinned) en la
    construcción del input; el log del timeout estampa `carRest=` junto a `stopped=` para que las
    trazas muestren ambos relojes.
  - Regresión `should_saveBoundedZone_when_carRestsAtPinnedAnchorWhileThePhoneStopClockKeepsResetting`
    **verificada ROJA sin el fix** (única en fallar: 23 tests, 1 failed) y verde con él.
  - **1223 tests verdes** (1222 en master + 1). `compileProdDebugKotlinAndroid` y
    `compileMockDebugKotlinAndroid` OK.
  - `docs/detection/PARKING-DETECTION.md` Sección 2 actualizada.
  - Sin strings nuevos, sin pantalla/estado nuevo (Dev Catalog sin cambios), sin provenance nueva
    (`unattended_zone_*` ya existen; solo pasan a ser alcanzables con GPS indoor ruidoso).
