# DET-TWO-DISPATCHES-OF-ONE-DEPARTURE-READ-DIFFERENT-STATE-001

**Estado:** ✅ **FASE 2 hecha (2026-09-03)** — el sello de adjudicación existe y lo consulta la
puerta 4 (el safety net), que es de donde salieron los DOS incidentes medidos. Las 3 preguntas
abiertas están contestadas abajo. ⏳ Campo: la línea `⊷ observation ADHERES`.
Fase 1 (censo + definición): 01-09-2026, sin código por diseño.

> ⛔ **Este ticket se llamaba `DET-A-DEPARTURE-DISPATCHES-ONCE-PER-FENCE-001` y ese nombre estaba
> mal.** Prejuzgaba la solución —deduplicar— y la deduplicación es precisamente lo que los datos
> desaconsejan. Renombrado para describir el HECHO, no el arreglo.

## El hecho, medido

Field 30-08 21:27 (Oppo). La MISMA salida del MISMO geofence (`785dabe3`) se despachó **dos veces**,
con **596 ms** de diferencia, por dos puertas distintas — una la disparó entrar en una geocerca, otra
el AR:

```
21:27:33.967  SafetyNet ▶ dispatching departure (…)                    ← puerta 1: [geofence-enter]
21:27:33.968            → chaining parking backfill … arrivalWalk=12 steps      ← PLANTA el pin
21:27:34.563  SafetyNet ▶ dispatching departure (…)                    ← puerta 2: [ar-enter]
21:27:34.566            ⊘ arrival NOT placed … arrivalWalk=0 steps     ← VETA
```

## ⛔ Por qué deduplicar NO es la respuesta (objeción del user, 31-08)

> *«No veo por qué omitir uno si se encola otro; el que omitimos podría ser el bueno.»*

**Y es exactamente lo que pasó.** El primer despacho leyó `arrivalWalk=12` y **plantó el pin
equivocado**; el segundo leyó `arrivalWalk=0` y **vetó, que era lo correcto**. Un dedupe *"quédate
con el primero"* habría conservado justo el malo. *"Quédate con el último"* sería igual de arbitrario:
elegiría por orden de llegada, que es el defecto, no el criterio.

## Lo que el dato revela de verdad

Las dos evaluaciones **no son independientes**: el estado que leen es **mutable y se consume**. El
primer despacho gastó el presupuesto de pasos, y por eso el segundo vio `0`. O sea, el segundo no
acertó por tener mejor información — acertó **como efecto colateral de que el primero ya había
gastado la suya**.

Así que el problema no es *"se despacha dos veces"*. Es:

> **Un mismo hecho se juzga dos veces contra un estado que la primera evaluación ya ha alterado, y
> el veredicto depende del orden de llegada.**

Cualquier arreglo tiene que responder antes a: **¿qué es «el hecho»?** ¿La salida de esa geocerca?
¿Cada entrega del OS? Y **¿cómo se evalúa contra un estado estable**, en lugar de contra uno que la
propia evaluación consume?

## Daño confirmado hoy: ninguno activo

- 🟢 **El pin fantasma ya no ocurre**: `DET-BACKFILL-MUST-NOT-PIN-A-MOVING-CAR-001` (`29a9b0a5`) cede
  la llegada a la detección viva, así que esa vía ya no planta.
- 🔎 **Buscado y NO encontrado**: en el trazado del 30-08 el worker de salida corrió dos veces para
  `785dabe3` (21:27:34.092 y .876) y **ningún efecto se duplicó** — ni dos publicaciones de plaza ni
  dos cierres. Ambas pasadas eran `preconfirmed`, así que solo procesaron.
- 🟡 **Riesgo plausible, sin observar**: la cadena se encola con `beginUniqueWork(…, REPLACE)`, así
  que un segundo despacho **reemplaza** la del primero. En un despacho NO preconfirmado —los que
  reintentan a 15/30/60 s midiendo velocidad— reiniciaría los intentos desde cero y retrasaría
  liberar la plaza. Mecanismo claro, caso no medido.

