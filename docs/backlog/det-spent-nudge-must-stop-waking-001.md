# DET-SPENT-NUDGE-MUST-STOP-WAKING-001 · Un nudge gastado no despierta cada 24 h para siempre

**Estado:** ✅ Done (28-08-2026) · mergeada a master vía squash · 1.711 tests verdes (3 nuevos) · mock compila

## Problema
`FirstParkNudgeWorker` es un periódico de 24 h cuyo único trabajo es el nudge de cold-start
"aparca una vez". El evaluador (`EvaluateFirstParkNudgeUseCase`) se auto-desactiva de forma
PERMANENTE en dos casos — `hasConfirmedFirstPark = true` (primer park confirmado) o
`firstParkNudgeCount >= 3` (cap duro) — pero el worker periódico **sigue instalado y despertando
cada 24 h para siempre**, evaluando y devolviendo `success` sin hacer nada. Además
`PaparcarApp.enqueueKeep` lo re-instala en cada arranque aunque esté gastado.

Detectado en el repaso de workers del 27-08-2026 con la lente de Android 16: los jobs lanzados
con un FGS residente pasan a consumir cuota de runtime compartida. Cada despertar de este worker
gastado es cuota que se le roba al `ParkingSafetyNetWorker` — el que tiene 50 salidas
reconstruidas (`safety_net_dispatch_stepbudget`) en telemetría y no puede permitirse el bucket
degradado.

## Doctrina violada
Ninguna regla escrita — es higiene de recursos. La forma sí toca doctrina: el estado "gastado"
debe decidirse en UN sitio (predicado puro compartido), no duplicarse en worker y scheduler
[DET-VERDICT-NOT-PREDICATE-001: predicado compartido por 2+ consumidores → función pura de nivel
superior].

## Señales / datos disponibles
- `AppPreferences.hasConfirmedFirstPark`, `firstParkNudgeCount`, `lastFirstParkNudgeAtMillis` —
  ya persistidos, ya leídos por el evaluador.
- `shouldSendFirstParkNudge(...)` (función pura, `EvaluateFirstParkNudgeUseCase.kt`) ya codifica
  el gate; lo que NO existe es la noción separada de "gastado para siempre" (≠ "hoy no toca").

## Diseño
El invariante: *un nudge permanentemente gastado no posee ningún reloj.*
1. **Predicado puro** `isFirstParkNudgeSpent(hasConfirmedFirstPark, nudgeCount)` junto a
   `shouldSendFirstParkNudge` (mismo fichero, mismo patrón). "Gastado" = confirmó un park o
   agotó el cap. El cooldown NO es gastado.
2. **El worker se auto-cancela**: tras evaluar, si está gastado →
   `WorkManager.cancelUniqueWork(TAG)`. Es el punto que se ejecuta seguro aunque el estado se
   gastara con la app muerta.
3. **El scheduler no re-instala un reloj gastado**: `FirstParkNudgeWorker.enqueueKeep` (o su
   caller en `PaparcarApp`) consulta el mismo predicado y no encola si está gastado. Sin esto, la
   auto-cancelación del punto 2 duraría hasta el siguiente arranque.
4. `shouldSendFirstParkNudge` puede reescribirse sobre el predicado (spent → false) para que no
   haya dos verdades.

## Criterio de éxito
- Test unitario del predicado: gastado por park confirmado, gastado por cap, NO gastado por
  cooldown.
- Test del gate de enqueue (si el punto 3 acaba en una función pura testeable).
- Observable: tras `hasConfirmedFirstPark`, `adb shell dumpsys jobscheduler` deja de listar el
  periódico de `FirstParkNudgeWorker` después del siguiente tick / arranque.

## Consumidores auditados (grep `FirstParkNudgeWorker|hasConfirmedFirstPark|firstParkNudgeCount`)
- `PaparcarApp` — **cerrado**: era el único enqueue del periódico; `enqueueKeep` sustituido por
  `syncSchedule(workManager, nudgeSpent)` (borrado limpio, no quedan dos entradas).
- `FirstParkNudgeWorker.doWork` — **cerrado**: auto-cancel tras el show (el tick del 3er nudge
  también retira el reloj).
- `shouldSendFirstParkNudge` — **cerrado**: reescrito sobre el predicado; misma semántica
  (`!confirmed && count<MAX` ≡ `!spent`), tests previos en verde sin tocar asserts.
- `ConfirmParkingUseCase.setHasConfirmedFirstPark()` — **cubierto por convergencia**: commonMain no
  conoce WorkManager; el siguiente tick del worker o el siguiente app-start cancelan. A lo sumo un
  despertar más.
- `BootCompletedReceiver` — **exento**: no encola este worker (verificado por grep).
- Fakes (`FakeAppPreferences`, `FakeOtherDataSources`) e `IosAppPreferences` — **exentos**: solo
  implementan la interfaz de prefs, no duplican la regla.

## Resultado
- `isFirstParkNudgeSpent` en `EvaluateFirstParkNudgeUseCase.kt` + 3 tests
  (`should_beSpent_when_firstParkConfirmed` / `…capExhausted` / `should_notBeSpent_when_underCapAndNeverParked`).
- Entrada en `docs/detection/PARKING-DETECTION.md` Sección 2.
- Sin strings nuevos, sin cambios de UI/mock, sin `detectionPath` nuevo.
