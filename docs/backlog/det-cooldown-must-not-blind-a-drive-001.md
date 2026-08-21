# DET-COOLDOWN-MUST-NOT-BLIND-A-DRIVE-001 · El amortiguador de despertares no puede tapar un viaje

**Estado:** ✅ Done · en master vía squash · **1347 tests verdes** (24 en `SentryWakeCooldownTest`,
eran 10) · `prod` y `mock` compilan · ⏳ **validar conduciendo** (el criterio de campo, abajo)

## Problema

Field 2026-08-21, noche, los dos móviles. Dos pérdidas distintas, **una sola cadena**:

```
caminas hacia el coche → 8 pasos antes de velocidad de conducción
  → ⊘ false-ENTER abort  [BUG-FALSE-ENTER-WALKING]  → la sesión MUERE
  → 3 abortos "seguidos"  → re-arm cooldown  [DET-SENTRY-COOLDOWN-001]  → sensor dormido, GPS off
  → el viaje entero cabe dentro del silencio
```

### Caso A — Redmi, FN completo en Covirán (uid `WZB7oft…`)

| Hora local | Qué pasó |
|---|---|
| 22:10:38 | `GEOFENCE_EXIT` geof=`8125ce5c` entregado **d=398 m** (lejos, sin autoridad instantánea) |
| 22:10:46 | `⊘ false-ENTER abort — 11 steps before driving speed` |
| 22:10:58 · 22:11:20 · 22:11:53 | tres sentry-wakes más, los tres `aborted_false_enter` |
| **22:12:00** | `re-arm cooldown for 180s — walking-abort storm damper` · streak=3 |
| ~22:12:30 → 22:15:38 | **el viaje**. Sensor suprimido, GPS apagado, cero medición |
| 22:15:38 | `sync → armed=true` — ya había aparcado en Covirán |
| 22:15:41 | primer fix: `36.6143368,-6.2865144` · `d=966 m` del pin |

Resultado: **ninguna sesión sobrevivió al trayecto**, cero pines. La sesión vieja de Star Petroleum
(`8125ce5c`) se quedó activa hasta las 23:44.

### Caso B — Oppo, el pin acabó en casa (uid `fiypNbE…`)

| Hora local | Qué pasó |
|---|---|
| 23:38:51 | `⊘ false-ENTER abort — 14 steps before driving speed` (andando al coche en Covirán) |
| **23:38:55** | `re-arm cooldown for 180s` · streak=3 |
| 23:39 → 23:46 | **el viaje de vuelta, 988 m**. 7 min 43 s ciego |
| 23:46:38 | la red de seguridad periódica (rejilla de 15 min) despierta ya en casa: `far with vehicle evidence — dispatching departure (steps=206 d=988m)` |
| 23:46:38 → 23:50:47 | `ARRIVAL_HANDOFF` corre 4 min con el móvil **quieto** (63 fixes, vmax 0,49 km/h) |
| 23:50:47 | honest close → `closed_approximate_pin` en `36.6083588,-6.2781826` = **la casa**, no el punto de bajada |

Los tres abortos que dispararon el cooldown del Oppo fueron a las **22:46:56, 22:59:48 y 23:38:15**
— repartidos en **52 minutos**. Eso no es una tormenta.

### Por qué no lo cazó nada más

`SentryWakeCooldown.kt:17-19` justifica el amortiguador diciendo que *«la valla EXIT, el carril AR y
la red de seguridad periódica siguen mirando»*. Esta noche **ninguno de los tres miró**:

- **Valla EXIT** — la de Covirán nunca la entregó el OS; la del Redmi ya se había consumido a las
  22:10:38 y él estaba 389 m **fuera**: no quedaba valla que pudiera emitir un EXIT.
- **AR** — no dio `IN_VEHICLE ENTER`. Dio `ON_BICYCLE ENTER` a las 22:13:12 en pleno trayecto en
  coche (ver §Hallazgos).
- **Red de seguridad** — en el Oppo el heartbeat exacto de 5 min lleva muerto desde las 18:28 (un
  único disparo suelto a las 21:16). Sólo quedó el worker de 15 min: checks a las 23:15:21 →
  **23:46:38**. El viaje cupo entero en una celda de la rejilla.

## Doctrina violada

**«Todo trigger dispara SIEMPRE.»** No se violó descartando un evento: se violó **apagando el
sensor que los produce**. El efecto es el mismo — un viaje entero sin un solo trigger.

Y una premisa falsa: el amortiguador se autorizó a dormir al sensor *porque otros carriles vigilan*.
Esa premisa **no se comprueba en ningún sitio**. Cuando es falsa, silencia al último nominador vivo.

## Señales / datos disponibles

Todo está ya en el punto donde se decide el cooldown (`resolveIdleEpilogue`):

- `parkedSessions: List<UserParking>` — con `location` y `sizeCategory`.
- `config.geofenceRadiusFor(sizeCategory, accuracy)` — el radio real de cada valla.
- `parkingDetectionCoordinator.lastSessionFix` — dónde estaba el cuerpo al abortar.
- El reloj, para fechar el aborto anterior (hoy no se guarda).

No hace falta ninguna señal nueva del hardware.

