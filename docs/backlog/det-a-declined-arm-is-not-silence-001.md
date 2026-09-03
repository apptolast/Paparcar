# DET-A-DECLINED-ARM-IS-NOT-SILENCE-001 · un arm declinado no es silencio

**Estado:** ✅ En master — implementado en `748648fc` (1 commit(s) citan el ID · 5 fichero(s) de código lo referencian) · línea añadida por el barrido [DOCS-BACKLOG-TRUTH-002] del 03-09-2026, verificado por COMMIT y por referencias en código, **no** releyendo su criterio de éxito uno a uno. ⏳ El propio doc anota pendientes: leerlo antes de darlo por terminado del todo.

`bugfix/DET-A-DECLINED-ARM-IS-NOT-SILENCE-001-ar-recheck` · ⏳ **sin validar en campo**

## Problema

Field 30-08 21:20:42 (Oppo). Llega un AR `IN_VEHICLE_ENTER` **fresco** (lag 232 ms) con el fix a
**143 m** del coche aparcado. La escalera lo declina:

```
21:20:42.846  ⊘ AR ENTER not armable (TickOnly, lag=232ms) — evaluator's call [DET-AR-FIRST-001]
21:20:42.856  ⏾ enterSentry(post-ACTION_AR_TRANSITION) — resident, GPS off
              ... 6 min 51 s y 3,6 km sin que nadie mire ...
21:27:33      la red de 15 min despierta, ya a 3 652 m del coche
21:27:41      llega el GEOFENCE_EXIT — 7 min tarde
```

**El decline es CORRECTO** y no se toca: el AR dispara con cualquier vehículo (bus, taxi), y armar
con él sería el evento confirmándose a sí mismo. El defecto es **lo que venía después: nada**.

## ⛔ Por qué esto NO es un fallo estructural (medido antes de tocar código)

De los 5 `TickOnly` del día entre los dos móviles, **3 quedaron cubiertos por el `GEOFENCE_EXIT` en
menos de 100 ms**:

| | TickOnly | GEOFENCE_EXIT arma | Δ |
|---|---|---|---|
| Oppo | 23:25:39.462 | 23:25:39.555 | **93 ms** |
| Redmi | 19:45:59.282 | 19:45:59.369 | **87 ms** |
| Redmi | 20:35:59.985 | 20:36:00.068 | **83 ms** |
| Oppo | **21:20:42.846** | **21:27:41.764** | **🔴 6 min 59 s** |

(el 5.º ocurrió con detección ya corriendo → inofensivo)

**Por qué el cuarto llegó tarde.** ⛔ **NO es "porque el GPS estaba apagado"** — lo estaba en los
cuatro: `enterSentry — GPS off` se ejecuta en el mismo milisegundo en que llegan los EXITs rápidos
(Oppo 23:25:39.474 EXIT / .495 GPS off; Redmi 20:36:00.008 GPS off / .023 EXIT). **El geofence de
Play Services dispara con nuestro GPS apagado — está diseñado para eso y funciona incluso con el
proceso muerto.**

Lo que distingue al caso lento es el **re-registro reciente de la valla**:

| Valla | Último re-registro | Latencia del EXIT |
|---|---|---|
| `785dabe3` (el lento) | **2 min 21 s antes** de la salida | 🔴 **6 min 59 s** |
| `a15b7e3a` | 65 min antes | 93 ms |
| `f678420b` | nunca | 83 ms |
| `47882636` | nunca | 87 ms |

La única valla que falló es la única que se había re-registrado justo antes de que el usuario se
fuera (`inside fence — re-registering geofence=785dabe3 (poisoned 1213s ago)`, 21:18:21). Play
Services necesita tiempo para asentar el estado de una valla recién registrada, y sin fixes de GPS
propios sólo tiene wifi/celda para hacerlo — el GPS apagado **agrava** la latencia, no la causa.

**Dimensionamiento**: la app ya telemetriza esta latencia (`⚑ GEOFENCE_EXIT delivered FAR from
fence`) y el 30-08 saltó **8 veces** (5 Oppo + 3 Redmi), una de ellas a 474 m. O sea, el EXIT tardío
no es un rarísimo: es un régimen conocido, y este ticket es la red para cuando se dispara.

Así que la segunda puerta existe y normalmente cubre a la primera al instante. Este ticket cubre el
caso en que no lo hace.

## Doctrina

Se aplica **al pie de la letra, no se dobla**: *el evento NOMINA, sólo el movimiento MEDIDO
confirma.* El embarque declinado es la razón para **MIRAR**, nunca la razón para armar. Lo que arma
es la conducción medida 90 s después.

