# IOS-ROUTE-A-SNAP-THE-DRAINER-NEVER-DOES-001 · El enrichment iOS geocodifica pero nunca snapea la ruta

**Estado:** ⚪ Pendiente · sin rama
**Origen:** hallazgo del barrido de `ios-a-parity-tail-001.md` (fila 3) — el hueco profundo detrás
del binding del store.

## Problema
Con [IOS-A-PARITY-TAIL-001], las sesiones iOS ya llevan `routePolyline` cruda (store + feeder +
`encodeFreshRoute` común). Pero el carril de enrichment iOS (`OutboxOp.EnrichSession` →
`SyncOutboxDrainer`) hace SOLO geocoding, mientras el gemelo Android (`EnrichParkingSessionWorker`)
además: (a) **snapea** la ruta grabada contra OSM (`TrailMapMatcher` + `RoadNetworkDataSource`,
ambos ya comunes y ya bindeados en iOS), y (b) **infiere pin-a-pin** (`inferPinToPinRoute`) cuando
una colocación fiable no trae polyline — gated por `PIN_TO_PIN_ELIGIBLE_PATHS` (⛔ `"bt"` excluido
A PROPÓSITO). Ambas mejoras son `private` del worker androidMain: iOS no puede llamarlas.

## Diseño
Sistemas, no parches: extraer la lógica de `snapRoute()` + `inferPinToPinRoute()` del worker a una
pieza común (función pura de nivel superior en `domain/`, o evaluador si su resultado entra en el
vocabulario de diagnóstico — `routeSnapped` ya se estampa), consumida por:
- el worker Android (sin cambio de comportamiento, mismos gates), y
- un paso nuevo del drain de `EnrichSession` en `SyncOutboxDrainer`.
Los umbrales (`MAX_MEASURED_STEP_METERS`, `GAP_BRIDGE_CEILING_METERS`) viajan con la pieza, no se
duplican.

## Criterio de éxito
Una sesión confirmada en iOS acaba con la ruta snapeada a calle igual que en Android; el replay de
las 16 trazas no cambia; los tests del worker siguen verdes sin tocar sus asserts.