## Antes de implementar nada, CONFIRMAR

1. ¿Ocurre el doble despacho en despachos **no** preconfirmados? (los del trazado eran los dos
   preconfirmados, que es el caso benigno).
2. ¿Se llega a reiniciar de verdad la cadena de reintentos, y cuánto retrasa liberar la plaza?
3. ¿Hay algún efecto no idempotente aguas abajo que dos pasadas puedan duplicar?

Sin al menos una de las tres respondida con datos, esto es deuda estructural sin daño demostrado —
y hay trabajo con daño medido por delante (p. ej. la latencia de los EXIT de geocerca, que saltó
8 veces en un solo día).


---

## FASE 1 (01-09-2026) · Censo de puertas y de estado consumible

> Hecho desde `chore/…-census`, solo lectura de código + los `parkdiag` archivados
> (`paparcar-fieldlogs/2026-09-01/`). Cero cambios de código, por diseño de la fase.

### Qué es «el hecho» (definición propuesta)

**Una salida = (valla rota, instante del primer trigger que la despachó).** Las N entregas del OS —
EXIT en frontera, EXIT far-delivered, AR ENTER, y cualquier wake del safety net que la deduzca —
son OBSERVACIONES del mismo hecho, no hechos nuevos. Hoy cada observación abre su propia
adjudicación contra el estado que la anterior dejó.

### Las puertas que despachan una salida (todas al mismo unique work `DepartureDetectionWorker_<geofenceId>`, todas con `REPLACE`)

| # | Puerta | Sitio |
|---|---|---|
| 1 | EXIT en frontera (paso 3a) | `CoordinatorDetectionService` ~797 |
| 2 | EXIT far-delivered (paso 3b, carril stale) | `CoordinatorDetectionService` ~830 |
| 3 | AR `ArmMidTrip` (re-run del speed-gated check) | `CoordinatorDetectionService` ~1143 |
| 4 | Safety net «far with vehicle evidence» (preconfirmado o no) — alcanzable desde SUS SEIS fuentes de wake (`periodic`/`exact-alarm`/`geofence-enter`/`ar-enter`/`detection-end`/`app-start`); el doble del 30-08 fueron dos wakes de ésta a 596 ms | `ParkingSafetyNetWorker` ~463 |
| 5 | Watchdog «me he ido» — no pasa por el worker: procesa DIRECTO (`processConfirmedDeparture`), puerta paralela de proceso | `CoordinatorDetectionService.handleWatchdogDeparture` |

### El estado consumible que las adjudicaciones leen — y quién lo consume

| Estado | Lo consume | Lo lee | Mordisco medido |
|---|---|---|---|
| **Witness slot** (`KEY_LAST_WITNESSED_*`, prefs) | CADA pasada del safety net lo RESELLA (~241-248) | `EvaluateSafetyNetCheckUseCase` (arrivalWalk), honest close (`readLastWitnessedFix`), `witness_ride` | **30-08 21:27**: pasada 1 reselló → pasada 2 leyó `arrivalWalk=0` y vetó — acertó como efecto colateral |
| **`DepartureEventBus.lastVehicleEnteredAt`** | `reset()` en 4 sitios (`ProcessConfirmedDeparture:210`, `ConfirmParking:344`, `UpdateParkingLocation:84`, revert vía doc) | `DetectParkingDeparture:132`, `VerifyDepartureEvidence`, evaluador del safety net, telemetría | un 1º despacho Processed borra el boarding que un 2º despacho del MISMO hecho necesitaría |
| **La fila de sesión** (`isActive`) | el clear del 1º despacho | TODO lo demás | **31-08 21:22**: honest close leyó sesión viva y dijo «mantén»; el carril de salida la borró 6 s después. Y el AR de las 21:28 leyó `NoSession` |
| **Step-anchor seals** (`ANCHOR_KEY_PREFIX`) | resellados por el safety net («resello la referencia») | honest close (budget), evaluador | (mismo mecanismo que el witness slot) |
| **La cadena WorkManager** (unique + `REPLACE`, en las 4 puertas worker) | un 2º despacho REEMPLAZA la del 1º | — | mecanismo confirmado en código; reinicio de attempts (15/30/60 s) sin medir aún |
| **Arrival-resolution seal** (`c5bfd274`) | el coordinator lo escribe, el backfill difiere | backfill | ya parcheado para sus 8 razones — parche previo de ESTA misma familia |
| `detectionRuntime.isRunning` | — (carrera de lectura) | gate del follower (`89266cfd`) | reconocida y re-chequeada en el arm site — el patrón «snapshot + re-check» ya existe en miniatura |

