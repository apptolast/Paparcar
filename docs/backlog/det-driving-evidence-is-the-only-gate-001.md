# DET-DRIVING-EVIDENCE-IS-THE-ONLY-GATE-001 · el confirm silencioso pregunta a la nominación, no a la prueba

**Estado:** ✅ Done · rama `feature/DET-DRIVING-EVIDENCE-IS-THE-ONLY-GATE-001-driving-evidence` · worktree `../Paparcar-driving-evidence`

## Problema

Falso positivo de campo, **29-08-2026 23:56**, Redmi (`2201117TY`), uid `itmGbBxaz8ZJkLUlwvOnWDnMDto1`.

Pin `c6a57fad-6571-4650-a7ed-14d5d733fd7b` — `placeInfo = {category: PHARMACY, name: "La Parafarmacia"}`,
`detectionPath = steps+egress`, `armEvidence = enter_at_car`, `detectionReliability = 0.9`,
`routeDistanceMeters = 244.9`, `isActive = true`. El usuario estaba parado; no hubo viaje.

Reconstrucción fix a fix desde el `parkdiag.log` del propio móvil (líneas 2602–2937):

```
23:47:41  IN_VEHICLE ENTER  → bus stamped (lag=89019ms)          ← AR con 89 s de retraso
23:47:44  AR ENTER at own fence — arming, waiting for ride proof (dep=enter_at_car)
23:47:44  loc#1  36.5993395,-6.2516270  speed=0.00  acc=16.4m
23:47:52  loc#2  36.5990717,-6.2508977  speed=7.71  acc=16.1m
          ✓ hasEverReachedDrivingSpeed → true (7.71≥5.0) dist=71.59m [BUG-SHORT-TRIP]
23:47:55  loc#3  36.5993522,-6.2515334  speed=1.52  acc=11.6m    ← vuelve 64,8 m ATRÁS en 3,5 s
23:47:59  loc#4  36.5992437,-6.2513251  speed=0.25  acc=11.3m    ← coordenada exacta del pin
23:48:13  ⚓ anchor FROZEN                                        acc=63m
          …acc sigue a 123 m, 220 m, 251 m, 180 m
23:54:23  ▶ steps+egress (steps=8 kinematicFixes=0) → fast confirm
23:54:23  ⏸ tentative confirm — holding 120000ms [DET-C-02]
23:56:28  ✓ hold settled (held=125003ms, userYes=false) — finalizing
23:56:28  → confirmParking(reliability=0.9, path=steps+egress)
```

Medidas que lo caracterizan:

- **Desplazamiento neto armado → pin: 29 m**, con fixes de 16 m de precisión.
- El "viaje" fue 71,6 m de ida **deshechos 64,8 m** 3,5 s después. Los 244,9 m de ruta son la suma
  del zigzag, no ground cubierto.
- `hasEverMoved = false` en **todas** las líneas de estado de la sesión — la app dice ella misma que
  el vehículo nunca se desplazó, y confirma igual.
- **`DriveProof` nunca se probó**: no hay una sola línea `✓ drive PROVEN` entre el armado (2602) y el
  confirm (2937). Las únicas del log están en las líneas 56 y 3912, de otras sesiones.
- `kinematicFixes = 0`, `vehicleExit = false`.

Control positivo en el mismo log: el viaje real de las 21:47 sí trae `drive PROVEN by track`,
`sustained drive 30001ms` y `MOTOR witnessed 40074ms`, y su pin (`092c74d7`, Calle del Vivero) es
correcto.

## Doctrina violada

