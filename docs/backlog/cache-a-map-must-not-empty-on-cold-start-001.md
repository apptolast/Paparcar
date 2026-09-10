# CACHE-A-MAP-MUST-NOT-EMPTY-ON-COLD-START-001 · Los parkings y las gasolineras que ya vimos sobreviven al arranque en frío

**Estado:** ✅ Done — en master (hash en `MEMORY.md`, por construcción del cierre)

## Verificado EN DEVICE (emulador, prodDebug, sha256 comprobado)

GPS en Madrid, con red: 54 parkings y 63 gasolineras con precio real del Ministerio. La base de
datos, leída del propio device, quedó así:

| tabla | filas |
|---|---|
| `osm_coverage` | 2 — `parkings\|1500\|40.417,-3.704` y `fuel\|5000\|40.417,-3.704` |
| `osm_public_parkings` | 54 |
| `fuel_stations` | 63, con `stationKey` = `es-minetur\|4508` |
| `fuel_station_fuels` | 189, cada precio con su `reportedAtMs` |

Dos discos separados por capa **y por radio**, y la clave de estación con su prefijo de fuente:
las dos decisiones de diseño, visibles en el dato.

Luego, **matando la app y cortando toda la red**, arranque en frío:
- Parkings: la lista entera, y al pie *«No connection: these are the car parks we saw here 5 min ago.»*
- Gasolineras: la lista con sus precios, y *«No connection: these are the stations we saw here 5 min ago.»*

Antes de este ticket las dos secciones decían «no hemos podido cargar» y el mapa salía vacío.

## Avance

**Hecho y verde** (suite completa de `:shared`, sin commitear):
- Room v6 → **v7**: `osm_public_parkings` + `osm_coverage`, con `MIGRATION_6_7`. El `CREATE TABLE`
  escrito a mano se comparó carácter por carácter contra el `createSql` que Room exporta en
  `7.json` — coincide.
- `data/cache/OsmCoverage.kt`: la geometría pura — `coverageKey`, `CoverageDisc.covers`
  (**contención, no solape**), `boundingBox` (siempre más ancha que el círculo) y
  `effectiveRadiusMeters` (una respuesta al tope estaba truncada y no puede reclamar su radio).
- `data/parkings/RoomPublicParkingsCache.kt`: lectura con cobertura (edad = disco más viejo) y
  escritura que **reconcilia el disco entero**, más la retención que poda **discos** y barre las
  filas que ningún disco vivo cubre.
- Tests: `OsmCoverageTest` (9) y `RoomPublicParkingsCacheTest` (7), más el **testigo de
  `MIGRATION_6_7`** dentro de `AppDatabaseV1BaselineTest`, siguiendo el patrón que ya usan las
  cinco migraciones anteriores.
- Falsificado: quitando el `deleteByIds` de la reconciliación, `should_dropAFacility_…` falla —
  y solo ese. El test vigila lo que dice vigilar.

**La mitad de parkings, ENCHUFADA de punta a punta** (2.646 tests, 0 fallos, guardarraíles Konsist
forzados con `--rerun-tasks`; `assembleMockDebug` y `compileProdDebugKotlin` verdes):
- `PublicParkingsResult(parkings, rememberedAtMs)` — el puerto ya no devuelve una lista pelada.
  `null` = recién bajado; un valor = el disco, y **cuándo se preguntó por última vez**.
- `DiskBackedPublicParkingsSource` = L2, entre L1 y la red. Se lee **solo cuando la red falla**, y
  un fallo sin cobertura sigue siendo el fallo ORIGINAL, nunca «no hay parkings aquí».
- DI: la pila queda `Caching(memoria 30 min) → DiskBacked(Room 30 días) → Osm(red)`.
- El use case propaga el sello **a través del filtrado** — con su test, porque ese es exactamente el
  sitio donde un campo se pierde al reconstruir la lista.
- Sección: tercera frase bajo las filas, con la EDAD vía `relativeTimeText` (ya traducido) — una
  sola key nueva, `home_parkings_remembered`, en los **9 locales**, verificada sin `\'` ni `%%`.
