# DET-STARVED-PROMPT-HAS-NO-WITNESS-001 · si el stream muere mientras preguntamos, el aparcamiento se pierde en silencio

**Estado:** 🔵 En progreso · rama `feature/DET-STARVED-PROMPT-HAS-NO-WITNESS-001-starved-prompt` ·
worktree `../Paparcar-starved-prompt` · apilada sobre `DET-THE-EVIDENCE-MUST-REACH-THE-TRACE-001`

Sale de una pregunta del user: *«¿qué pasa si desde el último fix malo no nos vuelve a llegar otro
bueno en 1 hora?»*

## Problema

`ResponseTimeoutStage` es un **`SessionStage`**, y los stages corren `evaluate(state, fix, now, …)` —
**sólo cuando llega un fix**. Consecuencia, medida leyendo el código:

| escenario | qué pasa hoy |
|---|---|
| llegan fixes, aunque sean malos | a los 15 min el stage dispara y guarda zona con su radio de duda ✅ |
| **no llega ningún fix** | **el stage nunca corre. Ni pin, ni zona, ni nudge, ni una línea en la traza** 🔴 |

Es el mismo agujero que `DET-STARVED-HOLD-HAS-NO-WITNESS-001` tenía en el HOLD — y ahí sí se puso un
watchdog. La ventana del prompt se quedó sin él.

Y no es un caso raro: **el prompt salta justo cuando el usuario se aleja del coche y entra en un
edificio**, que es exactamente cuando el GPS se muere.

## Doctrina violada

*Todo trigger dispara SIEMPRE, aunque llegue tarde, con verificación tardía.* Aquí no dispara nunca.
Y el corolario del user: **un aparcamiento perdido teniendo datos es un bug NUESTRO**.

## Diseño

Un watchdog espejo del hold: al vencer `confirmationResponseTimeoutMs + PROMPT_WATCHDOG_MARGIN_MS`
con el MISMO prompt aún en pie, re-ejecuta el veredicto sobre **el último fix que la sesión vio**.

⚠️ **No es una segunda copia del veredicto.** Reejecuta la MISMA stage a través del MISMO `runStage`,
así que la decisión, sus efectos y su línea de traza son los de la stage. Una implementación paralela
es como dos caminos acaban coincidiendo por suerte y divergiendo en campo. Lo único que aporta el
watchdog es el fix que el stream no mandó.

Si la sesión **nunca vio un fix**, no hay nada que colocar — y lo dice, porque un pin ausente con una
línea ausente no se distingue de un crash.

### ⚠️ Sin guarda `> 0`, a diferencia del hold

`confirmHoldMs` es `require(>= 0)` y su propio mensaje dice *«0 disables the post-confirm hold»*: es
una **costura de test** real, y tres ficheros la usan. `confirmationResponseTimeoutMs` **no puede ser
0**: `require(confirmationResponseTimeoutMs > lowNotifTimeoutMs)` + `require(lowNotifTimeoutMs > 0)`
lo hacen estrictamente positivo en construcción. Una guarda aquí sería código muerto **con aspecto de
interruptor**, y el siguiente que pase iría a buscar cómo apagarlo. (Lo cazó el user preguntando si
era mutable.)

## El test, y por qué la primera versión no valía

La primera versión pasaba **también con el watchdog neutralizado**. Dos motivos, los dos instructivos:

1. **La aserción era trivial.** `confirmationNotifOps.isNotEmpty()` es cierto en cuanto sale el
   prompt — el propio prompt es una notificación. Ahora se compara contra una **línea base** tomada
   antes de que muera el stream: lo que se afirma es que el contador **se movió**.
2. **El escenario no llegaba al prompt.** El stub emitía un fix parado y ya; el prompt necesita
   quietud sostenida (en campo, `⊘ Low/Medium notif suppressed — no vehicleExit` hasta pasar
   `lowNotifTimeoutMs`). Se vio con un volcado: `ops=[dismiss]` — la única notificación era el
   descarte de arranque de sesión. Ahora el stub se está quieto de verdad, 20 fixes.

También: `testScheduler.runCurrent()` y **no** `advanceUntilIdle()` antes de la línea base — el
segundo salta el tiempo virtual por delante del `delay` del watchdog y lo resuelve ANTES de medir,
que es la tercera forma en que este test se ponía verde sin demostrar nada.

**Falsación verificada**: con el watchdog el test pasa; neutralizándolo se pone rojo.

## Consumidores auditados

| sitio | clasificación |
|---|---|
| `ResponseTimeoutStage` (camino por fixes) | **cubierto por convergencia** — el watchdog reejecuta ESTA stage |
| watchdog del hold | **exento**: otra ventana, ya tiene el suyo |
| `ParkingSafetyNetWorker` | **exento**: reconcilia aparcamientos ACTIVOS, y aquí no llegó a haber ninguno |

## Criterio de éxito

- ✅ Prompt + stream muerto → el veredicto corre igualmente y deja `Decision` + `SessionEnded` en la traza.
- ✅ Sesión sin un solo fix → no inventa un pin, y lo dice.
- ✅ **1.816 tests en verde**, falsación comprobada.
