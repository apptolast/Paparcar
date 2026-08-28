# DET-REFUTED-STILLNESS-CANNOT-MATURE-AN-ANCHOR-001 · un stop refutado 4 veces maduró un ancla por TIEMPO con el reloj intacto

**Estado:** ✅ Done · en master (hash en `MEMORY.md`) · 1.724 tests verdes · ⏳ sin conducir
**Origen:** field 2026-08-28 ~00:52–01:18, Redmi (Coordinator puro), sesión `1787871129368`.
FP: pin `743342c4` plantado DENTRO de casa (Calle Góndola 3, `detectionPath=user`) a ~41 m del
coche real (el Oppo, mismo viaje físico, confirmó `kinematic+egress` en Calle de la Fragata 31 a
la 01:09 en silencio). El mismo bug produjo también el lado FN: «no validaba» — todos los
`kinematic+egress` del Redmi degradaron a prompt con `egress_not_at_anchor`.

## Problema

Volviendo a casa, el GPS degradó a fixes de red (accuracy 64–266 m, speed=0 declarada) con el
coche AÚN en marcha. `DET-STOP-MUST-BE-STILL-IN-SPACE-001` hizo exactamente su trabajo — refutó el
stop **cuatro veces** antes de la congelación (00:58:32, 00:58:54, 00:59:05, 00:59:31: «the car
was still moving — not evidence of rest»)… y aun así:

```
00:59:42  ⚓ anchor FROZEN — drive-entered stop matured (time=104593ms, walkFixes=0)
```

El ancla se congeló EN MITAD DE LA RUTA, a ~3,5 km del aparcamiento real. La cadena completa:

1. **00:57:57** se abre el stop sobre un fix de red mid-route (`stoppedSince=1787871477465`).
2. **00:58:32–00:59:31** cuatro refutaciones DET-STOP-MUST-BE-STILL-IN-SPACE-001.
3. **00:59:42** `restProvenByTime` = `now - startedAt ≥ anchorFreezeStopMs` madura el ancla:
   el guard `!stillnessRefuted` solo protege EL BEAT refutado, y el reloj (`stoppedSince`) guarda
   íntegro el crédito de la quietud ya refutada. El comentario del código promete lo contrario:
   *«A creeping stop keeps its clock; what it loses is the right to call itself proven»* — pero el
   camino por TIEMPO se lo devuelve un beat después.
4. **~01:04** el coche aparca DE VERDAD en Fragata; llegan fixes de 4–16 m en el sitio (01:07).
   Ninguno puede ser ancla: `pinnedToOtherStop` — el ancla congelada pertenece al stop fantasma.
5. **01:04–01:09** los candidatos expiran «without egress proof» y `kinematic+egress` degrada en
   bucle con `egress_not_at_anchor`: los 223 pasos y el egress real se miden contra un ancla a
   3,5 km. **Cero auto-confirm** (el Oppo, con ancla sana, confirmó a la 01:09).
6. **01:11:04** un fix espejismo (11,4 m/s declarados, acc 81,8 m, usuario ya en el sofá) — la
   puerta de accuracy lo rechaza como conducción («⊘ ignoring driving-speed fix») y EN EL MISMO
   BEAT `DET-CREDIBLE-DRIVE-001` lo acepta como «SUSTAINED DEPARTURE — position ran 4205 m from
   the anchor»: la distancia era real porque el ANCLA era falsa. Eso resolvió CAR → limpió ancla,
   stop, fase Notified y odómetro walk-in (ver ticket hermano
   `det-displacement-drive-must-survive-its-next-fix-001`, §evidencia 28-08).
7. **01:11:32–01:11:50** el primer fix de DENTRO de casa (16,9 m) abre stop «drive-entered»
   (el «drive» era el espejismo) y el ancla se re-congela en casa (`stableFixes=3, walkFixes=1`).
8. **01:14:32** re-prompt (el de la 01:01 se mostró EN MARCHA a ~50 km/h, mismo reloj envenenado,
   y murió con la fase). **01:18:12** el user pulsa «Sí he aparcado» → `UserConfirmStage` rama 1
   (egress nacido en el ancla… la de casa) → pin = ancla = dentro de casa, reliability 1.0.

## Doctrina violada

*Fallo asimétrico: ante la duda se pregunta, nunca se planta un pin* — y su corolario de ancla:
**el ancla solo puede congelarse donde el movimiento medido demuestra que el coche DESCANSÓ**. Una
quietud refutada por su propia traza no es descanso, y el tiempo transcurrido dentro de esa
refutación no puede contar como prueba de descanso.

## Invariante

**Madurar por tiempo exige `anchorFreezeStopMs` de quietud SIN refutar.** Una refutación
DET-STOP-MUST-BE-STILL-IN-SPACE-001 no solo reinicia el quórum de fixes (ya lo hace): reinicia
también el crédito del reloj de maduración.

## ⚠️ La trampa conocida (no pisarla)

