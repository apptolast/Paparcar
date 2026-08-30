# POI-A-PLACE-IS-NAMED-ONLY-IF-YOU-ARE-AT-IT-001 · el POI que se nombra es el más cercano, y solo si estás en él

**Estado:** ✅ Done · mergeado a master (squash) el 30-08-2026 · 1.882 tests en verde

## Problema

El nombre del POI que acompaña a un aparcamiento sale a veces de un sitio en el que no estás. Dos
causas independientes, medidas en el código de master (`fdba2b00`):

**1 · La distancia no decide.** `parseResponse` ordena por `CATEGORY_PRIORITY` **primero** y por
distancia **después** (`OverpassPlacesDataSourceImpl.kt:93-96`, idéntico en el de iOS `:105-110`).
Dentro del radio, una gasolinera a 79 m gana a un café a 3 m, porque `FUEL` es el índice 0 de la
lista de prioridad y `CAFE` el 6. La prioridad se pensó para desempatar dos negocios del mismo
portal; hoy desempata cosas separadas por una manzana.

**2 · La distancia que se mide no es al POI, es a su centroide.** La query pide `out center`
(`:58`), que para ways y relations devuelve el centro geométrico del polígono. Estando pegado a la
fachada de un supermercado su propio edificio ya aporta 25-35 m, y un centro comercial o un hospital
100-150 m. Por eso el radio es 80 y no 30: **no se puso para alcanzar POIs lejanos, se puso para que
un supermercado en cuya puerta estás no se cayera de la lista.** El efecto colateral es que ese
mismo radio deja entrar bares al otro lado de la calle.

De ahí que no exista hoy "un radio fiable": con la distancia medida al centroide, el umbral honesto
sería ~40 m para nodes y ~100 m para polígonos grandes, y el código no distingue unos de otros.

**Síntoma acreditado por el user (30-08):** *"hay veces que no caemos exactamente en el poi y queda
algo lejos"* → *"si estoy a 100 metros de un poi no tiene sentido decir que el aparcamiento está
ahí"*.

**Daño colateral ya visible:** el radio real ni siquiera está documentado igual en tres sitios que
lo describen — `PlacesDataSource.kt:6` dice "~50 m", el KDoc de la impl Android dice "~150 m"
(`:20`), y la constante vale **80**. Tres números para un solo valor es la firma de una regla que
nadie posee.

## Doctrina violada

- **Fallo asimétrico, aplicado al copy.** Un POI equivocado no es un detalle estético: es la línea
  que el usuario lee para reconocer dónde dejó el coche. Callar (quedarse con el nombre de la calle)
  cuesta un nombre; mentir cuesta la credibilidad del pin. La doctrina de detección ya dice
  *"ante la duda se PREGUNTA, nunca se planta una plaza fantasma"*; nombrar un POI a 100 m es
  exactamente plantar una certeza que no se tiene.
- **Sistemas, no parches.** `OverpassPlacesDataSourceImpl` (androidMain) e
  `IosOverpassPlacesDataSourceImpl` (iosMain) son **copias literales**: misma query, mismo
  `resolveCategory` de 20 líneas, mismo `haversineMeters` reimplementado, misma `CATEGORY_PRIORITY`.
  No hay una sola línea de esa lógica en `commonMain` y por tanto **no hay un solo test** que la
  cubra. Arreglar el orden en un fichero y no en el otro dejaría iOS mintiendo.
- **Magic numbers.** El 80 vive duplicado en dos `companion object` sin fuente común.

## Señales / datos disponibles

- Overpass admite el modificador `bb` en el `out`: *"Adds only the bounding box of each element to
  the element. For nodes this is equivalent to `geom`. For ways it is the enclosing bounding box of
  all nodes"* (wiki OSM, Overpass QL, sección `out`), y mantiene los tags porque `body` sigue siendo
  el modo por defecto. Es decir: **`out bb` da el rectángulo del POI sin traerse la geometría
  completa**, que es justo lo que hace falta para medir al borde en vez de al centro.
  ⚠️ Verificado por documentación, no contra la API: `overpass-api.de` es inalcanzable desde este
  entorno (los mirrors devuelven 502 tras el proxy, `example.com` responde 200).
