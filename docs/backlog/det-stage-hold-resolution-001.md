# DET-STAGE-HOLD-RESOLUTION-001 · P3.10 — la primera de la precedencia, y dos ramas que NO terminan la pasada

**Estado:** ✅ Done (2026-08-25) · rama `refactor/DET-STAGE-HOLD-RESOLUTION-001-p3-10` ·
worktree `../Paparcar-stage-10`

Paso **P3.10**. **Cierra las diez mudanzas de etapa.** Sigue a `beb2a2a3` (P3.9).

## Qué mueve

[DET-C-02] Un confirm retenido **posee el fix que si no lo re-decidiría**. Nada la supera, y dos
tests de P0.1 lo sostienen. El afilado: cuando el usuario contesta **DURANTE** un hold, el pin va
donde estaba el COCHE cuando se abrió el hold, no donde está la persona cuando toca.

## El hallazgo: dos de las cuatro ramas NO terminan la pasada

Nadie lo había escrito en ninguna parte.

| Rama | ¿Termina la pasada? |
|---|---|
| rancio al asentar | **NO** — descarta y sigue detectando hacia la plaza real |
| asienta | sí |
| arrancó | **NO** — igual que el rancio |
| sigue reteniendo | sí, y no cambia nada |

**Un descarte no es un final.** Dice *este pin estaba mal*, y la sesión tiene que seguir para
encontrar el bueno — que es el propósito entero del hold.

- Si un descarte terminara la pasada, **retrasaría un fix cada aparcamiento real**.
- Si la rama de «sigue reteniendo» cayera hacia abajo, las etapas de debajo **re-decidirían un fix que
  el hold ya reclamó**.

En el código viejo la diferencia era **si al bloque le seguía un `return`**.

## Un guardrail disparó, con razón, y se AMPLIÓ en vez de debilitarse

`HoldLaneGuardrailTest` [DET-HOLD-BRANCHES-MUST-SPEAK-001] comprueba que ningún `HoldAction` esté
muerto, y nombraba **un fichero**.

Las acciones ahora las **NOMBRA la etapa** y las **REGISTRA el orquestador** — que es exactamente lo
que es una etapa. Así que la comprobación sigue a la lane por los dos ficheros. Falla en lo mismo que
fallaba antes: **una salida que nadie produce**.

Su hermano —que `DetectionEvent.Hold` se construye en **UN** solo sitio— pasa intacto: la lane sigue
teniendo una sola puerta.

Conviene distinguirlo de debilitar un test: la propiedad protegida es *ninguna salida muerta*, no *en
este fichero*. Cambiar el alcance para seguir al código es mantener el guardrail; cambiar el umbral
para que pase habría sido romperlo.

## `DiscardHold` ata el descarte a su traza

Las dos mitades **siempre viajan juntas, y una vez no lo hicieron** — ese hermano mudo es la razón de
que exista el ticket. Un solo efecto es cómo eso se queda arreglado.

## Doctrina

Ninguna tocada. **Cero cambio de conducta.**

## Tests

Ni un assert editado. **6** de precedencia (P0.1) verdes, **18** replays verdes, los **2** del
guardrail del hold verdes. **1.629 tests**, 0 fallos. `assembleMockDebug` ✅.

---

## Las diez mudanzas, cerradas

`5e52c641` (andamio) → aquí.

| Coordinator | Líneas |
|---|---|
| antes de la Fase 3 (`44f8ba5d`) | 3.336 |
| tras las diez etapas | **2.567** |
| las diez etapas + andamio | 1.545 |

**En ninguna de las diez se editó un assert.** Las seis pruebas de precedencia de P0.1 y los 18
replays estuvieron verdes en cada commit.

### Lo que la fase enseñó, por si sirve para las que quedan

1. **Un andamio diseñado sin consumidor se falsifica solo.** Cuatro correcciones de FORMA en las dos
   primeras etapas; cero a partir de la quinta.
2. **El snapshot muerde tres veces** (P3.1 `pendingConfirm`, P3.3 la línea de frescura, P3.6 la
   atribución). Misma raíz, y la cura es P3.13.
3. **Dos respuestas nunca caben en un booleano** — `endsPass` contra `endsSession` costó tres replays.
4. **Cuando el plan y el código no coinciden, gana el código** — el efecto `ResolveVehicle` que el
   plan describía no podía existir.

Siguiente: **P3.11**, `DetectionEffectExecutor` — el ejecutor inline que ha ido creciendo dentro del
coordinator se muda a su fichero, y su criterio de aceptación es un test de arquitectura: **ninguna
etapa importa un repositorio**.
