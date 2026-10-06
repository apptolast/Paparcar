# DET-A-MANEUVERING-CAR-IS-NOT-AN-EGRESS-001 · un coche buscando sitio al ralentí contaba como el conductor bajándose

**Estado:** ✅ Done — en master. Suite completa verde: **3858 tests, 0 fallos**
(`testAll`), más `:app:assembleMockDebug` y `:app:compileProdDebugKotlin`.

⛔ Sin captura: **sin resultado visual** [PR-A-VISUAL-CHANGE-SHOWS-ITSELF-001]. Cambia qué cuenta
como prueba de egreso; no hay pantalla, estado ni copy nuevos.

## Problema

Tercero de los tres falsos positivos del 05-10-2026 (Oppo CPH2371, app 1.4.0, carril COORDINATOR,
uid `0l7X59ptXeQ4WUMlpFOZHLPIJAI3`). El user lo describió así: *«eso fue un stop, aparqué un minuto
más tarde en C. Fray José Cordero»*.

Sesión `1791194402036` — el viaje real Granada → El Puerto, 198 km, 3 h 07 min. Al final:

```
15:04:39  FIX  36.593800,-6.239878  spd 0.00  acc 1.57   ← el coche para 4 s. ESTE punto se pinea
15:04:45  FIX  …                    spd 2.76             ┐
15:04:47  FIX  …                    spd 3.75             │ el coche rueda despacio
15:04:49  FIX  …                    spd 2.98             │ buscando aparcamiento
15:04:51  FIX  …                    spd 1.57             │
15:05:27  FIX  …                    spd 2.21             │
15:05:29  FIX  …                    spd 1.39             ┘ ← la SEXTA cierra el umbral
15:05:29  HOLD OPENED   kinematic+egress   lat 36.5938,-6.2398783
15:05:31 → 15:07:29  118 s con los fixes repitiendo 36.593522,-6.239660 IDÉNTICO, acc 1,5 m
15:07:29  HOLD SETTLED  sessionAgeMs=120004
15:07:29  DECISION CONFIRMED  kinematic+egress  conf=0.85
```

**Las dos pruebas de que nadie se bajó estaban ahí, y las dos son cero:** ni un solo evento `STEP`
en toda la ventana, y **cero metros** de desplazamiento durante los 118 s del hold — los fixes
repiten la misma coordenada. Un teléfono que sale de un coche se aleja.

**La causa, en una línea.** `StopTracking` contaba una fijación de egreso cinemático así:

```kotlin
location.speed < config.minimumTripSpeedMps   // 5 m/s = 18 km/h
```

La «banda peatonal» era *«todo lo que no llega a conducción»*. **Nadie anda a 18 km/h**, y entre
2,5 y 5 m/s sólo caben un coche maniobrando y un corredor.

## Doctrina violada

*El evento NOMINA, sólo el movimiento MEDIDO confirma* — en su forma más literal: el movimiento se
midió, pero se le atribuyó el sujeto equivocado. Y *fallo asimétrico*: ante dos hipótesis que la
señal no separa (peatón andando / coche rodando), el carril se quedó con la que planta pin.

## Señales / datos disponibles

**El techo peatonal ya existía y estaba documentado**, a 15 cm del sitio donde hacía falta:

```kotlin
/** … 2.5 m/s = 9 km/h sustained; brisk walking is ~1.7 m/s, running commuters
 *  don't carry a parked-car session. */
val maxPedestrianSpeedMps: Float = 2.5f
```

No hubo que inventar un umbral: la señal usaba el de la pregunta de al lado.

## Diseño

Un símbolo. La banda que afirma «esto es una caminata» pasa a ser la PEATONAL:

```kotlin
it.anchorTrust.frozenByRest &&
    location.speed < config.maxPedestrianSpeedMps &&      // era minimumTripSpeedMps
    isCredibleFixAccuracy(location, config.minGpsAccuracyForDriving)
```

Sobre los datos de campo: de las seis fijaciones que cerraron el umbral sólo sobreviven tres
(1,57 · 2,21 · 1,39) contra un `kinematicEgressMinWalkFixes = 6` → **no confirma**.

⛔ **Y no se apila un segundo guard.** La tentación era exigir además que el desplazamiento
CONTINÚE durante el hold (los 118 s a cero metros lo delatan). No hace falta y sería un parche sobre
un parche: el desplazamiento del coche rodando y el del peatón andando son indistinguibles en
metros — **lo único que separa las dos hipótesis con los datos disponibles es la velocidad**, que es
exactamente lo que este cambio corrige. Tocar también `confirmHoldMs` se descartó por lo mismo: el
hold funciona (en ese viaje descartó bien dos veces, a los 16 s y a los 102 s), y subirlo retrasaría
todos los aparcamientos buenos para comprar un caso.

## Criterio de éxito

- Una fijación a 3,75 m/s con el ancla congelada **no** cuenta como egreso. ✅
- Un peatón a paso vivo (1,7 m/s) **sigue** contando — el carril nació para los podómetros mudos
  (Redmi/MIUI) y perderlo sería un FN sistemático en ese hardware. ✅
- Las 16 trazas de campo del replay siguen aterrizando donde aterrizaban. ✅

**Testigo falsificado** [TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001]: revertido el símbolo,
`should_not_count_a_car_creeping_for_a_space_as_kinematic_egress` falla. Hacía falta, porque los
tests que ya existían ponían `kinematicEgressFixes` **a mano** y ninguno probaba cómo se incrementa
— por eso la suite entera pasaba con la banda mal.

## Consumidores auditados

`grep -rn "minimumTripSpeedMps" shared/src/commonMain` → 19 usos. La pregunta del barrido: *¿alguno
más lo usa para afirmar que el sujeto es un PEATÓN?*

| Uso | Qué afirma | Estado |
|---|---|---|
| `StopTracking:530` (contador de egreso cinemático) | «esto es una caminata» | 🔴 **cerrado por este ticket** |
| `DriveProof:187`, `FixReduction:104-132`, `StopTracking:162/338`, `AnchorPredicates:199`, `EvaluateHonestClose:254/280`, `EvaluateUnattendedParkingSave:296/329` | «esto es conducción» (`>=`) | ✅ exento — es su pregunta, y está bien planteada |
| `ConfirmParkingUseCase:239` (`tripMaxSpeedMps <`) | «la sesión NO vio conducción» | ✅ exento — es la negación de conducción, no una afirmación de caminata |
| `HoldResolutionStage:79` (`resumeSpeedBar`) | «volvió a conducir» | ✅ exento — ídem |
| `StopTracking:480` (mensaje de traza) | decía *«ignoring walking-range speed»* | 🟡 **corregido a `sub-driving`** — no decidía nada, pero es la frase que hizo elegir este umbral para contar caminatas |

## Follow-up detectado, fuera de alcance

**El ancla eligió una parada de 4 segundos teniendo una de 118 s a 37 m.** El pin cayó en
`36.5938,-6.2398783` (parado 15:04:39→15:04:43) mientras el coche descansó de verdad en
`36.593522,-6.239660` durante casi dos minutos. Es un invariante distinto —cómo se elige el ancla
entre varias paradas candidatas— y tiene su propio doc:
`docs/backlog/det-the-anchor-must-prefer-the-longer-rest-001.md`.
