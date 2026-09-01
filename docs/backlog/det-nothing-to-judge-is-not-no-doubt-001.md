# DET-NOTHING-TO-JUDGE-IS-NOT-NO-DOUBT-001

> **Estado:** ✅ **Done** — mergeado a master el 31-08-2026 (squash, `c0144b5e`). Rama y worktree
> borrados.
> **Origen:** **Pieza 3b** del rediseño (*«una sola política de nulos, escrita una vez»*).
> **Cierra el fallo #10** de §6.2 y deja escrita la política. **#6 queda declarado inseparable de #7.**

---

## 1. Lo que decía el plan, y lo que había de verdad

3b nombra cinco sitios. Auditados uno a uno contra master **antes de tocar nada**:

| # | sitio | plan | realidad |
|---|---|---|---|
| 8 | `isAdmissibleEvidence(sessionStartMs = null)` | corregir | ✅ **REFUTADO y con razón** — gobierna una SEÑAL que nomina, no un pin. Ya estaba razonado dentro del propio fichero; voltearlo ponía en rojo dos tests de `VerifyDepartureEvidenceUseCase` |
| 9 | `isCredibleDrivingSpeed(accuracyMeters = null)` | corregir | ✅ ya cerrado — ⚠️ **pero su KDoc seguía diciendo lo contrario** |
| 5 | `EvaluateGeofenceExitUseCase`, distancia no medible | corregir | ✅ ya cerrado (va a `stale`, no a `boundary`) |
| 6 | `EvaluateBackfillDeferralUseCase(resolutionAtMs = null)` | corregir | ⛔ **no es arreglable solo** — ver §4 |
| 10 | `isEgressBornAtAnchor` | corregir | 🔴 **abierto: es este ticket** |

Tres de cinco ya estaban, arreglados **de uno en uno por tres tickets distintos**, y la política que
los unifica no estaba escrita en ninguna parte. Eso es exactamente lo que 3b pedía y lo único que
nadie había hecho.

## 2. La política, escrita una vez

> **En una pregunta sobre EVIDENCIA, `null` significa "no hay evidencia" — y no hay evidencia no
> puede autorizar una afirmación.**

Pero la regla tiene una segunda mitad que la frase del plan omite, y omitirla es lo que hacía que
**una de sus cinco entradas fuera falsa**:

| la pregunta es sobre… | qué significa un input ausente | dirección |
|---|---|---|
| **PLANTAR** — una afirmación que cambia lo que ve el usuario | no hay sobre qué sostenerla → no se afirma | fallar **CERRADO** |
| **NOMINAR** — una señal que sólo despierta un carril | no es motivo para tirar una señal real | fallar **ABIERTO** |

El contrato de triggers del proyecto ES la segunda mitad: *todo trigger dispara siempre; un evento
viejo pierde autoridad directa y pasa al evaluador, nunca se descarta*. Fallar cerrado gobierna lo
que la app **planta**, no lo que **escucha**.

Queda escrita en `docs/detection/PARKING-DETECTION.md` (entrada de este ticket), que es donde vive la
doctrina narrativa del proyecto.

## 3. El fallo #10: el regalo caía justo donde duele

`isEgressBornAtAnchor` respondía `true` —*«no hay duda sobre el ancla»*— por **dos razones
distintas**: una birth MEDIDA dentro de la consistencia peatonal del ancla, y **ninguna birth
registrada**. La segunda no es ausencia de duda: es ausencia de medición.

Y no se reparte por igual entre los caminos de confirmación:

| camino de confirmación | ¿puede llegar sin birth registrada? |
|---|---|
| pasos + alejamiento | **No** — un paso contado ABRE una birth, y ese camino exige pasos |
| cinemático | **No** — un testigo cinemático aceptado también la abre, y ese camino los exige |
| **AR vehicle-exit + ventana + alejamiento** | **Sí** — es el único que no exige ninguna de las dos |

El tercero es, dicho por el propio código, *«the weakest confirm path in the system»*: ninguna prueba
física de que el usuario se bajara **ahí**. Le tocaba el «no hay duda» gratis siempre que no se
registrara birth.

