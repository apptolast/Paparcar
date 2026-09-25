# DET-THE-CADENCE-CORPUS-MUST-BE-EXECUTABLE-001 · un fixture generado que no reproduce su bug parece cobertura

**Estado:** ✅ Done · en master (squash; el hash vive en `MEMORY.md`, no aquí — el doc entra en el
mismo commit y no puede citarlo) · rama y worktree borrados

**Origen:** `DET-PEDAL-CADENCE-CANNOT-CONVICT-A-CAR-IN-TRAFFIC-001` está bloqueado por medición, y
mientras espera **no tenía ningún caso ejecutable**: su trayecto del 26-08
(`TRACE_GONDOLA_2608_CADENCE_VETO`) ya NO reproduce la pérdida, porque las dos refutaciones de
`DET-HUMAN-POWERED-VETO-MUST-BE-REVOCABLE-001` lo salvan (llegó a 94 km/h y llevaba su propio
embarque AR dentro del stream). El field del 25-09 es el caso que sí queda vivo: un coche que nunca
tocó 40 km/h y con cero AR.

## Problema

Dos capas, y la segunda se descubrió al ejecutar la primera.

**1 · El corpus del ticket bloqueado no era ejecutable.** Sin fixture, la única forma de comprobar
qué hace hoy el veto con un coche urbano era volver a conducir. El 25-09 quedó grabado en remoto;
faltaba pasarlo al corpus.

**2 · `trace2fixture` no leía los `CADENCE`, así que generaba un fixture MUDO sobre su propio bug.**
Una pedalada **no es** un evento `STEP` — los dos contadores son disjuntos. En la sesión
`1790330246055` hay **265 `STEP` y CERO en los 80 s de conducción**: el primero llega en Δ139 s, ya
aparcado. Las 25 pedaladas que vetaron el aparcamiento viven **sólo** como el total acumulado
(`stepCount`) dentro de los 8 eventos `CADENCE`.

El generador consumía LOCATION_FIX y STEP, de modo que producía este mismo trayecto con **574 filas
de aspecto impecable** y el replay **GUARDABA el aparcamiento**: no reproducía nada. Es exactamente
la forma de `DET-CADENCE-STEPS-ARE-INVISIBLE-TO-TELEMETRY-001` un piso más arriba — allí la
telemetría no grababa las entradas del veto; aquí ya las graba y **el generador no las lee**. Y es
también la razón por la que el fixture del 26-08 tuvo que reconstruir sus doce pasos A MANO: nadie se
lo enseñó a la herramienta, así que el siguiente que llegara volvería a tropezar.

## Doctrina violada

`TEST-AN-ORPHANED-FIELD-TRACE-STILL-LOOKS-LIKE-COVERAGE-001`, en su segunda forma. Aquella regla
caza la traza que **ningún test lee**; esto es la traza que un test SÍ lee y que **no puede
reproducir su propio incidente**. Las dos cuentan como cobertura en el recuento del corpus y ninguna
protege. Y el arreglo va donde manda *sistemas, no parches*: en el **generador**, no en el fichero
generado.

## Señales / datos disponibles

Export completo de la sesión (587 documentos) leído de `diagnostics/{uid}/sessions/{id}/events`:

| tipo | nº | qué aporta |
|---|---|---|
| `LOCATION_FIX` | 309 | lat/lon/acc/speed de todo el trayecto |
| `STEP` | 265 | pasos logueados, **ninguno** en la ventana de conducción |
| `CADENCE` | 8 | `stepCount` acumulado + **velocidad del fix juzgado** + `enterAgeMs` |
| `DECISION` | 3 | `PEDAL_CADENCE_LATCHED`, `CONFIRM_DEGRADED_PROMPT`, `UNATTENDED_HUMAN_POWERED_NUDGE` |
| `ACTIVITY_TRANSITION` | **0** | no hay embarque AR dentro del stream |

⛔ **El `parkdiag` local no se podía sacar**: el móvil corría un build de RELEASE, así que
`adb shell run-as` contesta `package not debuggable`, y el uid no tenía `diagnostics_reports`. Este
es **el primer fixture del corpus construido sin cable**, y sólo es posible porque
`DET-CADENCE-STEPS-ARE-INVISIBLE-TO-TELEMETRY-001` puso el `CADENCE` en el hilo remoto con la
velocidad de su fix.

