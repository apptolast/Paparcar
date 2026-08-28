# DET-INFERRED-PIN-CARRIES-ITS-DOUBT-001 · un pin inferido no puede afirmar más precisión que su fix

**Estado:** ✅ Done · en master, mismo commit que
`DET-REFUTED-STILLNESS-CANNOT-MATURE-AN-ANCHOR-001` (hash en `MEMORY.md`) · ⏳ sin conducir

## Problema

Lo destapó el replay del FP del 28-08 (`Trace_Redmi2808RefutedStillness`) al arreglar el ancla:
con el ancla mid-route muerta, el re-anclaje honesto cayó en un fix de red de **92,9 m** de
accuracy… y `steps+egress` lo guardó como pin **EXACTO** con reliability 0,9 — la misma forma que
el FP de campo, una calle más allá. `ConfirmParkingUseCase` hasta lo avisaba
(«⚠ poor GPS accuracy … spot position may be imprecise») y guardaba el punto exacto igualmente.

## Doctrina violada

*Fallo asimétrico + honestidad de forma*: la duda aquí es de DÓNDE, no de SI (conducción medida +
pasos + egress = el aparcamiento es real). El sistema de zonas existe exactamente para eso
(`honestZoneRadius`, DET-USER-YES-IS-NOT-A-COORDINATE-001, los saves unattended) — los carriles de
confirm INFERIDO simplemente nunca lo adoptaron.

## Invariante y dónde vive

**`inferredPinDoubtRadius(fixAccuracy, floor, ceiling)`** — función pura en
`domain/detection/physics/HonestZoneRadius.kt`, al lado de la fórmula que reutiliza:
- accuracy ≤ `honestCloseMinZoneRadiusMeters` (60 m) → `null` (el punto dice más que el área);
- por encima → radio = la accuracy del propio fix, con techo `unattendedZoneMaxRadiusMeters`.

Aplicada en los DOS embudos por los que pasa todo pin inferido:
1. **`DetectionEffectExecutor.confirm`** — fast confirm, candidate, hold settle (incluido el
   atajo de `beginConfirm` con hold desactivado, que fue lo que se saltó el primer intento en el
   dispatcher) y el save exacto del unattended. Se aplica en el SAVE final, nunca en
   `beginConfirm`: el hold anti-recado (DET-C-02) sigue corriendo sobre el pin.
2. **`ParkingBackfillWorker`** — el pin más inferido que existe (reconstruido sin sesión viva; el
   FP del 27-08 lo plantó este worker).

La demotion se imprime a parkdiag (`◯ inferred pin demoted to a ZONE r=…`): un pin que cambia de
forma en silencio sería indiagnosticable.

## Consumidores auditados

- `DetectionEffectExecutor.confirm` — **cerrado** (los 3 call sites del dispatcher + el atajo de
  `beginConfirm` convergen aquí).
- `ParkingBackfillWorker` — **cerrado**.
- `SaveManualParkingUseCase` (manual / nudge / confirmDetected) — **exento con razón**: pin
  ASERTADO por el user; un pin puesto a mano es verdad del usuario, diga lo que diga la accuracy
  del fix del teléfono.
- `UserConfirmStage` — **exento con razón**: ya decide su propia forma (zona por gap/accuracy vía
  DET-USER-YES-IS-NOT-A-COORDINATE-001); sus pines exactos llegan con accuracy ≤ 60 por
  construcción.
- `BluetoothParkingDetector` — **exento con razón**: carril determinista que no se mezcla con la
  maquinaria del Coordinator; si el campo enseña un fix BT difuso pinchando exacto, será su propio
  ticket.
- Dispatcher rama `BoundedZone` — sin tocar (sigue siendo solo del carril user; conserva su
  reliability hardcodeada — deuda preexistente, fuera de alcance).

## Criterio de éxito — RESULTADO

- ✅ Replay 28-08: con solo este guard neutralizado, `steps+egress` vuelve a guardar el pin exacto
  a 52 m del coche (rojo); con él vivo, zona r≈93 m `isApproximate=true` que cubre el coche.
- ✅ Unit: `should_demote_an_inferred_confirm_to_a_zone_when_its_fix_cannot_carry_an_exact_claim`
  (coordinator) + los dos casos puros en `HonestZoneRadiusTest`. Control: las decenas de asserts
  de pin exacto de la suite (anclas nítidas) siguen verdes — el suelo no sobre-dispara.
- ⚠️ `zoneRadiusMeters` es LOCAL-ONLY (no llega a Firestore): en remoto la demotion solo se ve por
  la línea de parkdiag.