- Galería: variante «Recordado del disco (sin conexión)», **con fecha calculada**, no con el valor
  por defecto — la lección de `DEV-A-GALLERY-VARIANT-MUST-EXERCISE-ITS-CASE-001`.

**La mitad de gasolineras, también ENCHUFADA** (2.655 tests, 0 fallos):
- `fuel_stations` (geografía, retención 30 días) + `fuel_station_fuels` (precio **y**
  disponibilidad, **sin TTL**), en la **misma v7**: v7 no ha salido nunca, así que partir un ticket
  en dos números inventaría un escalón que ningún device dará jamás.
- ⛔ **Una sola tabla para los tres países, y la fila se identifica por `fuente|id`.** Lo destapó
  una pregunta del user: `FuelStation.id` es estable *dentro de su fuente*, y con Minetur, Francia y
  OSM en la misma tabla el `IDEESS` `1234` podía pisar un element id `1234` de OSM. Una tabla por
  país sería peor: la clave del caché es **geográfica**, un disco cerca de Irún cruza la frontera, y
  el `countryCode` viene del geocoder, **que falla justo cuando no hay red** — sería ilegible
  exactamente cuando hace falta.
- `FuelStationsResult` gana `source` e `isTruncated`, ambos cosas que **solo sabe quien contestó**:
  OSM corta en 60, Francia en 100, y España no corta porque filtra una provincia entera en el móvil.
  Por eso `effectiveRadiusMeters` recibe el flag en vez de deducirlo de un recuento.
- El pie de sección dice lo recordado **antes** que `reportedAt`, porque una respuesta que no
  ocurrió no tiene sello de feed. Key nueva `home_fuel_remembered` en los 9 locales.
- Testigo de migración para las dos tablas, y galería con su variante.

**Corregido durante la implementación:** el §3 decía que *«pasada una edad máxima, la estación se
enseña SIN precio»*. **Se retira.** El carril de Francia ya enseña precios de hasta trece meses con
su edad, por decisión medida de `FUEL-FRANCE-A-PRICE-CARRIES-ITS-OWN-HOUR-001`; un corte solo en la
ruta de disco daría **dos verdades distintas para el mismo número**. Un precio de disco no es menos
honesto que uno de red — dice cuándo se midió igual que el otro. Lo que añade el disco es que la
pregunta de HOY falló, y eso lo dice el pie.

**Pendiente:**
1. ⏳ **Decisión de diseño, no la tomo yo**: dónde dice el MAPA que está recordando. El dato ya
   viaja en `HomeParkingsLayerSlice.rememberedAtMs`; el sitio natural es la banda de los FABs, con
   la cápsula de GPS [UI-A-WARNING-COSTS-NOTHING-WHEN-SILENT-001], no una superficie flotante nueva.
   Mientras no se decida, el mapa dibuja pines recordados sin decirlo — y eso es deuda abierta.
3. Verlo en device: modo avión + arranque en frío.

**Consumidores auditados** (grep de `PublicParkingsSource` · `parkingsNear` · el use case): los 8 —
`DataModule`, `DomainModule`, `HomeViewModel` (los DOS carriles: sección y capa), `HomeParkingsSlice`
+ `HomeParkingsLayerSlice`, `FakePublicParkingsSource` (mock), `MockModule`, y los tests
`GetNearbyPublicParkingsUseCaseTest` + `HomeViewModelTest`. Ninguno quedó asumiendo la lista pelada.

## Problema

Las dos capas de terceros del mapa —parkings públicos y gasolineras— viven **solo en memoria**:

| Capa | Caché | TTL | Sobrevive a la muerte del proceso |
|---|---|---|---|
| Gasolineras | `CachingFuelStationsSource.kt:29` | 15 min | **no** |
| Parkings públicos | `CachingPublicParkingsSource.kt:27-29` | 30 min | **no** |
| Geocoder | Room `geocoder_cache` | 30 días | sí |
| Spots | Room `cached_spots` | — | sí |

Dos consecuencias, y la segunda es la que importa:

