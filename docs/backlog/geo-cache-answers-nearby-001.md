# GEO-CACHE-ANSWERS-NEARBY-001 · Sin geocoder, la caché responde por vecindad — y la respuesta lleva su duda

**Estado:** ✅ Done (29-08-2026) · mergeada a master vía squash · ⏳ sin ver en device · ⚠️ guarda iOS pendiente del compañero

## Problema
Capa 2 de UI-LOCATION-FALLBACK-SPEAKS-HUMAN-001. Offline, `getAddressAndPlace` emite
`AddressInfo(null×4)` y la UI cae al fallback genérico ("Ubicación desconocida" en cámara). Pero la
mayoría de estos fallos ocurren donde el usuario ya ha estado: su Room `geocoder_cache` (celdas de
~11 m, TTL 30 días) suele tener la calle a unos metros. Hoy la caché solo responde al punto EXACTO.

## Doctrina aplicada
- *Fallo asimétrico* trasladado al geocoding: una respuesta aproximada vale para PINTAR («Cerca de
  Calle Mayor»), pero **jamás se persiste** como dirección real de una sesión ni se publica en una
  plaza — eso sería estampar una suposición como hecho.
- La aproximación se DECLARA en el copy («Cerca de …»), nunca se hace pasar por exacta.

## Señales / datos disponibles
- `RoomLocalAddressAndPlaceDataSource`: celdas selladas (única vía de escritura), clave
  `"${lat*1e4}_${lon*1e4}"`, TTL 30 días. Todas las filas cacheadas tienen fase 2 completada.
- El repo ya distingue el fallo de fase 1 (`fetchedAddress == null`) y ya garantiza que un fallo
  no escribe caché [GEOCODE-DEADLINE-001] — el enganche es limpio.

## Diseño
El invariante vive en el MODELO: `AddressAndPlace.approximate: Boolean = false`. Una respuesta
aproximada se distingue por tipo, no por convención de call site.

1. **Datos**: `LocalAddressAndPlaceDataSource.getNearest(lat, lon)` — candidatas con
   `addressStreet != null` dentro del TTL (query nueva del DAO, sin migración de schema: el parseo
   de la clave y el nearest-pick son una función pura en Kotlin, testeable), la más cercana dentro
   de `MAX_NEAREST_DISTANCE_METERS` (250 m), devuelta con `approximate = true`.
2. **Repo**: en `AddressAndPlaceRepositoryImpl`, si fase 1 falla → `local.getNearest(...)` y se
   emite esa respuesta en lugar de la dirección vacía. Nada aproximado se escribe en caché (el
   sello sigue exigiendo `fetchedAddress != null`). Fase 2 sigue igual; si un POI real llega, la
   emisión combinada conserva `approximate = true` (la dirección sigue siendo prestada).
3. **Guardas de persistencia** (una respuesta aproximada nunca se graba):
   - `EnrichParkingSessionWorker` (Android): emisión aproximada no marca `addressSaved` ni escribe
     → el retry con backoff busca la respuesta exacta.
   - `IosParkingEnrichmentScheduler`: ídem.
   - `ReportSpotReleasedUseCase`: choke point de TODA publicación de plaza — un `prefetched`
     aproximado se trata como no-prefetch, y el collect inline ignora emisiones aproximadas.
4. **Display**: los resolvers de cámara (`cameraTitleOrFallback` / `cameraTitleWhileSettling`) y el
   `addressLine` del `ConfirmationBottomSheet` envuelven la línea con
   `location_approximate_near` («Cerca de %1$s») cuando `approximate`. 9 locales.

## Criterio de éxito
- ✅ Test repo (4 nuevos): fase 1 falla + celda vecina → emite `approximate = true` y NO escribe
  caché; sin vecina → dirección vacía como hoy; fase 1 OK → no se consulta la vecina; POI real
  sobre dirección prestada → la emisión sigue `approximate`.
- ✅ Test nearest-pick (`RoomLocalAddressAndPlaceDataSourceTest`, 4): más cercana gana, radio de
  250 m, clave no parseable se ignora, lista vacía → null.
- ✅ Test `ReportSpotReleasedUseCase` (2): geocode inline aproximado no publica dirección; prefetch
  aproximado se ignora y se geocodifica inline.
- ✅ `testProdDebugUnitTest` + `compileProdDebugKotlinAndroid`/`compileMockDebugKotlinAndroid` verdes.
- Galería mock: variante "add parking, dirección aproximada (Cerca de …)" +
  `FakeData.addressAndPlaceApproximate`.
- ⚠️ La guarda de `IosParkingEnrichmentScheduler` (iosMain) no es compilable en esta máquina —
  espejo literal de la de Android; la valida el compañero de iOS.

## Consumidores auditados (`getAddressAndPlace` + campos `AddressAndPlace` en estado)
| Consumidor | Tipo | Tratamiento |
|---|---|---|
| `HomeGeocodingController` → `cameraAddressAndPlace` | display | «Cerca de …» en los 3 render sites de cámara |
| `HomeState.userAddressAndPlace` | estado | no se renderiza en ningún sitio (verificado) — sin cambio |
| `HomeViewModel:391` `prefetched = cameraAddressAndPlace` | persistencia indirecta | cubierto por la guarda de `ReportSpotReleasedUseCase` |
| `ReleaseActiveParkingSession`/`ProcessConfirmedDeparture`/`FinalizeDeducedDeparture` (prefetch desde `session.address`) | persistencia | las sesiones nunca almacenan aproximado (por las guardas) → prefetch siempre exacto |
| `EnrichParkingSessionWorker` / `IosParkingEnrichmentScheduler` | persistencia | guarda: skip aproximado + retry |
| `UpdateParkingLocationUseCase` | — | no geocodifica (delega en enrichment) — sin cambio |
| `ReportManualSpotUseCase` | persistencia | delega en `ReportSpotReleasedUseCase` → cubierto |
| Fakes (`FakeAddressAndPlaceRepository`, `FakeLocalAddressAndPlaceDataSource`) | test | ampliados con `getNearest`/`approximate` |