### Las 3 preguntas del doc, revisitadas con los datos del 31-08

1. ¿Doble despacho en NO preconfirmados? — **Sigue sin medirse** (el 31-08 el EXIT despachó
   no-preconfirmado y no hubo segunda entrega en su ventana). Estructuralmente posible: 4 puertas
   worker + 1 de proceso comparten el hecho.
2. ¿`REPLACE` reinicia la cadena? — Mecanismo confirmado en las 4 puertas; caso sin medir.
3. ¿Efecto no idempotente aguas abajo? — **SÍ, DOS medidos**: el resello del witness (30-08, cambió
   el veredicto de la 2ª pasada) y el `reset()` del bus + clear de sesión (31-08, honest close
   contra carril de salida, veredictos opuestos sobre el mismo hecho a 6 s). **La condición del
   doc («al menos una respondida con datos») está cumplida.**

### Dirección de diseño para la FASE 2 (propuesta, NO implementada)

**Snapshot de adjudicación por hecho**: el primer despacho de una salida captura sus insumos
(witness + steps baseline + boarding + exitAt) bajo la identidad del hecho; las observaciones
posteriores del mismo hecho SE ADHIEREN — leen el snapshot, no el estado vivo — mientras la
adjudicación siga abierta. El resello del witness pasa a ocurrir al CERRAR la adjudicación, no por
pasada. Es el mismo patrón que el repo ya usa en miniatura (`DriveProof.onFix` recibe valores
presentados «para que una segunda llamada no coincida por suerte») y el que `c5bfd274` aplicó a la
mitad llegada del problema.

Preguntas abiertas para la fase 2: (a) ¿`KEEP` en vez de `REPLACE` pierde el upgrade a
`preconfirmed` de un despacho posterior mejor informado?; (b) ¿dónde vive el snapshot —
prefs junto a los seals, o workData del primer despacho?; (c) ¿el watchdog (puerta 5, sin worker)
se adhiere o su palabra-de-user lo exime?

---

## FASE 2 (2026-09-03) · El sello de adjudicación

**El invariante, en una frase: un hecho, una adjudicación.** El primer despacho de una salida SELLA
el hecho; una observación posterior del MISMO hecho **se adhiere** —no re-adjudica y no vuelve a
despachar— salvo que traiga prueba **estrictamente mayor**.

La regla vive pura en `domain/detection/DepartureAdjudication.kt` (`adjudicateDeparture`, patrón
`HumanPoweredRide`/`ExplainedDeparture` — predicado, no caso de uso [DET-VERDICT-NOT-PREDICATE-001])
y el sello se persiste en las mismas prefs por-valla que los anchors.

### Por qué esto NO es el dedupe que el user descartó

Su objeción sigue en pie y es la que da forma al arreglo: *«el que omitimos podría ser el bueno»*.
La respuesta no es elegir mejor, es que **el segundo veredicto nunca fue evidencia independiente**:
la primera pasada reselló el witness, así que la segunda midió un presupuesto de pasos que la
primera acababa de gastar. Adherirse no es «me quedo con el primero» — es **negarse a re-derivar un
veredicto a partir de estado ya comido**. El pin equivocado de aquel día lo cerró
`DET-BACKFILL-MUST-NOT-PIN-A-MOVING-CAR-001` (`29a9b0a5`); lo que faltaba aquí era que el veredicto
**dejara de depender del orden de llegada**.

