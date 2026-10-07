# DET-A-WALK-IS-NOT-AN-EGRESS-001 · Andar no es bajarse de un coche

**Estado:** ✅ Done · master `b4bf68862` · cierra la **issue #72**

## Problema

Falso positivo de campo (20-09, Cádiz): el user salió **andando** de su casa y se le guardó una zona
de aparcamiento de 250 m. La cuenta no tenía diagnósticos activos.

## Lo que sí se pudo leer

`users/{uid}/parkingHistory/8d931ac1-…` en Firestore:

```
detectionPath = "unattended_zone_no_drive_egress"   ← el culpable, ya en el provenance
armEvidence   = "self_observed"
publishedSpot = false        ← las guardas de PUBLICACIÓN aguantaron
```

## Causa raíz

Rama `NO_DRIVE_EGRESS`: guarda zona **sin conducción medida** si `liveEgress && vehicularSignal`.

`liveEgress` son **8 pasos y 18 metros** — no discrimina nada, porque andar y bajarse de un coche
producen la misma señal. Todo el peso caía en `vehicularSignal`, cuya primera rama era
`vehicleExitConfirmed` **a secas**, con velocidad medida cero.

⚠️ Y estaba **escrito como test**, defendido por su comentario. El bug era una creencia documentada,
no un descuido.

## Fix

| | Qué |
|---|---|
| **B · guard** | el AR necesita `arEgressMinCredibleFixes = 1` fijación creíble. Lo que pierde cae en `Ask(NO_DRIVE)`: pregunta, no silencio |
| **A · dato** | `UserParking.decisionEvidence` — **un** campo con los números de la rama que decidió. Room v15→v16 |
| **A1 · traza** | `exitHint`, `rawPeak` y `credibleFixes` entran en `unattendedVerdictTrace`, que las decidía sin nombrarlas |

⛔ **Un campo, no diez**, por petición expresa: sólo lo que esa rama mira.

## Consumidores auditados

`decisionEvidence` recorre el mismo camino exacto que `armEvidence`: dominio → `UserParkingEntity`
(+ `MIGRATION_15_16`) → `ParkingHistoryDto` → `FIELD_DECISION_EVIDENCE` en Firestore → los **4**
sitios de `ParkingSessionMapper` → `ConfirmParkingUseCase` → `confirm` → `saveZone`.

| Sitio | Estado |
|---|---|
| `vehicularSignal` | **cerrado** — único punto de decisión |
| `SharedCarDto` | **exento**: el espejo del grupo no lleva mecánica de detección [VEH-SHARED-001] |
| otras ramas de `UnattendedSaveReason` | **cubiertas por convergencia**: todas pasan por el mismo `saveZone` |

## Tests

- **testigo**: AR solo → `Ask`. Es el test que antes afirmaba lo contrario.
- **la mitad que no se pierde**: AR + una fijación creíble → `SaveZone`.
- **3.534 tests verdes**, 0 fallos, con `--rerun-tasks` (migración incluida).

## Pendiente

⏳ **Campo.** Diagnósticos **ya activados** para esa cuenta (`diagnostics_config`), así que el
próximo caso traerá `exitHint`/`credibleFixes` sin que haya que adivinar.