## Diseño

- **`tools/trace2fixture/trace2fixture.py` aprende `CADENCE`.** Un evento por fix recién acreditado,
  con el total acumulado: las pedaladas desde el evento anterior son su **delta**, y se emiten **en
  el instante de ese evento**. La colocación es fiel, no cómoda: `cadenceQualifies` juzga cada
  pedalada contra el fix fresco más reciente, y el evento ES el momento en que ese fix se acreditó
  (`enterAgeMs` 84-1 150 ms) — así cada pedalada reconstruida se juzga contra **exactamente** el fix
  contra el que la juzgó el campo. Lo que no es recuperable es en qué punto del intervalo cayó cada
  una, y **nada del veto lee eso**.
- **`Trace_Fruteria2509CadenceVeto.kt`**: 309 fixes + 290 pasos (265 logueados + 25 reconstruidos),
  todo generado por la herramienta. El KDoc lleva la procedencia completa y dice qué es medido y qué
  derivado.
- **Dos casos en `DetectionTraceReplayTest`, que difieren en UNA línea**, y por eso el par está
  falsificado:
  - `fruteria_2509_a_city_car_under_40_kmh_is_convicted_and_the_park_is_lost` —
    ⚠️ **CHARACTERIZATION**: afirma el DEFECTO a propósito, porque su arreglo está bloqueado por
    medición. Cero guardados y `aborted_unattended_human_powered`, igual que el campo. **Cuando
    llegue la calibración este test se INVIERTE, no se borra** (y así está escrito en el ticket padre
    y en el propio test).
  - `fruteria_2509_the_boarding_the_arm_already_had_would_have_carried_it` — el mismo stream con el
    embarque que el arm YA tenía, enchufado: **guarda**, y el pin cae en la llegada.
- Suelo y prosa del guard de trazas puestos en la verdad medida (**20 ficheros / 25 streams**; decían
  18/21 y ya estaban desfasados antes de esta tarea), y su KDoc reconoce el export remoto como
  segunda procedencia legítima.

## Criterio de éxito

- [x] El replay reproduce el campo **1:1** sin el embarque: 0 guardados, `aborted_unattended_human_powered`.
- [x] El mismo stream **con** el embarque guarda y ancla en la llegada (≤ 60 m del último fix).
- [x] Las dos mitades difieren **sólo** en el stamp → el veto queda señalado como causa, no supuesto.
- [x] `trace2fixture` regenera el fixture desde el export sin nada escrito a mano.
- [x] `FieldTraceGuardrailTest` ve la traza nueva como leída, y su recuento deja de mentir.
- [x] `./gradlew testAll` en verde (exit 0), y `FieldTraceGuardrailTest` forzado con `--rerun-tasks`: 2 tests, 0 fallos.

## Consumidores auditados

`grep` de quién genera o lee trazas de campo:

| sitio | veredicto |
|---|---|
| `tools/trace2fixture/trace2fixture.py` | **cerrado** — es el arreglo |
| `Trace_Gondola2608CadenceVeto.kt` (12 pasos a mano) | **exento**: queda como está. Es un registro fechado, su reconstrucción está documentada, y regenerarlo desde remoto no se puede — su fuente fue el `parkdiag` de otro móvil |
| Los otros 18 `Trace_*.kt` | **cubiertos por convergencia**: ninguno depende de `CADENCE` — no existía cuando se grabaron |
| `FieldTraceGuardrailTest` + `GuardrailScope` | **cerrado** — recuento y procedencia al día |
| `DetectionTraceIngestion` | **exento**: consume `TraceEvent`, y la reconstrucción produce `Kind.STEP` normales; no hay tipo nuevo |

## Follow-up abierto

`docs/backlog/det-a-verified-boarding-must-reach-the-veto-001.md` — salió de aquí al preguntarle al
replay por qué no reproducía la pérdida, y es un arreglo disponible HOY, sin tocar ningún número de
los que bloquean al ticket padre.