⛔ **No se baja el listón del arm.** `EvaluateArEnterArmUseCase` queda intacto: `TickOnly` sigue sin
armar. La regla escrita en CLAUDE.md (*"la escalera sólo arma si el embarque está atado al PROPIO
coche"*) sigue vigente palabra por palabra.

## Diseño

1. **`shouldArmAfterDeclinedBoarding(fix, session, config)`** — función pura en
   `domain/detection/DeclinedBoardingRelook.kt`. Dos gates, ambos reutilizando predicados existentes:
   `config.isCredibleDrivingSpeed(...)` y `isWithinFence(...)`. **Cero constantes de calibración
   nuevas.** No es un `Evaluate*UseCase` [DET-VERDICT-NOT-PREDICATE-001].
2. **`DeclinedBoardingRelookWorker`** — encolado sólo desde `TickOnly`, delay **90 s**, trabajo único
   por geocerca (`REPLACE`). Salta si ya hay detección viva o si el aparcamiento ya no existe. Coste:
   **un fix**, ~5 veces/día entre los dos móviles.
3. **`ArmEvidence.BoardedAwayFromCar`** (`ArmLabel.BOARDED_AWAY`, `boarded_away`) →
   **`DriveAuthorization.None`**, `isVerifiedDeparture = false`,
   `confirmsSilentlyWithoutMeasuredDrive = false`. La sesión sigue el viaje a plena calidad pero **no
   puede guardar un aparcamiento en silencio**: al final del trayecto PREGUNTA.
   **Un viaje en autobús cuesta una pregunta, jamás un pin fantasma** — la misma asimetría que
   protege el propio decline.

### Por qué 90 s y no evaluar en el ENTER

Porque el fix del embarque **no puede decidir**: el real de las 21:20:42 leía `speed=0.22708045 m/s`
— el usuario aún andaba, o acababa de arrancar. Una prueba de velocidad en ese instante responde
"no" y es exactamente igual de ciega que el silencio que sustituye. Ese fix real es un test
(`should_notArm_when_judgedAtTheBoardingFixItself`): si algún día pasa a verde, el retardo ha dejado
de sostener el diseño y hay que releerlo entero.

### Por qué no se reutiliza `DepartureDetectionWorker`

Era la opción tentadora — ya mide en reintentos a 15/30/60 s y `ArmMidTrip` lo encola. **Se descartó
al leerlo**: ese worker *procesa una salida*, y procesarla libera la plaza. Encolarlo desde
`TickOnly` liberaría tu plaza cada vez que te subieras a un autobús a 143 m de tu coche. `ArmMidTrip`
puede permitírselo porque exige `recentStaleExitRecorded` — un EXIT de TU valla que ata el movimiento
a TU coche. `TickOnly` no tiene esa ancla.

## Consumidores auditados

- ✅ `EvaluateArEnterArmUseCase` — **sin cambios**. El decline se respeta.
- ✅ Rama `TickOnly` de `handleArTransition` — único sitio que encola el re-look. Las otras tres
  decisiones de esa misma rama (`NoSession`, `StaleEnter`, `NoFix`) **quedan exentas con razón**:
  significan *"la evidencia era inutilizable"*, y mirar otra vez sería mirar a nada. Sólo `TickOnly`
  significa *"alguien se subió a un vehículo y no es demostrablemente el tuyo"*.
- ✅ `ArmLabel` / `ArmEvidence` — los 4 `when` exhaustivos obligaron a declarar el arm nuevo; el
  guard de población `ArmLabelTest` **falló hasta registrarlo en `allArms`**, exactamente como debía.
- ⚪ `TriggerDisposition.NOT_ARMABLE` — sigue siendo write-only (nadie lo lee). No se cambia: el
  re-look no lo consulta, se encola directamente.
- ⚪ El safety net, el heartbeat exacto y el worker de 15 min **no se tocan**: siguen siendo el
  backstop, y lo son también cuando el OS deniega el arranque en background del re-look.

## Estado de verificación

- ✅ `:shared:testDebugUnitTest` → **2.044 tests** (2.036 de base + 8 nuevos).
- ✅ **Falsación de los DOS gates**: desactivar el de la valla → `…InsideTheCarsOwnFence` ROJO;
  desactivar el de velocidad creíble → 3 tests ROJOS (incluido el del fix real de campo).
- ✅ `:app:compileProdDebugKotlin` + `:app:compileMockDebugKotlin`.
- ⏳ Sin strings, sin pantalla ni estado MVI nuevos → no toca los 9 locales ni el Dev Catalog.
- ⏳ **Sin validar en campo.** Lo que hay que mirar en el próximo viaje: la línea
  `⏱ boarding declined away from the car — one re-look scheduled` y, 90 s después, si dice
  `✓ re-look MEASURED driving` o `⊘ re-look says nothing is driving`.

## Riesgo asumido

El re-look arranca un FGS desde background, que Android 12+/OEM puede denegar. En ese caso se
registra y no se hace nada más: se vuelve exactamente al comportamiento anterior a este ticket, con
el safety net de backstop. **No se muestra prompt**: no hemos probado que el usuario dejara SU coche,
sólo que algo conduce — preguntar sobre eso sería ruido.
