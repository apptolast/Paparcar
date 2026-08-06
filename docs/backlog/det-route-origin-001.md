# DET-ROUTE-ORIGIN-001 — Origen del viaje retrodatado al último parking (la ruta nace en la plaza, no en el primer fix)

**Estado:** ✅ EN MASTER `c415e36b` (merge squash lineal 2026-08-05, rama y worktree borrados);
⏳ device/field-test (los APKs de campo instalados son anteriores a este master — regenerar).
MÁS SIMPLE que la spec:
el canal ya existía — el service ya resuelve `TripContext(session.location, vehicleId)` en
GEOFENCE_EXIT / AR ENTER / sentry-wake y llega como `Monitoring.departurePoint`; solo
`HomeTripController` lo ignoraba (`departurePoint = trail.firstOrNull()`). Cambios: seed del trail
en trip nuevo + techo `MAX_BACKDATED_ORIGIN_METERS = 5 km` + seed una sola vez por trip (supersede
no reescribe) + log del gap ancla→primer-fix (métrica de despertar-tarde; evento Firestore
diferido) + polyline 14→20 px (petición user). CERO cambios en lado detección → el test de replay
de no-contaminación de la spec es innecesario por construcción; el test viejo
`should_ignore_the_service_departure_point…` [DRIVE-PUCK-NATIVE-001] se invierte con conocimiento
de causa (4 tests nuevos). Suite prod verde + mock compila.
**v2 map-matcher (mismo día, petición user "ruta real por las calles"):** `TrailMapMatcher` era
per-point (el tramo retrodatado quedaba como cuerda recta cruzando manzanas) → v2 rellena huecos
>60 m ruteando A* sobre el grafo de ways de Overpass (nodos compartidos = intersecciones), acepta
solo si camino ≤3× distancia recta, fallback honesto a cuerda si grafo desconectado/implausible.
Pure commonMain + 3 tests (esquina en L, desconectado, sin-relleno-denso).
**Origen:** Pieza 2/2bis del plan Driversnote (`project_det_driversnote_learnings_plan` en memoria).
Observación de campo del user (30-07): Driversnote, SIN Bluetooth configurado, muestra el viaje
completo punto-a-punto aunque despierte tarde. Verificado (docs Transistor): NO despiertan antes
que nosotros — **retrodatan el origen del viaje al ancla estacionaria guardada** y rellenan el
hueco con map-matching. Como su GPS está apagado mientras el coche está aparcado, esa ancla ES la
plaza; despertar tarde no deja cicatriz visible.

---

## El gap (confirmado en nuestro código, 2026-07-30)

Cuando el coordinator despierta a mitad de trayecto (AR/geocerca con latencia, proceso muerto,
Doze), la ruta y el punto de salida nacen **donde aparecen los fixes**, no donde estaba el coche:

- `presentation/home/HomeTripController.kt:223` — `val depart = newTrail.firstOrNull()`: el
  marcador de salida (`departurePoint`) es el **primer fix vivo** del trail.
- `presentation/home/MapTrail.kt` — el polyline se acumula solo con fixes medidos de la sesión.
- Resultado: viajes que visualmente empiezan a 500 m – 4 km de la plaza real, y un marcador de
  salida plantado en mitad de una avenida.

La fuente de verdad del origen **ya existe y es mejor que la de Driversnote**: la sesión aparcada
de la que se está saliendo es un parking CONFIRMADO con pin anclado
(`domain/model/UserParking.kt:25` — `location: GpsPoint`; lectura vía
`UserParkingRepositoryImpl.getActiveSessionByVehicle/observeActiveSessions`, Room offline-first).
Ellos retrodatan a un "still" cualquiera; nosotros retrodataríamos a una plaza verificada.

---

## La decisión

**Cuando una sesión de detección arranca existiendo una sesión aparcada del vehículo (escenario
salida), el viaje se siembra con el punto del parking como origen.** El polyline nace en la plaza,
`departurePoint` = la plaza, y el hueco ancla→primer-fix-vivo lo resuelve el map-matcher que ya
tenemos (ROUTE-SNAP-001, Overpass) ajustándolo a calles — exactamente el efecto Driversnote.

**Guardas de doctrina (esto es lo crítico del ticket):** el punto sembrado es ENSAMBLAJE DE
PRESENTACIÓN, no evidencia. Prohibido que entre en:

- `EvaluateParkingDecisionUseCase` / conducción medida / egress cinemático — la distancia
  ancla→primer-fix NO es movimiento medido y no puntúa NADA.