1. **Esta es una app de detección: el proceso lo mata el OEM constantemente.** Cada arranque en
   frío de Home es una query a Overpass desde cero, sobre infraestructura donada que ya nos
   contestó `406` una vez [F5b-2] y que no tiene ningún rate-limiter por nuestro lado
   (`OverpassClient.kt` no maneja 429 ni `Retry-After`).
2. **Sin cobertura, el mapa se queda vacío justo en el caso de uso literal de la feature**: estás
   en un parking subterráneo, o en una carretera sin red, buscando dónde meter el coche. La
   pregunta *"¿dónde aparco?"* se hace precisamente donde la radio no llega.

La distinción que hoy no está resuelta: los TTL de 15/30 min contestan a **«¿vuelvo a preguntar?»**.
Nadie contesta a **«¿qué enseño cuando no puedo preguntar?»**. Son dos preguntas distintas, y
reutilizar el TTL de la primera para la segunda sería el error.

## Doctrina violada

Ninguna doctrina de detección. La que se viola es la del propio proyecto sobre datos de terceros,
ya establecida y aplicada **una sola vez de tres**: `geocoder_cache` es exactamente esta clase de
dato —respuesta de un tercero anclada a coordenadas— y está en Room a 30 días. Parkings y
gasolineras son el mismo dato y no lo están. El invariante existe, pero vive en un solo consumidor
en vez de en un sitio.

Y hay una segunda, más fina, que este ticket debe **respetar**: *un dato dice cuándo se midió*
[FUEL-AN-AVERAGE-MUST-SAY-WHEN-IT-WAS-MEASURED-001]. Un dato leído del disco no puede presentarse
como si acabara de bajarse.

## Objeción ya registrada en el código, y cómo se resuelve

⛔ Esto no es un descuido: **el código argumenta contra persistir, con una medición detrás**, y hay
que responderle antes de tocar nada.

`CachingFuelStationsSource.kt:14-18` dice, literalmente, que está en memoria y no en Room *"y eso es
una decisión con una razón"*: no sabemos cuánto tiempo sigue siendo útil un precio —el feed italiano
tiene el **69 % de sus precios con 2 a 7 días** y una cola que llega a **2013**— y *"decidir un TTL
de disco antes de medirlo sería hornear una suposición dentro de la base de datos"*.

**La objeción es correcta sobre el PRECIO y no aplica a la ESTACIÓN.** Hoy `FuelStation` mezcla dos
vidas en un objeto: la estación (id, nombre, dirección, coords, horario) es geografía y dura meses;
el precio es perecedero. La salida no es elegir un TTL de disco para el precio: es **no ponerle
ninguno**. Desde Francia, `StationPrice.reportedAtMs` ya lleva su propia hora
(`StationPrice.kt:40`), medida y con su razón — p50 2,2 h, p90 111,8 h, máximo **trece meses**. Un
precio guardado con su hora no miente por estar guardado; el lector decide. Que es la doctrina de
`f76a91df` aplicada un nivel más abajo.

Lo mismo con el KDoc de parkings (`CachingPublicParkingsSource.kt:17-19`): la ocupación en vivo de
`PARKINGS-LIVE-OCCUPANCY-001` **no viaja bajo este TTL**. Sigue fuera, y en disco también.

## Señales / datos disponibles

- `StationPrice.reportedAtMs` — la edad de cada precio, ya persistible y ya renderizada en 9 locales.
- `PublicParking` (`PublicParking.kt:54-75`) — sin ningún campo perecedero: id, nombre, operador,
  tipo, coords, capacidad, tarifa, gálibo, horario. Cobertura medida el 09-09 sobre cinco ciudades.
- `id` estable dentro de su fuente en ambos modelos (`FuelStation.kt:37`, `PublicParking.kt:55`) —
  es la clave primaria natural, sin inventar nada.
- `haversineMeters` + el patrón `pickNearestCell` (`RoomGeocoderCacheDataSource.kt:93-104`): filtro
  fino en Kotlin sobre un candidato ancho de SQL. Ya probado.
