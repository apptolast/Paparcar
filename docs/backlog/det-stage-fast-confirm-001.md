# DET-STAGE-FAST-CONFIRM-001 · P3.2 — dos preguntas que eran un booleano

**Estado:** ✅ Done (2026-08-25) · rama `refactor/DET-STAGE-FAST-CONFIRM-001-p3-2` ·
worktree `../Paparcar-stage-2`

Paso **P3.2**. Sigue a `5cbe59a2` (P3.1).

## Qué mueve

[DET-D-03] La vía de **pasos + egress** pasa a `stages/FastConfirmStage.kt`: el usuario condujo,
paró, dio pasos suficientes Y se alejó lo bastante del coche. No queda nada que esperar, así que
esta vía **se salta la ventana de observación entera** (`elapsedSinceHighMs = 0`).

Con ella viaja su par de contador mudo, el egress cinemático [DET-KINEMATIC-EGRESS-001]: los dos
testigos de la misma caminata quedan en **un predicado dentro del veredicto al que alimentan**,
como manda `DET-VERDICT-NOT-PREDICATE-001`.

## Tres replays cazaron el defecto de verdad

Y la forma se repite, así que conviene dejarla escrita:

> Un fast confirm **siempre** termina la PASADA, pero que termine la SESIÓN depende de si el confirm
> quedó retenido en su ventana de gracia.

Son **dos respuestas**, y la rama siempre lo supo: `completed = beginConfirm(...)` y luego
`return@collect`, dos sentencias. Al colapsarlas en un solo booleano devuelto por el runner, la
sesión sencillamente **no terminaba nunca** — y `calle_gavia_001`, `enamorados_001` y
`same_trace_with_speed_verified_arm` se pusieron rojos a la vez.

`StagePass(endsPass, endsSession)` devuelve la distinción como tipo.

## Cuarta corrección del andamio

`degradeToPrompt` se queda como **un solo efecto compuesto** en vez de partirse en dismiss + notify
+ record + cambio de estado: **lee su PROPIO reloj** para el instante del prompt. Partirlo obligaría
o a mover esa lectura o a inventar una segunda, y las dos cambian lo que dice la traza.

Van cuatro correcciones al andamio de P3.0 en dos etapas. No es mala señal — es la señal de que el
andamio se diseñó sin un consumidor, que es exactamente lo que el plan pedía. Lo que sí conviene
recordar es que **el censo de P3.0 no valía como comprobación**: mapeaba por nombre.

## El adaptador compartido deja de ser una lambda por etapa

`parkingDecisionInput` ya tiene dos de sus tres consumidores, así que pasa a ser **un método con
nombre**. Sigue en el coordinator a propósito: es lo que mantiene el TIPO de vehículo como lectura
viva, exactamente como lo leen las ramas. Se muda a `stages/` con el tercer consumidor.

## Doctrina

Ninguna tocada. **Cero cambio de conducta.**

## Tests

Ni un assert editado. Los **6** tests de precedencia de P0.1, verdes. Los **18** replays, verdes.

**1.629 tests**, 0 fallos. `assembleMockDebug` ✅.

Siguiente: **P3.3**, `CandidateStage` — el tercer consumidor del adaptador, que se lo lleva a
`stages/`.