- El stream de fixes (`GetOneLocationUseCase` / `ObserveAdaptiveLocationUseCase`) — la inyección
  va en la capa de viaje/presentación (`HomeTripController` o el estado del trip), nunca aguas
  arriba donde beben los evaluadores.
- `TripTrailImpl` (androidMain) — el ring-buffer forense sigue conteniendo SOLO fixes reales del
  teléfono.

El pin de la plaza nueva, el freed-spot y toda la confirmación quedan intactos. Esto arregla
"cómo se cuenta el viaje", no "cuándo se cree que hubo viaje".

---

## Diseño por pieza

1. **Semilla del origen.** Al construir el `TripUpdate` inicial de una salida
   (`HomeTripController`), si existe sesión aparcada del vehículo en curso de salida: prepend
   `UserParking.location` al trail (marcada como sintética, p.ej. `isBackdatedOrigin` o lista
   separada `originSeed`) y `departurePoint` = esa posición SIEMPRE que exista (hoy
   `firstOrNull()` del trail medido pasa a ser fallback para el primer viaje sin sesión previa).
2. **Selección de la sesión.** "La de salida" = la sesión activa del vehículo resuelto por la
   detección en curso (misma resolución que usa el depart/freed-spot). Multi-vehículo: si no hay
   sesión del vehículo en curso, NO sembrar (comportamiento actual).
3. **Sanity del ancla.** Si la distancia ancla→primer-fix supera un techo de plausibilidad
   (constante, p.ej. `MAX_BACKDATE_DISTANCE_METERS` ~5 km — sesión stale/inconsistente), no
   sembrar y loggear. Mejor ruta corta que ruta inventada (fallo asimétrico aplicado al visual).
4. **Map-matching del hueco.** Sin trabajo extra: el seed entra en `trailForMatching` y
   ROUTE-SNAP-001 lo ajusta a calles. Si el matcher no corre (iOS/sin roads), el tramo queda
   recto — aceptable y honesto.
5. **Telemetría.** Evento de diagnóstico con `originBackdated: Boolean` + distancia
   ancla→primer-fix. Esa distancia es LA métrica de cuánto tarde despertamos — hoy no la medimos
   y es oro para los field-tests de FN (complementa `feedback_detection_trigger_provenance`).

## Riesgos

1. **Contaminación de evidencia** si el seed se cuela aguas arriba → mitigado por diseño (pieza 1:
   inyección solo en capa trip/presentación) + test que verifique que los evaluadores nunca ven el
   punto sintético.
2. **Ancla stale** (sesión vieja no liberada) → techo de plausibilidad (pieza 3).
3. **Re-park corto** (salir y aparcar a 100 m): el seed apenas cambia nada — el matcher une dos
   puntos cercanos. Sin riesgo.

## Validación

- Test unitario `HomeTripController`: con sesión aparcada + primer fix a 2 km → trail nace en el
  parking, `departurePoint` = parking; sin sesión → comportamiento actual; ancla >techo → sin seed.
- Replay harness: sesión de despertar tardío (fixes empezando lejos) → verificar que la decisión
  de parking es BIT-IDÉNTICA con y sin seed (prueba de no-contaminación).
- Device: field-test normal — la ruta del viaje debe nacer en la plaza anterior.

## Ficheros previstos

- `presentation/home/HomeTripController.kt` (seed + departurePoint; hoy `firstOrNull()` en :223).
- `presentation/home/MapTrail.kt` (si el seed necesita esquivar la decimación `MIN_POINT_DISTANCE_M`).
- Acceso a sesión aparcada desde el controller (repo ya inyectable; use case si hace falta).
- Tests: `HomeTripControllerTest` + replay de no-contaminación.
- `docs/detection/PARKING-DETECTION.md` (changelog, misma tarea).

## Relacionados

- `project_det_driversnote_learnings_plan` (pieza 2/2bis) · `reference_driversnote_detection_stack`.
- Sinergia con DET-EXACT-HEARTBEAT-001: la red de polling acota el retraso del despertar a ~5 min
  y este ticket hace que ese retraso no se vea. Juntos = "viaje completo sin BT".
- Sinergia con DET-RESIDENT-FGS-001 (SENTRY): menos despertares tardíos; este ticket cubre los que
  queden.
- No confundir con DET-GAP-ANCHOR-001 (ancla del parking NUEVO tras hueco GPS); esto es el origen
  del viaje en el parking VIEJO.
