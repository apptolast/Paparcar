# DET-A-WALK-REPORTING-ZERO-IS-STILL-A-WALK-001 · la caminata que el GPS declara parada no la mide nadie

**Estado:** 🧊 **APARCADO 2026-09-01 — NO es una tarea.** Los dos números están medidos y **no hay
nada que implementar** (ver §«Corrección» abajo: la «versión débil» no existe). Se revisa **cuando
saquemos diagnósticos**, con la lista de comprobación del final. Decisión del user: no sobrecargar la
cola con esto.
**Origen:** hallazgo medido de `DET-A-DISOWNED-ANCHOR-TAKES-ITS-WALK-WITH-IT-001` (31-08), que fue a
cerrar el «bug #6» del egress birth y encontró que el bug era otro.

## Problema

El carril cinemático es el par del proof de pasos: existe para el hardware cuyo contador se queda
mudo. Pero **sólo cuenta fixes que el propio GPS declara en movimiento**.

`updateStopTracking` parte por `location.speed < stoppedSpeedThresholdMps` (**1 m/s**). El contador
`kinematicEgressFixes` se incrementa **únicamente en la rama MOVING**, y ahí exige además
`frozenByRest`, `speed < minimumTripSpeedMps` y accuracy creíble.

Una persona anda a ~1,4 m/s, así que en la calle con cielo abierto funciona. Pero el campo `speed`
de un fix es Doppler: **en interior, en un garaje, con receptor frío o con proveedor de red, llega
0.0 mientras la persona camina**. Esos fixes caen del lado PARADO, donde:

- no incrementan `kinematicEgressFixes` → `hasKinematicEgressSignal` nunca se cumple → **el camino de
  confirmación cinemático no existe para esa sesión**;
- no abren `egressBirth` → `judgeEgressBirth` responde `NOT_RECORDED` → desde
  `DET-NOTHING-TO-JUDGE-IS-NOT-NO-DOUBT-001`, el único camino que le queda (AR vehicle-exit +
  ventana) **degrada a pregunta**.

O sea: el usuario con podómetro mudo **y** GPS que no reporta velocidad se queda sin los dos caminos
que no exigen pasos, y su aparcamiento acaba en pregunta. Con **P3**, en nada.

## Lo que ya está medido, y lo que NO

✅ Medido (`AnchorTrustTest`, `DET-A-DISOWNED-ANCHOR-TAKES-ITS-WALK-WITH-IT-001`):
- la rama MOVING **sí** abre birth con testigo cinemático (la bandera ya es `true` ahí);
- aceptar el testigo cinemático en la rama STOPPED **no es el arreglo**: fabrica una birth *en el
  ancla* a partir de un contador viejo, que `judgeEgressBirth` leería como `BORN_AT_ANCHOR`.

🟢 **MEDIDO el 2026-09-01 — el ticket queda DESBLOQUEADO, y el número es mucho mayor de lo que
sugería el enunciado.**

Método (sin decidir yo qué es una caminata): un fix está «en caminata» si el `parkdiag` registró
eventos de paso **dentro de los 15 s anteriores Y posteriores**. El podómetro es el testigo
independiente; el `speed` del GPS es lo que está en juicio. Barrido sobre **8 sesiones de parkdiag**
distintas (Oppo, Redmi y un tercer device), **6.403 fixes en caminata**:

| sesión | fixes en caminata | `< 1,0 m/s` | `< 0,5 m/s` | mediana declarada |
|---|---|---|---|---|
| oppo | 999 | 71,6 % | 49,8 % | 0,51 |
| oppo | 644 | 70,3 % | 48,6 % | 0,55 |
| oppo | 330 | 77,0 % | 53,0 % | 0,45 |
| redmi | 1 370 | 56,6 % | 42,5 % | 0,79 |
| redmi | 1 331 | 70,2 % | 56,3 % | 0,34 |
| redmi | 1 002 | 56,2 % | 42,2 % | 0,82 |
| redmi | 307 | 57,7 % | 40,4 % | 0,83 |
| 23117RA68G | 420 | 76,0 % | 64,8 % | 0,21 |
| **TOTAL** | **6 403** | **65,5 %** | **~48 %** | **0,21–0,83** |

**Dos de cada tres fixes tomados mientras el usuario anda demostrablemente declaran por debajo del
umbral de parado.** No es un caso raro de interior o garaje: es el caso NORMAL, en todos los devices
y todos los días del corpus.