## Diseño

Dos cambios, ambos **funciones puras en `domain/detection/SentryWakeCooldown.kt`** — donde ya vive
la política del amortiguador. Son predicados que alimentan a un veredicto ya existente, así que no
llevan `Evaluate*UseCase` propio [DET-VERDICT-NOT-PREDICATE-001].

### 1 · Una tormenta es una CADENCIA, no un recuento

`nextSentryWakeAbortStreak` pasa a recibir `msSinceLastAbort`. Si el aborto anterior queda fuera de
`config.sentryWakeStreakDecayMs`, este aborto **empieza racha nueva** (devuelve 1, no acumula).

La tormenta del 13-08 era un aborto cada ~18 s. Un aborto cada media hora es la vida normal de
alguien que entra y sale de casa. El umbral de 3 nunca debió alcanzarse en 52 minutos.

### 2 · Silenciar al último nominador es quedarse ciego

`sentryWakeRearmCooldownMs` pasa a recibir `hasFenceThatCanStillFire`. Una valla sólo puede emitir
un EXIT mientras el teléfono siga **dentro** de ella; fuera de todas sus vallas, la moción
significativa es el último nominador que queda y el amortiguador **no se aplica**.

Nueva función pura `isInsideAnyOwnedFence(fix, parkedSessions, config)` para responderlo con el
mismo radio que registra `ConfirmParkingUseCase` — sin radio paralelo.

**Posición desconocida → no se amortigua** (fail-open). El coste de equivocarse hacia el silencio es
una plaza perdida; hacia el ruido, unos despertares de más. La asimetría manda.

### Verificación del diseño contra los datos reales

| Escenario | Δ desde aborto previo | ¿Dentro de valla? | Hoy | Con el fix |
|---|---|---|---|---|
| Tormenta 13-08 (≈130 sesiones) | ~18 s | sí (d=36 m, r=109 m) | amortigua | **amortigua** ✅ |
| Oppo 22:06:37 | ~30 s | sí (d=6 m, r=85 m) | amortigua | **amortigua** ✅ (y su EXIT cazó la salida a las 22:09) |
| **Oppo 23:38:55** | **38 min** | sí (d=11 m, r=83 m) | amortigua ❌ | **racha=1 → NO amortigua** ✅ |
| **Redmi 22:12:00** | ~25 s | **no (d=389 m, r=109 m)** | amortigua ❌ | **fuera de valla → NO amortigua** ✅ |

Los dos incidentes quedan cubiertos, cada uno por un cambio distinto, y el motivo por el que el
amortiguador existe queda intacto.

### Riesgo residual asumido (no se cierra aquí)

Si alguien encadena 3 abortos **rápidos** mientras camina **dentro** de la valla y acto seguido
conduce, y la valla no entrega su EXIT, seguimos ciegos. La cura de fondo es que el despertar
durante el cooldown no se suprima sino que se **abarate** (un solo fix, escalar a sesión completa
sólo si lee por encima del techo peatonal). Queda como follow-up propio, no se mete aquí.

## Criterio de éxito

- Tests nuevos en `SentryWakeCooldownTest` que reproducen las cuatro filas de la tabla anterior.
- Suite completa verde.
- En campo: un trayecto corto urbano precedido de caminata deja pin; y una vuelta por el barrio
  junto al coche sigue sin inundar de sesiones.

## Consumidores auditados

| Sitio | Clasificación |
|---|---|
| `CoordinatorDetectionService.resolveIdleEpilogue` (único call site de las dos funciones) | **cerrado** — pasa los dos parámetros nuevos |
| `SignificantMotionMonitor.applyRearmCooldown` | **cubierto por convergencia** — sigue recibiendo un ms; `0` = sin cooldown, ya soportado |
| `UserStopQuietPeriod` (DET-STOP-BUTTON-001) | **exento** — quiet period del usuario, no del amortiguador; es una orden explícita, no una inferencia |
| `SentryLifecycleDecision` / `enterSentry` | **exento** — decide residencia, no re-armado del sensor |

## Hallazgos del field-test que NO se arreglan aquí

1. **AR volvió a etiquetar un coche como bici** — Redmi `ON_BICYCLE ENTER` a las 22:13:12 en pleno
   trayecto urbano en coche. Confirma la hipótesis abierta del 20-08. No hizo daño (no había sesión
   viva) y `DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001` ya deja que la medición refute la etiqueta.
2. **El heartbeat exacto de 5 min está muerto en el Oppo** — último a las 18:28 del 21-08, uno
   suelto a las 21:16, nada después. Merece ticket propio.
3. **El honest close planta un PUNTO donde está el cuerpo** (`EvaluateHonestCloseUseCase.kt:368`,
   rama pin/zona decidida sólo por `abortFix.accuracy`). Con un hueco ciego de 8 min la respuesta
   honesta es una zona. Ticket propio.
4. **La plaza provisional comparte id con su sesión** (`ReleaseActiveParkingSessionUseCase.kt:43`,
   `ProcessConfirmedDepartureUseCase.kt:67`) y `HomeState.selectedSpot` cede ante `selectedSession`:
   la plaza es inseleccionable y abre la modal del coche. Ticket propio.
