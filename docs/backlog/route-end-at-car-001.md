# ROUTE-END-AT-CAR-001 — la ruta del aparcamiento termina en el coche, no en el peatón

**Estado:** implementado en rama `bugfix/ROUTE-END-AT-CAR-001-trim-walk-tail` · ⏳ field-test
(el recorte actúa al CONFIRMAR: las rutas ya guardadas conservan su cola — solo los viajes nuevos
salen recortados)
**Origen:** field 2026-08-13 17:33, Calle Mar de Alborán 2 (El Puerto), Coordinator. Caso espejo:
Ebro 2026-08-11 21:43, donde el ancla quedó ATRÁS del fin de la polyline (la línea sobrepasaba el
coche).

## Defectos observados

1. **La línea seguía más allá del coche**: pin correcto, pero la routePolyline continuaba por la
   caminata del usuario tras aparcar, hasta donde estaba al confirmar. Causa: el
   `DrivingRouteStore` sigue recibiendo fixes mientras el GPS está vivo durante el egress (pasos +
   ventana de hold) y `ConfirmParkingUseCase.encodeFreshRoute` codificaba el buffer entero; el
   snap del worker (`TrailMapMatcher`) matchea lo que le den — la cola peatonal entraba al matcheo.
2. **Corte final abrupto**: la línea moría en un vértice seco, sin remate visual (el origen sí
   tiene su punto desde ROUTE-QUALITY-001).

## Invariante

**La routePolyline es la RUTA DE CONDUCCIÓN: termina en el ancla del aparcamiento.** Los fixes
posteriores al final de conducción medido son la caminata, no el viaje, y no pertenecen a la línea.

## Cambios

### Pieza 1 — datos (`DrivingRoute.endAtAnchor` + `ConfirmParkingUseCase`)

- `DrivingRoute.endAtAnchor(points, anchor)` (commonMain, puro): descarta los fixes con timestamp
  posterior al del ancla y añade el ancla como vértice final si la línea restante se queda corta
  (ventana de plausibilidad 15 m–5 km, `MIN/MAX_ANCHOR_APPEND_METERS`, espejo del prepend de
  origen de ROUTE-QUALITY-001). Un ancla sin timestamp real (>0) no recorta nada — el append solo
  sigue rematando la línea en el pin.
- `ConfirmParkingUseCase.encodeFreshRoute(nowMs, origin, anchor)`: aplica el recorte ANTES de
  codificar (y por tanto antes del snap del worker). El `anchor` es el `location` que el caller
  pasa al confirm — para el Coordinator es `bestStopLocation`/el pin refinado, cuyo timestamp de
  fix ES el final de conducción medido (la parada en la que se capturó/congeló el ancla,
  ANCHOR-LOCK/DET-ANCHOR-FREEZE). El gate de frescura (30 min) se evalúa sobre el último fix
  CRUDO del store (refleja cuándo dejó de trackear el servicio), el recorte después.
- El prepend de origen y el matcher NO se tocan: el recorte es solo de la cola.

### Pieza 2 — UI (remate visual)

- `PaparcarMapView`: nuevo parámetro `arrivalPoint` (State, default vacío) + marker `MARKER_ARRIVAL`
  que reutiliza `DepartureDotMarker` — mismo lenguaje visual (anillo blanco, relleno drive-blue,
  centrado en la coordenada, mismo zIndex/anchor que el origen) en ambos extremos de la línea.
- `ParkingLocationScreen` (HISTORY-DETAIL): pasa `routeEnd` = último vértice de la ruta decodificada.
  Home NO pinta routePolyline guardadas (solo el trail vivo, cuyo final es el puck) → sin cambios allí.

## Decisiones

- **Señal de fin de conducción = timestamp del fix del ancla** (la señal que ya existe: el pin que
  cada path pasa al confirm conserva el timestamp del fix con el que se capturó). Se descartó
  recortar por "último fix a velocidad de conducción": habría comido la aproximación lenta final
  (maniobra de aparcar a <5 m/s) y tocaba geometría validada de ROUTE-QUALITY-001.
- **Límite conocido**: un pin puesto por el usuario (manual/nudge/"Sí") lleva timestamp = ahora →
  no se recorta la cola (no hay señal de freeze en ese path); el append del ancla al menos remata
  la línea en el pin. Si el caso Ebro reaparece en pins de nudge, haría falta propagar el timestamp
  del último fix de conducción desde el detector (ticket futuro).
- El vértice final añadido es el PIN crudo; el matcher lo snapea a la vía dentro de sus radios
  normales (o lo descarta si está a >120 m de toda vía, comportamiento deliberado documentado en
  ROUTE-QUALITY-001 — no se amplió el radio del último punto).

## Tests

- `DrivingRouteTest`: 4 nuevos (cola peatonal excluida + ancla como vértice final; ruta sin cola
  intacta; techo de plausibilidad; ancla sin timestamp).
- `ConfirmParkingUseCaseTest`: 3 nuevos (recorte end-to-end en el confirm; ruta sin cola intacta;
  no-regresión del origen con recorte). Los 6 tests previos de ruta/origen pasan sin cambios.

## Pendiente

- [ ] Commit + merge a master (squash) con go-ahead.
- [ ] Field-test: viaje nuevo → la línea debe (a) morir en el coche aunque se camine tras aparcar,
      (b) rematar con el circulito en el último vértice, (c) conservar el vértice/prepend de origen.