### Las 3 preguntas abiertas, contestadas

| # | Pregunta | Respuesta |
|---|---|---|
| a | ¿`KEEP` en vez de `REPLACE` pierde el upgrade a `preconfirmed`? | **Sí, y por eso `REPLACE` se queda.** `KEEP` decidiría en WorkManager, que es demasiado tarde y demasiado tonto: dejaría caer en silencio justo la observación mejor informada. El sello decide ANTES del enqueue — una observación que se adhiere no llega nunca a WorkManager (así que tampoco reinicia la cadena, que era el riesgo de la pregunta b de la fase 1), y un `Upgrade` sí reemplaza a propósito, porque tiene que meter `preconfirmed` en la request. |
| b | ¿Dónde vive el snapshot — prefs o `workData`? | **Prefs, y `workData` está descartado por construcción**: muere con la request que el siguiente `REPLACE` sobrescribe, o sea exactamente cuando una segunda observación necesitaría leerlo. Va al mismo fichero que los seals del anchor (`adjudication_<geofenceId>`), y **se cierra por el mismo barrido**: la salida adjudicada termina la sesión → `pruneStaleAnchors` no encuentra valla viva y borra el sello. |
| c | ¿El watchdog (puerta 5, sin worker) se adhiere? | **No: exento, y sin cierre explícito.** Su autoridad es la PALABRA DEL USUARIO, y la aserción supera a la inferencia [DET-ASSERTION-OUTRANKS-INFERENCE-001]; hacer que se adhiera a una deducción del safety net sería invertir esa jerarquía. No necesita cerrar nada: procesa directo, la sesión acaba y el barrido tira el sello en el tick siguiente. |

**Y una respuesta que el diseño de la fase 1 ya no necesita**: el doc proponía mover el resello del
witness al CIERRE de la adjudicación. No hace falta y no se ha tocado — una vez que no existe una
segunda adjudicación del mismo hecho, el resello no engaña a nadie, y para las OTRAS sesiones del
mismo tick (hechos distintos) el sello fresco es justamente la lectura correcta.

### Alcance: quién CONSULTA el sello, y por qué solo uno

- **Puerta 4 (`ParkingSafetyNetWorker`) — consulta y escribe.** Es de donde salieron los dos
  incidentes medidos (30-08 doble wake a 596 ms; 31-08 honest close vs carril de salida).
- **Puertas 1-3 (EXIT frontera / EXIT far-delivered / AR `ArmMidTrip`, en `CoordinatorDetectionService`)
  — NO consultan.** Es el carril VIVO, y la doctrina dice *todo trigger dispara SIEMPRE*: hacer que
  el tiempo real se calle porque el reconciliador ya opinó invierte la jerarquía. ⏳ Lo que sí queda
  como continuación natural (sin caso medido todavía) es que ESCRIBAN el sello sin leerlo, para que
  el safety net pueda adherirse a un despacho del carril vivo. No es re-implementar la regla: es
  añadir escritores al mismo sello, que ya tiene una sola casa.
- **Puerta 5 (watchdog)** — exenta, ver (c).

### Medido

- Suite **2.151/0** (8 tests nuevos de la regla pura). **Falsación por partida doble**: neutralizando
  `Adhere` caen 4/8; neutralizando `Upgrade` cae 1/8.
- ⚠️ **El cableado del worker no tiene test unitario, y se dice en vez de disimularse**: este repo no
  tiene harness para `ParkingSafetyNetWorker` (inyecta ubicación, repos, geocercas, podómetro, BT,
  bus y notificaciones) y montarlo era más ticket que el ticket. La DECISIÓN sí está cubierta, que es
  donde la doctrina la pone; las 20 líneas de cableado se verifican en campo con su línea de traza.
- ⏳ **Criterio de campo**: `⊷ observation ADHERES` en el `parkdiag` cuando dos wakes del safety net
  caigan sobre la misma valla, y **cero** `safety_net_dispatch` duplicados para un mismo `geofenceId`
  en la misma ventana.