- `evictExpired` (`GeocoderCacheDao.kt:26-27`) — la evicción por edad, ya escrita.

## Diseño

### 1 · Dos capas, dos preguntas. L1 no se toca

`LocationKeyedCache` (memoria) se queda tal cual, con sus 15/30 min: sigue contestando *"¿vuelvo a
preguntar?"*. Room se apila **debajo** como L2 y contesta *"¿qué enseño si no puedo preguntar?"*.
No se sustituye nada. Orden de lectura: L1 → red → L2. ⛔ **L2 se lee cuando la red FALLA**, no
antes que ella: si hay cobertura, el dato de hoy gana siempre.

### 2 · Por entidad con bbox, NUNCA por blob de respuesta

⛔ El error que hay que no cometer: guardar en disco la respuesta entera bajo la clave de
`locationCacheKey` (coords redondeadas a 110 m + radio). Esa clave es **correcta para L1 y
equivocada para L2** — mueves la cámara 200 m y es otra clave, con lo que el caché de disco falla
casi siempre y encima ocupa. Ese es justo el fallo que su propio KDoc describe para las coordenadas
exactas (`LocationKeyedCache.kt:41-44`), un escalón más arriba.

En Room va **una fila por elemento**, con su `id` de fuente como PK, `latitude`/`longitude`
indexados, y se consulta por caja:

```sql
SELECT * FROM osm_public_parkings
WHERE latitude BETWEEN :minLat AND :maxLat AND longitude BETWEEN :minLon AND :maxLon
```

…y el haversine fino en Kotlin, como ya hacen las fuentes (`OsmPublicParkingsSource.kt:50-52`).
Así una fila guardada sirve a cualquier consulta que la contenga, venga de donde venga la cámara.

### 2 bis · ⛔ La unidad de verdad es el ÁREA, no la fila

Guardar solo las entidades tira **lo único que tiene edad de verdad**. Overpass no afirma *"este
parking existe"*: afirma *"esto es todo lo que hay dentro de R metros de P, ahora"*. Esa frase es la
que envejece. Sin ella, el diseño rompe por tres sitios a la vez:

1. **Las bajas no se propagan nunca.** Una lectura por bbox es la unión de todo lo escrito ahí
   alguna vez. Si OSM borra un parking, la respuesta nueva ya no lo trae, pero la fila sigue y el
   bbox la sigue devolviendo. La tabla acumula lo que ya no existe y solo crece.
2. **La lectura por bbox mezcla edades.** Devuelve filas de hace dos minutos junto a filas de un
   viaje del mes pasado. Filtrar cada fila por su `cachedAt` deja un conjunto parcial —un agujero
   donde estaban las viejas, que se lee como *"aquí no hay parkings"*, y es mentira—. No filtrar
   mezcla sin poder decir cuál es cuál. Ninguna de las dos vale.
3. **"Aquí no hay nada" no se puede guardar.** Cero filas es indistinguible de "nunca preguntamos".

Por eso va una tercera tabla, pequeña: **`osm_coverage`** — qué disco (`latitude`, `longitude`,
`radiusMeters`, `layer`) se preguntó y **cuándo** (`fetchedAt`). Con ella:

- **Refrescar** = re-pedir un **disco** caducado, no una fila. La pregunta *"¿esto está viejo?"*
  pasa a tener respuesta.
- **Reconciliar** = una respuesta nueva es la verdad sobre su disco entero: lo que estaba dentro y
  **no** viene en la respuesta **se borra**; lo demás se upserta; el disco se sella. Sin esto no hay
  ninguna vía para dar de baja nada.
- **Edad honesta** = lo que se enseña tiene la edad del **disco más viejo que cubre lo visible**,
  nunca la de la fila más nueva. Y una zona sin resultados pasa a ser un hecho guardable.

Esto último es la doctrina de la casa un nivel más abajo: `FeeInfo.UNKNOWN` y
`FuelAvailability.UNKNOWN` existen porque *la ausencia no es gratis* (`PublicParking.kt:36-39`,
`FuelStation.kt:5-20`). Aquí: **el silencio no es un dato si nadie apuntó haber preguntado.**

