# OVERPASS-A-SINGLE-MIRROR-HAS-NO-FAILOVER-001 · Tres vías hablan con Overpass por fuera del cliente que sabe cuándo un servidor miente

**Estado:** ✅ Done — en master (10-09-2026). ⏳ Queda **verlo en device**: el criterio 6, que es el
único que la suite no puede afirmar porque lo que cambió es el transporte.

Sale del *«Fuera de alcance»* de [CACHE-A-MAP-MUST-NOT-EMPTY-ON-COLD-START-001] (`96f09b73`), donde
se anotó como hallazgo lateral sin ticket propio.

## Problema

`OSM-ONE-MIRROR-IS-A-SINGLE-POINT-OF-FAILURE-001` (`7ae5159c`) construyó el sistema entero: una lista
de espejos (`OverpassEndpoints.ALL`), un recorrido que baja al siguiente cuando uno falla
(`OverpassClient`) y un chequeo de que la respuesta se puede creer aunque venga con un 200
(`OverpassResponseHealth`). Tres consumidores lo usan.

**Otros tres no.** Cada uno se trae su propio POST a mano y su propia constante:

| Fichero | Transporte | Qué le pasa si el primer espejo falla |
|---|---|---|
| `androidMain/location/OverpassPlacesDataSourceImpl` | `HttpURLConnection` | `null` |
| `androidMain/location/OverpassRoadNetworkDataSourceImpl` | `HttpURLConnection` | `emptyList()` |
| `iosMain/location/IosOverpassPlacesDataSourceImpl` | `NSURLSession` | `null` |

Los tres declaran `val ENDPOINT = OverpassEndpoints.ALL.first()` — comparten la lista y **usan solo su
primer elemento** — y los tres llevan escrito el mismo comentario desde `PARKINGS-ON-THE-MAP-001`:
*«This lane still uses only the first of the shared list … Noted as a follow-up rather than
duplicated.»* Este ticket es ese follow-up.

Ninguno mira `timestamp_osm_base`, así que **el 200 mentiroso de `overpass.osm.ch` los engañaría hoy
igual que engañó a las gasolineras**: lista vacía, cero errores, cero rastro.

### Medido contra el endpoint real (10-09-2026)

Consulta de control (3 cafés en Chueca) contra `overpass-api.de`:

| User-Agent | Resultado |
|---|---|
| `okhttp/4.12.0` | **406** — el filtro de bots sigue vivo, un año después de medirlo |
| `Dalvik/2.1.0 (Linux; U; Android 14; …)` | **200** |
| `Paparcar/1.0 (+https://paparcar.com)` | **200** |
| curl por defecto | **200**, `timestamp_osm_base: 2026-09-10T12:48:02Z` |

Dos lecturas, y la segunda condiciona el diseño:

1. ⛔ **El User-Agent NO es el problema de estas tres vías.** `HttpURLConnection` manda el agente de
   Dalvik y pasa. No hay ningún 406 escondido aquí; decir lo contrario sería vender el arreglo con
   un fallo que no tiene.
2. ⛔⛔ **Pero sí es el peligro de ARREGLARLAS.** Pasarlas a Ktor sin más las pone detrás de OkHttp,
   cuyo agente por defecto **es exactamente el que devuelve 406**. Migrar a `HttpClient()` a pelo
   convertiría tres vías que hoy funcionan en tres vías rotas al 100 %.

Y en la misma tanda, sin buscarlo: la instancia de referencia contestó **504 dos veces de tres** en
el primer intento (los 429 posteriores me los provoqué yo martilleando, no cuentan). O sea que el
fallo del que hablamos no es hipotético — estaba pasando mientras se escribía este doc.

## Doctrina violada

**Sistemas, no parches: el invariante vive en UN sitio y se barren TODOS sus consumidores.** El
invariante *«a Overpass no se le habla sin failover ni sin comprobar que no miente»* ya está en un
sitio, escrito y comentado. Lo que faltó fue el barrido: se cerró la vía donde mordió (gasolineras) y
tres consumidores se quedaron fuera, con un comentario reconociéndolo.

⚠️ **Corrección sobre este mismo doc.** Al abrirlo escribí aquí que el failover *«no dejó ni un
test»*, apoyándome en un `grep` de `OverpassClient|isTrustworthy|OverpassEndpoints` sobre los
tests. **Es falso**: existe `OsmFuelStationsFailoverTest`, con los cinco casos (primer espejo sano ·
caído · que miente · vacío honesto · todos caídos). Lo destapó falsificar el guard, que lo puso rojo
junto al mío. El `grep` no lo encontró porque ejercita el sistema **a través de
`OsmFuelStationsSource`**, sin nombrar ninguno de los tres símbolos — un recordatorio de que un
`grep` por símbolo no prueba ausencia de cobertura.

Consecuencia ya aplicada: el `OverpassClientTest` que había escrito **se borra por redundante**
—cubría esos mismos cinco casos un nivel más abajo, que es justo la duplicación que este ticket
persigue—. Lo que sí faltaba, y se queda, es el testigo de que **las vías nuevas** recorren los
espejos: ninguna prueba tocaba Places ni RoadNetwork.

