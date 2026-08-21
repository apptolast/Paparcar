# DET-PROMPT-STATES-ITS-REASON-001 · Un veredicto que pregunta tiene que decir por qué pregunta

**Estado:** ✅ **Done** — mergeado a master por squash el 21-08-2026 (código, tests y este doc en un
único commit; el hash vive en `MEMORY.md`, ver más abajo el porqué). Rama y worktree borrados al
cerrar. Base: `bef70ec7`.

## Problema

Field 2026-08-20 23:56, Oppo, sesión `diagnostics/fiypNbElGlfFexLMpU9sNaMjRMD3/sessions/1787263007358`:
un viaje en coche de 36,6 min con **vmax 63,3 km/h**, parada total y 175 pasos de egress terminó
`aborted_unattended_human_powered` **sin un solo pin**. Tres veredictos y ninguna plaza:

```
t=  585 s  CONFIRM_DEGRADED_PROMPT        kinematic+egress     ← ¿por qué?
t= 1291 s  CONFIRM_DEGRADED_PROMPT        steps+egress         ← ¿por qué?
t= 2191 s  UNATTENDED_HUMAN_POWERED_NUDGE unattended_timeout   ← lo dice
```

Los dos primeros son **inatribuibles**. El caso solo se pudo cerrar porque el TERCERO nombra su
causa en el outcome: sin esa línea, el diagnóstico se queda en "algo degradó el confirm" y no hay
manera de saber qué. Y el trace es lo único que hay — el móvil no va enchufado a un PC cuando se
conduce, y el anillo de logcat ya había rotado sobre la decisión cuando se investigó.

`CONFIRM_DEGRADED_PROMPT` tiene hoy **seis productores** que emiten exactamente el mismo string:

| # | Causa | Dónde |
|---|---|---|
| 1 | `weakEvidenceOnly` — arm débil (`verified_enter`/`verified_late`/`self_observed`) sin conducción medida | `EvaluateParkingDecisionUseCase:265` |
| 2 | `humanPowered` — perfil bici/patinete **o** `humanPoweredRide` | ídem |
| 3 | `!egressBornAtAnchor` — el egreso no nació en el ancla | ídem |
| 4 | `anchorWalkEntered` — el ancla se capturó en una parada entrada andando | ídem |
| 5 | `anchorGapEntered` — la parada se abrió a través de un hueco GPS | ídem |
| 6 | `ImplausibleRepark` — el guard de re-aparcamiento rechazó el save | `CoordinatorParkingDetector:1920` |

1-5 colapsan en un único `||` que construye `ParkingDecision.Prompt(pathLabel)` (`:267`), y `pathLabel`
describe **cómo se probó** el aparcamiento, no **por qué se degradó**. Son ejes distintos y hoy solo
se registra el primero.

## Doctrina violada

- **⛔ En diagnósticos, identificar SIEMPRE qué trigger/path produjo cada resultado.** La provenance
  se cerró para los PINES (`detectionPath` + `armEvidence`, DET-PIN-PROVENANCE-001) y quedó abierta
  para los NO-pines. Una sesión que no guarda nada es justo la que hay que poder explicar.
- **Fallo asimétrico.** La doctrina acepta preguntar ante la duda; el precio de esa política es
  poder auditar cada duda. Una pregunta anónima no es auditable, así que la política no se puede
  ni validar ni calibrar.
- **Sistemas, no parches.** El vocabulario correcto **ya existe en este mismo fichero**:
  `UnattendedSaveReason` (`key` + `decisionOutcome` + `nudgeSource` + `abortedOutcome`) hace
  exactamente esto para el timeout desatendido, y es la razón de que el tercer veredicto de la
  sesión sí se pudiera leer. El carril de candidato/confirm nunca aprendió a hablarlo.

## Señales / datos disponibles

Todo está ya calculado en el punto de decisión: los cinco booleanos son variables locales de
`EvaluateParkingDecisionUseCase.invoke`, y la sexta causa es un tipo de excepción distinguible
(`PaparcarError.Parking.ImplausibleRepark`). **No hace falta medir nada nuevo** — solo dejar de
tirar el dato al construir el veredicto.

## Diseño

El invariante vive donde nace el veredicto, no donde se registra: *si un veredicto pregunta, la
razón viaja DENTRO del veredicto*.

1. **`PromptReason`**, enum en `EvaluateParkingDecisionUseCase.kt`, hermano de `UnattendedSaveReason`:
   `WEAK_EVIDENCE`, `HUMAN_POWERED`, `EGRESS_NOT_AT_ANCHOR`, `ANCHOR_WALK_ENTERED`,
   `ANCHOR_GAP_ENTERED`, `IMPLAUSIBLE_REPARK`. Lleva **solo `key`**, no el `decisionOutcome` de su
   hermano: el carril desatendido necesita ese segundo campo porque cada razón suya SÍ produce un
   `outcome` distinto, y aquí el `outcome` es único e inmutable por decisión (§3). Añadirlo sería
   simetría decorativa con un campo que nadie leería.
2. **`ParkingDecision.Prompt(pathLabel, reason)`.** El `||` de cinco ramas pasa a una cadena
   ordenada que devuelve la PRIMERA que se cumple, con el orden fijado y documentado (empezando por
   la más específica) para que dos sesiones con la misma forma produzcan siempre la misma etiqueta.
   Los dos ejes se conservan: `pathLabel` = cómo se probó, `reason` = por qué no bastó.