⛔⛔ **Y el mismo número REFUTA el arreglo obvio.** Bajar `stoppedSpeedThresholdMps` no sirve: con el
**48 %** de los fixes de caminata por debajo de 0,5 m/s, no queda margen entre «anda» y «está quieto»
en el eje de la velocidad declarada — el campo no lleva señal a esas magnitudes. El separador tiene
que ser **posición contra tiempo**, que es exactamente el movimiento que ya hicieron
`DET-STOP-MUST-BE-STILL-IN-SPACE-001` y `DET-A-HOLE-THE-SPEED-FIELD-DENIES-IS-STILL-A-HOLE-001`.

### 🟢 El SEGUNDO número, medido el 2026-09-01 — y **refuta el arreglo por pares**

Mismas dos poblaciones, definidas SIEMPRE por el podómetro (el testigo independiente), nunca por el
`speed`. `WALK` = pasos a ambos lados dentro de 15 s. `REST` = ningún paso en ±120 s, **y** ninguna
marca de conducción del propio log en ±120 s.

**(a) Entre fixes CONSECUTIVOS — no separa, y el test de envolvente está INVERTIDO:**

| | metros movidos p50 | p90 | p99 | **supera `acc₁+acc₂`** |
|---|---|---|---|---|
| WALK (n=8 092) | 2,2 | 9,3 | 69,5 | **4,2 %** |
| REST (n=9 037) | 0,3 | 5,1 | 176,7 | **6,8 %** |

⛔⛔ **Un móvil QUIETO supera la envolvente conjunta MÁS a menudo que uno andando** (6,8 % contra
4,2 %), y su p99 es **más grande** (177 m contra 70 m). Es multipath: el salto es grande justo cuando
la accuracy es mala. **El predicado que usa todo el código (`d > acc₁+acc₂+margen`) no vale aquí.**

**(b) Por VENTANA de 30 s, desplazamiento NETO (primer fix → último) — aquí sí hay señal:**

| | p10 | p50 | p90 | p99 |
|---|---|---|---|---|
| WALK (n=5 534) | 3,0 | **18,6** | 43,1 | 159,2 |
| REST ±120 s (n=8 053) | 0,1 | **1,3** | 17,0 | 259,8 |
| REST ±300 s (n=4 479) | 0,3 | **1,2** | 6,3 | 99,8 |

Mediana **14× mayor** andando. Punto de operación según qué reposo haya que sobrevivir:

| umbral neto / 30 s | caminatas conservadas | **reposos admitidos** (±120 s) | (±300 s) |
|---|---|---|---|
| 10 m | 67,5 % | **12,5 %** | 5,9 % |
| 20 m | 47,5 % | **9,4 %** | 3,6 % |
| 30 m | 29,5 % | **7,4 %** | 2,6 % |
| 50 m | 5,9 % | **5,2 %** | 1,9 % |

⚠️ **La ventana de 60 s es PEOR que la de 30**, no mejor: la cola del reposo explota (p90 149 m,
p99 1 108 m). Más tiempo no es más señal.

⛔ **Y la columna que manda es la de ±120 s, no la de ±300 s.** El reposo que este carril tiene que
distinguir empieza **segundos** después de una caminata —aparcas, andas al portal, te quedas en el
recibidor—, así que la población «móvil asentado cinco minutos» es la optimista y no la relevante.
**1 de cada 8 ventanas en reposo pasaría por caminata a 10 m**, y a 50 m las dos curvas se cruzan.

## ⛔ Veredicto: el desplazamiento SOLO no puede licenciar el carril cinemático

El carril cinemático **CONFIRMA**. Admitir el 12,5 % de las ventanas en reposo es plantar pines en un
móvil sobre una mesa, que es exactamente el riesgo que este doc ya anticipaba. Y no hay umbral que lo
salve: por debajo de 20 m admite demasiado reposo, por encima de 40 m ya no conserva caminatas, y a
50 m las curvas se cruzan.

**Lo que el número SÍ deja abierto** (y es la única dirección que queda viva): separar *«puede ABRIR
una birth de egress»* de *«puede CONFIRMAR»*. Abrir una birth sólo **registra la duda** — su efecto
es que la sesión pregunte en vez de plantar. Para eso un 12,5 % de falsos positivos cuesta preguntas,
no pines fantasma, y la asimetría de la doctrina lo tolera. Confirmar necesitaría un segundo testigo
independiente, y el único que hay es el podómetro — que es justo el que falta en este caso.

