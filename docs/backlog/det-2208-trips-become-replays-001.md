# DET-2208-TRIPS-BECOME-REPLAYS-001 · los dos viajes del 22-08 pasan a ser trazas permanentes

**Estado:** ✅ Done (2026-08-24) · rama `refactor/DET-2208-TRIPS-BECOME-REPLAYS-001-traces` ·
worktree `../Paparcar-2208-traces`

Cierra **P0.3** del plan `docs/detection/10-plan-refactor.md` — la única tarea de la Fase 0 que
dependía de que los datos siguieran en los móviles. Seguían.

## Problema de partida

Había **11 trazas** y el subsistema llevaba ~15 commits nuevos desde la última. Los tres fixes del
22-08 (`b6906711`, `06fbc5e8`, `8bf6f02b`) se validaron con tests unitarios sintéticos: ninguno se
había ejecutado nunca contra el stream real que los provocó. Un fix que sólo se prueba contra el
escenario que uno mismo construye demuestra que uno entendió su propia hipótesis, no que arregló el
viaje.

## Origen de los datos

`parkdiag.log` **de los propios móviles**, no Firestore (la sesión del Oppo de esa noche nunca llegó
íntegra a remoto). Los dos logs seguían vivos y sin rotar, arrancando el 08-22 14:08.

La base de epoch se verificó **de forma cruzada entre los dos aparatos** antes de transcribir nada:
la entrada `invoke()` del Oppo a las 18:41:39.048 coincide al milisegundo con el id de sesión
`1787416899048`, y el `trueTime=1787402651536` que el receptor AR del Redmi estampó cae exactamente
en su 14:44:11.536 con el `lag=189ms` que el propio log declara.

| | evento | fixes | pasos | comprobación |
|---|---|---|---|---|
| `Trace_CameliasGondola001` | Oppo `CPH2371`, viaje 2, sesión `1787416899048` | 147 | 106 | el coordinator anotó `locationCount=147` |
| `Trace_GondolaCamelias001` | Redmi `2201117TY`, viaje 1 | 76 | 106 | el coordinator anotó `locationCount=76` |

Los conteos salen del parseo y **cuadran con el recuento que el propio detector escribió al salir**.
Índices `loc#1..N` sin huecos. La deriva entre la hora del log y el `sessionAge` que el detector
lleva por dentro es de 5 ms como mucho en el Oppo y 145 ms en el Redmi — irrelevante frente a unos
umbrales que se miden en segundos.

## Qué prueba cada traza, y cómo se comprobó que prueba algo

La regla de admisión del harness: *una traza que pasaría igual con el fix revertido no vale nada*.
Cada aserción se verificó **neutralizando el guard correspondiente** y comprobando que se pone roja.

### `camelias_gondola_001_a_stop_that_moved_122_m_must_not_pin_the_mouth_of_the_street`

Tres fixes de llegada declarando `speed=0.0` mientras la posición medía **122,5 m en 9,56 s** con
precisiones de 6–11 m. Hoy el pin cae en el reposo real del coche.

> Neutralizando `stillnessRefuted` → **rojo**, y con la coordenada exacta que se plantó en campo:
> `36.6086383,-6.2775383`. La traza no se limita a fallar: **reproduce el bug original clavado**.

### `gondola_camelias_001_the_egress_walk_must_not_be_judged_a_bicycle`

Esta lleva los tres fixes del día montados sobre un solo stream real, y lo que fija es **el orden en
que se resuelven ahora**:

1. La acusación de bicicleta desapareció — ni un `human_powered`.
   > Neutralizando `!isAnchorPinned(s)` en `cadenceStep` → **rojo**, reproduciendo la secuencia de
   > campo literal: `PEDAL_CADENCE_LATCHED` → `CONFIRM_DEGRADED_PROMPT/human_powered`.
2. **El confirm sigue degradando, y está bien que lo haga.** Hay un **hueco GPS real de 100,5 s**
   entre el último fix conduciendo (loc#39, 6,8 m/s) y el primero parado (loc#40): la app nunca vio
   dónde se detuvo el coche. Lo que cambió es que ahora lo dice con su nombre,
   `anchor_gap_entered` [DET-GAP-ANCHOR-ZONE-001], en vez de acusar a un ciclista inexistente.
3. El "sí" del usuario (Δ 584 231, el segundo exacto del toque en campo) completa el guardado, en el
   cúmulo de fixes parados — no donde estaba el peatón.
4. Y como esa parada se entró por el hueco, **se guarda como ÁREA, no como punto**.
   > Neutralizando `userZoneRadius` → **rojo**. Esta es la aserción que ejerce `8bf6f02b`.

## Un resultado negativo que se queda escrito en el test

La aserción de posición del punto 3 **no prueba `DET-CONFIRM-ANCHOR-001`**, aunque lo pareciera.
Forzando la rama del "sí" a coger siempre el fix actual en vez de la parada atestiguada, la traza
sale **byte a byte idéntica**: en este viaje el usuario contestó estando todavía junto al coche, así
que las dos ramas coinciden. Queda como guard posicional de regresión y con esa limitación escrita
en el propio test, apuntando a `supermarket_001`, que sí tiene un toque lejano.

Es el mismo criterio de `DET-CONFIRM-BRANCH-ORDER-MUST-BE-TESTABLE-001`: una aserción cuyo
comentario afirma más de lo que demuestra es un bug con forma de test verde.

## Hallazgo de paso

`8bf6f02b` **no mueve la coordenada** — deja el punto donde estaba y le cuelga un radio de duda. Su
efecto observable es `isApproximate`/`zoneRadiusMeters`, nunca la latitud. Cualquier test que
pretenda ejercerlo mirando dónde cae el pin está mirando otro guard.

## Doctrina

Ninguna violada; **una confirmada por medición**. *El evento NOMINA, solo el movimiento MEDIDO
confirma* — la traza del Oppo es el caso puro: una velocidad **declarada** de cero contra una
posición **medida** que se movía 46 km/h, y gana la medida.

## Estado final

- **Cero cambios en `commonMain`.** Sólo `commonTest`.
- 2 ficheros de traza nuevos + 2 tests en `DetectionTraceReplayTest`.
- **1.440 tests**, 0 fallos, 0 errores. 13 replays `Trace_*` (eran 11).
- Los `parkdiag.log` de los dos móviles quedan **sin tocar** en los aparatos.
