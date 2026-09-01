# DET-A-DISOWNED-ANCHOR-TAKES-ITS-WALK-WITH-IT-001

> **Estado:** ✅ **Done** — mergeado a master el 31-08-2026 (squash, `d74e6e8c`). Rama y worktree
> borrados.
> **Origen:** ir a cerrar el «**bug #6**» del egress birth. **El bug era otro**, y el #6 resultó ser
> la regla, no deuda.

---

## 1. Lo que iba a hacer, y por qué no se hizo

`withEgressBirth` recibe `acceptsKinematicWitness = false` en la rama STOPPED y `true` en la MOVING.
El comentario de producción lo llamaba, dos veces, *«bug #6 — preserved and named, never fixed inside
a move»*, pendiente de *«un replay dirigido o datos de campo»*.

La hipótesis era: con el contador de pasos mudo no se abre birth nunca, así que aceptar el testigo
cinemático en la rama STOPPED le daría una. **Las dos mitades de esa frase son falsas**, y lo son de
forma medible.

### 1.1 ⛔ La mitad falsa de la premisa

El contador `kinematicEgressFixes` **sólo se incrementa en la rama MOVING** — y en ESE mismo fix se
llama a `withEgressBirth` con la bandera ya en `true`. Así que la caminata de un usuario con podómetro
mudo **sí** abre birth, siempre que sus fixes reporten ≥ `stoppedSpeedThresholdMps` (1 m/s) con
accuracy creíble.

### 1.2 ⛔ Y voltear la bandera no es un no-op: es dañino

Sonda directa sobre `AnchorTrust` (ahora test):

```
PROBE after disown: anchor=null birth=null kinFixes=3 frozen=true
PROBE flag ON  -> birth=90000 steps=0
PROBE flag OFF -> birth=null
```

Con la bandera a `true`, un fix PARADO abre una birth **en el propio fix parado — es decir, sobre el
ancla recién capturada — a partir de un contador ganado antes**. `judgeEgressBirth` la leería como
`BORN_AT_ANCHOR`: **resucitaría el «no hay duda» que `DET-NOTHING-TO-JUDGE-IS-NOT-NO-DOUBT-001`
acababa de quitar, y esta vez disfrazado de medición.**

La asimetría es la regla, no deuda. Los dos comentarios se reescriben para decirlo.

---

## 2. El bug que la sonda SÍ encontró

`disownedByRefutation()` dice en su propio KDoc que el ancla se va *«the same way a resolved car
movement takes it»*. Un movimiento de coche (`onMovingFix(anchorCleared = true)`) limpia **cinco**
cosas: ancla, stop-of-record, captura sellada, **`frozenByRest`** y **el contador cinemático** (que su
llamante pasa a 0). El disown limpiaba **tres**.

Los dos supervivientes los lee **el ancla que se recaptura después**, que es otra posición:

```kotlin
hasKinematicEgressSignal = frozenByRest && anchor != null && kinematicEgressFixes >= min
```

→ el **camino de confirmación cinemático podía dispararse sobre el ancla nueva con fixes ganados
caminando desde la vieja** — la que la propia sesión acababa de declarar refutada
(`DET-REFUTED-STILLNESS-CANNOT-MATURE-AN-ANCHOR-001`, campo 2026-08-28: el ancla pegada al fix de
apertura 3,5 km atrás, refutada cuatro veces).

Y es lo que hacía alcanzable el contador viejo en un fix parado, o sea lo que volvía peligroso voltear
la bandera. Un bug, dos consecuencias.

**Arreglo:** el disown limpia las cinco. Una línea de más y la frase de su KDoc pasa a ser cierta.

---

## 3. Barrido de consumidores

