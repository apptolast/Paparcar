# DET-BACKFILL-CANNOT-PIN-A-MOVING-FIX-001 · una plaza no se coloca sobre un fix que se está moviendo

**Estado:** ✅ Done · mergeado a master por squash · worktree y rama retirados

> Rebasada dos veces el 27-08-2026: sobre `46c7bad4` sin conflictos, y luego sobre `96afe146`
> con un conflicto **puramente aditivo** en `docs/detection/PARKING-DETECTION.md` (master había
> añadido las entradas de DET-HUMAN-POWERED-VETO-MUST-BE-REVOCABLE-001 y
> DET-CADENCE-STEPS-ARE-INVISIBLE-TO-TELEMETRY-001 al final del mismo fichero); se conservaron las
> tres entradas. Verde tras cada rebase.

## Problema

Field 2026-08-27, Oppo (`fiypNbElGlfFexLMpU9sNaMjRMD3`), camino del fisio. El user describe el pin
como *«un FALSO POSITIVAZO en Dia · Calle Ronda del Puerto 15; reduce algo de velocidad para entrar
en la rotonda pero nada de aparcamiento»*.

Pin `724befda-f973-411a-b360-18465199a938` · 12:29:18 · `36.6027462,-6.2568375` ·
`detectionPath = safety_net_backfill` · `armEvidence = null` · fiabilidad 0,5 · `routePolyline` null.

Del `parkdiag` del propio móvil:

```
12:29:17.911  ARReceiver:  → IN_VEHICLE ENTER
12:29:17.988  OneFix: fix lat=36.6027462 lon=-6.2568375 speed=2.116912m/s acc=2.5m age=0s
12:29:17.994  Service:  ⊘ AR ENTER not armable (TickOnly, lag=227ms) — evaluator's call
12:29:18.217  SafetyNet: ▶ far with vehicle evidence — dispatching departure geofence=e1cb2b34
                          (preconfirmed=true steps=4 d=2008m)
12:29:18.220  SafetyNet:   → chaining parking backfill at wake-up fix (steps=4 acc=2.5, arrivalWalk=4 steps)
12:29:18.568  Backfill:  ✓ backfilled parking at 36.6027462,-6.2568375 (reliability=0.5)
```

Liberar la plaza de casa era **correcto** (2 008 m, con prueba de vehículo). Encadenar un backfill
que planta un pin NUEVO en el fix de despertar, no: **ese fix declara `speed=2.116912m/s` (7,6 km/h)
en su propia línea de log**. Cero reposo medido; 4 pasos de `arrivalWalk` como toda prueba de que el
cuerpo se había bajado.

### El autotestigo

La propia app se desdice 63 segundos después:

```
12:29:36  ExitWitness: ⚑ EXIT emitted geof=724befda ... (la valla del pin recién creado)
12:29:36  Depart: attempt=0 geof=724befda speed=44.392323km/h → Inconclusive(exit_echo)
12:30:21  Depart: attempt=2 geof=724befda speed=16.297134km/h → Confirmed
12:30:21  ClearActiveParkingSessionWorker: ■ SUCCESS session=724befda
```

Procesó la **salida** de una plaza que nunca se ocupó. El registro del histórico se queda igual, con
`isActive:false` y sin dirección — indistinguible para el user de un aparcamiento real.

## Doctrina violada

Las dos mitades de la doctrina rectora, a la vez:

- ***El evento NOMINA, solo el movimiento MEDIDO confirma.*** El `IN_VEHICLE ENTER` se declinó bien
  como ARME (`⊘ AR ENTER not armable`) y acto seguido se aceptó como prueba suficiente para
  **COLOCAR UNA PLAZA**. El mismo evento no puede ser insuficiente para armar y suficiente para
  pinchar.
- ***Fallo asimétrico: mejor un falso negativo que un falso positivo.*** Ante cero reposo medido
  correspondía preguntar, no plantar.

Y lo llamativo: el KDoc de `arrivalWalkSteps` **ya enuncia la regla correcta** —
*«Better a late question than a pin at a traffic light»* (`EvaluateSafetyNetCheckUseCase.kt:264`).
La intención estaba escrita; la condición no la comprueba.

## Señales / datos disponibles — no hay que instrumentar nada

`backfillBounded` (`EvaluateSafetyNetCheckUseCase.kt:271`) ya recibe el `fix` entero y exige tres
cosas de él y de los pasos:

| Condición actual | Valor el 27-08 | ¿Filtró? |
|---|---|---|
| `trustedStepsSinceAnchor != null && <= backfillMaxSteps` | 4 | no |
| `fix.accuracy <= minGpsAccuracyForDriving` | 2,5 m | no |
| `arrivalWalkSteps != null` (>0 y ≤ tope) | 4 | no |
| **velocidad del fix** | **2,117 m/s** | **no se mira** |

