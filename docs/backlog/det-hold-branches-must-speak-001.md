# DET-HOLD-BRANCHES-MUST-SPEAK-001 · el hold cuenta por dónde ha salido

**Estado:** ✅ Done (2026-08-24) · rama `feature/DET-HOLD-BRANCHES-MUST-SPEAK-001-hold-notes` ·
worktree `../Paparcar-hold-notes`

Segunda entrega de la **propuesta 3** (`09 §14.3` / P4.2). La primera fue el carril de triggers
(`DET-EVERY-TRIGGER-LEAVES-A-TRACE-001`, `fb817e19`); esta es la que motivó adelantar la propuesta.

## Problema

El **hold** [DET-C-02] son los dos minutos entre *«la prueba de egress dice que has aparcado»* y
plantar el pin, abiertos para descartar una parada de recado. Tiene **siete salidas y seis eran
mudas en remoto**: la traza mostraba el confirm que acabó ocurriendo y nada del hold que lo produjo.

Y eso no es un problema de comodidad, es de **testabilidad**.
`DET-CONFIRM-BRANCH-ORDER-MUST-BE-TESTABLE-001` se propuso fijar la precedencia de tres ramas del
hold y **no pudo escribir ninguno de los tres tests**: al neutralizar la rama, la salida quedaba
**byte a byte idéntica**. Una rama que no emite nada no la discrimina ningún test — solo leer el
código y confiar.

Dos de esas salidas **plantan un pin sin ningún fix que lo justifique** (`STARVED` y
`SESSION_ENDED`). En diagnóstico de campo, eso es exactamente lo que se ve cuando *«apareció una
plaza y no sé por qué»*.

## Diseño

`HoldAction` (enum puro en `domain/detection/`) + `DetectionEvent.Hold`, con **una sola puerta**
(`logHold`). Modelado sobre `DetectionEvent.Candidate`, que ya tenía esta forma exacta — una fase
que se abre, espera y resuelve de varias maneras.

| Acción | Qué es | Antes |
|---|---|---|
| `OPENED` | un confirm tentativo abrió el hold | mudo — **y es la clave de todo** |
| `SETTLED` | venció la ventana (o el "sí") con un fix, y se plantó el pin | mudo como hold |
| `STARVED` | el stream se calló y el watchdog plantó el pin **sin fix que lo revalidara** | mudo |
| `SESSION_ENDED` | la sesión acabó con un confirm retenido y el epílogo lo finalizó | mudo |
| `DISCARDED_STALE` | la posición superó a los pasos: era un recado | ya emitía, como `Decision` ad-hoc |
| `DISCARDED_DROVE_OFF` | velocidad de conducción real dentro de la ventana | mudo |
| `DROPPED_BY_USER` | pulsaste "Parar detección" con un confirm retenido | mudo |

Dos eventos por hold, **no uno por fix**: el hold abarca ~2 min de un stream a 2 s, así que una nota
por fix costaría ~50 documentos para decir lo que la salida dice una vez.

**Cero decisiones cambiadas.** Ni un `if` de detección tocado.

## Lo que esto desbloquea, medido

El par `hold ↔ vía rápida` que la Fase 0 documentó como **inobservable** ya se puede escribir, y es
una sola aserción: **el hold abre UNA vez**.

| | `HoldAction.OPENED` |
|---|---|
| master | **1** |
| sin el `return@collect` del hold | **5** |

Cinco, no dos: sin la salida temprana, la vía rápida se re-dispara en **cada fix siguiente** y
reinicia el reloj de dos minutos cada vez. En una caminata real eso aplaza el pin indefinidamente, y
con un stream hambriento lo pierde. El ticket anterior no pudo ver nada de esto porque no había nada
que ver.

## Dos trampas esquivadas