- `GeoUtils.kt` ya tiene `haversineMeters`, `BoundingBox` y `boundingBox(...)` en commonMain — falta
  la distancia punto→caja, que es la pieza nueva de geometría.
- Error real del pin medido en campo: 12-54 m en condiciones normales
  (`project_det_field_2026_08_30_redmi_gps_wall`), con episodios patológicos de 59-245 m.

## Diseño

**El invariante:** *un POI se nombra solo si el pin está DENTRO o al borde de él; y entre los que
cumplen eso, gana el más cercano.* Vive en una función pura de `commonMain`, y las dos impls de
plataforma se quedan **solo con el HTTP**.

Tres números, todos con su razón:

| Constante | Valor | Por qué |
|---|---|---|
| `QUERY_RADIUS_METERS` | 80 | Lo que se le pide a Overpass. Se mantiene: ahora que medimos al **borde**, el margen extra solo sirve para que un polígono grande entre en la lista y luego se mida bien. No es el radio de nombrado. |
| `NAMING_RADIUS_METERS` | 40 | Distancia máxima **al borde** para nombrar. Error GPS urbano típico (10-25 m) + acera/calzada/plaza (10-15 m) ≈ media manzana. Más allá ya hay otro portal en medio. |
| `CATEGORY_TIE_METERS` | 10 | La prioridad de categoría **deja de ordenar y pasa a desempatar**: solo decide entre candidatos que están a ≤10 m del más cercano, o sea el mismo portal con dos negocios. |

**Distancia al borde:** `distanceToBoundingBoxMeters` en `GeoUtils` — distancia AABB estándar, **0 si
el punto está dentro de la caja**. Para un node (café, farmacia, cajero) el bbox degenera en el punto
y sale la haversine de siempre, así que un solo umbral vale para nodes y para polígonos. Esa es la
razón de ser del cambio a `out bb`: sin él, 40 m no puede ser honesto para las dos cosas.

**Reparto de ficheros:**

- `domain/places/NearbyPlacePolicy.kt` *(nuevo, puro)* — `PlaceCandidate`, la distancia de un
  candidato, las tres constantes y `pick(candidates, lat, lon): PlaceInfo?`.
- `data/places/OverpassPlaceParser.kt` *(nuevo, commonMain)* — construcción de la query, modelos
  `@Serializable` y `resolveCategory`. El parser lee `bounds` → `center` → `lat/lon` en ese orden,
  así que si un mirror ignorase `bb` la respuesta degrada al comportamiento de hoy en vez de perder
  el POI.
- `OverpassPlacesDataSourceImpl` / `IosOverpassPlacesDataSourceImpl` — solo POST y timeouts.

## Verificación

`./gradlew :shared:testDebugUnitTest --rerun-tasks` → **1.882 tests, 0 fallos** (master traía 1.865;
16 nuevos + 1 en `AddressAndPlaceRepositoryImplTest`). `:app:compileProdDebugKotlin` y
`:app:compileMockDebugKotlin` en verde.

**Falsación de los tests nuevos.** Un test que nunca se ha visto fallar no prueba nada, así que se
revirtió a mano la política al comportamiento viejo y se comprobó el rojo:

| Se revirtió | Rojos |
|---|---|
| distancia al centroide + orden por categoría sobre toda la lista | 4 (los dos de "gana el más cercano" y los dos de borde/footprint) |
| solo el umbral de nombrado (40 → 80) | 1 (el del POI a 60 m) |

