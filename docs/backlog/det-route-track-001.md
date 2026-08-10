# DET-ROUTE-TRACK-001 — La ruta dibujada es el trayecto REAL grabado, no una reconstrucción

**Estado:** ✅ código en rama `feature/ROUTE-LINE-ONROAD-001-route-stays-on-road`. ⏳ tests + device/field.

## Idea (propuesta del usuario, 2026-08-08)
En vez de reconstruir la ruta desde la última plaza cada vez que se abre la app (una conjetura que el
matcher rellena con el camino más corto), **guardar los puntos GPS reales del viaje** en un objeto de
ruta durable. Ya capturamos los fixes al armar y trackear — basta con persistirlos.

## Hallazgo del diagnóstico
Hoy NO persistíamos los puntos densos:
- **`TripTrailImpl`** (prefs `trip_trail`) persiste, pero es **esporádico** (solo fixes one-shot:
  chequeos, pre-arm, red de seguridad; tope 60/12h). Forense, no sirve para dibujar.
- **Trail denso del puck** (`HomeTripController.current.trail` / `MapTrail`, ≤500 pts): son los puntos
  reales, pero **solo en memoria** y vienen de `observeUiLocation()` atado a la pantalla → **solo se
  acumulan en primer plano**. Se pierden al reiniciar; no se capturan en background.

Los densos los tiene el **servicio de detección** (lo único vivo en background), vía
`ObserveAdaptiveLocationUseCase` (`Flow<GpsPoint>`), pero no se guardaban como ruta.

## Diseño
Store durable de la ruta del viaje en curso, grabado por el servicio, restaurado por el controller.
- **`DrivingRouteStore`** (interfaz commonMain) + **`DrivingRoute`** (regla pura de acumulación:
  decimar <12 m, cap 500 pts, auto-corte por hueco temporal >20 min = viaje nuevo) +
  **`DrivingRouteStoreImpl`** (androidMain, SharedPreferences `driving_route`, espejo de `TripTrailImpl`).
- **Servicio**: `observeAdaptiveLocation().onEach { drivingRouteStore.append(it) }` — mismo stream que
  bebe el coordinator, **coste de batería cero**. `clear()` al terminar el viaje (rama
  `detectionJob === thisJob` del finally: confirm/abort limpian; supersede NO — la continuación del
  mismo viaje conserva la ruta).
- **`HomeTripController`**: en trail vacío (arranque en frío / primer fix), la base = **origen de la
  plaza (antepuesto)** + **ruta real restaurada** del store. Si el store está vacío (iOS / primer
  viaje) → solo el origen sembrado (fallback = causa B DET-ROUTE-ORIGIN-002). El matcher v5 snapea el
  hueco plaza→primer-fix-grabado a las calles.

## Buffer único (no por-vehículo)
Un solo detectionJob activo (supersede) → un solo viaje trackeado → un buffer. El auto-corte por hueco
temporal evita que un viaje nuevo herede la cola del anterior; el `clear()` explícito al terminar es
la vía principal, el hueco es la red de seguridad para la muerte de proceso que se saltó el clear.

## Relación con causa B (DET-ROUTE-ORIGIN-002)
La ruta persistente **supera** a causa B como mecanismo principal: da el trayecto real, no el más
corto. Causa B queda como **fallback** del ORIGEN (la plaza) cuando no hay ruta grabada o para el
tramo plaza→primer-fix.

## Ficheros
- `domain/detection/DrivingRouteStore.kt` (interfaz) · `domain/detection/DrivingRoute.kt` (pura).
- `androidMain/detection/DrivingRouteStoreImpl.kt` · `di/AndroidDetectionModule.kt` (binding).
- `detection/service/CoordinatorDetectionService.kt` (inyecta, envuelve el flow, clear al terminar).
- `presentation/home/HomeTripController.kt` (restaura) · `di/PresentationModule.kt` (getOrNull).
- Tests: `DrivingRouteTest` (pura), `FakeDrivingRouteStore`, `HomeTripControllerTest` (+2 restore).

## Pendiente
- iOS: sin store (null) → cae al fallback de origen. Impl iOS futura.
- Actualizar `docs/detection/PARKING-DETECTION.md` al cerrar.
