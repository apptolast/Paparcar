# DET-ORCHESTRATOR-ASSEMBLY-001 · P3.13 — el orquestador, y el borrado

**Estado:** ✅ Done (2026-08-26) · rama `refactor/DET-ORCHESTRATOR-ASSEMBLY-001-p3-13` ·
worktree `../Paparcar-orchestrator`

> **Con esto la FASE 3 queda CERRADA** (14/14 pasos). Lo que sigue es la Fase 4 — los tres cambios
> de conducta, todos marca `C` — y la Fase 5, la puerta de entrada. Y la puerta G1 sigue cerrada:
> **la Fase 3 entera no se ha conducido.**

Paso **P3.13**, el último de la Fase 3. Sigue a `d7299f4d` (P3.12).

## Problema

`CoordinatorParkingDetector.kt` seguía teniendo **2.344 líneas** después de mover diez etapas, el
ejecutor y el tap. El plan [10 §5 P3.13] daba por hecho que a estas alturas *«el fichero viejo está
vacío»* y que el paso sería ensamblar 235 líneas y borrar. **No lo estaba**, y saber por qué es el
trabajo de este ticket: lo que quedaba no eran restos de las etapas, eran **cuatro poblaciones que
nunca tuvieron dueño** y que las fases 1–3 no tocaron porque ninguna de ellas era una etapa.

| Población | LOC | Por qué seguía ahí | Dónde vive ahora |
|---|---|---|---|
| `updateStopTracking` | ~370 | Es el reducer del fix, no una etapa: corre ANTES de la precedencia | `state/StopTracking.kt` |
| 12 predicados del ancla | ~285 | El reflejo de [DET-VERDICT-NOT-PREDICATE-001] | `state/AnchorPredicates.kt` |
| `runStageEffects` | ~220 | El despachador de efectos: ni la decisión ni el I/O | `DetectionEffectDispatcher.kt` |
| 4 constructores de input | ~160 | Presentados a las etapas como lambdas del coordinator | `stages/StageInputs.kt` |

## Doctrina violada

`⛔ Un caso de uso por VEREDICTO, nunca por PREDICADO` [DET-VERDICT-NOT-PREDICATE-001], en su mitad
menos citada: *«un predicado NO se queda como método privado del coordinator sólo porque estuvieras
editando ese fichero»*. Los doce predicados de aquí eran literalmente esa frase, y ninguno era un
veredicto: ni uno aparece en `detectionPath`, `outcome`, `armEvidence` ni `sessionOutcome`.

## Las cuatro cosas que el plan NO decía — qué se hizo con cada una

### 1 · El snapshot muerde → el bucle de un solo escritor ✅

Las etapas recibían el estado del PRINCIPIO de la iteración y esa foto caducaba tres veces dentro
del mismo fix: P3.1 perdió un `pendingConfirm` puesto microsegundos antes, P3.3 estampó la línea de
frescura sobre un contador que se había movido, y P3.6 leyó un `vehicleId` nulo **en el fix que lo
resolvía** — y guardó la plaza a nadie. Cada una se parcheó donde mordió.

Ahora **una etapa lee el estado tal como lo dejó la etapa de encima**. Eso es lo que los tres
parches suplían, y es lo que hace que la precedencia signifique lo que dice: *«el hold gana al
user-confirm»* sólo es verdad si el user-confirm PUEDE VER lo que hizo el hold.

⚠️ **El write-back sigue siendo estrecho, y no por inercia.** El colector de pasos es un segundo
escritor de verdad y siempre lo será — es un stream de sensor. Un `newState` calculado hace
microsegundos no se puede asignar entero sin pisar un paso contado en medio. Las etapas sólo cambian
`confirmation.phase` (idempotente frente a esa carrera); todo lo definido RELATIVO a un contador
sigue viajando como `DetectionEffect` y se aplica al estado vivo.

**Y el orden dejó de ser una segunda opinión.** El bucle recorre `detectionStageOrder` en vez de
tener nueve llamadas escritas a mano. Antes `StageOrderTest` comparaba la lista con el enum y **las
dos podían estar de acuerdo mientras el bucle hacía otra cosa**.

> **Verificado discriminante:** permutando `HOLD_RESOLUTION` y `USER_CONFIRM` en la lista,
> `StagePrecedenceCharacterizationTest` se pone **rojo en 3 de sus 6 tests**. Antes de este paso la
> misma permutación no habría cambiado ni una línea de conducta.

### 2 · `sessionOutcome` y `completed` se mudan JUNTOS ✅

Prometido desde P1.11 y P2.2. Los dos vivían fuera del estado porque tienen que sobrevivir al
`reset()` del `finally` — y `stopped_by_user` lo estampa **la misma llamada que borra el estado**.

