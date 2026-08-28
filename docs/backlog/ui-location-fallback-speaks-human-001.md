# UI-LOCATION-FALLBACK-SPEAKS-HUMAN-001 · Sin geocoder ni POI, el usuario lee un label humano, nunca coordenadas

**Estado:** ✅ Done (28-08-2026) · mergeada a master vía squash

## Problema
Cuando el reverse-geocoding y el lookup de POI fallan (sin conexión, Geocoder caído), la UI cae a
`formatCoords(lat, lon)` y el usuario ve `40.4167°, -3.7037°` como título de una plaza, de su
aparcamiento o de una fila del historial. Coordenadas crudas no son copy: no dicen nada que el mapa
de al lado no diga mejor, y delatan mecánica interna.

## Doctrina violada
- «No copy al usuario con mecánica interna» (CLAUDE.md) — un par de grados decimales es mecánica.
- Vocabulario PLAZA ≠ APARCAMIENTO [COPY-SPOT-IS-NOT-A-PARKING-001]: el fallback debe respetar de
  qué habla cada superficie.

## Señales / datos disponibles
- El pin siempre está pintado en un mapa adyacente (sheet, peek, historial, detalle) — el «dónde»
  ya lo cuenta el mapa; el texto solo necesita no mentir.
- `cameraTitleOrFallback` (PeekShared) ya hace lo correcto: cae a `home_address_unknown`, nunca a
  números. Es el quinto consumidor y queda como estaba.
- Para sesiones propias el dato es transitorio: `EnrichParkingSessionWorker` rellena la dirección
  cuando vuelve la conexión.

## Diseño
El invariante — **presentación no renderiza jamás coordenadas crudas** — se implementa haciendo
IRREPRESENTABLE el estado: se borra `formatCoords`/`formatCoord` de `presentation/util`, con lo que
ningún call site futuro puede volver a caer ahí.

- `locationDisplayText(placeInfo, address): String?` pierde `lat`/`lon` y devuelve `null` cuando no
  hay nada humano que decir; cada call site aporta su fallback contextual vía `stringResource`.
- `peekTitle(placeName, addressLine, fallback)` pierde `lat`/`lon` y recibe el fallback del caller
  (deja de necesitar ser resolver de coordenadas; el contexto lo tiene quien pinta).
- Dos strings nuevos (9 locales):
  - `location_fallback_spot` — EN «Spot on the map» / ES «Plaza en el mapa» (superficies de plaza
    comunitaria).
  - `location_fallback_parking` — EN «Location on the map» / ES «Ubicación en el mapa» (superficies
    de tu aparcamiento). No se reutiliza «Unknown location»: aquí la ubicación SÍ se conoce — está
    en el mapa — lo que falta es su nombre.

## Criterio de éxito
- ✅ Grep de `formatCoords`/`°` en `presentation/` → cero renders de coordenadas (helpers borrados).
- ✅ Con `address == null && placeInfo == null`, cada superficie muestra su label contextual.
- ✅ `testProdDebugUnitTest` + `compileMockDebugKotlinAndroid`/`compileProdDebugKotlinAndroid` en verde.
- Previews/galería: `FakeData` gana `sp_6_ungeocoded` (spot) y `s_ungeo` (sesión cerrada), ambos con
  `address = null, placeInfo = null` — el fallback ahora es VISIBLE en las superficies existentes
  (filas del sheet, peeks, historial) sin pantalla ni estado nuevos.

## Consumidores auditados
| Consumidor | Contexto | Fallback |
|---|---|---|
| `HomeSpotRows.kt` (fila de plaza del sheet) | plaza | `location_fallback_spot` |
| `SpotPeek.kt` (peek de plaza) | plaza | `location_fallback_spot` |
| `ParkingPeek.kt` (peek de tu sesión) | aparcamiento | `location_fallback_parking` |
| `BrowsePeek.kt` (peek colapsado con coche aparcado) | aparcamiento | `location_fallback_parking` |
| `HistoryTimeline.kt` (fila del historial) | aparcamiento | `location_fallback_parking` |
| `ParkingHistoryDetailScreen.kt` (detalle de historial) | aparcamiento | `location_fallback_parking` |
| `cameraTitleOrFallback` (PeekShared) | cámara | ya usa `home_address_unknown` — exento, sin cambio |
| Resto del árbol (`grep formatCoord`) | — | sin más usos; helpers borrados |

## Follow-up (fuera de alcance)
Capa 2: lookup aproximado en `RoomGeocoderCacheDataSource` (clave a ~11 m, TTL 30 días) para
responder «cerca de <calle cacheada>» offline. Sin ticket abierto aún.
