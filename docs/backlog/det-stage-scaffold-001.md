# DET-STAGE-SCAFFOLD-001 · P3.0 — la precedencia deja de ser dónde cae el código

**Estado:** ✅ Done (2026-08-25) · rama `refactor/DET-STAGE-SCAFFOLD-001-p3-0` ·
worktree `../Paparcar-stage-0`

Paso **P3.0**, el andamio que abre la **Fase 3**. Sigue a `b42f0d01` (P2.6, cierre de Fase 2).
**No mueve ninguna rama todavía.**

## El problema

El bucle evalúa **diez ramas por fix** y gana la primera que aplica. Ese orden **ES conducta**
—permuta dos entradas y se planta un pin distinto— y hasta ahora era **la posición física de los
bloques dentro de un método de 700 líneas**.

Peor: el KDoc que lo documenta **miente** [08 §10.3]. Omite el hold, que corre PRIMERO, y llama
short-circuit a la rama del user-confirm cuando hay **tres** ramas que la superan.

## Qué entra

`domain/detection/stages/SessionStage.kt`:

- `DetectionStage` — el orden como enum, y cada entrada **dice por qué supera a la de abajo**.
- `detectionStageOrder` — la lista literal.
- `SessionStage` — la interfaz: una etapa **decide, no actúa**.
- `StageVerdict` — `Skip` / `Handled(newState, effects, stopsIteration)`.
- `DetectionEffect` — el sealed de lo que una etapa PIDE: `Confirm`, `AskUser`, `Prompt`,
  `DismissPrompt`, `ResolveVehicle`, `EndSession`.

## El orden se declara DOS veces, a propósito

Una como orden de declaración del enum, otra como `detectionStageOrder`. **Dos enunciados del mismo
orden que tienen que coincidir**, así que permutar cualquiera de los dos por separado falla.

| Neutralización | Resultado |
|---|---|
| permutar solo la LISTA | 🔴 2 tests |
| permutar solo el ENUM | 🔴 1 test |

El criterio del plan pide que falle al permutar dos entradas. Se cumple **en las dos direcciones**,
no en una — que es la diferencia entre un test del orden y un test de una copia del orden.

## El orden no está inventado

Es el **medido**, el que estableció `StagePrecedenceCharacterizationTest` (P0.1), cuyos cuatro tests
discriminantes fijan cada uno un par adyacente y fueron verificados fallando al permutarlo:

| Par | Test que lo prueba |
|---|---|
| hold ≻ user-confirm | `should_plant_the_held_pin_not_the_answer_fix_when_the_user_says_yes_during_a_hold` |
| false-ENTER ≻ user-confirm | `should_abort_the_false_enter_even_when_the_user_already_said_yes` |
| no-movement ≻ user-confirm | `should_fold_the_no_movement_budget_even_when_the_user_already_said_yes` |
| atribución ≻ fast-confirm | `should_resolve_the_vehicle_before_confirming_within_the_same_fix` |

`StageOrderTest` los registra como afirmaciones de adyacencia, para que el día que alguien reordene
la lista **el diff apunte al test que lo refuta**, no a una opinión.

## `DetectionEffect` no entra como decoración

Nadie lo emite todavía —P3.0 es andamio— así que `StageScaffoldTest` **mapea cada método de I/O que
el coordinator ejecuta hoy** sobre el efecto que lo sustituirá, con un `when` exhaustivo:

| Efecto | Sustituye a |
|---|---|
| `Confirm` | `runConfirm` / `beginConfirm` / `saveUnattendedZone` |
| `AskUser` | `nudgeUnattended` / `closeHumanPoweredRide` |
| `Prompt` | `notifyParkingConfirmation` / `degradeToPrompt` |
| `DismissPrompt` | `notificationPort.dismiss` |
| `ResolveVehicle` | el lookup del repo dentro de la rama de atribución |
| `EndSession` | los pares `completed = true` / `return@collect` |

Un arma que nadie haya pensado aparece como hueco **ahora** y no como sorpresa en P3.11. Es la misma
técnica que `SavedParkingShapeTest` en P1.10.

`StageVerdict.Handled` además hace explícitas las dos cosas que hoy dependen de si un bloque va
seguido de un `return`: si pide efectos y si termina la pasada.

## Doctrina

Ninguna tocada. **Cero cambio de conducta** — nada llama a nada de esto.

## Tests

`StageOrderTest` (5) + `StageScaffoldTest` (2). **1.629 tests**, 0 fallos.
`assembleMockDebug` ✅.

Siguiente: **P3.1**, `ConfidenceScoringStage` — la primera etapa, y se mueve la ÚLTIMA de la lista
a propósito: así las de arriba siguen viendo exactamente lo que esperan mientras tanto. Criterio
común de la fase: **cada etapa hereda los tests del bloque que muda, sin editarlos**, y los tests de
precedencia de P0.1 siguen verdes en cada commit.
