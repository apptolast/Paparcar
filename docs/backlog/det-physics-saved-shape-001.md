# DET-PHYSICS-SAVED-SHAPE-001 · P1.10 — un solo nombre para lo que la sesión deja detrás

**Estado:** ✅ Done (2026-08-25) · rama `refactor/DET-PHYSICS-SAVED-SHAPE-001-p1-10` ·
worktree `../Paparcar-physics-10`

Paso **P1.10** de la Fase 1. Sigue a `46b83012` (P1.9).

## Qué introduce

Cuatro desenlaces, tres vocabularios:

| Forma | `EvaluateUnattendedParkingSave` | `EvaluateHonestClose` | `EvaluateParkingDecision` |
|---|---|---|---|
| pin | `SaveExact` | `ApproximatePin` | `Confirmed` |
| zona | `SaveZone` | `ApproximateZone` | — |
| preguntar | `Ask` | — | `Prompt` · `CloseHumanPowered` |
| callar | — | `KeepSilent` | — |

`physics/SavedParkingShape.kt` les da uno: `ExactPin` / `BoundedZone` / `AskUser` / `KeepSilent`.
Tres nombres para una pregunta es exactamente cómo una regla se arregla en una escalera y se olvida
en la otra — la historia literal del techo de la zona, que vivió en los dos caminos del coordinator
y no en el honest-close (`8bf6f02b`, luego P1.6).

## Nadie lo adopta, a propósito

El plan es explícito (07 §3.4.1, plan P1.10): hacer que los veredictos lo devuelvan cambia sus
firmas y sus tests, y eso va en la fase de veredictos.

Pero **un tipo sin usuarios es un cuarto vocabulario esperando a nacer**. Lo que sí entra ahora es
un **censo**: `SavedParkingShapeTest` mapea cada rama de los tres sealed existentes a través de un
`when` EXHAUSTIVO. Kotlin deja de compilar el fichero en cuanto alguien añade una rama a cualquiera
de ellos. La divergencia no puede crecer mientras la adopción espera.

Vive en el test y no en producción justamente porque en producción sería código muerto — y el
código muerto se borra, mientras que un test rojo se lee.

## Tres decisiones, cada una equivocada en silencio más adelante

### 1. La razón NO viaja en la forma

El boceto de `09 §6` daba a `AskUser`/`KeepSilent` un `reason: String`. Se cae.

`UnattendedSaveReason`, `HonestCloseVerdict.REASON_*` y `PromptReason` son **contrato de trazas**:
una traza de julio tiene que leerse igual en un build de septiembre, y por eso `UnattendedSaveReason`
ya se negó una vez a renombrar dos etiquetas históricas. Un campo `reason` compartido es una
invitación abierta a fundir los tres vocabularios dentro, que es lo que 07 §3.4.1 prohíbe **en la
misma frase** en que propone el tipo.

La forma dice QUÉ se guarda; el veredicto sigue diciendo POR QUÉ.

### 2. `BoundedZone` lleva el radio FINAL, no la duda

Es el único desacuerdo real que quedaba tras P1.6: `EvaluateHonestClose` convierte duda→radio
**dentro** del veredicto (EvalHC:419) mientras `EvaluateUnattendedParkingSave` emite `doubtMeters`
crudo y deja que el coordinator lo acote (CPD:2006). Los dos llaman a `honestZoneRadius` — pero solo
uno de los dos puede olvidarse de hacerlo.

Con el radio en el tipo, la conversión ocurre antes de emitir, en un sitio, siempre.

### 3. `Rejected` e `Inconclusive` NO son `KeepSilent`

La lectura descuidada las mete en el silencio y **no son lo mismo**: callar es *terminal, nada
guardado*, mientras que `Inconclusive` significa *todavía no, pregúntame en el próximo fix* y
`Rejected` descarta UN candidato dejando la parada viva. Aplanarlas compra un `when` más limpio y
una clase entera de falsos negativos: sesiones cerradas mientras seguían trabajando.

En cambio `ParkingDecision.CloseHumanPowered` **sí** es una forma, y hoy es `AskUser`, no silencio:
`closeHumanPoweredRide` lanza `UNATTENDED_HUMAN_POWERED_NUDGE`. Su propio KDoc dice que *debería*
pasar a silencio cuando DET-HANDOFF-NOT-MANUAL-001 §B deje de comprometer una salida sin prueba —
queda escrito en el censo para que ese cambio sea un diff de una línea con un assert rojo, y no
arqueología.

## Lo que el censo reveló de paso

Las tres ramas «pin» llevan **tres subconjuntos distintos** de la misma información: `SaveExact` no
lleva ni punto ni fiabilidad (el caller lee el ancla), `ApproximatePin` lleva solo el punto,
`Confirmed` solo la fiabilidad. La unión está disponible en los tres sitios de emisión; simplemente
se tira al suelo para que el caller la vuelva a adivinar. Por eso `ExactPin` lleva las dos.

## Verificado discriminante, no supuesto

| Neutralización | Resultado |
|---|---|
| añadir una rama a `HonestCloseDecision` **y satisfacer el `when` de producción** | 🔴 el censo no compila |
| mapear `Inconclusive` → `KeepSilent` | 🔴 `should_refuse_to_call_a_live_session_silent` |

La primera importa porque producción ya tenía su propia red exhaustiva: el compilador paraba antes
de llegar al test. Solo tras darle a `RunHonestCloseUseCase` su rama se ve que **el censo dispara
solo** — y pregunta algo distinto: producción obliga a *manejar* la rama, el censo obliga a *decir
qué forma es*.

## Doctrina

Ninguna tocada. **Cero cambio de conducta**: no cambia un solo call site de producción.

## Tests

`SavedParkingShapeTest` (6). **1.528 tests** (1.522 + 6), 0 fallos. `assembleMockDebug` ✅.

## Red de P0.4

```
tests 1528 - desaparecidos: 5 (los renombrados de P1.8, ya justificados) - nuevos: 79
```

Queda **P1.11** (`physics/SessionOutcome.kt`, el tipado del desenlace con serialización byte a byte
idéntica) para cerrar la Fase 1. Es el único `L` de la fase y el único con trampa: el desenlace
`aborted_unattended_human_powered` tiene DOS productores y el tipado **no debe** intentar arreglarlo
— es el bug #7 y tiene ticket propio.