- Los dos entran en `SessionTelemetry` y `keepingIdentity()` los preserva, que es lo que les permite
  entrar sin perder la propiedad. **No van en `ConfirmationLifecycle`** como decía [09 §3]: los dos
  vetos del usuario borran ese sub-estado.
- `completed` deja de ser un `var` local capturado por tres corrutinas hermanas.
- El snapshot de fin de sesión se hace explícito: **`SessionEpilogue`**, escrito en UNA sentencia,
  sustituye a cinco `@Volatile` que se ponían de uno en uno, cada uno con su comentario recordando
  que había que leerlo antes del `reset()`. Cinco campos con la misma vida, la misma razón y el
  mismo plazo eran un valor.

### 3 · El canal de notas se tipa ✅ — y con eso muere una decisión que se leía del log

`StageVerdict.notes` pasa de `List<String>` a `List<DiagnosticNote>`. **El motivo no es estético:**
el presupuesto de no-movimiento elegía `aborted_no_movement_jam` sobre `aborted_no_movement`, y el
bucle averiguaba cuál tocaba preguntando **si la etapa había emitido alguna nota**
(`notes.isNotEmpty()`). Un veredicto con la clave puesta en el canal de diagnóstico — el mismo
defecto que el KDoc del tap denuncia en `jamExtensionLogged`, y aquí un grado peor: ni siquiera un
flag con nombre, sólo la presencia de *algún* texto. Silenciar la línea habría cambiado el desenlace.

`DiagnosticNote.claim` es la cura y su alcance es deliberadamente diminuto: **una nota recibe nombre
sólo cuando una decisión la lee**. Hoy hay exactamente una. Nombrar las sesenta sería inventar un
vocabulario que nadie consume, y qué notas llegan al trazado REMOTO es una decisión aparte con su
propio presupuesto de escrituras [09 §7, P4.2].

> **Verificado discriminante:** quitando el `claim` de la nota de la extensión,
> `should_fold_with_the_jam_outcome_when_the_extended_budget_expires_without_driving` se pone rojo.

### 4 · `NoMovementBudgetStage` no implementa `SessionStage` ✅ — se queda, y se dice en el bucle

No se tocó: su KDoc ya declara por qué (necesita la ventana de creep y el latch de la extensión, que
son bookkeeping por sesión que el bucle mantiene **incluso en los fixes donde la etapa se salta**) y
nombra la consolidación de Fase 4 que la dejaría entrar. Lo que cambia es que **el bucle nombra la
excepción en voz alta** en vez de esconderla tras un lookup que tendría que lanzar.

## Hallazgos que valen más que el código

1. **Dos wrappers muertos**: `corroboratesDrive` y `pruneRecentFixes` no los llamaba nadie desde que
   `DriveProof.onFix` recibe `bounds` (P1.x los dejó colgando). Y **cuatro constantes muertas** en el
   companion: `TAG`, `IMPLAUSIBLE_REPARK_PROMPT_SCORE`, `WEAK_EVIDENCE_PROMPT_SCORE` y
   `USER_CONFIRM_NEAR_CAR_MAX_METERS` — las tres últimas ya viven en el ejecutor y en `UserConfirmStage`
   desde P3.6/P3.11, y las copias del coordinator llevaban desde entonces sin leerse.
2. **`parkingDecisionInput` tenía un parámetro vestigial**: recibía `activeVehicleType` y lo IGNORABA,
   leyendo el `attributedVehicleType` vivo del coordinator. Su único llamante le pasaba justo ese
   valor, así que nunca pudieron discrepar. No era conducta; era ruido. Ya no está.
3. **`FastConfirmStage.hasKinematicEgress` era un duplicado exacto** de `hasKinematicEgressSignal`. Su
   propio KDoc decía *«sube a terreno compartido el día que una segunda etapa lo necesite»*: ese día
   llegó con el constructor de input.
4. 🔴 **La línea de salida de `invoke` miente desde antes de F6.** Dice
   `locationCount=${_detectionState.value.session.fixCount}` **después** del `finally`, que ya llamó a
   `reset()` — así que en la rama normal imprime siempre `0`. **NO se ha tocado** (regla 4: no se
   arreglan bugs dentro de un paso de refactor). `completed` sí se preservó, con un testigo leído
   antes del borrado, porque ese sí era real. → merece ticket propio.
5. **El guardrail del hold siguió al código por CUARTA vez** (coordinator → ejecutor → tap →
   despachador). Se puso rojo solo, se comprobó que `SETTLED` sigue emitiéndose, y se movió **la
   comprobación**, nunca el código de vuelta. La propiedad —*ninguna salida muerta*— no ha cambiado
   nunca; sólo la dirección.

## Método: lo que se hizo para no repetir P3.11

