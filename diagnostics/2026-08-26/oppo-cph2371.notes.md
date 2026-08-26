# Oppo CPH2371 · captura del 2026-08-26 21:14

**uid** `fiypNbElGlfFexLMpU9sNaMjRMD3` · coche activo **Ford Focus** `addbe660` (sin BT → Coordinator).

## Qué cubre

Los dos ficheros son **contiguos, sin hueco** — juntos son 4 días y 7 horas de traza continua:

| Fichero | Desde | Hasta | Líneas |
|---|---|---|---|
| `oppo-cph2371.old.log` | 08-22 14:09:30.921 | 08-25 22:12:30.472 | 43.183 |
| `oppo-cph2371.log` | 08-25 22:12:30.491 | 08-26 21:12:39.024 | 6.650 |

`.old.log` es la **rotación única legacy**, de antes de
[DET-PARKDIAG-KEEP-MORE-HISTORY-001](../../docs/backlog/det-parkdiag-keep-more-history-001.md). Se
saca de aquí porque a partir de ese ticket ese nombre ya no se escribe nunca más: es la última copia
que existirá con ese formato.

## Por qué se guarda: contiene el FN del 25-08, que dábamos por perdido

`project_det_field_2026_08_25_oppo_fn` decía *"el `parkdiag` ya había rotado — esta vez el bueno fue
Firestore"*. **Era falso.** La traza estaba en el `.old.log`, en la línea 41.808. El incidente entero
se lee ahí, y con más detalle del que llegó a Firestore:

```
08-25 19:59:05.345 D PARKDIAG/Service: ▶ onStartCommand action=…ACTION_AR_TRANSITION startId=22
08-25 19:59:05.427 D PARKDIAG/Service:   ⤳ AR_TRANSITION 6214m from running anchor → superseding zombie session [DET-SUPERSEDE-001]
08-25 19:59:05.446 D PARKDIAG/OneFix: fix lat=36.6089869 lon=-6.2779194 speed=0.031433307m/s acc=3.804m age=1s
08-25 19:59:05.457 D PARKDIAG/Service:   → AR ENTER at own fence — arming Coordinator, waiting for ride proof (geof=a786c135 lag=149ms dep=enter_at_car)
08-25 19:59:05.472 D PARKDIAG/Service:     ✗ detection cancelled: StandaloneCoroutine was cancelled
08-25 19:59:05.475 D PARKDIAG/Service:     ■ finally → superseded by newer job; its completion callback skips the stop
08-25 19:59:05.490 D PARKDIAG/Service:   ▶ startParkingDetection — launching coordinator (trigger=AR_VEHICLE_ENTER)
```

El pin del Kamiq `a786c135` aparece **1.015 veces** en el `.old` y **326** en el activo: es la
vigilancia que se quedó pegada al coche equivocado durante días.

## Dos correcciones que esta captura obligó en los docs

Los tickets del FN se escribieron con datos que **no** salían de este log, y dos cifras estaban mal.
Corregidas en el mismo commit que sube esta captura:

1. **`6,3 km` → `6.214 m`.** La línea del device dice `6214m`. El `6314` que citaba
   `PARKING-DETECTION.md` no lo escribió nunca ningún móvil. Estaba replicado en 6 sitios
   (2 tickets, el log de fixes, el KDoc de `VehicleFenceOwnershipPolicy`, su test y un comentario del
   service).
2. **`ARM:AR_VEHICLE_ENTER (…)` citada como línea de `parkdiag`.** Esa etiqueta **existe**, pero es el
   label del evento **remoto** `SessionStarted` (`docs/detection/08-flujo-e2e.md:268`), no una línea
   del fichero del móvil. Lo que el Oppo escribió es
   `→ AR ENTER at own fence — arming Coordinator, waiting for ride proof (…)`. Sustituida por la real.

⛔ Las dos son el mismo error de método, y es el que el proyecto ya tiene escrito: **transcribir I/O
de memoria no es seguro — mover texto, no reescribirlo.** Un bloque que parece traza literal y no lo
es envenena el diagnóstico siguiente, porque quien lo lea buscará en el móvil una línea que no está.

## Cómo se sacó

Con el formato viejo (una sola rotación). A partir de ahora, el pull por defecto es el del paso 3 de
`diagnostics/README.md`, que concatena las 5 generaciones en orden cronológico.