**Lo que la falsación descubrió, y que corrigió un test.** La primera versión del test del umbral
usaba un hospital a **100 m** — y NO se ponía rojo contra el código viejo, porque `around:80` ya lo
filtra en el servidor: un node a 100 m nunca llega. O sea que el "100 metros" del síntoma **no puede
venir de un node lejano**: viene de la banda 40-80 m (que el código viejo nombraba sin dudar) y sobre
todo del orden por categoría. El test se reescribió a **60 m**, que es la distancia que de verdad
discrimina, conservando el caso de 100 m solo como garantía de que la política no se apoya en la
query para ser correcta.

⚠️ **iOS no se ha compilado aquí.** `:shared:compileCommonMainKotlinMetadata` ni arranca, por un
bloqueo previo y ajeno a este ticket: `com.github.apptolast.BaseLogin:custom-login-iosarm64:1.1.0`
no existe en JitPack. El fichero de iOS quedó reducido a HTTP y no contiene ya ninguna decisión, así
que el riesgo es de imports, no de lógica — pero lo tiene que confirmar el job `macos-latest` del CI
(`CI-IOS-COMPILES-ON-A-MAC`).

## Criterio de éxito

- Un café a 3 m gana a una gasolinera a 79 m (hoy pierde).
- Un POI cuyo borde queda a 100 m no se nombra: la línea se queda con la calle.
- Estando dentro del recinto de un supermercado la distancia es 0 y se nombra, aunque su centroide
  esté a 60 m — el caso que el radio de 80 protegía por accidente y que ahora está protegido a
  propósito.
- Dos negocios del mismo portal (≤10 m entre sí) siguen resolviéndose por categoría.
- Todo lo anterior cubierto por tests de `commonTest`, que hoy no existen para nada de esto.

## Consumidores auditados

| Sitio | Asume | Estado |
|---|---|---|
| `OverpassPlacesDataSourceImpl` (androidMain) | orden por categoría, `out center`, radio 80 | 🔧 reescrito sobre la política común |
| `IosOverpassPlacesDataSourceImpl` (iosMain) | copia literal del anterior | 🔧 reescrito igual — es la mitad del ticket |
| `PlacesDataSource.kt:6` | KDoc dice "~50 m" | 🔧 corregido: el contrato pasa a nombrar la política |
| `RoomLocalAddressAndPlaceDataSource.getNearest` (`MAX_NEAREST_DISTANCE_METERS = 250`) | presta la celda vecina **con su `placeInfo`** | 🔧 **rompe el invariante nuevo**: nombraría un POI de hasta 250 m. Ver abajo |
| `AddressAndPlaceRepositoryImpl:65` | `placeInfo = nearest?.placeInfo` | 🔧 pasa a `null` — la celda prestada presta calle, no POI |
| `EnrichParkingSessionWorker`, `ReportSpotReleasedUseCase`, `IosParkingEnrichmentScheduler` | persisten lo que llegue | ✅ exentos, no deciden nada |
| `HomeParkingRow:329`, `HomeSpotRows:174`, `BrowsePeek:103`, `HistoryTimeline`, `ParkingHistoryDetailScreen` | pintan `placeInfo?.name` si existe | ✅ exentos, ya toleran null |
| Caché Room (celda ~11 m, TTL 30 días, `poiChecked`) | sella también los `null` | ✅ correcto y deseado: "aquí no hay POI" es una respuesta real |

**El caso de los 250 m.** `GEO-CACHE-ANSWERS-NEARBY-001` existe para que, si el geocoder de Fase 1
falla, se muestre la calle de una celda cacheada cercana en vez de una dirección vacía. Su propio
KDoc lo dice en singular — *"borrow the nearest cached **street**"* (`AddressAndPlaceRepositoryImpl:57`)
— pero la línea 65 presta además el `placeInfo` de esa celda. Una calle vecina a 250 m sigue siendo
una calle honesta ("estás por aquí"); un **POI** a 250 m es precisamente la afirmación que este
ticket prohíbe. Se deja de prestar el POI. No se toca el radio de 250 m, que gobierna la calle y
tiene su propia justificación.