📌 **Decisión pendiente, con los dos números ya delante**: o se implementa esa versión débil (birth
sí, confirm no), o se cierra el ticket aceptando que **el móvil con podómetro mudo en interior es
ASK-only por física**, y eso se escribe donde se pueda leer en vez de quedar como hueco.

## Dirección de diseño — ⛔ la que este doc proponía está REFUTADA por su propia medición

Lo que decía aquí, y que la medición (b) desmonta: *«una caminata debería poder medirse por
desplazamiento entre fixes CONSECUTIVOS con accuracy creíble»*. No puede: entre fixes consecutivos un
móvil quieto supera la envolvente conjunta **más** a menudo que uno andando (6,8 % contra 4,2 %). La
premisa era que la envolvente de accuracy filtraría el multipath, y hace lo contrario, porque el
multipath **hincha la accuracy y el salto a la vez**.

Lo que sí sobrevive del razonamiento original es el marco: `DET-STOP-MUST-BE-STILL-IN-SPACE-001` dice
*«una parada es una afirmación sobre POSICIÓN»*, y el espejo sigue valiendo — el `speed` declarado no
puede desmentir movimiento. Sólo que la magnitud que lleva la señal **no es el salto entre dos fixes,
es el desplazamiento NETO de una ventana de 30 s**, y ni siquiera ésa alcanza para confirmar.

### ⛔ Corrección: la «versión débil» NO EXISTE, y este doc la proponía

Se llegó a escribir aquí que se podía *«abrir la birth pero no alimentar `kinematicEgressFixes`»*,
para que la sesión pasara de perder el aparcamiento a preguntar. **Es falso, y al revés.** Verificado
en `EvaluateParkingDecisionUseCase:428-429`:

```kotlin
input.egressBirth == EgressBirthJudgement.BORN_AWAY    -> PromptReason.EGRESS_NOT_AT_ANCHOR
input.egressBirth == EgressBirthJudgement.NOT_RECORDED -> PromptReason.EGRESS_NOT_WITNESSED
```

Sin birth (`NOT_RECORDED`) **hoy ya se PREGUNTA**. Con birth abierta en el ancla (`BORN_AT_ANCHOR`)
no se añade motivo de pregunta → **pin silencioso**. Es decir: **abrir la birth ES lo que quita la
pregunta.** La supuesta versión débil convertiría una pregunta en un pin puesto solo, sobre una señal
que se equivoca el **9,4 %** de las veces con el móvil quieto. Plantaría pines en salones.

### Y con eso, el «hueco» de este ticket es mucho más pequeño de lo que decía arriba

Un móvil con podómetro mudo **no pierde el aparcamiento: se le PREGUNTA**, que es exactamente lo que
la doctrina pide ante la duda. El enunciado original (*«su aparcamiento acaba en pregunta. Con P3, en
nada»*) sigue siendo correcto — pero la primera mitad no es un defecto, es el comportamiento deseado.

**Sólo un cambio futuro lo revive**: el día que una pregunta sin contestar deje de guardar nada
(«P3»). Hasta entonces no hay nada roto que arreglar.

## 🔬 Qué mirar CUANDO SAQUEMOS DIAGNÓSTICOS (lo único pendiente)

No hay código que escribir. Hay que contar, sobre sesiones reales, si esto le pasa a alguien:

1. `StepCounter: cumulative steps read → 0 (mute counter → treated as unknown)` — podómetro mudo;
2. fixes de la caminata de egress declarando **< 1 m/s**;
3. `judgeEgressBirth` → **`NOT_RECORDED`**;
4. desenlace **pregunta** (`EGRESS_NOT_WITNESSED`) — nunca un pin mal puesto.

Y entonces la pregunta que decide: **¿esas preguntas se contestan?**

- Si son ~0 sesiones → **cerrar refutado**: el usuario con podómetro mudo en interior es ASK-only por
  física, y se escribe en el KDoc del carril cinemático para que nadie lo «arregle».
- Si son muchas **y** las preguntas se quedan sin contestar → hay un problema real, pero **no es
  éste**: es el de la pregunta que no llega o no se ve, y se abre con esos datos.

⛔ **Lo que NO hay que hacer en ningún caso**: usar el desplazamiento para abrir birth. Está medido
arriba y planta pines.

⚠️ Contexto del user (01-09): sólo hay **2 móviles de test**, así que la ausencia de casos en campo
no prueba que otros hardware no lo sufran — pero como el comportamiento actual ya es el seguro
(preguntar), esperar no cuesta un pin.
