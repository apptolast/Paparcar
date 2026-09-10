# OVERPASS-CLIENT-IS-NOT-FUEL-001 · Un cliente que ya sirve a tres carriles no vive en el paquete de uno

**Estado:** ✅ Done — en master (hash en `MEMORY.md`, por construcción del cierre)

## Problema

`OverpassClient`, `OverpassEndpoints` y `OverpassResponseHealth` viven en
`data/fuel/` porque ahí nacieron: los estrenó el fallback OSM de gasolineras
[OSM-ONE-MIRROR-IS-A-SINGLE-POINT-OF-FAILURE-001] y los extrajo Francia cuando necesitó el mismo
failover para otra pregunta [FUEL-FRANCE-A-PRICE-CARRIES-ITS-OWN-HOUR-001].

Hoy tienen **cuatro consumidores y solo dos son de combustible**:

| Consumidor | ¿Es de gasolineras? |
|---|---|
| `OsmFuelStationsSource` | sí |
| `OsmStationNames` (Francia) | sí |
| `OsmPublicParkingsSource` | **no** — parkings públicos [PARKINGS-ON-THE-MAP-001] |
| `OverpassPlacesDataSourceImpl` · `OverpassRoadNetworkDataSourceImpl` · `IosOverpass…` | **no** — POI y red de carreteras |

Un import `data.fuel.OverpassClient` desde `data/parkings/` no rompe nada, pero **enseña un mapa del
código que es falso**: sugiere que hablar con Overpass es asunto del combustible, cuando es la
infraestructura compartida de todo lo que pregunta «qué hay cerca». El próximo carril que la
necesite tendrá que decidir si importar «de fuel» o copiarla, y la copia es exactamente lo que
`OverpassClient` se creó para evitar.

## Doctrina que aplica

- **Sistemas, no parches**: el invariante «cómo se habla con Overpass» vive en UN sitio, y ese sitio
  debe poder nombrarse sin mentir.
- ⛔ Nada de wildcard imports al mover (`ImportGuardrailTest`).
- Es un movimiento **sin cambio de comportamiento**: si algún test cambia de resultado, el refactor
  está mal hecho.

## Diseño

`data/fuel/` → `data/overpass/` para los tres ficheros, y actualizar los imports de los 7
consumidores. `FuelHttpClient` **NO se mueve**: sirve también a Minetur y a OpenVan, que no son
Overpass, y su nombre sigue siendo cierto donde está.

## Criterio de éxito

- Cero referencias a `data.fuel.Overpass*` fuera del nuevo paquete.
- Suite, `assembleMockDebug`, `compileProdDebugKotlin` y klib iOS verdes **sin tocar un solo test**:
  un refactor de paquete que obliga a cambiar asserts no era un refactor de paquete.

## Consumidores auditados

Los **7**, todos por import y ninguno por comportamiento:
- `OsmFuelStationsSource` · `OsmStationNames` · `FranceFuelStationsSource` — estaban en el MISMO
  paquete y usaban las clases sin import; ahora lo llevan explícito, que es lo que hace visible la
  dependencia.
- `OsmPublicParkingsSource` — el que motivó el ticket: su import ya no miente.
- `OverpassPlacesDataSourceImpl` (androidMain) · `OverpassRoadNetworkDataSourceImpl` (androidMain) ·
  `IosOverpassPlacesDataSourceImpl` (iosMain) — POI y red de carreteras, que solo leen la lista.
- **`OsmFuelStationsFailoverTest` NO se movió y no se tocó**: prueba `OsmFuelStationsSource`, que es
  una clase de gasolineras. El test del failover vive con quien lo usa, no con quien lo implementa.
- `FuelHttpClient` **tampoco se movió**: sirve también a Minetur y a OpenVan, que no son Overpass.

✅ Cero referencias a `data.fuel.Overpass*` fuera del paquete nuevo. Suite, mock, prod y klib iOS
verdes **sin editar un solo test**, que era el criterio de que el refactor no cambiaba conducta.