1. **No tocar `PendingConfirm`.** La idea natural era llevarle un contador de fixes. Pero el
   watchdog compara `pendingConfirm === pending` **por identidad** y el flujo va con
   `distinctUntilChanged()`: mutarlo en cada fix **cancelaría y reiniciaría el watchdog en cada
   fix**. Habría sido un cambio de conducta real colado dentro de un ticket de telemetría. El
   contador se descartó; la potencia discriminante está en la ACCIÓN, no en la cuenta.
2. **El `DROPPED_BY_USER` no se podía emitir donde ocurre.** `onUserStoppedDetection()` no es
   `suspend` y el coordinator no tiene scope propio; añadirle uno se sale del remit. Se recuerda el
   hold caído en un campo y lo emite el **epílogo**, que sí es `suspend` y corre microsegundos
   después. Con su cinturón: `reset()` limpia el campo, porque el epílogo de una sesión *superseded*
   se salta la emisión a propósito y si no la nota sobreviviría a la sesión siguiente.

## Cambio de superficie de traza — declarado

⚠️ `HOLD_STALE_DISCARDED` **deja de emitirse como `Decision`** y pasa al carril tipado
(`type=HOLD`, `action=DISCARDED_STALE`). Es la única forma de que su hermana muda sea comparable con
ella — que una hablase y la otra no era justo lo que las hacía indistinguibles. Coste asumido: una
query que buscara ese `outcome` en eventos `DECISION` deja de encontrarlo. Es superficie de
diagnóstico, tras opt-in, con retención corta.

## Doctrina

- *El evento NOMINA, solo el movimiento MEDIDO confirma* — intacta, cero decisiones tocadas.
- *Fallo asimétrico* — intacta. `STARVED` y `SESSION_ENDED` siguen plantando el pin exactamente
  igual que antes; lo único nuevo es que ahora **lo dicen**.
- Carriles separados — el BT no entra (sigue en `04 §2.12`).

## Barrido de consumidores

| Consumidor | Estado |
|---|---|
| `when` de `toDto()`/`typeName()` | **cerrado** — el `when` exhaustivo lo exige |
| `HOLD_STALE_DISCARDED` en `CoordinatorParkingDetectorTest:3310` | **cerrado** — migrado al carril tipado |
| `accumulate()` del logger | **exento con razón** — `Hold` cae en su `else`, no toca rollups |
| `pendingConfirm` (identidad + `distinctUntilChanged`) | **cerrado** — deliberadamente NO se toca (trampa 1) |
| `reset()` / epílogo superseded | **cerrado** — el campo del drop se limpia en ambos |
| Ramas mudas del servicio y workers (`04 §2.7-2.15`) | **abierto, declarado** — siguiente entrega |

## Tests

- `StagePrecedenceCharacterizationTest` +1 — **el par que no se podía escribir**. ✅ discriminante:
  1 → 5 `OPENED` al neutralizar.
- `HoldLaneGuardrailTest` (2, Konsist) — sin acción muerta, una sola puerta. ✅ discriminante:
  sustituyendo la emisión de `STARVED` se pone rojo nombrándola.
- `DetectionEventDtoTest` +2 — paridad de wire; ninguna salida serializa `action` nulo.
- Retirado un `println("PROBE …")` de depuración que se coló en `3b49bde5` (regla del proyecto).

**1.454 tests**, 0 fallos (eran 1.449). `assembleMockDebug` ✅. Sin pantalla, estado ni routing
nuevos → Dev Catalog intacto. Sin strings.

## Lo que queda de la propuesta 3

1. `PROMPT_ANSWERED` y el resultado del **backfill** (`04 §2.11`, `§2.15`).
2. Telemetría **al extender** la ventana de atasco, no solo al plegar (`09 §14.4-bis`).
3. El recorte de `LOCATION_FIX` bajo flag de replay — lo que paga el coste de todo lo demás.
   ⚠️ El flag debe quedar **ACTIVO** en los móviles de field-test o perdemos la materia prima.
4. La estrategia **BT sin sesión** (`04 §2.12`) y los receivers/sensores (`§2.13`).