*El evento NOMINA, solo el movimiento MEDIDO confirma* (`CLAUDE.md` → Doctrina rectora; también
escrita literalmente en el KDoc de `DriveProof.kt`: *"the event nominates while only measured
movement confirms"*).

La sesión confirmó en silencio con `DriveProof.proven == null`. La nominación se usó como si fuera
la prueba.

Segundo invariante roto, del propio `CLAUDE.md`: *sistemas, no parches — el invariante se arregla en
UN sitio*. Hay **dos** clasificaciones de la fuerza de un armado y el sitio que decide usa la mala.

## Señales / datos disponibles

Todo lo necesario ya está calculado en el punto de decisión:

- `sessionSawDriving` — ya se computa en `EvaluateParkingDecisionUseCase:223`
  (`sustainedDriveWitnessed`). Es la prueba medida.
- `ArmEvidence.driveAuthorization` — `when` **exhaustivo, declarado por cada armado y forzado por el
  compilador** (*"a new arm does not compile until its author answers"*), con tres valores:
  `Measured` · `OnTrust` · `None`. `BoardingAtCar` (= `enter_at_car`) ya está correctamente
  clasificado como `None`.
- `input.evidenceLabel` — la etiqueta persistida del armado.

## Diseño

### La causa raíz, exacta

`EvaluateParkingDecisionUseCase` **no consulta `driveAuthorization`**. Re-deriva la debilidad del
armado de una lista de strings mantenida a mano:

```kotlin
val weakLabels = setOf(LABEL_VERIFIED_ENTER, LABEL_VERIFIED_LATE, LABEL_SELF_OBSERVED, LABEL_ARRIVAL_HANDOFF)
val weakEvidenceOnly = config.autoConfirmRequiresStrongEvidence &&
    input.evidenceLabel in weakLabels && !sessionSawDriving
```

Esa lista enumera **las debilidades por las que ya nos habíamos quemado**, así que es un acumulador
de parches: cada armado nuevo entra por la puerta hasta el día que muerde. `enter_at_car` no estaba
en ella, luego se trató como evidencia fuerte, luego no hubo prompt, luego pin silencioso a 0.9.

Cotejo de las nueve etiquetas contra si llevan una medición detrás:

| etiqueta | en `weakLabels` | `driveAuthorization` | ¿lleva medición? |
|---|---|---|---|
| `manual` | no | `None` | sí — la palabra del usuario (aserción, no inferencia) |
| `inherited_drive` | no | **`Measured`** | sí — medida por la sesión superseded |
| `verified_speed` | no | `OnTrust` | no — confianza, no medición |
| **`enter_at_car`** | **no** | **`None`** | **no** ← el agujero |
| `verified_enter` | sí | `OnTrust` | no |
| `verified_late` | sí | — | no |
| `self_observed` | sí | `None` | no |
| `arrival_handoff` | sí | `None` | no |
| `bt_ride` | no | `None` | (otra estrategia, no pasa por aquí) |

### El arreglo

Sustituir la lista de strings por la clasificación que ya existe y que el compilador vigila. La
regla, escrita como doctrina y no como enumeración de accidentes:

> Una sesión puede confirmar **en silencio** sólo si hay una conducción MEDIDA: la de su propio
> stream (`sessionSawDriving`), o una que el armado trae medida de otra sesión
> (`DriveAuthorization.Measured`). La palabra del usuario (`manual`) es una **aserción**, no una
> inferencia, y queda exenta por `DET-ASSERTION-OUTRANKS-INFERENCE-001`. Todo lo demás **pregunta**.

Esto invierte el conjunto: de "las debilidades conocidas" (abierto, crece con cada FP) a "lo que
lleva una medición" (cerrado, y un armado nuevo no compila sin clasificarse).

### Por qué el gate NO va en `confirmNow`

Considerado y descartado: añadir `!sessionSawDriving -> false` a `confirmNow` haría que la sesión no
confirme **ni pregunte** (cae a `Inconclusive`/`Rejected`), que es un FN mudo sin recurso para el
usuario. La doctrina dice *ante la duda se PREGUNTA*. El gate pertenece a la política de prompt.

### Nota sobre la asimetría `hasStepsProof` / `hasKinematicProof`

`hasKinematicProof` sí exige `sessionSawDriving` (línea 229); `hasStepsProof` no (línea 208), pese a
que el comentario de `DET-SOLID-001` justo encima declara la intención correcta: *"has no business
confirming silently by **ANY** path"*. Con el arreglo de arriba la asimetría deja de poder producir
un pin silencioso (cualquier sesión sin conducción medida pregunta), y se documenta en el código en
vez de quedar como diferencia muda.

## Criterio de éxito

- Replay `Trace_Parafarmacia2908` que reproduce el stream real y exige `ParkingDecision.Prompt`
  (no `Confirmed`). Verificado **neutralizando el guard**: con el arreglo revertido la aserción se
  pone roja. (Método de `DET-2208-TRIPS-BECOME-REPLAYS-001`: *una aserción cuyo comentario afirma más
  de lo que demuestra es un bug con forma de test verde*.)
- Test unitario: `enter_at_car` + `!sessionSawDriving` + pasos y egress suficientes → `Prompt`.
- Test unitario: `inherited_drive` + `!sessionSawDriving` → sigue confirmando en silencio (no
  regresión de `DET-SUPERSEDE-CANNOT-DISCARD-A-MEASURED-DRIVE-001`).
- Test unitario: `manual` → sigue confirmando en silencio (no regresión de
  `DET-HANDOFF-NOT-MANUAL-001`, cuyo ticket movió el handoff FUERA de `manual` precisamente porque a
  `manual` se le trata como la palabra del usuario).
- Test de consistencia etiqueta ↔ clasificación, para que la lista no pueda volver a desincronizarse.
- Suite completa de `:shared` en verde.

## Consumidores auditados

Barrido de **todo** lo que lea `evidenceLabel` o re-derive la fuerza de un armado a partir de la
etiqueta (`grep` de `evidenceLabel`, `isVerifiedLabel` y `LABEL_*` fuera de `ArmEvidence.kt`).

| sitio | qué decide | clasificación |
|---|---|---|
| `StageInputs.kt:68` | único productor de `evidenceLabel` (`session.armEvidence`, no-nulo, default `self_observed`) | **cerrado** — es la vía que el arreglo corrige; el caso `null` del predicado es defensivo, no alcanzable en producción |
| `EvaluateParkingDecisionUseCase.kt:296` | política de evidencia débil → prompt vs confirm silencioso | **cerrado** — el arreglo |
| `ConfirmParkingUseCase.kt:193` (guard de aserción) y `:207` (guard de repark), vía `ArmEvidence.isVerifiedLabel` | si esos dos últimos guards se aplican | **exento con razón** (ver abajo) |
| `CoordinatorParkingDetector.kt:621` (enter-arm step veto) | degradar un `verified_enter` espurio | **exento con razón** (ver abajo) |
| `VerifyDepartureEvidenceUseCase.kt:31` | sólo una referencia en KDoc | sin efecto |
| Estrategia Bluetooth | — | **no converge aquí**: el evaluador sólo se alcanza desde los stages del `CoordinatorParkingDetector` (`FastConfirmStage`, `ConfidenceScoringStage`). `bt_ride` no cambia de conducta |

### `isVerifiedLabel` — la SEGUNDA lista mantenida a mano, y no la cierra este ticket

`ConfirmParkingUseCase` no pregunta por la debilidad del armado: pregunta por
`isVerifiedLabel = {verified_speed, verified_enter, verified_late, inherited_drive}`, y un armado
"verificado" **desactiva** el guard de aserción y el de repark.

- Para `enter_at_car` la omisión juega a favor: no está en la lista → `!isVerifiedLabel` es cierto →
  **los dos guards SÍ se aplican**. No hay regresión ni agujero por esta vía hoy.
- Pero las dos listas ya **discrepan sobre `verified_enter`**: para la política nueva es un EVENTO y
  pregunta; para `ConfirmParkingUseCase` sigue siendo "verificado" y desarma sus dos últimos guards.
  Es preexistente —este ticket no lo introduce ni lo empeora— y es exactamente la forma que
  `DET-FAIL-CLOSED-BY-CONSTRUCTION-001` (§Pieza 3 del rediseño) tiene que cerrar: una segunda
  clasificación por strings que puede desincronizarse de la primera.

**Decisión: exento aquí, anotado allí.** Unificar las dos clasificaciones cambia qué armados pierden
los guards de aserción/repark, que es un delta de conducta propio y merece su propio replay — no se
mete de polizón en el ticket que arregla el confirm silencioso.

### `enterArmStepVetoMs` — el mecanismo que habría cazado esto, atado a la otra etiqueta y apagado

`CoordinatorParkingDetector.kt:621` ya sabe degradar un ENTER espurio: si el primer paso llega
demasiado pronto tras el armado y el stream no vio conducir, marca la evidencia como `self_observed`
y **re-arma el false-ENTER abort**. Es literalmente la forma del FP de la parafarmacia (armado por
ENTER, cero pasos al principio, `provenMaxSpeedMps` bajo el listón). No disparó por dos motivos
independientes: compara por igualdad exacta con `LABEL_VERIFIED_ENTER` —y el armado era
`enter_at_car`— y `enterArmStepVetoMs` viene **`0L` (apagado) por defecto**
(`ParkingDetectionConfig.kt:318`).

**Exento con razón:** encenderlo y generalizarlo es un cambio de conducta *aguas arriba* (aborta la
sesión en vez de preguntar al final) con su propio riesgo de falso negativo. Candidato directo para
`DET-TWO-TIER-SENTRY-001`, donde el problema es el armado y no el confirm.

## Follow-ups deliberadamente fuera de alcance

- **`DET-NOTIFIED-MUST-EXPIRE-001`** — la sesión de casa (30-08 01:34→01:41+) se queda clavada en
  `phase=Notified` con `userConfirmed=false`, reevaluando y relogueando el mismo veredicto en cada
  fix (~4–6 s) indefinidamente; `stoppedDur` iba por 368 s y subiendo al tirar el log. Es el FN de
  casa y la quema de batería. Otro invariante, otro ticket.
- **`DET-FRESH-INSTALL-IS-NOT-BLIND-001`** — el rename a `com.rndeveloper.paparcar` reinstaló la app
  como paquete nuevo (29-08 21:36) y el uid resultante no tiene `diagnostics_config`, así que ninguna
  sesión de esa noche llegó a Firestore. Toda beta nueva nace ciega.