## ⛔ Lo que este ticket NO arregla (para no venderlo de más)

Al abrirlo se dijo que estas vías están *«en la ruta de detección, donde una respuesta mala cuesta un
pin»*. **Medido, no es así**, y conviene que quede escrito para que nadie lo re-descubra al revés:

| Vía | Consumidor real | Coste de una respuesta mala |
|---|---|---|
| Places | `EnrichParkingSessionWorker` → `GetAddressAndPlaceUseCase` | el **nombre** del sitio donde aparcaste. Es enriquecimiento post-park, best-effort, y la sesión conserva su dirección |
| RoadNetwork | `HomeTripController` (map-matching) | la **traza** no se pega a la calle y se queda en el GPS crudo |

Ninguna de las dos coloca, mueve ni suprime un pin, ni participa en ninguna decisión de detección.
Además las dos ya degradan limpiamente: `HomeTripController` guarda `if (roads.isNotEmpty())`, así
que ni siquiera una lista vacía mentirosa envenena su caché.

El valor de este ticket es **higiene de un sistema que ya existe y que tres sitios no obedecen**, más
el testigo que le faltaba. No es seguridad de detección.

## Señales / datos disponibles

Nada que instrumentar. Las tres piezas existen y están comentadas:
`OverpassEndpoints.ALL` (3 espejos, con el roto excluido y por qué) · `OverpassClient.fetch()`
(recorre y registra qué espejo pasó turno) · `OverpassResponseHealth.isTrustworthy()`.

## Diseño

**No se añade failover en tres sitios. Se borran los tres transportes.**

Añadir el bucle a cada uno sería el parche: tres copias del mismo recorrido, y la cuarta vía que
aparezca volvería a nacer sin él. `OverpassClient` es Ktor y por tanto multiplataforma, así que las
implementaciones no tienen ninguna razón para vivir por plataforma.

1. **`commonMain/data/places/OverpassPlacesDataSource.kt`** — implementa `PlacesDataSource` sobre
   `OverpassClient`. Sustituye a la de Android **y a la de iOS**, que eran el mismo fichero escrito
   dos veces (su propio KDoc lo decía: *«this file used to be a literal copy of the Android one,
   which is how a rule could be fixed on Android and stay broken here»* — y es exactamente lo que
   volvió a pasar con el failover).
2. **`commonMain/data/places/OverpassRoadNetworkDataSource.kt`** — implementa `RoadNetworkDataSource`
   sobre `OverpassClient`. La construcción de la query y el parseo de `out tags geom` suben con
   ella y pasan a ser testeables; hoy son privados de un fichero de `androidMain` sin un solo test.
3. **DI**: las dos se registran en `DataModule` (commonMain) y salen de `AndroidPlatformModule` e
   `IosPlatformModule`.
4. **Testigos de lo que no tenía ninguno** (el failover en sí ya lo tiene, ver la corrección de
   arriba): que la vía de Places **recorre los espejos** en vez de pararse en el `[0]`, que un fallo
   de red llega como `failure` y no como `success(null)`, y el formato de carreteras — `isMinor` y
   el descarte de ways con menos de dos vértices, que hasta hoy vivían sin prueba dentro de un
   fichero de `androidMain`.

### ⛔ El cliente HTTP es `fuelHttpClient()`, y aquí no es reutilización por pereza

Es la pieza que hace **segura** la migración, por lo medido arriba: trae el `User-Agent` que evita el
406 de OkHttp. Un `HttpClient()` a pelo en este ticket rompería las tres vías de golpe. Es el mismo
razonamiento que ya está escrito en `DataModule` para los parkings públicos.

⚠️ Su nombre dice `fuel` y ya sirve a parkings, y ahora servirá a POI y carreteras — la misma
disonancia que `OVERPASS-CLIENT-IS-NOT-FUEL-001` resolvió para el cliente de Overpass y que **dejó
fuera a propósito** para este (sirve a Minetur y a OpenVan, que sí son combustible). Renombrarlo es
una decisión aparte y **no se toca aquí**.

### Consecuencia asumida: los deadlines cambian, a mejor

| Vía | Hoy | Con el cliente compartido |
|---|---|---|
| Places | connect 6 s / read 10 s | connect 10 s / request 25 s |
| RoadNetwork | connect 6 s / read 15 s | connect 10 s / request 25 s |

El presupuesto del propio servidor Overpass es de 20 s, así que el read de 10 s de Places **mataba
en cliente respuestas que el servidor iba a entregar**. El 25 s del cliente compartido está elegido
justo por encima de ese presupuesto.

⚠️ A cambio, el peor caso de una llamada pasa a ser 3 espejos × 25 s. Es el mismo peor caso que ya
aceptan gasolineras y parkings desde `7ae5159c`, y las dos vías afectadas son de fondo (un worker con
reintentos y un map-matching con debounce cuyo fallo deja la traza cruda).

### Sobre iOS

