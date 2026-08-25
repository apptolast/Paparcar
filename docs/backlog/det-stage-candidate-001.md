# DET-STAGE-CANDIDATE-001 · P3.3 — un snapshot viejo no puede estampar una línea de frescura

**Estado:** ✅ Done (2026-08-25) · rama `refactor/DET-STAGE-CANDIDATE-001-p3-3` ·
worktree `../Paparcar-stage-3`

Paso **P3.3**. Sigue a `88cc4557` (P3.2).

## Qué mueve

[DET-D-02] El árbol de decisión del candidato abierto pasa a `stages/CandidateStage.kt`.

Termina la pasada en **todas** sus ramas, incluida la inconclusa — y eso siempre fue así: la rama
estaba envuelta en un `return@collect` decidiera lo que decidiera. **Un candidato en vuelo no es un
estado que nadie por debajo pueda re-decidir.**

## El hallazgo: un límite del andamio, ahora escrito dentro de él

> `StageVerdict.newState` **solo es seguro para cambios IDEMPOTENTES** frente a un snapshot viejo.

Una etapa razona sobre el estado tal como estaba al principio de la iteración. Para cuando su
veredicto se aplica, el colector de pasos puede haber contado uno.

- Asignar una fase **es** idempotente: sale la misma fase hicieran lo que hicieran los contadores.
- `egress.candidateDiscarded()` **no lo es**: estampa la línea de frescura *donde esté la cuenta
  AHORA*, y reproducirlo desde un snapshot **pierde en silencio los pasos dados en medio** — que es
  exactamente el fallo que `DET-EVIDENCE-MUST-NOT-LOWER-CONFIDENCE-001` existe para impedir.

Así que el descarte es un **EFECTO**, aplicado por el ejecutor al estado VIVO, con la transición
verbatim. La advertencia queda en el KDoc de `StageVerdict.Handled`, que es donde la leerá quien
mueva la siguiente etapa.

## Un efecto absorbe dos plomerías distintas

El cierre por tracción humana se alcanza desde **dos** etapas, y cada una terminaba la sesión de una
forma distinta: la rama del candidato devolvía `true`, la del scoring se apoyaba en que su call site
terminaba la sesión para toda rama terminal.

`CloseHumanPowered` es ahora **un efecto con una sola respuesta**, y los dos call sites leen
`endsSession` igual.

## Doctrina

Ninguna tocada. **Cero cambio de conducta.**

## Tests

Ni un assert editado. **6** tests de precedencia de P0.1 verdes, **18** replays verdes.

**1.629 tests**, 0 fallos. Coordinator **−90 líneas**. `assembleMockDebug` ✅.

## Estado de la Fase 3

Tres de diez etapas movidas: `ConfidenceScoringStage`, `FastConfirmStage`, `CandidateStage`.

`parkingDecisionInput` ya tiene **sus tres consumidores**, así que está listo para mudarse a
`stages/`. Se queda un paso más para que esa mudanza sea su propio diff y no un pasajero de éste.

Siguiente: **P3.4**, `ResponseTimeoutStage` [DET-RECONCILE-001] — la más grande de las diez
(~+140/−120) y la que entra en el territorio de `EvaluateUnattendedParkingSaveUseCase`.
