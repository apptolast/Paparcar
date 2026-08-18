# ROUTE-MANUAL-PIN-INFERRED-001 · Un pin avalado por el usuario sin ruta medida recibe SIEMPRE la reconstrucción pin-a-pin

**Estado:** 🔵 En progreso · rama `feature/ROUTE-MANUAL-PIN-INFERRED-001-manual-pin-inferred-route` · worktree `../Paparcar-manual-pin-route`

## Problema

Field 17/18-08 (Chema → Calle Balsa): el Oppo perdió la detección (EXIT entregado 11 h tarde) y el
user colocó un pin **manual** en Calle Balsa 8. Ese pin acabó con una ruta pin-a-pin reconstruida
por calles (221 pts, `routeInferredSpans=0:218`, pregunta "¿pasaste por aquí?" pendiente) que el
user validó — "la ruta lo ha clavado" — y quiere conservar: *"aunque pongamos el pin manual, sí que
preguntaremos la ruta, lo que tenemos ahora"*.

Pero esa ruta salió **de rebote**, no por diseño. La vía deliberada (`inferBackfillRoute`) está
gateada a `detectionPath == "safety_net_backfill"`; al pin manual la ruta le llegó porque el
centinela casualmente tenía fixes frescos alrededor del coche y la semilla de origen
([ROUTE-QUALITY-001], pin anterior a 4.489 m < techo 5.000 m) se añade ANTES del check
`MIN_ROUTE_EXTENT_METERS`, colando una "ruta" de 2 tramos que el worker luego reconstruyó como un
agujero gigante. Si ese día el centinela no hubiera despertado justo antes → pin manual sin ruta.
El mismo día, el pin `nudge` del Redmi (17-08 19:43) quedó sin ruta por la misma puerta cerrada.

## Doctrina violada

Ninguna regla dura — es un comportamiento querido que hoy depende del azar. Lo que sí aplica:
**sistemas, no parches**: el invariante debe vivir en el gate del worker, no en la carambola
semilla+agujero de `encodeFreshRoute`.

## Señales / datos disponibles

- `detectionPath` persiste en cada pin ([DET-PIN-PROVENANCE-001]) — el gate ya lee exactamente eso.
- `inferBackfillRoute` ya trae TODOS los guards necesarios: pin anterior real
  (`getPreviousSession`), cota inferior (hop > `MAX_MEASURED_STEP_METERS` — un hop trivial no
  dibuja) y techo (`GAP_BRIDGE_CEILING_METERS` — un salto absurdo no se inventa), fetch OSM con
  retry, resultado SIEMPRE `inferred` completo + `routeInferredResolution=null` (pregunta
  pendiente; REJECTED lo borra para siempre). [ROUTE-GAP-HONEST-001]

## Diseño

El invariante: **un pin sin ruta medida cuyo emplazamiento es fiable — ground truth del usuario
(fiabilidad 1.0) o reconciliación del safety net — recibe la reconstrucción pin-a-pin por calles,
marcada inferida y con la pregunta pendiente.**

Un solo sitio: el gate de `EnrichParkingSessionWorker.inferBackfillRoute` pasa de un path único a
el conjunto `{"safety_net_backfill", "manual", "user", "nudge"}`. Cero clases nuevas, cero casos de
uso nuevos (es un predicado de un solo consumidor — [DET-VERDICT-NOT-PREDICATE-001]). Se renombra
`inferBackfillRoute` → `inferPinToPinRoute` (ya no es solo backfill) y se actualizan los kdoc + el
comentario espejo de `ConfirmParkingUseCase.encodeFreshRoute`.

Por qué cada path del conjunto:
- `safety_net_backfill` — el caso fundacional (sin cambio).
- `manual` — el de este field: pin colocado a mano tras una detección perdida.
- `user` — "Sí, he aparcado" en el prompt; si su sesión midió conducción ya trae ruta real y este
  código ni se ejecuta (`routePolyline != null` → snap normal); solo actúa si quedó sin ruta.
- `nudge` — pin colocado respondiendo al nudge; mismo escenario que `manual` (la detección abortó).

## Criterio de éxito

- Un pin `manual` creado con el store de rutas vacío (sin fixes frescos) acaba, tras el worker, con
  `routePolyline` pin-a-pin, `routeInferredSpans` cubriendo toda la línea y la pregunta pendiente —
  siempre que exista pin anterior dentro de las cotas; sin pin anterior o hop trivial/absurdo, sin
  ruta (comportamiento actual intacto).
- En campo: el próximo pin manual tras una detección perdida enseña la ruta atenuada + pregunta,
  sin depender de que el centinela despertara antes.

## Consumidores auditados

`grep safety_net_backfill` + `grep inferBackfillRoute` sobre el worktree:

- `EnrichParkingSessionWorker` — el único gate; es el fix. ✅
- `ParkingBackfillWorker.PATH_SAFETY_NET_BACKFILL` — escribe el path del pin backfill; no lee el
  gate. Exento. ✅
- `ConfirmParkingUseCase.encodeFreshRoute` — comentario espejo ("the post-park worker may still
  reconstruct pin-to-pin") actualizado. La carambola semilla+agujero se DEJA como está: con el
  gate abierto, el caso "store fresco + semilla" produce la misma ruta por `snapRoute`/`match`
  (anclada además a los fixes reales del final) y el caso "store vacío" cae en la vía nueva. ✅
- Paths deliberadamente EXENTOS del conjunto:
  - `bt` — hueco conocido aparte (los viajes BT deberían tener ruta MEDIDA; inventarla taparía ese
    hueco). Sin ticket aún.
  - `unattended_timeout` / `closed_approximate_pin` / zonas (`zoneRadiusMeters != null`) — pin de
    fiabilidad 0.5 colocado a ciegas; una ruta inferida hacia él apila suposición sobre suposición.
  - Paths con conducción medida (`steps+egress`, `kinematic+egress`, `vehicle-exit`) — siempre
    llevan ruta real; si no la llevan es porque el viaje fue trivial (extent < 150 m) y el hop
    tampoco pasaría la cota inferior.
- iOS: sin worker de enriquecimiento aún (`IosParkingEnrichmentScheduler` es stub). Exento. ✅

## Notas de test

El gate vive en un `CoroutineWorker` androidMain con Koin (sin arnés de test; no lo tenía tampoco
para el caso backfill). La lógica con riesgo real — matcher, spans, cotas — ya está cubierta en
`TrailMapMatcherTest` + `InferredRouteTest` (commonTest). El cambio es pertenencia a un conjunto;
verificación = suite completa verde + compilación prod y mock + validación en campo del criterio.
