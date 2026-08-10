# ROUTE-LINE-ONROAD-001 — La línea de ruta va SIEMPRE por la carretera (v5)

**Estado:** ✅ código en rama `feature/ROUTE-LINE-ONROAD-001-route-stays-on-road`, tests verdes
(`TrailMapMatcherTest` + `HomeTripControllerTest` exit 0). ⏳ device/field-test.

## El problema (campo, 2026-08-08)
La línea de ruta se salía de la carretera. Decisión del usuario: **"me da igual que el GPS llegue
fuera, la ruta debe ir por la carretera"** → revertir la regla honest-raw de v4 a favor de
"siempre asfalto".

## Diagnóstico contra el mercado
Investigado cómo lo hacen Google Snap-to-Roads, Mapbox Map Matching, OSRM `match`, Valhalla/Meili,
GraphHopper, FMM. El error nº1 del sector ("dibujar cuerdas entre puntos snappeados", `pgeom` vs
`mgeom`) **ya lo teníamos resuelto en v4**: emitimos geometría de calle ruteada (los `nodeChain` de
Dijkstra entre candidatos). Las causas que quedaban eran tres:

1. **`MAX_SNAP_METERS = 60` demasiado corto** → un fix con multipath urbano (30-50 m NLOS) no
   encontraba calle candidata y caía al cubo off-road. El sector usa 100-200 m.
2. **Tramos off-road largos se dibujaban RAW** (`decode` v4) → *esto* sacaba la línea del asfalto.
3. **Origen retrodatado en un parking sin calle** se mantenía RAW → tramo recto plaza→calle.

## Cambios v5
- `MAX_SNAP_METERS` 60 → **120** m (candidatos por fix). σ=10 sigue penalizando la calle lejana, así
  que solo ayuda al caso "sin candidato", no roba fixes a la calle correcta.
- **`ORIGIN_SNAP_METERS` = 300 m solo para el origen** (índice 0): la plaza retrodatada retirada de la
  calle (parking, camino de entrada) snappea a la calle más cercana → la línea EMPIEZA en el asfalto.
- **Off-road nunca RAW**: los fixes sin calle candidata se **descartan** y la transición ruteada entre
  los candidatos on-road que lo rodean **puentea el hueco por las calles**. Eliminada la rama RAW y la
  constante `MAX_OUTLIER_RUN`.
- **Único tramo no-calle que queda**: la rotura honesta (sin ruta plausible entre dos candidatos
  on-road → cuerda recta, pero **entre dos puntos EN calle**, nunca hacia un pincho GPS). Si TODO el
  trail no tiene calle cerca (rural/sin datos OSM) → se devuelve el trail crudo (no hay calle que
  dibujar).

## Ficheros
- `domain/matching/TrailMapMatcher.kt` (constantes, `snap` con radio de origen + drop off-road,
  `decode` simplificado, `candidatesFor(p, maxSnapMeters)`).
- Tests: `TrailMapMatcherTest` (3 casos de honestidad reescritos a la doctrina v5).

## Pendiente / relacionado
- La ruta ruteada del hueco es una CONJETURA (camino más corto), no el trayecto real conducido. La
  tarea hermana **persistir los puntos GPS reales del viaje** (propuesta del usuario) hace que ese
  tramo sea el real en vez de una reconstrucción. Ver nota en `det-route-origin-002.md`.
- Actualizar `docs/detection/PARKING-DETECTION.md` (changelog) al cerrar.