⛔ *Transcribir I/O de memoria no es una operación segura.* Las diez llamadas a `PaparcarLogger` de
`updateStopTracking` se convirtieron en notas con una transformación **mecánica y verificada** que
cambia el envoltorio de la llamada y **no toca la expresión de la cadena**. Ni una línea de
diagnóstico se retecleó. Lo mismo con los KDoc: los dos bloques huérfanos que quedaban en el
coordinator (documentando `runConfirm` y `saveUnattendedZone`, que se fueron al ejecutor en P3.11) se
**movieron** al ejecutor con su incidente de campo dentro — el Redmi del 25/26-07 —, no se borraron.

Y una nota de orden que la suite NO atrapa: `refinedParkLocation` y `sustainedDepartureFrom` logueaban
desde dentro. Al devolver su nota, la línea pasa a imprimirse **después** de `evaluate`, así que en
las tres etapas que la usan la nota se **antepone** (`listOfNotNull(pin.note) + notes`) para que
`parkdiag` conserve el orden exacto que tenía.

## Criterio de éxito — el de TODA la Fase 3

**Los ~3.350 líneas de tests del coordinator y los 18 replays pasan sin un assert editado.** ✅

| | |
|---|---|
| Línea base en este worktree sobre `d7299f4d` | **1.636 tests**, 0 fallos, 0 errores |
| Después de P3.13 (`--rerun-tasks`, no de caché) | **1.636 tests**, 0 fallos, 0 errores |
| Diff contra `docs/detection/P0.4-baseline-tests.txt` | **0 nombres perdidos por este ticket** |
| `assembleMockDebug` / `compileMockDebugKotlinAndroid` | ✅ |

Los dos nombres que faltan respecto a la marca P0.4 son **anteriores** a este ticket y están
justificados: el rename del guardrail del hold (P3.10) y un test de
`EvaluateShortHopDriveProofUseCase`, que el plan absorbe en `DriveProof` [07 P14]. Se verificó que
ninguno de los dos existe ya en `d7299f4d`.

## Recuento honesto

| | líneas |
|---|---|
| `CoordinatorParkingDetector.kt` antes de F6 | 3.336 |
| …al empezar P3.13 | 2.344 |
| …ahora | **1.286** |

⚠️ **No son las ~235 del plan, y el plan no las iba a dar nunca.** Ese presupuesto contaba
sentencias, y en este proyecto el comentario es la mitad del fichero por decisión explícita: cada
guard lleva su incidente de campo dentro. Las 1.286 son el ciclo de vida, el bucle, las tres
corrutinas hermanas, los entrypoints y el epílogo — exactamente el reparto que el plan enumera —
**con su documentación**. Recortarlas a 235 sólo se consigue borrando el porqué de cada guard, que es
justo lo que hace que el bug 148 cueste lo que cuesta su tamaño.

`state/StopTracking.kt` sale a 423 líneas, por encima del techo de ~330 que el §3 promete. Misma
causa y misma respuesta: es un reducer con nueve incidentes de campo citados dentro.

## Consumidores auditados

- **El paquete `domain.coordinator` ya no existe.** `CoordinatorParkingDetector` → `domain/detection/`,
  `ConfirmationPhase` → `domain/detection/state/`, y los 18 ficheros de test → `domain/detection/coordinator/`.
  30 ficheros re-importados; los tests que vivían en el mismo paquete que la clase ganaron el `import`
  explícito que la visibilidad de paquete les regalaba.
- Los constructores de las etapas quedan con **sólo sus casos de uso**: ninguna recibe ya lambdas del
  coordinator. `HoldResolutionStage()`, `UserConfirmStage()`, `CandidateStage(evaluateParkingDecision)`,
  `FastConfirmStage(evaluateParkingDecision)`, `ResponseTimeoutStage(evaluateUnattendedParkingSave)`.
- `activeParkedPin` deja de ser `@Volatile` del coordinator y entra en `SessionTelemetry` como
  identidad de sesión, igual que hizo `nominatingVehicleId` en P3.7 y por la misma razón: **una etapa
  ve el estado y nada más**.
- `PARKDIAG_COORD` pasa a ser la única fuente del prefijo local, porque el reducer y el orquestador
  son dos ficheros y un tag que deriva parte una traza por la mitad.

## Lo que este paso NO hace

- **No arregla el `locationCount=0`** de la línea de salida (hallazgo 4). Ticket propio.
- **No nombra las 60 notas.** `DiagnosticNote.claim` sólo cubre la que una decisión lee. Ampliar la
  superficie remota es P4.2.
- **No mete `NoMovementBudgetStage` en la interfaz.** Es la consolidación de Fase 4 que su KDoc cita.
- **No valida nada en campo.** Cero cambio observable es el criterio y se cumple, pero la Fase 3
  entera sigue **sin conducir**.