⚠️ **CORRECCIÓN (`DET-A-DISOWNED-ANCHOR-TAKES-ITS-WALK-WITH-IT-001`, el mismo día).** Este doc decía
*«sí, siempre con el contador mudo»* y culpaba a la asimetría de `acceptsKinematicWitness` («bug
#6»). **Medido después, las dos afirmaciones son demasiado fuertes**: la caminata de un usuario con
podómetro mudo **sí** abre birth por la rama MOVING, donde la bandera ya es `true`, siempre que los
fixes reporten ≥ `stoppedSpeedThresholdMps` (1 m/s) con accuracy creíble. Lo que no tiene testigo es
una caminata cuyos fixes reporten **por debajo** de ese umbral: ésos caen del lado parado, donde
nadie los cuenta. Lo de arriba vale para «sin birth registrada», no para «contador mudo». El bug #6
quedó **resuelto como REGLA, no como deuda**, y el hueco real tiene spec propia:
`docs/backlog/det-a-walk-reporting-zero-is-still-a-walk-001.md`.

## 4. ⛔ #6 no es arreglable sin #7, y eso es un hallazgo

`EvaluateBackfillDeferralUseCase` devuelve `false` (= plantar normal) cuando no hay sello de
resolución. Suena a default permisivo, pero **el null es el caso NORMAL**: el sello sólo se escribe
para `GAP_ANCHOR`, 1 de las 8 razones — que es el fallo **#7**. Hacer que el null difiera suprimiría
**todo** backfill legítimo hasta que #7 esté cerrado. Son un solo problema y hay que abordarlos
juntos, en el ticket de 3c.

## 5. El arreglo

```kotlin
enum class EgressBirthJudgement { BORN_AT_ANCHOR, BORN_AWAY, NOT_RECORDED }
fun DetectionSessionState.judgeEgressBirth(config): EgressBirthJudgement
```

Tres consumidores, y **cada uno DECLARA** qué hace con el tercer valor:

| consumidor | qué hace con `NOT_RECORDED` | cambia |
|---|---|---|
| `EvaluateParkingDecisionUseCase` | degrada el guardado silencioso a **pregunta** | 🔴 sí |
| `EvaluateUnattendedParkingSaveUseCase` | **no** entra en la rama zona-entre-birth-y-ancla | 🟢 no (queda como hoy, ahora por escrito) |
| `UserConfirmStage.whereTheCarIs` | sigue confiando en el ancla (`!= BORN_AWAY`) | 🟢 no, **a propósito** |

- El **unattended** no entra porque esa rama lee `egressOriginFix`, y sin birth es null: centraría
  una zona sobre una birth inexistente y estamparía un `EGRESS_MISMATCH` que nadie midió.
- El **user-confirm** no cambia porque su cascada existe para un ancla de la que hay MOTIVO para
  dudar (nacida lejos, o entrada por un hueco de GPS). *«No vimos empezar la caminata»* no es ese
  motivo, y degradar movería un pin que el USUARIO acaba de confirmar al fix desde el que contestó —
  un portal a 40 m es peor apuesta que la parada que la sesión midió. La respuesta prueba el
  aparcamiento; esa rama sólo elige coordenadas.

⛔ **Y la pregunta nombra su propia causa**: `PromptReason.EGRESS_NOT_WITNESSED`, no
`EGRESS_NOT_AT_ANCHOR`. Esta última es una MEDICIÓN — la caminata empezó, y empezó en otro sitio.
Reportar una ausencia con ese nombre le diría al usuario, y a toda forensia futura, que medimos una
caminata que empezó lejos cuando no se registró ninguna: el defecto *«un prompt que nombra la causa
equivocada»* que `DET-PROMPT-STATES-ITS-REASON-001` existe para evitar.

**Sin trabajo de i18n**: `PromptReason.key` va sólo a la columna `reason` de diagnósticos; el texto
que ve el usuario es el mismo prompt de siempre.

## 6. ⚠️ Coste conocido, aceptado con el ticket

En hardware con el contador de pasos mudo, ese camino débil pasa de **pin silencioso** a **pregunta**.
Hoy una pregunta sin contestar deja un pin impreciso; cuando entre **P3** (*una pregunta es una
puerta, no un retraso*) pasará a **sin pin** — o sea, **el precio de esta decisión sube después**.

Es la dirección que manda el fallo asimétrico (mejor un FN que un FP), y revertirla es **una línea**
si el campo dice otra cosa: quitar la fila `NOT_RECORDED -> EGRESS_NOT_WITNESSED` del `when` de
`promptReason`.

## 7. Barrido de consumidores (todos los sitios auditados)

| # | fichero | qué había | qué hay |
|---|---|---|---|
| 1 | `domain/detection/state/AnchorPredicates.kt` | `isEgressBornAtAnchor: Boolean` | `EgressBirthJudgement` + `judgeEgressBirth` |
| 2 | `domain/usecase/parking/EvaluateParkingDecisionUseCase.kt` | `egressBornAtAnchor: Boolean` + `!it -> EGRESS_NOT_AT_ANCHOR` | `egressBirth` + dos filas en el `when`, y `PromptReason.EGRESS_NOT_WITNESSED` nuevo |
| 3 | `domain/usecase/parking/EvaluateUnattendedParkingSaveUseCase.kt` | `if (!input.egressBornAtAnchor)` | `if (input.egressBirth == BORN_AWAY)` |
| 4 | `domain/detection/stages/StageInputs.kt` ×2 | `isEgressBornAtAnchor(config)` | `judgeEgressBirth(config)` |
| 5 | `domain/detection/stages/UserConfirmStage.kt` | `isEgressBornAtAnchor(config) && …` | `judgeEgressBirth(config) != BORN_AWAY && …` — **misma conducta, ahora razonada** |
| 6 | `domain/model/ParkingDetectionConfig.kt` | KDoc de `isCredibleDrivingSpeed` afirmando que el null pasa | corregido: la afirmación llevaba semanas contradiciendo la línea que documenta |
| 7 | `DetectionEffectExecutor` / `Dispatcher` | leen `PromptReason` genérico | **sin tocar** — el valor nuevo viaja solo |
| 8 | strings / locales | — | **sin tocar**: `PromptReason` no llega a la UI |

## 8. Tests

**Nuevos** (5)
- `AnchorPredicatesTest` — los 5 tests del predicado migrados a los tres valores: sin ancla y sin
  birth ahora **afirman `NOT_RECORDED`**, donde antes afirmaban el mismo `true` que una birth medida.
- `EvaluateParkingDecisionUseCaseTest` (+3) — `NOT_RECORDED` pregunta; los dos motivos de egress son
  **distinguibles**; y el **censo**: cada valor del enum tiene su desenlace propio, con la población
  tomada del propio `entries` (un cuarto valor mañana no tiene fila y falla).
- `EvaluateUnattendedParkingSaveUseCaseTest` (+1) — una ausencia de medición **no** se reporta como
  `EGRESS_MISMATCH`.
- `AnchorTrustTest` (+1) — **la afirmación en la que se apoya la pregunta nueva**, clavada donde vive
  el mecanismo: un paso contado abre birth, un testigo cinemático aceptado también, y la rama STOPPED
  sigue sin abrirla con sólo el cinemático (bug #6, nombrado).

**Falsación (⛔ un test sin verlo fallar siempre pasa)** — tres inyecciones:
1. quitada la fila `NOT_RECORDED -> EGRESS_NOT_WITNESSED` → **3 FAILED**;
2. cambiada a `NOT_RECORDED -> EGRESS_NOT_AT_ANCHOR` (que tome prestado el nombre del otro) → **las
   mismas 3 FAILED**;
3. devuelto `BORN_AT_ANCHOR` en la rama sin birth del predicado → **1 FAILED**.

Restaurado todo, verde.

**Suite completa:** `:shared:testDebugUnitTest` → **2041 tests, 0 fallos**.
`:app:compileProdDebugKotlin` + `:app:compileMockDebugKotlin` OK.

## 9. Lo que queda de la Pieza 3

- **3b**: cerrado salvo **#6, que se declara inseparable de #7** y pasa a 3c.
- **3c**: intacto (el sello para las 8 razones, #7 — que ahora arrastra también #6).

## 10. Doctrina que aplica

- *Fallar cerrado por construcción*, **con su segunda mitad**: plantar sí, nominar no.
- *Sistemas, no parches*: el invariante («nada que juzgar ≠ ninguna duda») se expresa UNA vez en el
  tipo, y los tres consumidores declaran su respuesta — dos la conservan y uno cambia, con la razón
  escrita en cada sitio.
- *Un caso de uso por VEREDICTO*: sigue siendo un predicado; no se creó ninguna clase.
- *Un prompt nombra su causa real*: la razón nueva existe para no mentir, no para tener más enum.