3. **✅ DECIDIDO — el `outcome` NO se toca.** Se mantiene `CONFIRM_DEGRADED_PROMPT` y la razón viaja
   en la columna **`reason`**, que ya existe en `DetectionEventDto` y ya la usan `HonestClose`,
   `Released` y `GeofenceRegistration`. Es la opción recomendada y la que se ha implementado:
   - **Cero rotura de histórico.** Renombrar el outcome habría invalidado toda traza guardada y toda
     nota de memoria que lo cita — exactamente lo que `UnattendedSaveReason` evitó al negarse a
     renombrar dos etiquetas históricas. Prueba de que no se rompió nada: los **1312 tests previos
     siguieron verdes sin tocar ni una aserción**.
   - **Los dos ejes quedan limpios.** Meter la razón en `pathLabel` (`"steps+egress/human_powered"`)
     se descartó: `pathLabel` se compara por igualdad en tests y consultas, y contaminarlo habría
     roto ese eje para arreglar el otro.
   - **Sin cambio de superficie del serializador**, igual que el `enterAgeMs` de §C del motorway.
4. **Sin tipo de evento nuevo.** Igual que `MOTOR_WITNESSED` y `PEDAL_CADENCE_LATCHED`, se reutiliza
   `DetectionEvent.Decision`, que gana un `reason: String?`.
5. **La cadena es un first-match ORDENADO**, no un `||`. Con varias causas simultáneas la etiqueta
   tiene que ser determinista o la telemetría no se puede agrupar. Orden: la duda más ANCHA primero
   — `HUMAN_POWERED` (afirma algo del viaje entero) → `WEAK_EVIDENCE` (afirma algo del arm) → las
   tres que solo dudan de DÓNDE está el ancla.

Fuera de alcance: cambiar CUÁNDO se degrada. Este ticket no mueve ni un umbral — solo hace legible
lo que ya ocurre.

## Criterio de éxito

- Un test por causa: cada una de las seis produce su propia etiqueta en el trace, y dos causas
  distintas nunca comparten string.
- Replay de la sesión `1787263007358` (si se convierte en fixture): sus dos prompts quedan
  atribuidos sin recurrir al tercer veredicto.
- Campo: una sesión degradada se explica **leyendo el trace remoto**, sin logcat y sin el móvil
  delante.

## Consumidores auditados

Barrido completo sobre master `bef70ec7`. `grep` de `ParkingDecision.Prompt(` en `commonMain` +
`androidMain` deja **un solo constructor** (`:322`), así que la razón no puede nacer anónima.

| Sitio | Qué asumía | Estado |
|---|---|---|
| `EvaluateParkingDecisionUseCase:322` | `Prompt(pathLabel)` de un arg | **cerrado** — único constructor; `reason` es obligatorio |
| `CoordinatorParkingDetector:1519` (carril rápido) | `degradeToPrompt(pathLabel, …)` | **cerrado** — pasa `decision.reason` |
| `CoordinatorParkingDetector:2029` (carril candidato) | ídem | **cerrado** — ídem |
| `CoordinatorParkingDetector:2051` (`degradeToPrompt`) | firma de 3 args | **cerrado** — `reason` **sin default a propósito**: un default resucitaría el prompt anónimo |
| `CoordinatorParkingDetector:1926` (`ImplausibleRepark`) | emitía el string a mano | **cerrado** — estampa `IMPLAUSIBLE_REPARK` |
| `DetectionEventDto.toDto()` (`when` exhaustivo) | mapeaba `Decision` sin `reason` | **cerrado** — el `when` protege variantes, no campos → test de paridad propio (`should_carryThePromptCause…` + el caso nulo) |
| `when (decision)` exhaustivos sobre `ParkingDecision` | 5 variantes | **cerrado por construcción** — un arg nuevo no añade rama; `compileProdDebugKotlinAndroid` y `compileMockDebugKotlinAndroid` verdes |
| Tests que afirman `outcome == "CONFIRM_DEGRADED_PROMPT"` | string literal | **exento** — el outcome no cambió; **1312 tests previos verdes sin tocar una aserción** |
| `EvaluateUnattendedParkingSaveUseCase` | su propio `UnattendedSaveReason` | **exento** — ya cumple el invariante; es el modelo imitado |

## Resultado

**1322 tests verdes** (1312 previos + 10 nuevos), `compileProdDebugKotlinAndroid` y
`compileMockDebugKotlinAndroid` OK. Sin strings de usuario, sin pantalla ni estado nuevo → los 9
locales y el Dev Catalog no se tocan. Sin cambio de superficie del serializador.

Tests nuevos: una causa por test (las seis), el desempate determinista cuando varias se cumplen a la
vez, la unicidad de las claves de traza, y la paridad DTO en los dos sentidos (con razón y sin ella).

## Colisión — RESUELTA 21-08

`DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001` tocaba `CoordinatorParkingDetector` y
`EvaluateParkingDecisionUseCase` en la misma zona y tenía que ir primero (arregla el bug; este solo
lo hace legible). **Ya está en master `1c292ac8`** (+ `bef70ec7` de cierre), rama y worktree
borrados, con el pestillo de cadencia (`PEDAL_CADENCE_LATCHED`) incluido en su §C. La colisión
desaparece; esta rama solo necesita ponerse al día sobre master antes de empezar el código.

## Nota de proceso

Primer ticket cerrado con la regla del 21-08-2026: **el doc va DENTRO del commit de código**, no en
un `docs(backlog): close…` aparte. Consecuencia asumida — este doc no puede citar su propio hash,
porque no existe hasta después del commit que lo contiene. El hash se registra en `MEMORY.md`, que
es donde se consulta de verdad.