⛔ Y la consecuencia que hay que no olvidar: **el TTL de fila deja de ser el mecanismo de frescura**.
Las filas viven mientras su disco las respalde; los 30 días pasan a ser el techo de retención (con
la poda por número), no la definición de "fresco". Lo fresco lo dice el disco.

### 2 ter · Refresco dirigido por la atención, sin worker

⛔ **No hay worker de refresco en background, y es a propósito.** Overpass es capacidad donada, y
`OverpassClient` no maneja 429 ni `Retry-After`: refrescar mapa que nadie está mirando gasta esa
capacidad en una suposición. El refresco lo dirige la atención, que es el modelo que la app ya
tiene: si el usuario está ahí y su disco está caducado, se re-pregunta y se reconcilia. Los carriles
de `ViewportRefetch` ya deciden *cuándo* mirar; lo que aporta este ticket es que ese vistazo, además
de pintar, **salda la deuda del disco**.

Si algún día una medición muestra zonas que el usuario visita a diario y cuyo disco nunca se salda,
esta es la sección que cambia — y nada por encima.

### 3 · Dos tablas de datos, y el precio en su propia fila

**`osm_public_parkings`** — espejo de `PublicParking` + `cachedAt`. Un TTL largo de verdad
(**30 días**, el mismo que el geocoder, y por el mismo motivo: es geografía).

**`fuel_stations`** — la parte que no envejece: id, fuente, nombre, dirección, town, coords,
horario, disponibilidad, `cachedAt`. TTL de fila: **30 días**.

**`fuel_station_prices`** — `(stationId, fuelType)` como PK compuesta, `perUnit`, `currency` y
`reportedAtMs`. **Sin TTL propio, a propósito**: la fila guarda cuándo se midió el precio y el
lector decide qué hacer con esa edad. Es lo que responde a la objeción de arriba: la base de datos
no hornea ninguna suposición, almacena una medición.

…y "el lector decide" tiene que decidir algo concreto, o es una excusa: **pasada una edad máxima, la
estación se enseña SIN precio**, no con el de hace tres semanas. Fallo asimétrico, el de siempre:
mejor sin precio que con uno falso. El umbral se pone donde la medición de Francia lo pide
(`StationPrice.kt:10-13`: p50 2,2 h, p90 111,8 h, cola a trece meses) y se escribe en UN sitio, del
lado del lector — no en el esquema.

### 4 · Un dato de disco no se disfraza de dato de hoy

El resultado que sube a la UI tiene que poder decir que viene del disco y de cuándo. Para las
gasolineras ya está resuelto sin trabajo extra: cada precio lleva su hora y la UI ya dice la edad.
Para los parkings hace falta que el resultado lleve su `cachedAt`, y que la capa lo diga —copy
pendiente de decidir, pero el dato tiene que llegar arriba. ⛔ Sin esto el mapa afirma estar vivo
cuando está recordando, que es la mentira que este proyecto ya arregló dos veces.

### 5 · Evicción obligatoria, por edad **y** por número, al escribir

`geocoder_cache` solo evicta por edad, y le basta: es una fila por celda de 11 m que el usuario ha
visitado de verdad. Estas tablas crecen **por paneo de mapa**, que es otro régimen. Van las dos
podas, en el `put`:

- por edad (`DELETE … WHERE cachedAt < :expiry`), copiando `evictExpired`;
- por número, con un tope duro por tabla, tirando las más viejas.

⛔ Y **la poda arrastra su disco**: borrar filas por retención sin retirar el `osm_coverage` que las
respaldaba deja un disco que afirma cobertura sobre datos que ya no están — o sea, convierte una
poda en un "aquí no hay nada" falso. Se borran juntos.

### 6 · Esquema: Room v6 → **v7**, cuatro tablas

`osm_public_parkings`, `fuel_stations`, `fuel_station_prices` y `osm_coverage`.