| # | fichero | qué había | qué hay |
|---|---|---|---|
| 1 | `AnchorTrust.disownedByRefutation` | limpiaba 3 de 5 | limpia las 5 (`frozenByRest`, `kinematicEgressFixes`) |
| 2 | `AnchorTrust.withEgressBirth` KDoc | *«bug #6, preserved not fixed»*, pendiente de datos | **resuelto como REGLA**, con la medición y con el hueco real señalado |
| 3 | `StopTracking` ×2 comentarios | *«bug #6 lives here»* / *«never fixed inside a move»* | dicen lo que la asimetría impide, y que la rama MOVING sí sirve al contador mudo |
| 4 | `docs/detection/PARKING-DETECTION.md` (entrada de ayer) | *«every time the step counter stayed mute»* | **corregido**: vale para «sin birth registrada», no para «contador mudo» |
| 5 | `docs/backlog/det-nothing-to-judge-is-not-no-doubt-001.md` | idem, y culpaba al bug #6 | **corregido**, con puntero al hueco real |
| 6 | `EgressEvidence`, `AnchorCapture.clearedWithAnchor` | otras asimetrías de limpieza, documentadas y deliberadas | **sin tocar** — cada una tiene su razón escrita y su propio ticket |

⚠️ **Sitio auditado y NO tocado**: `AnchorCapture.clearedWithAnchor()` conserva a propósito
`walkFixes`/`stepEvents`/`sawSteps` porque `isAnchorWalkEntered` los lee **sin** ancla, y limpiarlos
volvería «limpio» un veredicto walk-entered justo donde falta el ancla. Esa asimetría está razonada
en su KDoc y tiene su ticket (`det-state-anchor-trust-001.md`); ésta no lo estaba.

---

## 4. Tests

**Nuevos** (2, en `AnchorTrustTest`)
- `should_take_the_walk_and_the_rest_with_it_when_an_anchor_is_disowned` — las cinco.
- `should_refuse_to_open_a_birth_from_a_kinematic_witness_on_a_stopped_fix` — la sonda que zanjó el
  #6, conservada como el test que impide voltearlo luego. Afirma las dos mitades: que hoy no abre, y
  **qué produciría si abriera** (una birth con `stepCountAtBirth = 0`, sobre el ancla).

**Falsación (⛔ un test sin verlo fallar siempre pasa)** — dos inyecciones:
1. quitadas las dos líneas nuevas del disown → `should_take_the_walk_and_the_rest_with_it…` **FAILED**;
2. puesta la bandera a `false` en la mitad «qué produciría» → `should_refuse_to_open_a_birth…`
   **FAILED**.

⚠️ **Y la comprobación que NO se salta**: con la bandera volteada en producción **la suite entera
pasaba** (2052/0, incluidos los 16 replays de campo). Un cambio verde no es un cambio inocuo: lo que
demuestra es que ningún test cubría el caso, que es justo por lo que hizo falta la sonda directa.

**Suite completa:** `:shared:testDebugUnitTest` → **2054 tests, 0 fallos**.
`:app:compileProdDebugKotlin` + `:app:compileMockDebugKotlin` OK.

---

## 5. Lo que este ticket NO hace

⛔ **No arregla el hueco real que había detrás de la pregunta.** Una caminata cuyos fixes reporten
**por debajo de 1 m/s** (Doppler a 0 en interior, garaje, receptor frío) cae del lado PARADO, donde
nadie la cuenta: ni abre birth ni acumula fixes de egress. Ese usuario se queda sin los dos caminos
que no exigen pasos. Es el espejo de `DET-STOP-MUST-BE-STILL-IN-SPACE-001` (*el campo `speed` no es
posición*) y **está bloqueado por medición**: hay que contar en el `parkdiag` cuántos fixes de una
caminata real llegan así. Spec: `docs/backlog/det-a-walk-reporting-zero-is-still-a-walk-001.md`.

---

## 6. Doctrina que aplica

- *Medir antes de prometer*: la premisa del ticket cayó con una sonda de veinte líneas, y el arreglo
  que iba a hacer habría reintroducido el defecto de ayer.
- *Sistemas, no parches*: el invariante es «cuando un ancla muere, muere con todo lo suyo», y se
  compara puerta por puerta con la otra muerte (el movimiento de coche) en vez de arreglar el síntoma.
- *Un doc que sobrevive a su código es peor que ninguno*: dos correcciones a documentos escritos ayer,
  en el mismo commit que las mide.
- *Una deuda nombrada que resulta ser la regla deja de llamarse deuda*: si no, el siguiente que pase
  la «arregla».
