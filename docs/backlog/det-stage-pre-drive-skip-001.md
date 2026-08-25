# DET-STAGE-PRE-DRIVE-SKIP-001 · P3.5 — la etapa más pequeña, y la primera que le cupo al andamio

**Estado:** ✅ Done (2026-08-25) · rama `refactor/DET-STAGE-PRE-DRIVE-SKIP-001-p3-5` ·
worktree `../Paparcar-stage-5`

Paso **P3.5**. Sigue a `738e1c9e` (P3.4).

## Qué mueve

Dos líneas: **sin conducción, nada que decidir**. Todas las etapas por debajo razonan sobre un
viaje, así que hasta que la sesión esté autorizada como post-conducción la pasada termina aquí.

## Por qué merece ser una etapa y no un `return` temprano

Porque **es una afirmación de PRECEDENCIA**. Cuatro ramas la superan —el hold, el abort de
false-ENTER, el presupuesto de no-movimiento y el toque del usuario— y cinco están puertas abajo de
ella. Ese ranking no era más que **dónde caía el `if`**.

Ahora lo dice `DetectionStage`, y `StageOrderTest` falla si alguien la mueve.

## El dato del paso: el andamio dejó de moverse

Es la primera etapa que **no ha necesitado ninguna corrección del andamio** — verde a la primera.

| Paso | Correcciones al andamio |
|---|---|
| P3.1 | 3 (`AskUser.at`, `stoppedDurationMs`, partir `Prompt`) |
| P3.2 | 1 (`DegradeToPrompt` compuesto) |
| P3.3 | 0 de forma; 2 ramas nuevas |
| P3.4 | 0 de forma; 2 ramas nuevas |
| **P3.5** | **0** |

Añadir ramas al sealed de efectos es crecimiento esperado; cambiar la FORMA de `SessionStage` o de
`StageVerdict` era la señal de que el andamio se había diseñado sin consumidor. Esa señal se ha
apagado.

## ⚠️ Lo que queda escrito en la etapa

Pasar esta puerta **no prueba nada del viaje**. `driveAuthorized` es la NOMINACIÓN: el arm puede
habérsela prestado por confianza y un dismissal todavía puede retirarla [07 §3.3]. Una etapa de más
abajo sigue teniendo que preguntarle a la prueba de conducción qué se midió de verdad.

Es la doctrina rectora otra vez —*el evento NOMINA, solo el movimiento MEDIDO confirma*— y este es
exactamente el sitio donde alguien podría confundirlas.

## Doctrina

Ninguna tocada. **Cero cambio de conducta.**

## Tests

Ni un assert editado. **6** de precedencia (P0.1) verdes, **18** replays verdes.
**1.629 tests**, 0 fallos. `assembleMockDebug` ✅.

**Cinco de diez etapas movidas.** Siguiente: **P3.6**, `UserConfirmStage` — y el plan la marca como
especial: *«gana el derecho a emitir `SaveZone`»*, porque `DET-USER-YES-IS-NOT-A-COORDINATE-001` hizo
que la vía del «sí» pueda guardar zona cuando el ancla nació en un hueco. Es **el único sitio del
plan donde el refactor cierra estructuralmente un bug de omisión**: con la forma como tipo
obligatorio, un camino nuevo no puede olvidarse de decidirla.