⚠️ Sería el **primer `CREATE TABLE`** de la cadena: `MIGRATION_1_2` … `MIGRATION_5_6` son todas
`ALTER TABLE ADD COLUMN` (`AppDatabaseMigrations.kt`). Y el aviso de `AppDatabase.kt:31-36` aplica
en pleno, porque **ya hay usuarios reales desde 1.0.0**: el bump de versión y la `Migration` van
juntos o el fichero queda rechazado en cada apertura para siempre.

## Criterio de éxito

1. **Observable en device**: abrir Home con las dos capas encendidas, matar la app, **modo avión**,
   arrancar en frío → el mapa sigue enseñando los parkings y las gasolineras de antes, y dice que
   son recordados y de cuándo. Hoy sale vacío.
2. Con red, el comportamiento no cambia en nada: la respuesta de hoy sigue ganando siempre.
3. Un precio guardado nunca se enseña sin su edad, y pasado el umbral no se enseña en absoluto.
4. **Una baja desaparece**: un elemento guardado que deje de venir en una respuesta fresca de su
   disco ya no se pinta ni sobrevive en la tabla. Es el criterio que distingue este diseño del que
   solo acumula.
5. **Una zona vacía se recuerda como vacía**, no como desconocida: volver a ella sin red no dispara
   otro intento ni finge que faltan datos.
6. Tests: round-trip de las tablas, la consulta por bbox, la **reconciliación de un disco** (alta,
   baja y permanencia en la misma respuesta), la edad tomada del disco más viejo, las dos podas
   —con su disco—, y la migración 6→7 sobre un fichero v6 con datos (el guardarraíl de
   `AppDatabaseV1BaselineTest` fija el esquema).

## Riesgos

- ⚠️ **`refactor/OVERPASS-CLIENT-IS-NOT-FUEL-001-neutral-package`** (worktree
  `../Paparcar-overpass-pkg`) mueve `OverpassClient` y compañía de `data/fuel/` a `data/overpass/`.
  Medido el 10-09: el solape con este ticket es **una línea de import** en `OsmPublicParkingsSource`
  — no es una colisión de paquete, era una alarma mía mal calibrada.
  ⛔ Lo que sí importa de esa rama: **su doc se declara `✅ Done — en master` y es falso**. No tiene
  ni un commit (`git log master..rama` vacío), el trabajo está entero sin commitear en su worktree,
  y en master los tres ficheros siguen bajo `data/fuel/`. Conviene cerrarla ANTES que este ticket,
  para nacer ya sobre `data/overpass/` en vez de importar de un sitio que se va a mover.

## Fuera de alcance (decidido, no olvidado)

- **La media nacional de OpenVan.** Comprobado en `AverageFuelPricesRepositoryImpl.kt:37-41`: sigue
  en memoria. Y su KDoc `:21-27` argumenta contra persistirla **con razón** —el snapshot embebido
  cubre el hueco del arranque en frío y se regenera por script en cada release, así que persistir
  solo compra los segundos hasta el primer refresco— y además deja escrita la condición que lo
  cambiaría: *"si una medición muestra que los arranques en frío pasan mucho tiempo sin refrescar"*.
  No tenemos esa medición. Se queda fuera.
- **Los spots de la comunidad más allá de su TTL** — serían plazas fantasma.
- **La ocupación en vivo** (`PARKINGS-LIVE-OCCUPANCY-001`): perecedera por definición, trae su
  propio caché.
- **La segunda vía Overpass sin caché ni failover** (`OverpassPlacesDataSourceImpl`,
  `OverpassRoadNetworkDataSourceImpl`, en `androidMain` con `HttpURLConnection` propio): no tienen
  el health-check de `timestamp_osm_base` ni la lista de mirrors, o sea que son vulnerables al bug
  exacto de `overpass.osm.ch`, **y están en la ruta de detección**. Hallazgo lateral de este ticket
  → ticket propio, no se mete aquí.

## Consumidores auditados

⏳ Pendiente: `grep` de todo lo que asume que estas dos capas solo pueden estar vacías o vivas
(nunca "recordadas") — estados de las secciones del sheet, capas del mapa, y los dos carriles de
`ViewportRefetch`.
