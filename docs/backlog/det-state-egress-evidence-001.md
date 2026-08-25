# DET-STATE-EGRESS-EVIDENCE-001 · P2.3 — tres reglas de reset que se leían como una

**Estado:** ✅ Done (2026-08-25) · rama `refactor/DET-STATE-EGRESS-EVIDENCE-001-p2-3` ·
worktree `../Paparcar-state-3`

Paso **P2.3** de la Fase 2, el primero marcado `L`. Sigue a P2.2.

## Qué mueve

Todo lo que dicen los PIES y el reconocedor de actividad — la máquina de pasos, su línea de
frescura, el odómetro crudo de eventos, el latch de sensor vivo, los contadores de cadencia de
pedaleo y las estampas AR — pasa a `domain/detection/state/EgressEvidence.kt`. Once campos.

`vehicleExitConfirmed` toma el nombre que le da el plan: **`vehicleExitHint`**. Nomina; nunca
confirmó nada, y el nombre viejo decía lo contrario de la doctrina rectora.

## Lo que arregla de verdad

Las reglas de reset eran **tres condiciones distintas** intercaladas línea a línea dentro de un
`copy` de 40 campos, donde se leen como una sola regla aplicada con consistencia:

| Reset | Lo borra |
|---|---|
| contador de pasos + línea de frescura + hint de EXIT | conducción medida |
| odómetro crudo de eventos | conducción medida **o una maniobra de re-aparcamiento** |
| carrera de salida sin pasos | que se vaya el **ancla** |

Colapsarlas es una regresión real: una maniobra de re-aparcamiento **es** movimiento de coche
resuelto para el odómetro crudo —que mide «desde el último movimiento del coche»— pero **no** para
el contador, porque el usuario recolocó el coche, no se fue conduciendo, y los pasos de egress que
ya dio siguen siendo válidos. Y la carrera sin pasos pertenece al ANCLA, no a la conducción.

`onFix` enuncia las tres juntas, precisamente porque no son la misma.

## La frontera ahora es simétrica, y es a propósito

La regla de diseño era *AnchorTrust posee el ancla; los pasos se le PRESENTAN, nunca se le copian*
[07 §2.4]. `DET-CADENCE-CANNOT-ACCUSE-AFTER-EGRESS-001` (22-08) hizo que el tráfico vaya en los dos
sentidos: el clasificador de cadencia tiene que saber si el ancla está FIJADA, porque la misma
firma —pies moviéndose junto a un fix rápido— significa cosas opuestas a cada lado de ella.

Eso no rompe la frontera, la hace simétrica: `onStepEvent` recibe el estado del ancla **como
argumento**, igual que el ancla recibe los pasos. Lo que sí obliga es a **declarar el orden de
reducción**, que es exactamente para lo que existe P2.6.

## Verificado discriminante, no supuesto

| Neutralización | Resultado |
|---|---|
| colapsar las tres reglas de reset en una | 🔴 1 test, **solo el nuevo** |
| quitar el veto de cadencia con ancla fijada | 🔴 3 tests, incluido el replay de Góndola |

La segunda importa decirla tal cual: **ese guard ya estaba bien cubierto**. El experimento sirve
para saber cuál de los dos casos es cuál, no para acumular rojos.

## Doctrina

Ninguna tocada. **Cero cambio de conducta.**

## Tests

`EgressEvidenceTest` (18): el triple gate con sus tres cláusulas alcanzables, las cuatro cotas de la
cadencia con su incidente cada una, las tres reglas de reset por separado, la línea de frescura y el
sellado forward-only del EXIT.

**1.588 tests**, 0 fallos. **Ni un fichero de test tocado.** Coordinator **−83 líneas netas**.
`assembleMockDebug` ✅.

Siguiente: **P2.4**, `state/DriveProof.kt` — el paso donde el plan avisa de que la frontera real no
es «anillos contra relojes» sino *todo lo que la conducción medida resetea*, y de que
`hasEverReachedDrivingSpeed` se queda FUERA (ya vive en `SessionTelemetry` desde P2.1).
