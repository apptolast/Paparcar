# ROUTE-GAP-HONEST-001 · Un agujero de datos GPS nunca se puentea en silencio: tramo inferido marcado + pregunta

**Estado:** ✅ Done · master `a244d975` (ff-only, 15-08-2026) · rama y worktree borrados · ⏳ field

## Problema
Field 14-08: el Redmi tuvo un agujero GPS de 7 min / 4,6 km en la vuelta a Valdelagrana y
`TrailMapMatcher` lo rellenó con el camino de carretera MÁS CORTO (corredor N-IV) — una ruta de
kilómetros nunca conducida, indistinguible de la medida (pin `878e167b`, 590 pts para 177 fixes).
El mismo día, un arm tardío dejó un chord recto de 2 km (Redmi 19:08) y el pin backfill de las
22:51 llevó una "ruta" stub de 40 m con el punto A pegado al B.

## Doctrina violada
*Solo el movimiento MEDIDO se dibuja* + *ante la duda se PREGUNTA*. El Viterbi solo limitaba el
detour (`3×recta+120 m`) sin tope de paso medido: un hueco enorme autorizaba un puente inventado.

## Diseño (decidido con el user 15-08, tras revisar el estado del arte)
- Google Maps Timeline dibuja chord recto sobre huecos (y sus usuarios lo odian igual); OSRM/Valhalla
  parten la traza en el hueco (`gaps=split`); las apps de kilometraje (Driversnote/MileIQ/TripLog)
  reconstruyen la ruta más probable con un motor de rutas y la presentan como estimación editable.
- Paparcar combina ambos: **el matcher ROMPE en el hueco** (paso medido > `MAX_MEASURED_STEP_METERS`),
  el hueco se **reconstruye por carretera** (línea siempre sobre vía — requisito del user) pero
  **marcado como tramo INFERIDO en el dato**, y al abrir la ruta en pantalla se **pregunta**
  "¿pasaste por aquí?". Sí → confirmada; No → los tramos inferidos dejan de dibujarse (corte).
  Huecos > `GAP_BRIDGE_CEILING_METERS` o sin camino plausible → corte duro (nunca chord kilométrico).
- El pin backfill sin ruta medida entra por el MISMO mecanismo: ruta pin-anterior → pin-nuevo
  totalmente inferida y pendiente de confirmar. El stub de 40 m muere con un gate de extensión
  mínima en `ConfirmParkingUseCase.encodeFreshRoute`.

## Piezas
1. `TrailMapMatcher.match()` → `MatchedRoute(points, inferredSpans, cuts)`; `snap()` delega.
2. `domain/matching/InferredRoute.kt`: codec del string de spans ("a:b" inferido · "a!" corte) +
   `splitRouteSegments()` puro para render. Enum `RouteInferenceResolution` en domain/model.
3. Persistencia end-to-end: `routeInferredSpans` + `routeInferredResolution` en Room (migración
   17→18), DTO Firestore, mapper (4 direcciones), DAO, repo (+`resolveInferredRoute`), 2 fakes.
4. `EnrichParkingSessionWorker`: persiste spans; pin backfill sin ruta → inferencia pin→pin.
5. UI `ParkingLocationScreen`: segmentos (medido sólido · inferido α0.45 hasta confirmar ·
   rechazado = corte) + banner pregunta con Sí/No → intent → repo. Strings ×9 locales.
6. `PaparcarMapView`: param `tripSegments` (gana a tripTrail cuando no vacío).

## Criterio de éxito
- ✅ Test matcher: hueco (7 min silencio) entre dos tramos medidos → puente por carretera MARCADO
  inferido con el span abriendo/cerrando en los anclas medidos.
- ✅ Test matcher: paso disperso de autovía (400 m / 15 s) NO es hueco (criterio temporal
  `MAX_MEASURED_SILENCE_MS` — sin él, la CA-32 del Oppo se habría marcado inferida entera).
- ✅ Test matcher: hueco sin camino plausible / > techo (8 km) → CUT, sin chord ni invención.
- ✅ Tests codec/split de `InferredRoute` (round-trip, rechazo, cortes, tokens malformados).
- ✅ Test `ConfirmParkingUseCase`: ruta < 150 m → no se adjunta (mata el stub del backfill).
- ✅ Paridad del deserializer manual de Firestore (FirestoreDeserializerParityTest) con los 2
  campos nuevos.
- 1161 tests · `compileProdDebug` + `compileMockDebug` ✔ (15-08).
- ⏳ Campo: la vuelta del Litoral se dibuja con el tramo del hueco atenuado + pregunta.

## Consumidores auditados
- `TrailMapMatcher.snap` ← worker (ahora `match()`, persiste spans) + `HomeTripController` en vivo
  (sigue con `snap()`: en vivo el puente inferido se dibuja sin distinción, aceptado V1 — el dato
  persistido, que es el que se relee, sí va marcado). ✔
- `updateParkingSessionRoute` ← worker (2 call sites: snap + acceptRawRouteAsFinal). ✔ barridos.
- Render de rutas guardadas ← solo `ParkingLocationScreen` (grep `routePolyline` en presentation). ✔
- Galería mock: `ParkingLocationScreen` no está en `StateGalleryScreen` (pantalla-mapa con VM, sin
  `*Content(state)` split) → sin variante de galería; sin escenario nuevo de routing.