El proyecto ya tiene su propia definición de reposo: `stoppedSpeedThresholdMps = 1f`
(*«Speed (m/s) at or below which the vehicle is considered fully stopped. 1 m/s ≈ 3.6 km/h — above
pure GPS noise, below the slowest real creep speed»*). El fix de hoy la triplica.

## Diseño

El invariante vive en **un solo sitio**: `backfillBounded` es un único valor calculado que alimenta
las **cinco** construcciones de `SafetyNetAction.DispatchDeparture` del fichero. Añadir la condición
ahí las cubre todas, sin tocar ningún consumidor ni añadir umbral nuevo.

```kotlin
val backfillBounded = trustedStepsSinceAnchor != null &&
    trustedStepsSinceAnchor <= config.backfillMaxSteps &&
    fix.accuracy <= config.minGpsAccuracyForDriving &&
    fix.speed <= config.stoppedSpeedThresholdMps &&   // ← una plaza se coloca en reposo MEDIDO
    arrivalWalkSteps != null
```

**Por qué la velocidad del propio fix y no otra cosa.** El backfill coloca el pin *exactamente en
ese fix*. La pregunta que tiene que contestar no es «¿ha conducido esta sesión?» (eso ya lo contestan
las ramas de salida, y contestarlo bien es lo que hace que la salida se despache) sino **«¿está el
coche quieto AQUÍ, donde voy a clavar la plaza?»**. La única medición que responde eso es la
velocidad de ese fix. `stoppedSpeedThresholdMps` es el umbral que el resto del detector ya usa para
esa misma frase, así que no se introduce calibración nueva.

**Qué pasa cuando no se cumple.** Exactamente lo que ya pasa hoy con `arrivalWalkSteps == null`: la
salida **se sigue despachando** (la plaza vieja se libera igual, que era la parte correcta) y la
llegada cae en `DET-ARRIVAL-HANDOFF-001` → detección viva o prompt. No se pierde nada: se deja de
inventar una posición.

### Alternativas descartadas

- **Exigir más pasos de `arrivalWalk`.** No discrimina: el problema no es cuántos pasos hay, es que
  el coche estaba rodando. Con 40 pasos y el coche en marcha el pin seguiría estando mal.
- **Filtrar por `isCredibleDrivingSpeed`.** Al revés de lo que hace falta: contesta «¿esto es
  conducir?», y la banda de duda (1–5 m/s) es justo donde cayó el fix de hoy.
- **Retractar el pin cuando la salida se confirma 63 s después.** Cura el síntoma tarde y deja al
  user viendo un pin fantasma mientras tanto. Va aparte como follow-up (ver abajo).

## Criterio de éxito

- Test: `DispatchDeparture` con pasos, precisión y `arrivalWalk` válidos pero **fix a 2,117 m/s**
  (los números exactos del 27-08) → `backfillBounded == false`.
- Test de regresión: el mismo caso con el fix a 0,0 m/s → `backfillBounded == true` (el backfill
  legítimo, que es la razón de existir de la red de seguridad, sigue funcionando).
- Verificar que el test discrimina: neutralizando el guard nuevo, el test se pone **rojo**.
- Campo: pasar por una rotonda a 7 km/h tras un despertar por AR no debe crear ningún pin.

## Consumidores auditados

`grep -rn "backfillBounded" composeApp/src --include=*.kt`

| Sitio | Clasificación |
|---|---|
| `EvaluateSafetyNetCheckUseCase.kt:271` — el cálculo | **cerrado** — es el punto único donde vive el invariante |
| `:308` · `:346` · `:383` · `:410` · `:422` — las 5 `DispatchDeparture` | **cubiertos por convergencia** — todas leen el mismo valor |
| `ParkingSafetyNetWorker.kt:421` `if (action.preconfirmed && action.backfillBounded)` | **cerrado** — la vía donde mordió el 27-08 |
| `ParkingSafetyNetWorker.kt:463` `backfillChained` | **cubierto por convergencia** — misma conjunción |
| Confirmación por el Coordinator (`ConfirmParkingUseCase` desde la estrategia) | **exento** — llega por conducción medida y egress, no por esta rama |
| Estrategia Bluetooth | **exento por construcción** — no consume la red de seguridad |

## Follow-up deliberado (fuera de alcance)

`PARK-RETRACTED-BACKFILL-MUST-LEAVE-NO-PIN-001`: cuando la salida de un pin de backfill se confirma
a los pocos segundos de crearlo, el `ClearActiveParkingSessionWorker` cierra la sesión pero **deja el
registro en el histórico**. Se abre como ticket propio.