`StopTracking.kt` (~línea 160) documenta por qué `stoppedSince` NO se reinicia en la refutación:
reiniciarlo reabría `initialStopWindowMs` a mitad de stop y permitía re-capturas que master nunca
permitió — **los dos replays de Enamorados lo cazan**. El fix NO toca `stoppedSince`: añade un
testigo separado (p. ej. `lastStillnessRefutedAt` en `AnchorTrust`) y `restProvenByTime` mide
desde `max(startedAt, lastStillnessRefutedAt)`. La ventana de captura y el scoring quedan como
están salvo lo que diga la auditoría de consumidores.

## Diseño ejecutado (as-built)

**Dos relojes: el de PREGUNTAR y el de PROBAR.** Una refutación revoca la EVIDENCIA del stop,
nunca su reloj:

- `AnchorTrust.stopEvidenceSince` (campo nuevo): dónde empezó la racha de quietud SIN refutar.
  Avanza al fix refutador en cada refutación; `restProvenByTime` y la ventana de captura
  (`withinInitialWindow`) miden desde ahí. `stoppedSince`/`stoppedDurationMs` quedan INTACTOS
  (scoring, prompts) — preguntar es el lado barato de la doctrina asimétrica, y NO se pisó la
  trampa de Enamorados (el reloj del stop no se reinicia).
- `AnchorTrust.disownedByRefutation()`: la refutación DESHEREDA un ancla no-pinned capturada en el
  stop refutado (el track probó que esos fixes eran movimiento) → el concurso de accuracy se
  reinicia entre fixes no contradichos. Un ancla PINNED es intocable por construcción
  (`!isAnchorPinned` en la condición); la mancha gap-entered sobrevive.
- La línea `⚓✗ anchor DISOWNED…` se imprime en parkdiag; el `time=` de la línea de freeze imprime
  ahora la racha sin refutar (cambio de bytes en parkdiag, deliberado y más honesto).

**Descubierto por el replay, una capa más abajo:** con el ancla ya honesta, `steps+egress` guardó
un pin EXACTO sobre un fix de 92,9 m → segundo guard, doc propio:
`det-inferred-pin-carries-its-doubt-001.md`.

## Criterio de éxito — RESULTADO

- ✅ **Replay 1:1 del stream real** (`Trace_Redmi2808RefutedStillness`: 216 fixes, 287 steps,
  AR exit y el tap del user en su segundo de campo, generado con `trace2fixture`). Con el guard
  NEUTRALIZADO el replay reproduce el pin de campo **byte a byte** (`path=user`, rel 1.0, exacto,
  `36.6084105,-6.2780907` — dentro de casa). Con el guard vivo: `steps+egress` guarda ZONA honesta
  silenciosa (r≈93 m) que cubre el coche, ~12 min antes de que el build de campo preguntara.
  ⚠️ Matiz sobre la promesa original ("congela en Fragata con el fix de 4,27 m"): el stream del
  Redmi NUNCA vio el descanso del coche con buena accuracy — los fixes de 4–16 m de la 01:07 son
  el PEATÓN junto al coche. El desenlace honesto es la zona, no el pin exacto del Oppo.
- ✅ Regresión: Enamorados (×3), Calle Gavia, Góndola 22-08, Camelias, supermarket, motorway —
  los 13+ replays y la suite entera verdes (1.724 tests, 0 fallos).
- ✅ `DET-SHORT-TRIP-FREEZE-001` (maduración por `stableFixes`) intacta.
- ✅ Unit tests nuevos: `should_not_spend_refuted_stillness_as_time_credit_toward_the_anchor_freeze`
  · `should_disown_an_anchor_captured_from_fixes_the_stop_later_refuted` (coordinator, junto a los
  de DET-STOP-MUST-BE-STILL).

## Consumidores auditados

- `restProvenByTime` (StopTracking) — **cerrado** (mide `evidenceSince`).
- Captura del ancla / `mayCapture` — **cerrado** (ventana desde `evidenceSince` + desheredamiento).
- `stoppedDurationMs` aguas abajo (ConfidenceScoringStage, CandidateStage, FastConfirmStage,
  FalseEnterAbortStage, PreDriveSkipStage, ResponseTimeoutStage, HoldResolutionStage,
  UserConfirmStage) — **exentos con razón**: son el lado PREGUNTAR (scoring/prompt/timeout);
  la doctrina asimétrica tolera preguntar sobre quietud declarada. Consecuencia asumida: el prompt
  en marcha de la 01:01 puede seguir ocurriendo — es ruido de notificación, no un pin; si molesta
  en campo, ticket propio.
- `frozenByRest` — solo lo leen `isAnchorPinned` / `hasKinematicEgressSignal`
  (AnchorPredicates) — **cubiertos por convergencia**: cambia CUÁNDO se congela, no qué significa.
- `EvaluateSafetyNetCheckUseCase` / `EvaluateUnattendedParkingSaveUseCase` — **cubiertos por
  convergencia**: leen el ancla ya saneada; cero cambios.
- `UserConfirmStage.whereTheCarIs` rama 1 — **cubierto por convergencia**: su premisa («un ancla
  congelada es el coche») vuelve a ser cierta. Sin tocar.
- `anchorFreezeStopMs` — sin más lectores (config + StopTracking).
- `stopEvidenceSince` — campo nuevo, único escritor/lector StopTracking (+ reset en
  `onMovingFix`).