Con las clases en commonMain, iOS deja de tener su copia de Places (gana el failover) y **pasa a
tener map-matching, que hoy no tiene**. No es trabajo de iOS —🧊 aparcado— sino la ausencia de una
exclusión artificial: la única razón por la que no lo tenía era que la implementación estaba en
`androidMain`. `RoadNetworkDataSource?` se queda nullable y `getOrNull()` intacto.

## Criterio de éxito

1. `grep` de `OverpassEndpoints.ALL.first()` → **cero resultados** en todo el repo.
2. No queda ningún `HttpURLConnection` ni `NSURLSession` apuntando a Overpass.
3. Los testigos nuevos, **falsificables**: quitar el `isTrustworthy` del cliente pone rojo el caso
   del 200 mentiroso y nada más; devolver `success(null)` ante un fallo de red pone rojo el de la
   celda sellada y nada más.
4. `:shared:testDebugUnitTest --rerun-tasks` en verde (Konsist incluido: los imports nombrados).
5. `assembleProdDebug` + `assembleMockDebug` compilan; el klib de iOS compila.
6. En device: un aparcamiento sigue recibiendo su nombre de sitio y la traza de un viaje sigue
   pegándose a la calle — o sea, que la migración de transporte no rompió lo que ya funcionaba.

## Consumidores auditados

`grep` de todo lo que habla con Overpass:

| Sitio | Estado |
|---|---|
| `OsmFuelStationsSource` | ✅ ya usa `OverpassClient` |
| `OsmStationNames` (Francia) | ✅ ya usa `OverpassClient` |
| `OsmPublicParkingsSource` | ✅ ya usa `OverpassClient` |
| `OverpassPlacesDataSourceImpl` (androidMain) | 🔴 se borra → commonMain |
| `OverpassRoadNetworkDataSourceImpl` (androidMain) | 🔴 se borra → commonMain |
| `IosOverpassPlacesDataSourceImpl` (iosMain) | 🔴 se borra → commonMain |
| `MockModule` (`app/src/mock/`) | 🔴 **se me escapó en el `grep` y lo cazó el compilador** — ataba el road source REAL a propósito, para que el sim del Dev Catalog ejercite map-matching de verdad en device. Reatado a la clase nueva con `fuelHttpClient()` |
| `AndroidGeocoderDataSourceImpl` | ⚪ exento — es **Photon/Nominatim**, no Overpass; ya manda su propio `User-Agent` |

⚠️ El de `MockModule` no aparecía porque busqué por el nombre de la INTERFAZ y por `Overpass*` en
`shared/`, y ese estaba en `:app`. Barrer un invariante exige barrer los dos módulos.

### Consumidor con deadline propio, arreglado

`AddressAndPlaceRepositoryImpl.PHASE2_TIMEOUT_MS` valía **20 s** — puesto cuando la vía hablaba con
UN espejo, y documentado como *«safety net ABOVE Overpass's own HTTP timeouts (6s connect + 10s
read)»*. Con el failover, ese techo habría cortado la caminata a mitad del segundo espejo: el tercero
no se habría preguntado nunca y el arreglo entero sería invisible desde el único sitio que lo usa.
Sube a 80 s, por encima de 3 espejos × 25 s.

## Verificado

- **2.670 tests / 0 fallos** con `--rerun-tasks` (18 ficheros de guardarraíles ejecutados, Konsist
  incluido). El total baja de 2.674 a 2.670 porque el `OverpassClientTest` redundante se borró.
- **Falsificados los dos invariantes nuevos**, uno cada vez, y cada uno tiñe de rojo solo lo suyo:
  - quitar el `isTrustworthy` de `OverpassClient` → 3 rojos: los dos de `OsmFuelStationsFailoverTest`
    que lo vigilaban **y el mío**. Es lo que destapó que la afirmación de «sin tests» era falsa.
  - devolver `success(null)` ante un fallo de red (`.recover { null }`) → **1 rojo exacto**:
    `should_reportAFailure_when_everyMirrorIsDown`.
- **Criterio 1**: `grep` de `ALL.first()` → cero ocurrencias en código (solo la cita en un comentario
  de `DataModule` que explica por qué ya no existe).
- **Criterio 2**: no queda ningún `HttpURLConnection`/`NSURLSession` apuntando a Overpass — las
  únicas menciones que quedan son comentarios que cuentan la historia. El geocoder sigue con el suyo,
  que es otro servicio.
- `assembleProdDebug` + `assembleMockDebug` generan APK; `compileKotlinIosSimulatorArm64` compila.

⏳ **Falta el criterio 6 (device)**: que un aparcamiento siga recibiendo su nombre de sitio y que la
traza de un viaje siga pegándose a la calle. Es lo único que no puede afirmar la suite, porque lo que
cambió es el transporte.

## Detección: por qué no entra por `det-change`

Uno de los consumidores es `EnrichParkingSessionWorker`, que está en la lista de la skill. Pero aquí
**no se edita ese worker, ni ninguna estrategia, evaluador, config, geofence o mapper**: cambia el
transporte de una fuente de datos que el worker consume a través de un use case, y su contrato
(`Result<PlaceInfo?>`) no se toca. Ninguna decisión de detección cambia de resultado.
