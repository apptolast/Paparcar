# ROUTE-QUALITY-001 — Ruta almacenada: origen real, sin desvíos inventados, vértice de inicio, header del detalle

**Estado:** ✅ EN MASTER (`2809e4c0`, + el header del detalle en `25afb888`) · campo cubierto por la
validación hasta `1a4128d5` (23-08-2026). El snap es una sola vez: las rutas guardadas ANTES
conservan su geometría vieja — solo los viajes NUEVOS muestran los fixes.

> Continuado en `12b1969a` [ROUTE-START-AT-CAR-001]: la ruta debe ARRANCAR en el pin del que salió
> el coche, esté liberado o no.
**Origen:** field 2026-08-10, Redmi (Coordinator/C5): capturas del user sobre la ruta del pin 20:39
(Calle Góndola 25) y del 19:51 (Calle Estopa 9).

## Defectos observados

1. **La ruta no empezaba donde salió el coche**: la línea arrancaba en la A-491, ~500 m después del
   aparcamiento real (Calle Estopa). Causa: el store solo guarda fixes posteriores al armado; el
   primer fix llegó ya a ~240 m con precisión 50–70 m (el matcher lo descarta) — parkdiag Redmi
   20:24:44 `loc#1 acc=68m`.
2. **Desvío inventado en tramo recto** (CA-603 junto al CEIP Marqués de Santa Cruz): la línea se
   salía a un vial paralelo. Causa: `highway=service` entraba al grafo sin tags ni peso de clase —
   un loop de colegio conectado en ambos extremos tiene longitud rutada casi idéntica (transición
   ≈ 0) y la emisión aplanada (σ=10) decidía por ruido.
3. **La línea empezaba "cortada"**, sin marcador de origen (Home sí dibuja su punto de salida).
4. **Header "APARCAMIENTO HISTÓRICO/ACTUAL"** del sheet: no estaba centrado (el título iba pegado
   al chevron izquierdo en `PapSectionHeaderRow`) y con demasiado peso visual.

## Cambios

- `ConfirmParkingUseCase`: `encodeFreshRoute(origin=...)` — prepend de la ubicación del parking
  activo previo del vehículo, con ventana de plausibilidad `MIN/MAX_ORIGIN_PREPEND_METERS`
  (15 m–5 km, espejo del techo de Home). 3 tests nuevos.
- `OverpassRoadNetworkDataSourceImpl`: `out tags geom` + `RoadWay.isMinor = (highway == service)`.
- `TrailMapMatcher`: `MINOR_WAY_EMISSION_PENALTY = 4.5` (≈ hándicap de 30 m) en la emisión de
  candidatos minor; candidatos ordenados por coste penalizado (no distancia cruda); edge compartido
  con vía mayor se "upgradea" (sin tax en tramos dual-mapped). 3 tests nuevos (paralela no roba /
  aisle genuino sí matchea / upgrade del edge compartido).
- `ParkingLocationScreen`: pasa `departurePoint` = primer punto de la ruta → reusa el
  `DepartureDotMarker` de Home; header con `centerTitle=true` + color `outline`.
- `PapSectionHeaderRow`: param `centerTitle` (default false — resto de callers intactos).
- `docs/detection/PARKING-DETECTION.md`: sección ROUTE-QUALITY-001.

## Decisión revertida durante la tarea

Amplié el radio de snap del ÚLTIMO punto a 300 m (el KDoc decía "endpoints") pero rompía el test
`should keep a far point unchanged`, que documenta comportamiento deliberado: un trail entero lejos
de toda carretera (rural/OSM incompleto) se devuelve crudo, no se arrastra a una vía a 220 m.
Revertido; el KDoc de `ORIGIN_SNAP_METERS` ahora explica por qué es solo-origen.

## Pendiente

- [ ] Commit + merge a master (squash) con go-ahead.
- [ ] Field-test: viaje nuevo con el Redmi → la ruta debe (a) empezar en el parking anterior con
      vértice, (b) sin desvíos en rectas, (c) header centrado y discreto.
- [ ] NO cubierto: viajes BT-owned siguen sin ruta (gap de diseño, ver det-bt-wrong-car-abort-001.md).
